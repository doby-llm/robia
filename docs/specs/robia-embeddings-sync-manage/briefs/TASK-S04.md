# TASK-S04 — Processor: WorkManager, transient = pending, conflict, merge on connect

## Goal
"Sync failed" appeared after a day because retries only happened 3 times (1/5/15 min) while the app was open, then gave up forever, and a pending photo restore blocked all uploads. After this task: sync runs in the background with WorkManager (even with the app closed), network problems keep items as "pending" and retry forever, only real problems need the user, one phone cannot silently overwrite a newer backup from another phone, and connecting Drive on a phone that already has garments shows a merge summary.

## Read first
`sync/WardrobeSyncOutboxProcessor.kt` (whole file; note `hasGuardedPhotoRestoreIssues`, `scheduleWorkFor`, `processPendingGarments`, `syncUploadLocalSnapshot`, `scheduleRetryWakeUp`, `SYNC_OPERATION_TIMEOUT_MILLIS`, `releaseClaimedWork`, `mergeSnapshots`), `sync/WardrobeSyncGateway.kt`, `data/local/Dao.kt` (`markGarmentSyncFailedRetryable` + metadata equivalents, `markGarmentPhotoRestoreFailed`), `data/SettingsRepository.kt`, `MainActivity.persistDriveAuthorizationResult`, `ui/RobiaApp.kt` (`CloudSetupGuard`, `LateEnableBlocked`). Spec TS§4.5, §4.6, DEC-014.

## Steps
1. Settings: `deviceId` (UUID generated on first read and stored), `lastKnownRemoteRevision: Long = 0`, `syncConflict: Boolean = false`.
2. DAO: `releaseGarmentToQueued`, `releaseMetadataToQueued` (one per metadata table, same pattern as existing methods), `markGarmentNeedsUserAction(id, revision, reasonCode, now)`. Stop calling `markGarment…FailedRetryable` for transient errors (leave the DAO methods if other code uses them).
3. Processor:
   - public `suspend fun runOnce(trigger: SyncTrigger): SyncRunResult` (`enum SyncTrigger { Foreground, Background }`, `sealed SyncRunResult { Success, Retry, AuthBlocked, Conflict, NeedsUser }`), protected by the existing mutex.
   - remove the global guard early-returns (TS§4.5).
   - upload cycle order exactly as TS§4.5 (fetch remote → newer-writer check → index → photos → snapshot → purge → persist → mark synced).
   - error mapping as TS§4.5; remove `scheduleRetryWakeUp` and the whole-cycle `withTimeout(SYNC_OPERATION_TIMEOUT_MILLIS)`.
   - exported metadata: `writerDeviceId = settings.deviceId`; after success `lastKnownRemoteRevision = uploaded revision`.
   - `remote_photo_transient` guarded photos: retried automatically on each cycle without incrementing the attempt counter.
4. `sync/DriveSyncScheduler.kt`: behind an interface `SyncWorkScheduler { fun requestSync(); fun ensurePeriodic(); fun cancelAll() }` with a WorkManager implementation per TS§4.6 (names `robia-drive-sync`, `robia-drive-sync-periodic`).
5. `sync/DriveSyncWorker.kt` (`CoroutineWorker`) → `(applicationContext as RobiaApplication).syncGateway.runOnce(Background)`; `Retry` → `Result.retry()`, else `Result.success()`.
6. Call `requestSync()` wherever the processor is told work exists today (`enqueue…` methods) — single place: inside the processor's enqueue path, via the injected scheduler. On app start: if pending work exists → `requestSync()`. When Drive becomes connected → `ensurePeriodic()`; on disconnect/backup deletion → `cancelAll()`.
7. Background authorization: if the token request needs UI (`hasResolution()`), return `AuthBlocked` and set the SignInRequired attention.
8. Conflict: implement TS§4.5 "Newer-writer protection" with actions `useBackup()` and `keepThisPhone()` on the gateway.
9. Connect on a non-empty phone: compute `MergeSummary(remote, local, merged)` into state `pendingMergeSummary`; `confirmMerge()` / `cancelMerge()` on the gateway; empty phone → full restore as today. Remove `CloudSetupGuard`/`LateEnableBlocked` from `RobiaApp.kt` and show the merge dialog with existing dialog components (final styling in S05).
10. State: add `attention: List<SyncAttention>` (`sync/SyncAttention.kt` per TS§4.7) derived from DB + settings.

## Tests (new)
`DriveSyncSchedulerTest` (fake `SyncWorkScheduler`), `TransientIsPendingTest`, `ProcessorGuardTest`, `ConnectFlowTest`, `NewerWriterTest` using `FakeDriveSnapshotApi` + in-memory fakes of the repositories (create fakes under `sync/fakes/`). If the processor constructor is too Android-bound, inject the new collaborators via interfaces; do not add Robolectric.

## Done when
CI green; V-S06, V-S07, V-S08b, V-S10a/b/c.
