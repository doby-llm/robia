# TASK-S03 — Upload: change detection, WebP, purge, guarded isolation

## Goal
Today every sync re-encodes and re-uploads every photo, and one problematic photo blocks all uploads. Upload only photos whose local file changed, as WebP under the short key; delete old blobs only after the new snapshot is safely uploaded; never delete the Drive photo of a garment whose local photo is missing (guarded).

## Read first
`GoogleDriveWardrobeRepository.kt`: `uploadPhotoBlobs`, `purgeDeletedPhotoBlobs`, `upsertSnapshot`. `LocalWardrobeSyncSnapshotRepository.exportSnapshot`. `core/model/SyncModels.kt` `GarmentPhotoRecord`. Spec TS§4.2, §4.5 (photo part). **High-risk task: deletes Drive data. A second independent review is mandatory.**

## Steps
1. `GarmentPhotoRecord.sourceHash: String? = null` + JSON encode (if non-null) / decode (`optString`, blank → null).
2. `sync/LastUploadedPhotosStore.kt`: read/write `filesDir/sync/last_uploaded_photos.json` (list of garmentId, sourceHash, blobPath, contentHash, byteSize, mimeType); atomic write (temp file + `renameTo`).
3. Change the repository API to `upsertSnapshot(snapshot, remote: WardrobeSyncSnapshot?)`; update the single caller in the processor (pass the remote snapshot already fetched, or `null`).
4. Per photo (TS§4.2): stream SHA-256 of the local file → `sourceHash16`; reuse previous record if same `sourceHash` and the key is in the index; else `encodeDriveWebp` → `PhotoBlobKeys.driveKey(garmentId, hash16(webp))` → `upsertBlob` (with fileId if the key already exists). A legacy key (`PhotoBlobKeys.isLegacyKey`) is always re-uploaded under the new key.
5. Guarded garments (local photo missing/restore guarded): copy the photo record from `remote` unchanged; never list its blob for deletion.
6. Per-photo permanent failure: keep the previous remote record for that garment, add `garmentId` to `photoUploadFailures` in the result, continue.
7. Order: upload changed photos → upload snapshot JSON → only if that succeeded: purge (TS§4.2 "Purge after verified upload") → write `LastUploadedPhotosStore`.
8. Delete `ClothingImageStore.readCanonicalDriveImageBlob`.

## Tests (new, with `FakeDriveSnapshotApi` from S02)
`UploadChangeDetectionTest` (V-S03a/b/c/d), `GuardedIsolationTest` (V-S08). Make the per-photo decision a pure internal function `planPhotoUploads(local, previous, index): List<PhotoUploadAction>` and test it directly as well.

## Done when
CI green; rows V-S03a–d, V-S08; second reviewer confirms "no deletion before verified snapshot upload".
