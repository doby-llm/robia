package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ClothingItemWithTags
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
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
        wardrobeDao.archiveItem(id, updatedAtEpochMillis)
    }

    override suspend fun archiveItems(ids: List<String>, updatedAtEpochMillis: Long) {
        wardrobeDao.archiveItems(ids, updatedAtEpochMillis)
    }
}

class LocalTagRepository(
    private val tagDao: TagDao,
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
        tagDao.upsertCategory(category.toEntity())
    }

    override suspend fun upsertTag(tag: GarmentTag) {
        tagDao.upsertTag(tag.toEntity())
    }

    override suspend fun upsertMainColor(color: MainColor) {
        tagDao.upsertMainColor(color.toEntity())
    }

    override suspend fun deleteCustomTag(id: String) {
        tagDao.deleteCustomTag(id)
    }

    override suspend fun deleteMainColor(id: String) {
        tagDao.deleteMainColor(id)
    }

    override suspend fun restoreDefaultTags(categoryId: String) {
        DefaultTags.categories
            .firstOrNull { category -> category.id == categoryId }
            ?.let { category -> tagDao.upsertCategory(category.toEntity()) }
        tagDao.upsertTags(
            DefaultTags.tags
                .filter { tag -> tag.categoryId == categoryId }
                .map(GarmentTag::toEntity),
        )
    }

    override suspend fun resetMainColorsToDefaults() {
        tagDao.replaceMainColors(DefaultTags.mainColors.map(MainColor::toEntity))
    }

    override suspend fun seedDefaultsIfNeeded() {
        tagDao.seedCategories(DefaultTags.categories.map(TagCategory::toEntity))
        tagDao.seedTags(DefaultTags.tags.map(GarmentTag::toEntity))
        if (tagDao.mainColorCount() == 0) {
            tagDao.seedMainColors(DefaultTags.mainColors.map(MainColor::toEntity))
        }
    }
}

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
)

private fun TagCategoryEntity.toDomain(): TagCategory = TagCategory(id, name, sortOrder, isSystem)
private fun TagCategory.toEntity(): TagCategoryEntity = TagCategoryEntity(id, name, sortOrder, isSystem)
private fun GarmentTagEntity.toDomain(): GarmentTag = GarmentTag(id, categoryId, name, sortOrder, isSystem)
private fun GarmentTag.toEntity(): GarmentTagEntity = GarmentTagEntity(id, categoryId, name, sortOrder, isSystem)
private fun MainColorEntity.toDomain(): MainColor = MainColor(id, name, hex, sortOrder, isDefault)
private fun MainColor.toEntity(): MainColorEntity = MainColorEntity(id, name, hex, sortOrder, isDefault)

private fun List<GarmentTag>.filterNotCare(): List<GarmentTag> = filterNot { tag -> tag.categoryId == "care" }
