# Robia investigation: R4, R7/R8, R15 (read-only)

Scope: source tree at the repository checkout at main fc45cec. Nothing was built or edited. Gradle cache and bash were not usable, so ML Kit internals are unverified. Items marked CERTAIN are read from code; HYPOTHESIS items need a device or log check.

## R4: photo cleanup fails randomly, retry sometimes works

**Where it lives**
- Engine: `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt:20-103`. `InputImage.fromFilePath` at line 28; `enableForegroundBitmap()` at line 37; `process().await()` at lines 42-45.
- Wrapper: `media/PhotoBackgroundRemover.kt:7-21`. Any thrown exception becomes `ORIGINAL_FALLBACK` with `SEGMENTATION_FAILED`.
- Result model: `media/BackgroundRemovalEngine.kt:17-43`. `usedFallback` at line 23.
- Manifest: `AndroidManifest.xml:18-20`, `com.google.mlkit.vision.DEPENDENCIES` = `subject_segment`.
- Dependency: `gradle/libs.versions.toml:12,38` (`play-services-mlkit-subject-segmentation` 16.0.0-beta1); `app/build.gradle.kts:145`.

**Root cause (CERTAIN from code)**
- The error is classified at `MlKitSubjectSegmentationBackgroundRemovalEngine.kt:77-83`. A `MlKitException` with `errorCode == UNAVAILABLE` maps to `MODEL_UNAVAILABLE`. The `causeClass` at line 89 is `this::class.java.name`, which explains `y7.a`: it is an obfuscated class inside the ML Kit AAR.
- The failure is returned, not thrown. It becomes `ORIGINAL_FALLBACK` at lines 42-45.
- `ModuleInstall` / `moduleinstall` appears nowhere in the tree. The app never requests the optional module, and `hasGooglePlayServices()` (lines 74-75) checks only `ConnectionResult.SUCCESS`, not module state.
- There is no wait, backoff, or automatic retry anywhere in the chain.

**Why it is random (HYPOTHESIS)**
- The module is installed by Play services in the background, so the same input fails until the download completes. The "Please wait" text is ML Kit's own.
- "Always fails" likely means the module never arrives on that device or install path (sideload, debug, Play services not current). This is consistent with the code but cannot be confirmed from source.

**Input resolution (CERTAIN)**
- Full resolution, no downscale. The capture file comes from `ClothingImageStore.createCaptureUri` (lines 27-34), or gallery bytes are copied verbatim by `copyContentUriToPrivateStorage` (lines 36-46). `InputImage.fromFilePath` (engine line 28) receives that file directly. Whether ML Kit downscales internally is unknown.

**Retry and notice paths (CERTAIN)**
- Single item, `ui/AddEditClothingScreen.kt`:
  - `processSelectedPhoto` (lines 319-492) calls the remover at line 367. Fallback sets `captureStatus = BackgroundFallback` and `photoRetrySource` (lines 467-483).
  - Retry button at lines 1608-1617, shown only when `captureStatus == BackgroundFallback`. It calls `queuePhotoForProcessing` (line 800-801), which re-runs the same engine on the same URI.
  - User-visible text: `background_removal_fallback_status` (`values/strings.xml:253`). The reason string appears only in `developerDiagnostics` (line 381).
- Batch, `ui/BatchAddClothingScreen.kt`:
  - `processBatchDraft` (483-566) calls the remover at line 504. A fallback sets `NeedsReview` (lines 524-527) with `batch_original_fallback_message` (`strings.xml:347`).
  - Retry is offered only for `Failed` or `Interrupted` (lines 571-572, UI at 378-382). A `NeedsReview` item has no retry button in the batch list.
- **PREMISE PARTLY DOES NOT MATCH:** the user said "sin aviso". A notice does exist (the fallback status text and the NeedsReview message). It just never names the cause, and in batch it offers no retry.

**Hot-file regions, `ui/RobiaApp.kt`**
- `:934` `val batchBackgroundRemover = remember { PhotoBackgroundRemover() }`
- `:1195-1216` `processQueuedBatchDrafts()`
- `:1218-1228` `startBatchAdd()`
- `:1230-1234` `retryBatchDraft()` (calls `retryForBatch()`, then re-queues)

**Coupling**
- The fallback contract is shared by both screens and by `PhotoBackgroundRemover`. Changing where retry lives affects both.
- `proguard-rules.pro` has no ML Kit keep rule (lines 1-6). `docs/specs/robia-embeddings-sync-manage/requirements.md:38` requires one. Release minify with that rule missing is a plausible additional risk, though not verified.

**Minimal change (suggestion)**
1. In `PhotoBackgroundRemover.removeBackground` (lines 10-20), add a bounded retry with delay only for `MODEL_UNAVAILABLE`, 2-3 attempts.
2. Before `process`, check module availability with `ModuleInstall` and call `installModules` for the subject-segmentation API if missing (HYPOTHESIS: exact API names are from memory, not verified). Surface a distinct "downloading model" status instead of the generic fallback.
3. Show the failure reason in the single-item and batch messages, not only in developer diagnostics.

## R7/R8: jagged edges and output quality

**Pipeline, in order**
1. **Segmenter output.** `foreground = result.foregroundBitmap` (engine line 47) is used as-is. CERTAIN: the app applies no alpha threshold, no feather, and no mask resampling. HYPOTHESIS: the mask is low-resolution inside ML Kit and upscaled there, which is the most likely source of stair-stepping.
2. **Write.** `ClothingImageStore.writeProcessedBitmap` (lines 238-252) saves `PNG`, quality 100, full size, prefix `subject`. Lossless, so no artifacts from this step.
3. **Crop.** `cropTransparentPixels` (lines 221-236) crops to alpha bounds, padding 4% (`CROP_PADDING_RATIO`, line 526). The threshold is alpha > 24 (line 521). Pixel-exact, but faint halo pixels remain inside the crop. Bounds are found with a per-pixel `getPixel` scan of the full-resolution image (lines 480-490), which is a performance cost.
4. **Colour analysis.** `extractPaletteColorDiagnostics` (ClothingImageStore 139-158) decodes at full size with `decodeStream`, with a grid step of `max/96` (line 382-385). `GarmentColorAnalyzer.kt:60-64` and 80-84 drop alpha < 192, weight 192-239 as 0.35, and weight ≥ 240 as 1.0. Colour detection therefore depends directly on the alpha ramp.
5. **Embedding input.** `suggestions/engine/ImageInputEncoder.kt:27-56`: power-of-two decode subsampling (`sampleSizeFor`, lines 59-63), bilinear scale to `IMAGE_MAX_EDGE_PX = 1024` (`EmbeddingRecipe.kt:16`), then composite onto **opaque white** (lines 45-49), then PNG. Partial alpha becomes a light halo, not transparency. The caller is `SuggestionService.suggestForPhoto` (lines 68-73, embed at line 86), wired through `RobiaApp.kt:429` and `RobiaApplication.kt:108`. `AddEditClothingScreen.kt:658` and `RobiaApp.kt:4311` also call the encoder.
6. **Grid display.** `ImagePipeline.requestFor` (lines 111-133) sizes the request to bounds and uses `ContentScale.Fit` (lines 371, 426). `ClothingImageStore.getOrCreateBoundedThumbnail` (lines 254-314) uses power-of-two decode (lines 341-348), then a single bilinear `createScaledBitmap` (line 287), then PNG if alpha (290-295). HYPOTHESIS: subsample aliasing followed by bilinear is the likely "jagged at card size" cause.
7. **Drive upload.** `encodeDriveWebp` (lines 62-97) bilinear-scales to 2048 px (`DRIVE_WEBP_MAX_EDGE_PX`, line 519), then `WEBP_LOSSY` at quality 90 (line 518, `compressDriveWebp` 375-380). The caller is `GoogleDriveWardrobeRepository.kt:146`. HYPOTHESIS: lossy alpha on fringes.
8. **Generated background.** `ui/GarmentSwatchBackground.kt:11-44` is a Compose vertical gradient from `surfaceContainerLow` to the swatch colour at alpha 0.26. It is drawn behind the photo at `RobiaApp.kt:4005-4009` (`GarmentPhotoPlaceholder`), `ColorReviewScreen.kt:427`, and `TagReviewScreen.kt:746`. It is display-only and never baked into any file, so a semi-transparent fringe shows a tinted swatch.

**Quick Edit (CERTAIN):** `QuickEditImageProcessor.kt:15-25, 69-85` redraws onto a new ARGB bitmap with a colour matrix (alpha untouched), then saves a new PNG. No resampling.

**Other risk (HYPOTHESIS):** EXIF orientation. `BitmapFactory` decode paths (ClothingImageStore 145, 198, 278; ImageInputEncoder 33) ignore EXIF, while `InputImage.fromFilePath` may not. Check that the ML Kit foreground is upright for rotated camera photos.

**Hot-file regions:** `RobiaApp.kt:3998-4014` (swatch brush, line 4009) and `:4296-4319` (embedding export).

**Minimal change (suggestion):** request the ML Kit confidence mask if the API offers it (HYPOTHESIS, not verified) and build alpha from it with a smoothstep, instead of using `foregroundBitmap` alpha. This changes colour analysis and embeddings. Already-saved cutouts are not reprocessed.

## R15: no socks or underwear, socks classified as trousers

**Default categories and occasions (CERTAIN)**
- Category tags, `core/model/DefaultTags.kt:12-30`: Shorts, Jackets, Jumpsuits, Blouses, Dresses, Skirts, Blazers, Cardigans, Bags, Tops, Knitwear, Trousers, Sweaters, Shoes, Shirts, Vests, Jewelry (`suggestOccasions = false`), Accessories (`suggestOccasions = false`), Coats.
- Seasons `:31-34`: Spring, Summer, Fall, Winter. Occasions `:35-40`: Active, Statement, Dressed-up, Formal, Everyday, Business. Location `:41`: Main closet.
- Scoring descriptions `suggestions/EmbeddingRecipe.kt:31-57`: one text per category and occasion. The same strings are in `tools/embedding-precision/labels.py:6-33`. The recipe comment says they are copied from there, and I compared the strings by reading them.
- **There is no socks or underwear entry anywhere in the source** (grep for `socks|underwear` is empty).

**How prediction works (CERTAIN)**
- Label text is `CLASSIFICATION_PREFIX + description` (`EmbeddingRecipe.kt:18, 64-71`). It falls back to `tag.name` if the tag has no description, or if the name was changed by the user (`TagDisplayPolicy.kt:8-11`).
- Label vectors come from `embedder.embedText`, cached in memory only (`SuggestionService.kt:64, 190-195`).
- Image vector: `embedder.embedImage` (line 86).
- `scoreLabels` (`suggestions/CategoryScorer.kt:29-48`): TEXT mode is cosine only. COMBINED mode adds a z-scored kNN term (mean of top 3 cosines to saved garments, `KNN_TOP = 3`) and is used only when at least two labels have saved examples.
- `selectCategory` (lines 54-64): the top label is marked only if its lead over the second is ≥ `CATEGORY_MARGIN_TEXT = 0.010` (TEXT) or `CATEGORY_MARGIN_COMBINED = 0.50` (COMBINED). Otherwise nothing is marked.
- Occasions are scored only when the chosen category has `suggestOccasions` (`SuggestionService.kt:119-129`).
- HYPOTHESIS for socks: no socks label exists, so the cosine ranks the nearest garment label first. The Trousers prompt ("each leg down to the ankle") is the most plausible match, and no margin check stops it.

**Seeding on existing installs (CERTAIN)**
- `seedDefaultsIfNeeded` (`data/LocalRepositories.kt:230-238`) runs every launch from `ui/RobiaApp.kt:441-445`.
- `seedCategories` and `seedTags` use `OnConflictStrategy.IGNORE` (`data/local/Dao.kt:477-481`), so existing rows are untouched and new ids are inserted.
- Tombstoned defaults are skipped (`DefaultTagSeeds.withoutDeleted`, `core/model/TagDedupePlanner.kt:62`). `restoreDefaultTags` (lines 209-224) clears tombstones.
- Seed rows are **not** `queuedForSync()`, unlike `restoreDefaultTags` (line 220). HYPOTHESIS: a new default reaches Drive only through another snapshot.
- Room version is 13 (`data/local/RobiaDatabase.kt:21`). A data-only addition needs no migration.
- Existing garments are not re-tagged. Only new adds use the new label.

**Renamed or deleted defaults (CERTAIN)**
- Renamed: `TagDisplayPolicy.shouldUseStoredName` (lines 8-11) returns true, so the stored name is used for both display and the scoring label (`EmbeddingRecipe.kt:66-70`).
- Deleted: a tombstone is written and the default is not reseeded.
- Sync: `WardrobeSyncOutboxProcessor.kt:1733-1735` uses `isCustomOrModifiedDefault` (`DefaultTags.kt:65-88`) to decide what to sync.

**Localisation (CERTAIN)**
- UI names: `ui/TagLocalization.kt:14-17` maps canonical ids to `R.string.tag_*` (`defaultTagLabelRes`, lines 20-52). Fallback is the raw name. Example: `tag_shorts` is "Shorts" in EN and DE, "Pantalones cortos" in ES (`values*/strings.xml:165`).
- **Scoring labels are always English**, regardless of app language (`EmbeddingRecipe.kt:31-57`). This is by design, but it is a fact the planner should know.

**Adding a socks category needs (suggestion)**
1. `DefaultTags.kt`: a `category-socks` entry in `tags` with a new `sortOrder`. Decide `suggestOccasions` (probably false, like Jewelry).
2. `EmbeddingRecipe.kt:31-57` and `labels.py`: one description, kept in sync.
3. `TagLocalization.kt:20-52`: a `tag_socks` mapping.
4. `values`, `values-es`, `values-de` `strings.xml`: `tag_socks`.
5. Re-check the margins. A new competing label can lower the margin for existing predictions, so more items may end up with no prediction (HYPOTHESIS; the mechanism is clear from `selectCategory`).

**Coupling across items**
- R4 and R15 both change the fallback and `processSelectedPhoto` / `processBatchDraft` behaviour. Keep them in one change set.
- R7 changes the cutout alpha, which changes colour analysis (`GarmentColorAnalyzer`) and the embedding input. Both are affected by any segmentation tuning.
- R15 changes `labels.py` and `EmbeddingRecipe.kt` together, or they drift.
