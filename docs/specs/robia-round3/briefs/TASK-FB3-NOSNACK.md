# TASK-FB3-NOSNACK — No snackbar for a single decision

## Goal
ui3 F23 (REQ-FB-24, R3-DEC-059). Every Apply shows "1 change applied", every Reject "Kept as it was". Remove those messages; keep the bulk message and the failure messages.

## Read first
`artifacts/inv-f3-a.md` F23; `R3-DEC-059`; `briefs/TASK-REV-UNDO.md` (what the snackbar was for) and `TASK-REV-BULK.md`; `ui/TagReviewScreen.kt`: `ReviewUndoSnack`, `BulkUndo`, `showUndoSnack`, `writeDecision`, `undoDecision`, `applyAllRemaining`, `undoBulk`, `LaunchedEffect(undoSnack)`, the `SnackbarHost` block, `undoSnackOutcome`, `reviewOutcomeOf`, `canUndoNow`, `reviewAgainEnabled`; `TagReviewUndoTest`.

## Files
`ui/TagReviewScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewUndoTest.kt` (one new source-contract test; every existing assertion stays).

## Steps
1. **Remove** `showUndoSnack` and its three call sites (the Apply handler, the Reject handler, and the landed-result branch of `writeDecision`); the `keptMessage` value; the `outcome` field of `ReviewUndoSnack`; in `writeDecision` the "turn the snackbar into Kept" and "dismiss the undo snack" branches (the failure branch keeps `bringBack` and the apply-failed message); in the `SnackbarHost` content the single-garment case of `undoEnabled` (`listOf(snack.garmentId)`).
2. **Keep** `undoSnack` as the bulk snackbar only (`ReviewUndoSnack` keeps `serial` and `bulk`; `garmentId` may stay as the batch's first id). `LaunchedEffect(undoSnack)` then only shows "N garments updated" with Undo; its Undo calls `undoBulk`.
3. **Failures.** Before `showSnackbar(applyFailedMessage)` (in `writeDecision` and `applyAllRemaining`) call `snackbarHostState.currentSnackbarData?.dismiss()`, as the revert-failed branches already do, so a standing bulk snackbar does not hold the failure for 4 s.
4. A single decision no longer replaces a standing bulk snackbar; the bulk Undo stays valid for each garment through `canUndoNow` (it acts only on garments that can be undone).
5. **Unchanged:** `undoDecision`, `canUndoNow`, `reviewAgainEnabled`, `bulkRevert`, `undoBulk`, `ReviewedRow` and the "N reviewed ▾" link, `undoSnackOutcome`, `reviewOutcomeOf`, `ReviewOutcome`, the strings `review_snack_applied` and `review_snack_kept` (the Reviewed rows show them), `doneFinishes`, `shouldFinishTagReview`. No string is removed. Update the comments that still describe a per-decision snackbar.
6. **Test.** In `TagReviewUndoTest` add one source-contract test: the text of `TagReviewScreen.kt` does not contain `showUndoSnack(` or `keptMessage`, and `ReviewUndoSnack` has no `outcome`. Read the file with the helper the test already has.
7. Receipt: how a single decision can still be taken back (Reviewed › Review again, Edit changes), and that REV-DONE's "the last Undo snackbar stays over the completion view" now holds only for the bulk snackbar.

## Tests
V-FB3-NOSNACK-1, -2 (CI); V-FB3-NOSNACK-3 (REV); V-FB3-NOSNACK-D (Manu).

## Done when
Rows pass; CI green.

## Do not
Remove the bulk snackbar or any failure message, change the Reviewed section, the finish guards or the write path, or delete `review_snack_*`.
