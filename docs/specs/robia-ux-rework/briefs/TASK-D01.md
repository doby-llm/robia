# TASK-D01 — Show how much space Robia uses in Google Drive

## Goal
Manu (rev 3, R18): in the Sync/Drive settings, show the total size of what Robia stores in Google Drive (DATA-DEC-001). No new permission and no extra network request during normal sync.

## Read first
`sync/GoogleDriveWardrobeRepository.kt`: `listAll` (683-697, fields `files(id,name,modifiedTime,size)` in `appDataFolder`), its callers in `upload` (352) and restore (1042), `DriveFileMetadata` (562-568); `sync/WardrobeSyncOutboxProcessor.kt` (how a cycle result reaches `WardrobeSyncState`, `toWardrobeSyncState` ~1370-1395); `sync/WardrobeSyncGateway.kt` (`WardrobeSyncState`); U05's `ui/DriveSettingsScreen.kt` (after U05).

## Steps
1. Pure `sync/DriveUsage.kt`: `data class DriveUsage(val totalBytes: Long, val photoCount: Int, val photoBytes: Long, val backupCount: Int, val measuredAtEpochMillis: Long)` and `fun driveUsageOf(files: List<DriveFileMetadata>, now: Long): DriveUsage` (photos = name starts with `photos/`; backups = the names `listBackupFiles` uses; null size counts 0).
2. Persist the last value: small JSON file next to `LastUploadedPhotosStore` (`DriveUsageStore(file)`, same read/write style; corrupt → null).
3. After every successful `listAll` inside `upload` and restore, compute `driveUsageOf` and write it (no extra request). Add `suspend fun refreshDriveUsage(): DriveSyncResult<DriveUsage>` to the repository (one `listAll` via `withAccessToken`) and `refreshDriveUsage()` to `WardrobeSyncGateway` (+ fakes).
4. `WardrobeSyncState.driveUsage: DriveUsage? = null`, filled from the store in `toWardrobeSyncState`.
5. `DriveSettingsScreen`: a row "Space used in Google Drive" with value `drive_usage_value` "%1$s · %2$d photos" (size via `android.text.format.Formatter.formatShortFileSize`), secondary text `drive_usage_updated` "Updated %1$s" (relative time, the same helper the screen uses for last sync, if any; else `DateUtils.getRelativeTimeSpanString`), and a refresh icon button (content description `drive_usage_refresh` "Refresh") calling `refreshDriveUsage()`. Before the first value: `drive_usage_unknown` "Not measured yet". Hidden when Drive is not connected.

## Tests
`DriveUsageTest` (V-D01-1): sums, photo/backup split, null sizes, empty list. `DriveUsageStoreTest` (V-D01-2): round trip, corrupt file → null.

## Done when
V-D01-1..3, CI green; device row V-Z2-25.

## Do not
Request a new scope, call `about.get`, change upload/restore logic beyond writing the usage, or add a network call to every sync.
