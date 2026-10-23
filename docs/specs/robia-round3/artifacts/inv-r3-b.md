# Robia: B1–B4 investigation (read-only)

CERTAIN means seen in code. HYPOTHESIS means inferred or needs a device test. No build, no network, no edits.

## Premise check
- **B1:** matches. The card is unconditional.
- **B2 "hoy no guardamos la foto sin editar":** PREMISE DOES NOT MATCH (partly). The garment row stores only the processed PNG and nothing is synced. But the raw gallery/camera JPEG is written to disk and never deleted; it is just never referenced (B2b-5).
- **B3:** matches. No local export or backup exists.
- **B4:** matches for the editor. The only raw-source path is Redo, and it is session-only.

## B1: last-synced card
1. `ItemDetailScreen.kt:305` `item { GarmentCloudStatusRow(item) }` is unconditional. The row is at `:122-152`. Its supporting text "Last synced: %1$s" (`R.string.garment_sync_last_synced_at`, `values/strings.xml:152`) is at `:126-133`. Pending help is at `:134-135` and parked help at `:136-138`. The headline comes from `garmentCloudStatusLabel()` (`RobiaApp.kt:2949-2966`).
2. Developer flag: `RobiaSettings.developerModeEnabled`. DataStore keys are `developer_mode_unlocked` and `developer_mode_enabled` (`data/SettingsRepository.kt:280-281`). The effective value already requires unlock (`:232`). Unlock is 10 taps in 5 s on Settings (`RobiaApp.kt:247-248`, `handleSettingsClick` `:1400-1412`).
   - The existing dev-gating pattern is `RobiaNavHost` passing `showSyncDetails = developerModeEnabled` (`RobiaApp.kt:2860`) and `showDiagnostics = showSuggestionDiagnostics(...)` (`:2864`, `SuggestionDiagnosticsCard.kt:36`). The diagnostics card is gated at `ItemDetailScreen.kt:368`.
3. Touch: `ItemDetailScreen.kt:305` only, e.g. `item { if (showSyncDetails) GarmentCloudStatusRow(item) }`. No RobiaApp change is needed, since `showSyncDetails` is already the dev flag.
4. Risks:
   - The attention cards (`:306-356`: DriveFull and PhotoUploadFailed retry, PhotoUnrecoverable with its retry button) are not dev-only and must stay visible.
   - Non-dev users lose the "saved on device / pending" headline.
   - Less literal alternative: keep the headline and gate only the supporting text (`:126-133`). That is the planner's call.
   - No coupling with B2–B4.

## B2: export screen and photo storage

### (a) Export today
- Entry: `DetailMediaCard` share button and dropdown at `ItemDetailScreen.kt:447-483`. Items `share_image` (`:468-474`) and `share_pdf` (`:475-481`). Handlers at `:246-289`. Chooser at `launchShareChooser` (`:644-666`).
- Image: `GarmentShareExporter.createShareImage` (`media/GarmentShareExporter.kt:49-63`) re-encodes the stored PNG at q100. It renders no fields and has no size cap. The output goes to `cacheDir/robia_shares` through FileProvider (`:254-258`, `res/xml/file_paths.xml`).
- PDF: `createSharePdf` (`:65-109`) calls `computePdfLayout` (`media/PdfLayout.kt:108-225`). The page is A4 at 595×842 (`GarmentShareExporter.kt:32-33`).
- Fields are hard-wired in `ItemDetailScreen.toGarmentShareItem()` (`:668-707`): category, season, occasion, fit, colours as swatches, notes. Location is omitted. There are at most 4 metadata rows (`PdfLayout.kt:91,113`), a photo height of 430–600 pt (`:79-80`), and a "Created with Robia" footer (`GarmentShareExporter.kt:234`).
- No field-selection or mode parameter exists anywhere.

### (b) Camera or gallery to stored PNG
1. **Pick.** Gallery: `AddEditClothingScreen.kt:1247-1265` calls `copyContentUriToPrivateStorage` (`media/ClothingImageStore.kt:36-46`). It is a byte-for-byte copy with no decode, no resize, and EXIF kept, written to `getExternalFilesDir(PICTURES)/robia_clothing_images/gallery-<uuid>.jpg` (dir at `:310-313`). Camera: `createCaptureUri` (`:27-34`) writes `camera-<uuid>.jpg`.
2. **Background removal** (`processSelectedPhoto` `AddEditClothingScreen.kt:312-457`, call at `:360`):
   - Precise engine when active (`PhotoBackgroundRemover.kt:27-31`). `decodeWorkBitmap` (`media/cutout/PreciseCutoutEngine.kt:382-~420`) caps the long edge at 1024 (`:22`, `workSize` `:371-376`). EXIF applies only the 90/180/270 rotations (`:405-410`), with no flips.
   - ML Kit (`MlKitSubjectSegmentationBackgroundRemovalEngine.kt:34-94`). The decode uses a power-of-two sample to get the long edge ≤2048 (`:27`, `:201-208`; a 4032 px photo becomes 2016). EXIF is applied including flips (`:210-232`). `composeWithAlpha` (`:260-267`). The source bitmap is recycled at `:92`. On failure the original URI is the output (`ORIGINAL_FALLBACK`).
   - Output: `writeProcessedBitmap` PNG q100 (`ClothingImageStore.kt:232-246`) as `subject-<uuid>.png`.
3. **Crop.** `cropTransparentPixels` (`ClothingImageStore.kt:215-230`, bounds `:484-516`): alpha > 24 (`:532`), padding max(8 px, 4% of the long edge) (`:511`, `:537-538`). It writes `cropped-subject-<uuid>.png`. The crop rectangle is not persisted.
4. **Save.** `photo_uri` = `canonicalPhotoUri` (`AddEditClothingScreen.kt:633`) into `ClothingItemEntity.photoUri` (`data/local/Entities.kt:19`). There is one photo column and no original column. The raw URI lives only in `EditorPhotoState.rawSourceUri` (`media/EditorPhotoState.kt:12`).
5. **Never deleted.** The gallery/camera raw files and the `subject-*.png` intermediate have no delete call (deletes exist only in `PhotoEditorScreen.kt:928-934` and `:958`). So each pick leaves about three files, two of them unreferenced (CERTAIN). Raw size is full-res, roughly 3–6 MB for a 12 MP JPEG (HYPOTHESIS).
6. **Fallback stores the unedited photo.** Single add: `AddEditClothingScreen.kt:463` (`photoUri = uriString`). Batch: `BatchAddClothingScreen.kt:91-92` and `617/629`, where the draft photo defaults to the original.
7. **Cloud copy comes from the stored PNG, not the raw.** `encodeDriveWebp` (`ClothingImageStore.kt:62-97`) caps the long edge at 2048 (`:530`), encodes lossy WebP q90 (`:529`, `compressDriveWebp` `:370-374`), and reads `localSourceUri()` (`GoogleDriveWardrobeRepository.kt:601`).

### (c) Redo cutout source
- `redoSourceUri` returns null when raw equals saved (`ui/PhotoEditorScreen.kt:164-165`). Edit mode sets `editorPhotoState.rawSourceUri = existingItem.photoUri` (`AddEditClothingScreen.kt:595`). So **Redo is hidden for a saved garment** unless a new photo was picked in this session (`:672-676`, button at `PhotoEditorScreen.kt:587`).
- When it is shown, Redo runs the engine on the session raw (`:351`, `:942-963`). The engine output goes to a cache temp, is decoded, and both files are deleted. No persistent copy of the raw is made.

### (d) Drive sync of photos
- **Keys:** `photos/<garmentId>/<hash16 of WebP bytes>.webp` (`sync/PhotoBlobKeys.kt:13,21`). `isLegacyKey` is "does not match that regex" (`:25`). Legacy keys are never reused (`GoogleDriveWardrobeRepository.kt:373-390`).
- **Change detection:** `sourceHash` is the first 16 hex of SHA-256 of the local bytes (`GDWR:615-626`). It is stored in the snapshot photo record and in `filesDir/sync/last_uploaded_photos.json` (`LastUploadedPhotosStore.kt:9-16, 43-49`). Reuse also requires the listed blob size to equal `byteSize` (`GDWR:373-390`).
- **Snapshot:** one file, `wardrobe_snapshot.json` (`GDWR:1127`). The encoder is `DriveSnapshotJson.encode` (`GDWR:1615-1641`): `metadata{schemaVersion, appPackage, generatedAtEpochMillis, wardrobeId, revision, writerDeviceId}`, `taxonomies`, `garments`, `garmentTags`, `garmentColors`, `photos[garmentId, localUri, blobPath, mimeType, contentHash, byteSize, revision, updatedAtEpochMillis, sourceHash?]` (`:1741-1750`), `tombstones`. "Manifest" is `DriveManifest`, derived from snapshot metadata and never a separate file (`DriveWardrobeRepository.kt:112-~140`).
- **schemaVersion** = 2 (`core/model/SyncModels.kt:221`).
- **Restore:** `restorePhotoBlobs` (`GDWR:1154`) and `restoreOnePhoto` (`:1220-1354`). Hash and size are checked against the record (`:1284-1297`), then a decode check (`:1307`). `writeRestoredImageFile` writes `restored-<id>-<hash16>.webp` (`ClothingImageStore.kt:103-123`, `PhotoBlobKeys.kt:23`). After restore the local photo is the Drive WebP.
- **Integrity:** `checkPhotos` (`GDWR:177`) calls the pure `checkPhotoIntegrity` (`sync/PhotoIntegrityCheck.kt:52-75`). Orphan blobs are `photos/` names not referenced (`:72-73`).
- **Purge:** `purgeReplacedPhotoBlobs` (`GDWR:568-596`) deletes photo-prefix blobs of swept garments that are not in `snapshot.photos.blobPath` (`:574`), skipping protected garments (`:574-586`).
- **Quota:** `driveUsageOf` (`sync/DriveUsage.kt:18-29`) counts `photos/` as photos and everything else as backup.

**Adding an "original" blob per photo:**
- **Schema bump is harmful.** Old apps treat a newer schema as `NeedsUser` (`WardrobeSyncOutboxProcessor.kt:1084`) and fail restore (`:1287-1290`). `MergeDecision.kt:68-69` stops treating them as the same wardrobe. Alternative: stay at 2 and add optional JSON fields. `jsonToPhoto` already reads optional keys (`GDWR:1840-1854`). But an old app re-uploads its snapshot without the field, which drops the reference. `SnapshotJsonCompatTest.kt:109` pins a schema-2 fixture. This is the planner's call.
- **Do not put originals under `photos/`.** `DRIVE_KEY_PATTERN` matches only `.webp` (`PhotoBlobKeys.kt:13`), so a JPEG would count as legacy. Purge would delete it (`GDWR:574-586`) unless it is added to `activeBlobPaths`. `PhotoIntegrityCheck` would count it as orphan, and `driveUsageOf` would count it as a photo. Use a separate prefix such as `originals/<id>/`. Old-app purge does not sweep that prefix. `driveUsageOf` would then count it as backup, which needs fixing too.
- **Upload:** a second `upsertBlob` in `uploadChanged` (`GDWR:516-566`) with the same verification.
- **Restore:** a second fetch in `restoreOnePhoto`. A missing original must not set `photoRestoreGuarded`.
- **Quota (HYPOTHESIS):** an original of 3–6 MB against a WebP of about 0.3–0.8 MB is roughly 5–10× the Drive usage.
- **"Reprocess the raw gives the same result" does not hold by default.** Engines read live settings (`CutoutEngineRegistry.options()`). The two engines differ (2048 vs 1024 work size, flip handling). The crop rectangle is not stored. Determinism needs a persisted options snapshot and crop rectangle.

## B3: zip export
- `DriveSettingsScreen.kt`: the nav card is at `:199-~247` (setup `:208`, check photos `:215-222`, delete `:232-236`). The usage card is at `:134-195`. A zip row would go here, unconditional. Callbacks are wired in the `RobiaNavHost` DriveSettings branch (`RobiaApp.kt:2877-2891`) and in `RobiaShell`.
- **Existing local export:** none. There is no SAF (`CreateDocument`, `OpenDocumentTree`) and no `ZipOutputStream` (grep). The only file writes are MediaStore PNG dumps to `Pictures/Robia` (`AddEditClothingScreen.kt:1754-1780`, `SuggestionDiagnosticsCard.kt:97-121`).
- **Policy conflict:** `AndroidManifest.xml` sets `android:allowBackup="false"` with a comment that backup is Google Drive-only. A user-initiated export should be reconciled with that comment.
- **Reusable:** `LocalWardrobeSyncSnapshotRepository.exportSnapshot` (`:58`) and `DriveSnapshotJson.encode` give a `data.json` almost for free. Embeddings are already base64 (`EmbeddingSyncRecord.b64`). Photo entries need `localUri` mapped to zip paths.
- **Data and size:**
  - Room `robia.db` has 7 tables: `clothing_items`, `clothing_item_tags`, `tag_categories`, `garment_tags`, `main_colors`, `sync_tombstones`, `garment_embeddings`. These are KB-scale. A 768-dim float32 vector is 3 KB (`suggestions/EmbeddingRecipe.kt:10`).
  - Photos in `Pictures/robia_clothing_images`: the cropped PNG (keep), plus the orphan raw and subject PNG (B2b-5), plus `restored-*.webp`.
  - Exclude as regenerable: thumbnails in `filesDir/robia_thumbnails` and the Coil cache in `cacheDir` (64 MB cap, `ImagePipeline.kt:485`).
  - Exclude as re-downloadable: models in `filesDir/models` (embedder about 388 MB, `EmbeddingRecipe.kt:14`) and the cutout pack.
  - Order of magnitude (HYPOTHESIS): 300 garments ≈ 0.5 GB of cropped PNGs, or 1.5–2.5 GB if raw originals are included. Stream through `ZipOutputStream` into `openOutputStream` of a SAF URI. Do not buffer in memory.

## B4 / D1: Keep mode
- The editor loads only the stored PNG (`PhotoEditorScreen.kt:237-244`, with the comment "the original photo is not kept with the garment"). Default mode is Keep (`:196`).
- `keepAlpha` (`media/cutout/RefineSession.kt:15-17`): if stored alpha < `UNRESTORABLE_ALPHA` = 0.05 (`:13`), nothing changes; otherwise `max(current, mask)`. **So Keep cannot restore removed background** (stored alpha 0). It only raises pixels that were already partly transparent. Keep never reads an original.
- Colour under alpha 0: `composeWithAlpha` keeps the source RGB in the int array (`MlKit...:260-267`, `PhotoEditorScreen.kt:884-886`, `PreciseCutoutEngine.kt:76`). `RefineSession.kt:10-11` asserts that the stored PNG has RGB 0 under alpha 0. CERTAIN that the code relies on this. HYPOTHESIS that Android's premultiplied ARGB_8888 storage produces it. Verify with an instrumented test that decodes a stored PNG and checks alpha-0 RGB.
- Done path: `writeCutout` (`PhotoEditorScreen.kt:913-934`) takes RGB from the stored PNG, refines alpha, crops, and writes a new PNG.
- **D1 answer:** restoring removed background needs the original. The stored cutout is EXIF-normalised, downscaled (1024 or 2048), and cropped by an unpersisted rectangle. Mapping the original onto it needs the crop, work size, and engine persisted. That makes B4 depend on the B2 storage decision.

## Room
- `RobiaDatabase.kt:21`: version 13, `exportSchema = true`. Schemas are in `app/schemas/.../RobiaDatabase/{1..13}.json`.
- Explicit `object : Migration` per version (`MIGRATION_1_2` … `MIGRATION_12_13`, `:59-241`), registered through `addMigrations` (`:41-53`). No destructive fallback. The v12→13 SQL is in `MIGRATION_12_13_SQL` (`:256-284`) for `Migration12To13Test`.
- A new column means v14 with `ALTER TABLE … ADD COLUMN` and a generated `14.json`. The generated schema needs a build, which I did not run.

## RobiaApp.kt hot ranges (3384 lines)
| Lines | Content | Relevance |
|---|---|---|
| 247-248 | `DEVELOPER_UNLOCK_*` consts | none |
| 249-313 | `RobiaRoute` sealed interface | B2 new export route |
| 328-573 | `RobiaApp` root, dev callbacks at 416-420 | none |
| 591-745 | `CloudSyncIndicator`, `cloudSyncIndicator` (630-664) | none |
| 815-2002 | `RobiaShell`: `startBatchAdd` 1191-1200, `handleSettingsClick` 1400-1412, dev flags 1010, 1848, 1855, 1991 | B3 wiring |
| 2629-2946 | `RobiaNavHost`: ItemDetail 2852-2869, DriveSettings 2877-2891, Settings 2892-2908, DeveloperSyncLog 2909+ | B2 route, B3 |
| 2948-2969 | `garmentCloudStatusLabel`, `formatEpochMillis` | B1 only if restructured |

B1 needs no RobiaApp change. B2 touches `RobiaRoute` and the `RobiaNavHost` branches. B3 touches `DriveSettingsScreen.kt`, `RobiaNavHost` 2877-2891, and `RobiaShell` wiring.

## Unverified (HYPOTHESIS)
- Premultiplied storage zeroing alpha-0 RGB (B4).
- File sizes and the Drive usage multiplier (B2, B3).
- Reprocessing drift: not measured.
