package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-COL-08 paint-drag range logic (V-C04-3). Pure, so it runs on the JVM. */
class CollectionDragPaintTest {
    private val ids = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun anchorOutsideDraftPaintsTheRangeIn() {
        val paint = DragPaint(anchorId = "b", before = setOf("a", "f"))
        assertTrue(paint.targetIn)
        paint.moveTo("d")
        // Range b..d is painted in; a and f keep their draft state.
        assertEquals(setOf("a", "b", "c", "d", "f"), paint.draft(ids))
    }

    @Test
    fun anchorInsideDraftPaintsTheRangeOut() {
        val paint = DragPaint(anchorId = "b", before = setOf("a", "b", "c", "d", "f"))
        assertFalse(paint.targetIn)
        paint.moveTo("d")
        assertEquals(setOf("a", "f"), paint.draft(ids))
    }

    @Test
    fun reversingDirectionPaintsTheSameRange() {
        val up = DragPaint(anchorId = "d", before = emptySet())
        up.moveTo("b")
        assertEquals(setOf("b", "c", "d"), up.draft(ids))
    }

    @Test
    fun shrinkingTheRangeRestoresItemsToTheirPreDragState() {
        // d was in the draft before the drag; e was not. Both leave the range when it shrinks back.
        val paint = DragPaint(anchorId = "c", before = setOf("d"))
        paint.moveTo("e")
        assertEquals(setOf("c", "d", "e"), paint.draft(ids))
        paint.moveTo("a")
        // Range a..c is painted in. d is outside it again and is back to its before state (in); e is out.
        assertEquals(setOf("a", "b", "c", "d"), paint.draft(ids))
    }

    @Test
    fun shrinkingAPaintOutRestoresItemsThatWereInBefore() {
        val paint = DragPaint(anchorId = "b", before = setOf("b", "c", "d", "f"))
        paint.moveTo("e")
        assertEquals(setOf("f"), paint.draft(ids))
        paint.moveTo("c")
        // Range b..c is painted out. d was in before the drag, so it comes back.
        assertEquals(setOf("d", "f"), paint.draft(ids))
    }

    @Test
    fun reversingBelowTheAnchorRestoresItemsNotInBefore() {
        val paint = DragPaint(anchorId = "c", before = emptySet())
        paint.moveTo("e")
        assertEquals(setOf("c", "d", "e"), paint.draft(ids))
        paint.moveTo("b")
        // d and e left the range and were not in the draft before the drag.
        assertEquals(setOf("b", "c"), paint.draft(ids))
    }

    @Test
    fun withoutAMoveOnlyTheAnchorChanges() {
        val paint = DragPaint(anchorId = "b", before = setOf("a"))
        assertEquals("b", paint.currentId)
        assertEquals(setOf("a", "b"), paint.draft(ids))
    }

    @Test
    fun missingAnchorLeavesTheDraftUnchanged() {
        val paint = DragPaint(anchorId = "zz", before = setOf("a"))
        paint.moveTo("c")
        assertEquals(setOf("a"), paint.draft(ids))
    }

    @Test
    fun missingCurrentLeavesTheDraftUnchanged() {
        val paint = DragPaint(anchorId = "b", before = setOf("a"))
        paint.moveTo("zz")
        assertEquals(setOf("a"), paint.draft(ids))
    }
}
