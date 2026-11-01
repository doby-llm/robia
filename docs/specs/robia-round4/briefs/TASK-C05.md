# TASK-C05 — Collection members screen and route

## Goal
The ± on a collection row (C02) opens a Browse-like screen where the user adds/removes many garments to/from that
collection and saves once (SPEC §3, mockup frames 3–6). It must reuse Browse building blocks without sharing Browse
state: Browse selection, filters and search live in `RobiaShell` (`ui/RobiaApp.kt:971-1084`) and the Advanced filters
route edits the shared Browse filters (`:3369-3379`), so this screen owns its own instances.

## Read first
SPEC §3 (R4-D10 … R4-D16), R4-D04; mockup frames 3–6 and copy table; `artifacts/inv-r4-member-screen.md` (all of
§1–§9, especially §4.4 exact RobiaApp regions and §5.5 caller); C04 files (`CollectionMembersSession.kt`,
`CollectionDragPaint.kt`); C01 `TagRepository.setTagMembership`; `ui/BrowseGrid.kt:186-420,581-640`;
`ui/DragSelect.kt:79-170` (gesture shape to mirror); `ui/BrowseFilterBar.kt:299-400` (`FilterBar`);
`ui/AdvancedFiltersScreen.kt:110-120`; `suggestions/SearchController.kt`; `ui/ModelDownloadUi.kt:38`;
`ui/ReviewScaffold.kt:400-422`, `StackedActionDialog` (`RobiaApp.kt:2386`); `DarkThemeBrowseScanTest.kt`.

## Steps
1. `BrowseGrid.kt`: change ONLY `private fun GarmentPhotoTile` and `private fun GarmentGridCard` to `internal`.
2. New `ui/CollectionMembersScreen.kt`:
   `internal fun CollectionMembersScreen(innerPadding, collection: GarmentTag, items: List<UiWardrobeItem>, availableTags, mainColors, vectors…, modelState, browseLayout, columns, onSave: (add: Set<String>, remove: Set<String>) -> Unit, onClose: () -> Unit)`
   (adapt the parameter list to what Browse's search needs; copy the search block of RobiaApp.kt:1050-1084 into the
   screen with its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)` remembered and cancelled in a
   `DisposableEffect`, and call `app.embeddingEngineHolder.preload()` when the model is Ready).
   - Own top bar (frame 3): back arrow, title = collection name, subtitle `collection_members_count` with
     `saved.size → draft.size` when changed. Back arrow and `BackHandler` both go through `session.leaveDecision()`.
   - Body: search field via `FilterBar` (with its own `BrowseFilterState`, `cloudSync` = an idle indicator,
     `onAnalyticsClick = null`), segmented `All / In collection / Not in` (`SingleChoiceSegmentedButtonRow`), grid with
     `GarmentPhotoTile`/`GarmentGridCard` per `browseLayout`; overlay a check badge (filled `primary` = in draft) and
     the signed badge for changed items (`collection_members_added_badge` green container + "+", or
     `collection_members_removed_badge` errorContainer with strike-through). Tap = `session.toggle`. Long-press +
     drag = `DragPaint` with a gesture modifier mirroring `dragSelect` (edge autoscroll, two fingers cancel); light
     haptic on each change. Pinch: no column change (do not call `onBrowseColumnsChange`).
   - Advanced filters: open `AdvancedFiltersScreen` as a full-screen overlay bound to the screen's own filter state;
     hide the Collections card there (pass a flag or filter `availableTags` without collection tags).
   - Bottom bar (frames 3/4): no changes → hint `collection_members_hint` + disabled Save; changes → `+n` / `−m`
     chips, `collection_members_undo_all` (outlined), `collection_members_save` (filled).
   - Leave dialog (frame 6): `StackedActionDialog`-style, title `collection_members_leave_title`, body
     `collection_members_leave_body`, Save / Discard changes (error colour) / Keep editing; outside tap = keep editing.
   - Save: `onSave(session.added, session.removed)` then `onClose()`; double-tap guarded (disable after first tap).
   - `rememberSaveable` the session via `toSaveable`/`fromSaveable`. Prune with `session.pruned(activeIds)` in a
     `LaunchedEffect(items)` guarded so an empty initial list does not wipe the draft (only prune after the first
     non-empty load). If `collectionStillExists` turns false → `onClose()` without writing.
   - Empty wardrobe → `EmptyFiltersCard`-like empty text, no Save. Model absent → no search field (same rule as Browse).
3. `RobiaApp.kt` — ONLY these regions (inv-r4-member-screen §4.4):
   a) `RobiaRoute`: `data class CollectionMembers(val tagId: String) : RobiaRoute { override val titleRes = R.string.manage_tab_collections }`
   b) `needsWardrobeUiModels` (:1033): add `|| currentRoute is RobiaRoute.CollectionMembers` (must be `is`).
   c) Shell top bar (:2001): skip the shell top bar for this route (the screen draws its own), like AdvancedFilters.
   d) Bottom bar exclusion (:2097-2109): add `currentRoute !is RobiaRoute.CollectionMembers`.
   e) `RobiaShell`/`RobiaNavHost` params: `onSaveCollectionMembership: (tagId: String, add: Set<String>, remove: Set<String>) -> Unit`;
      at the RobiaShell call site: `launchReviewWrite { val changed = (add + remove); tagRepository.setTagMembership(tagId, add, remove, System.currentTimeMillis()); changed.map { WardrobeSyncOperation.UpsertItem(it) } }`.
   f) ManageTags branch: `onEditMembers = { tag -> onRouteSelected(RobiaRoute.CollectionMembers(tag.id)) }`.
   g) `when (currentRoute)`: `is RobiaRoute.CollectionMembers -> availableTags.firstOrNull { it.id == currentRoute.tagId }?.let { CollectionMembersScreen(…, onClose = onBack) } ?: LaunchedEffect(Unit) { onBack() }`.
   Before editing, grep `RegressionSourceContractTest.kt` for every line you touch.
4. Tests: `CollectionMembersWiringContractTest` (V-C05-1 … V-C05-3 as source-contract assertions on RobiaApp.kt and
   CollectionMembersScreen.kt, e.g. `contains("is RobiaRoute.CollectionMembers")`, no `clearTags`, no
   `browseFilters`/`selectedBrowseItemIds` in the new file, `setTagMembership` inside `launchReviewWrite`);
   `DarkThemeBrowseScanTest` add the new file (V-C05-4).

## Done when
V-C05-1 … V-C05-4 green in CI; then the independent review (V-C05-5, V-C05-6).

## Do not
Change Browse behaviour, `DragSelect.kt`, sync code, or add Browse multi-select "add to collection"; no snackbar.
