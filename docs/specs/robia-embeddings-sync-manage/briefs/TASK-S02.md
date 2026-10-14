# TASK-S02 — Restore: concurrency, per-photo isolation, no re-upload, log batching

## Goal
Restoring 34 photos took 47 s and one bad photo aborted everything. Make restore download 4 photos at a time with one listing, isolate failures per photo, stop re-uploading everything after a restore, and stop rewriting the log file on every line.

## Read first
`GoogleDriveWardrobeRepository.kt`: `hydratePhotoBlobs`, `fetchPhotoBlob`, `retryRestoredPhoto`, `withDrivePhaseDeadline`, `DRIVE_PHASE_TIMEOUT_MILLIS`. `WardrobeSyncOutboxProcessor.kt`: `syncRestoreRemoteThenUpload`. `RestoreSyncLogRepository.kt` (`FileRestoreSyncLogRepository.append`). Spec TS§4.4, §4.8.

## Steps
1. Rewrite `hydratePhotoBlobs` per TS§4.4: `api.listAll` once → `DriveFileIndex`; resolve each photo by exact `blobPath`, legacy fallback = single candidate with prefix `photos/<garmentId>/`; `Semaphore(4)`; `fetchBlobToFile` into `context.cacheDir/restore/<garmentId>.part`; per photo `withTimeout(60_000)`; bounds-decode from file; `hash16` streamed; `ClothingImageStore.writeRestoredImageFile`.
2. Outcome per photo: restored / guarded (missing, unreadable, timeout, transient after retries → category `remote_photo_transient`). `Unauthorized`/`StorageFull` from any photo → cancel the others and return the existing auth/failure result.
3. Remove `withDrivePhaseDeadline` and `DRIVE_PHASE_TIMEOUT_MILLIS` (keep the 30 s authorization timeout).
4. Remove the full re-read (`readBackBytes`) and the second dimensions pass; keep exactly one bounds decode.
5. Delete `ClothingImageStore.writeRestoredImageBlob` (callers moved).
6. `syncRestoreRemoteThenUpload`: after import, upload only if `!merged.equivalentForUpload(remote)` (new extension in `core/model/SyncModels.kt` or a new `sync/SnapshotEquivalence.kt`; compare as TS§4.4/tasks.md S02).
7. `FileRestoreSyncLogRepository`: buffered writer per TS§4.8; inject `flushIntervalMs = 2000`, `flushLines = 50`, and a `scope`. Add `suspend fun flush()` for tests and call it at the end of restore.

## Tests (new)
Create `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveSnapshotApi.kt` implementing `DriveSnapshotApi` in memory (files map name→bytes, per-name failure injection, counters: listCalls, concurrent in-flight max). Then `HydrateConcurrencyTest` (V-S04), `RestoreNoReuploadTest` (V-S11a), `RestoreSyncLogBatchingTest` (V-S11b, count file writes via an injectable `FileWriter` interface or by counting `appendText` calls through a small wrapper). If `hydratePhotoBlobs` needs Android `Context`/`BitmapFactory`, extract the orchestration into an internal function that takes lambdas (`decodeBounds(file)`, `moveToFinal(...)`) and test that function.

## Done when
CI green; V-S04, V-S11a, V-S11b.
