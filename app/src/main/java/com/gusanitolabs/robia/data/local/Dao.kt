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

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id = :itemId")
    suspend fun archiveItem(itemId: String, updatedAtEpochMillis: Long)

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id IN (:itemIds)")
    suspend fun archiveItems(itemIds: List<String>, updatedAtEpochMillis: Long)

    @Transaction
    suspend fun upsertItemWithTags(item: ClothingItemEntity, tagIds: List<String>) {
        upsertItem(item)
        clearTags(item.id)
        insertTagRefs(tagIds.map { tagId -> ClothingItemTagCrossRef(item.id, tagId) })
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

    @Query("DELETE FROM garment_tags WHERE id = :id AND is_system = 0")
    suspend fun deleteCustomTag(id: String): Int

    @Query("DELETE FROM main_colors WHERE id = :id AND (SELECT COUNT(*) FROM main_colors) > 1")
    suspend fun deleteMainColor(id: String): Int

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
}

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones ORDER BY entity_type, entity_id")
    suspend fun getAllForSync(): List<SyncTombstoneEntity>

    @Upsert
    suspend fun upsert(tombstone: SyncTombstoneEntity)
}
