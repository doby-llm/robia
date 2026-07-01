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
import com.gusanitolabs.robia.core.model.GarmentSyncStatus

@Entity(tableName = "clothing_items")
data class ClothingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @ColumnInfo(name = "fit_value") val fitValue: Int?,
    @Embedded(prefix = "color_") val colorMetrics: ColorMetricsEntity,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: GarmentSyncStatus = GarmentSyncStatus.LocalOnly,
    @ColumnInfo(name = "sync_revision") val syncRevision: Long = updatedAtEpochMillis,
    @ColumnInfo(name = "sync_dirty_at_epoch_millis") val syncDirtyAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_synced_at_epoch_millis") val lastSyncedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "sync_failure_message") val syncFailureMessage: String? = null,
)

data class ColorMetricsEntity(
    @ColumnInfo(name = "primary_raw_value") val primaryRawValue: String?,
    @ColumnInfo(name = "primary_display_label") val primaryDisplayLabel: DisplayColorLabel?,
    @ColumnInfo(name = "primary_palette_color_id") val primaryPaletteColorId: String?,
    @ColumnInfo(name = "primary_palette_color_name") val primaryPaletteColorName: String?,
    @ColumnInfo(name = "primary_palette_color_hex") val primaryPaletteColorHex: String?,
    @ColumnInfo(name = "secondary_raw_value") val secondaryRawValue: String?,
    @ColumnInfo(name = "secondary_display_label") val secondaryDisplayLabel: DisplayColorLabel?,
    @ColumnInfo(name = "secondary_palette_color_id") val secondaryPaletteColorId: String?,
    @ColumnInfo(name = "secondary_palette_color_name") val secondaryPaletteColorName: String?,
    @ColumnInfo(name = "secondary_palette_color_hex") val secondaryPaletteColorHex: String?,
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
    tableName = "sync_tombstones",
    indices = [Index(value = ["entity_type", "entity_id"], unique = true)],
)
data class SyncTombstoneEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "deleted_at_epoch_millis") val deletedAtEpochMillis: Long,
    val revision: Long = 0L,
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

data class PendingGarmentSyncWorkEntity(
    val id: String,
    val revision: Long,
)
