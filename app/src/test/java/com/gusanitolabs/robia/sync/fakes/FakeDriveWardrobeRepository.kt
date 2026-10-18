package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveBackupDeletionResult
import com.gusanitolabs.robia.sync.DriveManifest
import com.gusanitolabs.robia.sync.DriveRestoreProgressListener
import com.gusanitolabs.robia.sync.DriveSyncResult
import com.gusanitolabs.robia.sync.DriveUsage
import com.gusanitolabs.robia.sync.DriveWardrobeRepository

/** Remote Drive backup. Each call returns the configured result; uploads are recorded even when they fail. */
internal class FakeDriveWardrobeRepository(
    var manifest: DriveSyncResult<DriveManifest> = DriveSyncResult.Success(DriveManifest()),
    var snapshot: DriveSyncResult<WardrobeSyncSnapshot> = DriveSyncResult.Success(WardrobeSyncSnapshot()),
    var upsertResult: DriveSyncResult<DriveManifest>? = null,
) : DriveWardrobeRepository {
    override val target: DriveSyncTarget = DriveSyncTarget()
    val upserts = mutableListOf<WardrobeSyncSnapshot>()
    /** The `remote` argument of each upload, so tests can see which photo records the processor passed. */
    val upsertRemotes = mutableListOf<WardrobeSyncSnapshot?>()
    var recordsFetches = 0
    var manifestFetches = 0
    /** Full-snapshot fetches (the restore path), so a test can see that a restore really ran. */
    var snapshotFetches = 0
    /** When set, fetching the manifest throws, as an unexpected crash in the Drive client would. */
    var manifestThrows: Throwable? = null
    /** Runs as the full backup download starts: the window in which a local edit can land. */
    var onSnapshotFetch: (() -> Unit)? = null
    /** Every call that would write to Drive or to the phone's restored photos; a held account must leave all of them at zero. */
    var deleteBackupCalls = 0
    var retryRestoredPhotoCalls = 0

    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> {
        manifestFetches++
        manifestThrows?.let { throw it }
        return manifest
    }

    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> {
        snapshotFetches++
        onSnapshotFetch?.invoke()
        return snapshot
    }

    override suspend fun fetchSnapshotRecords(): DriveSyncResult<WardrobeSyncSnapshot> {
        recordsFetches++
        return snapshot
    }

    override suspend fun retryRestoredPhoto(garmentId: String, progress: DriveRestoreProgressListener?): DriveSyncResult<GarmentPhotoRecord> {
        retryRestoredPhotoCalls++
        return DriveSyncResult.Failure(IllegalStateException("not used by sync tests"))
    }

    override suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int) -> Unit)?,
    ): DriveSyncResult<DriveManifest> {
        upserts += snapshot
        upsertRemotes += remote
        return upsertResult ?: DriveSyncResult.Success(DriveManifest.fromSnapshot(snapshot))
    }

    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> {
        deleteBackupCalls++
        return DriveSyncResult.Success(DriveBackupDeletionResult())
    }

    override suspend fun refreshDriveUsage(): DriveSyncResult<DriveUsage> =
        DriveSyncResult.Failure(IllegalStateException("not used by sync tests"))
}
