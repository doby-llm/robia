package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncCycleSummary
import com.gusanitolabs.robia.sync.SyncRunResult
import org.junit.Assert.assertEquals
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
    fun idleAndDoneAndFailedDoNotMove() {
        for (phase in listOf(MergeCombinePhase.Idle, MergeCombinePhase.Done, MergeCombinePhase.Failed)) {
            assertEquals(phase, nextMergeCombinePhase(phase, beforeTap, afterTap, proposalPending = false))
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
    fun finishedCycleWithProposalGoneIsDone() {
        assertEquals(MergeCombinePhase.Done, nextMergeCombinePhase(MergeCombinePhase.Combining, beforeTap, afterTap, proposalPending = false))
    }
}
