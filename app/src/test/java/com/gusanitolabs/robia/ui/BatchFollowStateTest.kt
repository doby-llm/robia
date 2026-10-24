package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFollowStateTest {
    private fun row(status: BatchDraftStatus, orderIndex: Int) = BatchDraftItem(
        orderIndex = orderIndex,
        originalPhotoUri = "content://photo/$orderIndex",
        status = status,
    )

    @Test
    fun followingStartsOn() {
        assertTrue(BatchFollowState().following)
    }

    @Test
    fun manualScrollTurnsFollowingOff() {
        assertFalse(BatchFollowState().onUserScroll().following)
    }

    @Test
    fun followTapTurnsFollowingOn() {
        val stopped = BatchFollowState().onUserScroll()

        assertTrue(stopped.onFollowTapped().following)
    }

    @Test
    fun returningToTheBottomTurnsFollowingOn() {
        val stopped = BatchFollowState().onUserScroll()

        assertTrue(stopped.onAtBottom().following)
    }

    @Test
    fun targetIsTheFirstProcessingRow() {
        val rows = listOf(
            row(BatchDraftStatus.Ready, 0),
            row(BatchDraftStatus.Processing, 1),
            row(BatchDraftStatus.Queued, 2),
            row(BatchDraftStatus.Processing, 3),
        )

        assertEquals(1, BatchFollowState().targetIndex(rows))
    }

    @Test
    fun noTargetWithoutAProcessingRow() {
        val rows = listOf(
            row(BatchDraftStatus.Queued, 0),
            row(BatchDraftStatus.NeedsReview, 1),
        )

        assertNull(BatchFollowState().targetIndex(rows))
        assertNull(BatchFollowState().targetIndex(emptyList()))
    }
}
