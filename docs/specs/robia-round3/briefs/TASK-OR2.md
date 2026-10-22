# TASK-OR2 — Back up originals to Google Drive

## Goal
Manu chose to keep originals on the phone and in Drive (answer 1A). Old app versions must keep working: they treat a newer schemaVersion as "needs user" and fail restore, so the fields are optional and the version stays 2 (inv-r3-b.md §B2(d)).

## Read first
`artifacts/inv-r3-b.md` §B2(d) (keys, purge, integrity, quota, schema risks); `sync/GoogleDriveWardrobeRepository.kt` `uploadChanged` l.516-566, `purgeReplacedPhotoBlobs` l.568-596, `restoreOnePhoto` l.1220-1354, `DriveSnapshotJson` l.1615+ (`photoToJson` l.1741, `jsonToPhoto` l.1840); `sync/PhotoBlobKeys.kt`; `sync/DriveUsage.kt`; `sync/PhotoIntegrityCheck.kt`; `core/model/SyncModels.kt` `GarmentPhotoRecord` l.187.

## Files
`core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `sync/PhotoBlobKeys.kt`; `sync/PhotoIntegrityCheck.kt`; `sync/DriveUsage.kt`; `ui/DriveSettingsScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/DriveUsageTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/PhotoIntegrityCheckTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/sync/OriginalBlobTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `GarmentPhotoRecord`: `originalBlobPath: String? = null`, `originalHash: String? = null`, `originalByteSize: Long? = null`, `recipe: String? = null` (JSON text). Encode only when non-null; decode optional.
2. Export snapshot fills them from `ClothingItem.originalUri/originalRecipe` (hash16 = first 16 hex of SHA-256 of the file bytes).
3. Upload: after the cutout blob, `upsertBlob` the original under `originals/<garmentId>/<hash16>.jpg` with the same verification; reuse when the listing has the same name and size.
4. Restore: second pass after all cutouts; write `restored-original-<id>-<hash16>.jpg`; set `originalUri`/`originalRecipe` locally. Failure → log `original_restore_failed`, continue.
5. Purge: originals of garments with a tombstone or no snapshot record are deleted; `originals/` blobs whose garment has a record are kept even if the record has no original fields; replaced originals (hash changed) deleted only when the new one is verified.
6. `DriveUsage`: `originalCount`, `originalBytes`; Drive settings usage row adds "· %d originals" (`drive_usage_originals`).

## Tests
V-OR2-1..3.

## Done when
V-OR2-1, V-OR2-2, V-OR2-3, V-OR2-4, V-OR2-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Bump `WARDROBE_SYNC_SCHEMA_VERSION`, place originals under `photos/`, or block a restore on an original.
