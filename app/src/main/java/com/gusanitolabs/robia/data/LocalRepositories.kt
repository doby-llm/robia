package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ClothingItemWithTags
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
import com.gusanitolabs.robia.data.local.PendingGarmentSyncWorkEntity
import com.gusanitolabs.robia.data.local.PendingMetadataSyncWorkEntity
import com.gusanitolabs.robia.data.local.SyncTombstoneDao
import com.gusanitolabs.robia.data.local.SyncTombstoneEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.data.local.TagDao
import com.gusanitolabs.robia.data.local.WardrobeDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalWardrobeRepository(
    private val wardrobeDao: WardrobeDao,
) : WardrobeRepository {
    override fun observeActiveItems(): Flow<List<ClothingItem>> =
        wardrobeDao.observeActiveItems().map { items -> items.map(ClothingItemWithTags::toDomain) }

    override fun observeItem(id: String): Flow<ClothingItem?> =
        wardrobeDao.observeItem(id).map { it?.toDomain() }

    override fun observePendingGarmentSyncCount(): Flow<Int> = wardrobeDao.observePendingGarmentSyncCount()

    override fun observeGarmentSyncAttentionCount(): Flow<Int> = wardrobeDao.observeGarmentSyncAttentionCount()

    override fun observePendingMetadataSyncCount(): Flow<Int> = wardrobeDao.observePendingMetadataSyncCount()

    override fun observeMetadataSyncAttentionCount(): Flow<Int> = wardrobeDao.observeMetadataSyncAttentionCount()

    override suspend fun pendingGarmentSyncWork(): List<PendingGarmentSyncWork> =
        wardrobeDao.pendingGarmentSyncWork().map(PendingGarmentSyncWorkEntity::toDomain)

    override suspend fun pendingMetadataSyncWork(): List<PendingMetadataSyncWork> =
        wardrobeDao.pendingMetadataSyncWork().map(PendingMetadataSyncWorkEntity::toDomain)

    override suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int =
        wardrobeDao.recoverStaleRunningSyncWork(staleBeforeEpochMillis)

    override suspend fun upsertItem(item: ClothingItem) {
        wardrobeDao.upsertItemWithTags(item.toEntity(), item.tags.map(GarmentTag::id))
    }

    override suspend fun upsertItems(items: List<ClothingItem>) {
        wardrobeDao.upsertItemsWithTags(
            items = items.map(ClothingItem::toEntity),
            tagIdsByItemId = items.associate { item -> item.id to item.tags.map(GarmentTag::id) },
        )
    }

    override suspend fun archiveItem(id: String, updatedAtEpochMillis: Long) {
        wardrobeDao.archiveItemWithTombstone(
            itemId = id,
            updatedAtEpochMillis = updatedAtEpochMillis,
            tombstone = syncTombstone(entityType = "garment", entityId = id, deletedAtEpochMillis = updatedAtEpochMillis),
        )
    }

    override suspend fun archiveItems(ids: List<String>, updatedAtEpochMillis: Long) {
        if (ids.isEmpty()) return
        wardrobeDao.archiveItemsWithTombstones(
            itemIds = ids,
            updatedAtEpochMillis = updatedAtEpochMillis,
            tombstones = ids.map { id ->
                syncTombstone(entityType = "garment", entityId = id, deletedAtEpochMillis = updatedAtEpochMillis)
            },
        )
    }

    override suspend fun markGarmentSyncing(id: String, revision: Long, startedAtEpochMillis: Long): Boolean =
        wardrobeDao.markGarmentSyncing(id, revision, startedAtEpochMillis) > 0

    override suspend fun markGarmentSynced(id: String, revision: Long, syncedAtEpochMillis: Long): Boolean =
        wardrobeDao.markGarmentSynced(id, revision, syncedAtEpochMillis) > 0

    override suspend fun markGarmentSyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Boolean =
        wardrobeDao.markGarmentSyncFailedRetryable(id, revision, message, now) > 0

    override suspend fun markGarmentSyncAuthBlocked(id: String, message: String?): Boolean =
        wardrobeDao.markGarmentSyncAuthBlocked(id, message) > 0

    override suspend fun markMetadataSyncing(work: PendingMetadataSyncWork): Boolean =
        wardrobeDao.markMetadataSyncing(work.toEntity())

    override suspend fun markMetadataSynced(work: PendingMetadataSyncWork, syncedAtEpochMillis: Long): Boolean =
        wardrobeDao.markMetadataSynced(work.toEntity(), syncedAtEpochMillis)

    override suspend fun markMetadataSyncFailedRetryable(work: PendingMetadataSyncWork, message: String?): Boolean =
        wardrobeDao.markMetadataSyncFailedRetryable(work.toEntity(), message)

    override suspend fun markMetadataSyncAuthBlocked(work: PendingMetadataSyncWork, message: String?): Boolean =
        wardrobeDao.markMetadataSyncAuthBlocked(work.toEntity(), message)
}

class LocalTagRepository(
    private val tagDao: TagDao,
    private val syncTombstoneDao: SyncTombstoneDao? = null,
) : TagRepository {
    override fun observeCategories(): Flow<List<TagCategory>> =
        tagDao.observeCategories().map { categories ->
            categories.map(TagCategoryEntity::toDomain).filterNot { category -> category.id == "care" }
        }

    override fun observeTags(): Flow<List<GarmentTag>> =
        tagDao.observeTags().map { tags -> tags.map(GarmentTagEntity::toDomain).filterNotCare() }

    override fun observeMainColors(): Flow<List<MainColor>> =
        tagDao.observeMainColors().map { colors -> colors.map(MainColorEntity::toDomain) }

    override suspend fun upsertCategory(category: TagCategory) {
        tagDao.upsertCategory(category.toEntity().queuedForSync())
    }

    override suspend fun upsertTag(tag: GarmentTag) {
        tagDao.upsertTag(tag.toEntity().queuedForSync())
    }

    override suspend fun upsertMainColor(color: MainColor) {
        tagDao.upsertMainColor(color.toEntity().queuedForSync())
    }

    override suspend fun applyMainColorChange(
        upsertColors: List<MainColor>,
        deleteColorIds: List<String>,
        updatedItems: List<ClothingItem>,
    ) {
        tagDao.applyMainColorChange(
            upsertColors = upsertColors.map { color -> color.toEntity().queuedForSync() },
            deleteColorIds = deleteColorIds,
            updatedItems = updatedItems.map(ClothingItem::toEntity),
            tagIdsByItemId = updatedItems.associate { item -> item.id to item.tags.map(GarmentTag::id) },
            tombstones = deleteColorIds.map { id -> syncTombstone(entityType = "main_color", entityId = id) },
        )
    }

    override suspend fun deleteCustomTag(id: String) {
        tagDao.deleteEditableTagAndReferences(
            id = id,
            tombstone = syncTombstone(entityType = "garment_tag", entityId = id),
        )
    }

    override suspend fun deleteMainColor(id: String) {
        if (tagDao.deleteMainColor(id) > 0) {
            syncTombstoneDao?.upsert(syncTombstone(entityType = "main_color", entityId = id))
        }
    }

    override suspend fun restoreDefaultTags(categoryId: String) {
        DefaultTags.categories
            .firstOrNull { category -> category.id == categoryId }
            ?.let { category -> tagDao.upsertCategory(category.toEntity().queuedForSync()) }
        tagDao.upsertTags(
            DefaultTags.tags
                .filter { tag -> tag.categoryId == categoryId }
                .map { tag -> tag.toEntity().queuedForSync() },
        )
    }

    override suspend fun resetMainColorsToDefaults() {
        tagDao.replaceMainColors(DefaultTags.mainColors.map { color -> color.toEntity().queuedForSync() })
    }

    override suspend fun seedDefaultsIfNeeded() {
        tagDao.seedCategories(DefaultTags.categories.map(TagCategory::toEntity))
        tagDao.seedTags(DefaultTags.tags.map(GarmentTag::toEntity))
        if (tagDao.mainColorCount() == 0) {
            tagDao.seedMainColors(DefaultTags.mainColors.map(MainColor::toEntity))
        }
    }
}

private fun PendingGarmentSyncWorkEntity.toDomain(): PendingGarmentSyncWork = PendingGarmentSyncWork(
    id = id,
    revision = revision,
)

private fun PendingMetadataSyncWorkEntity.toDomain(): PendingMetadataSyncWork = PendingMetadataSyncWork(
    entityType = entityType,
    id = id,
    revision = revision,
)

private fun PendingMetadataSyncWork.toEntity(): PendingMetadataSyncWorkEntity = PendingMetadataSyncWorkEntity(
    entityType = entityType,
    id = id,
    revision = revision,
)

private fun ClothingItemWithTags.toDomain(): ClothingItem = ClothingItem(
    id = item.id,
    name = item.name,
    notes = item.notes,
    photoUri = item.photoUri,
    tags = tags.map(GarmentTagEntity::toDomain),
    fitValue = item.fitValue,
    colorMetrics = ClothingColorMetrics(
        primaryRawValue = item.colorMetrics.primaryRawValue,
        primaryDisplayLabel = item.colorMetrics.primaryDisplayLabel,
        primaryPaletteColorId = item.colorMetrics.primaryPaletteColorId,
        primaryPaletteColorName = item.colorMetrics.primaryPaletteColorName,
        primaryPaletteColorHex = item.colorMetrics.primaryPaletteColorHex,
        secondaryRawValue = item.colorMetrics.secondaryRawValue,
        secondaryDisplayLabel = item.colorMetrics.secondaryDisplayLabel,
        secondaryPaletteColorId = item.colorMetrics.secondaryPaletteColorId,
        secondaryPaletteColorName = item.colorMetrics.secondaryPaletteColorName,
        secondaryPaletteColorHex = item.colorMetrics.secondaryPaletteColorHex,
    ),
    isFavorite = item.isFavorite,
    isArchived = item.isArchived,
    createdAtEpochMillis = item.createdAtEpochMillis,
    updatedAtEpochMillis = item.updatedAtEpochMillis,
    syncStatus = item.syncStatus,
    syncRevision = item.syncRevision,
    syncDirtyAtEpochMillis = item.syncDirtyAtEpochMillis,
    lastSyncedAtEpochMillis = item.lastSyncedAtEpochMillis,
    syncFailureMessage = item.syncFailureMessage,
)

private fun ClothingItem.toEntity(): ClothingItemEntity = ClothingItemEntity(
    id = id,
    name = name,
    notes = notes,
    photoUri = photoUri,
    fitValue = fitValue,
    colorMetrics = ColorMetricsEntity(
        primaryRawValue = colorMetrics.primaryRawValue,
        primaryDisplayLabel = colorMetrics.primaryDisplayLabel,
        primaryPaletteColorId = colorMetrics.primaryPaletteColorId,
        primaryPaletteColorName = colorMetrics.primaryPaletteColorName,
        primaryPaletteColorHex = colorMetrics.primaryPaletteColorHex,
        secondaryRawValue = colorMetrics.secondaryRawValue,
        secondaryDisplayLabel = colorMetrics.secondaryDisplayLabel,
        secondaryPaletteColorId = colorMetrics.secondaryPaletteColorId,
        secondaryPaletteColorName = colorMetrics.secondaryPaletteColorName,
        secondaryPaletteColorHex = colorMetrics.secondaryPaletteColorHex,
    ),
    isFavorite = isFavorite,
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    syncStatus = syncStatus,
    syncRevision = syncRevision,
    syncDirtyAtEpochMillis = syncDirtyAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    syncFailureMessage = syncFailureMessage,
)

private fun TagCategoryEntity.toDomain(): TagCategory = TagCategory(id, name, sortOrder, isSystem)
private fun TagCategory.toEntity(): TagCategoryEntity = TagCategoryEntity(id, name, sortOrder, isSystem)
private fun GarmentTagEntity.toDomain(): GarmentTag = GarmentTag(id, categoryId, name, sortOrder, isSystem)
private fun GarmentTag.toEntity(): GarmentTagEntity = GarmentTagEntity(id, categoryId, name, sortOrder, isSystem)
private fun MainColorEntity.toDomain(): MainColor = MainColor(id, name, hex, sortOrder, isDefault)
private fun MainColor.toEntity(): MainColorEntity = MainColorEntity(id, name, hex, sortOrder, isDefault)

private fun TagCategoryEntity.queuedForSync(): TagCategoryEntity {
    val revision = System.currentTimeMillis()
    return copy(syncStatus = GarmentSyncStatus.Queued, syncRevision = revision, syncDirtyAtEpochMillis = revision, syncFailureMessage = null)
}

private fun GarmentTagEntity.queuedForSync(): GarmentTagEntity {
    val revision = System.currentTimeMillis()
    return copy(syncStatus = GarmentSyncStatus.Queued, syncRevision = revision, syncDirtyAtEpochMillis = revision, syncFailureMessage = null)
}

private fun MainColorEntity.queuedForSync(): MainColorEntity {
    val revision = System.currentTimeMillis()
    return copy(syncStatus = GarmentSyncStatus.Queued, syncRevision = revision, syncDirtyAtEpochMillis = revision, syncFailureMessage = null)
}

private fun syncTombstone(
    entityType: String,
    entityId: String,
    deletedAtEpochMillis: Long = System.currentTimeMillis(),
): SyncTombstoneEntity {
    return SyncTombstoneEntity(
        id = "$entityType:$entityId",
        entityType = entityType,
        entityId = entityId,
        deletedAtEpochMillis = deletedAtEpochMillis,
        revision = deletedAtEpochMillis + 1,
    )
}

private fun List<GarmentTag>.filterNotCare(): List<GarmentTag> = filterNot { tag -> tag.categoryId == "care" }
