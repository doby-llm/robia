package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveBackupDeletionResult
import com.gusanitolabs.robia.sync.DriveManifest
import com.gusanitolabs.robia.sync.DriveRestoreProgressListener
import com.gusanitolabs.robia.sync.DriveSyncResult
import com.gusanitolabs.robia.sync.DriveWardrobeRepository

/** Remote Drive backup. Each call returns the configured result; uploads are recorded even when they fail. */
internal class FakeDriveWardrobeRepository(
    var manifest: DriveSyncResult<DriveManifest> = DriveSyncResult.Success(DriveManifest()),
    var snapshot: DriveSyncResult<WardrobeSyncSnapshot> = DriveSyncResult.Success(WardrobeSyncSnapshot()),
    var upsertResult: DriveSyncResult<DriveManifest>? = null,
) : DriveWardrobeRepository {
    override val target: DriveSyncTarget = DriveSyncTarget()
    val upserts = mutableListOf<WardrobeSyncSnapshot>()

    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = manifest

    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> = snapshot

    override suspend fun retryRestoredPhoto(garmentId: String, progress: DriveRestoreProgressListener?): DriveSyncResult<GarmentPhotoRecord> =
        DriveSyncResult.Failure(IllegalStateException("not used by sync tests"))

    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot?): DriveSyncResult<DriveManifest> {
        upserts += snapshot
        return upsertResult ?: DriveSyncResult.Success(DriveManifest.fromSnapshot(snapshot))
    }

    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> =
        DriveSyncResult.Success(DriveBackupDeletionResult())
}
