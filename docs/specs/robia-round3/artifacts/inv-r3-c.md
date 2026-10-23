# Robia investigation: C1–C5 and DB

Read-only; no files edited, no gradle, no network. Line numbers are relative to `app/src/main/java/com/gusanitolabs/robia/` unless a path is shown. CERTAIN means seen in code or docs. HYPOTHESIS means inferred and not verified.

## C1 Duplicate detector

**1. Where today.** No duplicate concept exists. `grep duplicat` hits only tag and colour name checks (`ui/ManageTagsScreen.kt:611`, `core/model/TagDedupePlanner.kt`). The nearest thing is "See similar", which ranks by IMAGE cosine (`suggestions/SearchRanker.kt:35-41`) and is wired at `ui/RobiaApp.kt:988-996` and `ui/BrowseFilterBar.kt:321-322`.

**2. What is embedded (CERTAIN).**
- IMAGE: the saved `photoUri`, composited on white, PNG, longest edge ≤1024 px, 70 vision tokens (`suggestions/engine/ImageInputEncoder.kt:19-56`, `suggestions/EmbeddingRecipe.kt:15-16`).
- Photos are written as processed PNGs by the cutout and editor paths (`media/cutout/PreciseCutoutEngine.kt:83`, `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt:77`, `ui/PhotoEditorScreen.kt:923`). Whether most saved garments are cutouts is a HYPOTHESIS.
- TEXT: `"title: none | text: " + name + ". " + notes` (`EmbeddingRecipe.kt:20, 76-80`).
- Not embedded: the category or occasion labels, which are computed separately.

**3. Dims, normalisation, storage (CERTAIN).**
- 768-dim float32, little-endian BLOB (`suggestions/VectorMath.kt:9-22`, `EmbeddingRecipe.kt:10`).
- Engine option `normalize = true` (`suggestions/engine/LiteRtNativeEmbedder.kt:28-31`), so stored vectors should be unit length. The code still computes full cosine.
- Table `garment_embeddings`: PK `(garment_id, kind)`, plus `model_id`, `recipe_id`, `dim`, `vector`, `input_hash`, `computed_at_epoch_millis`, `duration_millis`, `pending_upload` (`data/local/EmbeddingEntities.kt:12-28`).
- Keys: `MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"`, `RECIPE_ID = "eg2-r1-vt70"` (`EmbeddingRecipe.kt:8-9`). Reads filter on both (`data/EmbeddingRepository.kt:33-36`).
- `input_hash`: 16-hex SHA-256 of the PNG bytes for IMAGE (`suggestions/SuggestionService.kt:92, 199-200`) and of the text for TEXT (`suggestions/EmbeddingBackfill.kt:158`).

**4. How "Similar" works today (CERTAIN).** `rankSimilar` takes IMAGE cosine to the chosen garment, excludes it, sorts all candidates descending, and appends garments without IMAGE. There is no threshold and no top-k cap (`SearchRanker.kt:35-41`).

**5. Scale and all-pairs cost.**
- Manu's own wardrobe is 35 garments (`docs/specs/robia-embeddings-sync-manage/feedback/2026-10-15-manu-feedback-2.md:6`). A device layout test used 150 (`docs/specs/robia-ux-rework/artifacts/inv-r3-zoom-colors-storage.md:22`).
- Pairs: 595 at N=35, 11,175 at N=150, 124,750 at N=500.
- Each cosine is three 768-element double dot products (`suggestions/CategoryScorer.kt:20-23`, `VectorMath.kt:24-29`). At N=150 that is about 26M multiply-adds.
- HYPOTHESIS: N=500 runs in well under a second on the Poco F3 with one dot product per pair, but this is not measured.
- `EmbeddingDao.observeAll()` (`data/local/EmbeddingDao.kt:10-11`) re-emits on every vector write, so a scan should read a one-shot snapshot, not the observed flow.

**6. Dismissed pairs or tombstones.** None. `grep dismiss` in data, core, suggestions, and sync returns nothing. Tombstones exist only per entity: `garment`, `garment_tag`, `main_color` (`data/LocalRepositories.kt:79-95, 196-216`).

**7. Sync (CERTAIN).** Embeddings travel inside the Drive snapshot `wardrobe_snapshot.json` (`sync/GoogleDriveWardrobeRepository.kt:1127`). Each garment record carries `embeddings: List<EmbeddingSyncRecord>` with base64 `b64` (`core/model/SyncModels.kt:139-161`; `sync/LocalWardrobeSyncSnapshotRepository.kt:369-377`). `pendingUpload` drives the sync request (`sync/WardrobeSyncOutboxProcessor.kt:134, 826-827`). Writing a vector does not touch the garment revision (`data/EmbeddingRepository.kt:19-27`).

**8. Review-screen pattern to reuse.**
- `ui/ReviewScaffold.kt`: `ExitDirection` (77), `ReviewUndoPlan` (86), `ReviewKind` (99), `hasReviewableGarments` (105), `ReviewAcceptedCard` (118), `ReviewPickerList` (151), `ReviewProgressHeader` (193), `ReviewHelperRow` (228), `ReviewEmptyCard` (248), `ReviewBottomBar` (280), `ReviewDiscardDialog` (315).
- Consumers: `TagReviewScreen` (`ui/TagReviewScreen.kt:346`) and `ColorReviewScreen` (`ui/ColorReviewScreen.kt:123`).
- Planner: `suggestions/ReviewPlanner.kt:38` (pure, with progress via `scan`).
- Undo-able writes: `ReviewWrites` (`ui/RobiaApp.kt:3323-3353`).
- Routes: `private sealed interface RobiaRoute` (`RobiaApp.kt:249-275`). Nav host `RobiaNavHost` (`RobiaApp.kt:2629`), with the route `when` starting at `:2724`.

**9. Delete and archive API (CERTAIN).** There is no hard delete for garments. Deletion is archive: `WardrobeRepository.archiveItem(s)` (`data/WardrobeRepository.kt:28-29`), implemented with a `garment` tombstone (`data/LocalRepositories.kt:79-95`) and `archiveItem(s)WithTombstone(s)` (`data/local/Dao.kt:348-366`). The UI call is `RobiaApp.kt:484` (`onDeleteItems`).
- Archive keeps the row and its vectors. `observeVectors` does not filter archived garments (`EmbeddingRepository.kt:33-36`).

**10. Browse warning slot.** `BrowseGrid.kt:321-325` renders `SuggestionsProgressBanner` as a full-span item. A duplicates banner fits the same slot. `BrowseWardrobeScreen` starts at `BrowseGrid.kt:181`.

**11. Threshold.**
- The repo has no measured same-garment cosine distribution. The only thresholds are parity gates of ≥0.98 (`docs/specs/robia-embeddings-sync-manage/requirements.md:112`).
- HYPOTHESIS: different photos of the same garment fall well below 0.99, so a "very high" threshold will miss real duplicates. This needs calibration on Manu's 35 garments with `tools/embedding-precision`.
- CERTAIN: IMAGE `input_hash` equality means byte-identical input, which is an exact-duplicate tier that needs no threshold.

**12. Stale IMAGE vectors (CERTAIN facts, HYPOTHESIS on impact).**
- Backfill only fills missing IMAGE rows (`EmbeddingBackfill.kt:110, 115`).
- Save upserts IMAGE only when `capture.photoUri == item.photoUri` (`RobiaApp.kt:3365-3367`).
- The only other refresh is the developer "Recompute" button (`ui/ItemDetailScreen.kt:382-390`).
- HYPOTHESIS: a garment whose photo changes through a path with no capture keeps an old IMAGE vector, which would corrupt duplicate results.

**Minimal change:** a pure `DuplicateScan` (new file, one-shot snapshot, pairwise over active garments with IMAGE, exact-hash tier first) plus a banner in `BrowseGrid.kt:321-325`, a route in `RobiaApp.kt:249-275`, and archive via `onDeleteItems`. Dismissed pairs need a new table; see DB below.

## C2 Outfit generator input

**1. Model and engine (CERTAIN).**
- EmbeddingGemma 2 text-vision 440M, LiteRT-LM file, ~388 MB, SHA-256 pinned (`EmbeddingRecipe.kt:11-14`).
- `LiteRtNativeEmbedder.kt:17-26` uses `Backend.CPU()` for both text and vision. There is no GPU or NPU delegate.
- The engine is released after 60 s idle (`EmbeddingRecipe.kt:17`, `engine/EmbeddingEngineHolder.kt:117-122`).

**2. Shared space (CERTAIN).** Yes.
- Label scoring is `cos(image, label-text)` (`SuggestionService.kt:115-116, 190-195`; `CategoryScorer.kt:34`).
- Query search is `cos(query, IMAGE)` and `cos(query, TEXT)` (`SearchRanker.kt:55-59`).
- Cross-modal search exists in Browse: `RobiaApp.kt:946-1000`. The query prefix is `EmbeddingRecipe.kt:19`, the debounce 300 ms (`SearchController.kt:12`), and the embed timeout 5 s (`SearchController.kt:15`).

**3. Recorded latencies (CERTAIN).** `docs/specs/robia-embeddings-sync-manage/decision-log.md:128` (G-DEVICE-1, Poco F3):
- 70 vision tokens: `suggestion_ms` median 1613 / 1675 / 1912 ms over 3 runs; p90 2414 / 1937 / 2447; peak PSS 783 / 657 / 648 MB. Recipe kept.
- 140 tokens: median 4350 / 4236 ms, failed the gate.
- `docs/specs/robia-ux-rework/technical-specification.md:165` quotes ~1.6 s median for an image embedding.
- Text-only latency is not in the docs. It is stored per row in `duration_millis` (`EmbeddingEntities.kt:26`).

**4. Categories and tags (CERTAIN).** `core/model/DefaultTags.kt`:
- 21 category tags, `category-*` (lines 12-32).
- 4 seasons, `season-*` (33-36).
- 6 occasions, `occasion-*` (37-42).
- 1 location, `location-main-closet` (43).
- `suggestOccasions = false` for jewelry, accessories, socks, underwear (lines 28-32).
- Label descriptions for 27 defaults (`EmbeddingRecipe.kt:31-59`).
- Margins: `CATEGORY_MARGIN_TEXT 0.010`, `CATEGORY_MARGIN_COMBINED 0.50` (`EmbeddingRecipe.kt:21-24`).
- The garment's category is taken as `firstOrNull` (`SuggestionService.kt:146`), so one category is assumed.

**5. Consequence.** Any change to `MODEL_ID` or `RECIPE_ID` invalidates every vector, because reads filter on both (`EmbeddingRepository.kt:33-36`, `EmbeddingBackfill.kt:107-111`). The tree has no outfit or combination code.

## C3 Analytics histograms

**1. Taxonomy (CERTAIN).**
- `tag_categories` → `garment_tags` (with `category_id`, `suggest_occasions`, `description`) → `clothing_item_tags` join with composite PK and FKs (`data/local/Entities.kt:51-145`).
- Colours are not a tag category. They are `main_colors` (`Entities.kt:97-112`) plus two single-valued slots per garment, `color_primary_*` and `color_secondary_*` (`Entities.kt:38-49`). The "colors" histogram premise does not match the model, so **PREMISE DOES NOT MATCH** for colours as tags.
- Fit is a single nullable `fit_value INTEGER` column (`Entities.kt:20`), not a tag.
- Locations: one default tag.

**2. Existing counts (CERTAIN).** There is no garment-per-tag count query. `ManageTagsScreen` counts tag definitions per category (`ui/ManageTagsScreen.kt:177-192`), and `AdvancedFiltersScreen` counts filtered results (`AdvancedFiltersScreen.kt:121-122`). `WardrobeDao` has no `GROUP BY` (`data/local/Dao.kt`). Counting therefore happens in memory from `observeActiveItems` (`Dao.kt:18-19`) or `ClothingItem.tags`.

**3. Archived.** `observeActiveItems` filters `is_archived = 0` (`Dao.kt:18`). Colour review eligibility excludes archived garments (`ColorReviewScreen.kt:839`). Histograms should follow the same rule.

**4. No analytics surface (CERTAIN).** No route in `RobiaRoute` (`RobiaApp.kt:249-275`). Bottom bar is Browse, Add, Manage (`RobiaApp.kt:320-324`). No chart library in `gradle/libs.versions.toml`. `grep histogram|analytic|statistic` finds only a Python tool and an architecture HTML.

**Minimal change:** add a pure function `tagHistogram(items, kind)` that returns counts per tag, plus colour and fit histograms from the columns. Place the screen in Settings or a new route. Note that `RobiaApp.kt` is the hot file.

## C4 Colour scheme

**1. Tokens (CERTAIN).** `core/designsystem/Color.kt:5-40`, all 36 mapped in `Theme.kt:14-51`. Light only (`Theme.kt:70-71`).

| Token | Hex | Token | Hex |
|---|---|---|---|
| background | F9F9F9 | onTertiaryContainer | C37F50 |
| error | BA1A1A | outline | 817472 |
| errorContainer | FFDAD6 | outlineVariant | D2C3C0 |
| inverseOnSurface | F0F1F1 | primary | 1D100D |
| inversePrimary | DAC1BC | primaryContainer | 332421 |
| inverseSurface | 2F3131 | scrim | 000000 |
| onBackground | 1A1C1C | secondary | 6B5C4D |
| onError | FFFFFF | secondaryContainer | F1DCC9 |
| onErrorContainer | 93000A | surface | F9F9F9 |
| onPrimary | FFFFFF | surfaceBright | F9F9F9 |
| onPrimaryContainer | A08A85 | surfaceContainer | EFE5D9 |
| onSecondary | FFFFFF | surfaceContainerHigh | E8E8E8 |
| onSecondaryContainer | 6F6051 | surfaceContainerHighest | E2E2E2 |
| onSurface | 1A1C1C | surfaceContainerLow | F3F3F4 |
| onSurfaceVariant | 4F4442 | surfaceContainerLowest | FFFFFF |
| onTertiary | FFFFFF | surfaceDim | DADADA |
| | | surfaceTint | 6D5A56 |
| | | surfaceVariant | E2E2E2 |
| | | tertiary | 240D00 |
| | | tertiaryContainer | 431D00 |

- **Dark theme:** none, and dark is out of scope (`docs/specs/robia-embeddings-sync-manage/decision-log.md:80`, `requirements.md:20`). There is no `isSystemInDarkTheme` or dynamic colour in the tree.
- **Typography:** `Type.kt:8` uses `FontFamily.SansSerif`, not Inter as `DESIGN.md` specifies.

**2. Browse, cards, chips, buttons.**
- Cards: `surfaceContainerLowest` (`ui/BrowseGrid.kt:455, 496, 843`). Selected border: `primary` (`BrowseGrid.kt:501-503`).
- Photo background: a vertical gradient from `surfaceContainerLow` to the swatch at fixed alpha (`ui/GarmentSwatchBackground.kt:28-45`).
- Badges: `error` or `tertiary` (`BrowseGrid.kt:714`).
- Buttons: Material3 default container is `primary` (1D100D), content `onPrimary` (`BrowseGrid.kt:566`).
- Filter chips: `FilterChip` and `InputChip` with no explicit colours (`ui/BrowseFilterBar.kt:342, 461, 525`), so they take the scheme defaults.

**3. Hardcoded colours outside the token file.**
- `ui/GarmentSwatchBackground.kt:13-26`: swatch table. `Brown` #8B6848 (line 16), `Multicolor` #A56639 (line 25), `Gray` #8E8E8E (line 17). `DefaultTags.kt:50` gray is #8A8A8A, a mismatch.
- `ui/PhotoEditorScreen.kt`: 449 gradient E9E4DC→CFC3B4; 827 checker EEEEEE; 1075-1079 light-slider blue and orange (functional).
- `ui/PhotoProcessingVisuals.kt:87-88`: peach GlowColor FFD2AE, ShimmerPeach FFE2C4.
- `core/designsystem/Motion.kt:64-66`: ShimmerBase 332421 (= primaryContainer), ShimmerMid 5A3A2C, ShimmerBand 59FFDCBE.
- `ui/RobiaApp.kt:3067`: fallback DADADA.
- `ui/ManageTagsScreen.kt:87`: preset string "#EFE5D9" (= surfaceContainer).

**4. Why it does not fit (HYPOTHESIS, grounded in the docs).** The DESIGN.md prose and the code disagree (CERTAIN):
- Prose says primary is #332421 (espresso). YAML and code use #1D100D, with #332421 as primaryContainer.
- Prose says secondary is champagne #E2CEBB. It is absent from YAML and code. Code secondary is grey-brown 6B5C4D.
- Prose says terracotta #A56639 is tertiary. Code tertiary is 240D00. #A56639 appears only in the Multicolor swatch and in `browse.html --terra`.
- "Steel" has no token; it is neutrals only.
- Swatch colours come from a fixed separate table, not the theme, so chips and dots do not share a palette with the UI.

**5. "Marrón": PREMISE NOT VERIFIABLE.** Candidates: 8B6848 (Brown swatch and default, `DefaultTags.kt:51`), A56639 (terracotta, prose only), C37F50 (`onTertiaryContainer`), 332421 (espresso). The user should say which.

## C5 Earlier experiments and feedback

- `docs/ux/earth_steel/DESIGN.md`: the Earth & Steel definition (YAML tokens plus prose, light only).
- `docs/ux/*/code.html`: six Stitch exports titled "Closet Harmony", the placeholder name (`docs/product/brief.md:15`). Five use Earth tokens. `docs/ux/quick_edit_closet_harmony/code.html` uses neutral greys (#e0e0e0, #4d4d4d, #1a1a1a, #faf9f7, #2d2e32). No decision records its rejection; the Quick Edit flow was replaced by the Photo editor (`docs/specs/robia-ux-rework/requirements.md:47`).
- `docs/specs/robia-ux-rework/mockups/*.html`: CSS variables match the Earth tokens plus `--terra #a56639` and `--terral #c37f50` (browse.html). The burgundy #7a2e2e is sample garment art, not theme.
- `docs/product/brief.md:22`: "Keep the Earth & Steel visual system" is the baseline.
- No palette complaint appears in the feedback files. Manu's only colour request is that review cards use the same swatch-gradient background as Browse (`docs/specs/robia-embeddings-sync-manage/feedback/2026-10-16-manu-feedback-5.md:10`).
- A Gray swatch was added (`docs/specs/robia-ux-rework/decision-log.md:106`). `gray-charcoal` keeps its legacy id with the name "Beige / Cream" (`DefaultTags.kt:49`).

## DB: Room version and adding a table

- Version 13 (`data/local/RobiaDatabase.kt:21`), `exportSchema = true`. Schemas on disk: 12.json and 13.json (`app/schemas/`). KSP writes to `schemas` (`app/build.gradle.kts:142-144`).
- Migrations are objects in the companion, listed in `addMigrations` (`RobiaDatabase.kt:41-54`). There is no destructive fallback, so a missing migration crashes on open.
- Latest migration (12→13) stores its SQL in a top-level `internal val MIGRATION_12_13_SQL` (`RobiaDatabase.kt:256`) so `Migration12To13Test` runs the exact statements against in-memory sqlite-jdbc.
- To add a table (e.g. `dismissed_duplicate_pairs`):
  1. Add the entity and DAO, add the entity to `RobiaDatabase.kt:12-20`, add the DAO accessor (`:26-29`).
  2. Set `version = 14` (`:21`).
  3. Add `MIGRATION_13_14`, with SQL matching the entity exactly.
  4. Run a build so KSP writes `14.json`, then commit it. I could not run gradle here.
- Sync options:
  - **Local-only table:** no snapshot change. Dismissals are per device and are lost on reinstall.
  - **Synced:** add a field to the snapshot record (`SyncModels.kt`), bump `WARDROBE_SYNC_SCHEMA_VERSION` (currently 2, `SyncModels.kt:221`), and extend the import path (`LocalWardrobeSyncSnapshotRepository.kt:114-132`). Do not bump the garment revision, as vector writes don't.

## Hot file: `ui/RobiaApp.kt` (3,384 lines)

| Region | Lines | Relevance |
|---|---|---|
| `bottomDestinations` | 320–324 | Bottom nav (Browse, Add, Manage) |
| `RobiaApp()` | 328–573 | Root state, `vectors` flow, `onDeleteItems` at 481–486 |
| `RobiaRoute` sealed interface | 249–275+ | Add a route here |
| `RobiaShell()` | 815–2002 | Browse search 946–1000, similar 988–996, prompts 1376–1385, 1630–1650 |
| `RobiaBottomBar()` | 2609–2627 | Nav bar |
| `RobiaNavHost()` | 2629–2947 | Route `when` at 2724 (Browse), 2761 (Manage), 2776 (ColorReview), 2786 (TagReview), 2853 (ItemDetail), 2934 (Advanced filters) |
| `ReviewWrites` | 3323–3353 | Undo-able writes |
| `storeSuggestionVectors()` | 3360–3384 | IMAGE and TEXT vector writes on save |

Coupling: C1 and C3 both add routes here. Any new route or banner touches `RobiaShell`. C4 is confined to `Color.kt` and `Theme.kt`, plus `BrowseGrid.kt` and `GarmentSwatchBackground.kt` for hardcoded colours. Only `RobiaApp.kt:3067` is in the hot file. `MaterialTheme` is used 41 times in `RobiaApp.kt`, so token edits propagate safely.

## Risks and coupling

- Changing `RECIPE_ID` or `MODEL_ID` (for C2 or C1) re-embeds all garments.
- A duplicate scan over stale IMAGE vectors (see C1 item 12) will report wrong pairs.
- Dismissed pairs need a DB v14 migration, and their sync choice affects the snapshot schema.
- Duplicate threshold cannot be set from the repo. It needs measurement.
- Histograms on colours and fit need no DB change; tag histograms also need none.

## Suggested minimal changes

- **C1:** pure one-shot `DuplicateScan` in `suggestions/`; exact-hash tier, then a calibrated cosine tier; banner at `BrowseGrid.kt:321-325`; new route in `RobiaApp.kt`; archive via `wardrobeRepository.archiveItems`. Calibrate the threshold on Manu's 35 garments first.
- **C2:** no change to the model; a new surface would need the same recipe.
- **C3:** pure `tagHistogram` function over `observeActiveItems` (active only); new Settings entry or route.
- **C4:** replace the `Color.kt` tokens and `Theme.kt` mapping in one place; align `GarmentSwatchBackground.kt` with the same palette if the swatch colours should match the theme. Clarify which brown first.
- **DB:** migration 13→14 as described above, only if dismissals are persisted.
