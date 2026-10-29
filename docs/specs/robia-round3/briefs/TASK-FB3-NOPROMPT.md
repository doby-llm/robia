# TASK-FB3-NOPROMPT — No review question when nobody holds the deleted tag

## Goal
ui3 F29 (REQ-FB-29, R3-DEC-064). Deleting a category or occasion that no garment has still asks "Review garments?". The review would be empty, so the question must not appear.

## Read first
`artifacts/inv-f3-b.md` F29; `R3-DEC-064`; `ui/TagReviewScreen.kt`: `TagReviewRequest`, `affectedGarmentCount`, `offerTagReviewRequest`; `ui/ReviewScaffold.kt` `hasReviewableGarments`; `ui/RobiaApp.kt` `deleteTagAndOfferReview` and `offerTagReviewAfterWrite` (read only); `suggestions/ReviewPlanner.kt` `TagChangeSet`, `TagOperation`, `ReviewPlanner.deletedOccasion` / `deletedCategory` (both return null when no garment holds a touched tag); `ReviewDiscardTest` (it calls `offerTagReviewRequest`); `TagReviewScanTest`; `RegressionSourceContractTest` (the assertions on `offerTagReviewRequest`: snapshot read before `write()`).

## Files
`ui/TagReviewScreen.kt` (`hasAffectedGarments` and `offerTagReviewRequest` only); `app/src/test/java/com/gusanitolabs/robia/ui/ReviewDiscardTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (check only).

## Steps
1. Add `internal fun hasAffectedGarments(changeSet: TagChangeSet, garments: List<ClothingItem>): Boolean` next to `affectedGarmentCount`. `TagOperation.Deleted`: `garments.any { !it.isArchived && it.tags.any { tag -> tag.id in changeSet.touchedTagIds } }`. Every other operation: `true` (unchanged behaviour).
2. In `offerTagReviewRequest`, the final guard becomes `changeSet == null || garments == null || !hasReviewableGarments(garments, ReviewKind.Tag) || !hasAffectedGarments(changeSet, garments)` → `null`. Order stays: the snapshot is read first, then `write()`, which always lands. The snapshot is the pre-write read from Room, so a deleted tag is still on the garments that hold it (the TagReviewRequest KDoc says so): the check is a pass over an in-memory list, no model.
3. Run `RegressionSourceContractTest`: its assertions grep the body of `offerTagReviewRequest` (`val garments = try {`, `readGarments()` before `write()`); they must still pass without edits. If a pinned text moves, rewrite that assertion with equal strength and say so.
4. **Tests.** `TagReviewScanTest` (helper cases): Deleted category held by no garment → false; Deleted occasion held only by an archived garment → false; held by one active garment → true; held by an active and an archived garment → true; Added, Renamed, RestoredDefault → true with any garment list, even empty. `ReviewDiscardTest` (offer cases): Deleted tag nobody holds → null and `write` ran exactly once; held by an active garment → the request; Added with any active garment → the request (unchanged); a failing snapshot → null and the write still lands (existing).
5. **Receipt flag for Manu:** a tag held only by archived garments now also gives no prompt (the scan reads active garments only); the write still strips the tag from them and there is no Discard for those. `affectedGarmentCount` (the header count) still counts archived garments; not changed.

## Tests
V-FB3-NOPROMPT-1, -2 (CI); V-FB3-NOPROMPT-3 (REV); V-FB3-NOPROMPT-D (Manu).

## Done when
Rows pass; CI green; the receipt states the archived-only case.

## Do not
Cover Added, Renamed or Restored tags (they need the scan), change `ReviewPlanner`, `RobiaApp.kt` or the colour review's guard, or reorder the snapshot read and the write.
