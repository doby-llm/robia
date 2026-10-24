# TASK-SYNC-BANNER — Combine/Replace card only while a proposal is pending or combining

## Goal
F2 (REQ-FB-02): after Manu tapped Combine in the popup (it worked), Browse showed the Combine/Replace card again, in red "did not finish" form, although the combine was done. Cause (high confidence): the card shows when `pendingMergeSummary != null && mergeDialogDismissed` or `mergeCombinePhase != Idle`; a 90 s safety net (`MERGE_COMBINE_TIMEOUT_MS`) flips Combining to Failed while the 115-garment combine is still uploading; `nextMergeCombinePhase` never leaves Failed; when the cycle succeeds the summary clears but Failed stays, with enabled Combine and the destructive **Replace**.

## Read first
`R3-DEC-041`; planner investigation `sync.md` §F2; `ui/MergeCombineState.kt`; `ui/RobiaApp.kt` timeout (~1160-1170) and card condition (~1802-1803); `sync/WardrobeSyncOutboxProcessor.kt` `pendingMergeSummary` clearing (~949-952) and the guard at ~819; `MergeCombineStateTest`; `sync/MergeProposalScenarioTest`.

## Files
`ui/RobiaApp.kt` (timeout and card condition only); `ui/MergeCombineState.kt`; `sync/WardrobeSyncOutboxProcessor.kt` (the `pendingMergeSummary` clearing only); `app/src/test/java/com/gusanitolabs/robia/ui/MergeCombineStateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/MergeProposalScenarioTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Pure `mergeCombineTimedOut(phase, elapsedMs, cycleRunning)` in `MergeCombineState.kt`; the `RobiaApp` effect calls it and never flips to Failed while a cycle is running (`restoreProgress != null` or the sync status is Syncing).
2. `nextMergeCombinePhase`: Combining or Failed with a newer finished cycle (≠ `cycleAtTap`) and no pending proposal → Idle; Combining + newer cycle + proposal still pending → Failed (unchanged); Failed with a proposal pending stays Failed (retry stays possible); Idle and Done never move.
3. Pure `mergeCardVisible(proposalPending, dialogDismissed, phase)`: true only with a pending proposal whose dialog was dismissed, or while phase is Combining. Replace and `RobiaApp` use it instead of `phase != Idle`.
4. Processor: clear `pendingMergeSummary` as soon as the local merge import succeeded, whatever the upload result (AuthBlocked, Retry, Success). The guard at ~819 must no longer be blocked by a stale summary.
5. Replace `idleAndDoneAndFailedDoNotMove` with the new table (equal strength: Idle and Done never move; the Failed cases above).

## Tests
V-SYNC-BANNER-1..4 (CI), V-SYNC-BANNER-5 (REV: Replace reachable only from a pending proposal).

## Done when
V-SYNC-BANNER-1..5 pass, V-SYNC-BANNER-D is ready for Manu; CI green; independent review done.

## Do not
Change merge decisions, the Combine/Replace actions, upload ETA or speed; show Replace in any state without a pending proposal.
