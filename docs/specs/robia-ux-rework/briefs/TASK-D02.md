# TASK-D02 — Photo integrity: exact upload/restore, account binding, "Check photos"

## Goal
Manu (rev 3, R20, answer "2. A"): no photo may be lost or become unrecoverable through Google Drive sync. Close the gaps found in `artifacts/inv-r3-drive-photo-audit.md` and verified by the planner (SYNC-DEC-001). Answer "3. A": still one photo per garment (no original-photo backup).

## Read first
`sync/GoogleDriveWardrobeRepository.kt`: `planPhotoUploads` (302-318), upload loop (380-402), `uploadChanged` (431-451), `upsertBlob` (700-723), restore of one photo (1100-1215), `guardedRestore` (1253); `sync/WardrobeSyncGateway.kt:64-94` (`authorizedAccountEmail` / `expectedAccountEmail` / `hasConflictingAccountBinding`: declared but never assigned in main); `MainActivity.kt:93-190` (authorization request and `persistDriveAuthorizationResult`); `ui/RobiaApp.kt:1054` (only reader of the binding); `sync/SyncAttention.kt` (`SyncAttentionKind`); tests `UploadChangeDetectionTest`, `GuardedPhotoRestoreTest`, `RestoredPhotoReuploadTest`.

## Steps
1. **Exact restore.** After the download and before `moveToFinal`, when the record has `contentHash` and/or `byteSize`, compare them with the downloaded file (`streamedSha256Hex()` and length). On mismatch: `guardedRestore(category = REMOTE_PHOTO_MISMATCH, …)` (new constant), keep any phone-held file, delete the temp file. Records without hash/size (legacy) keep today's behaviour.
2. **Exact upload.** Ask Drive for the stored size and checksum in the upload response: append `&fields=id,size,sha256Checksum` to both multipart URLs and parse them in `upsertBlob` (return `DriveApiResult<UploadedBlobInfo>`, with `size: Long?` and `sha256: String?`). In `uploadChanged`, if Drive returns a size different from `blob.byteSize`, or a checksum different from `blob.contentHash`, treat it as `DriveApiResult.Failure` (→ `keepPreviousRecord`, the old blob is never purged). If Drive returns no checksum, size alone decides.
3. **CopyRemote must point at an existing blob.** In the upload loop, for `CopyRemote`, keep the remote/previous record only if `index.idOf(record.blobPath) != null`; otherwise keep the guard and do not claim the photo in the uploaded snapshot (add the garment id to `protectedGarmentIds` as today, so nothing is purged), and report it once through the existing PhotoUnrecoverable attention path.
4. **Account binding.**
   - On successful authorization, read the account e-mail. The `AuthorizationResult` does not expose it, so request `about?fields=user(emailAddress)` once (allowed with `drive.appdata`) and store it as `driveBoundAccountEmail` in settings the first time.
   - On later authorizations, store the current e-mail as `driveAuthorizedAccountEmail`.
   - Fill `authorizedAccountEmail` / `expectedAccountEmail` of `WardrobeSyncState` from these two values, so the existing `hasConflictingAccountBinding` becomes real.
   - The processor must not upload, restore or purge while `hasConflictingAccountBinding` is true. Instead, add `SyncAttentionKind.AccountMismatch` with two actions:
     - "Use this account" rebinds and then runs the normal merge-on-connect flow, never a silent overwrite.
     - "Disconnect".
   - E-mails stay out of logs (existing redaction).
5. **Check photos (read-only).** Settings › Google Drive gets a "Check photos" row. Pure planner `sync/PhotoIntegrityCheck.kt` with `fun checkPhotoIntegrity(local: List<LocalPhotoRef>, files: List<DriveFileMetadata>, snapshot: WardrobeSyncSnapshot?): PhotoIntegrityReport`. The report holds, per garment: `MissingLocalFile`, `MissingOnDrive`, `SizeMismatch`, `NotYetUploaded`, `Ok`, plus a count of orphan blobs.
   - Inputs come from Room photo URIs plus `File.length()`, one `listAll`, and the remote snapshot.
   - Nothing is deleted or rewritten.
   - Result dialog: "All 143 photos are safe in Google Drive", or a list of problems. Garments with a missing local file but present on Drive get "Restore photo", which reuses the existing guarded-restore retry. Garments not yet uploaded get "Back up now", which enqueues a normal sync.
6. Strings: `check_photos_title` "Check photos", `check_photos_all_ok` "All %1$d photos are safe in Google Drive", `check_photos_problems` "%1$d photos need attention", `check_photos_missing_local` "Missing on this phone", `check_photos_missing_drive` "Missing in Google Drive", `check_photos_not_uploaded` "Not backed up yet", `check_photos_size_mismatch` "Damaged copy", `account_mismatch_title` "Different Google account", `account_mismatch_body` "Robia was backed up with another Google account. Nothing will be uploaded until you choose.", `account_mismatch_use_this` "Use this account", `drive_disconnect` (reuse if it exists).

## Tests
- `PhotoIntegrityRestoreTest` (V-D02-1): wrong hash, wrong size and truncated file are guarded; legacy record without hash is accepted; a phone-held file is never overwritten.
- `PhotoUploadVerifyTest` (V-D02-2): a size or checksum mismatch keeps the previous record and the old blob is not purged.
- `CopyRemoteIndexTest` (V-D02-3).
- `AccountBindingTest` (V-D02-4): no upload, restore or purge while mismatched; rebind leads to merge-on-connect.
- `PhotoIntegrityCheckTest` (V-D02-5): every category, and orphan counting.

## Done when
V-D02-1..6, CI green, independent review PASS; device rows V-Z2-26, V-Z2-27.

## Do not
Back up original photos, change blob key format (16-hex keys stay; collision risk is accepted and recorded in SYNC-DEC-001), delete anything from "Check photos", or change Android backup rules.
