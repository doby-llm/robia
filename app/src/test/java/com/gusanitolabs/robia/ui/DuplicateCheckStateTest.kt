package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.suggestions.DuplicateCheckStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-DU1-4: the row's text and tap action for each state of the forced check. */
class DuplicateCheckStateTest {
    @Test
    fun runningShowsTheProgressAndTheTapWaits() {
        val running = DuplicateCheckStatus.Running(done = 2, total = 5)

        assertEquals(
            listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_running, listOf(2, 5))),
            duplicateCheckLines(running),
        )
        assertFalse(duplicateCheckTapEnabled(running))
        assertFalse(duplicateCheckOpensReview(running, onSettings = true))
    }

    @Test
    fun noGroupsSaysSoInlineWithNoReview() {
        val none = DuplicateCheckStatus.Done(groupCount = 0, notCompared = 0)

        assertEquals(listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_none)), duplicateCheckLines(none))
        assertTrue(duplicateCheckTapEnabled(none))
        assertFalse(duplicateCheckOpensReview(none, onSettings = true))
    }

    @Test
    fun noGroupsWithGarmentsNotComparedAddsTheCountLine() {
        val notReady = DuplicateCheckStatus.Done(groupCount = 0, notCompared = 3)

        assertEquals(
            listOf(
                DuplicateCheckLine.Plain(R.string.duplicate_check_none),
                DuplicateCheckLine.Counted(R.plurals.duplicate_check_not_ready, count = 3),
            ),
            duplicateCheckLines(notReady),
        )
        assertFalse(duplicateCheckOpensReview(notReady, onSettings = true))
    }

    @Test
    fun groupsFoundOpenTheReviewOnlyWhileTheUserIsOnSettings() {
        val groups = DuplicateCheckStatus.Done(groupCount = 2, notCompared = 0)

        assertEquals(listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_summary)), duplicateCheckLines(groups))
        assertTrue(duplicateCheckOpensReview(groups, onSettings = true))
        assertFalse(duplicateCheckOpensReview(groups, onSettings = false))
    }

    @Test
    fun groupsFoundWithGarmentsNotComparedShowTheCountLine() {
        val groups = DuplicateCheckStatus.Done(groupCount = 1, notCompared = 2)

        assertEquals(
            listOf(DuplicateCheckLine.Counted(R.plurals.duplicate_check_not_ready, count = 2)),
            duplicateCheckLines(groups),
        )
    }

    @Test
    fun failedSaysSoAndTheTapStartsAgain() {
        assertEquals(listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_failed)), duplicateCheckLines(DuplicateCheckStatus.Failed))
        assertTrue(duplicateCheckTapEnabled(DuplicateCheckStatus.Failed))
        assertFalse(duplicateCheckOpensReview(DuplicateCheckStatus.Failed, onSettings = true))
    }

    @Test
    fun beforeAnyCheckTheRowShowsItsSummary() {
        assertEquals(listOf(DuplicateCheckLine.Plain(R.string.duplicate_check_summary)), duplicateCheckLines(DuplicateCheckStatus.Idle))
        assertTrue(duplicateCheckTapEnabled(DuplicateCheckStatus.Idle))
    }
}
