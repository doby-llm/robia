package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncCycleSummary

/** What the merge card shows after the user taps Combine. */
enum class MergeCombinePhase { Idle, Combining, Done, Failed }

/**
 * Moves Combining on when a cycle that started after the tap has finished: the proposal still pending means the
 * merge did not apply (Failed), otherwise it did (Done). [cycleAtTap] is the last cycle seen when Combine was tapped.
 */
fun nextMergeCombinePhase(
    phase: MergeCombinePhase,
    cycleAtTap: SyncCycleSummary?,
    lastCycle: SyncCycleSummary?,
    proposalPending: Boolean,
): MergeCombinePhase = when {
    phase != MergeCombinePhase.Combining -> phase
    lastCycle == null || lastCycle == cycleAtTap -> phase
    proposalPending -> MergeCombinePhase.Failed
    else -> MergeCombinePhase.Done
}
