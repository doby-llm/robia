package com.gusanitolabs.robia.media.cutout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.random.Random

/** REQ-ED-03 (V-ED2-1..3): the full history of a photo, its memory budget, and Cut out again. */
class EditHistoryTest {
    private val w = 24
    private val h = 24

    @get:Rule
    val folder = TemporaryFolder()

    private fun baseAlpha() = FloatArray(w * h) { 0.2f }

    private fun newHistory(budget: EditBudget = EditBudget(), release: (String) -> Unit = {}) = PhotoHistory<String>(
        budget = budget,
        directory = folder.root,
        write = { text, file -> file.writeText(text) },
        read = { file -> file.readText() },
        release = release,
    )

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
     * reaches is still exact. The budget is 1 KB, not 1 MB, because the test photo is 24×24. Its two live arrays (4.6 KB) are
     * over the budget on their own, so only the folding is exercised: the photo on screen is never dropped.
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

    /**
     * Item 1 (review): the live arrays of every photo count against the budget. Five Cut outs with a 16 KB budget drop the
     * oldest photos kept off screen, and the photo on screen keeps its undo.
     */
    @Test
    fun manyCutOuts_overBudget_dropTheOldestKeptPhotos_andUndoOnTheCurrentPhotoStillWorks() {
        val limit = 16L shl 10
        val budget = EditBudget(limit)
        val history = newHistory(budget)
        var photo = "photo-0"
        var session = RefineSession(w, h, baseAlpha(), budget)
        repeat(5) { cut ->
            session.applyBrush(RefineSession.Mode.Remove, floatArrayOf(2f + cut, 3f), 2f, 1f)
            history.cutOut(photo, session, fromRedo = false)
            photo = "photo-${cut + 1}"
            session = RefineSession(w, h, baseAlpha(), budget)
            assertTrue("accounted ${budget.used} bytes, limit $limit", budget.used <= limit)
        }

        session.applyBrush(RefineSession.Mode.Remove, floatArrayOf(4f, 4f), 2f, 1f)
        val afterFirst = session.currentAlpha()
        session.applyBrush(RefineSession.Mode.Remove, floatArrayOf(20f, 20f), 3f, 1f)
        session.undo()
        assertArrayEquals("undo on the current photo", afterFirst, session.currentAlpha(), 0f)

        var backs = 0
        while (history.canGoBack) {
            val restored = checkNotNull(history.back(photo, session, fromRedo = false))
            photo = restored.photo
            session = restored.session
            backs++
        }
        // photo-0, photo-1 and photo-2 were dropped, so undo stops at photo-3, the oldest kept photo.
        assertEquals("photo-3", photo)
        assertEquals(2, backs)
        assertEquals(backs, folder.root.listFiles()!!.size)

        session.close()
        history.close()
        assertEquals(0L, budget.used)
    }

    /** V-ED2-3: Cut out again keeps the previous photo as a file; undo brings it back with its own history position. */
    @Test
    fun cutOut_keepsThePreviousPhotoAsAFile_andUndoRestoresItsPosition() {
        val history = newHistory()
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
        val history = newHistory()
        history.cutOut("photo-1", RefineSession(w, h, baseAlpha()), fromRedo = false)
        history.back("photo-2", RefineSession(w, h, baseAlpha()), fromRedo = true)
        assertTrue(history.canGoForward)

        history.cutOut("photo-1", RefineSession(w, h, baseAlpha()), fromRedo = false)

        assertFalse(history.canGoForward)
        assertEquals(listOf("photo-1"), folder.root.listFiles()!!.map { it.readText() })
        history.close()
    }

    /** Item 2 (review): undo and redo replayed on Dispatchers.Default give the alpha that the same steps give on this thread. */
    @Test
    fun undoAndRedoOnDefaultDispatcher_equalTheSynchronousSteps() = runBlocking {
        val synchronous = RefineSession(w, h, baseAlpha())
        val replayed = RefineSession(w, h, baseAlpha())
        val syncRandom = Random(19)
        val replayRandom = Random(19)
        repeat(20) {
            randomChange(synchronous, syncRandom)
            randomChange(replayed, replayRandom)
        }
        repeat(12) { step ->
            synchronous.undo()
            withContext(Dispatchers.Default) { replayed.undo() }
            assertArrayEquals("undo ${step + 1}", synchronous.currentAlpha(), replayed.currentAlpha(), 0f)
        }
        repeat(7) { step ->
            synchronous.redo()
            withContext(Dispatchers.Default) { replayed.redo() }
            assertArrayEquals("redo ${step + 1}", synchronous.currentAlpha(), replayed.currentAlpha(), 0f)
        }
    }

    /** Item 3 (review): the files of an editor whose process died are deleted when the next history starts. */
    @Test
    fun creatingAHistory_deletesTheFilesLeftByADeadEditor() {
        File(folder.root, "left-over.png").writeText("photo-0")

        val history = newHistory()

        assertTrue(folder.root.listFiles()!!.isEmpty())
        history.close()
    }

    /**
     * Item 5 (review): when the photo on screen cannot be written, back and forward fail without moving, and the photo they
     * read back is released.
     */
    @Test
    fun whenThePhotoOnScreenCannotBeWritten_backAndForwardReleaseThePhotoRead() {
        val released = mutableListOf<String>()
        var writable = true
        val history = PhotoHistory<String>(
            budget = EditBudget(),
            directory = folder.root,
            write = { text, file ->
                if (!writable) throw IOException("disk full")
                file.writeText(text)
            },
            read = { file -> file.readText() },
            release = { released += it },
        )
        val first = RefineSession(w, h, baseAlpha())
        history.cutOut("photo-1", first, fromRedo = false)
        val second = RefineSession(w, h, baseAlpha())

        writable = false
        assertTrue(runCatching { history.back("photo-2", second, fromRedo = true) }.isFailure)
        assertEquals(listOf("photo-1"), released)
        assertTrue(history.canGoBack)

        writable = true
        assertEquals("photo-1", checkNotNull(history.back("photo-2", second, fromRedo = true)).photo)

        writable = false
        assertTrue(runCatching { history.forward("photo-1", first, fromRedo = false) }.isFailure)
        assertEquals(listOf("photo-1", "photo-2"), released)
        assertTrue(history.canGoForward)
        history.close()
    }
}
