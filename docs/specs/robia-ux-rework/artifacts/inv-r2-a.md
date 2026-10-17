# Investigation report: R1, R2, R3, R9, R10, R12, R13 plus design system and haptics

Paths are relative to `app/src/main/java/com/gusanitolabs/robia/` unless noted. Strings are in `app/src/main/res/values/strings.xml`.

## Hot-file map (`ui/RobiaApp.kt`, 5569 lines)

| Function | Lines |
|---|---|
| `BrowseFilterState` | 342-372 |
| `RobiaShell` (state and callbacks) | 858-2013 (`browseFilters` 916, `browseQuery` 917, `manageTagsTabId` 922, `onResetFilters` 1950, `onClearSearch` 1989) |
| `SettingsScreen` | 2014-2243 |
| `SettingsSection` | 2245-2266 |
| `RobiaNavHost` | 3108-3390 (Manage call 3236-3240, AdvancedFilters 3378-3388) |
| `BrowseWardrobeScreen` | 3392-3515 |
| `FilterBar` | 3576-3700 |
| `GarmentGridCard` | 3766-3871 |
| `GarmentCloudStatusBadge` | 3873-3902 |
| `garmentCloudStatusIcon` / `Tint` | 3975-3996 |
| `GarmentPhotoPlaceholder` | 3998-4055 |
| `TonalTag` | 4087-4101 |
| `EmptyFiltersCard` | 4641-4675 |
| `AdvancedFiltersScreen` | 4726-4865 |
| `FilterSection` / `FilterTagChips` / `ColorPaletteChips` | 4867-4936 / 4938-4988 |
| Filter toggles | 4990-5028 |
| `LanguageSettingsScreen` | 5250-5295 |
| `toUiWardrobeItems` | 5297-5346 |

`ManageTagsScreen.kt` (994 lines) holds the Manage tabs at lines 102-300.

## R1 — Horizontal swipe between Manage tabs

**CERTAIN**
- Tabs are a `ScrollableTabRow` with one `Tab` per category, plus a Colors tab. Lines 159-185 of `ManageTagsScreen.kt`.
- `care` is hidden (line 110). `activeTabId` falls back to the first tab when the saved id is stale (line 127).
- Selection state is hoisted: `manageTagsTabId` at `RobiaApp.kt:922` (`rememberSaveable`), wired at 1960 and 3237.
- Tab body is one `LazyColumn` with `weight(1f)` (ManageTagsScreen.kt:186 onward). Row composables `TagListRow` (352) and `ColorListRow` (454) have no horizontal scrolling.
- Repo-wide grep finds no `HorizontalPager`, `LazyRow`, `SwipeToDismiss`, or `draggable`. Pointer input appears only in `AddEditClothingScreen.kt:1812, 2018`. The only `horizontalScroll` is `SuggestionDiagnosticsCard.kt:75`, which is not in Manage.
- Versions: `gradle/libs.versions.toml:5` sets `composeBom = "2024.12.01"`. Foundation, material3 and ui are versionless and come from the BOM (lines 29-33).
- Precedent for opt-ins: `@OptIn(ExperimentalFoundationApi::class)` at `RobiaApp.kt:3767`.

**HYPOTHESIS**
- A `HorizontalPager` is feasible on the BOM 2024.12.01 foundation line. It probably needs an `ExperimentalFoundationApi` opt-in. Not verified against the artifact.
- Fix sketch: `rememberPagerState(pageCount = { tabIds.size }, initialPage = tabIds.indexOf(activeTabId))` in `ManageTagsScreen`. Use `LaunchedEffect(pagerState.settledPage)` to call `onSelectedTabIdChange(tabIds[page])`. Tab click calls `animateScrollToPage`. Keep `ScrollableTabRow` as the indicator. Touch only `ManageTagsScreen.kt:102-300`; the hoisted contract stays.
- Risks: Android back-gesture at the left edge may fight the swipe. Per-page `LazyColumn` scroll state may be lost unless `beyondBoundsPageCount` is set.

**Premise:** partly matches. Tabs already scroll horizontally as a strip. Swiping between pages does not exist.

## R2 — Advanced Filters look ("labels floating")

**CERTAIN**
- Opened from the Filters chip via `RobiaRoute.AdvancedFilters` (`RobiaApp.kt:3209, 3378-3388`).
- Sections, top to bottom, in `AdvancedFiltersScreen` (4726-4865), a `LazyColumn` with 24dp padding and 18dp spacing:
  1. Header: title `advanced_filters_title` ("Advanced Filters") and a `TextButton` "Reset all" (`reset_filters`), enabled when `hasActiveFilters`. Lines 4736-4776.
  2. Favorites: one `FilterChip` (`favorites_only`), 4779-4795.
  3. Category: `FilterTagChips`, single-select via `toggleSingleValueTag`.
  4. Season: multi-select via `toggleTag`.
  5. Occasion: multi-select via `toggleTag`.
  6. Location: single-select via `toggleSingleValueTag`.
  7. Color Palette: `ColorPaletteChips` (4938-4988), with swatch leading icons and a count line.
  8. `EmptyFiltersCard` when results are zero.
  9. "Show N Results" `Button`, 4855-4862.
- Why it looks floating: `FilterSection` (4867-4896) is an uppercase `labelLarge` title over bare content. `FilterTagChips` (4898-4936) is a `FlowRow` of `FilterChip`s with no `Card` or `Surface`. Settings uses `SettingsSection`, a Card with `surfaceContainerLowest` (2250-2262), which the filter screen lacks.
- State: `BrowseFilterState` (342-372) holds `selectedTagIds`, `selectedUnsetTagCategoryIds`, `selectedPaletteColorIds` and `favoritesOnly`. It is hoisted in `browseFilters` (916) and passed through `onFiltersChange` (1949).
- Reset: "Reset all" calls `onResetFilters`, which is `browseFilters = BrowseFilterState()` (1950). It clears only filters.

**HYPOTHESIS**
- Category and Location are single-select, while Season and Occasion are multi-select. That is inconsistent in code. Confirm whether it is intended before a restyle.

**Fix touch:** `FilterSection`, `FilterTagChips` and `ColorPaletteChips` (4867-4988), probably by wrapping each section in a container.

## R3 — Reset button next to "Filters"

**CERTAIN**
- The Filters button is a `FilterChip` at `RobiaApp.kt:3628-3632` in `FilterBar` (3576-3700). The Row at 3623-3652 is ordered as follows:
  1. Filters chip (`selected = hasActiveFilters`, label `filterLabel`).
  2. Sparkle `IconButton` at 3635, shown only when search is hidden.
  3. A 1dp divider `Spacer` (3642).
  4. `Spacer(weight 1f)` (3648).
  5. `CloudSyncStatusIcon` (3650), shown when `headline.showsBrowseIndicator`.
- Label: `active_filters_count` gives "Filters (N)" (`strings.xml:128`). `activeFilterCount` (354-358) sums tags, unset categories, palette colours and favourites. `hasActiveFilters` is at 348-352.
- Reset paths:
  - (a) AdvancedFilters "Reset all" (4773) resets only `browseFilters` (1950).
  - (b) Browse empty-state `EmptyFiltersCard` (3488-3492) calls `onResetFilters()` and then `onClearSearch()`. `onClearSearch` (1989-1992) also clears `browseQuery` and `similarToGarmentId`.
- `FilterBar` does not receive `onResetFilters`. It must be added to its parameter list and to the call in `BrowseWardrobeScreen` (3446-3459). `BrowseWardrobeScreen` already receives it at line 3407.

**PREMISE DOES NOT MATCH (partly):** The E09 note that "Reset filters also clears search/similar" is not in `docs/specs/robia-embeddings-sync-manage/briefs/TASK-E09.md`, which contains no "reset". The claim is true only for path (b) above. Reset from AdvancedFilters does not clear search.

**Placement:** insert after the Filters chip (after 3632) and before the sparkle button (3635). Disable when `!hasActiveFilters`. The sparkle button's presence shifts the position, so the layout must not depend on it.

**Decision for the planner:** whether the new button clears search as well as filters.

## R9 — Garment card: "Synced" text and cloud icon, whitespace

**Card elements, top to bottom** (`GarmentGridCard` 3766-3871):
- `Card` (3780): `fillMaxWidth`, `surfaceContainerLowest`, elevation 1dp, default shape (12dp). Selection border is 2dp primary at 3773-3777.
- `Box` (3796) containing:
  - `GarmentPhotoPlaceholder` at 3797: `fillMaxWidth`, `aspectRatio(3f/4f)`.
  - Favourite `Surface` at top-end, 8dp padding (3804).
  - `GarmentCloudStatusBadge` at bottom-end, 8dp padding (3815-3819). This is the cloud icon: a circle with an 18dp icon and 6dp padding.
  - Selection check at top-start (3830).
- `Column` with 12dp padding and 6dp `spacedBy` (3848-3852):
  1. `name`: `titleSmall`, SemiBold, 1 line, ellipsis.
  2. `subtitle`: `bodyMedium`, `onSurfaceVariant`, 1 line, ellipsis (3859-3864).
  3. `Row` with `spacedBy(6)` containing `item.tags.take(2)` as `TonalTag` (3866-3867). The Row is always emitted, even when empty.

**Origin of "Synced":** `subtitle = item.notes.ifBlank { context.getString(cloud.labelRes) }` (`RobiaApp.kt:5306-5310`). For any garment without notes, the label is `garment_sync_synced` = "Synced" (`strings.xml:141`). It duplicates the badge.

**Origin of the cloud icon:** `garmentCloudStatusIcon` (3975-3985) maps `Synced` to `CloudDone`, tinted `primary` (3987-3996).

**Ambiguity:** there is also a global `CloudSyncStatusIcon` at the right end of the FilterBar row (3650). The user most likely means the per-card badge.

**Whitespace**
- CERTAIN: a card without tags still has the 6dp spacer and the empty Row, plus 12dp bottom padding.
- HYPOTHESIS: most of the perceived blank is the 3:4 photo for garments with no photo. `GarmentPhotoPlaceholder` (3998-4055) shows a 96dp icon box on a gradient. The 16dp grid gap also contributes.

## R10 — Card and image corners don't match

**CERTAIN**
- `Card` (3780) sets no `shape`, so it uses `shapes.medium` = 12dp. Theme values are in `core/designsystem/Theme.kt:49-54`: small 8, medium 12, large 16.
- Photo: `GarmentPhotoPlaceholder` applies `.clip(MaterialTheme.shapes.large)` (16dp) at 3999 onward.
- The selection border uses `shapes.medium` (12dp, at 3775).
- Result: the photo's 16dp top corners sit inside the card's 12dp clip. The wedge between them shows `surfaceContainerLowest` in the top-left and top-right. The photo's bottom corners also show a small notch against the text area.
- `docs/ux/earth_steel/DESIGN.md` (Shapes, lines 118-121, and Components, lines 123-127) specifies 16px for cards. The code's card is 12dp, which contradicts the doc.

**Minimal fix:** pass `shape = MaterialTheme.shapes.large` to the `Card` at 3780, and to the selection `border` at 3775. Do not change `RobiaShapes` globally, because every `Card` would change.

## R12 — Settings does not scale with languages

Current Settings rows, in order (`SettingsScreen` 2014-2243, `contentPadding` 24dp, `spacedBy` 16dp):
1. **Smart suggestions** (`SmartSuggestionsSettingsSection`, `ModelDownloadUi.kt:106`). A Card with state-dependent buttons, not `ListItem`s.
2. **Language** (2077). One `ListItem` "Language" (`language`), subtitle = current label, which opens `RobiaRoute.LanguageSettings` via `onLanguageClick`. Then `LanguagePreference.entries.forEach` at 2084 emits one `ListItem` per enum value: System, English, Spanish, German. That is 5 rows.
3. **Developer Mode** (2103, only if `developerModeUnlocked`). A switch row. If enabled, two more rows: `restore_sync_log_title` and `dev_model_benchmark`, with Run and Copy buttons.
4. **Data sync** (2180). "Delete backup from Google Drive" appears only when Connected or NeedsAttention. Always present: the Google Drive row (`data_sync_google_drive` or `drive_backup_again`).
5. **DeveloperCloudSyncCard** (2239, only if developer mode is enabled; section `dev_cloud_title` at 2444).

Default visible state (developer mode off, Drive not connected): 3 cards, 6 `ListItem` rows, plus the model card.

**Adding one language touches four places:**
- The enum at `core/model/ClothingModels.kt:99-104`.
- The `labelRes` `when` branch at `RobiaApp.kt:3080-3086`.
- The `language_choices` string array in `values/`, `values-es/` and `values-de/` (`strings.xml:488-493`).
- A new inline row generated by the enum loop at 2084.

**Duplicate list:** `LanguageSettingsScreen` (5250-5295) renders the string array as read-only `ListItem`s with no `clickable`. It is reached from the Language row and is a dead end. Selection happens only in the inline list in Settings.

**Minimal fix:** keep one Language row showing the current label. Make a single selectable list (driven by the enum) the destination, and delete the inline `forEach`.

## R13 — Browse grid: uneven heights, clipped chips

- Grid: `LazyVerticalGrid(GridCells.Adaptive(160.dp))`, 16dp spacing in both directions (`RobiaApp.kt:3435-3445`). It is not staggered, so cards in the same row have different heights.
- Height varies because the tag `Row` (3866) is always emitted. A card without tags is shorter by the chip height. The 6dp spacer still applies.
- Chips: `item.tags.take(2)` (3867) in stored order. Nothing sorts or prioritises categories or occasions.
- `TonalTag` (4087-4101): `Surface` with `shapes.small` (8dp), `labelMedium`, padding 8×5dp, and `Text(maxLines = 1)` with no `overflow`. The default is Clip, so glyphs are cut, which explains "Dressed-u". The Row children have no weight, so the second chip can be clipped to nothing.
- Fix: add `overflow = TextOverflow.Ellipsis` and give the chips `Modifier.weight(1f, fill = false)` or a `FlowRow`. `AddEditClothingScreen.kt:2310` has a separate `DetailTonalTag`, which I did not inspect in detail.
- `TagDisplayPolicy` (`core/model/TagDisplayPolicy.kt:3-12`) only decides between a stored name and the canonical default name. It does not limit or truncate chips.
- "Dressed-up" is `tag_dressed_up` (`strings.xml:185`).

## Design system, docs, haptics and animation

- **Design system:** `core/designsystem/` contains `Color.kt`, `Type.kt`, and `Theme.kt`. Shapes are at `Theme.kt:49-54`. The M3 color scheme is set at `Theme.kt:` lines 9 onward.
- **Earth & Steel:** `docs/ux/earth_steel/DESIGN.md`. Shapes at 118-121, layout at 108-111 (24dp margins, 16dp gutters, 8px baseline).
- **Haptics:** none. Grep for `HapticFeedback`, `performHapticFeedback`, `vibrat*` and `Vibrator` finds nothing in `app/src/main`.
- **Animations present:**
  - `AnimatedVisibility` for the search field in `FilterBar` (3611-3616).
  - `AnimatedVisibility` in `ui/ReviewScaffold.kt:123`.
  - `animateFloatAsState` in `TagReviewScreen.kt:427`, `ColorReviewScreen.kt:241`, `BatchAddClothingScreen.kt:298`, and `RobiaApp.kt:2824`.
  - Infinite pulse on the cloud icon: `PulsingCloudIcon` (`RobiaApp.kt:2419-2437`).
- **Ripples and indication:** default indication only, with no custom `indication`.
- **Long press:** `GarmentGridCard` uses `combinedClickable` with `onLongClick` (3780-3795). **HYPOTHESIS:** Compose foundation already performs a long-press haptic here. Verify on device before adding one.

## Coupling and risks

- **Hot-file collisions:** R2, R3, R9, R10, R13 all touch `RobiaApp.kt`. R1 touches `ManageTagsScreen.kt` but changes the `selectedTabId` contract at `RobiaApp.kt:3237`. Serialise edits to the `RobiaApp.kt` regions above.
- **R2 and R3 share** `BrowseFilterState` and the `onResetFilters` wiring (`RobiaNavHost` 3153, 3386, 3407). A change to reset semantics affects both.
- **R9, R10, R13** all edit `GarmentGridCard`. Do them in one pass.
- **R12** has no coupling with the others beyond `RobiaApp.kt:2014-2243`.
