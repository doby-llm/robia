# TASK-REV-SESSION — Decision state of a tag review (pure)

## Goal
F12 + F21 (R3-DEC-050, R3-DEC-053, R3-DEC-054 items 4–6): the review needs per-tag decisions (new state; today a card is accepted or discarded whole) and per-garment decisions that survive Undo, "Review again", bulk apply and leaving halfway. This task is that state as an immutable value with pure transitions, so every rule of the blocking list is a JVM test before any screen uses it.

## Read first
`R3-DEC-050`, `R3-DEC-053`, `R3-DEC-054`; `briefs/TASK-REV-MODEL.md` and the merged `suggestions/ReviewChanges.kt` (types, `primaryButtonFor`, `deltaFor`); `ui/TagReviewScreen.kt` (today's `acceptedIds`/`discardedIds`/`hiddenIds`, `shouldAutoCommit`, `tagReviewOpenAcceptableCount`: the behaviour to keep: a manual choice never joins a bulk, the review finishes only after the scan).

## Files
New `ui/TagReviewSession.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewSessionTest.kt`.

## Steps
1. State (internal, no Compose): `ReviewCardState(garmentId, changes: ReviewChanges, skipped: Set<String>, chosenTagId: String?, decision: ReviewDecision)`; `ReviewDecision` = `Pending`, `Applied(intended: TagDelta, applied: AppliedDelta?)` (`applied == null` means the write is in flight), `Rejected`; `TagReviewSession(cards: List<ReviewCardState>, decisionOrder: List<String>)`. Cards stay in scan order forever; the pending and reviewed lists are views.
2. Transitions, each returning a new session and ignoring a call that does not apply (no exceptions): `withCard` (idempotent by garment id); `toggle(garmentId, key)` (Pending only; a locked removal never toggles; a `Swap` with `fromLocked` toggles only the replacement); `choose(garmentId, tagId?)` (a `needsChoice` card only); `apply(garmentId)` → the session and the `TagDelta` to write or null: the button decides (`Apply*` → `Applied(delta, null)`, `KeepAsIs` → `Rejected`, `GotIt` → `Applied(empty delta, empty applied)` and no write); `reject(garmentId)` → `Rejected` (the card must offer Reject, i.e. the button shows it); `appliedResult(garmentId, applied)` (only while `Applied(_, null)`); `applyFailed(garmentId)` (only while `Applied(_, null)` → Pending); `revert(garmentId)` → the session and the `AppliedDelta` to reverse or null (Applied with a result, or Rejected → Pending with `skipped` and `chosenTagId` kept; refused while the write is in flight; none for `Rejected` and for empty deltas).
3. Queries: `leftCount`, `pending` (scan order), `reviewed` (most recent decision first), `appliedGarmentCount` (non-empty intended delta, in flight included), `bulkPlan()` = `BulkPlan(garmentIds, deltas, tagsAdded, tagsRemoved, garments)` over the Pending cards that have something to apply (`Apply*`), to dismiss (`GotIt`) or to keep (`KeepAsIs`); a `needsChoice` card without a choice is left out; `tagsRemoved` counts unlocked removals and swap halves only; `applyBulk()` decides exactly the planned cards and returns the deltas to write; `tallies()` = garments updated, tags added, tags removed, garments kept; `isComplete(scanning)` = not scanning, at least one card, none Pending; `leaveKind(appliedCount)` = `Discard` for 0, `Leave` otherwise (`enum class LeaveKind`).
4. Tests as the V-REV-SESSION rows. Include sequences: apply → result → revert → apply again (the second delta equals the first, one write each); apply → revert while in flight (refused); apply → `applyFailed`; bulk twice (the second plan is empty); toggle after revert keeps the user's choices; reject → revert → apply.

## Tests
V-REV-SESSION-1 to V-REV-SESSION-4 (CI).

## Done when
All rows pass; no existing file changed; CI green.

## Do not
Add Compose or Android types, write anything, call a repository, or change `suggestions/ReviewChanges.kt` (stop and ask if the model lacks something).
