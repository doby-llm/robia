package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import kotlinx.coroutines.flow.Flow

interface WardrobeRepository {
    fun observeActiveItems(): Flow<List<ClothingItem>>
    fun observeItem(id: String): Flow<ClothingItem?>
    suspend fun upsertItem(item: ClothingItem)
    suspend fun upsertItems(items: List<ClothingItem>)
    suspend fun archiveItem(id: String, updatedAtEpochMillis: Long)
    suspend fun archiveItems(ids: List<String>, updatedAtEpochMillis: Long)
}

interface TagRepository {
    fun observeCategories(): Flow<List<TagCategory>>
    fun observeTags(): Flow<List<GarmentTag>>
    fun observeMainColors(): Flow<List<MainColor>>
    suspend fun upsertCategory(category: TagCategory)
    suspend fun upsertTag(tag: GarmentTag)
    suspend fun upsertMainColor(color: MainColor)
    suspend fun deleteCustomTag(id: String)
    suspend fun deleteMainColor(id: String)
    suspend fun seedDefaultsIfNeeded()
}
