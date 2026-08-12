package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot

/**
 * Credential-free Drive contract. All cloud data is scoped to the authorized user's appDataFolder.
 */
interface DriveWardrobeRepository {
    val target: DriveSyncTarget
    suspend fun fetchManifest(): DriveSyncResult<DriveManifest>
    suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot>
    suspend fun retryRestoredPhoto(garmentId: String): DriveSyncResult<GarmentPhotoRecord>
    suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest>
    /** Deletes only Robia's remote appDataFolder backup; it never changes local clothes. */
    suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult>
}

class NotConfiguredDriveWardrobeRepository(
    private val reason: DriveSyncDisabledReason = DriveSyncDisabledReason.GoogleCloudSetupRequired,
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot> = notConfigured()
    override suspend fun retryRestoredPhoto(garmentId: String): DriveSyncResult<GarmentPhotoRecord> = notConfigured()
    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> = notConfigured()

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
    override suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot> =
        DriveSyncResult.Success(snapshot.sortedDeterministically())
    override suspend fun retryRestoredPhoto(garmentId: String): DriveSyncResult<GarmentPhotoRecord> =
        snapshot.photos.firstOrNull { it.garmentId == garmentId }
            ?.let { photo -> DriveSyncResult.Success(photo) }
            ?: DriveSyncResult.Failure(IllegalStateException("No remote photo exists for garment $garmentId."))

    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> {
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
}

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
) {
    companion object {
        fun fromSnapshot(snapshot: WardrobeSyncSnapshot): DriveManifest = DriveManifest(
            schemaVersion = snapshot.metadata.schemaVersion,
            wardrobeId = snapshot.metadata.wardrobeId,
            appPackage = snapshot.metadata.appPackage,
            updatedAtEpochMillis = snapshot.metadata.generatedAtEpochMillis,
            revision = snapshot.metadata.revision,
            target = snapshot.metadata.target,
        )
    }
}

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
