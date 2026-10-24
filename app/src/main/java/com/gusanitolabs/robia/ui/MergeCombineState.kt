package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.SyncCycleSummary

/** What the merge card shows after the user taps Combine. */
/** How long the card may stay on Combining without a finished cycle before it reports Failed (retry possible). */
const val MERGE_COMBINE_TIMEOUT_MS = 90_000L

/** How often the card checks the timeout while it is Combining. */
const val MERGE_COMBINE_CHECK_INTERVAL_MS = 5_000L

enum class MergeCombinePhase { Idle, Combining, Done, Failed }

/**
 * True when Combining has waited [MERGE_COMBINE_TIMEOUT_MS] with no sync cycle running. A running cycle never times out:
 * an upload can take minutes after the merge is already applied, and its own result decides the card.
 */
fun mergeCombineTimedOut(phase: MergeCombinePhase, elapsedMs: Long, cycleRunning: Boolean): Boolean =
    phase == MergeCombinePhase.Combining && !cycleRunning && elapsedMs >= MERGE_COMBINE_TIMEOUT_MS

/**
 * Moves Combining or Failed on when a cycle that started after the tap has finished. [cycleAtTap] is the last cycle seen
 * when Combine was tapped. A proposal still pending means the merge did not apply: Combining becomes Failed, and a Failed
 * result stays Failed so Combine can be retried. No pending proposal means the merge applied: the card returns to Idle.
 */
fun nextMergeCombinePhase(
    phase: MergeCombinePhase,
    cycleAtTap: SyncCycleSummary?,
    lastCycle: SyncCycleSummary?,
    proposalPending: Boolean,
): MergeCombinePhase = when {
    phase != MergeCombinePhase.Combining && phase != MergeCombinePhase.Failed -> phase
    lastCycle == null || lastCycle == cycleAtTap -> phase
    proposalPending -> if (phase == MergeCombinePhase.Combining) MergeCombinePhase.Failed else phase
    else -> MergeCombinePhase.Idle
}

/**
 * The Browse card shows only for a pending proposal the user kept (its dialog was dismissed), or while a Combine runs.
 * Without a pending proposal, no Combine or Replace is shown after a finished or failed Combine.
 */
fun mergeCardVisible(proposalPending: Boolean, dialogDismissed: Boolean, phase: MergeCombinePhase): Boolean =
    (proposalPending && dialogDismissed) || phase == MergeCombinePhase.Combining
