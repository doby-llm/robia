package com.gusanitolabs.robia.core.model

/** Connection state for the Google Drive adapter seam. */
enum class DriveSyncConnectionStatus {
    Disabled,
    NotConfigured,
    Disconnected,
    Connected,
    Syncing,
    NeedsAttention,
}

enum class DriveSyncDisabledReason {
    GoogleCloudSetupRequired,
    OAuthClientMissing,
    UserNotConnected,
}

/**
 * Where Robia stores sync payloads once OAuth credentials are supplied.
 *
 * The account is always the user-authorized Google account returned by the OAuth flow.
 * Robia must never bind wardrobe data to a service account or a generic app account.
 */
data class DriveSyncTarget(
    val accountBinding: DriveAccountBinding = DriveAccountBinding.UserAuthorizedGoogleAccount,
    val storageSpace: DriveStorageSpace = DriveStorageSpace.AppDataFolder,
)

enum class DriveAccountBinding {
    UserAuthorizedGoogleAccount,
}

enum class DriveStorageSpace {
    AppDataFolder,
}

/** Deterministic, credential-free export payload for the whole wardrobe graph. */
data class WardrobeSyncSnapshot(
    val metadata: WardrobeSnapshotMetadata = WardrobeSnapshotMetadata(),
    val taxonomies: WardrobeTaxonomySnapshot = WardrobeTaxonomySnapshot(),
    val garments: List<GarmentSyncRecord> = emptyList(),
    val garmentTags: List<GarmentTagMappingRecord> = emptyList(),
    val garmentColors: List<GarmentColorMappingRecord> = emptyList(),
    val photos: List<GarmentPhotoRecord> = emptyList(),
    val tombstones: List<SyncTombstoneRecord> = emptyList(),
) {
    fun sortedDeterministically(): WardrobeSyncSnapshot = copy(
        taxonomies = taxonomies.sortedDeterministically(),
        garments = garments.sortedWith(compareBy(GarmentSyncRecord::id)),
        garmentTags = garmentTags.sortedWith(compareBy(GarmentTagMappingRecord::garmentId, GarmentTagMappingRecord::tagId)),
        garmentColors = garmentColors.sortedWith(compareBy(GarmentColorMappingRecord::garmentId, GarmentColorMappingRecord::role)),
        photos = photos.sortedWith(compareBy(GarmentPhotoRecord::garmentId, GarmentPhotoRecord::localUri)),
        tombstones = tombstones.sortedWith(compareBy(SyncTombstoneRecord::entityType, SyncTombstoneRecord::entityId)),
    )
}

data class WardrobeSnapshotMetadata(
    val schemaVersion: Int = WARDROBE_SYNC_SCHEMA_VERSION,
    val appPackage: String = "com.gusanitolabs.robia",
    val generatedAtEpochMillis: Long = 0L,
    val target: DriveSyncTarget = DriveSyncTarget(),
    val wardrobeId: String? = null,
    val revision: Long = 0L,
)

data class WardrobeTaxonomySnapshot(
    val categories: List<TagCategorySyncRecord> = emptyList(),
    val tags: List<TagSyncRecord> = emptyList(),
    val mainColors: List<MainColorSyncRecord> = emptyList(),
) {
    fun sortedDeterministically(): WardrobeTaxonomySnapshot = copy(
        categories = categories.sortedWith(compareBy(TagCategorySyncRecord::sortOrder, TagCategorySyncRecord::id)),
        tags = tags.sortedWith(compareBy(TagSyncRecord::categoryId, TagSyncRecord::sortOrder, TagSyncRecord::id)),
        mainColors = mainColors.sortedWith(compareBy(MainColorSyncRecord::sortOrder, MainColorSyncRecord::id)),
    )
}

data class TagCategorySyncRecord(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    val isArchived: Boolean = false,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class TagSyncRecord(
    val id: String,
    val categoryId: String,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    val isArchived: Boolean = false,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class MainColorSyncRecord(
    val id: String,
    val name: String,
    val hex: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isArchived: Boolean = false,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class GarmentSyncRecord(
    val id: String,
    val name: String,
    val notes: String,
    val fitValue: Int?,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revision: Long = 0L,
)

data class GarmentTagMappingRecord(
    val garmentId: String,
    val tagId: String,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class GarmentColorMappingRecord(
    val garmentId: String,
    val role: GarmentColorRole,
    val rawValue: String?,
    val displayLabel: DisplayColorLabel?,
    val paletteColorId: String?,
    val paletteColorName: String?,
    val paletteColorHex: String?,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

enum class GarmentColorRole {
    Primary,
    Secondary,
}

data class GarmentPhotoRecord(
    val garmentId: String,
    val localUri: String,
    val blobPath: String = "photos/$garmentId/original",
    val mimeType: String? = null,
    val contentHash: String? = null,
    val byteSize: Long? = null,
    val revision: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class SyncTombstoneRecord(
    val entityType: String,
    val entityId: String,
    val deletedAtEpochMillis: Long,
    val revision: Long = 0L,
)

const val WARDROBE_SYNC_SCHEMA_VERSION: Int = 2
