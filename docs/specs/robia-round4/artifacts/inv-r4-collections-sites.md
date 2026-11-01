# Collections: every site a new `collection` TagCategory touches (R4-INV, task A)

Repo: `<planning>/workers/r4-inv`, `main @ 0ceb916`. Read-only investigation; nothing edited, no gradle run.

**Citation base:** `app/src/main/java/com/gusanitolabs/robia/` is abbreviated as `…/` below unless a path is given in full. Strings live under `app/src/main/res/` (`res/values`, `res/values-es`, `res/values-de`).

**Legend:** **[C]** = read in code at the cited line. **[H]** = inference, not confirmed by running the app or tests.

---

## 0. Bottom line

- **No Room migration.** The change is data-only: a new row in `tag_categories`. DB version stays **15** (`…/data/local/RobiaDatabase.kt:22`), no column or table change, so `app/schemas` is unchanged. [C]
- **Existing installs get the row on next launch.** `seedDefaultsIfNeeded` runs from a `LaunchedEffect` (`…/ui/RobiaApp.kt:450-453`), inserts categories with `INSERT OR IGNORE` (`…/data/local/Dao.kt:477-478`, called at `…/data/LocalRepositories.kt:244`). Categories are never deleted (no `deleteCategory` in the data layer), so no tombstone blocks the seed. [C]
- **Sync and import pass a new category id through untouched.** No id whitelist exists in the JSON codec, the merge, or the import. Schema version is `2` (`…/core/model/SyncModels.kt:243`) and **must stay 2**: a higher remote schema blocks old phones (`…/sync/WardrobeSyncOutboxProcessor.kt:1098`, `:1309-1312`). [C]
- **Suggestions and embeddings never see collection tags.** Predictions, label vectors, review and semantic search all filter by `category`/`occasion` or ignore tags. [C]
- **Garment editing preserves collection membership** (AddEdit and batch edit carry every tag id, including ones the UI does not render). [C]
- **Visible automatically:** only the ManageTags tab, the add/edit/delete tag dialogs, and the tag list. Everything else (ItemDetail, AddEdit rows, AdvancedFilters, Export, share, card text, Analytics) stays **invisible** until changed. [C]
- **Wrong output today if the id is reached:** `AddEditClothingScreen.kt:1185-1196` titles it "Seasons" and makes it single-select; `BrowseFilterBar.kt:579-585` shows the raw id `collection: Not set`; `CategoryIcons.kt:14-19` uses the Checkroom icon; `ManageTagsScreen.kt:973-979` shows the English stored name in es/de. [C]
- **Delete a collection:** removes its membership rows, the tag row, and writes a tombstone. It never deletes a garment. [C]

### Risks the plan must decide (ranked)

1. **Upgrade can trigger a merge dialog for multi-device users.** The seeded empty `collection` row makes local and remote snapshots differ in `taxonomies`. The merge rules then fall through to `Propose` (the dialog) when the remote is not this phone's base and the phone has no pending work. Details in §3.4. [C for rules, H for frequency]
2. **`isSystem` on the category row.** `false` makes Analytics show a "Custom" section with the stored English name (`…/core/model/WardrobeStats.kt:63-71`). `true` hides it. Recommend `true`.
3. **`isSystem` on collection tags must stay `false`.** The delete SQL refuses to delete system tags unless their category is one of the four hardcoded ids (`…/data/local/Dao.kt:459-466`). The UI already creates tags with `isSystem = false` (`…/ui/ManageTagsScreen.kt:699`).
4. **Same-name collections auto-merge at launch** when they come from two devices (`…/core/model/TagDedupePlanner.kt:36-47`, run from `…/data/LocalRepositories.kt:257` and `:264-283`). Within one device the editor blocks duplicates (`…/ui/ManageTagsScreen.kt:611`).
5. **Deleting a collection skips the review and undo path**, so memberships are dropped immediately with no confirmation (`…/ui/RobiaApp.kt:1494-1496`, `…/ui/TagReviewScreen.kt:393-396`). [C]

---

## 1. Decisions for the planner (the code does not decide these)

| # | Decision | Options | Code facts that bear on it |
|---|---|---|---|
| D1 | `isSystem` of the category row | `true` (recommended): hidden from Analytics. `false`: auto Analytics "Custom" section with stored English name | `WardrobeStats.kt:63-71`; `AnalyticsScreen.kt:263` (`Custom -> group.customName`) |
| D2 | `isSystem` of collection tags | Keep `false` (what the UI creates) | `Dao.kt:463`; `ManageTagsScreen.kt:699` |
| D3 | Seed the category at install vs. create it lazily on first use | Seed (plan as written): the upgrade-divergence risk in §3.4 applies. Lazy: no divergence, but the Manage tab needs an add path before any row exists | `MergeDecision.kt:51-74`; `SnapshotEquivalence.kt:18-19` |
| D4 | Membership in several collections | Free in the data model: `clothing_item_tags` has only two columns (`…/data/local/Entities.kt:145-148`). Multi-select is hardcoded in UI, not stored: `TagCategory` has only `id,name,sortOrder,isSystem` (`…/core/model/ClothingModels.kt:172-177`) | `AddEditClothingScreen.kt:1196` `multi = occasion \|\| season` (the one place to change) |
| D5 | Same-name collections in one category | Keep auto-merge (union of memberships, one row survives) or exclude `collection` from `TagDedupePlanner` grouping | `TagDedupePlanner.kt:36-47` |
| D6 | "Use backup" with an older backup | Accept that local collection rows survive with zero members, or handle explicitly | `LocalWardrobeSyncSnapshotRepository.kt:260-267` (§3.3) |
| D7 | Which surfaces show collections | ItemDetail row, AddEdit row, AdvancedFilters card, Analytics section, Export field, share sheet, card text. All opt-in (§4) | Hardcoded lists, §2 |
| D8 | Delete confirmation/undo for collections | Add a confirm (member count) since no review runs | `RobiaApp.kt:1494-1496` |
| D9 | `sortOrder` of the category | Tab order is `ORDER BY sort_order, name` (`Dao.kt:411-412`). Existing: 10, 20, 40, 50 (30 unused). Use 60 for last | `DefaultTags.kt:5-8` |

---

## 2. Hardcoded category ids and `when` branches (all sites)

Legend for "collection today": **Invisible** = code ignores it; **Auto** = works with no change; **Wrong** = produces wrong output; **Falls to else** = the `else` branch runs.

### 2.1 Seed and category model

| Site | What it does | Collection today | Change |
|---|---|---|---|
| `core/model/DefaultTags.kt:5-8` | Seeds `category`, `season`, `occasion`, `location` (`isSystem=true`, sortOrder 10/20/40/50). No `care`, no `fit`. | No row → not seeded [C] | Add `TagCategory(id="collection", name="Collection", sortOrder=60, isSystem=true)` (D1, D9) |
| `core/model/DefaultTags.kt:12-43` | Default tags per category. None for collection. | n/a | None (collections start empty; the tab shows `no_tags_in_category`) |
| `core/model/DefaultTags.kt:68-74` `isCustomOrModifiedDefault(category)` | `true` when id is not a default, or name/sortOrder/isSystem differ | Once seeded, equal → not "custom" [C] | None, provided the seed row matches the default exactly |
| `core/model/DefaultTags.kt:75-82` `isCustomOrModifiedDefault(tag)` | Tag not in defaults → `true` (customised) | A user collection tag counts as user data (correct) [C] | None |
| `data/LocalRepositories.kt:173` `observeCategories` filter `"care"` | Hides a legacy id | Shown [C] | None |
| `data/LocalRepositories.kt:446` `filterNotCare` | Hides legacy `care` tags | n/a | None |
| `data/local/RobiaDatabase.kt:76-78` (migration 1→2), `:93-97` (3→4) | Deletes `care` and `fit` categories and their tags | n/a: precedent for removing categories by migration | None |
| `data/local/RobiaDatabase.kt:144-145` (migration 6→7) | Inserts `season-fall` into `season` | n/a | None |
| `core/model/ClothingModels.kt:172-177` `TagCategory` | `id, name, sortOrder, isSystem` only. No multi/single flag | Cardinality is hardcoded in UI (D4) [C] | Decide D4 |

### 2.2 Persistence and delete

| Site | What it does | Collection today | Change |
|---|---|---|---|
| `data/local/Dao.kt:459-466` `deleteEditableTag` | `DELETE … WHERE id=:id AND (is_system=0 OR category_id IN ('category','season','occasion','location'))` | Custom collection tags (`is_system=0`) deletable [C]. A collection tag stored with `is_system=1` is **silently not deleted** (returns 0) | Keep collection tags `is_system=0`, or add `'collection'` to the list |
| `data/local/Dao.kt:650` `metadataSyncTable` | Maps entity types `tag_category`/`category` | Generic [C] | None |
| `data/local/Entities.kt:70-80` FK `garment_tags.category_id → tag_categories.id`, CASCADE | Tags need their category row | Category row must exist first; import does this (§3.2) [C] | None |
| `sync/LocalWardrobeSyncSnapshotRepository.kt:696-697`, `sync/WardrobeSyncOutboxProcessor.kt:2138-2139` | Tombstone entity-type sets (`tag_category`, `category`, `garment_tag`, `tag`, `main_color`…) | Not category ids [C] | None |

### 2.3 Suggestions, review and dedupe

| Site | What it does | Collection today | Change |
|---|---|---|---|
| `suggestions/SuggestionService.kt:115`, `:121` `tagLabels(tags, "category"/"occasion")` | Only these two label sets are embedded and scored | Never predicted [C] | None |
| `suggestions/SuggestionService.kt:146`, `:187-194` | Selected category; label vector per tag | Collection never embedded [C] | None |
| `suggestions/CategoryScorer.kt`, `suggestions/OccasionSelector.kt` | Take label lists; no tag or category ids of their own (grep: no `tags` or `categoryId` references) | Not reached [C] | None |
| `suggestions/EmbeddingRecipe.kt:28` `SUGGEST_OCCASIONS_DEFAULT_OFF` | Default tag ids only | n/a | None |
| `suggestions/EmbeddingRecipe.kt:31-` `DEFAULT_LABEL_DESCRIPTIONS` | Default ids only | Collection falls back to `labelTextFor` (name) if ever embedded (`EmbeddingRecipe.kt:66-72`) | None |
| `suggestions/EmbeddingRecipe.kt:76-80` `garmentTextInput` | Name and notes only | Tags never in the text vector [C] | None. **Caveat:** putting tags into the recipe needs a new `RECIPE_ID`, since vectors from another model/recipe pair are ignored (`EmbeddingRecipe.kt:5`, `:7-8`) [C] |
| `suggestions/ReviewPlanner.kt:89-92` `proposalFor` | `Deleted & OCCASION → deletedOccasion`; `Deleted → deletedCategory`; `CATEGORY → addedCategory`; **`else → addedOccasion`** | Unreachable: `TagReviewScreen` never builds a change set for collection (§2.4). Latent risk | Keep collection out of `ReviewedTagKinds`. If ever reviewed, add an explicit branch |
| `suggestions/ReviewPlanner.kt:195-196` constants `CATEGORY`, `OCCASION` | Used above | n/a | None |
| `suggestions/ReviewChanges.kt:65`, `:218` `CATEGORY_GROUP` | One-category rule only for `category` | Collection gets multi semantics (correct for D4) [C] | None |
| `suggestions/DuplicateScan.kt:46`, `:481` `categoryOf` | Duplicate blocks keyed by `category` tag | Collection ignored [C] | None |
| `ui/DuplicateCalibrationScreen.kt:209-210` | Category tag for calibration signature | Ignored [C] | None |
| `suggestions/SearchController.kt`, `suggestions/SearchRanker.kt` | No tag references at all (grep) | Tags not used by search [C] | None |
| `suggestions/EmbeddingBackfill.kt` | No tag references (grep) | n/a [C] | None |
| `ui/TagReviewScreen.kt:97-101` `TAG_KIND_CATEGORY`, `ReviewedTagKinds = {category, occasion}` | Gate for the post-edit review | Collection never reviewed [C] | None (or extend deliberately) |
| `ui/TagReviewScreen.kt:425-427` `savedTagChangeSet`, `:438-441` `deletedTagChangeSet`, `:445-450` `restoredTagChangeSet` | Each returns `null` for non-reviewed kinds | No review offered [C] | None |
| `ui/TagReviewScreen.kt:379-396` `offerTagReviewRequest` | Always calls `write()` (line 393), then returns null if nothing to review | Save/delete proceed without review [C] | None |
| `ui/TagReviewScreen.kt:682,861,1024,1034,1233,1251` | Category/occasion-only copy and grouping | n/a | None |
| `ui/TagReviewCard.kt:319` `TAG_KIND_CATEGORY` | Identity line of a garment | Ignores collection [C] | None |
| `ui/BatchAddClothingScreen.kt:710` `selectedCategoryId` | `category` only | n/a [C] | None |
| `ui/BatchAddClothingScreen.kt:887-890`, `:926` `MODEL_PREDICTED_CATEGORIES = {category, occasion}` | Predicted tags merge only for these | Collection never auto-assigned [C] | None |
| `ui/AddEditClothingScreen.kt:292` | `category` only (suggestion input) | n/a [C] | None |
| `ui/AddEditClothingScreen.kt:234`, `:286-310` `updateUserSelectedTagIds`, `beginPhotoAnalysisGeneration` | Only `MODEL_PREDICTED_CATEGORIES` are touched | Collection membership untouched by predictions [C] | None |
| `ui/AddEditClothingScreen.kt:1964`, `:1987` `MODEL_PREDICTED_CATEGORIES` | Predicted reconciliation | Not affected [C] | None |
| `ui/BatchEta.kt:90-92` | Summary string: `category` + `occasion` | Collection not in the ETA text (Invisible) | Decide (D7) |

### 2.4 Editing (AddEdit, batch, item detail)

| Site | What it does | Collection today | Change |
|---|---|---|---|
| `ui/AddEditClothingScreen.kt:697` `selectedTagIds = existingItem?.tags…` | Loads **all** tags of the garment | Collection membership carried through edits [C] | None |
| `ui/AddEditClothingScreen.kt:951`, `:1009` save | `availableTags.filter { it.id in selectedTagIds }` | Saved [C] | None |
| `ui/AddEditClothingScreen.kt:1097-1123` `GarmentFieldRowsCard` | Renders selected tags per category it knows | Collection invisible [C] | Add a row (D7) |
| `ui/AddEditClothingScreen.kt:1137-1171` four `GarmentFieldRow`s (`category`, `occasion`, `season`, `location`) | Hardcoded rows; `categoryIconFor` per row | Collection not rendered [C] | Add a row + `valueIn` + `onClick` |
| `ui/AddEditClothingScreen.kt:1185-1196` `TagPickerSheet` title `when`: `category`, `location`, `occasion`, **`else → R.string.metadata_season`** | Title | **Wrong**: "Seasons" if reached [C] | Add `"collection" -> R.string.metadata_collections` (or similar) |
| `ui/AddEditClothingScreen.kt:1196` `multi = categoryId == "occasion" \|\| categoryId == "season"` | Multi-select rule | **Wrong**: collection would be single-select with a "None" row [C] | Add `"collection"` |
| `ui/AddEditClothingScreen.kt:1261` `forCategory` | Filter and sort by localised label | Generic [C] | None |
| `ui/GarmentFieldRows.kt:189-240` `TagPickerSheet` | Generic: `multi` passed in; `"None"` row only when single | Generic [C] | None |
| `ui/BatchAddClothingScreen.kt:912` `toBatchDraftFromExisting` `selectedTagIds = tags.map(GarmentTag::id)` | Copies every tag id | Membership preserved [C] | None |
| `ui/RobiaApp.kt:3234-3240` `BatchEditClothing` route | Renders `AddEditClothingScreen` | Same as AddEdit [C] | None beyond AddEdit |
| `ui/ItemDetailScreen.kt:539-566` four `GarmentFieldRow`s | Hardcoded: category, occasion, season, location, then fit | Collection not shown [C] | Add a row (D7) |
| `ui/ItemDetailScreen.kt:528-532` `valueIn` | Filters by `categoryId` | Generic [C] | None |
| `ui/ItemDetailScreen.kt:608-641` share metadata | Four `GarmentShareMetadata` (category, season, occasion, fit) + `location` | Collection not in share | Decide (D7) |

### 2.5 Browse, filters, stats, export, icons

| Site | What it does | Collection today | Change |
|---|---|---|---|
| `ui/BrowseFilterBar.kt:125-177` `BrowseFilterState` (`selectedTagIds`, `selectedTagCategories`, `matches`) | Matching groups by category id; AND across categories, OR within | Generic: works for collection [C] (`:160-175`) | None |
| `ui/BrowseFilterBar.kt:517-519` active chips | Labels via `localizedTagLabel` | Generic [C] | None |
| `ui/BrowseFilterBar.kt:532` "Not set" chip | `categoryTitleRes(categoryId) ?: categoryId` | Would show raw `collection: Not set` [C] | Add branch in `:579-585` |
| `ui/BrowseFilterBar.kt:579-585` `categoryTitleRes` `when`: category, season, occasion, location; else null | Chip title | Raw id if reached [C] | Add branch |
| `ui/BrowseFilterBar.kt:601-604`, `:615-630` unknown-tag pruning | Uses `availableTags` | Generic [C] | None |
| `ui/AdvancedFiltersScreen.kt:124-127` four `*Tags` lists | Lists per section | Collection not listed [C] | Add `collectionTags` |
| `ui/AdvancedFiltersScreen.kt:194-233` four `TagFilterCard(categoryId = …)` | Filter cards in fixed order | Invisible [C] | Add a card (D7) |
| `ui/AdvancedFiltersScreen.kt:310-` `TagFilterCard` | Generic: `categoryIconFor`, `activeFilterCountIn` | Generic [C] | None |
| `ui/AdvancedFiltersScreen.kt:439-444` `tagChipSlots` | Generic: sort, first 8, "+N more", "Not set" | Generic [C] | None |
| `ui/BrowseCardText.kt:16-18` constants; `:32` `cardTitle`; `:39` `cardMeta` (occasion + season); `:61` `duplicateRowLine` | Card and tile text | Collection invisible on cards [C] | Decide (D7) |
| `ui/CategoryIcons.kt:14-19` `categoryIconFor` `when`; `else → Icons.Outlined.Checkroom` | Icon per category | Falls to else: **Checkroom icon** [C] | Add `"collection"` icon branch |
| `ui/ManageTagsScreen.kt:131` `tabIds` | Tabs = visible categories + colours | **Collection tab appears automatically** [C] | None |
| `ui/ManageTagsScreen.kt:973-979` `TagCategory.localizedName()` `when`; `else → name` | Tab header title | **Falls to else**: shows stored English `name` in es/de [C] | Add branch + string |
| `ui/ManageTagsScreen.kt:982-988` `dotColor()` `when`; `else → surfaceVariant` | Dot colour for tags | Neutral grey dot [C] | Optional colour branch |
| `ui/ManageTagsScreen.kt:1008` `RestorableTagCategoryIds` | Restore-default button only for the three default categories | No restore button (correct) [C] | None |
| `ui/ManageTagsScreen.kt:371` `allowsNewTags = category.id != "season"` | Add button | Shown for collection [C] | None |
| `ui/ManageTagsScreen.kt:663` `state.categoryId == "category"` | Suggest-occasions switch | Hidden for collection [C] | None |
| `core/model/WardrobeStats.kt:56-61` system groups for `category`, `occasion`, `season`, `location` | Analytics bars | Collection not counted [C] | Add a group if wanted |
| `core/model/WardrobeStats.kt:63-71` custom groups: `filterNot(isSystem)` | Non-system categories → `WardrobeStatsSection.Custom` with `customName = category.name` | `isSystem=false` would show an English "Collection" section [C] | Set `isSystem=true` (D1) |
| `ui/AnalyticsScreen.kt:257-263` `sectionTitle` `when` (exhaustive over the enum) | Section titles | Compile-forced only if a new enum value is added [C] | None unless D1 adds one |
| `media/GarmentShareExporter.kt:173-182`, `core/model/ClothingModels.kt:282-289` `ExportField` enum, defaults | Export fields | No collection field [C] | Add enum value (D7) |
| `ui/ExportScreen.kt:205` `ExportField.entries.forEach` | Renders all enum values | Generic [C] | None |
| `ui/ExportScreen.kt:339-347` `labelRes` `when` (exhaustive over the enum) | Labels | Compile-forced if the enum grows [C] | Add label |
| `ui/TagLocalization.kt:14-16`, `:36` `localizedTagLabel` | Custom tags use stored name; defaults use strings | Collection tags show stored names [C] | None |
| `ui/RobiaApp.kt:1118-1123` `isFirstRunCloudSetup` | `tagCategories.none(isCustomOrModifiedDefault)` | Equal seed → still first-run [C] | None |

---

## 3. Seeding, tombstones, dedupe, Room, sync, import/export

### 3.1 Seeding and restore-default

- Seed path: `RobiaApp.kt:450-453` → `LocalRepositories.kt:242-257` → `Dao.kt:477-478` (`@Insert(onConflict = IGNORE)`). Runs on every app launch, so existing installs get the new row. [C]
- The seeded row is built by `TagCategory.toEntity()` (`LocalRepositories.kt:394`) with defaults `syncStatus = Synced`, `syncRevision = 0` (`data/local/Entities.kt:54-66`). It is **not queued** for upload. It reaches Drive on the next full-snapshot write, because `getCategoriesForSync` exports every row (`Dao.kt:415`, `LocalWardrobeSyncSnapshotRepository.kt:68`). Whether a snapshot write happens when nothing else is pending: [H].
- `restoreDefaultTags(categoryId)` (`LocalRepositories.kt:221-236`) is reachable only from the button in `RestorableTagCategoryIds` (`ManageTagsScreen.kt:331`, `:1008`). Collection is excluded, so it is unreachable. [C]
- No `deleteCategory` / `addCategory` exists anywhere in the data layer (grep). Users cannot create or delete categories. [C]

### 3.2 Room

- Version 15 (`RobiaDatabase.kt:22`), `exportSchema = true`. Adding only a row needs no migration. A migration would be needed only for a new column, such as a collection colour or description. [C]
- FK `garment_tags.category_id → tag_categories.id` with CASCADE (`Entities.kt:70-80`). The category must exist before its tags: import upserts categories first (`LocalWardrobeSyncSnapshotRepository.kt:124-128`, in the same transaction). [C]

### 3.3 Tombstones, dedupe, restore

- Built-in seeding skips tombstoned ids (`core/model/TagDedupePlanner.kt:62-68`). Collections have no built-ins, so this path does not apply. [C]
- Dedupe groups by `(categoryId, dedupeKey(name))` (`TagDedupePlanner.kt:36`). `dedupeKey` keeps accents (`core/model/TagNameRules.kt:21-23`). The lowest id survives, built-ins first. Custom duplicates merge into it, which repoints `clothing_item_tags` (`Dao.kt:547-579`). A ghost keep (tombstone not older than its revision) blocks the merge (`TagDedupePlanner.kt:43`). [C]
- Dedupe runs at launch via `seedDefaultsIfNeeded` → `mergeDuplicateTags(null)` (`LocalRepositories.kt:257`, `:264-283`). [C]
- Editor duplicate check: `TagNameRules.conflicts` (`TagNameRules.kt:26-29`), which is accent- and case-insensitive, against sibling labels in the same category, in all three locales (`ManageTagsScreen.kt:606-611`). Mismatch with dedupe: `Mäntel` is blocked in the editor but not merged. [C]
- `replaceLocalWithRemote` ("Use backup", `LocalWardrobeSyncSnapshotRepository.kt:260-267`) deletes all items, item-tag refs and embeddings. It **does not delete** tags or categories. Local-only collection rows therefore survive with zero members. Same as today for custom tags. [C]

### 3.4 Sync merge, schema gate, upgrade divergence

**Filter (drop tags of inactive categories):** `LocalWardrobeSyncSnapshotRepository.kt:661-677` (`withoutTombstonedTaxonomy`, applied at import `:114`); `WardrobeSyncOutboxProcessor.kt:1894-1915` (`mergeSnapshots`). Tags are kept only if their category is in the merged, non-tombstoned category list. Collection tags pass because the category row is always exported. [C]

**Codec:** `GoogleDriveWardrobeRepository.kt:1955-1959` encodes categories and tags as objects; `:1978-1991` decodes with no id check; `jsonToTagCategory` at `:2106`. Any id round-trips. JSON shape is unchanged: the only difference is one more row, so no new field. [C]

**Schema gate:** `SyncModels.kt:243` `WARDROBE_SYNC_SCHEMA_VERSION = 2`. Old phones refuse remote schema > 2 (`WardrobeSyncOutboxProcessor.kt:1098`, `:1309-1312`). Schema is also compared for "same wardrobe" (`MergeDecision.kt:67-68`; `SnapshotEquivalence.kt:46`). **Do not bump it.** [C]

**Upgrade divergence (the main sync risk):**

- `decideMerge` (`sync/MergeDecision.kt:51-74`) evaluates in order: (1) remote has no user data → upload; (2) own lineage (writer = this device, or base revision matches) → upload if pending else no-op; (3) phone empty at start → restore silently; (4) `sameWardrobe && local.equivalentForUpload(remote)` → record base (or upload if pending); (5) `sameWardrobe && !pending && mergeSnapshots(local, remote).equivalentForUpload(remote)` → fast-forward; (6) otherwise **`Propose`** (the merge dialog). [C]
- `equivalentForUpload` compares `taxonomies` in full (`SnapshotEquivalence.kt:18-19`, `:52-63` `equivalenceKey`). A seeded but empty `collection` row therefore makes local ≠ remote. [C]
- Consequence, for an existing multi-device user whose remote revision is not their base: rule 4 fails; rule 5 fails if there is pending local work, and `mergeSnapshots` keeps the empty local row, so it fails anyway. Result: rule 6, a merge dialog. [C for the rules; H for how often this happens in practice]
- Same pattern as Gray (`LocalRepositories.kt:249-257`, seeded into existing installs). [H] that Gray can already trigger the same dialog.
- Mitigations, for the planner: (a) lazy row creation (D3); (b) exclude default-equal empty categories from `equivalenceKey`/taxonomy comparison; (c) a `MergeDecisionTest` case for an extra empty default category before shipping.

### 3.5 Import from a Drive backup: the four cases

**A. New app ← backup written by an old app (no collection row).**
- Fresh install: categories upserted (`:124-128`); the seeded collection row stays, because nothing deletes categories; the empty row is uploaded later. No failure. [C]
- Use backup: items, refs and embeddings are cleared (`:260-267`); tags and categories remain. Result: local collection rows, if any, survive with zero members, and are uploaded (D6). [C]
- Merge path: pending local collection rows stay in the merged snapshot. [C]

**B. Old app ← backup written by the new app (has collection row, collection tags, memberships).**
- Old app has no `collection` default, but its import has no whitelist: it upserts the category and tags as-is. Schema is 2 on both sides, so no gate. Round trip is **lossless**. [C]
- Old app shows a Collection tab (generic tab build, `ManageTagsScreen.kt:131`), with the stored name, English in es/de (`ManageTagsScreen.kt:973-979`). It can add and delete collection tags (`:695-699`). [C]
- Old app edits keep memberships: AddEdit loads and saves all tags (`AddEditClothingScreen.kt:697`, `:951`, `:1009`). [C]
- Old app has no collection filter, field or export. It behaves as invisible but lossless. [C]
- Analytics: if `isSystem=false`, old and new app both show the Custom section (`WardrobeStats.kt:63-71`). [C]
- Old-app device with pending edits, against a remote that has the collection row: rule 4 fails and rule 5 fails with pending work, so rule 6, the merge dialog. Mixed-version risk, same as §3.4. [C for rules]

**C. Zip export.** `data/WardrobeZipExporter.kt` contains no tag or category references (grep). No zip import exists (grep). Drive snapshot is the only restore path. [C]

**D. Old tags in an old backup (`care`, `fit`).** Old backups can still carry removed categories; the `care` filters (`LocalRepositories.kt:173`, `:446`; `ManageTagsScreen.kt:128-129`) hide them. Collection needs no such filter because it is a known id. [C]

### 3.6 Pending counts and sync work

- Pending-work SQL counts `tag_categories` rows with `Dirty` / `Queued` / failed states (`Dao.kt:44`, `:55`, `:68`). A seeded `Synced` row does not count. [C]
- `UpsertTags` affects no garments (`WardrobeSyncOutboxProcessor.kt:1484-1489` `affectedGarmentIds` → empty). Garments are not enqueued on tag edits or deletes. An operation with no garment ids sets `forceSnapshot = true` (`WardrobeSyncOutboxProcessor.kt:274`), so the enqueue runs a snapshot cycle: `exportSnapshot` (`:872`), then merge. Membership changes reach Drive through that full snapshot. [C]

---

## 4. Suggestions, predictions, reviews, search

- **Predictions:** `SuggestionService.kt:115`, `:121`, `:187-194` build label vectors only for `category` and `occasion`. Collection is never embedded or predicted. [C]
- **Predicted assignment:** `AddEditClothingScreen.kt:1987` and `BatchAddClothingScreen.kt:926` (`MODEL_PREDICTED_CATEGORIES = {category, occasion}`). Collection is never auto-assigned. [C]
- **Review:** gated by `ReviewedTagKinds` (`TagReviewScreen.kt:97-101`). Collection save/delete/restore return `null` (`:425-427`, `:438-441`, `:445-450`), so no review runs. The write still happens (`:393`). The one unreachable branch is `ReviewPlanner.kt:92` (`else → addedOccasion`), which would catch collection if the gate changed. [C]
- **Label text:** `labelTextFor` (`EmbeddingRecipe.kt:66-72`) is used only for reviewed kinds (`TagReviewScreen.kt:410`, `:430`; `RobiaApp.kt:3748-3751`) and label vectors (`SuggestionService.kt:191`). [C]
- **Semantic search:** `SearchController.kt` and `SearchRanker.kt` have no references to tags (grep). Garment text vector is name + notes (`EmbeddingRecipe.kt:76-80`). Collections do not affect search. Adding them would need a recipe-id bump (`EmbeddingRecipe.kt:5-8`). [C]
- **Duplicates:** keyed by category only (`DuplicateScan.kt:46`, `:481`). Collections ignored. [C]

---

## 5. Strings

### 5.1 Existing category keys (values / values-es / values-de)

| Key | values (en) | values-es | values-de | Used by |
|---|---|---|---|---|
| `category_category` | Category (`:465`) | Categoría (`:465`) | Kategorie (`:465`) | ManageTags `localizedName` `:974` |
| `category_season` | Seasons (`:421`) | Estaciones (`:421`) | Jahreszeiten (`:421`) | `:975` |
| `category_occasion` | Occasions (`:422`) | Ocasiones (`:422`) | Anlässe (`:422`) | `:976` |
| `category_location` | Locations (`:466`) | Ubicaciones (`:466`) | Orte (`:466`) | `:977` |
| `category_style`, `category_care`, `category_custom` | Style / Care / Custom (`:420,423,424`) | Estilo / Cuidado / Personalizadas | Stil / Pflege / Benutzerdefiniert | No Kotlin references found (H: unused, lint would confirm) |
| `metadata_category` | Category (`:312`) | Categoría (`:312`) | Kategorie (`:312`) | ItemDetail/AddEdit rows, export label, share |
| `metadata_season` | Season (`:313`) | Temporada (`:313`) | Saison (`:313`) | AddEdit season row (`:1153`); picker `else` fallback (`:1191`) |
| `metadata_location` | Location (`:316`) | Ubicación (`:316`) | Ort (`:316`) | AddEdit/ItemDetail |
| `metadata_occasions` | Occasions (`:317`) | Ocasiones (`:317`) | Anlässe (`:317`) | AddEdit title `:1190` |
| `choose_category` / `choose_location` | Choose category / location (`:326-327`) | Elegir (`:326-327`) | Wählen (`:326-327`) | AddEdit picker titles `:1188-1189` |
| `filter_category` | Category (`:371`) | Categoría (`:371`) | Kategorie (`:371`) | AdvancedFilters card, `categoryTitleRes` `:580` |
| `filter_season` | Season (`:372`) | Temporada (`:372`) | Saison (`:372`) | `:581` |
| `filter_occasion` | Occasion (`:425`) | Ocasión (`:425`) | Anlass (`:425`) | `:582` |
| `filter_location` | Location (`:373`) | Ubicación (`:373`) | Ort (`:373`) | `:583` |
| `analytics_section_categories` / `_locations` | Categories / Locations (`:832`, `:837`) | Categorías / Ubicaciones | Kategorien / Standorte | `AnalyticsScreen.kt:257,262` |
| `export_field_seasons` | Seasons (`:853`) | Temporadas (`:853`) | Saisons (`:853`) | `ExportScreen.kt:344` |
| `manage_tags_body` | "…categories, locations, occasions, seasons, and the main color palette." (`:237`) | Same (`:237`) | Same (`:237`) | Manage header body: must mention collections |
| `tag_review_category_noun` / `tag_review_occasion_noun` | category / occasion (`:558-559`) | categoría / ocasión | Kategorie / Anlass | Review copy only |
| `filters_category_not_set` | `%1$s: %2$s` (`:675`) | same | same | Active filter chip |
| `filters_not_set` | Not set (`:672`) | Sin definir | Nicht festgelegt | "Not set" chip |
| Generic tag strings | `content_add_tag` "Add tag" (`:402`), `add_tag_title` (`:408`), `edit_tag_title` (`:409`), `tag_count` (`:405`), `no_tags_in_category` (`:406`), `tag_name_duplicate` (`:453`) | Añadir etiqueta, etc. | Tag hinzufügen, etc. | Used for every category, including collection |

Line numbers are from `res/values/strings.xml`; the es and de files have the same key layout at the same lines (verified by grep).

### 5.2 Keys a collection category would need (by analogy)

| New key | Purpose | Analogue |
|---|---|---|
| `category_collection` (en "Collections", es "Colecciones", de "Sammlungen") | Manage tab / header title (`localizedName`) | `category_season` |
| `metadata_collections` (en "Collections", es "Colecciones", de "Sammlungen") | ItemDetail/AddEdit row label; picker title | `metadata_occasions` |
| `choose_collection` (optional) | Picker title (AddEdit `:1188-1194`) | `choose_category` |
| `filter_collection` | AdvancedFilters card title; `categoryTitleRes` | `filter_occasion` |
| `analytics_section_collections` | Analytics section title, only if D1 adds one | `analytics_section_locations` |
| `export_field_collections` | Export field label, only if D7 adds one | `export_field_seasons` |
| `no_collections_yet` (optional) | Empty tab copy instead of "No tags in this category yet." | `no_tags_in_category` |
| `add_collection_title` / `edit_collection_title` (optional) | Editor dialog titles (today "Add tag"/"Edit tag") | `add_tag_title`/`edit_tag_title` |
| `collection_name_duplicate` (optional) | Editor duplicate message (today "A tag with this name already exists.") | `tag_name_duplicate` |
| `manage_tags_body` (updated) | Mention collections in all three locales | existing key |
| `tag_review_collection_noun` | Only if reviews are ever enabled for collections | `tag_review_occasion_noun` |

---

## 6. Deleting a custom tag: exact behaviour

Path: `ManageTagsScreen.kt:354` (`onDelete` for every tag row, no `canDelete` gate, `TagListRow` `:412-418`) → `RobiaApp.kt:1494-1495` `deleteTagAndOfferReview` → `offerTagReviewAfterWrite(deletedTagChangeSet(...) → null for collection)` → `write()` (`TagReviewScreen.kt:393`) → `RobiaApp.kt:567-571` `onDeleteCustomTag` → `LocalRepositories.kt:208-214` `deleteCustomTag` → `Dao.kt:519-524` `deleteEditableTagAndReferences` (one `@Transaction`).

What it does, in order:

1. `DELETE FROM clothing_item_tags WHERE tag_id = :id` (`Dao.kt:457-458`). Removes membership rows for that collection only. [C]
2. `DELETE FROM garment_tags WHERE id = :id AND (is_system = 0 OR category_id IN ('category','season','occasion','location'))` (`Dao.kt:459-466`). For a collection tag with `is_system = 0`, the row is deleted. Returns row count. [C]
3. If the count is > 0, upsert a tombstone `entity_type='garment_tag'`, `revision = now + 1` (`Dao.kt:522`, `LocalRepositories.kt:211-213`). [C]

**Garments are untouched.** The transaction has no `clothing_items` statement: no delete, no `updatedAt` bump, no sync-status change. [C] Only the links go. Deleting a collection cannot delete a garment.

Sync: the only enqueued op is `UpsertTags(tag.id)` (`RobiaApp.kt:569-571`, undo path `:603-611`). `affectedGarmentIds` is empty for it (`WardrobeSyncOutboxProcessor.kt:1484-1489`), so no garment is enqueued. It does set `forceSnapshot` (`WardrobeSyncOutboxProcessor.kt:274`), so the run uploads the full snapshot, which carries the removed links. The tombstone filter also drops garment-tag records whose tag is tombstoned (`WardrobeSyncOutboxProcessor.kt:1940-1946` `garmentTags … filter tagId in activeTagIds`). [C]

No review, no undo for collections (`RobiaApp.kt:1494-1496` offers none; `TagReviewScreen.kt:393-396` returns null). No confirmation appears in this path. [C] Decision D8.

Cascade: `clothing_item_tags` also has a FK to `garment_tags` with CASCADE (`Entities.kt:137-141`), so the row removal in step 1 is redundant but harmless. [C]

---

## 7. Tag name rules and sorting

- `TagNameRules.normalize` (NFD, strip marks, trim, collapse spaces, lowercase) (`core/model/TagNameRules.kt:11-19`). `dedupeKey` (no accent stripping) (`:23`). `conflicts` (normalized equality; empty never conflicts) (`:26-29`). [C]
- Editor: `TagEditorDialog` (`ManageTagsScreen.kt:592`). `trimmedName` `:603`. Duplicate check against sibling labels in the same category, across en/es/de (`:606-611`, `namesInAllLocales`). Save disabled when blank or duplicate. Message `tag_name_duplicate`. [C]
- New collection ids: `customId(prefix = categoryId, name)` = `collection-<slug>-<uuid>` (`ManageTagsScreen.kt:695`, `customId` `:1010-1018`). `isSystem = false` (`:699`). `sortOrder = categoryTags.nextTagSortOrder()` (`:698`, `:990-991`). [C]
- Sorting: `sortedAlphabetically` = Collator PRIMARY on the display label (`ManageTagsScreen.kt:1000-1006`). Used for tabs (`:298`), editor, AddEdit (`:1261`), TagPickerSheet (`GarmentFieldRows.kt:201`), filters (`AdvancedFiltersScreen.kt:444`), and the review list. Collection labels come from the stored name (`TagLocalization.kt:15`). Generic: no change. [C]
- DB order for `observeTags`: `ORDER BY sort_order, name` (`Dao.kt:423-424`). [C]
- Name collisions with an English default in other categories: not an issue, since the check is per category.

---

## 8. Final table

Columns: site | file:line | today with "collection" | required change | risk. "Risk" is HIGH / MED / LOW. Paths are relative to `app/src/main/java/com/gusanitolabs/robia/` unless they begin with `res/`.

| # | Site | file:line | Today with "collection" | Required change | Risk |
|---|---|---|---|---|---|
| 1 | Seed category row | `core/model/DefaultTags.kt:5-8` | Not seeded | Add `TagCategory("collection", "Collection", sortOrder=60, isSystem=true)` (D1, D9) | LOW |
| 2 | Category seeding (INSERT IGNORE) | `data/LocalRepositories.kt:242-245`, `data/local/Dao.kt:477-478`, `ui/RobiaApp.kt:450-453` | Reaches existing installs on launch | None | LOW |
| 3 | Seed row sync status | `data/LocalRepositories.kt:394`, `data/local/Entities.kt:54-66` | Inserted as `Synced`, rev 0, not queued | Decide (D3) | MED |
| 4 | Upgrade divergence in merge | `sync/MergeDecision.kt:51-74`; `sync/SnapshotEquivalence.kt:18-19,52-63` | Empty extra row → rule 6 `Propose` (dialog) for non-base multi-device users | Lazy row (D3), or exclude empty default categories from equivalence; add a MergeDecisionTest | HIGH |
| 5 | Schema version | `core/model/SyncModels.kt:243`; `sync/WardrobeSyncOutboxProcessor.kt:1098,1309-1312` | 2 | Keep 2. Bumping blocks old phones | HIGH if bumped |
| 6 | Room version | `data/local/RobiaDatabase.kt:22`, `:27-62` | 15, no migration | None (migration only for new columns) | LOW |
| 7 | FK category→tag | `data/local/Entities.kt:70-80` | Row must exist first | Import order already correct (`LocalWardrobeSyncSnapshotRepository.kt:124-128`) | LOW |
| 8 | Tombstone/filter on import | `sync/LocalWardrobeSyncSnapshotRepository.kt:661-677`, `:114` | Kept (category active) | None | LOW |
| 9 | Tombstone/filter in merge | `sync/WardrobeSyncOutboxProcessor.kt:1894-1915,1936-1946` | Kept; garment tags of tombstoned tags dropped | None | LOW |
| 10 | Codec (encode) | `sync/GoogleDriveWardrobeRepository.kt:1955-1959` | Round-trips unchanged | None | LOW |
| 11 | Codec (decode) | `sync/GoogleDriveWardrobeRepository.kt:1978-1991, 2106` | Round-trips unchanged | None | LOW |
| 12 | Use backup | `sync/LocalWardrobeSyncSnapshotRepository.kt:260-267` | Local collection rows survive, zero members | Accept or handle (D6) | MED |
| 13 | Export all rows for sync | `data/local/Dao.kt:415`, `sync/LocalWardrobeSyncSnapshotRepository.kt:68` | All categories exported, including the seeded one | None | LOW |
| 14 | Pending-work counts | `data/local/Dao.kt:44,55,68` | Seeded `Synced` row not counted | None | LOW |
| 15 | Restore-default button | `ui/ManageTagsScreen.kt:331,1008`; `data/LocalRepositories.kt:221-236` | No button (correct) | None | LOW |
| 16 | Dedupe same-name collections | `core/model/TagDedupePlanner.kt:36-47`; `data/LocalRepositories.kt:257,264-283` | Merged at launch across devices | Decide (D5) | MED |
| 17 | Editor duplicate check | `ui/ManageTagsScreen.kt:606-611`; `core/model/TagNameRules.kt:26-29` | Blocks accent-insensitive dupes; dedupe does not merge them | Accept, or align (copy: collection-specific message) | LOW |
| 18 | Custom tag delete (SQL guard) | `data/local/Dao.kt:459-466` | Deletable if `is_system=0` | Keep collection tags `is_system=0` (D2); or add `'collection'` to the list | HIGH if tags are system |
| 19 | Custom tag delete (transaction) | `data/local/Dao.kt:519-524`, `data/LocalRepositories.kt:208-214` | Removes links + tag + tombstone; garments untouched | None | LOW |
| 20 | Delete UI path | `ui/RobiaApp.kt:569-571,1494-1496`; `ui/TagReviewScreen.kt:393-396` | No review, no undo, no confirm | Add confirm with member count (D8) | MED |
| 21 | Undo path | `ui/RobiaApp.kt:603-611` | Undo only for reviewed kinds | Accept or extend | LOW |
| 22 | Garment enqueue on tag edit/delete | `sync/WardrobeSyncOutboxProcessor.kt:1484-1489,274` | Garments not enqueued; `UpsertTags` sets `forceSnapshot`, so a full snapshot carries the change [C] | None | LOW |
| 23 | Predictions (labels) | `suggestions/SuggestionService.kt:115,121,187-194` | Never predicted | None | LOW |
| 24 | Predicted assignment | `ui/AddEditClothingScreen.kt:1964,1987`; `ui/BatchAddClothingScreen.kt:887-890,926` | Not auto-assigned | None | LOW |
| 25 | Predicted reconciliation | `ui/AddEditClothingScreen.kt:234,286-310` | Not touched | None | LOW |
| 26 | Review gate | `ui/TagReviewScreen.kt:97-101,425-427,438-441,445-450` | No review offered | None (keep excluded) | LOW |
| 27 | Review planner else-branch | `suggestions/ReviewPlanner.kt:89-92` | Unreachable; would route to `addedOccasion` if gate changed | If ever reviewed, add explicit branch | LOW |
| 28 | Review one-category rule | `suggestions/ReviewChanges.kt:65,218` | Multi semantics (correct) | None | LOW |
| 29 | Label text / embeddings | `suggestions/EmbeddingRecipe.kt:66-80`; `suggestions/SuggestionService.kt:187-194`; `ui/RobiaApp.kt:3748-3751` | Never embedded | None | LOW |
| 30 | Semantic search | `suggestions/SearchController.kt`, `suggestions/SearchRanker.kt` | No tag use | None (adding tags needs new `RECIPE_ID`, `EmbeddingRecipe.kt:5-8`) | LOW |
| 31 | Duplicate scan | `suggestions/DuplicateScan.kt:46,481`; `ui/DuplicateCalibrationScreen.kt:209-210` | Category only | None | LOW |
| 32 | Manage tabs | `ui/ManageTagsScreen.kt:128-131` | **Tab appears automatically** | None | LOW |
| 33 | Manage tab title | `ui/ManageTagsScreen.kt:973-979` | Falls to stored English name | Add `"collection"` branch + `category_collection` | MED |
| 34 | Manage dot colour | `ui/ManageTagsScreen.kt:982-988` | `surfaceVariant` grey | Optional colour branch | LOW |
| 35 | Manage add button | `ui/ManageTagsScreen.kt:371` | Shown | None | LOW |
| 36 | Suggest-occasions switch | `ui/ManageTagsScreen.kt:663` | Hidden (correct) | None | LOW |
| 37 | Manage header copy | `res/values*/strings.xml:237` (`manage_tags_body`) | Doesn't mention collections | Update in 3 locales | LOW |
| 38 | Editor add/edit titles | `res/values*/strings.xml:408-409` | "Add tag"/"Edit tag" | Optional collection-specific strings | LOW |
| 39 | AddEdit row | `ui/AddEditClothingScreen.kt:1137-1171` | Invisible | Add row (D7) | MED |
| 40 | AddEdit picker title | `ui/AddEditClothingScreen.kt:1185-1196` | **Wrong: "Seasons"** (else → `metadata_season`) | Add branch | HIGH if reached |
| 41 | AddEdit picker multi | `ui/AddEditClothingScreen.kt:1196` | **Wrong: single-select** | Add `"collection"` to `multi` | HIGH if reached |
| 42 | AddEdit field rows helper | `ui/AddEditClothingScreen.kt:1097-1123` | Generic filter by id | None | LOW |
| 43 | AddEdit load/save | `ui/AddEditClothingScreen.kt:697,951,1009` | Membership preserved | None | LOW |
| 44 | Batch edit draft | `ui/BatchAddClothingScreen.kt:912` | Membership preserved | None | LOW |
| 45 | Batch edit route | `ui/RobiaApp.kt:3234-3240` | Uses AddEdit | None beyond AddEdit | LOW |
| 46 | Picker sheet | `ui/GarmentFieldRows.kt:189-240` | Generic (`multi` passed in) | None | LOW |
| 47 | ItemDetail rows | `ui/ItemDetailScreen.kt:539-566` | Invisible | Add row (D7) | MED |
| 48 | ItemDetail share metadata | `ui/ItemDetailScreen.kt:608-641` | Not in share | Decide (D7) | LOW |
| 49 | Export fields enum | `core/model/ClothingModels.kt:282-289`; `media/GarmentShareExporter.kt:173-182` | No field | Add enum value (D7) | LOW |
| 50 | Export labels `when` | `ui/ExportScreen.kt:339-347` | Compile-forced if enum grows | Add label | LOW |
| 51 | Export list | `ui/ExportScreen.kt:205` | Generic | None | LOW |
| 52 | Browse matching | `ui/BrowseFilterBar.kt:160-175` | Works (by id and category) | None | LOW |
| 53 | Browse chips (tags) | `ui/BrowseFilterBar.kt:517-519` | Generic label | None | LOW |
| 54 | Browse "Not set" chip | `ui/BrowseFilterBar.kt:532` | Raw id shown | Via `categoryTitleRes` | MED |
| 55 | Category title for chips | `ui/BrowseFilterBar.kt:579-585` | `null` → raw id | Add branch + `filter_collection` | MED |
| 56 | Pruning unknown tags | `ui/BrowseFilterBar.kt:615-630` | Generic | None | LOW |
| 57 | AdvancedFilters lists | `ui/AdvancedFiltersScreen.kt:124-127` | Not listed | Add `collectionTags` | MED |
| 58 | AdvancedFilters cards | `ui/AdvancedFiltersScreen.kt:194-233` | Invisible | Add `TagFilterCard` (D7) | MED |
| 59 | Tag filter card internals | `ui/AdvancedFiltersScreen.kt:310-`, `:444-449` | Generic | None | LOW |
| 60 | Card text | `ui/BrowseCardText.kt:16-18,32,39,61` | Invisible | Decide (D7) | LOW |
| 61 | Batch ETA | `ui/BatchEta.kt:90-92` | Not in text | Decide (D7) | LOW |
| 62 | Icon | `ui/CategoryIcons.kt:14-19` | **Checkroom (else)** | Add branch | LOW |
| 63 | Analytics groups | `core/model/WardrobeStats.kt:56-61` | Not counted | Add group if wanted (D1) | LOW |
| 64 | Analytics custom | `core/model/WardrobeStats.kt:63-71`; `ui/AnalyticsScreen.kt:263` | `isSystem=false` → English "Collection" section | Set `isSystem=true` (D1) | HIGH if false |
| 65 | Analytics title `when` | `ui/AnalyticsScreen.kt:257-263` | Compile-forced only for new enum | Only if D1 adds a section | LOW |
| 66 | Tag label localisation | `ui/TagLocalization.kt:14-16,36` | Stored name | None | LOW |
| 67 | Sorting | `ui/ManageTagsScreen.kt:1000-1006`; `data/local/Dao.kt:423-424,411-412` | Generic | None (tab order via sortOrder, D9) | LOW |
| 68 | Name rules | `core/model/TagNameRules.kt:11-29` | Generic | None | LOW |
| 69 | Custom id and flags | `ui/ManageTagsScreen.kt:695-699` | `collection-<slug>-<uuid>`, `isSystem=false` | None | LOW |
| 70 | First-run detection | `ui/RobiaApp.kt:1118-1123` | Seed row equal → still first-run | None | LOW |
| 71 | Stats of user data | `sync/WardrobeSyncOutboxProcessor.kt:1837-1844` | Custom tags count as user data | None | LOW |
| 72 | Metadata table map | `data/local/Dao.kt:644-650` | Generic | None | LOW |
| 73 | Tombstone entity types | `sync/LocalWardrobeSyncSnapshotRepository.kt:696-697`; `sync/WardrobeSyncOutboxProcessor.kt:2138-2139` | Not category ids | None | LOW |
| 74 | `care` filters (legacy) | `data/LocalRepositories.kt:173,446`; `ui/ManageTagsScreen.kt:128-129` | Unaffected | None | LOW |
| 75 | Old backups with `care`/`fit` | `data/local/RobiaDatabase.kt:76-78,93-97` | Migration precedent | None | LOW |
| 76 | Zip export | `data/WardrobeZipExporter.kt` | No tag/category references (grep) | None | LOW |
| 77 | Strings (new keys) | `res/values`, `res/values-es`, `res/values-de` (§5.2) | Missing | Add keys in all three locales | MED |
| 78 | Strings (generic reuse) | `res/values*/strings.xml:402-409,453` | Generic "tag" copy used for collections | Optional collection copy | LOW |

---

## 9. Hypotheses and gaps (not verified by running)

- [H] Whether a seeded, unqueued category row causes an upload on its own. Depends on sync scheduling, not read end to end.
- [H] How often the merge dialog (§3.4) appears in practice. The rules are read; real frequency depends on multi-device timing.
- [H] Gray (`LocalRepositories.kt:249-257`) already has the same divergence effect on existing installs.
- [H] `category_style`, `category_care`, `category_custom` are unused. grep found no Kotlin references; lint would confirm.
- [H] What a forced snapshot cycle uploads. I read its entry point (`WardrobeSyncOutboxProcessor.kt:262-290`, `:872`) but not `executeDirection` in full.
- Not inspected in depth: `WardrobeSyncGateway`/`DriveSyncWorker` scheduling, and tests that fix categories (e.g. `app/src/test/.../sync/fakes/RealPathFixtures.kt:50`, `UploadSkipTest.kt:97`, `MergeDecisionTest.kt:378`). They will need a collection case if the plan goes ahead.
- Nothing was built or run (read-only task). Behaviour claims are from reading the source.
