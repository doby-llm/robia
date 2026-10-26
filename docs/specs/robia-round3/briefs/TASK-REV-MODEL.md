# TASK-REV-MODEL — What a review card says and what applying does (pure)

## Goal
F12 + F21 (R3-DEC-052 to R3-DEC-054): one pure file defines the vocabulary of the new tag review: the changes of a garment (add, remove, swap, locked), their fixed order, the counted primary button, the quiet "no occasions" note, and the two operations that touch user data, `applyDelta` and `revertDelta`. Nothing here writes; REV-SESSION keeps the state, REV-CARD shows it, REV-SAVE writes with it.

## Read first
`R3-DEC-052` to `R3-DEC-054`; `suggestions/ReviewPlanner.kt` (`ReviewProposal(garmentId, before, proposed, kind)`, `ProposalKind`, and which proposals exist: `addedCategory`, `deletedOccasion`, `deletedCategory`, `addedOccasion`); `ui/TagReviewScreen.kt` (`kindIds` ~359 is the group's tag ids, `commitUpdatedItems` ~408 is today's write: `afterTags.filter { in proposed }` on a snapshot copy; `restoredGarmentTags` ~246 is the compare-and-restore pattern to follow); `core/model/ClothingModels.kt` (`ClothingItem.tags`, `GarmentTag.categoryId`); `mockups/review-redesign-3/build.py` (`ordered`, `footer`, `none_left`, `item_chip`).

## Files
New `suggestions/ReviewChanges.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/ReviewChangesTest.kt`. No existing file.

## Steps
1. Types (public to the module, no Android imports):
   ```kotlin
   sealed interface ReviewChange {
       val key: String                                   // "add:<id>", "rem:<id>", "swap:<from>:<to>"
       data class Add(val tagId: String) : ReviewChange
       data class Remove(val tagId: String, val locked: Boolean) : ReviewChange
       data class Swap(val fromId: String, val toId: String, val fromLocked: Boolean) : ReviewChange
   }
   data class ReviewChanges(val changes: List<ReviewChange>, val needsChoice: Boolean)
   ```
2. `reviewChangesFor(proposal, groupTagIds, isCategoryGroup, existingTagIds, label, collator)`: inside the group only, removed = `before − proposed`, added = `proposed − before`. `locked` = the removed tag is not in `existingTagIds` (it was deleted before the review opened, R3-DEC-054 item 2). Category group with exactly one removed and one added → one `Swap`; otherwise single `Remove`/`Add`. Adds of tags not in `existingTagIds` are dropped. `ProposalKind.ChooseManually` → `needsChoice = true` (its changes are the locked removal only). Tags of other groups are ignored. Order: swaps, then removals, then additions; each sorted by `label(tagId)` with a primary-strength `Collator` (accents and case ignored), ties by id.
3. `primaryButtonFor(changes, skipped, chosenTagId): ReviewButton` with `Apply(n)`, `ApplyPartial(on, of)`, `KeepAsIs`, `GotIt`, and `val showsReject` (true for the first two). Units are the unlocked ones: each `Add`, each unlocked `Remove`, each `Swap` (its replacement half is the switch when `fromLocked`), plus the chosen tag of a `needsChoice` card; a unit is on unless its key is in `skipped`. `of == 0` and no `needsChoice` → `GotIt`; `of == 0` with `needsChoice` and no choice → `KeepAsIs`; `on == 0` → `KeepAsIs`; `on == of` → `Apply(of)`; else `ApplyPartial`.
4. `noTagsLeft(isOccasionGroup, beforeGroupIds, changes, skipped, chosenTagId)`: true only when `isOccasionGroup`, the garment has at least one occasion now (`beforeGroupIds` not empty) and none remains after the active units (locked removals count as removed).
5. Writes: `data class TagDelta(val garmentId: String, val remove: Set<String>, val add: Set<String>) { val isEmpty }`; `deltaFor(garmentId, changes, skipped, chosenTagId)` = the active units (a `Swap` is remove `from` + add `to`; locked removals are never in a delta, so a deleted-occasion card has an empty delta). `data class AppliedDelta(val garmentId: String, val removed: Set<String>, val added: Set<String>)` is what actually changed. `applyDelta(live: ClothingItem, delta, existingTags: List<GarmentTag>, categoryId: String, now: Long): ApplyResult(item: ClothingItem?, applied: AppliedDelta)`:
   - an archived `live` → null item, empty applied;
   - effective removals = delta.remove ∩ the live tags of the group; effective adds = delta.add that exist in `existingTags`, belong to `categoryId`, and are not on the garment;
   - category group: the add applies only when no category tag remains after the effective removals (the garment changed since the proposal → nothing), and the removal applies only together with its add (or when the delta has no add);
   - other groups' tags and every other field stay untouched except `tags` and `updatedAtEpochMillis = now`; nothing effective → null item.
6. `revertDelta(live, applied, existingTags, categoryId, now)` = `applyDelta` of the inverse (remove `applied.added`, add `applied.removed`) with the same guards, so a tag the user changed in between is not touched. Apply then revert gives back the original tag set.
7. Tests as the V-REV-MODEL rows, including the proposals the planner really makes (deleted category with and without a winner, `ChooseManually`, deleted occasion, added occasion with several adds, category swap).

## Tests
V-REV-MODEL-1 to V-REV-MODEL-6 (CI).

## Done when
All rows pass; no existing file changed; CI green. The receipt lists the final signatures and says how locked and unlocked removals are told apart.

## Do not
Touch `ReviewPlanner.kt`, `TagReviewScreen.kt` or any UI; read a repository; add state (REV-SESSION); invent chips for the restore merge (R3-DEC-054 item 3).
