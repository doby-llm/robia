package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.MainColor

/**
 * Credential-free contract for a future Google Drive adapter.
 *
 * Implementations must not assume OAuth is available. Until Google Cloud and OAuth setup
 * are complete, production code should provide [NotConfiguredDriveWardrobeRepository].
 */
interface DriveWardrobeRepository {
    suspend fun fetchManifest(): DriveSyncResult<DriveManifest>
    suspend fun listItems(): DriveSyncResult<List<DriveItemSnapshot>>
    suspend fun upsertItem(item: ClothingItem): DriveSyncResult<DriveItemSnapshot>
    suspend fun deleteItemFolder(itemId: String): DriveSyncResult<Unit>
    suspend fun upsertPalette(colors: List<MainColor>): DriveSyncResult<Unit>
}

class NotConfiguredDriveWardrobeRepository(
    private val reason: DriveSyncDisabledReason = DriveSyncDisabledReason.GoogleCloudSetupRequired,
) : DriveWardrobeRepository {
    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = notConfigured()
    override suspend fun listItems(): DriveSyncResult<List<DriveItemSnapshot>> = notConfigured()
    override suspend fun upsertItem(item: ClothingItem): DriveSyncResult<DriveItemSnapshot> = notConfigured()
    override suspend fun deleteItemFolder(itemId: String): DriveSyncResult<Unit> = notConfigured()
    override suspend fun upsertPalette(colors: List<MainColor>): DriveSyncResult<Unit> = notConfigured()

    private fun <T> notConfigured(): DriveSyncResult<T> =
        DriveSyncResult.Blocked(reason, "Google Drive sync is not configured yet.")
}

/** Small deterministic fake for JVM tests and future merge-policy tests. */
class InMemoryDriveWardrobeRepository(
    private var manifest: DriveManifest = DriveManifest(),
) : DriveWardrobeRepository {
    private val itemsById = linkedMapOf<String, ClothingItem>()

    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = DriveSyncResult.Success(manifest)

    override suspend fun listItems(): DriveSyncResult<List<DriveItemSnapshot>> = DriveSyncResult.Success(
        itemsById.values.map { item -> item.toDriveSnapshot() },
    )

    override suspend fun upsertItem(item: ClothingItem): DriveSyncResult<DriveItemSnapshot> {
        itemsById[item.id] = item
        manifest = manifest.copy(updatedAtEpochMillis = maxOf(manifest.updatedAtEpochMillis, item.updatedAtEpochMillis))
        return DriveSyncResult.Success(item.toDriveSnapshot())
    }

    override suspend fun deleteItemFolder(itemId: String): DriveSyncResult<Unit> {
        itemsById.remove(itemId)
        return DriveSyncResult.Success(Unit)
    }

    override suspend fun upsertPalette(colors: List<MainColor>): DriveSyncResult<Unit> {
        manifest = manifest.copy(updatedAtEpochMillis = System.currentTimeMillis())
        return DriveSyncResult.Success(Unit)
    }

    private fun ClothingItem.toDriveSnapshot(): DriveItemSnapshot = DriveItemSnapshot(
        itemUid = id,
        item = this,
        folderName = DriveFolderNaming.itemFolderName(id),
        updatedAtEpochMillis = updatedAtEpochMillis,
        isTrashed = isArchived,
    )
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
    val schemaVersion: Int = 1,
    val wardrobeId: String? = null,
    val appPackage: String = "com.gusanitolabs.robia",
    val itemsPath: String = "items/",
    val palettePath: String = "palettes/colors.json",
    val updatedAtEpochMillis: Long = 0L,
)

data class DriveItemSnapshot(
    val itemUid: String,
    val item: ClothingItem,
    val folderName: String,
    val updatedAtEpochMillis: Long,
    val isTrashed: Boolean = false,
)

object DriveFolderNaming {
    private val unsafePathCharacters = Regex("[^A-Za-z0-9._-]")

    fun itemFolderName(itemUid: String): String =
        "garment_${itemUid.trim().replace(unsafePathCharacters, "_")}"
}
