package com.gusanitolabs.robia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeDao {
    @Transaction
    @Query("SELECT * FROM clothing_items WHERE is_archived = 0 ORDER BY is_favorite DESC, updated_at_epoch_millis DESC")
    fun observeActiveItems(): Flow<List<ClothingItemWithTags>>

    @Transaction
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun observeItem(id: String): Flow<ClothingItemWithTags?>

    @Transaction
    @Query("SELECT * FROM clothing_items ORDER BY id")
    suspend fun getAllItemsForSync(): List<ClothingItemWithTags>

    @Query("SELECT * FROM clothing_item_tags ORDER BY clothing_item_id, tag_id")
    suspend fun getItemTagRefsForSync(): List<ClothingItemTagCrossRef>

    @Upsert
    suspend fun upsertItem(item: ClothingItemEntity)

    @Query("DELETE FROM clothing_item_tags WHERE clothing_item_id = :itemId")
    suspend fun clearTags(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<ClothingItemTagCrossRef>)

    @Query("SELECT id FROM garment_tags WHERE id IN (:ids)")
    suspend fun existingTagIds(ids: List<String>): List<String>

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id = :itemId")
    suspend fun archiveItem(itemId: String, updatedAtEpochMillis: Long)

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id IN (:itemIds)")
    suspend fun archiveItems(itemIds: List<String>, updatedAtEpochMillis: Long)

    @Transaction
    suspend fun upsertItemWithTags(item: ClothingItemEntity, tagIds: List<String>) {
        upsertItem(item)
        clearTags(item.id)
        val activeTagIds: Set<String> = if (tagIds.isEmpty()) emptySet() else existingTagIds(tagIds).toSet()
        insertTagRefs(
            tagIds
                .filter(activeTagIds::contains)
                .map { tagId -> ClothingItemTagCrossRef(item.id, tagId) },
        )
    }

    @Transaction
    suspend fun upsertItemsWithTags(items: List<ClothingItemEntity>, tagIdsByItemId: Map<String, List<String>>) {
        items.forEach { item ->
            upsertItemWithTags(item, tagIdsByItemId[item.id].orEmpty())
        }
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag_categories ORDER BY sort_order, name")
    fun observeCategories(): Flow<List<TagCategoryEntity>>

    @Query("SELECT * FROM tag_categories ORDER BY sort_order, id")
    suspend fun getCategoriesForSync(): List<TagCategoryEntity>

    @Query("SELECT * FROM garment_tags ORDER BY category_id, sort_order, id")
    suspend fun getTagsForSync(): List<GarmentTagEntity>

    @Query("SELECT * FROM main_colors ORDER BY sort_order, id")
    suspend fun getMainColorsForSync(): List<MainColorEntity>

    @Query("SELECT * FROM garment_tags ORDER BY sort_order, name")
    fun observeTags(): Flow<List<GarmentTagEntity>>

    @Query("SELECT * FROM main_colors ORDER BY sort_order, name")
    fun observeMainColors(): Flow<List<MainColorEntity>>

    @Upsert
    suspend fun upsertCategory(category: TagCategoryEntity)

    @Upsert
    suspend fun upsertTag(tag: GarmentTagEntity)

    @Upsert
    suspend fun upsertTags(tags: List<GarmentTagEntity>)

    @Upsert
    suspend fun upsertMainColor(color: MainColorEntity)

    @Upsert
    suspend fun upsertMainColors(colors: List<MainColorEntity>)

    @Upsert
    suspend fun upsertClothingItems(items: List<ClothingItemEntity>)

    @Query("DELETE FROM clothing_item_tags WHERE clothing_item_id IN (:itemIds)")
    suspend fun clearTagsForItems(itemIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTagRefs(refs: List<ClothingItemTagCrossRef>)

    @Upsert
    suspend fun upsertSyncTombstones(tombstones: List<SyncTombstoneEntity>)

    @Query("DELETE FROM clothing_item_tags WHERE tag_id = :id")
    suspend fun deleteItemRefsForTag(id: String)

    @Query(
        """
        DELETE FROM garment_tags
        WHERE id = :id
            AND (is_system = 0 OR category_id IN ('category', 'season', 'occasion'))
        """,
    )
    suspend fun deleteEditableTag(id: String): Int

    @Query("DELETE FROM main_colors WHERE id = :id AND (SELECT COUNT(*) FROM main_colors) > 1")
    suspend fun deleteMainColor(id: String): Int

    @Query("DELETE FROM main_colors WHERE id IN (:ids) AND (SELECT COUNT(*) FROM main_colors) > :deleteCount")
    suspend fun deleteMainColors(ids: List<String>, deleteCount: Int): Int

    @Query("SELECT COUNT(*) FROM main_colors")
    suspend fun mainColorCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedCategories(categories: List<TagCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedTags(tags: List<GarmentTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedMainColors(colors: List<MainColorEntity>)

    @Query("DELETE FROM main_colors")
    suspend fun deleteAllMainColors()

    @Transaction
    suspend fun replaceMainColors(colors: List<MainColorEntity>) {
        deleteAllMainColors()
        seedMainColors(colors)
    }

    @Transaction
    suspend fun applyMainColorChange(
        upsertColors: List<MainColorEntity>,
        deleteColorIds: List<String>,
        updatedItems: List<ClothingItemEntity>,
        tagIdsByItemId: Map<String, List<String>>,
        tombstones: List<SyncTombstoneEntity>,
    ) {
        if (upsertColors.isNotEmpty()) upsertMainColors(upsertColors)
        if (deleteColorIds.isNotEmpty()) deleteMainColors(deleteColorIds, deleteColorIds.size)
        if (tombstones.isNotEmpty()) upsertSyncTombstones(tombstones)
        if (updatedItems.isNotEmpty()) {
            val itemIds = updatedItems.map(ClothingItemEntity::id)
            upsertClothingItems(updatedItems)
            clearTagsForItems(itemIds)
            insertItemTagRefs(
                tagIdsByItemId.flatMap { (itemId, tagIds) ->
                    tagIds.map { tagId -> ClothingItemTagCrossRef(itemId, tagId) }
                },
            )
        }
    }

    @Transaction
    suspend fun deleteEditableTagAndReferences(id: String, tombstone: SyncTombstoneEntity): Int {
        deleteItemRefsForTag(id)
        val deletedCount = deleteEditableTag(id)
        if (deletedCount > 0) upsertSyncTombstones(listOf(tombstone))
        return deletedCount
    }
}

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones ORDER BY entity_type, entity_id")
    suspend fun getAllForSync(): List<SyncTombstoneEntity>

    @Upsert
    suspend fun upsert(tombstone: SyncTombstoneEntity)
}
