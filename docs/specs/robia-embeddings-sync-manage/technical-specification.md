# Technical specification — robia-embeddings-sync-manage

Revision 1 · Base `origin/main` `791dacd`. Paths are relative to the repo root; Kotlin sources under `app/src/main/java/com/gusanitolabs/robia/` are abbreviated `…/`. Line numbers refer to the base commit and are hints only — always locate by symbol name.

Traceability: REQ/NFR in `requirements.md`, DEC in `decision-log.md`.

---

## 1. Current-state facts (verified in code)

| Area | Fact | Where |
|---|---|---|
| DB | Room v12, migrations 1→12 inline, schemas exported to `app/schemas` | `…/data/local/RobiaDatabase.kt` |
| Tags | `GarmentTag(id, categoryId, name, sortOrder, isSystem)`; entity mapping by position | `…/core/model/ClothingModels.kt`, `…/data/LocalRepositories.kt` (`toEntity`/`toDomain` at end of file) |
| Old classifier | `TfliteAdditionalInfoDetector` used in add/edit, batch, and protected tag ids in Manage | `…/ui/AddEditClothingScreen.kt` (`additionalInfoDetector`, `reconcilePredictedAdditionalInfoTags`, `MODEL_PREDICTED_CATEGORIES`), `…/ui/BatchAddClothingScreen.kt` (`processBatchDraft`, `mergePredictedTags`), `…/ui/RobiaApp.kt` (`batchAdditionalInfoDetector`), `…/ui/ManageTagsScreen.kt` (`AdditionalInfoModelOutputTags`, `AiDetectedTagWarningDialog`) |
| Lost photos | `ClothingImageStore.writeRestoredImageBlob` names files `drive-<previous name>-<uuid>.<ext>`; `DriveFolderNaming.photoBlobPath(id, localUri)` uses the local file name as Drive key → name grows ~43 chars per restore → write fails past 255 bytes | `…/media/ClothingImageStore.kt`, `…/sync/DriveWardrobeRepository.kt` |
| Full re-upload | `uploadPhotoBlobs` re-encodes (PNG/JPEG) and uploads every photo each sync | `…/sync/GoogleDriveWardrobeRepository.kt` |
| Per-file lookup | `fetchBlob`/`upsertBlob`/`deleteBlob` call `findFileByName` (1 extra request each) | same, `HttpDriveSnapshotApi` |
| Abort on one error | `hydratePhotoBlobs`: `DriveApiResult.Failure -> return DriveSyncResult.Failure` | same |
| 403 misclassified | `toDriveResult`/`toDriveByteResult`: 401 and 403 → `Unauthorized` | same |
| Global deadlines | `DRIVE_PHASE_TIMEOUT_MILLIS = 60_000` wraps the whole operation; `SYNC_OPERATION_TIMEOUT_MILLIS = 2 min` | same; `…/sync/WardrobeSyncOutboxProcessor.kt` |
| Retry cap | DAO marks `NeedsUserAction` after 3 failures (1/5/15 min) | `…/data/local/Dao.kt` `markGarmentSyncFailedRetryable` and metadata equivalents |
| Guard blocks all | `hasGuardedPhotoRestoreIssues` stops `scheduleWorkFor`, `processPendingGarments`, `syncUploadLocalSnapshot` | processor |
| App-open only | processor runs in `MainActivity.lifecycleScope` | `…/MainActivity.kt` |
| Log rewrite | `FileRestoreSyncLogRepository.append` reads+rewrites the whole file each event | `…/sync/RestoreSyncLogRepository.kt` |
| Sync label | `garment_sync_failed_retryable` = "Sync failed — retry/details" (plain text) | `res/values*/strings.xml`, `RobiaApp.kt` `garmentCloudStatusLabel`, `cloudSetupSummaryRes`, `itemStatusLabelRes` |
| Manage lag | each category = one lazy item rendering all rows; `RobiaShell` maps every garment with `@Composable toUiWardrobeItems` on every recomposition for all routes | `ManageTagsScreen.kt`, `RobiaApp.kt` |
| CI | JDK 17, Gradle 8.9, TFLite drift check, `assembleDebug` only | `.github/workflows/android-apk.yml` |

---

## 2. Architecture overview

```
ui (Compose)
 ├─ AddEdit / Batch ──► SuggestionService ──► EmbeddingEngineHolder ──► LiteRT-LM
 ├─ Browse search  ──► SearchRanker      ─┘        ▲
 ├─ TagReview      ──► ReviewPlanner     ─┐        │ ModelStore (download/verify)
 ├─ Manage (redesign)                     │
 └─ Settings (model, dev benchmark)       ▼
                         EmbeddingRepository (Room garment_embeddings)
                         EmbeddingBackfill (foreground queue)
sync
 ├─ WardrobeSyncOutboxProcessor (single brain, unchanged ownership)
 ├─ DriveSyncWorker (WorkManager) ─► processor.runOnce()
 ├─ GoogleDriveWardrobeRepository (+ DriveFileIndex, PhotoBlobKeys, HttpErrorClassifier)
 └─ snapshot JSON (+ optional embeddings, tag fields, writerDeviceId)
```

New package `…/suggestions/` holds all pure-Kotlin logic (no Android imports except where stated) so it is unit-testable on the JVM:

| File | Responsibility | Android deps |
|---|---|---|
| `suggestions/EmbeddingRecipe.kt` | constants (DEC-003/005, REQ-E04), prefixes, default label descriptions | none |
| `suggestions/VectorMath.kt` | dot, normalize, z-score, float32 LE ⇄ ByteArray ⇄ base64 | none (java.util.Base64) |
| `suggestions/CategoryScorer.kt` | DEC-008 | none |
| `suggestions/OccasionSelector.kt` | DEC-009 | none |
| `suggestions/ReviewPlanner.kt` | DEC-010 | none |
| `suggestions/SearchRanker.kt` | DEC-011 + fallback text match | none |
| `suggestions/SuggestionService.kt` | orchestrates engine + repository + scorers for capture | Context |
| `suggestions/engine/EmbeddingEngineHolder.kt` | DEC-013 lifecycle; interface `TextImageEmbedder` | LiteRT-LM, ProcessLifecycleOwner |
| `suggestions/engine/ImageInputEncoder.kt` | URI → white-composited PNG ≤ 1024 bytes | Bitmap |
| `suggestions/model/ModelStore.kt` | download/resume/verify/delete state machine | OkHttp-free: `HttpURLConnection` |
| `suggestions/EmbeddingBackfill.kt` | foreground queue + progress flow | coroutines |
| `data/local/EmbeddingEntities.kt`, `data/local/EmbeddingDao.kt`, `data/EmbeddingRepository.kt` | storage | Room |

---

## 3. smart-suggestions design

### 3.1 Recipe constants (`EmbeddingRecipe.kt`)

```kotlin
object EmbeddingRecipe {
    const val MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"
    const val RECIPE_ID = "eg2-r1"
    const val MODEL_FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    const val MODEL_APPROX_MB = 388
    const val DIMENSION = 768
    const val VISION_TOKENS = 140          // NFR-01 fallback: 70 (pre-approved, change here only)
    const val IMAGE_MAX_EDGE_PX = 1024
    const val CLASSIFICATION_PREFIX = "task: classification | text: "
    const val QUERY_PREFIX = "task: search query | text: "
    const val DOCUMENT_PREFIX = "title: none | text: "
    const val CATEGORY_MARGIN_TEXT = 0.010f
    const val CATEGORY_MARGIN_COMBINED = 0.50f
    const val OCCASION_MARGIN_TEXT = 0.010f
    const val OCCASION_MARGIN_COMBINED = 0.50f
    const val KNN_TOP = 3
    const val SEARCH_IMAGE_WEIGHT = 0.7f
    const val SEARCH_TEXT_WEIGHT = 0.3f
    const val ENGINE_IDLE_RELEASE_MS = 60_000L
    val SUGGEST_OCCASIONS_DEFAULT_OFF = setOf("category-accessories", "category-jewelry")
    val DEFAULT_LABEL_DESCRIPTIONS: Map<String, String> = mapOf(/* copy verbatim from tools/embedding-precision/labels.py CATEGORIES + OCCASIONS second tuple element */)
}
```

`DEFAULT_LABEL_DESCRIPTIONS` content is exactly the strings in `labels.py` (19 categories + 6 occasions). A JVM test asserts 25 entries and that every default `category-*`/`occasion-*` id in `DefaultTags.tags` has an entry.

**Label text resolution** (`labelTextFor(tag)`):
1. if `tag.description` non-blank → `CLASSIFICATION_PREFIX + description`;
2. else if `tag.id` in defaults AND `TagDisplayPolicy.shouldUseStoredName(tag) == false` (not renamed) → `CLASSIFICATION_PREFIX + DEFAULT_LABEL_DESCRIPTIONS[id]`;
3. else → `CLASSIFICATION_PREFIX + tag.name`.

Label vectors are cached in memory keyed by `(modelId, recipeId, labelText)`; recomputed lazily (text embedding ~ tens of ms).

**Garment TEXT input:** `DOCUMENT_PREFIX + listOf(name.trim(), notes.trim()).filter(String::isNotEmpty).joinToString(". ")`; if both empty → no TEXT vector (delete existing one).

**Input hashes:** IMAGE `inputHash` = SHA-256 hex (first 16) of the encoded PNG bytes passed to the engine; TEXT `inputHash` = SHA-256 hex (first 16) of the full input string.

### 3.2 Image encoder (`ImageInputEncoder.kt`)
`suspend fun encode(context, uri): EncodedImage(bytes: ByteArray, width: Int, height: Int)` on `Dispatchers.IO`: decode with `ImageDecoder` (API ≥ 28) or `BitmapFactory` with `inSampleSize` so that longest edge ≥ 1024 then scale to exactly ≤ 1024 (`Bitmap.createScaledBitmap`, filter=true); draw on an ARGB_8888 canvas filled `Color.WHITE`; compress PNG 100. Recycle intermediate bitmaps. Same function backs "Export input image" (writes these exact bytes to `Pictures/Robia/` via MediaStore — reuse the existing export helper pattern from `AdditionalInfoInputImageExporter` before deleting it).

### 3.3 Engine holder (`EmbeddingEngineHolder.kt`)

```kotlin
interface TextImageEmbedder {
    suspend fun embedImage(png: ByteArray): FloatArray   // normalized, size DIMENSION
    suspend fun embedText(text: String): FloatArray
}
class EmbeddingEngineHolder(
    private val modelStore: ModelStore,
    private val scope: CoroutineScope,                 // app-wide SupervisorJob + Dispatchers.Default
    private val factory: (modelPath: String) -> NativeEmbedder = ::LiteRtNativeEmbedder,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : TextImageEmbedder {
    val state: StateFlow<EngineState>  // Unavailable(reason) | Idle | Loading | Ready
    fun preload()                      // fire-and-forget load if model Ready
    fun release()
}
```
- One `Mutex` guards load and every compute (REQ-E02). Each call resets an idle job (`delay(60_000)` then `release()`).
- `LiteRtNativeEmbedder` wraps `EmbeddingEngine(EmbeddingEngineConfig(modelPath = path, backend = Backend.CPU(), visionBackend = Backend.CPU(), cacheDir = context.cacheDir.absolutePath, visionTokensPerImage = VISION_TOKENS))`, `initialize()`, compute with `EmbeddingOptions(normalize = true, visionTokensPerImage = VISION_TOKENS)` and `listOf(InputData.Image(png))` / `listOf(InputData.Text(text))`, `close()` on release. All native calls on `Dispatchers.Default`.
- Registered in a new `RobiaApplication : Application` (add `android:name=".RobiaApplication"` in manifest) holding app singletons: database, repositories, sync processor, `ModelStore`, `EmbeddingEngineHolder`, `EmbeddingBackfill`. `MainActivity` reads them from the application instead of constructing them. `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`) ON_STOP → `release()` and `backfill.pause()`; ON_START → `backfill.resume()`. `onTrimMemory(level >= TRIM_MEMORY_RUNNING_LOW)` → `release()`.
- Validation: result size must equal `DIMENSION` and all finite, else throw `EmbeddingFailure`.

### 3.4 Model store (`ModelStore.kt`)
States: `NotDownloaded`, `WaitingForWifi`, `Downloading(bytes, total)`, `Verifying`, `Ready(path)`, `Failed(reason: NoNetwork | Http(code) | CorruptDownload | NoSpace)`.
- Files: partial `filesDir/models/<MODEL_FILE_NAME>.part`, final `filesDir/models/<MODEL_FILE_NAME>`. Ready iff final exists AND a sidecar `…​.sha256` contains `MODEL_SHA256` (written after verification), avoiding re-hashing 388 MB at each start.
- Resume: `Range: bytes=<partSize>-`; accept 206 (append) or 200 (truncate and restart).
- Network policy: download only when `ConnectivityManager.activeNetwork` has `NET_CAPABILITY_NOT_METERED`, unless the user confirmed mobile data for this download (flag in memory + DataStore `modelMobileDataApproved` reset after completion).
- Runs inside a WorkManager unique job `robia-model-download` (`CoroutineWorker`, constraint `NetworkType.UNMETERED`, or `CONNECTED` when mobile data was approved; no foreground service, no notification). If Android stops the worker (≈10 min limit), it returns `Result.retry()` and the next run resumes with `Range`. Progress is written to the worker progress (`setProgress`) and mirrored into `ModelStore.state` while the app is open.
- Space check: require `filesDir.usableSpace > 450 MB` before starting.
- Delete: removes final, sidecar and part; holder `release()` first.
- DataStore keys (in `SettingsRepository`): `model_prompt_shown: Boolean`, `model_mobile_data_approved: Boolean`.

### 3.5 Storage (Room v13)

```kotlin
@Entity(
  tableName = "garment_embeddings",
  primaryKeys = ["garment_id", "kind"],
  foreignKeys = [ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["garment_id"], onDelete = ForeignKey.CASCADE)],
)
data class GarmentEmbeddingEntity(
  @ColumnInfo(name = "garment_id") val garmentId: String,
  val kind: String,                       // "image" | "text"
  @ColumnInfo(name = "model_id") val modelId: String,
  @ColumnInfo(name = "recipe_id") val recipeId: String,
  val dim: Int,
  val vector: ByteArray,                  // float32 little-endian, dim*4 bytes
  @ColumnInfo(name = "input_hash") val inputHash: String,
  @ColumnInfo(name = "computed_at_epoch_millis") val computedAtEpochMillis: Long,
  @ColumnInfo(name = "duration_millis") val durationMillis: Long,
  @ColumnInfo(name = "pending_upload") val pendingUpload: Boolean = true,
)
```
`MIGRATION_12_13`:
```sql
CREATE TABLE IF NOT EXISTS garment_embeddings (garment_id TEXT NOT NULL, kind TEXT NOT NULL, model_id TEXT NOT NULL, recipe_id TEXT NOT NULL, dim INTEGER NOT NULL, vector BLOB NOT NULL, input_hash TEXT NOT NULL, computed_at_epoch_millis INTEGER NOT NULL, duration_millis INTEGER NOT NULL, pending_upload INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(garment_id, kind), FOREIGN KEY(garment_id) REFERENCES clothing_items(id) ON UPDATE NO ACTION ON DELETE CASCADE);
ALTER TABLE garment_tags ADD COLUMN description TEXT;
ALTER TABLE garment_tags ADD COLUMN suggest_occasions INTEGER NOT NULL DEFAULT 1;
UPDATE garment_tags SET suggest_occasions = 0 WHERE id IN ('category-accessories','category-jewelry');
```
Domain: `GarmentTag` gains `description: String? = null`, `suggestOccasions: Boolean = true` (appended last, defaults keep existing call sites compiling). `DefaultTags` sets `suggestOccasions = false` for the two ids. `DefaultTags.isCustomOrModifiedDefault(tag)` additionally compares `description` and `suggestOccasions`. All `GarmentTag ⇄ GarmentTagEntity` and `TagSyncRecord` mappers updated (`LocalRepositories.kt`, `LocalWardrobeSyncSnapshotRepository.kt`, `WardrobeSyncOutboxProcessor.kt` `TagSyncRecord.toDomain`).

`EmbeddingDao`:
- `observeAll(): Flow<List<GarmentEmbeddingEntity>>` (used by search/review/scoring; ~6 KB × N rows — fine for N ≤ 2000).
- `get(garmentId, kind)`, `upsert(entity)`, `delete(garmentId, kind)`, `markUploaded(keys)`, `pendingUpload()`.
- `garmentsNeedingImage(modelId, recipeId)`: active garments with `photo_uri NOT NULL` and no image row matching model+recipe, ordered `created_at_epoch_millis DESC`. Input-hash staleness is checked by the backfill after encoding (cheap compared to inference).

`EmbeddingRepository` exposes decoded `GarmentVectors(garmentId, image: FloatArray?, text: FloatArray?)` maps for the current model+recipe only.

### 3.6 Scoring (`CategoryScorer.kt`, `OccasionSelector.kt`)

```kotlin
data class LabelScore(val tagId: String, val text: Float, val knn: Float?, val score: Float)
enum class ScoreMode { TEXT, COMBINED }
data class CategoryResult(val ranked: List<LabelScore>, val mode: ScoreMode, val margin: Float, val marked: String?, val reason: String)
fun scoreLabels(image: FloatArray, labels: List<Pair<String, FloatArray>>, examples: Map<String, List<FloatArray>>): Pair<List<LabelScore>, ScoreMode>
fun selectCategory(scored, mode): CategoryResult
fun selectOccasions(scored, mode): OccasionResult(ranked, mode, g12, g23, selected: List<String>, reason)
```
Exact maths in DEC-008/009. `examples[tagId]` = IMAGE vectors of saved garments (excluding the garment being scored) carrying that tag. z uses population std; if std < 1e-6 the z vector is all zeros. Ties: sort by score desc then tagId asc. `reason` values (used in dev mode): `"margin_ok"`, `"margin_below:<value>"`, `"no_labels"`, `"flag_off"`, `"no_image_vector"`.

Fixture tests use small synthetic vectors (dim 4) with hand-computed expected scores, plus one golden fixture exported from the Pi tool: `app/src/test/resources/suggestions/golden-scores.json` (full 768-d IMAGE vectors for 6 garments, 25 label vectors, and the Python-computed rankings/margins/selections for both TEXT and COMBINED modes; ~250 KB; generated by `tools/embedding-precision/export_fixture.py`). The Kotlin test asserts identical rankings and selections and `abs(score diff) ≤ 1e-4`.

### 3.7 Capture integration
`SuggestionService.suggestForPhoto(photoUri, availableTags, currentSelection): SuggestionOutcome`:
1. If model not Ready → `SuggestionOutcome.Unavailable` (no tags touched).
2. Encode image, `embedImage`, keep vector + hash + duration (stored on save so no recomputation).
3. Category: labels = tags with `categoryId == "category"`; examples from repository; `selectCategory`.
4. Occasion: if resulting category (marked or user-selected) has `suggestOccasions == false` → skip with reason `flag_off`; else `selectOccasions`.
5. Return marked ids + full diagnostics.

`AddEditClothingScreen`: replace the detector stage with `PhotoProcessingStage.PreparingSuggestions` (string "Preparing suggestions"), call `suggestForPhoto` in the same places the detector is called today (fresh photo after color extraction; Quick Edit `reprocessEditedPhoto`). Keep the generation/token guards and the user-override logic, with `MODEL_PREDICTED_CATEGORIES = setOf("category", "occasion")` (season removed). Delete the original-photo fallback re-run (it existed for the classifier only). Preload: `LaunchedEffect(Unit) { holder.preload() }` when the screen opens with no existing photo or a photo is picked.

On save (`onSaveItem` in `RobiaApp.kt`): after `wardrobeRepository.upsertItem(item)`, call `embeddingRepository.onGarmentSaved(item, pendingCaptureVector?)`:
- if the capture produced a vector for the saved `photoUri` → store it (image kind);
- else if `photoUri` changed vs stored hash → enqueue backfill for that garment;
- TEXT: recompute if TEXT input hash differs (via backfill, foreground).
Never bump garment revision (REQ-E03).

Batch: `processBatchDraft` takes `SuggestionService` instead of the detector; draft keeps `suggestionVector` (FloatArray? excluded from equality via wrapper) to store on save; `mergePredictedTags` keeps semantics (replace only model-owned categories).

### 3.8 Backfill (`EmbeddingBackfill.kt`)
- `progress: StateFlow<BackfillProgress(remaining: Int, running: Boolean)>`.
- Loop while resumed and model Ready: take next garment from `garmentsNeedingImage` + garments whose TEXT hash is stale (computed in Kotlin from current name/notes); compute; upsert; yield. On `EmbeddingFailure` for a garment: record attempt count in memory, skip after 2 failures until next app start.
- Starts after model becomes Ready, on app foreground, and after restore import.
- Browse shows `SuggestionsProgressBanner` when `remaining > 0` (string `suggestions_preparing_count` plural: "Preparing suggestions for %d garments…").

### 3.9 Drive fields (embeddings & tag fields)
Snapshot JSON additions (DriveSnapshotJson encode/decode in `GoogleDriveWardrobeRepository.kt`):
```json
"garments": [{ "...existing...": "", "embeddings": [ {"kind":"image","modelId":"…","recipeId":"eg2-r1","dim":768,"inputHash":"…","b64":"…"} ] }]
"taxonomies": { "tags": [{ "...": "", "description": "…" , "suggestOccasions": false }] }
"metadata": { "...": "", "writerDeviceId": "uuid" }
```
- Model: `GarmentSyncRecord.embeddings: List<EmbeddingSyncRecord> = emptyList()`; `TagSyncRecord.description: String? = null`, `suggestOccasions: Boolean = true`; `WardrobeSnapshotMetadata.writerDeviceId: String? = null`.
- Decode: missing → defaults. `suggestOccasions` missing → `true` except the two default-off ids.
- Export: `LocalWardrobeSyncSnapshotRepository.exportSnapshot` joins `EmbeddingDao` rows (current model+recipe) into each garment record. After a successful upload, `markUploaded`.
- Embedding-only changes: `EmbeddingRepository.upsert` with `pendingUpload = true` enqueues the sync worker; the processor treats `pendingUpload` rows as pending work that requires a snapshot upload **without** changing garment revisions (new query `hasPendingEmbeddingUploads()` OR-ed into "pending work").
- Import: rows with matching model+recipe and `dim == 768` and valid base64 length → insert with `pendingUpload = false`; others ignored (backfill recomputes).
- Merge (`mergeSnapshots`): embeddings travel with the winning garment record; if the winning record has none and the other side has matching ones with the same `inputHash` as computed locally, keep them (simple rule: union by `(garmentId, kind)` preferring newer `computedAt` — `computedAt` added to the record).

### 3.10 Review flow
New `ui/TagReviewScreen.kt`, structurally copied from `ColorReviewScreen.kt` (progress header, 2-column grid of candidate cards, per-card accept, bottom commit button, discard dialog):
- `TagChangeSet(operation: Added|Renamed|Deleted|RestoredDefault, categoryId: "category"|"occasion", touchedTagIds, beforeTags, afterTags, id)`.
- Candidate card: photo, current value chip(s) → proposed chip(s); tap proposed chip opens a picker with all tags of that category (and "None" for occasion); "Accept" button.
- Header line "Skipped N garments without suggestions yet" when applicable.
- Commit: `onSaveItems(updatedItems)` (tags changed → normal revision bump + sync, correct because the user changed data).
- Wiring in `RobiaApp.kt` mirrors `saveMainColorAndOfferReview` / `commitColorReview`: `onSaveTag`, `onDeleteCustomTag`, `onRestoreDefaultTags` for categories `category`/`occasion` first persist the tag change (same as today), then compute `ReviewPlanner.plan(...)`; if non-empty → `activeTagReviewChangeSet = …; replaceRoute(RobiaRoute.TagReview)`; if empty → toast "No garments need changes". Model not Ready → no review, toast "Download smart suggestions to review garments".
- New route `RobiaRoute.TagReview` (title "Review garments"), hidden bottom bar, back → discard dialog (discard keeps the tag change, drops proposals).

### 3.11 Search
- `BrowseFilterState` unchanged; new `var browseQuery by rememberSaveable { mutableStateOf("") }` in `RobiaShell`.
- `FilterBar` gains a `OutlinedTextField`-style search pill (per mockup) with clear button.
- Derivation in `RobiaShell`: `filteredItems` (existing) → if query blank: unchanged order; else `SearchRanker.rank(filteredItems ids, queryVector?, vectors, nameNotes)` computed in `LaunchedEffect(query, filtered, vectors)` with 300 ms debounce, `holder.preload()` on first non-blank char; query embedding via `embedText(QUERY_PREFIX + query)`.
- Fallback when no query vector: normalize (`Normalizer.NFD` + strip diacritics + lowercase) and keep items whose name or notes contain all query words; order unchanged.
- Similar garments: route arg `similarToGarmentId`; ranking by `cos(IMAGE_self, IMAGE_j)`; Browse shows a dismissible chip "Similar to <name>" replacing the search text.

### 3.12 Developer diagnostics
- `ItemDetailScreen` gets `developerModeEnabled` + `SuggestionDiagnostics?` (computed on demand by `SuggestionService.diagnose(garment)` — reuses stored IMAGE vector, no inference except label vectors). Card "Suggestion diagnostics" (monospace) + buttons "Recompute embedding" (deletes rows → backfill with priority for that garment) and "Export input image".
- `AddEditClothingScreen` DeveloperDiagnosticsSection: replace `addDetectionDebugLines` with lines from `SuggestionOutcome` (mode, top-5 with text/knn/score, margin, marked, reason, duration).
- Settings developer section: "Model benchmark" → runs 1 load + 5 image embeddings on a bundled 512×512 test image (`app/src/main/assets/benchmark/garment.png`, synthetic, generated by the Pi tool) and shows: `load_ms`, `median_ms`, `p90_ms`, `peak_pss_mb` (`Debug.getPss()` max sampled every 200 ms), `vision_tokens`, `device`. Copy-to-clipboard button. Output also logged to the dev sync log.

### 3.13 Removal list (REQ-B04)
Delete: `settings.gradle.kts` includes for `:additional-info-core` and `:additional-info-cli`; directories `additional-info-core/`, `additional-info-cli/`; `…/media/additionalinfo/`; `app/src/main/assets/additional_info/`; `libs.tensorflow.lite` + version; `app/build.gradle.kts` `implementation(project(":additional-info-core"))` and tensorflow-lite; scripts `check_additional_info_android_parity_static.py`, `check_additional_info_tflite_drift.py`, `debug_additional_info_inference.py`, `run_additional_info_shared_cli.sh`; `docs/additional_info_classifier_debug.md`; tests `AdditionalInfoModelContractTest.kt`, `AdditionalInfoTagMapperTest.kt`; CI steps "Set up Python for TFLite drift check", "Install TFLite drift check dependencies", "Check bundled TFLite model drift"; strings `rename_ai_tag_*`, `delete_ai_tag_*`, `rename_anyway`, `delete_anyway` (only if unused elsewhere), `processing_stage_detecting_additional_information`, `developer_export_input_image*` (replaced by `developer_export_suggestion_input*`) in all three locales; `RegressionSourceContractTest` assertions that reference removed symbols are deleted or rewritten to the new symbols. `EditorPhotoState.additionalInfoSourceUri` → `suggestionSourceUri`, `canonicalAdditionalInfoUri` → `canonicalSuggestionUri`. `AdditionalInfoPrediction.kt` types (`AdditionalInfoDetectionResult`, `AdditionalInfoLabelScore`) removed with the core module. Season behaviour is preserved exactly: adding season tags stays disabled (`allowsNewTags = category.id != "season"`); the four default season tags are editable today only because they were model-output ids, so `canEdit` becomes `true` for all rows (same observable result, no special-case).

---

## 4. cloud-sync design

### 4.1 Photo keys and local names (REQ-S01)
New `…/sync/PhotoBlobKeys.kt`:
```kotlin
object PhotoBlobKeys {
    fun hash16(bytes: ByteArray): String            // sha256 hex, first 16 chars
    fun driveKey(garmentId: String, hash16: String) = "photos/${safe(garmentId)}/$hash16.webp"
    fun restoredLocalFileName(garmentId: String, hash16: String) = "restored-${safe(garmentId)}-$hash16.webp"
    fun isLegacyKey(blobPath: String): Boolean      // !matches Regex("^photos/[A-Za-z0-9._-]+/[0-9a-f]{16}\\.webp$")
}
```
- `ClothingImageStore.writeRestoredImageBlob(context, bytes, blobPath, mimeType)` is replaced by `writeRestoredImageFile(context, garmentId, hash16, source: File): Uri` that **moves** a downloaded temp file to `robia_clothing_images/restored-<garmentId>-<hash16>.webp` (overwrite allowed; same content → same name).
- `ClothingImageStore.createImageFile` unchanged for camera/gallery inputs (prefix + UUID, bounded length).
- `DriveFolderNaming.photoBlobPath(id, localUri)` is no longer used for new uploads; `jsonToPhoto` fallback keeps it only for legacy snapshots lacking `blobPath`.

### 4.2 Upload encoding and change detection (REQ-S02, S03)
- `ClothingImageStore.readCanonicalDriveImageBlob` becomes `encodeDriveWebp(context, uri): ImageBlob`: decode (ImageDecoder software allocator / BitmapFactory), downscale so longest edge ≤ 2048, compress `WEBP_LOSSY` q90 on API ≥ 30, `@Suppress("DEPRECATION") WEBP` q90 on API 26–29 (alpha preserved by both), bounds-decode verify, `mimeType = "image/webp"`, `contentHash = sha256 full hex`, `hash16`.
- **Local source hash cache:** new Room-free in-memory + DataStore-free approach: table not needed — the snapshot already stores `photos[].contentHash` (encoded WebP hash) and gains `sourceHash` (sha256 of the local file bytes, hash16). Upload decision per garment photo:
  1. compute `sourceHash16` of the local file (streamed digest, no decode);
  2. if previous uploaded snapshot record (kept locally, see below) has same `sourceHash` and the blob key exists in the Drive listing → skip encode + upload, copy previous `blobPath/contentHash/byteSize`;
  3. else encode WebP, upload to `driveKey(garmentId, hash16(webpBytes))`.
- **Last uploaded snapshot cache:** after each successful snapshot upload, store the `photos` array (garmentId, sourceHash, blobPath, contentHash, byteSize, mimeType) in `filesDir/sync/last_uploaded_photos.json` (atomic write via temp + rename). On fresh install / missing file, the remote snapshot's photos (fetched at the start of every upload cycle, see 4.5) are used, with `sourceHash` absent → re-upload once.
- **Purge after verified upload:** after `api.upsertSnapshot` succeeds, using the listing from this cycle: delete every `photos/<id>/*` blob not referenced by the new snapshot, and every `photos/<id>/*` where `<id>` is tombstoned. Deletion failures are logged, retried next cycle, never fail the sync.

### 4.3 Drive file index and HTTP layer (REQ-S04, S05)
- `DriveSnapshotApi` gains `listAll(accessToken): DriveApiResult<List<DriveFileMetadata>>` (`files?spaces=appDataFolder&fields=nextPageToken,files(id,name,size,md5Checksum,modifiedTime)&pageSize=1000`, follows `nextPageToken`). `DriveFileMetadata` gains `name: String`.
- New `DriveFileIndex(files)` with `idOf(name)`, `namesWithPrefix(prefix)`. Built once per cycle; updated in memory after each upload/delete.
- `fetchBlob`, `upsertBlob`, `deleteBlob` take an optional `fileId` from the index; `findFileByName` stays only as fallback when no index is available (retryRestoredPhoto single-photo path).
- New `fetchBlobToFile(accessToken, fileId, target: File, onProgress)`: streams `alt=media` into `target` (64 KB buffer), returns size; no `ByteArray` of the whole photo.
- New `…/sync/DriveHttpErrors.kt`:
```kotlin
sealed interface DriveErrorClass { data object Transient; data object AuthRequired; data object StorageFull; data object NotFound; data class Permanent(val code: Int) }
fun classify(code: Int, body: String): DriveErrorClass
```
Rules: 401 → AuthRequired; 403 with `reason` in {`rateLimitExceeded`,`userRateLimitExceeded`,`backendError`} → Transient; 403 `storageQuotaExceeded` → StorageFull; other 403 → AuthRequired; 404 → NotFound; 408/429/5xx → Transient; other 4xx → Permanent. IOExceptions (timeout, DNS, connect, reset) → Transient. `DriveApiResult` gains `data class Transient(val cause: Throwable)` and `data object StorageFull`; `Unauthorized` kept for AuthRequired.
- `withRetry(maxAttempts = 5) { … }` wrapper in `HttpDriveSnapshotApi` for Transient: delays 1, 2, 4, 8 s × random(0.8..1.2). Applies to single requests, not to whole cycles.
- Timeouts: connect 15 s, read 30 s (unchanged) per request; `withDrivePhaseDeadline` (60 s for whole operation) removed; replaced by per-photo `withTimeout(60_000)` around each download/upload, and authorization timeout unchanged (30 s).

### 4.4 Restore (REQ-S04, S08, S11)
`hydratePhotoBlobs` rewritten:
- Build index once; for each photo record, resolve fileId by exact `blobPath`; legacy fallback: if missing, single candidate among `namesWithPrefix("photos/<id>/")` (existing rule).
- Download with `Semaphore(4)` + `coroutineScope { photos.map { async(Dispatchers.IO) { … } } }` into `cacheDir/restore/<garmentId>.part`; verify bounds-decode once (`BitmapFactory.Options.inJustDecodeBounds` from file); compute hash16 of file bytes (streamed); move to final local name (4.1).
- Outcomes per photo: Restored(uri, size, w, h) | Guarded(category, message) (missing, unreadable, timeout, transient after retries). Auth/StorageFull abort the whole restore (cannot continue meaningfully); Transient on a single photo → Guarded with category `remote_photo_transient` (retry button works, and the processor auto-retries guarded transient photos on next cycle — see 4.6).
- Progress: aggregate counters updated atomically; emit at most every 250 ms (existing throttle).
- After restore import: **no final re-upload** unless the merged snapshot differs from the remote one (compare garment/tag/color/tombstone records and photo `sourceHash`); embeddings are not a reason to upload during restore.
- Debug decodes: keep one bounds decode; delete `readBackBytes` full re-read and `readImageDimensions` second pass.

### 4.5 Processor changes (REQ-S06, S07, S08, S10)
`WardrobeSyncOutboxProcessor` keeps ownership of the mutex and state, plus:
- `suspend fun runOnce(trigger: SyncTrigger): SyncRunResult` — public entry used by the worker and the UI (`enqueue` now schedules the worker and, if the app is in the foreground, also runs `runOnce` directly; both paths share the mutex so no double work).
- Remove the global-guard checks: `processPendingGarments` and `scheduleWorkFor` no longer return early on `hasGuardedPhotoRestoreIssues`; `syncUploadLocalSnapshot` no longer returns `Attention` for guards. Instead, export marks guarded garments so that their remote photo record is **copied from the remote snapshot** (fetched at cycle start) — never deleted (REQ-S08). If the remote snapshot is unavailable (transient), the cycle aborts as Transient.
- Each upload cycle: (1) fetch remote snapshot metadata+photos (JSON only, small); (2) newer-writer check; (3) build index; (4) upload changed photos (sequential, per-photo timeout, a per-photo permanent failure marks that garment `NeedsUserAction` with category `photo_upload_failed` and continues; its previous remote photo record is preserved); (5) upload snapshot; (6) purge; (7) persist `last_uploaded_photos.json`, `lastKnownRemoteRevision`; (8) mark synced (garments, metadata, embeddings `pendingUpload=false`).
- Remove `SYNC_OPERATION_TIMEOUT_MILLIS` whole-cycle timeout (per-request/photo timeouts replace it). Keep cancellation handling (`releaseClaimedWork`) but release to `Queued` (not failed).
- Error mapping (cycle level): Transient → release claimed work to `Queued`, return `Retry`; AuthRequired → `AuthBlocked`; StorageFull → items `NeedsUserAction` with `syncFailureMessage = "drive_full"`; NewerWriter → nothing marked, settings `syncConflict = true`, return `Conflict`.
- **DAO retry semantics:** `markGarmentSyncFailedRetryable` and the four metadata equivalents are no longer used for transient errors. New DAO methods `releaseGarmentToQueued(id, revision)` and `releaseMetadataToQueued(work)` set `sync_status='Queued', sync_started_at_epoch_millis=NULL, retry_after_epoch_millis=NULL` (keep `sync_failure_message` NULL). `markGarmentNeedsUserAction(id, revision, reasonCode)` for user-action errors. The 3-attempt cap remains only for photo-restore retry (`markGarmentPhotoRestoreFailed`) but restore-transient guarded photos get unlimited automatic retries (attempt counter not incremented for `remote_photo_transient`).
- `scheduleRetryWakeUp` removed; WorkManager backoff replaces it.
- **Newer-writer protection:** settings gain `deviceId` (UUID created on first read), `lastKnownRemoteRevision: Long` and `syncConflict: Boolean`. Before upload: if `remote.metadata.writerDeviceId != null && != deviceId && remote.metadata.revision > lastKnownRemoteRevision` → Conflict. Resolution actions: "Use backup" = `ImportFullSnapshot` forced replace (clear local garments/tags/embeddings in one transaction, then the existing restore path), "Keep this phone" = set `lastKnownRemoteRevision = remote.revision` and upload. Exported metadata sets `writerDeviceId = deviceId`.
- **Connect on non-empty phone:** `MainActivity.persistDriveAuthorizationResult` currently enqueues `ImportFullSnapshot`; for a non-empty phone the processor fetches the remote snapshot, computes `MergeSummary(remoteGarments, localGarments, mergedGarments)` and stores it in state `pendingMergeSummary`; UI shows a dialog "Drive: X · This phone: Y → Z garments. Merge" / "Cancel"; on confirm → `mergeSnapshots` (existing) + import + upload. The `CloudSetupGuard`/`LateEnableBlocked` path that today forbids late enabling is removed (DEC-014, round-2 answer 5A).

### 4.6 WorkManager (REQ-S06)
- Dependency `androidx.work:work-runtime-ktx:2.11.2`.
- `…/sync/DriveSyncWorker.kt` (`CoroutineWorker`): gets the processor from `RobiaApplication`; `runOnce(SyncTrigger.Background)`; `Retry` → `Result.retry()`; others → `Result.success()`.
- `…/sync/DriveSyncScheduler.kt`: `fun requestSync(context)` → `enqueueUniqueWork("robia-drive-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<DriveSyncWorker>().setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())`. Plus periodic safety net `enqueueUniquePeriodicWork("robia-drive-sync-periodic", KEEP, 6 h, CONNECTED)` only while Drive is Connected (cancelled on disconnect/backup deletion).
- Called from: `onSaveItem(s)`, tag/color changes, deletes, embedding upserts with `pendingUpload`, app start when pending > 0, connectivity regained (implicit via constraint).
- Auth in background: `AuthorizationClient.authorize` works without UI when previously granted; if it returns `hasResolution()` → AuthBlocked state (user taps "Sign in again" in-app).

### 4.7 Sync UI states (REQ-S09)
New `…/sync/SyncAttention.kt`:
```kotlin
enum class SyncAttentionKind { SignInRequired, DriveFull, PhotoUnrecoverable, PhotoUploadFailed, BackupConflict, MergeConfirmation }
data class SyncAttention(val kind: SyncAttentionKind, val garmentIds: List<String>)
```
derived in the processor state from DB + settings. UI (per mockup `sync-states.html`):
- Per-garment label (`garmentCloudStatusLabel`): Synced / Pending upload / Uploading… / Saved on this device (not connected) / one of the attention labels. `FailedRetryable` is no longer produced; existing rows with it are migrated to `Queued` in `MIGRATION_12_13` (`UPDATE clothing_items SET sync_status='Queued', retry_attempt_count=0, retry_after_epoch_millis=NULL WHERE sync_status IN ('FailedRetryable')` — `NeedsUserAction` rows without `photo_restore_guarded` are also reset to `Queued`; same for the four metadata tables and tombstones).
- Browse top: an `AttentionBanner` card (only when attention exists) with title, one-sentence cause and buttons. Item detail: same card scoped to that garment.
- Buttons → handlers: SignInRequired → `onRequestCloudSetup`; DriveFull → "Retry" (`requestSync`) + "Open Google storage" (intent `https://one.google.com/storage`); PhotoUnrecoverable → "Retry photo" (existing `RetryRestoredPhoto`) + "Keep without photo" (clears guard, keeps garment without photo, normal upload); PhotoUploadFailed → "Retry"; BackupConflict → dialog with two actions (4.5); MergeConfirmation → dialog.
- "Details" text button visible only in developer mode → `RobiaRoute.DeveloperSyncLog`.
- Strings removed: `garment_sync_failed_retryable`. New strings in EN/ES/DE (exact EN copy in the mockup file; ES/DE translations provided in `tasks.md` TASK-S05).

### 4.8 Restore log batching (REQ-S11)
`FileRestoreSyncLogRepository.append`: push the line into an in-memory buffer; a single writer coroutine flushes with `appendText` every 2 s or 50 lines; trimming (bounded lines/bytes) happens on flush only when file size > `maxBytes` (rewrite once). `text` flow updated on flush.

---

## 5. build-foundation design

### 5.1 Versions (DEC-002)
`gradle/libs.versions.toml`: `agp = "8.13.2"`, `kotlin = "2.4.21"`, `ksp = "2.3.12"` (KSP2 versioning is independent from Kotlin since 2.3.0), `room = "2.8.4"`, add `work = "2.11.2"`, `litertlm = "0.18.0"`, `lifecycleProcess = lifecycleRuntimeKtx`. Remove `tensorflowLite`. Other AndroidX versions unchanged unless the build requires a bump (record any bump in the receipt).
`app/build.gradle.kts`: `compileOptions` 21, `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }` (replaces deprecated `kotlinOptions`), `packaging { jniLibs { useLegacyPackaging = false } }` default; `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }` is NOT added (no device filter, DEC-016).
CI: `actions/setup-java` `java-version: '21'`, `gradle-version: 8.14.3` (or the minimum required by the chosen AGP).

### 5.2 Staging build type (DEC-015)
```kotlin
buildTypes {
    create("staging") {
        initWith(getByName("release"))
        isMinifyEnabled = true
        isShrinkResources = true
        isDebuggable = false
        signingConfig = if (hasRobiaCiDebugSigning) signingConfigs.getByName("robiaCiDebug") else signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
    release { isMinifyEnabled = true; isShrinkResources = true; /* existing signing */ }
}
```
The release-signing guard (`isReleaseBuildRequested`) must not trigger for `assembleStaging` (task names contain "staging", not "release" — verify).
`MainActivity.performanceFixtureUris` stays `BuildConfig.DEBUG`-gated (staging has `DEBUG=false`).

### 5.3 R8 rules (`app/proguard-rules.pro`)
```
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { native <methods>; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.ai.edge.litertlm.**
-keep class kotlin.Metadata { *; }
```
ML Kit, Play Services and Room ship consumer rules; add only what R8 reports as missing (`-dontwarn` lines generated in `app/build/outputs/mapping/staging/missing_rules.txt` must be reviewed, not blindly copied — copy is allowed only for `-dontwarn` of classes not referenced at runtime).

### 5.4 CI workflow
`android-apk.yml` debug job becomes `apks` job: remove TFLite steps; `gradle --no-daemon testDebugUnitTest assembleDebug assembleStaging`; upload `robia-debug-apk` and `robia-staging-apk`; print signing fingerprints for both. Release job: JDK 21 + gradle version bump only.

---

## 6. manage-redesign design (REQ-M01, M02)
- Implement per approved `mockups/manage.html`.
- Layout: top search field (filters tag names in the selected tab, accent-insensitive), a horizontally scrollable tab row with one tab per visible tag category in existing order plus a last tab "Colors" (`rememberSaveable` selected tab id, default first category). Only the selected tab's content is composed.
- `ManageTagsScreen` LazyColumn for a tag tab: `section-header:<categoryId>` (title, count, restore default, add) then `tag-row:<tagId>` items (`contentType = "tag-row"`; name, optional description line max 1 line ellipsized, badge "No occasions" when `categoryId == "category" && !suggestOccasions`, edit, delete), and `section-empty:<categoryId>` when no tags/no search match. For the Colors tab: the existing main-color palette card unchanged (key `"main-color-palette"`). Section visuals (card background/rounded corners) are achieved with per-item shapes: first row top corners, last row bottom corners (helper `sectionItemShape(index, size)`).
- Precompute `localizedName` for all tags once per locale: `remember(tags, configuration.locales) { tags.associate { it.id to it.localizedTagLabel(context) } }` using the non-Composable `localizedTagLabel(context)` added to `TagLocalization.kt` (pattern from unmerged commit `4f875a7`).
- `RobiaShell`: replace `val items = clothingItems.toUiWardrobeItems(syncState)` with `remember(context, clothingItems, syncState, needsWardrobeUiModels) { if (needsWardrobeUiModels) clothingItems.toUiWardrobeItems(syncState, context) else emptyList() }` where `needsWardrobeUiModels = currentRoute in setOf(Browse, ItemDetail, AdvancedFilters, TagReview)` and `toUiWardrobeItems` becomes non-Composable (context-based strings). `filteredItems` likewise gated.
- TagEditorDialog: name field, description field (placeholder example, 200-char counter, supporting text), switch "Suggest occasions for this category" with help text — switch shown only when `state.categoryId == "category"`.

---

## 7. Testing strategy and CI authority
- **CI authority:** GitHub Actions `Android APK CI` on the PR (`testDebugUnitTest`, `assembleDebug`, `assembleStaging`). No Android builds on the Raspberry Pi (README rule).
- **JVM unit tests** (new, under `app/src/test/java/com/gusanitolabs/robia/`): `suggestions/*Test.kt` (scorer, selector, planner, ranker, recipe, vector codec, golden fixture), `sync/PhotoBlobKeysTest`, `sync/DriveHttpErrorsTest`, `sync/HydrateConcurrencyTest` (fake `DriveSnapshotApi`), `sync/UploadChangeDetectionTest`, `sync/NewerWriterTest`, `sync/RestoreSyncLogBatchingTest`, `sync/SnapshotJsonCompatTest` (old JSON fixture from base commit decodes), `data/Migration12To13Test` → Room migration tests need Android (`MigrationTestHelper` is instrumented) → instead: a JVM test that executes the migration SQL on `org.xerial:sqlite-jdbc` (testImplementation) against the exported v12 schema JSON and asserts columns/defaults.
- **Robolectric is not introduced** (keeps CI fast); Android-dependent pieces are thin wrappers around pure functions.
- **Device checks** (Manu, Poco F3, staging APK): TASK-E01 benchmark; TASK-Z01 final checklist.

## 8. Risks and mitigations
| Risk | Mitigation |
|---|---|
| Kotlin 2.4 / AGP incompatibility with existing plugins (Compose BOM 2024.12) | TASK-B01 isolated, pre-approved fallback (DEC-002) |
| NFR-01 latency fails on Poco F3 | measured first (TASK-E01) before UI work; pre-approved 70-token fallback; else STOP |
| R8 strips JNI/Gson members → crash only on staging | keep rules + benchmark run on staging APK in TASK-E01 |
| Drive data loss during re-key | upload-then-snapshot-then-purge order; purge only after verified snapshot upload; legacy blobs readable |
| WorkManager auth without UI fails | AuthBlocked surfaced in-app with Sign-in button |
| ~1 GB RAM pressure kills app | lazy load, 60 s idle release, ON_STOP release, foreground-only backfill |
| Accuracy on real cutouts differs from Pi raw photos | dev-mode diagnostics; review flow requires user acceptance; margins in one constants file |

## 9. Rejected alternatives
- Bundling the model in the APK / Git LFS (size, bandwidth). Play AI packs (no Play release planned).
- Single combined embedding (image+name+notes) — no control over weighting; noise in classification.
- Fine-tuning in Colab (DEC-012).
- Full rewrite of the sync processor — high regression risk; targeted changes instead.
- Robolectric for Room tests — slower CI; sqlite-jdbc SQL test is enough for an additive migration.
