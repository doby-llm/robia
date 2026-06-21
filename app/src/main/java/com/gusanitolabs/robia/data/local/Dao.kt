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

    @Upsert
    suspend fun upsertItem(item: ClothingItemEntity)

    @Query("DELETE FROM clothing_item_tags WHERE clothing_item_id = :itemId")
    suspend fun clearTags(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<ClothingItemTagCrossRef>)

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis WHERE id = :itemId")
    suspend fun archiveItem(itemId: String, updatedAtEpochMillis: Long)

    @Transaction
    suspend fun upsertItemWithTags(item: ClothingItemEntity, tagIds: List<String>) {
        upsertItem(item)
        clearTags(item.id)
        insertTagRefs(tagIds.map { tagId -> ClothingItemTagCrossRef(item.id, tagId) })
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag_categories ORDER BY sort_order, name")
    fun observeCategories(): Flow<List<TagCategoryEntity>>

    @Query("SELECT * FROM garment_tags ORDER BY sort_order, name")
    fun observeTags(): Flow<List<GarmentTagEntity>>

    @Query("SELECT * FROM main_colors ORDER BY sort_order, name")
    fun observeMainColors(): Flow<List<MainColorEntity>>

    @Upsert
    suspend fun upsertCategory(category: TagCategoryEntity)

    @Upsert
    suspend fun upsertTag(tag: GarmentTagEntity)

    @Upsert
    suspend fun upsertMainColor(color: MainColorEntity)

    @Query("DELETE FROM garment_tags WHERE id = :id AND category_id != 'season'")
    suspend fun deleteCustomTag(id: String)

    @Query("DELETE FROM main_colors WHERE id = :id AND (SELECT COUNT(*) FROM main_colors) > 1")
    suspend fun deleteMainColor(id: String)

    @Query("SELECT COUNT(*) FROM main_colors")
    suspend fun mainColorCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedCategories(categories: List<TagCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedTags(tags: List<GarmentTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedMainColors(colors: List<MainColorEntity>)
}
