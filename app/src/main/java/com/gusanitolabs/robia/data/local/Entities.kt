package com.gusanitolabs.robia.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.gusanitolabs.robia.core.model.DisplayColorLabel

@Entity(tableName = "clothing_items")
data class ClothingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @Embedded(prefix = "color_") val colorMetrics: ColorMetricsEntity,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

data class ColorMetricsEntity(
    @ColumnInfo(name = "primary_raw_value") val primaryRawValue: String?,
    @ColumnInfo(name = "primary_display_label") val primaryDisplayLabel: DisplayColorLabel?,
    @ColumnInfo(name = "secondary_raw_value") val secondaryRawValue: String?,
    @ColumnInfo(name = "secondary_display_label") val secondaryDisplayLabel: DisplayColorLabel?,
)

@Entity(tableName = "tag_categories")
data class TagCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
)

@Entity(
    tableName = "garment_tags",
    foreignKeys = [
        ForeignKey(
            entity = TagCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class GarmentTagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
)

@Entity(tableName = "main_colors")
data class MainColorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hex: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
)

@Entity(
    tableName = "clothing_item_tags",
    primaryKeys = ["clothing_item_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["clothing_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GarmentTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class ClothingItemTagCrossRef(
    @ColumnInfo(name = "clothing_item_id") val clothingItemId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

data class ClothingItemWithTags(
    @Embedded val item: ClothingItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ClothingItemTagCrossRef::class,
            parentColumn = "clothing_item_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<GarmentTagEntity>,
)
