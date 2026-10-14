package com.gusanitolabs.robia.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

object EmbeddingKind {
    const val IMAGE = "image"
    const val TEXT = "text"
}

@Entity(
    tableName = "garment_embeddings",
    primaryKeys = ["garment_id", "kind"],
    foreignKeys = [ForeignKey(entity = ClothingItemEntity::class, parentColumns = ["id"], childColumns = ["garment_id"], onDelete = ForeignKey.CASCADE)],
)
data class GarmentEmbeddingEntity(
    @ColumnInfo(name = "garment_id") val garmentId: String,
    val kind: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val dim: Int,
    val vector: ByteArray,
    @ColumnInfo(name = "input_hash") val inputHash: String,
    @ColumnInfo(name = "computed_at_epoch_millis") val computedAtEpochMillis: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "pending_upload") val pendingUpload: Boolean = true,
)
