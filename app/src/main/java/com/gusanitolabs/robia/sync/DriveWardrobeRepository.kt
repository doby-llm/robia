package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import java.io.IOException

/**
 * Credential-free Drive contract. All cloud data is scoped to the authorized user's appDataFolder.
 */
interface DriveWardrobeRepository {
    val target: DriveSyncTarget
    suspend fun fetchManifest(): DriveSyncResult<DriveManifest>
    suspend fun fetchSnapshot(progress: DriveRestoreProgressListener? = null): DriveSyncResult<WardrobeSyncSnapshot>
    /** The remote snapshot JSON only: photo records without blobs, for keeping a guarded or failed photo's record. */
    suspend fun fetchSnapshotRecords(): DriveSyncResult<WardrobeSyncSnapshot>
    suspend fun retryRestoredPhoto(garmentId: String, progress: DriveRestoreProgressListener? = null): DriveSyncResult<GarmentPhotoRecord>
    /** [onPhotoProgress] reports photos done over total, and how many of them were Reuse, while photos upload; null keeps the upload silent. */
    suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int, reusedPhotos: Int) -> Unit)? = null,
    ): DriveSyncResult<DriveManifest>
    /** Deletes only Robia's remote appDataFolder backup; it never changes local clothes. */
    suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult>
    /** One listing of the app data folder for the Settings figure; never part of a normal sync. */
    suspend fun refreshDriveUsage(): DriveSyncResult<DriveUsage>
    /** Read-only "Check photos": one listing and the remote snapshot JSON, compared with the local photo files of [snapshot]. */
    suspend fun checkPhotos(snapshot: WardrobeSyncSnapshot): DriveSyncResult<PhotoIntegrityReport> =
        DriveSyncResult.Failure(UnsupportedOperationException("Photo check is not available for this Drive adapter."))
}

class NotConfiguredDriveWardrobeRepository(
    private val reason: DriveSyncDisabledReason = DriveSyncDisabledReason.GoogleCloudSetupRequired,
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> = notConfigured()
    override suspend fun fetchSnapshotRecords(): DriveSyncResult<WardrobeSyncSnapshot> = notConfigured()
    override suspend fun retryRestoredPhoto(garmentId: String, progress: DriveRestoreProgressListener?): DriveSyncResult<GarmentPhotoRecord> = notConfigured()
    override suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int, reusedPhotos: Int) -> Unit)?,
    ): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> = notConfigured()
    override suspend fun refreshDriveUsage(): DriveSyncResult<DriveUsage> = notConfigured()

    private fun <T> notConfigured(): DriveSyncResult<T> =
        DriveSyncResult.Blocked(reason, "Google Drive sync is not configured yet.")
}

/** Small deterministic fake for JVM tests and merge-policy tests. */
class InMemoryDriveWardrobeRepository(
    private var snapshot: WardrobeSyncSnapshot = WardrobeSyncSnapshot(),
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    private var manifest: DriveManifest = DriveManifest.fromSnapshot(snapshot)

    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = DriveSyncResult.Success(manifest)
    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> =
        DriveSyncResult.Success(snapshot.sortedDeterministically())
    override suspend fun fetchSnapshotRecords(): DriveSyncResult<WardrobeSyncSnapshot> =
        DriveSyncResult.Success(snapshot.sortedDeterministically())
    override suspend fun retryRestoredPhoto(garmentId: String, progress: DriveRestoreProgressListener?): DriveSyncResult<GarmentPhotoRecord> =
        snapshot.photos.firstOrNull { it.garmentId == garmentId }
            ?.let { photo -> DriveSyncResult.Success(photo) }
            ?: DriveSyncResult.Failure(IllegalStateException("No remote photo exists for garment $garmentId."))

    override suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int, reusedPhotos: Int) -> Unit)?,
    ): DriveSyncResult<DriveManifest> {
        val deterministicSnapshot = snapshot.sortedDeterministically()
        this.snapshot = deterministicSnapshot
        manifest = DriveManifest.fromSnapshot(deterministicSnapshot)
        return DriveSyncResult.Success(manifest)
    }

    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> {
        snapshot = WardrobeSyncSnapshot()
        manifest = DriveManifest.fromSnapshot(snapshot)
        return DriveSyncResult.Success(DriveBackupDeletionResult())
    }

    override suspend fun refreshDriveUsage(): DriveSyncResult<DriveUsage> =
        DriveSyncResult.Success(driveUsageOf(emptyList(), now = 0L))
}

fun interface DriveRestoreProgressListener {
    fun onProgress(progress: DriveRestoreProgress)
}

data class DriveRestoreProgress(
    val garmentId: String,
    val safeCategory: String?,
    val completedItems: Int,
    val totalItems: Int,
    val completedBytes: Long,
    val totalBytes: Long?,
)

sealed interface DriveSyncResult<out T> {
    data class Success<T>(val value: T) : DriveSyncResult<T>
    data class Blocked(val reason: DriveSyncDisabledReason, val message: String) : DriveSyncResult<Nothing>
    data class Failure(val throwable: Throwable) : DriveSyncResult<Nothing>
}

data class DriveManifest(
    val schemaVersion: Int = WARDROBE_SYNC_SCHEMA_VERSION,
    val wardrobeId: String? = null,
    val appPackage: String = "com.gusanitolabs.robia",
    val rootPath: String = "appDataFolder:/robia/",
    val snapshotPath: String = "wardrobe_snapshot.json",
    val photosPath: String = "photos/",
    val updatedAtEpochMillis: Long = 0L,
    val revision: Long = 0L,
    val target: DriveSyncTarget = DriveSyncTarget(),
    val photoUploadFailures: List<PhotoUploadFailure> = emptyList(),
    val writerDeviceId: String? = null,
) {
    companion object {
        fun fromSnapshot(snapshot: WardrobeSyncSnapshot): DriveManifest = DriveManifest(
            schemaVersion = snapshot.metadata.schemaVersion,
            wardrobeId = snapshot.metadata.wardrobeId,
            appPackage = snapshot.metadata.appPackage,
            updatedAtEpochMillis = snapshot.metadata.generatedAtEpochMillis,
            revision = snapshot.metadata.revision,
            target = snapshot.metadata.target,
            writerDeviceId = snapshot.metadata.writerDeviceId,
        )
    }
}

/** Google Drive reports the account quota is full. Classified by the processor as [SyncAttentionKind.DriveFull]. */
class DriveStorageFullException : IOException("Google Drive storage quota is exceeded.")

/** A photo whose upload failed permanently; its previous record was kept and its blobs are not purged. */
data class PhotoUploadFailure(val garmentId: String)

data class DriveBackupDeletionResult(
    val deletedFileCount: Int = 0,
    val remainingFileCount: Int = 0,
    val failureMessage: String? = null,
)

object DriveFolderNaming {
    private val unsafePathCharacters = Regex("[^A-Za-z0-9._-]")
    fun photoBlobPrefix(itemUid: String): String = "photos/${safeSegment(itemUid.ifBlank { "unknown" })}/"
    fun photoBlobPath(itemUid: String, localUri: String? = null): String {
        val photoKey = localUri?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "original"
        return "${photoBlobPrefix(itemUid)}${safeSegment(photoKey)}"
    }
    private fun safeSegment(value: String): String = value.trim().replace(unsafePathCharacters, "_")
}
