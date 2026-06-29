package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot

/**
 * Credential-free contract for a future Google Drive adapter.
 *
 * Implementations must not assume OAuth is available. Until Google Cloud and OAuth setup
 * are complete, production code should provide [NotConfiguredDriveWardrobeRepository].
 * The adapter must bind to the user-authorized Google account and default to Drive
 * appDataFolder storage; no service-account or generic app-account data stores.
 */
interface DriveWardrobeRepository {
    val target: DriveSyncTarget

    suspend fun fetchManifest(): DriveSyncResult<DriveManifest>
    suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot>
    suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest>
}

class NotConfiguredDriveWardrobeRepository(
    private val reason: DriveSyncDisabledReason = DriveSyncDisabledReason.GoogleCloudSetupRequired,
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot> = notConfigured()
    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> = notConfigured()

    private fun <T> notConfigured(): DriveSyncResult<T> =
        DriveSyncResult.Blocked(reason, "Google Drive sync is not configured yet.")
}

/** Small deterministic fake for JVM tests and future merge-policy tests. */
class InMemoryDriveWardrobeRepository(
    private var snapshot: WardrobeSyncSnapshot = WardrobeSyncSnapshot(),
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    private var manifest: DriveManifest = DriveManifest.fromSnapshot(snapshot)

    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = DriveSyncResult.Success(manifest)

    override suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot> =
        DriveSyncResult.Success(snapshot.sortedDeterministically())

    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> {
        val deterministicSnapshot = snapshot.sortedDeterministically()
        this.snapshot = deterministicSnapshot
        manifest = DriveManifest.fromSnapshot(deterministicSnapshot)
        return DriveSyncResult.Success(manifest)
    }
}

sealed interface DriveSyncResult<out T> {
    data class Success<T>(val value: T) : DriveSyncResult<T>
    data class Blocked(
        val reason: DriveSyncDisabledReason,
        val message: String,
    ) : DriveSyncResult<Nothing>
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

object DriveFolderNaming {
    private val unsafePathCharacters = Regex("[^A-Za-z0-9._-]")

    fun photoBlobPath(itemUid: String, localUri: String? = null): String {
        val safeItemUid = safeSegment(itemUid.ifBlank { "unknown" })
        val photoKey = localUri
            ?.substringAfterLast('/')
            ?.takeIf { segment -> segment.isNotBlank() }
            ?: "original"
        val safePhotoKey = safeSegment(photoKey)
        return "photos/$safeItemUid/$safePhotoKey"
    }

    private fun safeSegment(value: String): String = value.trim().replace(unsafePathCharacters, "_")
}
