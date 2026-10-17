# TASK-U10 — Pinch to change the number of Browse columns

## Goal
Manu (rev 3, R16): pinch in/out on Browse like Google Photos to change the number of columns, Browse only, with a minimum and maximum (REQ-BR-08, UX-DEC-017). No new library.

## Read first
TS§2b; `ui/BrowseGrid.kt` (after U00/U02): `BrowseWardrobeScreen` and its `LazyVerticalGrid(columns = …)`, `items(items, key = { it.id })`, the `browseLayout` parameter from U02; `data/SettingsRepository.kt` and `Preferences.toRobiaSettings()` (U01) for how `browseLayout` is stored; `core/model/ClothingModels.kt` `RobiaSettings`; `ui/AddEditClothingScreen.kt:1813` for the project's `awaitEachGesture` style.

## Steps
1. Settings: add `browseColumns: Int = 2` to `RobiaSettings`; key `browse_columns` (int); `toRobiaSettings()` maps a missing or out-of-range value to 2; `setBrowseColumns(n: Int)` in the repository interface + DataStore implementation (+ the test fake, if one implements the interface).
2. New pure file `ui/BrowseColumns.kt`:
   - `internal fun browseColumnRange(layout: BrowseLayout): IntRange` = 2..4 for `PhotosOnly`, 2..3 for `PhotosWithDetails`.
   - `internal fun nextColumnCount(current: Int, cumulativeZoom: Float, range: IntRange): Int`: zoom ≥ 1.25 → `current - 1`; zoom ≤ 0.8 → `current + 1`; else `current`; always coerced into `range`.
3. `BrowseWardrobeScreen` gets `columns: Int` and `onColumnsChange: (Int) -> Unit`. The effective count is `columns.coerceIn(browseColumnRange(browseLayout))`. Grid uses `GridCells.Fixed(effective)` (replacing `GridCells.Adaptive(160.dp)`).
4. Gesture: on the grid's parent `Box`, a `pointerInput(effective, browseLayout)` with `awaitEachGesture`:
   - Read events with `awaitPointerEvent(PointerEventPass.Initial)`.
   - Act only while ≥ 2 pointers are pressed: multiply a local `zoom` by `event.calculateZoom()` and consume those changes. With 1 pointer, do nothing and do not consume, so scrolling, taps and long-press selection work as today.
   - When `nextColumnCount(effective, zoom, range)` differs: call `onColumnsChange(new)`, reset `zoom = 1f`, and remember `gridState.firstVisibleItemIndex` so a `LaunchedEffect(effective)` can `scrollToItem(thatIndex)` after the change.
   - Visual feedback while pinching: `graphicsLayer { scaleX = scaleY = zoom.coerceIn(0.94f, 1.06f) }` on the grid, animated back to 1 with `animateFloatAsState` when the fingers lift.
5. Every garment item gets `Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)` so tiles glide to their new place. Full-span header items keep no animation.
6. `ui/RobiaApp.kt`: pass `settings.browseColumns` and `{ scope.launch { settingsRepository.setBrowseColumns(it) } }` (only the `BrowseWardrobeScreen(` call site).
7. Thumbnails: the requested thumbnail edge must not change with the column count (keep `GRID_THUMBNAIL_MAX_EDGE_PX`), so zooming does not trigger new decodes.

## Tests
`BrowseColumnsTest` (V-U10-1): range per layout; thresholds 1.25 / 0.8; clamping at both ends; no change between thresholds. `SettingsMappingTest` addition (V-U10-2): missing/5/1 → 2, 3 → 3.

## Done when
V-U10-1..3, CI green; device row V-Z2-23.

## Do not
Add a library, change filters/search/selection, pinch on any other screen, or change the card/tile design (U02).
