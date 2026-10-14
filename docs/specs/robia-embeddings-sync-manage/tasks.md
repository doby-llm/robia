# Tasks — robia-embeddings-sync-manage

Revision 1. Status of all tasks: `Ready` once G-APPROVAL passes (except where a gate is listed).

## Rules for every worker (read first)
0. Read `briefs/<TASK-ID>.md` first: it states the goal and the step-by-step implementation. This file stays authoritative for files, scope and acceptance.
1. Start from latest `origin/main`; branch `feat/<task-id-lowercase>-<slug>`; one PR per task; PR title `TASK-XXX: <title>`; PR body links this file and lists acceptance rows with evidence.
2. Touch **only** the files listed under "Files". If another file must change, stop and report (it is a planning change), except: imports, `strings.xml` in all three locales (`values`, `values-es`, `values-de`) and test files under `app/src/test/`.
3. Never build Android locally on the Raspberry Pi. Push and use CI: `gh pr checks <n> --watch` (repo `doby-llm/robia`). Read failing logs with `gh run view <id> --log-failed`.
4. Every new user-visible string goes to all three locales. Use the exact copy given in the task; if none is given, use the copy in the mockup HTML (`docs/specs/robia-embeddings-sync-manage/mockups/`).
5. Do not change behaviour outside the task's "In scope". Do not refactor unrelated code. Keep existing tests green; delete/adapt a test only when the task says so.
6. Code style: existing Kotlin style (4 spaces, trailing commas). No new libraries except those named in the task.
7. Budget: 90 min. At ~70 % push WIP and write the receipt. After 2 failed CI fix cycles: stop, mark Blocked with the failing log excerpt.
8. Receipt (PR comment): commit SHA, CI run URL, checked acceptance rows (V-IDs), anything not done.

Spec references: `technical-specification.md` §N = TS§N; decisions DEC-NNN in `decision-log.md`.

---

## TASK-B01 — Toolchain upgrade, staging APK, R8, CI
- **Reqs:** REQ-B01, REQ-B02, REQ-B03 · DEC-002, DEC-015 · TS§5
- **Predecessors:** G-APPROVAL · **Successors:** all
- **Files:** `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.github/workflows/android-apk.yml`, `.github/workflows/android-performance-baseline.yml` (JDK/gradle version lines only), `README.md` (CI artifacts section).
- **In scope:**
  0. Fix the broken "Set up Android SDK" step (`packages: 'platform-tools'`), see `briefs/TASK-B01.md` step 0. Every later task depends on this.
  1. Versions per TS§5.1 (Kotlin 2.4.21, KSP 2.3.12, AGP 8.13.2, Room 2.8.4). Add catalog entries (not yet used): `litertlm = "0.18.0"` → `com.google.ai.edge.litertlm:litertlm-android`; `work = "2.11.2"` → `androidx.work:work-runtime-ktx`; `lifecycle-process` → `androidx.lifecycle:lifecycle-process` (version.ref `lifecycleRuntimeKtx`).
  2. Add the dependency `implementation(libs.litertlm.android)` now (proves linkage).
  3. JDK 21 + `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }`.
  4. `staging` build type per TS§5.2; `release` minified. Verify `isReleaseBuildRequested` does not fire for staging tasks.
  5. R8 rules per TS§5.3.
  6. CI: JDK 21, Gradle 8.14.3 (or minimum required), run `testDebugUnitTest assembleDebug assembleStaging`, upload `robia-debug-apk` + `robia-staging-apk`, print fingerprints of both. Keep the TFLite drift steps for now (removed in B02).
  7. Room schema files are not committed today (`app/schemas` is absent on main). Add a CI step uploading `app/schemas` as artifact `room-schemas`; after the first green run, `gh run download <run-id> -n room-schemas -D app/schemas` and commit `app/schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/12.json` in this PR (needed by D01's migration test).
- **Test repair (PLAN-CHANGE 1):** may adapt/@Ignore the 8 pre-existing failing unit tests under `app/src/test/` per `decision-log.md` PLAN-CHANGE 1; no app code, no new libraries.
- **Out of scope:** any app code change; removing TFLite (B02).
- **Fallback (pre-approved):** if AGP 8.13.2 fails with Kotlin 2.4.21, use latest stable AGP 9.x + its minimum Gradle; document in receipt. If Compose BOM 2024.12.01 fails to compile with Kotlin 2.4 Compose plugin, bump to `2026.09.00` only.
- **Acceptance:** V-B01, V-B02. **Done when:** PR CI green with both APK artifacts; `12.json` committed; `aapt2 dump badging` output of both pasted in the receipt.

## TASK-B02 — Remove the old classifier
- **Reqs:** REQ-B04 · TS§3.13
- **Predecessors:** B01 · **Successors:** M01, E05
- **Files:** everything listed in TS§3.13, plus `ui/AddEditClothingScreen.kt`, `ui/BatchAddClothingScreen.kt`, `ui/RobiaApp.kt`, `ui/ManageTagsScreen.kt`, `media/EditorPhotoState.kt`, `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`, `app/src/test/java/com/gusanitolabs/robia/media/ImagePipelineContractTest.kt` (only if it references removed symbols), strings.
- **In scope:**
  1. Delete files/modules/steps of TS§3.13.
  2. `AddEditClothingScreen`: remove the detector, `addDetectionDebugLines`, `formatDebugTopK`, `appendDebugScores`, the fallback re-detection, the `DetectingAdditionalInformation` stage. Keep `applyPredictedTags`, `reconcilePredictedAdditionalInfoTags` renamed to `reconcilePredictedTags`, generation/override logic and `MODEL_PREDICTED_CATEGORIES = setOf("category", "occasion")` (they are reused in E05). Keep the "Export input image" button but disable it (hide) until E10 — simplest: remove the button and `exportAdditionalInfoInputImage`; E05 re-adds.
  3. `BatchAddClothingScreen.processBatchDraft`: remove the detector parameter and call; keep `mergePredictedTags` (unused for now is fine; mark `@Suppress("unused")` not needed if still referenced — if unused, keep it with a `// used by TASK-E05` comment).
  4. `RobiaApp.kt`: remove `batchAdditionalInfoDetector` and the parameter pass.
  5. `ManageTagsScreen.kt`: remove `AdditionalInfoModelOutputTags` checks, `pendingModelTagRename`, `pendingDeleteTag` warning flow (delete directly as for custom tags), `AiDetectedTagWarningDialog`. `canEdit = true` for all rows.
  6. `EditorPhotoState`: rename to `suggestionSourceUri` / `canonicalSuggestionUri` and update usages.
  7. Strings: delete in three locales: `rename_ai_tag_title`, `rename_ai_tag_body`, `rename_anyway`, `delete_ai_tag_title`, `delete_ai_tag_body`, `delete_anyway`, `processing_stage_detecting_additional_information`, `developer_export_input_image`, `developer_export_input_image_saved`, `developer_export_input_image_error`, `developer_export_input_image_no_photo` (grep first; delete only if no remaining reference).
  8. Update `RegressionSourceContractTest`: delete assertions on removed symbols only.
- **Out of scope:** adding any new suggestion code.
- **Acceptance:** V-B03 (grep clean), V-B04 (CI green). Manual: none.

## TASK-B03 — Application container
- **Reqs:** enabler for REQ-E02, REQ-S06 · TS§3.3
- **Predecessors:** B01 · **Successors:** E01, S04
- **Files:** new `RobiaApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` (add `libs.androidx.lifecycle.process`).
- **In scope:** create `class RobiaApplication : Application` exposing lazily-initialised singletons: `database`, `settingsRepository` (move the `preferencesDataStore` delegate from `MainActivity.kt` to `RobiaApplication.kt`, same name `robia_settings`), `wardrobeRepository`, `tagRepository`, `syncSnapshotRepository`, `restoreSyncLogRepository`, `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Move the `WardrobeSyncOutboxProcessor` construction to the application using `appScope` instead of `lifecycleScope` and `GoogleDriveWardrobeRepository(applicationContext, Identity.getAuthorizationClient(this), …)`. `MainActivity` keeps the authorization launcher and gets instances from `application as RobiaApplication`. Register `android:name=".RobiaApplication"`.
- **Out of scope:** WorkManager, engine.
- **Risk:** the processor now outlives the activity — that is the intent; authorization launches remain in the activity.
- **Acceptance:** V-B05 (CI green; existing tests pass), smoke in Z01.

## TASK-D01 — Schema v13 and snapshot contract
- **Reqs:** REQ-E03, REQ-E07 (data), REQ-E09 (contract), REQ-S10 (metadata field) · DEC-006, DEC-007 · TS§3.5, §3.9, §4.7 (migration of statuses)
- **Predecessors:** B01 · **Successors:** S03, S04, E05, E06, E08
- **Files:** `core/model/ClothingModels.kt`, `core/model/DefaultTags.kt`, `core/model/SyncModels.kt`, `data/local/Entities.kt`, new `data/local/EmbeddingEntities.kt`, new `data/local/EmbeddingDao.kt`, `data/local/RobiaDatabase.kt`, `data/LocalRepositories.kt`, new `data/EmbeddingRepository.kt`, `sync/LocalWardrobeSyncSnapshotRepository.kt` (tag mappers + embeddings export/import), `sync/GoogleDriveWardrobeRepository.kt` (`DriveSnapshotJson` only), `sync/WardrobeSyncOutboxProcessor.kt` (`TagSyncRecord.toDomain` only), `ui/ManageTagsScreen.kt` (tag save path only, pass existing `description`/`suggestOccasions`; PLAN-CHANGE 2), new `suggestions/VectorMath.kt`, `app/schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/13.json` (Room writes it at compile time; because builds only run in CI, add a CI step `actions/upload-artifact` name `room-schemas` path `app/schemas`, then `gh run download <run> -n room-schemas` and commit `13.json` in a second commit), `app/build.gradle.kts` (testImplementation `org.xerial:sqlite-jdbc:3.46.1.3` and `org.json:json:20240303`), tests.
- **In scope:**
  1. `GarmentTag.description: String? = null`, `suggestOccasions: Boolean = true`; entity columns `description`, `suggest_occasions`; mappers in all places (grep `GarmentTagEntity(` and `GarmentTag(` constructors using positional args — convert to named args).
  2. `DefaultTags`: `suggestOccasions = false` for `category-accessories`, `category-jewelry`; `isCustomOrModifiedDefault(tag)` also compares `description` and `suggestOccasions`.
  3. `GarmentEmbeddingEntity` + `EmbeddingDao` exactly per TS§3.5; add to `@Database(entities)`; `version = 13`; `MIGRATION_12_13` SQL per TS§3.5 + status reset per TS§4.7.
  4. `VectorMath`: `FloatArray.toLeBytes()`, `ByteArray.toFloatArrayLe()`, `toBase64()/fromBase64()`, `dot`, `normalized()`, `zScores()`.
  5. `EmbeddingRepository`: `observeVectors(modelId, recipeId): Flow<Map<String, GarmentVectors>>`, `upsert(garmentId, kind, vector, inputHash, durationMs)` (sets `pendingUpload = true`), `delete(garmentId, kind)`, `deleteAll(garmentId)`, `pendingUploadKeys()`, `markUploaded(keys)`.
  6. Snapshot model fields per TS§3.9 (`EmbeddingSyncRecord(kind, modelId, recipeId, dim, inputHash, computedAtEpochMillis, b64)`), JSON encode/decode with defaults, export/import in `LocalWardrobeSyncSnapshotRepository` (import only matching model+recipe; constants: hard-code `MODEL_ID`/`RECIPE_ID` in a new `suggestions/EmbeddingRecipe.kt` containing **only** these two constants plus `DIMENSION`; E01 extends the file).
  7. `WardrobeSnapshotMetadata.writerDeviceId: String? = null` (JSON only; usage in S04).
- **Out of scope:** using vectors; UI.
- **Acceptance:** V-D01 (migration SQL test on sqlite-jdbc), V-D02 (JSON round-trip + base-commit snapshot fixture decodes), V-D03 (embedding upsert does not change garment row), V-D04 (defaults for the 2 categories).

---

## TASK-S01 — Drive HTTP layer: keys, WebP, index, streaming, error classes
- **Reqs:** REQ-S01, REQ-S02, REQ-S05 · DEC-014 · TS§4.1–4.3
- **Predecessors:** B01, D01 (file lease on `GoogleDriveWardrobeRepository.kt`) · **Successors:** S02, S03
- **Files:** new `sync/PhotoBlobKeys.kt`, new `sync/DriveHttpErrors.kt`, new `sync/DriveFileIndex.kt`, `sync/GoogleDriveWardrobeRepository.kt` (`DriveSnapshotApi`, `HttpDriveSnapshotApi`, `DriveApiResult`, `DriveFileMetadata` only), `media/ClothingImageStore.kt` (`encodeDriveWebp`, `writeRestoredImageFile`), tests.
- **In scope:** add API methods `listAll`, `fetchBlobToFile`, optional `fileId` parameters; `classify(code, body)`; `withRetry` (5 attempts, 1/2/4/8 s ±20 % jitter, injectable sleeper for tests); `DriveApiResult.Transient`, `DriveApiResult.StorageFull`; encoder `encodeDriveWebp` per TS§4.2 (API split 30); `writeRestoredImageFile` per TS§4.1. Keep old methods working (callers switch in S02/S03). Old `readCanonicalDriveImageBlob` and `writeRestoredImageBlob` stay until S03/S02 remove them.
- **Out of scope:** changing `hydratePhotoBlobs`/`uploadPhotoBlobs` logic.
- **Tests:** `DriveHttpErrorsTest` (table: 401, 403×{rateLimitExceeded,userRateLimitExceeded,backendError,storageQuotaExceeded,forbidden}, 404, 408, 429, 500, 503, 400 + `SocketTimeoutException`, `UnknownHostException`), `PhotoBlobKeysTest` (key format regex, legacy detection, 10 restore cycles keep name length constant), `RetryPolicyTest` (attempt count and delays with fake sleeper). WebP encoder is Android-only → covered by V-Z (device) and a source-contract test asserting `WEBP_LOSSY` and `quality = 90` and max edge `2048` constants exist.
- **Acceptance:** V-S01, V-S02, V-S05.

## TASK-S02 — Restore: concurrency, per-photo isolation, no re-upload, log batching
- **Reqs:** REQ-S04, REQ-S08 (restore side), REQ-S11 · TS§4.4, §4.8
- **Predecessors:** S01 · **Successors:** S03 (lease), S04
- **Files:** `sync/GoogleDriveWardrobeRepository.kt` (`hydratePhotoBlobs`, `fetchPhotoBlob`, `retryRestoredPhoto`, `withDrivePhaseDeadline` removal), `sync/WardrobeSyncOutboxProcessor.kt` (`syncRestoreRemoteThenUpload` final-upload condition only), `sync/RestoreSyncLogRepository.kt`, `media/ClothingImageStore.kt` (remove `writeRestoredImageBlob`), tests (new fake `FakeDriveSnapshotApi` in `app/src/test/.../sync/fakes/`).
- **In scope:** TS§4.4 fully; final upload after restore only if merged ≠ remote (helper `WardrobeSyncSnapshot.equivalentForUpload(other)` comparing garments, garmentTags, garmentColors, taxonomies, tombstones and photos' `(garmentId, sourceHash ?: contentHash)`); log batching TS§4.8 with injectable clock/flush trigger.
- **Out of scope:** upload path, WorkManager, UI.
- **Tests:** V-S04 (34 photos, #5 fails → 33 restored + 1 guarded; peak concurrency 4; listing once), V-S11a (restore with identical merged snapshot → 0 uploads), V-S11b (500 log appends → ≤ 15 writes).
- **Acceptance:** V-S04, V-S11a, V-S11b.

## TASK-S03 — Upload: change detection, WebP, purge, guarded isolation
- **Reqs:** REQ-S02, REQ-S03, REQ-S08 · TS§4.2, §4.5 (photo part)
- **Predecessors:** S02, D01 · **Successors:** S04
- **Files:** `sync/GoogleDriveWardrobeRepository.kt` (`uploadPhotoBlobs`, `purgeDeletedPhotoBlobs`, `upsertSnapshot`), `sync/DriveWardrobeRepository.kt` (interface + 2 implementers: `upsertSnapshot` `remote` parameter and `photoUploadFailures` result field only; PLAN-CHANGE 4), `core/model/SyncModels.kt` (`GarmentPhotoRecord.sourceHash: String? = null`, serialized), JSON encode/decode for `sourceHash`, new `sync/LastUploadedPhotosStore.kt`, `sync/LocalWardrobeSyncSnapshotRepository.kt` (export sets blobPath placeholder; guarded garments keep remote record), `media/ClothingImageStore.kt` (remove `readCanonicalDriveImageBlob`), tests.
- **In scope:** per-photo decision algorithm TS§4.2 steps 1–3; legacy key re-key; purge order upload→snapshot→purge; guarded garments: copy remote photo record (input: remote snapshot passed into `upsertSnapshot(local, remote)` — change the repository method signature to `upsertSnapshot(snapshot, remote: WardrobeSyncSnapshot?)`); per-photo permanent failure → returns `PhotoUploadFailure(garmentId)` list in the result instead of failing the whole upload.
- **Out of scope:** processor scheduling/status mapping (S04).
- **Tests:** V-S03a (unchanged 30 garments → 0 uploads), V-S03b (1 changed → 1 upload + old blob purged after snapshot), V-S03c (snapshot upload fails → no purge), V-S08 (guarded garment record copied, blob not deleted), V-S03d (legacy long key re-keyed to short key).
- **Acceptance:** rows above.

## TASK-S04 — Processor: WorkManager, transient = pending, conflict, merge on connect
- **Reqs:** REQ-S06, REQ-S07, REQ-S08, REQ-S10 · DEC-014 · TS§4.5, §4.6
- **Predecessors:** S03, B03, D01 · **Successors:** S05, E06
- **Files:** `sync/WardrobeSyncOutboxProcessor.kt`, new `sync/DriveSyncWorker.kt`, new `sync/DriveSyncScheduler.kt`, `sync/WardrobeSyncGateway.kt` (state additions: `attention: List<SyncAttention>`, `pendingMergeSummary`, `conflict`), `sync/DriveWardrobeRepository.kt` (`DriveManifest.writerDeviceId` only; PLAN-CHANGE 5), `sync/LocalWardrobeSyncSnapshotRepository.kt` + `data/local/EmbeddingDao.kt` (`replaceLocalWithRemote`; PLAN-CHANGE 5), new `sync/SyncAttention.kt`, `data/local/Dao.kt` (new release/needs-action queries; stop using `markGarmentSyncFailedRetryable` for transient), `data/WardrobeRepository.kt` + `data/LocalRepositories.kt` (new methods), `data/SettingsRepository.kt` (`deviceId`, `lastKnownRemoteRevision`, `syncConflict`), `RobiaApplication.kt` (scheduler wiring), `MainActivity.kt` (connect flow uses merge summary), `ui/RobiaApp.kt` (**only** `CloudSetupGuard`/`LateEnableBlocked` removal and calls to `DriveSyncScheduler.requestSync`), `app/build.gradle.kts` (`libs.androidx.work.runtime.ktx`), tests.
- **In scope:** TS§4.5 and §4.6 completely; derive `SyncAttention` list in the processor state (UI rendering is S05).
- **Out of scope:** new UI components (S05 renders attention). Temporary: until S05, attention shows via existing label strings.
- **Tests:** V-S06 (scheduler enqueues unique work on save — fake `WorkScheduler` interface wrapping WorkManager), V-S07 (10 transient failures → Queued), V-S08b (guarded + edited → upload happens), V-S10a/b/c (empty restore; merge summary numbers; newer writer blocks upload).
- **Acceptance:** rows above + device row V-Z03 in Z01.

## TASK-S05 — Sync attention UX
- **PLAN-CHANGE 9:** S05 may also edit `sync/WardrobeSyncGateway.kt`, `sync/WardrobeSyncOutboxProcessor.kt` (dispatch + `affectedGarmentIds()`) and `data/local/Dao.kt` (+ repository wrappers) for Retry and Keep-without-photo; the PhotoUploadFailed body copy is fixed in decision-log PLAN-CHANGE 9.
- **PLAN-CHANGE 8:** S05 also owns the explicit Retry for `drive_full` / `photo_upload_failed` (new `WardrobeSyncOperation`, DAO query releasing NeedsUserAction rows to Queued, called by the Retry button only, plus a test that automatic cycles do not requeue them). It may edit `data/local/Dao.kt`, `data/WardrobeRepository.kt` and `LocalRepositories.kt` for this only.
- **Reqs:** REQ-S09 · TS§4.7 · mockup `mockups/sync-states.html`
- **Predecessors:** S04, M01 (lease order on `RobiaApp.kt`) · **Successors:** Z01
- **Files:** `ui/RobiaApp.kt` (status label/icon/tint functions, `cloudSetupSummaryRes`, `itemStatusLabelRes`, Browse banner slot, item-detail card, dialogs), new `ui/SyncAttentionCard.kt`, strings.
- **In scope:** render states and wire every button (TS§4.7); remove `garment_sync_failed_retryable`; "Details" only in dev mode.
- **Strings (EN / ES / DE):**
  - `sync_status_pending` Pending upload / Pendiente de subir / Wartet auf Upload
  - `sync_status_uploading` Uploading… / Subiendo… / Wird hochgeladen…
  - `sync_attention_sign_in_title` Sign in to Google Drive again / Vuelve a iniciar sesión en Google Drive / Erneut bei Google Drive anmelden
  - `sync_attention_sign_in_body` Your changes are safe on this phone. Robia needs permission again to back them up. / Tus cambios están a salvo en este móvil. Robia necesita permiso de nuevo para guardarlos. / Deine Änderungen sind auf diesem Handy sicher. Robia braucht erneut die Berechtigung, um sie zu sichern.
  - `sync_attention_sign_in_action` Sign in / Iniciar sesión / Anmelden
  - `sync_attention_drive_full_title` Google Drive is full / Google Drive está lleno / Google Drive ist voll
  - `sync_attention_drive_full_body` Free up space in your Google account, then retry. / Libera espacio en tu cuenta de Google y vuelve a intentarlo. / Gib Speicher in deinem Google-Konto frei und versuche es erneut.
  - `sync_attention_open_storage` Manage storage / Gestionar espacio / Speicher verwalten
  - `sync_action_retry` Retry / Reintentar / Erneut versuchen
  - `sync_attention_photo_lost_title` Photo could not be recovered / No se pudo recuperar la foto / Foto konnte nicht wiederhergestellt werden
  - `sync_attention_photo_lost_body` The garment is saved; only its photo is missing. / La prenda está guardada; solo falta su foto. / Das Kleidungsstück ist gespeichert, nur das Foto fehlt.
  - `sync_attention_keep_without_photo` Keep without photo / Mantener sin foto / Ohne Foto behalten
  - `sync_attention_retry_photo` Retry photo / Reintentar foto / Foto erneut versuchen
  - `sync_attention_photo_upload_title` A photo could not be uploaded / No se pudo subir una foto / Ein Foto konnte nicht hochgeladen werden
  - `sync_attention_conflict_title` Another phone updated your backup / Otro móvil actualizó tu copia / Ein anderes Handy hat deine Sicherung aktualisiert
  - `sync_attention_conflict_body` To avoid losing changes, Robia paused the backup. Choose which version to keep. / Para no perder cambios, Robia ha pausado la copia. Elige qué versión conservar. / Um keine Änderungen zu verlieren, hat Robia die Sicherung pausiert. Wähle, welche Version du behalten möchtest.
  - `sync_conflict_use_backup` Use backup (replace this phone) / Usar la copia (reemplaza este móvil) / Sicherung verwenden (ersetzt dieses Handy)
  - `sync_conflict_keep_phone` Keep this phone (overwrite backup) / Mantener este móvil (sobrescribe la copia) / Dieses Handy behalten (überschreibt die Sicherung)
  - `sync_merge_title` Combine with your Drive backup? / ¿Combinar con tu copia de Drive? / Mit deiner Drive-Sicherung zusammenführen?
  - `sync_merge_body` Drive: %1$d · This phone: %2$d → %3$d garments / Drive: %1$d · Este móvil: %2$d → %3$d prendas / Drive: %1$d · Dieses Handy: %2$d → %3$d Kleidungsstücke
  - `sync_merge_confirm` Combine / Combinar / Zusammenführen
  - `sync_details` Details / Detalles / Details
- **Acceptance:** V-S09 (state→action mapping unit test; every `SyncAttentionKind` has title, body, ≥ 1 action), visual check in Z01.

---

## TASK-S06 — Sync progress UX (requested by Manu, PLAN-CHANGE 6)
- **PLAN-CHANGE 10:** S06 may also edit `sync/DriveWardrobeRepository.kt` (optional `onPhotoProgress` parameter on `upsertSnapshot` only).
- **Reqs:** REQ-S09 (clarity of sync state) · investigation notes in `decision-log.md` PLAN-CHANGE 6
- **Predecessors:** S04, S05 · **Successors:** Z01
- **Files:** `ui/CloudRestoreProgressText.kt`, `ui/RobiaApp.kt` (`CloudRestoreProgressOverlay`, `CloudRestoreProgressBar` only), `sync/WardrobeSyncGateway.kt` (add an upload progress field to the restore progress state), `sync/WardrobeSyncOutboxProcessor.kt` (forward the upload progress only), `sync/GoogleDriveWardrobeRepository.kt` (`PhotoUploadPipeline`: progress callback with photos done and total, nothing else), strings (only if a new label is needed, EN/ES/DE, section `<!-- TASK-S06 -->`), tests.
- **In scope:**
  1. ETA text: estimate in the same composition (no one-frame null while bytes are unchanged), smoothed rate (cumulative since the first sample after 2 s and 64 KB, or EWMA alpha 0.2), shown value changes at most every 5 s, buckets (under 60 s "Less than a minute", under 10 min nearest minute, else nearest 5 min), never moves backwards, fixed reserved line height so nothing shifts.
  2. Bars: the photo download stage shows a determinate bar from bytes (items when any size is unknown or photos failed); the upload stage shows a determinate bar from photos done over total when photos are uploaded; the other stages (prepare, snapshot fetch, validate/merge, apply) show an indeterminate indicator with their step label. The bar animates smoothly between updates (animated value, no jumps).
  3. Every terminal outcome (success, failure, blocked, cancelled, timeout) clears or finalizes the overlay state; add a test that enumerates the outcomes.
- **Out of scope:** removing the whole-cycle timeout and WorkManager (S04), new sync behaviour, new strings beyond one stage label if truly needed.
- **Tests (JVM):** no null frame while bytes are unchanged; bucket boundary 59/60 s; displayed ETA never moves backwards; fraction falls back to items when bytes are unknown; upload progress forwarded from the pipeline; terminal outcomes clear the overlay; update `CloudRestoreProgressTextTest.hidesPreviouslyValidEstimateAfterAStalledSample` on purpose.
- **Acceptance:** V-S12 (the tests above, CI) and V-Z05 in Z01 (Manu, slow Wi-Fi: ETA line never disappears, text changes at most every 5 s, bar advances at each photo, no layout shift, bar does not look stuck after the download).

## TASK-M02 — Manage UI polish (Manu request, PLAN-CHANGE 11)
- **Predecessors:** S06 · **Successors:** E05
- **Files:** `ui/ManageTagsScreen.kt`, the shared Manage scaffold in `ui/RobiaApp.kt`, strings (remove the unused search label only), tests.
- **In scope:** (1) Colors tab in the same list format as Season/Occasions, no boxed card; (2) no blank space after the last item when scrolled to the bottom, in every tab; (3) remove the Search tags box, with no height jump between tabs.
- **Acceptance:** CI green; V-Z11 in Z01 (Manu: Colors looks like the other tabs, bottom scroll ends at the last item, no height jump).

## TASK-S07 — Cloud backup upload stage (Manu request, PLAN-CHANGE 11)
- **Predecessors:** S06 · **Successors:** Z01
- **Files:** investigate first; likely `sync/WardrobeSyncOutboxProcessor.kt`, `sync/GoogleDriveWardrobeRepository.kt`, `ui/CloudRestoreProgressText.kt`, `ui/RobiaApp.kt` (overlay only). Independent review required.
- **In scope:** find why "Updating cloud backup" takes about 3 minutes with a constant-rate bar and never finishes (app restart shows the data), make progress honest and finish cleanly; make the "N out of 12 steps" text truthful or replace it with a real count.
- **Acceptance:** V-S13 (tests reproducing the completion path in fakes) and V-Z12 in Z01 (Manu, slow Wi-Fi: the screen finishes by itself and the step text matches real work).

## TASK-E02 — Precision tool in repo + golden fixture
- **Reqs:** REQ-E04 (parity), REQ-E05/E06 (test data) · TS§3.1, §3.6
- **Predecessors:** G-APPROVAL (no code deps) · **Successors:** E04
- **Runs on:** the Raspberry Pi (Python), not CI. Python via `uv` (`pyproject.toml` + `uv.lock`), `UV_CACHE_DIR=/tmp/uv-cache`.
- **Files:** `app/src/main/assets/benchmark/garment_vector.b64`, new `tools/embedding-precision/` (`pyproject.toml`, `uv.lock`, `README.md`, `labels.py`, `embed.py`, `report.py`, `report_template.html`, `contact_sheets.py`, `download_drive.py`, `probe_search.py`, new `export_fixture.py`, new `make_benchmark_image.py`), `app/src/test/resources/suggestions/golden-scores.json`, `app/src/main/assets/benchmark/garment.png`, `.gitignore` (ignore `tools/embedding-precision/data/`).
- **Source:** copy scripts from `~/.local/share/robia-planning/precision-data/scripts/` (see `briefs/TASK-E02.md`). Remove any `cut` variant code. Data dir argument instead of hard-coded paths. **No photos, no Drive credentials, no personal names committed.**
- **`export_fixture.py`:** picks 6 stems (2 dresses, 1 shorts, 1 skirt, 1 accessory, 1 blouse — from provisional labels), writes JSON `{modelId, recipeId, labels:[{tagId, text, b64}], garments:[{id:"g1".."g6", category, image_b64}], expected:{text:{category:[{garment, ranked:[[tagId,score]...], margin, marked}], occasion:[…]}, combined:{…}}}`. In COMBINED mode garments g1..g6 are each other's examples (exclude self). Scores rounded to 6 decimals.
- **`make_benchmark_image.py`:** draws a synthetic 512×512 garment silhouette on transparent background (PNG, no personal data) and writes its reference IMAGE vector (same recipe: white composite, ≤1024, 140 tokens) as base64 float32 LE to `garment_vector.b64`.
- **Label text check:** run `probe_search.py` once with garment TEXT prefix `title: none | text: ` vs none for name "Red summer dress" (synthetic) and record which ranks the matching image higher; if `title: none` is not better, keep it anyway (doc format) and note it — no change to the recipe.
- **Acceptance:** V-E02 (fixture file valid JSON, 6 garments, 25 labels; `uv run python export_fixture.py --check` recomputes and matches).

## TASK-E04 — Scoring library (pure Kotlin)
- **Reqs:** REQ-E05, REQ-E06, REQ-E10 (planner), REQ-E11/E12 (ranker) · DEC-008..011 · TS§3.1, §3.6, §3.10, §3.11
- **Predecessors:** E02, D01 (`VectorMath`, `EmbeddingRecipe`) · **Successors:** E05, E08, E09
- **Files:** `suggestions/EmbeddingRecipe.kt` (complete constants + `DEFAULT_LABEL_DESCRIPTIONS` + `labelTextFor(tag)` + `garmentTextInput(name, notes)`), new `suggestions/CategoryScorer.kt`, `suggestions/OccasionSelector.kt`, `suggestions/ReviewPlanner.kt`, `suggestions/SearchRanker.kt`, tests under `app/src/test/java/com/gusanitolabs/robia/suggestions/`.
- **In scope:** functions and data classes exactly as TS§3.6; `ReviewPlanner.plan(changeSet, garments, vectors, labelVectors, tags): List<ReviewProposal>` per DEC-010; `SearchRanker.rankByQuery(...)`, `rankSimilar(...)`, `textFallback(...)` per TS§3.11 (accent-insensitive).
- **Out of scope:** Android, engine, UI.
- **Tests:** synthetic unit tests for every branch listed in REQ-E05/E06/E10/E11; `GoldenFixtureTest` reading `golden-scores.json`.
- **Acceptance:** V-E04a…e.

## TASK-E01 — Model store, engine holder, developer benchmark (device gate)
- **Reqs:** REQ-E01 (core, no UI prompt), REQ-E02, REQ-E04, REQ-E13 (benchmark part), NFR-01/03 measurement · DEC-003, DEC-005, DEC-013 · TS§3.2–3.4, §3.12 (benchmark)
- **Predecessors:** B01, B03, D01 · **Successors:** G-DEVICE-1, E05, E07
- **Files:** `suggestions/EmbeddingRecipe.kt` (model constants only if E04 not yet merged — coordinate: E01 adds model/runtime constants, E04 adds scoring constants; both append), new `suggestions/engine/EmbeddingEngineHolder.kt`, `suggestions/engine/LiteRtNativeEmbedder.kt`, `suggestions/engine/ImageInputEncoder.kt`, `suggestions/model/ModelStore.kt`, `suggestions/model/ModelDownloadWorker.kt`, `suggestions/DevBenchmark.kt`, `RobiaApplication.kt` (singletons, ProcessLifecycle observer, `onTrimMemory`), `data/SettingsRepository.kt` (2 keys), `ui/RobiaApp.kt` **SettingsScreen only**: minimal developer-only rows "Download model (dev)", "Model benchmark" with result text + copy button (final user-facing download UX is E07), `app/build.gradle.kts` (work dependency if S04 not merged yet), strings, tests.
- **In scope:** TS§3.2–3.4; benchmark per TS§3.12 measuring also `suggestion_ms` = encode + embedImage + label vectors cached + scoring with 19 synthetic labels (this is the NFR-01 quantity); results also appended to the dev sync log.
- **Tests:** `ModelStoreTest` (fake HTTP source: full download, resume 206, server 200 restart, hash mismatch → Failed(CorruptDownload) and files removed, metered network → WaitingForWifi), `EmbeddingEngineHolderTest` (fake native embedder, virtual time: idle release at 60 s, serialized calls, ON_STOP release).
- **Device gate G-DEVICE-1 (Manu):** install `robia-staging-apk` from this PR's CI run; Settings → Developer → Download model (Wi-Fi) → Model benchmark → copy result into chat. Pass: `suggestion_ms` median ≤ 2000, `peak_pss_mb` ≤ 1300, no crash. Parity: the benchmark also prints `parity_cosine` = cosine between the on-device embedding of `assets/benchmark/garment.png` and the Pi reference vector `assets/benchmark/garment_vector.b64` (written by E02 `make_benchmark_image.py` with the same recipe); pass ≥ 0.98.
- **Acceptance:** V-E01a (unit), V-E01b (device gate numbers recorded in the PR).

## TASK-E01b — Vision-token fallback (conditional)
- **Only if** G-DEVICE-1 fails on latency with 140 tokens. Change `VISION_TOKENS = 70` and regenerate `garment_vector.b64` and golden fixture with 70 tokens (E02 scripts `--vision-tokens 70`); rerun the gate. If it fails again → STOP the E lane, report to Manu.

## TASK-E05 — Capture integration (single + batch)
- **Reqs:** REQ-E05, REQ-E06, REQ-E03 (store at save), REQ-E13 (editor diagnostics) · TS§3.7
- **Predecessors:** G-DEVICE-1, E04, B02, D01 · **Successors:** E06, E10
- **Files:** new `suggestions/SuggestionService.kt`, `ui/AddEditClothingScreen.kt`, `ui/BatchAddClothingScreen.kt`, `ui/RobiaApp.kt` (pass service; `onSaveItem(s)` store capture vectors), strings (`processing_stage_preparing_suggestions` "Preparing suggestions" / "Preparando sugerencias" / "Vorschläge werden vorbereitet"; `developer_export_suggestion_input` "Export input image" / "Exportar imagen de entrada" / "Eingabebild exportieren" + saved/error variants), tests.
- **In scope:** TS§3.7; preload on screen entry; developer diagnostics lines; "Export input image" re-added using `ImageInputEncoder` bytes. If model not Ready: stage skipped silently, no tags touched.
- **Tests:** `SuggestionServiceTest` with fake embedder + in-memory vectors: unavailable model → no change; user override preserved (via `reconcilePredictedTags`); flag off → no occasion.
- **Acceptance:** V-E05a (unit), V-E05b (device: photo of a dress gets "Dresses" marked; accessories photo gets no occasion) in Z01.

## TASK-E06 — Backfill, indicator, embeddings in Drive
- **Reqs:** REQ-E08, REQ-E09 · TS§3.8, §3.9 (processor part)
- **Predecessors:** E05, S04 · **Successors:** E08, E09
- **Files:** new `suggestions/EmbeddingBackfill.kt`, `RobiaApplication.kt`, `sync/WardrobeSyncOutboxProcessor.kt` (pending embedding uploads count as work; `markUploaded` after success; backfill kick after restore), `ui/RobiaApp.kt` (Browse banner `SuggestionsProgressBanner`), new `ui/SuggestionsProgressBanner.kt`, strings (`suggestions_preparing_count` plurals: one "Preparing suggestions for %d garment…", other "Preparing suggestions for %d garments…"; ES "Preparando sugerencias para %d prenda…/prendas…"; DE "Vorschläge für %d Kleidungsstück werden vorbereitet…/Kleidungsstücke…"), tests.
- **Tests:** backfill selection & ordering; failure skip after 2; embedding-only change triggers snapshot upload without garment revision change (fake API).
- **Acceptance:** V-E06a/b/c.

## TASK-E07 — Model download UX
- **Reqs:** REQ-E01 (UI) · mockup `mockups/model-download.html`
- **Predecessors:** E01 · **Successors:** Z01
- **Files:** `suggestions/model/ModelStore.kt` (catch cancellation in `download()` -> publish `NotDownloaded`, keep `.part`; PLAN-CHANGE 3), new `ui/ModelDownloadUi.kt` (first-launch dialog, mobile-data confirm dialog, Settings section row with states), `ui/RobiaApp.kt` (show prompt once when `model_prompt_shown == false` and model not Ready; Settings section "Smart suggestions"), strings (copy from mockup).
- **Acceptance:** V-E07 (prompt shown once: unit test on the predicate), visual in Z01.

## TASK-E08 — Tag description, suggest-occasions switch, review flow
- **Reqs:** REQ-E07 (UI), REQ-E10 · DEC-010 · TS§3.10, §6 (editor) · mockups `mockups/manage.html` (editor), `mockups/tag-review.html`
- **Predecessors:** E06, M01 · **Successors:** Z01
- **Files:** `ui/ManageTagsScreen.kt` (TagEditorDialog fields), new `ui/TagReviewScreen.kt`, `ui/RobiaApp.kt` (route `TagReview`, change-set state, wiring of onSaveTag/onDeleteCustomTag/onRestoreDefaultTags), strings (copy from mockups; description placeholder EN "e.g. a light top with buttons down the front and a collar"; ES "p. ej. una prenda ligera con botones delante y cuello"; DE "z. B. ein leichtes Oberteil mit Knopfleiste und Kragen"; helper "One short sentence describing what it looks like. Helps suggestions." / "Una frase corta que describa cómo es. Mejora las sugerencias." / "Ein kurzer Satz, wie es aussieht. Verbessert Vorschläge."; switch "Suggest occasions for this category" / "Sugerir ocasiones para esta categoría" / "Anlässe für diese Kategorie vorschlagen"; help "Turn off for things that go with any occasion, like jewelry or accessories." / "Desactívalo para cosas que combinan con cualquier ocasión, como joyas o accesorios." / "Für Dinge ausschalten, die zu jedem Anlass passen, wie Schmuck oder Accessoires.").
- **Acceptance:** V-E08a (planner already tested in E04; here: change-set construction per trigger unit test), visual in Z01.

## TASK-E09 — Search and similar garments
- **Reqs:** REQ-E11, REQ-E12 · TS§3.11 · mockup `mockups/browse-search.html`
- **Predecessors:** E06 · **Successors:** Z01
- **Files:** `ui/RobiaApp.kt` (`FilterBar`, `RobiaShell` derivation, ItemDetail button, similar chip), strings.
- **Acceptance:** V-E09 (debounce + fallback wiring unit test on a small `SearchController` class in `suggestions/SearchController.kt`), device "vestido rojo" in Z01. "See similar garments" is approved and in scope (REQ-E12).

## TASK-E10 — Item-detail diagnostics (dev mode)
- **Reqs:** REQ-E13 · TS§3.12 · mockup `mockups/dev-diagnostics.html`
- **Predecessors:** E05 (and E06 for recompute) · **Successors:** Z01
- **Files:** `suggestions/SuggestionService.kt` (`diagnose`), `ui/RobiaApp.kt` (`ItemDetailScreen` card), new `ui/SuggestionDiagnosticsCard.kt`, strings.
- **Acceptance:** V-E10 (visibility predicate unit test).

## TASK-M01 — Manage redesign + scroll performance
- **Reqs:** REQ-M01, REQ-M02, NFR-02 · TS§6 · mockup `mockups/manage.html`
- **Predecessors:** B02 · **Successors:** E08, S05 (lease)
- **Files:** `ui/ManageTagsScreen.kt`, `ui/TagLocalization.kt` (context variant), `ui/RobiaApp.kt` (`toUiWardrobeItems` non-Composable + route gating), strings (copy from mockup).
- **In scope:** layout per mockup (without description/switch fields — E08 adds them), lazy rows TS§6, remembered labels.
- **Acceptance:** V-M01 (source-contract test: `item(key = "tag-row:` and `contentType = "tag-row"` exist; `toUiWardrobeItems` is not `@Composable`), V-M02 device gfxinfo in Z01.

## TASK-Z01 — Final device validation (Manu + worker)
- **Reqs:** all device rows · **Predecessors:** all tasks merged
- Worker prepares `docs/specs/robia-embeddings-sync-manage/artifacts/device-checklist.md` with steps and blanks; Manu runs on Poco F3 with the latest `main` staging APK and returns results; worker records them. Rows: V-Z01…V-Z10 in `verification-matrix.md`.
