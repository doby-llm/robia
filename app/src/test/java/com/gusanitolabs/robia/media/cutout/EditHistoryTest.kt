package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

/** REQ-ED-03 (V-ED2-1..3): the full history of a photo, its memory budget, and Cut out again. */
class EditHistoryTest {
    private val w = 24
    private val h = 24

    @get:Rule
    val folder = TemporaryFolder()

    private fun baseAlpha() = FloatArray(w * h) { 0.2f }

    /** One random change of any kind. Each call pushes exactly one op, so the states recorded after each call line up with the ops. */
    private fun randomChange(session: RefineSession, random: Random) {
        val mode = if (random.nextBoolean()) RefineSession.Mode.Keep else RefineSession.Mode.Remove
        when (random.nextInt(4)) {
            0 -> {
                val at = random.nextInt(w * h)
                session.apply(mode, FloatArray(w * h) { if (it == at) 1f else 0f }, hard = true)
            }
            1 -> session.applyBrush(
                mode,
                floatArrayOf(random.nextInt(w).toFloat(), random.nextInt(h).toFloat(), random.nextInt(w).toFloat(), random.nextInt(h).toFloat()),
                radiusPx = 2f + random.nextInt(3),
                featherPx = 1f,
            )
            2 -> session.apply(mode, FloatArray(w * h) { random.nextFloat() }, hard = false)
            else -> if (session.isChanged) session.reset() else session.applyBrush(mode, floatArrayOf(4f, 4f), 2f, 1f)
        }
    }

    /** V-ED2-1: 30 random changes; undo ×30 gives back the base alpha, redo ×30 the final alpha, every step exact. */
    @Test
    fun thirtyRandomChanges_undoAllThenRedoAll_isExact() {
        val session = RefineSession(w, h, baseAlpha())
        val random = Random(7)
        val states = mutableListOf(session.currentAlpha())
        repeat(30) {
            randomChange(session, random)
            states += session.currentAlpha()
        }

        for (step in 1..30) {
            session.undo()
            assertArrayEquals("undo $step", states[30 - step], session.currentAlpha(), 0f)
        }
        assertFalse(session.canUndo)

        for (step in 1..30) {
            session.redo()
            assertArrayEquals("redo $step", states[step], session.currentAlpha(), 0f)
        }
        assertFalse(session.canRedo)
    }

    /** V-ED2-1: a new change after 5 undos drops the 5-step redo tail. */
    @Test
    fun aNewChangeAfterFiveUndos_dropsTheRedoTail() {
        val session = RefineSession(w, h, baseAlpha())
        val random = Random(11)
        val states = mutableListOf(session.currentAlpha())
        repeat(10) {
            randomChange(session, random)
            states += session.currentAlpha()
        }
        repeat(5) { session.undo() }
        assertArrayEquals(states[5], session.currentAlpha(), 0f)

        session.applyBrush(RefineSession.Mode.Remove, floatArrayOf(3f, 3f), 2f, 1f)

        assertFalse(session.canRedo)
        repeat(6) { session.undo() } // the new change, then the five before it
        assertArrayEquals(states[0], session.currentAlpha(), 0f)
        assertFalse(session.canUndo)
    }

    /**
     * V-ED2-2: with a small budget the oldest ops fold into a checkpoint. Undo stops at that checkpoint, and every state it
     * reaches is still exact. The budget is 1 KB, not 1 MB, because the test photo is 24×24.
     */
    @Test
    fun overBudget_oldestOpsFold_andUndoStillExact() {
        val budget = EditBudget(limit = 1024L)
        val session = RefineSession(w, h, baseAlpha(), budget)
        val random = Random(3)
        val states = mutableListOf(session.currentAlpha())
        repeat(30) {
            randomChange(session, random)
            states += session.currentAlpha()
        }

        var steps = 0
        while (session.canUndo) {
            session.undo()
            steps++
            assertArrayEquals("undo $steps", states[30 - steps], session.currentAlpha(), 0f)
        }

        assertTrue("some ops were folded, $steps of 30 are undoable", steps in 1 until 30)
        session.close()
        assertEquals(0L, budget.used)
    }

    /** V-ED2-3: Cut out again keeps the previous photo as a file; undo brings it back with its own history position. */
    @Test
    fun cutOut_keepsThePreviousPhotoAsAFile_andUndoRestoresItsPosition() {
        val history = PhotoHistory<String>(
            directory = folder.root,
            write = { text, file -> file.writeText(text) },
            read = { file -> file.readText() },
        )
        val first = RefineSession(w, h, baseAlpha())
        first.applyBrush(RefineSession.Mode.Remove, floatArrayOf(5f, 5f), 3f, 1f)
        val afterOne = first.currentAlpha()
        first.applyBrush(RefineSession.Mode.Remove, floatArrayOf(15f, 15f), 3f, 1f)
        val atCut = first.currentAlpha()

        history.cutOut("photo-1", first, fromRedo = false)
        assertEquals(listOf("photo-1"), folder.root.listFiles()!!.map { it.readText() })

        val second = RefineSession(w, h, baseAlpha())
        second.apply(RefineSession.Mode.Keep, FloatArray(w * h) { if (it == 0) 1f else 0f }, hard = true)
        val secondAtCut = second.currentAlpha()

        val restored = history.back("photo-2", second, fromRedo = true)
        assertNotNull(restored)
        assertEquals("photo-1", restored!!.photo)
        assertTrue(restored.session === first)
        assertFalse(restored.fromRedo)
        assertArrayEquals(atCut, first.currentAlpha(), 0f)
        assertEquals(listOf("photo-2"), folder.root.listFiles()!!.map { it.readText() })

        first.undo()
        assertArrayEquals("undo keeps the restored session's position", afterOne, first.currentAlpha(), 0f)

        val forward = history.forward("photo-1", first, fromRedo = false)
        assertNotNull(forward)
        assertEquals("photo-2", forward!!.photo)
        assertTrue(forward.session === second)
        assertTrue(forward.fromRedo)
        assertArrayEquals(secondAtCut, second.currentAlpha(), 0f)
        assertEquals(listOf("photo-1"), folder.root.listFiles()!!.map { it.readText() })

        history.close()
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    /** V-ED2-3: a Cut out again made after an undo drops the undone photo and its file. */
    @Test
    fun cutOutAfterUndoingACut_dropsTheUndonePhoto() {
        val history = PhotoHistory<String>(
            directory = folder.root,
            write = { text, file -> file.writeText(text) },
            read = { file -> file.readText() },
        )
        history.cutOut("photo-1", RefineSession(w, h, baseAlpha()), fromRedo = false)
        history.back("photo-2", RefineSession(w, h, baseAlpha()), fromRedo = true)
        assertTrue(history.canGoForward)

        history.cutOut("photo-1", RefineSession(w, h, baseAlpha()), fromRedo = false)

        assertFalse(history.canGoForward)
        assertEquals(listOf("photo-1"), folder.root.listFiles()!!.map { it.readText() })
        history.close()
    }
}
