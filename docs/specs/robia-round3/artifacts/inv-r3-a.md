# Browse investigation report (read-only)

Source: `app/src/main/java/com/gusanitolabs/robia/` (paths below are relative to that root). No edits, no builds.

## Cross-cutting facts

- **CERTAIN:** Kotlin 2.4.21 with the Compose plugin (`gradle/libs.versions.toml`). No `composeCompiler {}` block, so strong skipping should be on by default (HYPOTHESIS: default behaviour).
- **CERTAIN:** No baseline profile, macrobenchmark or profileinstaller dependency. Release and staging have `isMinifyEnabled` and `isShrinkResources` (`app/build.gradle.kts`, buildTypes).
- **CERTAIN:** Light-only theme (`core/designsystem/Theme.kt:64-71`). Card surface is `surfaceContainerLowest` = `#FFFFFF`, `surfaceContainerLow` = `#F3F3F4` (`Color.kt:34-35`).
- **CERTAIN:** Browse has no selection bar. Selection is a top-bar mode (see A4).

---

## A1: Scroll smoothness at 4 columns

**Where:** `ui/BrowseGrid.kt` `BrowseWardrobeScreen` (181–389), `GarmentCardShell` (478–583), `GarmentPhotoTile` (587–611), `GarmentGridCard` (614–670), `GarmentCloudStatusBadge` (673–702), `BrowseImagePrefetch` (392–444). Image path: `ui/BoundedGarmentImage.kt:25–68`, `media/ImagePipeline.kt` (`Display` 349–471, `requestFor` 111–133, `prefetch` 136–170, telemetry 236–308, listener 541–636).

4 columns exists only for `BrowseLayout.PhotosOnly` (`ui/BrowseColumns.kt:10-13`), so the 4-column path is always `GarmentPhotoTile`, which has no footer.

**Ranked suspects:**

1. **CERTAIN code, HYPOTHESIS cost: image telemetry runs on the main thread, eagerly, per image event.**
   - `ImagePipeline.kt:307-308` `diskCacheEntryCount()` calls `listFiles()` on the disk cache directory.
   - `ImagePipeline.kt:299-305` `cacheStats()` copies all memory-cache keys into a set and also calls `diskCacheEntryCount()`.
   - `recordPlaceholderVisible` (236), `recordTerminal` (275, used by `recordBind` and `recordFirstDraw`) build the event arguments eagerly. Sampling in `SampledImageTelemetrySink` (`ImagePipelineContracts.kt:132-140`) only drops the log line afterwards.
   - `RobiaCoilEventListener.keyEnd` (561-566) and `record` (612-636) call `cacheStats()` on every request and decode event.
   - Each newly bound card therefore costs roughly 6–8 directory listings and key-set copies on the main thread.
   - `TrackingMemoryCache.set` (516-523) also copies the key set before and after every insert.
2. **CERTAIN: each card issues two image requests, and the prefetch never matches the display key.**
   - `BoundedGarmentImage.kt:38-53`: `measuredSize` starts at zero, so the first request uses the fallback bounds (288×384 from `GRID_THUMBNAIL_MAX_EDGE_PX=384`, `BrowseGrid.kt:143`). `onSizeChanged` (67) then changes the request to the measured bounds. The first request is cancelled.
   - `BrowseGrid.kt:424-437` prefetches 288×384. Real cards are about 4:5 (for example ~307×384 at 2 columns, ~219×273 at 4 columns), so the prefetched bitmaps are never hit. The prefetch only wastes decodes and memory-cache space (32 MB cap).
3. **HYPOTHESIS: the whole item list is rebuilt on every sync-state emission.**
   - `RobiaApp.kt:931-933` `items` is `remember(..., syncState, ...)` and `toUiWardrobeItems` runs over the full list. `filteredItems` follows at 934-938.
   - `UiWardrobeItem` (`BrowseGrid.kt:145-171`) contains `List<UiTag>`, which the compiler treats as unstable. A data class containing an unstable field is probably compared by identity under strong skipping, so every visible card recomposes on each emission.
4. **CERTAIN: an always-on animation sits in the header.** `BrowseFilterBar.kt:354-362` puts `Modifier.aiSurface()` on the sparkle button. `core/designsystem/Motion.kt:84-89` reads an infinite-transition phase during composition, so the header recomposes every frame while the button is visible (that is, while the model is not Ready). The idle keyframe loop runs continuously (`Motion.kt:168-172`).
5. **CERTAIN constructs, small cost each:**
   - `BrowseGrid.kt:497` `CardDefaults.cardElevation(1.dp)` (shadow per card).
   - `BrowseGrid.kt:500` `pressScale` (`Motion.kt:69-81`): per-card `animateFloatAsState` plus `graphicsLayer`.
   - `BrowseGrid.kt:542-554` nested favourite `Surface`.
   - `BrowseGrid.kt:694` `AnimatedVisibility` per card.
   - `BrowseGrid.kt:365` `animateItem` on every item.
   - `BrowseGrid.kt:789` a gradient `Brush` drawn behind each photo.
6. **CERTAIN: per-recomposition work.** `cardMeta` sorts with a `Collator` on every recomposition (`BrowseGrid.kt:628-629`, `BrowseCardText.kt:39-43`). `tileContentDescription` (602) and four `stringResource` calls per card (491, 596, 624–626) also run in composition.
7. **CERTAIN: no `contentType` on `items(...)`** (`BrowseGrid.kt:357`). Minor.

**Minimal change:** gate telemetry so it does no work unless a sink is enabled, or move the directory listing off the main thread. Make prefetch use the same bounds as display, or drop prefetch. Add `contentType`. Consider a baseline profile, which is a new module and therefore not minimal.

**Coupling:** touches `ImagePipeline` (shared with other screens) and `BrowseGrid.kt:392-444`. Risk: the ImagePipeline telemetry is probably used by the perf workflow (`scripts/check_android_performance_baseline_workflow_static.py`). Check before gating it.

---

## A2: White garments are hard to see

**Where:** `ui/GarmentSwatchBackground.kt`.
- `:11` `GARMENT_SWATCH_ALPHA = 0.26f`
- `:23` `White -> #F8F9FA`
- `:32-37` stops = `[surfaceContainerLow (#F3F3F4), swatch @ 26%]`
- `:41-44` `rememberGarmentSwatchBrush` (remembered on `swatch` and `surfaceTop`)

**Root cause (CERTAIN):** a white garment's gradient is near-white over a near-white card (`surfaceContainerLowest` = `#FFFFFF`). There is no contrast. Unknown colour uses `#DADADA` at 26%, which is also faint.

**Callers:** `BrowseGrid.kt:789` (grid card and tile), `ItemDetailScreen.kt:438`, `ColorReviewScreen.kt:442`, `TagReviewScreen.kt:768`. Dots use `swatchColor()` at `BrowseGrid.kt:747` with a 1 dp border.

**PREMISE NEEDS CLARIFICATION:** the request says "se usará un degradado blanco". A white gradient already exists. A white-on-white gradient would make the problem worse. The fix needs a neutral tint that differs from the card, for example a light grey, or a stronger edge or outline for near-white swatches. The planner should decide.

**Minimal change:** special-case `DisplayColorLabel.White` in `garmentSwatchStops` or `swatchColor`, for example a darker stop. Update `GarmentSwatchBackgroundTest.kt:30-47` if the stops change.

---

## A3: Missing "fits / doesn't fit" filter in Advanced filters

**Data (CERTAIN):**
- `core/model/ClothingModels.kt:10` `fitValue: Int? = null`.
- `data/local/Entities.kt:20` `fit_value INTEGER` (nullable). Column added in `RobiaDatabase.kt:99`.
- Values: `0` = does not fit (`ItemDetailScreen.kt:751-758`, `fit_does_not_fit`), `1` = snug, `2` = fits (`FIT_VALUE_FITS`, `AddEditClothingScreen.kt:1831`), `3` = relaxed, `4` = oversized. `null` = unset.
- The editor toggles only `0` and `2` (`AddEditClothingScreen.kt:1009-1060`, a Switch). New items default to `2` (`:607`, `:632`).
- Strings: `strings.xml:315` (`metadata_fit`), `:319-324`.

**Filter state (CERTAIN):** `BrowseFilterState` (`ui/BrowseFilterBar.kt`, class ~110–160) has no fit field. `hasActiveFilters` (~118), `activeFilterCount` (~126), `matches` (138), toggles (546–604) and `ActiveFilterChipsRow` (471–520) all need a fit branch.

**Application (CERTAIN):** in memory, not SQL. `RobiaApp.kt:934-938` `filteredItems` uses `browseFilters.matches`. `AdvancedFiltersScreen.kt:120-123` computes `resultCount` the same way.

**Reset (CERTAIN):** `RobiaApp.kt:1930` `onResetFilters = { browseFilters = BrowseFilterState() }`. The AdvancedFilters Reset is at `AdvancedFiltersScreen.kt:151-153`. `browseFilters` is `remember`, not saveable (`RobiaApp.kt:881`), so filters do not survive process death.

**Where to add UI:** `AdvancedFiltersScreen.kt` `LazyColumn` (~179–240). `FavoritesSwitchRow` (322–343) and `FilterCard` (249–296) are ready templates.

**Open decision:** does `null` count as "fits"? Existing rows with `null` have no value.

**Coupling:** `BrowseFilterState` is shared with A8 and the chips row. Tests to extend: `BrowseFilterStateTest.kt`, `PruneUnknownFiltersTest.kt`.

---

## A4: Multi-select by holding and dragging

**What exists (CERTAIN):**
- Long-press on a card toggles that card's selection. `BrowseGrid.kt:371` and `:381` call `onSelectionToggle`. `combinedClickable` is at `:510-515`.
- In selection mode, a tap toggles (`BrowseGrid.kt:358-364`).
- `RobiaApp.kt:1139-1145` `toggleBrowseSelection`.
- Top bar in selection mode: close `:1717-1721`, count title `:1747-1749`, delete icon `:1757+`.
- Delete dialog `:1584-1600`.
- `RobiaApp.kt:1147-1155` `deleteItemsAndReturnToBrowse`, which calls `onDeleteItems` (`:482-487`). That calls `wardrobeRepository.archiveItems` (`data/LocalRepositories.kt:87`) and enqueues `DeleteItemFolder`. There is no undo.
- Selection state: `RobiaApp.kt:903` (not saveable). Pruned at `:1096-1103`.

**PREMISE PARTLY DOES NOT MATCH:** multi-select already exists. What is missing is drag-to-select. "Borrar una a una" is not what happens: taps accumulate and one bulk delete runs.

**Gesture conflicts:**
- Pinch is a grid-level `pointerInput(browseLayout)` on the `Initial` pass (`BrowseGrid.kt:260-293`). It consumes events only with 2+ pointers, so single-finger scroll and tap are unaffected.
- A drag-select would need either a grid-level pointer gesture that starts on long-press and hit-tests `gridState.layoutInfo.visibleItemsInfo`, or a replacement of `combinedClickable`'s long-press. Auto-scroll at the edges is also needed.
- Risks: stealing vertical scroll, conflicting with pinch (both on the same grid), and losing the per-card `combinedClickable` semantics and TalkBack actions.

**Minimal change:** keep the card-level long-press. Add a grid-level drag that begins only after a long-press has fired, and hit-tests cards by index from `gridState` bounds.

---

## A5: Syncing chip and cloud icon are hard to see; icon only from 4 columns

**PREMISE DOES NOT MATCH for the column part:** no column-aware badge exists. `GarmentCloudStatusBadge` takes no `columns` (`GarmentCardShell` calls it at `BrowseGrid.kt:556-561` without one). Only `favoriteBadgeMetrics` (`ui/BrowseColumns.kt:58-62`) is column-aware.

**Two candidate "chips" exist.** Which one is meant is ambiguous:
1. **Header "Syncing…" indicator** (CERTAIN): `BrowseFilterBar.kt:162-215` `CloudSyncStatusIcon` (shown when `showsBrowseIndicator`, `RobiaApp.kt:675-679`). When Syncing it shows `labelSmall` text "Syncing…" (`strings.xml:86`) plus `PulsingCloudIcon` (`BrowseFilterBar.kt:256-273`), whose alpha pulses from 0.35 to 1.0 over 900 ms. That explains "apenas se ve". Otherwise it is a 22 dp icon only.
2. **Per-card pill** (CERTAIN): `BrowseGrid.kt:710-734` `GarmentCloudBadgePill`, which is a 14 dp icon plus `labelSmall` text on `surface` at 90%. The labels are "Waiting", "Uploading", "Needs attention" or "Sign in" (`BrowseCardText.kt:63-72`, strings `:661-664`). It never says "Syncing".

**Minimal change:** pass `columns` into `GarmentCloudStatusBadge`. At `columns >= 4`, render icon only with `semantics { contentDescription = label }` so TalkBack keeps the text. For the header, lower the pulse floor from 0.35 or use a tinted container.

---

## A6: Sign-in error shown on the banner and on every card

**Banner (CERTAIN):**
- Produced by `WardrobeSyncOutboxProcessor.kt:1503-1506`: `SignInRequired` if `authBlocked || (Disconnected && pendingOperationCount > 0)`.
- Rendered by `SyncAttentionCard.kt:84-135`. `errorCard` at `:92` uses `errorContainer` for `SignInRequired` and `DriveFull`.
- Placed by `BrowseGrid.kt:329-340`, only when items exist. Capped by `MAX_BROWSE_ATTENTION_CARDS = 2` (`RobiaApp.kt:926-929`, const at `:3239`). The `MergeConfirmation` kind is filtered out there too.

**Per-card badge (CERTAIN):**
- `RobiaApp.kt:3195-3221` `resolveForConnection`: when `Disconnected`, `Dirty`, `Queued` and `AuthBlocked` all become `AuthBlocked` (`:3203-3204`).
- `BrowseCardText.kt:71` maps `AuthBlocked` to `CardBadge(CloudOff, badge_sign_in, isError = true)`.
- `cardBadgeFor` (`BrowseCardText.kt:75-82`) keeps error badges first.
- Row-level `AuthBlocked` also comes from the DB (`data/local/Dao.kt:169`).

**Risk / coupling:** if `AuthBlocked` loses its badge, `Dirty` and `Queued` cards lose their "Waiting" badge too, because `resolveForConnection` maps them to `AuthBlocked` when disconnected. The mapping must be changed at the same time, for example to map them to a waiting state. `BrowseCardTextTest.kt:84` and `:99` assert `AuthBlocked` → `badge_sign_in`, so those tests change too.

---

## A7: "Shiny" AI buttons and the sparkle button beside Filters

**The shine (CERTAIN):**
- `core/designsystem/Motion.kt:84-94`: `rememberAiShimmerBrush` and `Modifier.aiSurface(active, shape = CircleShape)`.
- Palette `Motion.kt:64-66`. Idle sweep `:168-172`. Active period `:166`.
- `SparkleBurst` (`Motion.kt:120-147`, `sparklePositions` 150-156) already exists. It is a one-shot 900 ms burst used only in `ReviewCompleteOverlay.kt:93`.

**Call sites of `aiSurface`:**
- `BrowseFilterBar.kt:356`: the sparkle IconButton beside Filters.
- `ItemDetailScreen.kt:298`: "see similar", full width.
- `AddEditClothingScreen.kt:1371`: quick-edit button containing a literal `"✦"`.
- `ColorReviewScreen.kt:108`: dialog confirm.
- `TagReviewScreen.kt:299`: dialog confirm.
- `PhotoProcessingVisuals.kt:165-200` `MaskedShimmer` is a different effect, on the photo-processing screen. It is not an aiSurface button.

**The sparkle button beside Filters (CERTAIN):** it is not a toggle. `BrowseFilterBar.kt:354-362` is an IconButton with `Icons.Outlined.AutoAwesome`. It is shown only when `!searchVisible` (`browseSearchVisible` = `ModelState.Ready`, `ModelDownloadUi.kt:38`). Tapping it opens `ModelPromptDialog` (`BrowseFilterBar.kt:419`), which offers to download the model. It disappears once the model is ready.

**Minimal change:** remove `aiSurface()` and the shine colours from `BrowseFilterBar.kt:355-357`. Use a plain tinted IconButton with a sparkle, either a static `Text("✦")` or `SparkleBurst` on tap. Decide separately whether the other four `aiSurface` call sites change. That is a design choice for the planner.

---

## A8: Free icon slot to the right of Filters

**Row layout (CERTAIN):** `BrowseFilterBar.kt:337-375` (`Row`, 12 dp spacing at `:338`):
- FilterChip "Filters": `:342-348`
- Reset TextButton, only when active: `:349-351`
- Sparkle IconButton, only when model not Ready: `:354-362`
- 1 dp divider: `:365-370`
- `Spacer(weight 1f)`: `:371`
- CloudSyncStatusIcon, only when `showsBrowseIndicator`: `:372-374`

**Width (ESTIMATE, not measured):** the grid content width is 328 dp at 360 dp (16 dp padding each side, `BrowseGrid.kt:295`). With Filters, Reset, sparkle and cloud all visible, the row is roughly 300 dp. A new 48 dp icon with its spacing could overflow to about 360 dp. It fits once the model is Ready (sparkle gone) or when Reset and cloud are hidden.

**Icon:** `Icons.Rounded.Analytics` or `BarChart` from material-icons-extended. Not verified offline, so check it compiles.

**Coupling:** the slot is the same one A7 changes. If A7 keeps a sparkle button there, the analytics icon has to share it.

---

## Other hot-file regions (RobiaApp.kt)

- `RobiaApp` 329–572
- `RobiaShell` 816–2000: `browseFilters` 881, `selectedBrowseItemIds` 903, attention filter 926–929, `items` 931, `filteredItems` 934–938, prune effect 1096–1103, `toggleBrowseSelection` 1139–1145, `deleteItemsAndReturnToBrowse` 1147–1155, `handleAttentionAction` 1442–1457, delete dialog 1584–1600, selection top bar 1714–1760.
- `onDeleteItems` archive path 482–487.
- `cloudSyncIndicator` 630–662, `rememberCloudSyncIndicator` 732–745.
- `RobiaNavHost` 2629–2946. Browse call site 2724–2750.
- `garmentCloudStatusLabel` 2949–2966.
- `resolveForConnection` ~3195–3221.
- `MAX_BROWSE_ATTENTION_CARDS` 3239.

## Coupling summary

- A1 and A4 both touch the grid `pointerInput` and image path.
- A5 and A6 both touch `GarmentCloudStatusBadge` and `cardBadgeFor`, and A6's mapping change affects the "Waiting" badge.
- A7 and A8 share the same slot in the filter row.
- A3 and A8 both touch `BrowseFilterState` and the chips row.
