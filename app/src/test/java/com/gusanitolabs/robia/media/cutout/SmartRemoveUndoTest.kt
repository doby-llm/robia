package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** V-UI-SMART-4: a smart tap's removal is a Remove through [RefineSession], so Undo and Redo restore it exactly. */
class SmartRemoveUndoTest {
    private val w = 10
    private val h = 10

    /** An alpha that is 1 inside the blocks (inclusive x0..x1, y0..y1) and 0 everywhere else. */
    private fun alpha(vararg blocks: IntArray): FloatArray = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (blocks.any { (x0, y0, x1, y1) -> x in x0..x1 && y in y0..y1 }) 1f else 0f
    }

    private val garment = intArrayOf(0, 0, 5, 5)
    private val loose = intArrayOf(7, 1, 8, 2)

    @Test
    fun aSmartRemoveClearsThePieceAndUndoAndRedoReplayIt() {
        val base = alpha(garment, loose)
        val session = RefineSession(w, h, base)
        val pick = componentsToRemove(base, w, h, listOf(7 to 1), snapRadiusPx = 2)
        assertEquals(SmartTapOutcome.Remove, smartTapOutcome(RefineSession.Mode.Remove, pick, base))

        session.apply(RefineSession.Mode.Remove, pick.mask, hard = true)
        val removed = session.currentAlpha().toList()
        for (y in 1..2) for (x in 7..8) assertEquals(0f, session.currentAlpha()[y * w + x], 0f)
        // The mask's 2 px dilation takes the garment's edge column nearest the piece; pixels farther away keep their alpha.
        assertEquals(1f, session.currentAlpha()[0], 0f)
        assertEquals(1f, session.currentAlpha()[2 * w + 2], 0f)

        session.undo()
        assertEquals(base.toList(), session.currentAlpha().toList())
        session.redo()
        assertEquals(removed, session.currentAlpha().toList())
    }

    @Test
    fun aKeepSmartTapNeverCallsApply() {
        val base = alpha(garment, loose)
        val session = RefineSession(w, h, base)
        val pick = componentsToRemove(base, w, h, listOf(7 to 1), snapRadiusPx = 2)
        // The editor calls RefineSession.apply only for a Remove outcome, so a Keep tap leaves the session untouched.
        assertEquals(SmartTapOutcome.KeepHint, smartTapOutcome(RefineSession.Mode.Keep, pick, base))
        assertEquals(base.toList(), session.currentAlpha().toList())
        assertFalse(session.canUndo)
    }
}
