# Verification matrix — robia-ux-rework

Levels: **CI** = unit/JVM test or build check run by GitHub Actions on the PR; **REC** = evidence pasted in the receipt (command output); **REV** = independent reviewer checks; **DEV** = device row for Manu (Z02 checklist, staging APK from main).

| ID | Task | Level | Check | Pass |
|---|---|---|---|---|
| V-U00-1 | U00 | REC | `python3 tools/split-check/declarations.py <base-ref> HEAD` | "EQUAL" (same multiset of top-level declarations under `ui/`, visibility ignored) |
| V-U00-2 | U00 | CI | `testDebugUnitTest assembleDebug assembleStaging` | green; contract tests read `uiSource()` where symbols moved; no assertion removed (`git diff` of tests shows only path/helper changes) |
| V-U01-1 | U01 | CI | `HapticsTest`: `hapticConstantFor(Confirm, 30) == CONFIRM`, `(Confirm, 29) == CONTEXT_CLICK`, `(Reject, 30) == REJECT`, `(Reject, 26) == LONG_PRESS`, `(Tick, any) == CLOCK_TICK` | pass |
| V-U01-2 | U01 | CI | the `preferences → RobiaSettings` mapping is extracted to `internal fun Preferences.toRobiaSettings()` (move-only from the `settings` flow) and tested with `mutablePreferencesOf()`: absent key → `hapticsEnabled == true`; `false` stored → false | pass |
| V-U02-1 | U02 | CI | `BrowseCardTextTest`: `cardTitle("", [Dresses], u) == "Dresses"`; `cardTitle("Mine", …) == "Mine"`; no category → untitled; `cardMeta` orders occasions alphabetically then seasons, joined " · " | pass |
| V-U02-2 | U02 | CI | `badgeFor` test over every `GarmentSyncStatus` value matches the brief table (Synced, LocalOnly → null) | pass |
| V-U02-3 | U02 | REV/REC | grep: no `garment_sync_synced` used as card subtitle; `Card(` in `GarmentGridCard` has `shape = MaterialTheme.shapes.large`; footer height fixed | matches |
| V-U02-4 | U02 | CI | build green | — |
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
| V-U08-1 | U08 | REV/REC | review commit/discard logic diff empty (only animation/haptics/modifier lines changed) | confirmed |
| V-U09-1 | U09 | CI | `PdfLayoutTest` (fake measurer): typical garment (title 40, notes 250, 3 occasions) → 1 page, every box inside 44 pt margins and footer; empty notes → swatches start ≤ 24 pt below the title; 200-char title + 2000-char notes → overflow continues on page 2, nothing outside margins | pass |
| V-U09-2 | U09 | REC/CI | Inter release URL + sha256 for each TTF; OFL file present; rewritten PDF design tests + unchanged `garmentPdfExport_keepsRequiredMetadataGridOrder` green | listed |
| V-T01-1 | T01 | CI | `EmbeddingRecipeTest` 27 descriptions; ids `category-socks`, `category-underwear` present with REQ-TX-01 text | pass |
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
| V-SG2-6 | SG2b | CI | `Preferences.toRobiaSettings()` with `mutablePreferencesOf()`: absent key → `preciseCutoutEnabled == true`; `useEnginePrecise(packReady, enabled)` truth table | pass |
| V-SG3-1 | SG3 | CI | `RefineSessionTest`: keep/remove union/subtract, undo ≥ 10, reset | pass |
| V-SG3-2 | SG3 | REV | Done writes a new PNG and updates editor state only; Cancel writes nothing | confirmed |
| V-SG3-3 | SG3 | CI | build green | — |
| V-Z2-01 | Z02 | DEV | Browse: all cards in a row equal height; no gap at photo corners; no "Synced" text; long tags end with "…" | PASS/FAIL |
| V-Z2-02 | Z02 | DEV | Browse: a garment uploading/failing shows its badge; synced shows none | PASS/FAIL |
| V-Z2-03 | Z02 | DEV | Filters active → Reset next to Filters clears them; chip ✕ removes one | PASS/FAIL |
| V-Z2-04 | Z02 | DEV | Filters screen: grouped cards, "+N more", colour grid, "Show N garments" count right; Summer+Winter returns garments with either | PASS/FAIL |
| V-Z2-05 | Z02 | DEV | Manage: swipe left/right changes tab; vertical scroll still smooth | PASS/FAIL |
| V-Z2-06 | Z02 | DEV | Settings grouped rows; Language sub-screen changes language | PASS/FAIL |
| V-Z2-07 | Z02 | DEV | Google Drive sub-screen: every action you used before is there | PASS/FAIL |
| V-Z2-08 | Z02 | DEV | Add garment: rows + bottom sheets; ✦ on suggested values; save keeps everything | PASS/FAIL |
| V-Z2-09 | Z02 | DEV | Garment detail: rows instead of boxes | PASS/FAIL |
| V-Z2-10 | Z02 | DEV | Processing: sweep → masked shimmer → pop; feels smooth, not excessive | PASS/FAIL |
| V-Z2-11 | Z02 | DEV | Review Accept/Discard: vibration + slide; AI buttons shimmer subtly; Settings > Haptic feedback off stops vibration | PASS/FAIL |
| V-Z2-12 | Z02 | DEV | Share PDF: one A4 page for a normal garment, matches mockup (no Location row, as before) | PASS/FAIL |
| V-Z2-13 | Z02 | DEV | Manage > Category lists Socks and Underwear (ES Calcetines, Ropa interior) | PASS/FAIL |
| V-Z2-14 | Z02 | DEV | Photo of socks → suggested category Socks (or none), not Trousers | PASS/FAIL |
| V-Z2-15 | Z02 | DEV | 10 photos in a row: no "waiting for module" failure; if it appears, it recovers alone or Retry works | PASS/FAIL |
| V-Z2-16 | Z02 | DEV | Cutout edges look smooth at card size and in detail (no stair steps) | PASS/FAIL |
| V-Z2-17 | Z02 | DEV | Settings > Precise cutout: download, switch, delete work; G-SEG-2 table filled | PASS/FAIL |
| V-Z2-18 | Z02 | DEV | Refine: tap a leftover hanger with Remove → gone; Undo restores; Done saves; developer diagnostics show tap median ≤ 400 ms | PASS/FAIL |
| G-SEG-1 | SG2a | DEV | Developer > Run cutout benchmark on Manu's phone (Poco F3), plugged in, cool, battery saver off: median no-hanger ≤ 4000 ms, hanger ≤ 8000 ms, peak PSS ≤ 1300 MB | PASS/FAIL |
| G-SEG-2 | SG2b | DEV | ≥ 10 garments, pack ON vs OFF, OK/NOK each; pass rule SEG-DEC-006 | PASS/FAIL |
