# TASK-SYNC-SPEED — Faster photo upload, safely

## Goal
F1 (REQ-FB-01 b): the upload is about one photo per 3 s. Cause: a sequential loop; per garment a WebP encode, find-GET, POST cutout, find-GET, POST full original, each on a fresh connection; a failed cycle keeps nothing because `uploadChanged` never checks the index, so the retry re-uploads every blob.

## Read first
`R3-DEC-041`; planner investigation `sync.md` §F1; `sync/GoogleDriveWardrobeRepository.kt` upload loop (~478-560), `planPhotoUploads` (~395-410), `originalReusable` (~416), `attachOriginal` (~635), `findFileByName` (~995), HTTP layer and `disconnect()` (~1083, 1155, 1175), `restorePhotoBlobs` and `Semaphore(RESTORE_PHOTO_CONCURRENCY)` (~1262-1280); `sync/DriveFileIndex.kt`; `sync/DriveHttpErrors.kt` (Retry-After from SYNC-AUTH); `PhotoUploadVerifyTest`, `UploadSkipTest`, `FakeDriveSnapshotApi`.

## Files
`sync/GoogleDriveWardrobeRepository.kt`; `sync/DriveFileIndex.kt`; `sync/DriveHttpErrors.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveSnapshotApi.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/PhotoUploadVerifyTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/UploadSkipTest.kt` (plus imports, new tests).

## Steps (in this order; one commit each, stop adding when a step cannot be made safe)
1. Skip the POST when the index lists the same blob name and size (the `originalReusable` rule, applied to the cutout blob too).
2. Skip the find-GET when the index is known to be the full listing and lacks the name (add a "complete" flag or `containsName` helper to `DriveFileIndex`).
3. Keep connections alive: call `disconnect()` only on error paths, drain and close streams otherwise.
4. Bounded concurrency 2–3 (a constant) for Upload and attachOriginal using the `Semaphore` pattern of `restorePhotoBlobs`. Progress is reported from a single place, monotonic. Unauthorized/Transient on one worker cancels its siblings and is returned as today. A 429 or a `Retry-After` drops concurrency to 1 for the rest of the cycle. A `findFileByName` failure must never fall through to a POST (treat it as Transient for that blob); never two workers for the same name.
5. A failed cycle followed by a retry uploads no blob that the index already lists.

## Tests
V-SYNC-SPEED-1..3 (CI, fake API with request counters), V-SYNC-SPEED-4 (REV).

## Done when
V-SYNC-SPEED-1..4 pass, V-SYNC-SPEED-D is ready for Manu (record the 100-garment time; no hard target); CI green; independent review done.

## Do not
Add resumable uploads, change the snapshot format or restore speed, touch auth handling, or add a library.
