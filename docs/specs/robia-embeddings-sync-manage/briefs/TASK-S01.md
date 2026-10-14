# TASK-S01 — Drive HTTP layer: keys, WebP, index, streaming, error classes

## Goal
Give the sync code the building blocks to fix lost photos and speed: short fixed photo names, WebP encoding, one Drive listing per sync, streamed downloads, and correct classification of HTTP errors (a 403 rate-limit must be retried, not treated as "sign in again"). This task adds the blocks; S02/S03 switch the callers.

## Root cause being fixed (for context)
`ClothingImageStore.writeRestoredImageBlob` names restored files `drive-<previous name>-<uuid>.<ext>`, and the Drive key is derived from the local file name, so every restore adds ~43 characters; after 4–5 restores the name exceeds 255 bytes and the photo can no longer be written.

## Read first
`sync/GoogleDriveWardrobeRepository.kt`: `interface DriveSnapshotApi`, `class HttpDriveSnapshotApi`, `sealed … DriveApiResult`, `DriveFileMetadata`, `toDriveResult`, `toDriveByteResult`, `findFileByName`. `sync/DriveWardrobeRepository.kt` (`DriveFolderNaming.photoBlobPath`). `media/ClothingImageStore.kt`. Spec TS§4.1–4.3.

## Steps
1. `sync/PhotoBlobKeys.kt` exactly as TS§4.1. `safe(id)` keeps `[A-Za-z0-9._-]`, replaces others with `_`, truncates to 64 chars. `hash16` = first 16 lowercase hex chars of SHA-256.
2. `sync/DriveHttpErrors.kt`: `DriveErrorClass` + `classify(code, body)` per TS§4.3. Parse the reason from Google's JSON error body: `error.errors[0].reason` (fallback `error.status`). Use `org.json`. Also `fun classify(t: Throwable): DriveErrorClass` → IOExceptions Transient.
3. `withRetry`: `internal suspend fun <T> withRetry(maxAttempts: Int = 5, sleeper: suspend (Long) -> Unit = { delay(it) }, random: () -> Double = Math::random, block: suspend () -> DriveApiResult<T>): DriveApiResult<T>` — retries only `Transient`; delays 1000·2^(n-1) ms × (0.8 + 0.4·random).
4. `DriveApiResult`: add `data class Transient(val cause: Throwable?) ` and `data object StorageFull`. Map in `toDriveResult`/`toDriveByteResult` via `classify`. Every `when` over `DriveApiResult` must stay exhaustive — for now map `Transient`/`StorageFull` to the existing `Failure` behaviour in callers (S02/S03/S04 refine it). Do not change behaviour beyond the 403 split.
5. `DriveFileMetadata` add `name: String = ""`. `DriveSnapshotApi.listAll(accessToken)` with paging (TS§4.3). `sync/DriveFileIndex.kt`: `class DriveFileIndex(files: List<DriveFileMetadata>)` with `idOf(name)`, `namesWithPrefix(prefix)`, `put(meta)`, `remove(name)`.
6. Optional `fileId: String? = null` parameter on `fetchBlob`, `upsertBlob`, `deleteBlob`: if given, skip `findFileByName`.
7. `fetchBlobToFile(accessToken, fileId, target: File): DriveApiResult<Long>` streaming with a 64 KB buffer; delete `target` on failure.
8. `ClothingImageStore.encodeDriveWebp(context, uri): ImageBlob` (TS§4.2) and `writeRestoredImageFile(context, garmentId, hash16, source: File): Uri` (TS§4.1, `File.renameTo`, fallback copy+delete). Keep old functions.
9. Wrap each HTTP call of `HttpDriveSnapshotApi` with `withRetry`.

## Tests (new, JVM)
`sync/DriveHttpErrorsTest`, `sync/PhotoBlobKeysTest`, `sync/RetryPolicyTest` as listed in tasks.md S01; `sync/DriveFileIndexTest` (prefix, put/remove). Add to `RegressionSourceContractTest` (or a new `DriveWebpContractTest`) source assertions: `ClothingImageStore.kt` contains `WEBP_LOSSY`, `90`, `2048`.

## Done when
CI green; V-S01, V-S02 (CI part), V-S05.
