package com.gusanitolabs.robia.sync

import androidx.room.withTransaction
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentColorRole
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
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
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.data.local.SyncTombstoneDao
import com.gusanitolabs.robia.data.local.SyncTombstoneEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.data.local.TagDao
import com.gusanitolabs.robia.data.local.WardrobeDao

/** Builds deterministic snapshots of the full local wardrobe graph for Drive sync. */
class LocalWardrobeSyncSnapshotRepository(
    private val database: RobiaDatabase,
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
        val itemUpdatedAtById = items.associate { item -> item.id to item.syncRevision }
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
                ref.toSyncRecord(revision = itemUpdatedAtById[ref.clothingItemId] ?: 0L)
            },
            garmentColors = items.flatMap(ClothingItemEntity::toColorRecords),
            photos = items.mapNotNull(ClothingItemEntity::toPhotoRecord),
            tombstones = tombstones.map(SyncTombstoneEntity::toSyncRecord),
        ).sortedDeterministically()
    }

    /**
     * Applies a fetched Drive snapshot as one Room transaction. Remote photo metadata is kept in the
     * Drive payload, but local rows only keep photo URIs that are still device-addressable; this avoids
     * silently restoring broken file/content URIs until binary blob download is implemented.
     */
    suspend fun importSnapshot(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult {
        val deterministicSnapshot = snapshot.sortedDeterministically().withoutTombstonedTaxonomy()
        val remotePhotoByGarmentId = deterministicSnapshot.photos.associateBy(GarmentPhotoRecord::garmentId)
        val remoteColorsByGarmentId = deterministicSnapshot.garmentColors.groupBy(GarmentColorMappingRecord::garmentId)
        val restoredItems = deterministicSnapshot.garments.map { garment ->
            garment.toEntity(
                photoUri = remotePhotoByGarmentId[garment.id]?.restorableLocalUri(),
                colorRecords = remoteColorsByGarmentId[garment.id].orEmpty(),
            )
        }
        val itemIds = restoredItems.map(ClothingItemEntity::id)
        val itemIdSet = itemIds.toSet()
        val tagRefs = deterministicSnapshot.garmentTags
            .filter { record -> record.garmentId in itemIdSet }
            .map { record -> ClothingItemTagCrossRef(record.garmentId, record.tagId) }
        val tombstones = deterministicSnapshot.tombstones.map(SyncTombstoneRecord::toEntity)

        database.withTransaction {
            deterministicSnapshot.taxonomies.categories.forEach { record -> tagDao.upsertCategory(record.toEntity()) }
            tagDao.upsertTags(deterministicSnapshot.taxonomies.tags.map(TagSyncRecord::toEntity))
            tagDao.upsertMainColors(deterministicSnapshot.taxonomies.mainColors.map(MainColorSyncRecord::toEntity))
            if (restoredItems.isNotEmpty()) {
                tagDao.upsertClothingItems(restoredItems)
                tagDao.clearTagsForItems(itemIds)
                tagDao.insertItemTagRefs(tagRefs)
            }
            if (tombstones.isNotEmpty()) tagDao.upsertSyncTombstones(tombstones)
        }

        return ImportSnapshotResult(
            restoredGarmentCount = restoredItems.count { item -> !item.isArchived },
            guardedPhotoCount = deterministicSnapshot.photos.count { photo -> photo.restorableLocalUri() == null },
        )
    }
}

data class ImportSnapshotResult(
    val restoredGarmentCount: Int,
    val guardedPhotoCount: Int,
)

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
    revision = syncRevision,
)

private fun ClothingItemTagCrossRef.toSyncRecord(revision: Long): GarmentTagMappingRecord = GarmentTagMappingRecord(
    garmentId = clothingItemId,
    tagId = tagId,
    revision = revision,
    updatedAtEpochMillis = revision,
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
        revision = syncRevision,
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
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
    ),
).filter { record -> record.rawValue != null || record.displayLabel != null || record.paletteColorId != null }

private fun ClothingItemEntity.toPhotoRecord(): GarmentPhotoRecord? = photoUri?.let { uri ->
    GarmentPhotoRecord(
        garmentId = id,
        localUri = uri,
        blobPath = DriveFolderNaming.photoBlobPath(id, uri),
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun SyncTombstoneEntity.toSyncRecord(): SyncTombstoneRecord = SyncTombstoneRecord(
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = deletedAtEpochMillis,
    revision = revision,
)

private fun TagCategorySyncRecord.toEntity(): TagCategoryEntity = TagCategoryEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
)

private fun TagSyncRecord.toEntity(): GarmentTagEntity = GarmentTagEntity(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
)

private fun MainColorSyncRecord.toEntity(): MainColorEntity = MainColorEntity(
    id = id,
    name = name,
    hex = hex,
    sortOrder = sortOrder,
    isDefault = isDefault,
)

private fun GarmentSyncRecord.toEntity(
    photoUri: String?,
    colorRecords: List<GarmentColorMappingRecord>,
): ClothingItemEntity {
    val colorsByRole = colorRecords.associateBy(GarmentColorMappingRecord::role)
    return ClothingItemEntity(
        id = id,
        name = name,
        notes = notes,
        photoUri = photoUri,
        fitValue = fitValue,
        colorMetrics = ColorMetricsEntity(
            primaryRawValue = colorsByRole[GarmentColorRole.Primary]?.rawValue,
            primaryDisplayLabel = colorsByRole[GarmentColorRole.Primary]?.displayLabel,
            primaryPaletteColorId = colorsByRole[GarmentColorRole.Primary]?.paletteColorId,
            primaryPaletteColorName = colorsByRole[GarmentColorRole.Primary]?.paletteColorName,
            primaryPaletteColorHex = colorsByRole[GarmentColorRole.Primary]?.paletteColorHex,
            secondaryRawValue = colorsByRole[GarmentColorRole.Secondary]?.rawValue,
            secondaryDisplayLabel = colorsByRole[GarmentColorRole.Secondary]?.displayLabel,
            secondaryPaletteColorId = colorsByRole[GarmentColorRole.Secondary]?.paletteColorId,
            secondaryPaletteColorName = colorsByRole[GarmentColorRole.Secondary]?.paletteColorName,
            secondaryPaletteColorHex = colorsByRole[GarmentColorRole.Secondary]?.paletteColorHex,
        ),
        isFavorite = isFavorite,
        isArchived = isArchived,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        syncStatus = GarmentSyncStatus.Synced,
        syncRevision = revision,
        syncDirtyAtEpochMillis = null,
        lastSyncedAtEpochMillis = System.currentTimeMillis(),
        syncFailureMessage = null,
    )
}

private fun SyncTombstoneRecord.toEntity(): SyncTombstoneEntity = SyncTombstoneEntity(
    id = "$entityType:$entityId",
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = deletedAtEpochMillis,
    revision = revision,
)

private fun WardrobeSyncSnapshot.withoutTombstonedTaxonomy(): WardrobeSyncSnapshot {
    val tombstoneByCategoryId = tombstones
        .filter { tombstone -> tombstone.entityType in categoryEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByTagId = tombstones
        .filter { tombstone -> tombstone.entityType in tagEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByMainColorId = tombstones
        .filter { tombstone -> tombstone.entityType in mainColorEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)

    val activeCategories = taxonomies.categories
        .filterNot { category -> (tombstoneByCategoryId[category.id]?.revision ?: Long.MIN_VALUE) > category.revision }
    val activeCategoryIds = activeCategories.map(TagCategorySyncRecord::id).toSet()
    val activeTags = taxonomies.tags
        .filterNot { tag -> (tombstoneByTagId[tag.id]?.revision ?: Long.MIN_VALUE) > tag.revision }
        .filter { tag -> tag.categoryId in activeCategoryIds }
    val activeTagIds = activeTags.map(TagSyncRecord::id).toSet()
    val activeMainColors = taxonomies.mainColors
        .filterNot { color -> (tombstoneByMainColorId[color.id]?.revision ?: Long.MIN_VALUE) > color.revision }
    val activeMainColorIds = activeMainColors.map(MainColorSyncRecord::id).toSet()

    return copy(
        taxonomies = WardrobeTaxonomySnapshot(
            categories = activeCategories,
            tags = activeTags,
            mainColors = activeMainColors,
        ),
        garmentTags = garmentTags.filter { record -> record.tagId in activeTagIds },
        garmentColors = garmentColors.filter { record ->
            record.paletteColorId?.let(activeMainColorIds::contains) != false
        },
    ).sortedDeterministically()
}

private val categoryEntityTypes = setOf("tag_category", "category")
private val tagEntityTypes = setOf("garment_tag", "tag")
private val mainColorEntityTypes = setOf("main_color", "palette_color", "color")

private fun GarmentPhotoRecord.restorableLocalUri(): String? = restoredLocalUri?.takeIf(String::isNotBlank)
