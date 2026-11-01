# Collection members screen: inventory (task B)

Repo: `<planning>/workers/r4-inv`, `main` @ `0ceb916`. Read-only: nothing edited, committed or built. `git status` clean.
Paths below are under `app/src/main/java/com/gusanitolabs/robia/` unless stated (`ui/`, `data/`, `data/local/`, `suggestions/`, `sync/`, `core/model/`).

Legend: **[C]** CERTAIN, read in code (file:line). **[H]** HYPOTHESIS, inference or not run. Nothing was compiled or tested.

---

## 0. Key findings (read this first)

1. **Browse grid is state-hoisted.** `BrowseWardrobeScreen` keeps no selection, filter or search state of its own. A second instance can run on another screen. But it has 39 params, pulls in Browse banners and the Add CTA, and cannot be used as-is. Build a slimmer grid from reusable pieces (`FilterBar`, `dragSelect`, `GarmentPhotoTile`/`GarmentGridCard` after a visibility change). **[C]**
2. **Browse selection** (`selectedBrowseItemIds`) is `remember` state in `RobiaShell` (RobiaApp.kt:996), not top-level `RobiaApp`. The new screen must own its own selection and draft state. **[C]**
3. **`DragSelect` is union-only.** The range is always `baseline + range`, so a drag can add but never remove. Drag-to-remove needs a painted add/remove mode, which means a new class (DragSelect.kt:41-58 cannot do it). **[C]**
4. **Search**: `SearchController` is cheap (a cache and a scope). Its embedder, the `EmbeddingEngineHolder`, is app-level behind a Mutex (EmbeddingEngineHolder.kt:45). A second instance is safe. The search field is hidden unless the model is `Ready` (ModelDownloadUi.kt:38), so there is no text-only search without the model. **[C]**
5. **Filters**: `BrowseFilterState` is a plain data class and can be instantiated independently. Nothing persists it (only `remember`, RobiaApp.kt:971). The Advanced filters route is wired to the shared Browse state (RobiaApp.kt:3369-3379, 2232-2233). The Advanced sheet hardcodes four category sections (AdvancedFiltersScreen.kt:124-127, 192-239), so a `collection` filter does **not** appear there automatically. Selected collection chips in the chip row **do** appear automatically (BrowseFilterBar.kt:524-527). **[C]**
6. **Writes**: never use `clearTags` (Dao.kt:336-337) or `clearTagsForItems` (Dao.kt:447-448) for membership. They wipe **all** tags of the garment, including season, occasion and other collections. The membership edit needs a new `@Transaction` in `TagDao` with a targeted `DELETE ... WHERE tag_id = :tagId` and a sync touch. Existing queries cover reads and inserts. **[C]**
7. **Sync**: Drive uses one whole-snapshot file. Garment-tag pairs are merged by union with no tombstones (WardrobeSyncOutboxProcessor.kt:1940-1944). A removal can therefore be resurrected on restore or merge paths (decideWithRemote/proposeMerge, :993-1027, and the restore merge, :1330). This is the same exposure as garment-tag removals today. **[C]** on the code; impact **[H]**.
8. **Tag review does not trigger for collections.** `ReviewedTagKinds` = category and occasion only (TagReviewScreen.kt:101). Saving or deleting a `collection` tag returns a null change set (:426, :439), so no review opens. **[C]**
9. **Analytics**: `isSystem = true` on the new category excludes it from the custom analytics groups (WardrobeStats.kt:63-64). **[C]**
10. **Icon**: `Icons.Rounded.PlaylistAdd` (and `PlaylistRemove`, `LibraryAdd`, `ChecklistRtl`) are present in the cached material-icons-extended 1.7.6 jar. **[C]**

---

## 1. Browse composition and selection

### 1.1 Composables and reusability

| Symbol (file:line) | Signature (abridged) | Stateful? | Reusable as-is? |
|---|---|---|---|
| `BrowseWardrobeScreen` (ui/BrowseGrid.kt:186-227) | `internal fun BrowseWardrobeScreen(innerPadding, items: List<UiWardrobeItem>, isInitialLoading, hasItemsInWardrobe, hasActiveFilters, activeFilterCount, filters: BrowseFilterState, availableTags: List<GarmentTag>, mainColors: List<MainColor>, onFiltersChange, cloudSync: CloudSyncIndicator, selectedItemIds: Set<String>, onItemSelected: (UiWardrobeItem)->Unit, onToggleFavorite, onSelectionToggle: (String)->Unit, onSelectionSet: (Set<String>)->Unit, onAddClick, onFiltersClick, onResetFilters, attentions, mergeProposalCard, onAttentionAction, showSyncDetails, onSyncDetailsClick, duplicateGroupCount, onDuplicateReviewClick, searchQuery, onSearchQueryChange, searchMode, searchInProgress, similarToName, onClearSimilar, onClearSearch, modelState, onOpenSettings, browseLayout, columns, onColumnsChange, onAnalyticsClick)` | Only grid, pinch, drag-arm and backfill read. Selection, filters and search are all hoisted params. | Technically yes, but a poor fit: ~39 params, banners and the Add CTA appear, `onItemSelected` opens ItemDetail when nothing is selected (BrowseGrid.kt:376-382). |
| `FilterBar` (ui/BrowseFilterBar.kt:299-320) | `internal fun FilterBar(hasActiveFilters, activeFilterCount, filters, availableTags, paletteColors, onFiltersChange, cloudSync: CloudSyncIndicator, onFiltersClick, onResetFilters, searchQuery, onSearchQueryChange, searchMode, searchInProgress, resultCount, similarToName, onClearSimilar, modelState, onOpenSettings, onAnalyticsClick: (() -> Unit)? = null)` | Hoisted. Its `LaunchedEffect`s prune unknown tags (:336-343) and clear the query when search is hidden (:331-333). | **Yes.** `cloudSync` is non-null, so the caller must pass one. `onAnalyticsClick = null` hides the icon. |
| `dragSelect` (ui/DragSelect.kt:83-89) | `internal fun Modifier.dragSelect(gridState: LazyGridState, arm: DragSelectArm, items: List<UiWardrobeItem>, onSelectionChange: (Set<String>) -> Unit): Modifier` | Holds only its own gesture coroutine. | **Yes.** It needs its own `DragSelectArm()` per screen (BrowseGrid.kt:233). |
| `DragSelectArm` / `DragSelect` (ui/DragSelect.kt:25-58) | Arm: `anchorId`, `baseline`, `arm(id, selectedBefore)`, `disarm()`. Range: `DragSelect(anchorId, baseline).moveTo(id)`, `selectedIds(orderedIds)`. | Per gesture. | Yes. Union-only, see 1.3. |
| `GarmentPhotoTile` (ui/BrowseGrid.kt:581-590) | `private fun GarmentPhotoTile(item: UiWardrobeItem, isSelected, onClick, onLongClick, onFavoriteClick, modifier, columns, signInBannerShown)` | No. | **No, private.** Change to `internal` (1-word edit). |
| `GarmentGridCard` (ui/BrowseGrid.kt:610-620) | `private fun GarmentGridCard(item, isSelected, isSelectionMode, onClick, onLongClick, onFavoriteClick, modifier, columns, signInBannerShown)` | No. | **No, private.** Change to `internal`. |
| `BrowseCell` / `browseCellFor` (ui/BrowseCardText.kt:27, 77-81) | Tile for `PhotosOnly`, Card for `PhotosWithDetails`. | No. | Yes. Pass `settings.browseLayout`. |
| `toUiWardrobeItems` (ui/BrowseGrid.kt:886-890) | `internal fun List<ClothingItem>.toUiWardrobeItems(syncState, online, context): List<UiWardrobeItem>` | No. | Yes. Only needs the list. |
| `UiWardrobeItem`, `UiTag` (ui/BrowseGrid.kt:148-182) | `internal data class`. `tags: List<UiTag>` carries `categoryId`, so membership is `item.tags.any { it.id == collectionId }`. | Immutable. | Yes. |
| `AdvancedFiltersScreen` (ui/AdvancedFiltersScreen.kt:110-120) | `internal fun AdvancedFiltersScreen(innerPadding, filters, availableTags, mainColors, items: List<UiWardrobeItem>, allItemCount, onFiltersChange, onResetFilters, onShowResults)` | Hoisted. Its own `Scaffold` and top bar. | **Yes**, as an overlay with its own `BrowseFilterState`. It has no collection section. |
| `EmptyFiltersCard` (ui/BrowseGrid.kt:851) | `internal fun EmptyFiltersCard(onResetFilters)` | No. | Yes. |
| `GarmentPhotoPlaceholder` (ui/BrowseGrid.kt:786) | `internal` | No. | Yes. |

### 1.2 Where selection lives, and its lifecycle

- **Owner.** `var selectedBrowseItemIds by remember { mutableStateOf(emptySet<String>()) }` at RobiaApp.kt:996, inside `RobiaShell` (declared at RobiaApp.kt:901). The top-level composable `RobiaApp` is at :376. The brief's "~3127" is the pass-through into `BrowseWardrobeScreen` at RobiaApp.kt:3127 (`selectedItemIds = selectedBrowseItemIds`). **[C]**
- **Not persisted.** Plain `remember` (not `rememberSaveable`), so it is lost on process death. **[C]**
- **Entry.**
  - Long-press a card: `onLongClick` arms `dragArm.arm(item.id, selectedItemIds)`, then `onSelectionToggle(item.id)` (BrowseGrid.kt:383-387). That calls `toggleBrowseSelection` (RobiaApp.kt:1295-1301).
  - Tap a card while selection is non-empty: toggles (BrowseGrid.kt:377-379).
- **Exit.**
  - Top-bar Close (RobiaApp.kt:2003-2006 sets `emptySet()`).
  - Back, through `BackAction.ClearSelection` (RobiaApp.kt:1806; policy at BackExitPolicy.kt:32).
  - Delete (RobiaApp.kt:1310).
  - Pruned to the visible wardrobe whenever `items` changes (RobiaApp.kt:1252-1260).
  - Writes to `selectedBrowseItemIds` at 1256, 1296-1304, 1310, 1806, 2004. **Nothing clears it on navigation [C].**
- **Top bar.** The selection title and icons show only when `currentRoute == Browse` (RobiaApp.kt:2003, 2038, 2048).
- **Pruning trap.** The prune uses the whole active wardrobe (`items`), not the filtered list (RobiaApp.kt:1252-1256). The new screen must prune against its own list.

### 1.3 DragSelect: range and baseline

- **Anchor and baseline.** Long-press arms `DragSelectArm` with `anchorId = item.id` and `baseline = selectedBefore`, which is the selection before the long-press toggle (DragSelect.kt:29-31, BrowseGrid.kt:385).
- **Range.** `DragSelect.selectedIds(orderedIds) = baseline + orderedIds[min(anchor,current)..max(anchor,current)]`. It returns `baseline` if the anchor or current id is missing (DragSelect.kt:52-57). The order is the list passed as `items` to `dragSelect`, which is the displayed order (BrowseGrid.kt:305).
- **Hit testing.** `itemIdAt` checks only `visibleItemsInfo` with keys in the item set (DragSelect.kt:156-165).
- **Autoscroll.** 64 dp edge zones, up to 24 dp per 16 ms frame (DragSelect.kt:20-22, 64-76, 125-135).
- **Pinch.** Two or more fingers end the drag (DragSelect.kt:110-114). Pinch is handled in `BrowseWardrobeScreen` by `pinchResidual`/`columnsForPinch` (BrowseGrid.kt:271-304, BrowseColumns.kt:28-46).
- **Consequence for membership.** Range **adds** only, so the drag cannot remove. The brief asks for add and remove by drag, so a painted mode is needed (see §9, `CollectionDragPaint`).
- **Tests.** DragSelectTest covers `selectedIds`, `moveTo` and autoscroll (`DragSelectTest.kt:11-69`). No test covers the composable (no `androidTest` directory exists; see §8).

### 1.4 Can a second instance run without sharing state?

- **Yes.** Per-instance state is the grid state, `DragSelectArm`, pinch state and scroll anchor (BrowseGrid.kt:232-259). Selection and filters come in as params, so the new screen can hold its own. **[C]**
- **Process-level singletons read by the grid:** `app.embeddingBackfill` progress (BrowseGrid.kt:234-235, used for the progress banner at :334-338). Both screens show that banner, which is harmless.
- **Pinch writes a setting.** `onColumnsChange` goes to `onBrowseColumnsChange`, and `RobiaSettings.browseColumns` is the "pinch-chosen" count (ClothingModels.kt:240-241). The new screen should pass a no-op `onColumnsChange`. Persistence of that setting is **[H]** (not traced).

---

## 2. Search

### 2.1 Construction

- Built once in `RobiaShell`: `searchScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }` (RobiaApp.kt:1050), then `remember(app, searchScope) { SearchController(embedQuery = { app.embeddingEngineHolder.embedText(EmbeddingRecipe.QUERY_PREFIX + query) }, scope = searchScope) }` (RobiaApp.kt:1051-1056). **[C]**
- The supporting state is in RobiaShell: `browseQuery` (rememberSaveable, :972), `queryEmbedding` and `searchInProgress` (:1063-1064), a `LaunchedEffect(trimmedQuery, engineAvailable)` (:1066-1075), and `searchResult` (:1077-1084). **[C]**
- The vectors are `app.embeddingRepository.observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID).collectAsState` (RobiaApp.kt:1045-1047). `observeVectors` is at data/EmbeddingRepository.kt:37. **[C]**

### 2.2 Per-screen instance

- **Yes, a second instance is fine.** `SearchController` holds only a query cache, an in-flight deferred, and the `scope`/`embedQuery` (SearchController.kt:36-44). Its doc says the caller cancels `queryVector` on every query change, and that cancellation is the debounce (:34). **[C]**
- Use a separate instance (not the Browse one). The single cache and in-flight slot would thrash between two queries (SearchController.kt:54-56). Give it the same supervised scope pattern (RobiaApp.kt:1049-1050), so a query embedding outlives its caller.
- The engine is app-level: `app.embeddingEngineHolder` (RobiaApplication.kt:118-120), an `EmbeddingEngineHolder` with `Mutex` (EmbeddingEngineHolder.kt:39-45). Sharing it is safe. **[C]**
- **Preload trap.** `preload()` runs only from RobiaShell, keyed on Browse's `searchActive` (RobiaApp.kt:1088-1090). The member screen must call `app.embeddingEngineHolder.preload()` itself, or its first query may cold-load the model **[H]** on latency.

### 2.3 When the model is absent

- `browseSearchVisible(state) = state is ModelState.Ready` (ui/ModelDownloadUi.kt:38). **[C]**
- `FilterBar` hides the search field and clears the query when hidden (BrowseFilterBar.kt:326, 331-333, 350-360). It shows an AutoAwesome model-prompt icon instead (:386-396). **[C]**
- `SearchController.queryVector` returns `null` at once when `!engineAvailable`, before the debounce (SearchController.kt:52). `rank` then falls back to a word search (`SearchMode.Text`, :69-70). That branch is unreachable from the UI, because the field is hidden. **[C]**
- **Net:** there is no text-only search without the model. The member screen should hide search the same way.

### 2.4 Cost (per query)

- A 300 ms debounce (`SEARCH_DEBOUNCE_MILLIS`, SearchController.kt:12), one native text embed (cached per instance, :86-89), and a 5 s timeout (`SEARCH_EMBED_TIMEOUT_MILLIS`, :15).
- Ranking is a pass over the filtered list (`rankByQuery`, SearchController.kt:73). `SearchRanker.kt` was **not read**, so per-item cost is **[H]**.
- Each instance adds one `observeVectors` collector, which is a Room flow and cheap **[H]**.

---

## 3. Filters

### 3.1 Shape

`internal data class BrowseFilterState` (ui/BrowseFilterBar.kt:126-134):
- `selectedTagIds: Set<String>`
- `selectedTagCategories: Map<String, String>` (tagId to categoryId)
- `selectedUnsetTagCategoryIds: Set<String>` ("Not set" options)
- `selectedPaletteColorIds: Set<String>`
- `favoritesOnly: Boolean`
- `fit: Set<FitFilter>`

Logic: within a category it is OR, across categories it is AND, colours OR, favourites AND (BrowseFilterBar.kt:154-172). It is category-generic, so a new category gets the matching logic for free. **[C]**

### 3.2 Independence and persistence

- **Independent.** Plain constructor (`BrowseFilterState()`). A new screen can own `remember { mutableStateOf(BrowseFilterState()) }`. **[C]**
- **Not persisted.** The only holder is `var browseFilters by remember { mutableStateOf(BrowseFilterState()) }` (RobiaApp.kt:971). Resets: RobiaApp.kt:1810, 2178, 2233. It is not in DataStore: `robia_settings` holds settings only (RobiaApplication.kt:67), and filter keys are absent. Its `remember` state also does not survive configuration change **[C]**.
- **The Advanced route reads the shared state.** `RobiaRoute.AdvancedFilters` renders with `filters = browseFilters`, `onFiltersChange = { browseFilters = it }` (RobiaApp.kt:3369-3379, 2232). A member screen that routes to it would edit the Browse filters. So it must use `AdvancedFiltersScreen` as an overlay with its own state, or have a parameterised route.

### 3.3 Would a `collection` chip work once it is a category?

| Surface | Automatic? | Evidence |
|---|---|---|
| Active chip row for selected collection tags | **Yes** | `tagsById` is built from all `availableTags` and the label uses `localizedTagLabel` (BrowseFilterBar.kt:515-527). `availableTags` is `observeTags()`, which includes every category except `care` (LocalRepositories.kt:176-177). |
| Active chip row for "Not set" on collection | **No**, shows the raw id `collection` | `categoryTitleRes` has no branch, so it falls back to the id (BrowseFilterBar.kt:531-532, 579-585). Needs a string and a branch. |
| Advanced filters sheet section | **No** | Only four `TagFilterCard`s, for `category`, `season`, `occasion`, `location` (AdvancedFiltersScreen.kt:124-127, 192-239). Needs a new `TagFilterCard(categoryId = "collection")`. |
| Category icon | **No**, falls back to Checkroom | `categoryIconFor` has no branch (ui/CategoryIcons.kt:14-20). |
| Matching logic | **Yes** | `matches` is category-generic (BrowseFilterBar.kt:160-172). |
| Filter count | **Yes** | `activeFilterCount` and `activeFilterCountIn` count every tag (:142-152). |
| Analytics | **Only if `isSystem = false`** | Custom groups are `filterNot(isSystem)` (WardrobeStats.kt:63-64). With `isSystem = true` the category is excluded. |

---

## 4. Navigation

### 4.1 Route model

- `private sealed interface RobiaRoute` (RobiaApp.kt:279-358). Each entry has a `titleRes`. `data class Export(val itemId: String)` (:355-357) is the precedent for a parameterised route. Its title is static.
- `ManageTags` is `data object` (RobiaApp.kt:287-289) and a bottom destination (:366-370). Entry points use `replaceRoute(RobiaRoute.ManageTags)` (:1511, 1529, 1549, 1566, 1615, 2255).
- Tab state is hoisted: `manageTagsTabId` is `rememberSaveable` (RobiaApp.kt:977). It survives a push and pop of a child route.

### 4.2 Helpers

- `pushRoute` skips a duplicate top (RobiaApp.kt:1262-1264). `replaceRoute` clears the stack (:1266-1269). `popRoute` removes the top when size > 1 (:1271-1273). **[C]**
- `onRouteSelected` in RobiaNavHost is `pushRoute`, except for `AddEditClothing` (RobiaApp.kt:2158-2161). So the Manage row can call `onRouteSelected(RobiaRoute.CollectionMembers(tag.id))` and push.

### 4.3 Back handling

- **Root handler (pushed routes).** `BackHandler(enabled = restoreProgress != null || routeStack.size > 1)` calls `popRoute()`, with special cases for ColorReview and TagReview (RobiaApp.kt:1760-1770). A pushed `CollectionMembers` pops to Manage with no change. **[C]**
- **Root handler (tabs).** `BackHandler(enabled = routeStack.size == 1)` runs the `BackExitMachine` (RobiaApp.kt:1786-1824). It does not run for pushed routes. The policy order is selection, then query/similar/filters, then Manage to Browse, then exit (BackExitPolicy.kt:28-40; `decideBack` at :32-34). **[C]**
- **Nested handlers win.** A `BackHandler` composed later in a child screen takes priority (RobiaApp.kt:1785 comment: "Add, batch add, tag review and colour review have their own BackHandler composed later, so they win"). The new screen can clear its own selection or ask about discard with a nested handler. **[C]**
- **Top-bar back arrow bypasses handlers.** It calls `popRoute()` directly for any `routeStack.size > 1`, except AddEdit (RobiaApp.kt:2017-2034). For a discard prompt that the top bar also honours, the screen must either draw its own top bar (as AdvancedFilters does, RobiaApp.kt:2001) or hoist the request as ColorReview does (:2020-2023).

### 4.4 Exact edits in RobiaApp.kt for `RobiaRoute.CollectionMembers(tagId)`

| # | Lines (current) | Edit |
|---|---|---|
| 1 | 355-357 (inside `RobiaRoute`) | Add `data class CollectionMembers(val tagId: String) : RobiaRoute { override val titleRes = R.string.collections_title }`. `titleRes` is only a fallback. |
| 2 | 1033 | `needsWardrobeUiModels = currentRoute is RobiaRoute.Export \|\| currentRoute is RobiaRoute.CollectionMembers \|\| currentRoute in WardrobeUiModelRoutes`. **Must use `is`, not the set.** `WardrobeUiModelRoutes` (:893-897) holds objects, and a data class instance never matches by set membership. Without this, `items` is empty on the route. |
| 3 | 2036-2046 (title) | Replace the title with the collection name for this route. Look it up in `availableTags` by `tagId`. |
| 4 | 2001 (top bar) | Optional, for own top bar: `if (currentRoute != RobiaRoute.AdvancedFilters && currentRoute !is RobiaRoute.CollectionMembers)`. |
| 5 | 2097-2109 (bottom bar exclusions) | Add `currentRoute !is RobiaRoute.CollectionMembers &&`. |
| 6 | 2122-2311 (RobiaNavHost call) | Pass `onSaveCollectionMembership`. Pass `vectors` (already in RobiaShell, :1045) if needed. |
| 7 | 3158-3171 (ManageTags branch) | Add `onEditMembers = { tag -> onRouteSelected(RobiaRoute.CollectionMembers(tag.id)) }`. |
| 8 | after 3171 (inside `when (currentRoute)` at :3116) | Add `is RobiaRoute.CollectionMembers -> CollectionMembersScreen(...)`. The `when` is exhaustive over a sealed interface, so the compiler forces this branch. |
| 9 | RobiaShell params (:941-951 area) and RobiaNavHost params (:3004-3115 area) | Add `onSaveCollectionMembership: (tagId: String, add: Set<String>, remove: Set<String>) -> Unit`. |
| 10 | RobiaShell call site in `RobiaApp` (:459 onward; the tag callbacks are at :555-575) | Add `onSaveCollectionMembership = { tagId, add, remove -> launchReviewWrite { ... } }` (see §5.5). |

No change is needed to the back handlers at :1760-1770 or :1786-1824.

---

## 5. Writes and sync

### 5.1 Existing write paths

| Path | Where | Behaviour | Relevant? |
|---|---|---|---|
| Single garment upsert | `LocalWardrobeRepository.upsertItem` (data/LocalRepositories.kt:72-75) to `WardrobeDao.upsertItemWithTags` (Dao.kt:374-383) | Whole row, then `clearTags(item.id)` (Dao.kt:336) and reinsert of all tags. Uses `queuedForSync` with the photo-guard state. | Do not use for membership. |
| Batch upsert | `upsertItems` (LocalRepositories.kt:77-83) to `upsertItemsWithTags` (Dao.kt:386-390) | Loops over single upserts in one transaction. | Usable (the tag-review writer uses it). Whole-row. |
| Tag review batch | `writeTagReviewChanges` (RobiaApp.kt, `internal suspend fun`), with `applyDelta` (suggestions/ReviewChanges.kt:204-226) | Re-reads each live garment, skips archived, keeps other tags, sets `updatedAtEpochMillis = now`. Enqueues `UpsertItem` per written garment. Runs in the serial `ReviewWrites` chain (RobiaApp.kt:3770-3800). | **Closest precedent. Mirror its shape.** |
| Main colour change | `TagDao.applyMainColorChange` (Dao.kt:495-516) via `LocalTagRepository.applyMainColorChange` (LocalRepositories.kt:194-206) | Upserts colours and items and clears and reinserts tags, in one `@Transaction` on `TagDao`. | Precedent for a tag-side transaction that writes items. |
| Tag merge | `TagDao.applyTagMerges` (Dao.kt:547-578) | Repoints refs with `insertItemTagRefs` and queues items via `queuedForSync`. | Precedent for ref-level edits. |
| Archive | `archiveItemsWithTombstones` (Dao.kt:361-368), `archiveItems` SQL (:351) | SQL `UPDATE` of sync columns only, no whole-row write. | Precedent for a sync-only bump. |
| Delete collection | `deleteEditableTagAndReferences` (Dao.kt:518-524) | Deletes refs and writes a tombstone. Does **not** touch `clothing_items` sync state. | See §5.6. |

### 5.2 Sync queueing

- **Local state.** `queuedForSync()` (Dao.kt:612-623) sets `sync_status = Queued`, `sync_revision = max(updatedAt, now)`, `sync_dirty_at = revision`, clears `sync_failure_message`, and sets the photo-guard fields from its argument (null resets them). **[C]**
- **Counts.** Pending counts treat `Dirty` and `Queued` rows as pending (Dao.kt:32-33). Browse badges come from `syncStatus` (BrowseGrid.kt:762-786). **[C]**
- **Wake-up.** The caller must also enqueue sync ops. `WardrobeSyncOperation.UpsertItem(itemId)` and `UpsertTags(ids)` (sync/WardrobeSyncGateway.kt:269-300). The batch callers do this: `onSaveItems` returns `UpsertItem` per item (RobiaApp.kt:540-548). `ReviewWrites.launch` enqueues the returned ops after the write (RobiaApp.kt:3777-3782). **[C]**
- **Whole-snapshot upload.** The exported snapshot is built from the DB, active garments only (LocalWardrobeSyncSnapshotRepository.kt:61-69). **[C]**
- **Revision of a garment-tag pair.** The mapping's revision is the garment's `updatedAt` (LocalWardrobeSyncSnapshotRepository.kt:354-356, `toSyncRecord(revision = itemUpdatedAtById[...])` at :462-467). **[C]**

### 5.3 Archived garments

- Browse shows only active garments: `observeActiveItems` is `WHERE is_archived = 0` (Dao.kt:18-19; LocalRepositories.kt:38-39). The member screen therefore never lists archived garments. **[C]**
- Snapshot export drops archived garments' refs (LocalWardrobeSyncSnapshotRepository.kt:63-67). **[C]**
- Tag review skips archived garments (`applyDelta`, ReviewChanges.kt:210 `live.isArchived`). `hasReviewableGarments(Tag)` ignores archived too (ReviewScaffold.kt:120-123). **[C]**
- **Decision.** The new DAO method should still filter `is_archived = 0` defensively, and never touch archived ids.

### 5.4 Existing DAO queries: what suffices

| Need | Existing query (Dao.kt) | Verdict |
|---|---|---|
| Current members of a tag | `TagDao.itemRefsForTag(tagId)` (:529-530) | **Suffices.** |
| Insert pairs, ignoring duplicates | `TagDao.insertItemTagRefs` (:450-451, `@Insert(IGNORE)`) | **Suffices.** |
| Check archived or existence | `TagDao.clothingItemsByIds(ids)` (:532-533) | **Suffices**, filter in Kotlin. |
| Delete only this tag's links | none (`deleteItemRefsForTag` at :456-457 is for **all** items of a tag) | **New.** |
| Sync-touch changed garments | none (`archiveItems` at :351 is archive-only) | **New.** |

**Do not use:** `WardrobeDao.clearTags(itemId)` (:336-337) or `TagDao.clearTagsForItems(itemIds)` (:447-448). Both delete every tag of the garment.

### 5.5 Proposed minimal repository method

Interface, in `data/WardrobeRepository.kt` (`TagRepository`, :66-82):

```kotlin
/** Adds or removes [tagId] on the given garments in one transaction; other tags stay. Returns the number of links changed. */
suspend fun setTagMembership(
    tagId: String,
    addGarmentIds: Set<String>,
    removeGarmentIds: Set<String>,
    updatedAtEpochMillis: Long,
): Int
```

Implementation, in `data/LocalRepositories.kt` (`LocalTagRepository`, :167-):

```kotlin
override suspend fun setTagMembership(tagId: String, addGarmentIds: Set<String>, removeGarmentIds: Set<String>, updatedAtEpochMillis: Long): Int =
    tagDao.setTagMembership(tagId, addGarmentIds, removeGarmentIds, updatedAtEpochMillis)
```

DAO, in `data/local/Dao.kt`, inside `interface TagDao` (:410-). It goes next to `applyMainColorChange` (:495-516), which is the precedent:

```kotlin
@Transaction
suspend fun setTagMembership(tagId: String, addIds: Set<String>, removeIds: Set<String>, now: Long): Int {
    val members = itemRefsForTag(tagId).mapTo(HashSet(), ClothingItemTagCrossRef::clothingItemId)
    val toAdd = addIds.filter { it !in members }
    val toRemove = removeIds.filter { it in members }
    if (toAdd.isNotEmpty()) insertItemTagRefs(toAdd.map { ClothingItemTagCrossRef(it, tagId) })
    if (toRemove.isNotEmpty()) deleteTagRefsForItems(tagId, toRemove)
    val changed = toAdd + toRemove
    if (changed.isNotEmpty()) touchItemsForSync(changed, now)
    return changed.size
}

@Query("DELETE FROM clothing_item_tags WHERE tag_id = :tagId AND clothing_item_id IN (:itemIds)")
suspend fun deleteTagRefsForItems(tagId: String, itemIds: List<String>)

@Query(
    """
    UPDATE clothing_items
    SET sync_status = 'Queued', sync_revision = :now, sync_dirty_at_epoch_millis = :now,
        sync_failure_message = NULL, updated_at_epoch_millis = :now
    WHERE id IN (:itemIds) AND is_archived = 0
    """,
)
suspend fun touchItemsForSync(itemIds: List<String>, now: Long)
```

Notes:
- `touchItemsForSync` does **not** call `queuedForSync()`. That keeps the photo-guard, retry-after and deadline fields intact (Dao.kt:619-621). `applyMainColorChange` calls `queuedForSync` with null state (Dao.kt:508), which resets them. Whether that clears an active guard in practice is **[H]**.
- `updated_at = :now` is in the SQL. Decide whether to bump it (§10). The tag review does (`applyDelta`, `updatedAtEpochMillis = now`), but Browse sorts by `updated_at` (Dao.kt:18), so a 30-garment save moves those garments to the top of the grid.
- `IN ()` with an empty list: the code guards it. Room's handling of `@Query` INSERT and empty `IN` lists is **[H]**, to be confirmed at compile.
- Pure planning (the `members` diff) can move to `core/model` as a function of plain sets, so it is unit-testable without Room (§8).

Caller, in `RobiaApp` (the `RobiaShell` call site, near `onSaveTag` at RobiaApp.kt:555):

```kotlin
onSaveCollectionMembership = { tagId, add, remove ->
    launchReviewWrite {
        tagRepository.setTagMembership(tagId, add, remove, System.currentTimeMillis())
        (add + remove).map { id -> WardrobeSyncOperation.UpsertItem(id) }
    }
},
```

This mirrors `onSaveItems` (RobiaApp.kt:540-548) and `onRestoreDefaultTags` (:579-589). `launchReviewWrite` serialises with tag reviews (RobiaApp.kt:417, 3770-3782).

**Undo** (optional snackbar, see §6): call the same method with `add` and `remove` swapped. This matches `tagReviewUndoPlan` in spirit.

### 5.6 Deleting a collection

- `deleteEditableTagAndReferences` deletes all refs for the tag and writes a `garment_tag` tombstone (Dao.kt:518-524, 456-457). It does not touch `clothing_items` sync state. **[C]**
- Merge filters pairs whose tag is inactive (WardrobeSyncOutboxProcessor.kt:1944 `record.tagId in activeTagIds`), and tombstoned tags are removed (:1912-1913). **[C]**
- The removal reaches Drive through the whole-snapshot upload, because the export is built from the DB. The `UpsertTags` op is enqueued (RobiaApp.kt:567-572). **[H]** that this is sufficient; not traced end to end.

---

## 6. UX conventions to reuse

| Need | Existing symbol (file:line) | Notes |
|---|---|---|
| Discard prompt | `ReviewDiscardDialog(title, body, onConfirm, onDismiss)` (ui/ReviewScaffold.kt:400-422) | Generic. Buttons are `discard_changes` and `review_keep_reviewing`. Reusable with new title and body strings. |
| Leave-with-count dialog | `AlertDialog` with `review_leave_title`, `pluralStringResource(R.plurals.review_leave_body, n)`, `review_leave`, `review_keep_reviewing` (RobiaApp.kt:1887-1902) | Precedent for "leave and keep changes". |
| Stacked dialog buttons | `StackedActionDialog` (RobiaApp.kt:2386-2390) | Choices stacked full-width, for long EN/ES/DE labels. |
| Undo snackbar | `SnackbarHostState` owned by the screen (ui/TagReviewScreen.kt:602), `showSnackbar(message, actionLabel=undo, duration=Short)` and `ActionPerformed` to undo (:813-816), plural `review_snack_bulk` | The shell's `Scaffold.snackbarHost` is the back-exit pill, not a `SnackbarHost` (RobiaApp.kt:2119). The screen needs its own host. |
| +/- signed chips | `ReviewChangeChip` (ui/ReviewChangeChips.kt:58-59), `ReviewSwapChip` (:88-89), with `Icons.Rounded.Add` and `Remove` already imported (ReviewChangeChips.kt:20, 22) | Tap toggles with a light haptic tick (ReviewChangeChips.kt:53-57 doc). Reusable as the add/remove visual in the chip style. |
| Alphabetical order | `internal fun <T> List<T>.sortedAlphabetically(label, pinned)` (ui/ManageTagsScreen.kt:1000-1006), Collator PRIMARY | Used by Manage tabs (:298) and the chip row (BrowseFilterBar.kt:517). Use it for tag and collection lists. Browse order (`is_favorite DESC, updated_at DESC`, Dao.kt:18) is best kept for the grid. |
| Min-visible status | `visibleBadge(previous, shownAtMs, current, nowMs)` keeps a shown badge for `BADGE_HOLD_MILLIS` (ui/BrowseCardText.kt:135-137); `SYNC_INDICATOR_HOLD_MS = 1_500` (RobiaApp.kt:752, used at :762 and :791) | The "min-visible" rule in the brief. Reuse for any new status chip. |
| Cloud badges on cards | `GarmentCloudStatusBadge` and friends (BrowseGrid.kt:671-709) | Come with `GarmentGridCard`. |

---

## 7. Icons

Verified against the cached `material-icons-extended` **1.7.6** AAR (`~/.gradle/caches/.../material-icons-extended-release/jars/classes.jar`). The BOM is `2024.12.01` (gradle/libs.versions.toml), which maps to 1.7.6 **[H]** on the mapping, not separately checked.

| Icon | In 1.7.6 Rounded? | Use |
|---|---|---|
| `Icons.Rounded.PlaylistAdd` | **Yes** | Manage row "+/-" affordance (recommended). |
| `Icons.Rounded.PlaylistRemove` | **Yes** | Remove-side glyph if the pair is split. |
| `Icons.Rounded.LibraryAdd` | **Yes** | Alternative for the row. |
| `Icons.Rounded.ChecklistRtl` | **Yes** | Alternative for the screen top bar. |
| `Icons.Rounded.PlaylistAddCheck` | **Yes** | Option for a "saved" state. |
| `Icons.Rounded.Done`, `CheckCircle` | No (not in Rounded) | Do not use. Use `Icons.Rounded.Check` (already used 9x). |

Existing icons already in use for context: `Icons.Rounded.Add` and `Remove` (ReviewChangeChips.kt:20, 22), `Icons.Rounded.LocalOffer` (Manage header, ManageTagsScreen.kt:378), `Icons.Rounded.Edit` and `Delete` (TagListRow, :451, 460).

Recommendation: **`Icons.Rounded.PlaylistAdd`** for the row, plus `ReviewChangeChip` for the add and remove state on each card.

---

## 8. Tests

### 8.1 Existing tests relevant to the change

| Area | Test file | What it covers |
|---|---|---|
| BrowseFilterState | `test/.../ui/BrowseFilterStateTest.kt` | OR/AND semantics, "Not set", colours, favourites, fit, counts, chip removal (`:39-268`). |
| Prune | `test/.../ui/PruneUnknownFiltersTest.kt` | Unknown tags and colours pruned; empty lists mean not loaded (`:16-87`). |
| DragSelect | `test/.../ui/DragSelectTest.kt` | `selectedIds`, `moveTo`, baseline, autoscroll (`:11-69`). Not the composable. |
| Manage page helper | `test/.../ui/ManageTabPagerTest.kt` | `pageFor` (`:11-17`). |
| Manage rules | `test/.../ManagePerformanceContractTest.kt` | ManageTagsScreen.kt must contain `item(key = "tag-row:`, `contentType = "tag-row"`, `HorizontalPager(`, `beyondViewportPageCount = 0`, `key = { page -> tabIds[page] }`. **It asserts `!manage.contains("Card(")` for the whole file** (`:4-27`). Do not write `Card(` in `ManageTagsScreen.kt`. |
| Back handling | `BackExitMachineTest.kt`, `BackExitPolicyTest.kt`, `BackExitWiringContractTest.kt` | Exactly one `BackAction.Exit ->` branch (`:17`). Unaffected unless back logic changes. |
| Search | `test/.../suggestions/SearchControllerTest.kt`, `SearchRankerTest.kt` | Controller and ranking. |
| Tag defaults | `test/.../core/model/DefaultTagsTest.kt` (`:11, 17, 31, 39`) | Iterates `DefaultTags.categories`. Adding a category should pass; check its asserts. |
| Seeding | `test/.../data/DefaultCategorySeedingTest.kt` | Seeding and merge via sqlite-jdbc (see 8.2). |
| Tag-review logic | `test/.../ui/TagReviewSaveTest.kt`, `TagReviewUndoTest.kt`, `suggestions/ReviewChangesTest.kt` | `applyDelta` and undo behaviour. |
| Dark theme literals | `test/.../ui/DarkThemeBrowseScanTest.kt` | Scans **named** files (BrowseGrid, BrowseFilterBar, AdvancedFiltersScreen, ManageTagsScreen, …) for `Color(0x` without `// theme-fixed:`. A new screen file must be added to a scan list. |
| Locale parity | `TagReviewUndoTest.kt:198-199`, `CutoutMethodStringsTest.kt:13-14`, `TagReviewCardModelTest.kt:346-347` | Compare es and de strings files. Keep all three strings files in step (each has 815 strings). |
| Source-string contracts | `RegressionSourceContractTest.kt` (1,118 lines) | **Not read in full.** It matches source text, so grep it before editing RobiaApp.kt. |

### 8.2 Real DAO tests: pattern

- **No Room in JVM tests.** The pattern is sqlite-jdbc in memory: `DriverManager.getConnection("jdbc:sqlite::memory:")` (dependency at `app/build.gradle.kts:174`, `org.xerial:sqlite-jdbc:3.46.1.3`).
- SQL in `Dao.kt` is read from the file as written. `daoStatement(method, bindings)` extracts the `@Query` string after `suspend fun <method>(` and substitutes bindings (`DefaultCategorySeedingTest.kt:139-147`). Example usage in `UnsyncedLocalRowQueryTest.kt`.
- Room-generated methods (`@Insert`, `@Upsert`) have no SQL in Dao.kt, so the test writes them by hand (`DefaultCategorySeedingTest.kt`, the `seedDefaults` comment).
- **Implication.** Kotlin `@Transaction` bodies cannot run in JVM tests. Put the diff logic in a pure function and test the new `@Query` SQL via `daoStatement`.

---

## 9. Proposed file split

### 9.1 New files

| File | Contents | Why new |
|---|---|---|
| `ui/CollectionMembersScreen.kt` | `internal fun CollectionMembersScreen(...)`: own top bar (Close and Save), body, drag and tap, FilterBar, Advanced overlay, discard dialog, snackbar host. | Keeps the screen out of RobiaApp.kt. |
| `ui/CollectionMembership.kt` | Pure, no Android types: `membershipChange(initial, draft): (add, remove)`, `collectionMemberIds(items, collectionId)`, and `planTagMembership(current, add, remove)` (the DAO diff, reused by the DAO method). | Unit-testable without Room. |
| `ui/CollectionDragPaint.kt` | `internal class DragPaintArm` and paint logic: the anchor's target is `!anchorWasMember`, the range is set to that target, and everything else keeps its baseline. | `DragSelect.kt` is union-only (DragSelect.kt:52-57). A new file avoids changing its tested class. |
| `ui/CollectionSearch.kt` (optional) | A `rememberCollectionSearch(...)` helper: `SearchController` plus `queryEmbedding` and `searchInProgress`, copied from RobiaApp.kt:1050-1084. Or move the RobiaShell block into it. | Avoids a third copy later. |
| `test/.../ui/CollectionMembershipTest.kt` | Tests for the pure functions. | New. |
| `test/.../data/CollectionMembershipSqlTest.kt` | sqlite-jdbc tests for `deleteTagRefsForItems` and `touchItemsForSync` via `daoStatement`. | Same pattern as `DefaultCategorySeedingTest`. |

### 9.2 Hot-file edits (minimal regions)

**`ui/RobiaApp.kt`** (8 to 10 regions; see the table in §4.4):
- `RobiaRoute`: add `CollectionMembers` (after :357).
- `needsWardrobeUiModels` (:1033): add `is CollectionMembers`.
- Title (:2036-2046): special-case the name.
- Bottom-bar exclusion (:2097-2109): add one line.
- Optional top-bar skip (:2001).
- RobiaShell params (:941-951 area) and RobiaNavHost params (:3004-3115 area): add `onSaveCollectionMembership`.
- RobiaShell call site (:555-575 area): add the write lambda.
- RobiaNavHost ManageTags branch (:3158-3171): add `onEditMembers`.
- RobiaNavHost `when` (:3116-3387): add the branch.
- Optional, to make `BrowseFilterState` reuse easy: none.

**`ui/ManageTagsScreen.kt`** (5 regions):
- `ManageTagsScreen` signature (:107-120): add `onEditMembers: (GarmentTag) -> Unit` (default `{}` or required).
- Pager call (:209-223) and `ManageTabPage` signature (:281-295): pass it through.
- `TagListRow` (:412-469): add the `+/-` `IconButton` for `categoryId == "collection"`, next to Edit and Delete (:444-466). Keep the `40.dp` size and the `semantics { contentDescription }` pattern.
- `TagListRow` call (:347-355): pass it.
- `localizedName` (:973-979): add `"collection" -> stringResource(R.string.category_collection)`.
- Optional `dotColor` (:982-988) branch.
- Do **not** add `Card(` (`ManagePerformanceContractTest`).

**`ui/BrowseGrid.kt`** (2 edits): `GarmentPhotoTile` (:581) and `GarmentGridCard` (:610) from `private` to `internal`.

**`ui/BrowseFilterBar.kt`** (optional, 1 edit): `categoryTitleRes` (:579-585) add `"collection"` for the "Not set" chip.

**`ui/AdvancedFiltersScreen.kt`** (optional): add `collectionTags` (near :124-127) and a `TagFilterCard(categoryId = "collection", ...)` block (near :218-239). Decide whether collection filtering belongs in Browse at all.

**`ui/CategoryIcons.kt`** (optional, 1 edit): add `"collection"` branch (:14-20).

**`core/model/DefaultTags.kt`** (1 line): add `TagCategory(id = "collection", name = "Collections", sortOrder = 60, isSystem = true)` after :8. `sortOrder` is a suggestion. `seedCategories` is INSERT IGNORE on every launch (LocalRepositories.kt:244), so existing installs get the row with no migration. The DB stays at version 15 (RobiaDatabase.kt:22). **[C]**

**`data/local/Dao.kt`** (1 region): `TagDao` additions near :495-516 (§5.5).

**`data/WardrobeRepository.kt`** (1 region): `TagRepository` method (:66-82).

**`data/LocalRepositories.kt`** (1 region): `LocalTagRepository` method (:167-).

**`res/values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`** (3 files, keep parity): new keys include `category_collection`, `collections_title`, the screen title, `content_edit_collection_members` (or similar), add and remove descriptions, discard title and body, save and undo snackbar text, and an empty state. Each file is 815 strings, so keep the counts equal.

### 9.3 Count

- Hot files: `RobiaApp.kt` (about 8 to 10 small regions), `ManageTagsScreen.kt` (about 5), `BrowseGrid.kt` (2 word edits), three strings files, `Dao.kt` (1 region), `WardrobeRepository.kt` (1), `LocalRepositories.kt` (1), `DefaultTags.kt` (1 line).
- New: 2 to 4 UI files, 2 test files.
- **No schema migration. No DataStore change. No sync protocol change.**

---

## 10. Decisions for the user (my recommendation in bold)

1. **Bump `updated_at` on membership change?** Tag review does (`applyDelta`, ReviewChanges.kt:204-226). Browse orders by `updated_at` (Dao.kt:18), so a large save reorders the grid. **Recommend: keep the sync bump and leave `updated_at` alone** (the sync revision still moves). Confirm.
2. **Drag semantics.** Range only adds with the current `DragSelect`. **Recommend a painted mode** in a new class. Long-press anchor sets the target to `!anchorWasMember`, and the range is painted to that target. Taps toggle single garments.
3. **Own top bar vs. hoisted discard.** **Recommend the screen draws its own top bar** (the AdvancedFilters precedent, RobiaApp.kt:2001), so the top-bar back and the discard prompt live in one place.
4. **Filter entry point.** The member screen needs its own filter state. **Recommend the Advanced sheet as an in-screen overlay** with its own `BrowseFilterState`, not a new route. It has no collection section, which is fine: filtering a collection by its own membership is pointless. The only collection-filter surface in Browse is the chip row, which works automatically.
5. **Removal resurrection.** Union merge with no tombstones (WardrobeSyncOutboxProcessor.kt:1940-1944), on restore and merge paths. **This is the same exposure as garment-tag removal today.** Accept it for v1 and note it, or add a membership tombstone (larger change, touches the sync record format). Impact **[H]**.
6. **Analytics.** `isSystem = true` keeps it out of the analytics custom groups (WardrobeStats.kt:63-64). **Recommend `isSystem = true`.**
7. **Undo.** Optional snackbar, with undo as the same method with add and remove swapped (§5.5, §6).
8. **Pinch on the member screen** writes the Browse column setting. **Recommend a no-op `onColumnsChange`.**
9. **Stale editor race.** The garment editor loads all tags, including collections (AddEditClothingScreen.kt:697, :1009, :1117), and writes them back on save. Membership is therefore preserved on a normal save. A garment editor left open while a collection is saved could overwrite it on save **[H]**, a narrow race.
10. **Mixed-version fleets.** An older app reading a snapshot with a new `collection` category will treat it as a custom category **[H]**. Not traced.

---

## 11. Not verified

- Nothing compiled or ran. No gradle, no tests.
- Not read: `SearchRanker.kt`, `RegressionSourceContractTest.kt` (only its shape), `TagEditorDialog` in full (ManageTagsScreen.kt:592-716, only the category checks), `ReviewScaffold` beyond the dialog, `WardrobeSyncOutboxProcessor` claim logic beyond merge.
- Not confirmed: Room's handling of `@Query` INSERT and empty `IN` lists; `rememberSaveable` with `Set<String>` (use a `List` or a `Saver` for the draft); whether `onColumnsChange` persists.

---

## 12. Quick file:line index (edit regions)

- RobiaRoute: `ui/RobiaApp.kt:279-358`; WardrobeUiModelRoutes `:893-897`; needsWardrobeUiModels `:1033`; search block `:1045-1084`; selection `:996`; prune `:1252-1260`; push/replace/pop `:1262-1273`; toggle/set `:1295-1305`; root BackHandler `:1760-1770`; BackExit handler `:1786-1824`; top bar `:1998-2095`; bottom bar `:2097-2109`; RobiaNavHost call `:2122-2311`; ManageTags wiring `:3158-3171`; `when` `:3116-3387`; ReviewWrites `:3770-3800`.
- Manage: `ui/ManageTagsScreen.kt:107-120` (signature), `:281-295` (ManageTabPage), `:364-409` (TagCategoryHeader), `:412-469` (TagListRow), `:973-988` (localizedName, dotColor), `:1000-1006` (sortedAlphabetically).
- Browse: `ui/BrowseGrid.kt:148-182, 186-227, 233, 260-305, 375-412, 581, 610, 886`; `ui/DragSelect.kt:25-58, 83-89, 156-165`; `ui/BrowseFilterBar.kt:126-176, 299-320, 507-585`; `ui/AdvancedFiltersScreen.kt:110-120, 124-127, 192-239`.
- Search: `suggestions/SearchController.kt:12-15, 36-92`; `suggestions/engine/EmbeddingEngineHolder.kt:39-58`; `ui/ModelDownloadUi.kt:38`; `data/EmbeddingRepository.kt:37`.
- Data: `data/local/Dao.kt:18, 336-337, 374-390, 410-527 (TagDao), 447-451, 495-516, 529-533, 547-578, 612-623`; `data/LocalRepositories.kt:37-83, 167-206, 244`; `data/WardrobeRepository.kt:66-82`; `data/local/Entities.kt:14-39, 54-98, 136-154`; `data/local/RobiaDatabase.kt:22`.
- Sync: `sync/LocalWardrobeSyncSnapshotRepository.kt:61-69, 354-356, 462-467`; `sync/WardrobeSyncOutboxProcessor.kt:993-1027, 1330, 1888, 1940-1944`; `sync/WardrobeSyncGateway.kt:269-300`.
- Review: `ui/TagReviewScreen.kt:101, 425-436, 602, 813-816`; `suggestions/ReviewChanges.kt:204-226`; `ui/ReviewScaffold.kt:120-123, 400-422`; `ui/ReviewChangeChips.kt:58-59, 88-89`.
- Model: `core/model/DefaultTags.kt:4-9`; `core/model/WardrobeStats.kt:63-64`; `core/model/ClothingModels.kt:16, 240-241`; `ui/CategoryIcons.kt:14-20`; `ui/TagLocalization.kt:14-17`.
- Tests: `app/build.gradle.kts:174`; `test/.../data/DefaultCategorySeedingTest.kt:139-147`; `test/.../ui/DarkThemeBrowseScanTest.kt`; `test/.../ManagePerformanceContractTest.kt:4-27`.
