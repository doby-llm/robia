package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG3-1: keep/remove with smart (hard) and brush (soft) masks, undo ≥ 10, reset. */
class RefineSessionTest {
    private val w = 12
    private val h = 12

    private fun filled(value: Float) = FloatArray(w * h) { value }

    /** A stored alpha above the no-source threshold, so Keep has something to restore onto. */
    private val soft = 0.2f

    private fun pointMask(x: Int, y: Int, value: Float = 1f) = FloatArray(w * h).also { it[y * w + x] = value }

    private fun RefineSession.alphaAt(x: Int, y: Int) = currentAlpha()[y * w + x]

    @Test
    fun keep_hardMask_isDilatedByADiscOfTwoPixels_andLeavesFarPixelsAlone() {
        val session = RefineSession(w, h, filled(soft))

        session.apply(RefineSession.Mode.Keep, pointMask(6, 6), hard = true)

        assertEquals(1f, session.alphaAt(6, 6), 0f)
        assertEquals(1f, session.alphaAt(8, 6), 0f)
        assertEquals(1f, session.alphaAt(6, 4), 0f)
        assertEquals(1f, session.alphaAt(7, 7), 0f)
        assertEquals(soft, session.alphaAt(4, 4), 0f)
        assertEquals(soft, session.alphaAt(8, 8), 0f)
        assertEquals(soft, session.alphaAt(9, 6), 0f)
    }

    @Test
    fun keep_fullyTransparentBasePixel_staysTransparent() {
        val base = filled(1f).also { it[6 * w + 6] = 0f }
        val session = RefineSession(w, h, base)
        session.apply(RefineSession.Mode.Remove, pointMask(6, 6, value = 1f), hard = false)

        session.apply(RefineSession.Mode.Keep, pointMask(6, 6, value = 1f), hard = false)

        assertEquals(0f, session.alphaAt(6, 6), 0f)
    }

    @Test
    fun keep_partiallyTransparentBasePixel_canBeRestored() {
        val session = RefineSession(w, h, filled(0.5f))
        session.apply(RefineSession.Mode.Remove, pointMask(6, 6, value = 1f), hard = false)

        session.apply(RefineSession.Mode.Keep, pointMask(6, 6, value = 1f), hard = false)

        assertEquals(1f, session.alphaAt(6, 6), 0f)
    }

    @Test
    fun keepAlpha_belowTheUnrestorableThreshold_neverRaises() {
        assertEquals(0f, keepAlpha(current = 0f, base = 0f, mask = 1f), 0f)
        assertEquals(0.04f, keepAlpha(current = 0.04f, base = 0.04f, mask = 1f), 0f)
        assertEquals(1f, keepAlpha(current = 0.5f, base = 0.5f, mask = 1f), 0f)
    }

    @Test
    fun keep_hardMask_belowHalfIsIgnored() {
        val session = RefineSession(w, h, filled(0f))

        session.apply(RefineSession.Mode.Keep, filled(0.4f), hard = true)

        assertFalse(session.isChanged)
    }

    @Test
    fun remove_hardMask_clearsTheDilatedRegion() {
        val session = RefineSession(w, h, filled(1f))

        session.apply(RefineSession.Mode.Remove, pointMask(6, 6), hard = true)

        assertEquals(0f, session.alphaAt(6, 6), 0f)
        assertEquals(0f, session.alphaAt(6, 4), 0f)
        assertEquals(1f, session.alphaAt(4, 4), 0f)
        assertEquals(1f, session.alphaAt(1, 1), 0f)
    }

    @Test
    fun remove_softMask_scalesAlphaByOneMinusMask() {
        val session = RefineSession(w, h, filled(1f))

        session.apply(RefineSession.Mode.Remove, pointMask(6, 6, value = 0.25f), hard = false)

        assertEquals(0.75f, session.alphaAt(6, 6), 1e-6f)
        assertEquals(1f, session.alphaAt(5, 6), 0f)
    }

    @Test
    fun keep_softMask_takesTheMaximumOfAlphaAndMask() {
        val session = RefineSession(w, h, filled(0.5f))

        session.apply(RefineSession.Mode.Keep, pointMask(3, 3, value = 0.2f), hard = false)
        assertEquals(0.5f, session.alphaAt(3, 3), 1e-6f)

        session.apply(RefineSession.Mode.Keep, pointMask(3, 3, value = 0.9f), hard = false)
        assertEquals(0.9f, session.alphaAt(3, 3), 1e-6f)
        assertEquals(0.5f, session.alphaAt(4, 4), 1e-6f)
    }

    @Test
    fun undo_restoresEveryStepOfTenChanges() {
        val session = RefineSession(w, h, filled(soft))
        val snapshots = mutableListOf(session.currentAlpha())
        repeat(10) { x ->
            session.apply(RefineSession.Mode.Keep, pointMask(x, 0, value = 0.6f), hard = false)
            snapshots += session.currentAlpha()
        }

        repeat(10) { step ->
            session.undo()
            assertArrayEquals(snapshots[9 - step], session.currentAlpha(), 0f)
        }

        assertFalse(session.canUndo)
        assertFalse(session.isChanged)
    }

    /** REQ-ED-03: the history has no depth limit, so the first of 12 changes is still undoable after 11 undos (was: 10 steps). */
    @Test
    fun undo_keepsEveryChange_beyondTheOldLimitOfTen() {
        val session = RefineSession(w, h, filled(soft))
        val afterFirst = run {
            session.apply(RefineSession.Mode.Keep, pointMask(0, 0), hard = false)
            session.currentAlpha()
        }
        repeat(11) { x -> session.apply(RefineSession.Mode.Keep, pointMask(x + 1, 1), hard = false) }

        repeat(11) { session.undo() }

        assertArrayEquals(afterFirst, session.currentAlpha(), 0f)
        assertTrue(session.canUndo)
        session.undo()
        assertFalse(session.canUndo)
        assertFalse(session.isChanged)
    }

    @Test
    fun reset_returnsToTheAutomaticResult_andIsUndoable() {
        val base = filled(0.5f)
        val session = RefineSession(w, h, base)
        session.apply(RefineSession.Mode.Remove, pointMask(6, 6), hard = true)
        val edited = session.currentAlpha()

        session.reset()
        assertArrayEquals(base, session.currentAlpha(), 0f)
        assertFalse(session.isChanged)

        session.undo()
        assertArrayEquals(edited, session.currentAlpha(), 0f)
    }

    /** V-SG4-4: a replaced base is the new automatic result; undo brings back the previous base and its edits. */
    @Test
    fun replaceBase_thenUndo_restoresThePreviousBaseAndEdits() {
        val oldBase = filled(0.5f)
        val session = RefineSession(w, h, oldBase)
        session.apply(RefineSession.Mode.Remove, pointMask(6, 6), hard = true)
        val edited = session.currentAlpha()
        val newBase = filled(0.8f)

        session.replaceBase(newBase)
        assertArrayEquals(newBase, session.currentAlpha(), 0f)
        assertFalse(session.isChanged)

        session.undo()
        assertArrayEquals(edited, session.currentAlpha(), 0f)
        session.reset()
        assertArrayEquals(oldBase, session.currentAlpha(), 0f)
    }

    @Test
    fun replaceBase_resetReturnsToTheNewBase() {
        val session = RefineSession(w, h, filled(0.5f))
        val newBase = filled(0.8f)
        session.replaceBase(newBase)
        session.apply(RefineSession.Mode.Remove, pointMask(6, 6), hard = true)

        session.reset()

        assertArrayEquals(newBase, session.currentAlpha(), 0f)
    }

    @Test
    fun replaceBase_ofWrongSize_isRejected() {
        val session = RefineSession(w, h, filled(0f))

        assertThrows(IllegalArgumentException::class.java) { session.replaceBase(FloatArray(3)) }
        assertFalse(session.canUndo)
    }

    @Test
    fun reset_withoutChanges_isANoOp() {
        val session = RefineSession(w, h, filled(0.5f))

        session.reset()

        assertFalse(session.canUndo)
    }

    @Test
    fun maskOfWrongSize_isRejected() {
        val session = RefineSession(w, h, filled(0f))

        assertThrows(IllegalArgumentException::class.java) {
            session.apply(RefineSession.Mode.Keep, FloatArray(3), hard = false)
        }
        assertFalse(session.canUndo)
    }
}
