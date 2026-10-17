# Verification matrix — robia-ux-rework

Levels: **CI** = unit/JVM test or build check run by GitHub Actions on the PR; **REC** = evidence pasted in the receipt (command output); **REV** = independent reviewer checks; **DEV** = device row for Manu (Z02 checklist, staging APK from main).

| ID | Task | Level | Check | Pass |
|---|---|---|---|---|
| V-U00-1 | U00 | REC | `python3 tools/split-check/declarations.py <base-ref> HEAD` | "EQUAL" (same multiset of top-level declarations under `ui/`, visibility ignored) |
| V-U00-2 | U00 | CI | `testDebugUnitTest assembleDebug assembleStaging` | green; contract tests read `uiSource()` where symbols moved; no assertion removed (`git diff` of tests shows only path/helper changes) |
| V-U01-1 | U01 | CI | `MotionTest` (pure helpers): `SparkleBurst` angle/distance table is deterministic for a seed; reduced-motion flag read from a fake scale 0f → true, 1f → false; grep: no `performHapticFeedback` / `Haptic` in `app/src/main` | pass |
| V-U01-2 | U01 | CI | the `preferences → RobiaSettings` mapping is extracted to `internal fun Preferences.toRobiaSettings()` (move-only) and tested with `mutablePreferencesOf()`: absent `browse_layout` → PhotosOnly; "PhotosWithDetails" → PhotosWithDetails; garbage → PhotosOnly | pass |
| V-U02-1 | U02 | CI | `BrowseCardTextTest`: `cardTitle("", [Dresses], u) == "Dresses"`; `cardTitle("Mine", …) == "Mine"`; no category → untitled; `cardMeta` orders occasions alphabetically then seasons, joined " · " | pass |
| V-U02-2 | U02 | CI | `badgeFor` test over every `GarmentSyncStatus` value matches the brief table (Synced, LocalOnly → null) | pass |
| V-U02-3 | U02 | REV/REC | grep: no `garment_sync_synced` used as card subtitle; `Card(` in `GarmentGridCard` has `shape = MaterialTheme.shapes.large`; footer height fixed | matches |
| V-U02-4 | U02 | CI | build green | — |
| V-U02-6 | U02 | CI | `BadgeHoldTest`: pure `visibleBadge(previous, shownAtMs, current, nowMs)` keeps a shown badge until ≥ 1200 ms passed, then shows `current` (null hides) | pass |
| V-U02-5 | U02 | CI | `BrowseCardTextTest`: `tileContentDescription(name="", tags=[Dresses, Summer])` == "Dresses, Summer"; layout default PhotosOnly renders `GarmentPhotoTile` (pure `browseCellFor(layout)` mapping) | pass |
| V-U03-1 | U03 | CI | `BrowseFilterStateTest` semantics table (brief): OR within category, AND across, Not set OR selected-in-same-category, colours OR, favourites AND, empty = all | pass |
| V-U03-2 | U03 | CI | count badge = number of active entries per section; `hasActiveFilters` unchanged meaning | pass |
| V-U03-3 | U03 | REV | Reset in bar clears filters only; empty-state button behaviour unchanged; every chip ✕ removes exactly its filter | confirmed |
| V-U03-4 | U03 | CI | sorting test: locale collator (ES "Ñ" after "N"), "Not set" last | pass |
| V-U04-1 | U04 | CI | build + ManagePerformanceContractTest green; pager keyed by tab id | pass |
| V-U05-1 | U05 | CI | `LanguagePickerTest` on pure `languagePickerRows(current: LanguagePreference): List<LanguageRow>`: size = `LanguagePreference.entries.size`, exactly one selected = current, order System first then entries order | pass |
| V-U05-2 | U05 | REV/REC | grep: no inline `LanguagePreference.entries.forEach { ListItem` left in Settings; Drive actions all reachable from DriveSettings (list each action → its new location in the receipt) | confirmed |
| V-U05-3 | U05 | CI | build green | — |
| V-U06-1 | U06 | CI | `GarmentFieldRowsTest` (pure): value text for multi-select joins alphabetical labels; aiSuggested rule | pass |
| V-U06-2 | U06 | REV | `onSave(ClothingItem(...))` arguments identical to base (diff of that block empty or reviewer-justified); single-select categories still single | confirmed |
| V-U06-3 | U06 | REV/REC | no `DetailMetadataGrid`, no `MetadataShellCard` left (grep prints nothing) | confirmed |
| V-U06-4 | U06 | CI | build green | — |
| V-U06-5 | U06 | REV | `RobiaRoute.BatchEditClothing` still renders `AddEditClothingScreen` (grep `RobiaApp.kt`), so batch edit shows the same rows (REQ-GF-04) | confirmed |
| V-U07-1 | U07 | CI | `ProcessingStageMappingTest`: `processingStep(stage)` for every `PhotoProcessingStage` value per TS§7 | pass |
| V-U07-2 | U07 | REV/REC | reduced-motion branch present for each animation (grep `LocalReducedMotion`) | confirmed |
| V-U08-1 | U08 | REV/REC | review accept/discard/commit logic diff empty (only animation/modifier lines changed in review files) | confirmed |
| V-U08-2 | U08 | CI | `ReviewCompleteTextTest`: `reviewCompleteText(Tags, 5)` → (Tags, 5); (Colors, 1); 0 → null (no overlay); `reviewCompleteDurationMs(false) == 1600`, `(true) == 1200` | pass |
| V-U08-3 | U08 | REV/REC | grep: `aiSurface(` on the Browse sparkle button, the detail Similar button and the primary action of both review prompt dialogs; U06's "Edit photo" button uses it too | confirmed |
| V-U09-1 | U09 | CI | `PdfLayoutTest` (fake measurer): typical garment (title 40, notes 250, 3 occasions) → 1 page, every box inside 44 pt margins and footer; empty notes → swatches start ≤ 24 pt below the title; 200-char title + 2000-char notes → overflow continues on page 2, nothing outside margins ; sparse: no name + category Dresses → title "Dresses"; no name, no category → "Garment"; no notes → no notes box and photo box height > 430 pt; no colours → no swatch row; no gap > 40 pt above the footer in all cases | pass |
| V-U09-2 | U09 | REC/CI | Inter release URL + sha256 for each TTF; OFL file present; rewritten PDF design tests + unchanged `garmentPdfExport_keepsRequiredMetadataGridOrder` green | listed |
| V-T01-1 | T01 | CI | `EmbeddingRecipeTest` 27 descriptions; ids `category-socks`, `category-underwear` with REQ-TX-01 text; `category-accessories` text equals REQ-TX-02 | pass |
| V-T01-2 | T01 | CI | sqlite-jdbc DAO test: fresh seed → 21 categories; tombstoned `category-socks` not resurrected; custom "Socks" + seed → one row with id `category-socks`, garments repointed; `SUGGEST_OCCASIONS_DEFAULT_OFF` contains both ids | pass |
| V-T01-3 | T01 | CI | GoldenFixtureTest unchanged and green (fixture not regenerated) | pass |
| V-SG1-1 | SG1 | CI | `SegmentationModuleGateTest` with fake port: available → Available; installs after N → Available within timeout; never → Unavailable after timeout (virtual time) | pass |
| V-SG1-2 | SG1 | CI | engine flow test with fakes: UNAVAILABLE then success → REMOVED (one retry); UNAVAILABLE twice → ORIGINAL_FALLBACK reason MODEL_UNAVAILABLE | pass |
| V-SG1-3 | SG1 | CI | `AlphaRefinerTest`: output in 0..255; flat 1.0 region → 255; step edge monotone; smoothstep bounds | pass |
| V-SG1-4 | SG1 | REV | batch NeedsReview(background fallback) shows Retry; module install never blocks the UI thread; fallback keeps the original | confirmed |
| V-SG2-1 | SG2a | REC | curl-verified sha256 + size for 4 pack files + bundled tflite equal TS§SG2a table | equal |
| V-SG2-2 | SG2a | CI | `MaskOpsTest`: third-points on synthetic masks, overlap, components, island filter, hanger kill cap 15 %, plausibility rule | pass |
| V-SG2-3 | SG2a | CI | `ModelStoreTest` existing tests green + new: pack file name/sha params; Gemma defaults unchanged | pass |
| V-SG2-4 | SG2a | REV | fallback chain precise → ML Kit → original on every exception/timeout; sessions closed (`use {}`); no model resident after a photo; assets contain no real photo (only generated) | confirmed |
| V-SG2-5 | SG2a | REC | APK size delta (debug + staging) reported; abiFilters fallback applied if > 25 MB | reported |
| V-B01-1 | B01 | CI | `BatchProcessingCoordinatorTest` (a)–(h) of TS§B01 incl. the three existing cancellation tests | pass |
| V-B01-2 | B01 | REV | no draft can stay Queued/Processing after cancel; publication order = orderIndex; suggestion worker single; parallelism 1 when precise engine active | confirmed |
| V-B01-3 | B01 | REC | developer log line per garment `batch_item idx=… cutout_ms=… suggest_ms=… wait_ms=…` and a final `batch_total_ms=…` (Logcat tag `RobiaBatch`) so V-Z2-21 can compare | present |
| V-B02-1 | B02 | CI | `BatchEtaTest` (fake clock): null before 2 completions; mean of last 4 gaps × remaining; rise capped at 20 %; seconds rounded up to 10; < 10 s → AlmostDone; ≥ 90 s → minutes | pass |
| V-B02-2 | B02 | CI | `BatchRowSummaryTest`: category · occasions alphabetical; empty while Queued; Failed → error text | pass |
| V-B02-3 | B02 | REV/REC | grep: `batch_helper_active_summary` not shown; status text not repeated under the chip; list is `LazyColumn` | confirmed |
| V-SG2-6 | SG2b | CI | `Preferences.toRobiaSettings()` with `mutablePreferencesOf()`: absent key → `preciseCutoutEnabled == true`; `useEnginePrecise(packReady, enabled)` truth table | pass |
| V-SG3-1 | SG3 | CI | `RefineSessionTest`: keep/remove union/subtract with smart (hard) and brush (soft) masks, undo ≥ 10, reset; `BrushStrokeTest`: `brushRadiusPx(24f, density = 2f, zoom = 2f) == 24f`; soft edge (centre 1, beyond radius + feather 0) | pass |
| V-SG3-2 | SG3 | REV | Done writes a new PNG and updates editor state only; Cancel writes nothing | confirmed |
| V-SG3-3 | SG3 | CI | build green | — |
| V-SG3-4 | SG3 | REV/REC | grep: no `QuickEditDialog` left; Light tab uses `QuickEditImageProcessor` unchanged; Done calls `reprocessEditedPhoto` | confirmed |
| V-Z2-01 | Z02 | DEV | Browse: all cards in a row equal height; no gap at photo corners; no "Synced" text; long tags end with "…" | PASS/FAIL |
| V-Z2-02 | Z02 | DEV | Browse: a garment uploading/failing shows its badge; synced shows none | PASS/FAIL |
| V-Z2-03 | Z02 | DEV | Filters active → Reset next to Filters clears them; chip ✕ removes one | PASS/FAIL |
| V-Z2-04 | Z02 | DEV | Filters screen: grouped cards, "+N more", colour grid, "Show N garments" count right; Summer+Winter returns garments with either | PASS/FAIL |
| V-Z2-05 | Z02 | DEV | Manage: swipe left/right changes tab; vertical scroll still smooth | PASS/FAIL |
| V-Z2-06 | Z02 | DEV | Settings grouped rows; Language sub-screen changes language; Browse layout switches between photos only and photos with details; 7 taps on the Settings tab still unlock Developer | PASS/FAIL |
| V-Z2-07 | Z02 | DEV | Google Drive sub-screen: every action you used before is there | PASS/FAIL |
| V-Z2-08 | Z02 | DEV | Add garment: rows + bottom sheets; ✦ on suggested values; save keeps everything | PASS/FAIL |
| V-Z2-09 | Z02 | DEV | Garment detail: rows instead of boxes | PASS/FAIL |
| V-Z2-10 | Z02 | DEV | Processing: sweep → masked shimmer → pop; feels smooth, not excessive | PASS/FAIL |
| V-Z2-11 | Z02 | DEV | Review Accept/Discard: card slides out; AI buttons shimmer subtly; no vibration anywhere | PASS/FAIL |
| V-Z2-12 | Z02 | DEV | Share PDF: one A4 page for a normal garment, matches mockup (no Location row, as before) | PASS/FAIL |
| V-Z2-13 | Z02 | DEV | Manage > Category lists Socks and Underwear (ES Calcetines, Ropa interior) | PASS/FAIL |
| V-Z2-14 | Z02 | DEV | Photo of socks → suggested category Socks (or none), not Trousers | PASS/FAIL |
| V-Z2-15 | Z02 | DEV | 10 photos in a row: no "waiting for module" failure; if it appears, it recovers alone or Retry works | PASS/FAIL |
| V-Z2-16 | Z02 | DEV | Cutout edges look smooth at card size and in detail (no stair steps) | PASS/FAIL |
| V-Z2-17 | Z02 | DEV | Settings > Precise cutout: download, switch, delete work; G-SEG-2 table filled | PASS/FAIL |
| V-Z2-18 | Z02 | DEV | Edit photo > Cutout: tap or swipe over a leftover hanger with Remove → gone; Brush restores a small missing piece; Undo restores; Light tab brightness works; Done saves and colours update; developer diagnostics show tap median ≤ 400 ms | PASS/FAIL |
| V-Z2-19 | Z02 | DEV | Browse default: 2 columns of photos only, all tiles the same size, no text; heart and cloud badge still visible | PASS/FAIL |
| V-Z2-20 | Z02 | DEV | Finish a tag review and a colour review: short completion screen with sparks and "updated on N garments", disappears alone after ~1.6 s or on tap | PASS/FAIL |
| V-Z2-21 | Z02 | DEV | Batch of 10 photos (pack OFF, and ON if installed): garments become Ready in screen order; no crash; total time from the `RobiaBatch` log vs today's APK | PASS/FAIL |
| V-Z2-22 | Z02 | DEV | Batch screen: one row per garment with category and occasions, no duplicated status text, one header with ETA, bottom bar only Save + short attention line | PASS/FAIL |
| G-SEG-1 | SG2a | DEV | Developer > Run cutout benchmark on Manu's phone (Poco F3), plugged in, cool, battery saver off: median no-hanger ≤ 4000 ms, hanger ≤ 9000 ms (includes the hanger-first stage), peak PSS ≤ 1300 MB | PASS/FAIL |
| G-SEG-2 | SG2b | DEV | ≥ 10 garments, pack ON vs OFF, OK/NOK each; pass rule SEG-DEC-006 | PASS/FAIL |
