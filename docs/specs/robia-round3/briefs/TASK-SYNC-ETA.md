# TASK-SYNC-ETA — ETA in "Updating cloud backup"

## Goal
F1 (REQ-FB-01 a): the download phase shows an ETA but the upload phase ("Updating cloud backup", e.g. during Combine) does not. Cause: the overlay only gets `RestoreUploadProgress(completed, total)`; `RestoreEtaState` is byte-based and keyed on bytes, so the upload has no ETA. Instant `Reuse` completions would skew the gaps.

## Read first
`R3-DEC-041`; planner investigation `sync.md` §F1; `ui/RobiaApp.kt` restore overlay (~2423-2446, label `cloud_restore_phase_uploading`); `ui/CloudRestoreProgressText.kt`; `ui/BatchEta.kt` (`BatchEtaEstimator`, `batchEtaFor`); `BatchAddClothingScreen.kt` ETA rendering (~345, `batch_eta_*` strings); `sync/WardrobeSyncGateway.kt` `RestoreUploadProgress` (~159); `sync/DriveWardrobeRepository.kt` and `GoogleDriveWardrobeRepository.kt` progress callback; processor `onPhotoProgress` (~1372); `UploadProgressForwardingTest`; `CloudRestoreProgressTextTest`.

## Files
`ui/CloudRestoreProgressText.kt`; `ui/RobiaApp.kt` (the restore overlay only); `sync/WardrobeSyncGateway.kt`; `sync/DriveWardrobeRepository.kt` (callback signature only); `sync/GoogleDriveWardrobeRepository.kt` (the photo-progress callback only); `sync/WardrobeSyncOutboxProcessor.kt` (the `onPhotoProgress` lambda only); `app/src/test/java/com/gusanitolabs/robia/sync/UploadProgressForwardingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveWardrobeRepository.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/CloudRestoreProgressTextTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `RestoreUploadProgress(completedPhotos, totalPhotos, reusedPhotos = 0)`; the callback type becomes `(completedPhotos, totalPhotos, reusedPhotos)` in the interface, the Google repository (count `PhotoUploadAction.Reuse` completions) and the processor lambda; update the fakes.
2. Pure function in `CloudRestoreProgressText.kt` that, given the successive progress values, feeds a `BatchEtaEstimator` (`onTerminal()` only for non-reuse completions) and returns `estimate(total - completed)`; `null` before the first real upload completes.
3. The overlay holds the estimator with `remember(correlationId)` and updates it in a `LaunchedEffect(completedPhotos)`; it shows the result under the Uploading label with the `batch_eta_seconds/minutes/almost_done` strings (new `cloud_restore_eta_*` x3 only if the wording must differ).

## Tests
V-SYNC-ETA-1, V-SYNC-ETA-2 (CI), V-SYNC-ETA-3 (REV).

## Done when
V-SYNC-ETA-1..3 pass, V-SYNC-ETA-D is ready for Manu; CI green.

## Do not
Change the download-phase ETA, upload behaviour or speed, or the non-overlay upload path.
