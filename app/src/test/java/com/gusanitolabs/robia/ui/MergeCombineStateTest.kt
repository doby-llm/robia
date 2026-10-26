package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncCycleSummary
import com.gusanitolabs.robia.sync.SyncRunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeCombineStateTest {
    private val beforeTap = SyncCycleSummary(
        startedAtEpochMillis = 1_000L,
        endedAtEpochMillis = 1_100L,
        result = SyncRunResult.Retry,
        pendingAtStart = 2,
    )
    private val afterTap = SyncCycleSummary(
        startedAtEpochMillis = 2_000L,
        endedAtEpochMillis = 2_200L,
        result = SyncRunResult.Success,
        pendingAtStart = 2,
    )

    @Test
    fun idleAndDoneNeverMove() {
        for (phase in listOf(MergeCombinePhase.Idle, MergeCombinePhase.Done)) {
            for (proposalPending in listOf(false, true)) {
                assertEquals(phase, nextMergeCombinePhase(phase, beforeTap, afterTap, proposalPending))
                assertEquals(phase, nextMergeCombinePhase(phase, null, null, proposalPending))
            }
        }
    }

    @Test
    fun stillCombiningUntilACycleAfterTheTapFinishes() {
        assertEquals(MergeCombinePhase.Combining, nextMergeCombinePhase(MergeCombinePhase.Combining, beforeTap, beforeTap, proposalPending = true))
        assertEquals(MergeCombinePhase.Combining, nextMergeCombinePhase(MergeCombinePhase.Combining, null, null, proposalPending = true))
    }

    @Test
    fun finishedCycleWithProposalStillPendingIsFailed() {
        assertEquals(MergeCombinePhase.Failed, nextMergeCombinePhase(MergeCombinePhase.Combining, beforeTap, afterTap, proposalPending = true))
    }

    @Test
    fun finishedCycleWithProposalGoneReturnsToIdleWithoutAMessage() {
        assertEquals(MergeCombinePhase.Idle, nextMergeCombinePhase(MergeCombinePhase.Combining, beforeTap, afterTap, proposalPending = false))
    }

    @Test
    fun failedWithAPendingProposalStaysFailedSoTheRetryStaysPossible() {
        assertEquals(MergeCombinePhase.Failed, nextMergeCombinePhase(MergeCombinePhase.Failed, beforeTap, afterTap, proposalPending = true))
        assertEquals(MergeCombinePhase.Failed, nextMergeCombinePhase(MergeCombinePhase.Failed, null, null, proposalPending = true))
    }

    @Test
    fun failedWithANewerFinishedCycleAndNoProposalReturnsToIdle() {
        assertEquals(MergeCombinePhase.Idle, nextMergeCombinePhase(MergeCombinePhase.Failed, beforeTap, afterTap, proposalPending = false))
        assertEquals(MergeCombinePhase.Idle, nextMergeCombinePhase(MergeCombinePhase.Failed, null, afterTap, proposalPending = false))
    }

    @Test
    fun noNewerCycleLeavesAFailedOrCombiningPhaseAlone() {
        assertEquals(MergeCombinePhase.Failed, nextMergeCombinePhase(MergeCombinePhase.Failed, beforeTap, beforeTap, proposalPending = false))
        assertEquals(MergeCombinePhase.Combining, nextMergeCombinePhase(MergeCombinePhase.Combining, beforeTap, beforeTap, proposalPending = false))
    }

    @Test
    fun timeoutIsFalseWhileACycleIsRunning() {
        assertFalse(mergeCombineTimedOut(MergeCombinePhase.Combining, elapsedMs = 6 * 60_000L, cycleRunning = true))
    }

    @Test
    fun timeoutIsTrueAfter90SecondsWithNoCycle() {
        assertFalse(mergeCombineTimedOut(MergeCombinePhase.Combining, elapsedMs = MERGE_COMBINE_TIMEOUT_MS - 1, cycleRunning = false))
        assertTrue(mergeCombineTimedOut(MergeCombinePhase.Combining, elapsedMs = MERGE_COMBINE_TIMEOUT_MS, cycleRunning = false))
    }

    @Test
    fun timeoutOnlyAppliesToCombining() {
        for (phase in listOf(MergeCombinePhase.Idle, MergeCombinePhase.Done, MergeCombinePhase.Failed)) {
            assertFalse(phase.name, mergeCombineTimedOut(phase, elapsedMs = 10 * MERGE_COMBINE_TIMEOUT_MS, cycleRunning = false))
        }
    }

    @Test
    fun aKeptProposalShowsTheCardInEveryPhase() {
        for (phase in MergeCombinePhase.entries) {
            assertTrue(phase.name, mergeCardVisible(proposalPending = true, dialogDismissed = true, phase = phase))
        }
    }

    @Test
    fun combiningShowsTheCardEvenWithoutAProposal() {
        assertTrue(mergeCardVisible(proposalPending = false, dialogDismissed = false, phase = MergeCombinePhase.Combining))
    }

    @Test
    fun cardIsHiddenForFailedDoneAndIdleWithoutAProposal() {
        for (phase in listOf(MergeCombinePhase.Idle, MergeCombinePhase.Done, MergeCombinePhase.Failed)) {
            assertFalse(phase.name, mergeCardVisible(proposalPending = false, dialogDismissed = false, phase = phase))
            assertFalse(phase.name, mergeCardVisible(proposalPending = false, dialogDismissed = true, phase = phase))
        }
    }

    @Test
    fun proposalWhoseDialogIsStillOpenIsNotOnTheCardYet() {
        assertFalse(mergeCardVisible(proposalPending = true, dialogDismissed = false, phase = MergeCombinePhase.Idle))
    }
}
