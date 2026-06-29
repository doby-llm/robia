package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentColorRole
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColorSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSnapshotMetadata
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ClothingItemTagCrossRef
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
import com.gusanitolabs.robia.data.local.SyncTombstoneDao
import com.gusanitolabs.robia.data.local.SyncTombstoneEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.data.local.TagDao
import com.gusanitolabs.robia.data.local.WardrobeDao

/** Builds deterministic snapshots of the full local wardrobe graph for Drive sync. */
class LocalWardrobeSyncSnapshotRepository(
    private val wardrobeDao: WardrobeDao,
    private val tagDao: TagDao,
    private val syncTombstoneDao: SyncTombstoneDao,
) {
    suspend fun exportSnapshot(generatedAtEpochMillis: Long = System.currentTimeMillis()): WardrobeSyncSnapshot {
        val items = wardrobeDao.getAllItemsForSync().map { itemWithTags -> itemWithTags.item }
        val itemTagRefs = wardrobeDao.getItemTagRefsForSync()
        val categories = tagDao.getCategoriesForSync()
        val tags = tagDao.getTagsForSync()
        val colors = tagDao.getMainColorsForSync()
        val tombstones = syncTombstoneDao.getAllForSync()
        val itemUpdatedAtById = items.associate { item -> item.id to item.updatedAtEpochMillis }
        val snapshotRevision = maxOf(
            itemUpdatedAtById.values.maxOrNull() ?: 0L,
            tombstones.maxOfOrNull(SyncTombstoneEntity::revision) ?: 0L,
            generatedAtEpochMillis,
        )

        return WardrobeSyncSnapshot(
            metadata = WardrobeSnapshotMetadata(
                generatedAtEpochMillis = generatedAtEpochMillis,
                revision = snapshotRevision,
            ),
            taxonomies = WardrobeTaxonomySnapshot(
                categories = categories.map(TagCategoryEntity::toSyncRecord),
                tags = tags.map(GarmentTagEntity::toSyncRecord),
                mainColors = colors.map(MainColorEntity::toSyncRecord),
            ),
            garments = items.map(ClothingItemEntity::toGarmentRecord),
            garmentTags = itemTagRefs.map { ref ->
                ref.toSyncRecord(updatedAtEpochMillis = items.firstOrNull { item -> item.id == ref.clothingItemId }?.updatedAtEpochMillis ?: 0L)
            },
            garmentColors = items.flatMap(ClothingItemEntity::toColorRecords),
            photos = items.mapNotNull(ClothingItemEntity::toPhotoRecord),
            tombstones = tombstones.map(SyncTombstoneEntity::toSyncRecord),
        ).sortedDeterministically()
    }
}

private fun TagCategoryEntity.toSyncRecord(): TagCategorySyncRecord = TagCategorySyncRecord(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
)

private fun GarmentTagEntity.toSyncRecord(): TagSyncRecord = TagSyncRecord(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
)

private fun MainColorEntity.toSyncRecord(): MainColorSyncRecord = MainColorSyncRecord(
    id = id,
    name = name,
    hex = hex,
    sortOrder = sortOrder,
    isDefault = isDefault,
)

private fun ClothingItemEntity.toGarmentRecord(): GarmentSyncRecord = GarmentSyncRecord(
    id = id,
    name = name,
    notes = notes,
    fitValue = fitValue,
    isFavorite = isFavorite,
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    revision = updatedAtEpochMillis,
)

private fun ClothingItemTagCrossRef.toSyncRecord(updatedAtEpochMillis: Long): GarmentTagMappingRecord = GarmentTagMappingRecord(
    garmentId = clothingItemId,
    tagId = tagId,
    revision = updatedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ClothingItemEntity.toColorRecords(): List<GarmentColorMappingRecord> = listOf(
    GarmentColorMappingRecord(
        garmentId = id,
        role = GarmentColorRole.Primary,
        rawValue = colorMetrics.primaryRawValue,
        displayLabel = colorMetrics.primaryDisplayLabel,
        paletteColorId = colorMetrics.primaryPaletteColorId,
        paletteColorName = colorMetrics.primaryPaletteColorName,
        paletteColorHex = colorMetrics.primaryPaletteColorHex,
        revision = updatedAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    ),
    GarmentColorMappingRecord(
        garmentId = id,
        role = GarmentColorRole.Secondary,
        rawValue = colorMetrics.secondaryRawValue,
        displayLabel = colorMetrics.secondaryDisplayLabel,
        paletteColorId = colorMetrics.secondaryPaletteColorId,
        paletteColorName = colorMetrics.secondaryPaletteColorName,
        paletteColorHex = colorMetrics.secondaryPaletteColorHex,
        revision = updatedAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    ),
).filter { record -> record.rawValue != null || record.displayLabel != null || record.paletteColorId != null }

private fun ClothingItemEntity.toPhotoRecord(): GarmentPhotoRecord? = photoUri?.let { uri ->
    GarmentPhotoRecord(
        garmentId = id,
        localUri = uri,
        blobPath = DriveFolderNaming.photoBlobPath(id, uri),
        revision = updatedAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun SyncTombstoneEntity.toSyncRecord(): SyncTombstoneRecord = SyncTombstoneRecord(
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = deletedAtEpochMillis,
    revision = revision,
)
