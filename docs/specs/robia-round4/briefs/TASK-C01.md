# TASK-C01 — Collections data layer and upgrade-safe sync equivalence

## Goal
Collections are stored as tags of a new system category `collection` (SPEC R4-D01). Existing installs must get the
category without a Room migration, and the upgrade must NOT show the Drive merge proposal to multi-device users:
today `equivalentForUpload` compares the full taxonomy (`sync/SnapshotEquivalence.kt:18-19,52-63`), so a new empty
category row on the upgraded phone makes it differ from the remote written by another phone → `decideMerge` rule 6
`Propose` (`sync/MergeDecision.kt:51-74`; analysis in `artifacts/inv-r4-collections-sites.md` §3.4). The members
screen (C05) also needs one transactional write that changes one tag's links without touching the garments' other
tags (`clearTags`/`clearTagsForItems` wipe ALL tags of a garment: `data/local/Dao.kt:336,447`).

## Read first
SPEC §2 (R4-D01, D03, D04, D08, D09), §4 REQ-COL-01/04/05/10. `core/model/DefaultTags.kt:4-9`;
`data/local/Dao.kt` `interface TagDao` (:410-), `applyMainColorChange` (:495-516, precedent), `itemRefsForTag` (:529),
`insertItemTagRefs` (:450), `queuedForSync` (:612-623 — do NOT reuse it, it resets photo-guard fields);
`data/local/Entities.kt:14-39` (column names); `data/WardrobeRepository.kt:66-82`; `data/LocalRepositories.kt:167-`;
`sync/SnapshotEquivalence.kt`; `sync/MergeDecision.kt`; `sync/WardrobeSyncOutboxProcessor.kt` `mergeSnapshots`
(:1888-1960) and `hasUserData` (:1837); `sync/LocalWardrobeSyncSnapshotRepository.kt:330,355` (link revision =
garment `syncRevision`); tests `DefaultCategorySeedingTest.kt`, `SnapshotEquivalenceTest.kt`, `MergeDecisionTest.kt`.

## Steps
1. `DefaultTags.categories`: append `TagCategory(id = "collection", name = "Collections", sortOrder = 60, isSystem = true)`.
   No default tags for it. In new file `core/model/CollectionMembership.kt` add `const val COLLECTION_CATEGORY_ID = "collection"`
   and use it in all new code. Run through `DefaultTagsTest`/seeding tests mentally; adapt only a category-count
   assertion, with equal strength.
2. Same file, pure:
   - `data class MembershipPlan(val add: List<String>, val remove: List<String>)` with `val changed: List<String> get() = (add + remove)`
   - `fun planTagMembership(currentMembers: Set<String>, add: Set<String>, remove: Set<String>): MembershipPlan` —
     `add` keeps only non-members, `remove` only members, an id present in both inputs is ignored, outputs sorted.
3. `TagDao` in `Dao.kt`, next to `applyMainColorChange`, three queries plus one transaction:
   - `deleteTagRefsForItems(tagId: String, itemIds: List<String>)` — `DELETE FROM clothing_item_tags WHERE tag_id = :tagId AND clothing_item_id IN (:itemIds)`
   - `touchItemsForMembershipSync(itemIds: List<String>, now: Long)` — `UPDATE clothing_items SET sync_status = 'Queued', sync_revision = :now, sync_dirty_at_epoch_millis = :now, sync_failure_message = NULL WHERE id IN (:itemIds) AND is_archived = 0`
   - `activeItemIds(ids: List<String>): List<String>` — `SELECT id FROM clothing_items WHERE id IN (:ids) AND is_archived = 0`
   - `@Transaction suspend fun setTagMembership(tagId: String, add: Set<String>, remove: Set<String>, now: Long): Int`:
     read members with `itemRefsForTag`, restrict `add` and `remove` to `activeItemIds`, `planTagMembership`,
     `insertItemTagRefs(plan.add…)`, `deleteTagRefsForItems`, `touchItemsForMembershipSync(plan.changed)`, return
     `plan.changed.size`. Never call a query with an empty list (skip it).
   Verify every column name against `Entities.kt` (`@ColumnInfo`) before writing the SQL and fix names that differ.
4. **Gate (R4-D04 fallback):** run `grep -n "updatedAtEpochMillis" app/src/main/java/com/gusanitolabs/robia/sync/*.kt`.
   If any merge/conflict/equivalence DECISION compares garment `updatedAtEpochMillis` (rather than `revision`) to choose
   a winner, ALSO set `updated_at_epoch_millis = :now` in the UPDATE. Write the grep result and your verdict in the receipt.
5. `TagRepository`: `suspend fun setTagMembership(tagId: String, addGarmentIds: Set<String>, removeGarmentIds: Set<String>, nowEpochMillis: Long): Int`;
   `LocalTagRepository` delegates to the DAO. Check for other `TagRepository` implementations (`grep -rn ": TagRepository" app/src`)
   and add the method to each fake (compile-forced, no assertion change).
6. `SnapshotEquivalence.kt`, inside `equivalenceKey()` only: filter `taxonomies.categories` with
   `isIgnorableEmptyDefault(category, tags)` = a `DefaultTags.categories` entry has the same id, name, sortOrder and
   isSystem, the record is not archived, and no tag of the same snapshot has that `categoryId`. Because it is inside the
   key, it applies to both sides. Do NOT change `mergeSnapshots`, `hasUserData`, `remoteAlreadyHolds` logic or `decideMerge`.
   One KDoc line: "An upgrade seeds new empty default categories; they are not a user difference."
7. Tests V-C01-1 … V-C01-6 (verification-matrix). V-C01-3: create in sqlite-jdbc memory the `clothing_items` columns the
   SQL uses (+ the photo-guard/retry columns, to assert they are unchanged) and `clothing_item_tags`; run the SQL read
   through `daoStatement`; assert per row. V-C01-5: build snapshots the way `MergeDecisionTest` already does.

## Done when
V-C01-1 … V-C01-6 green in CI; receipt states the step-4 gate result; then the independent review (V-C01-R) passes.

## Do not
Bump the Room version or `WARDROBE_SYNC_SCHEMA_VERSION`; touch UI; reuse `queuedForSync` or `clearTags*`; change merge rules.
