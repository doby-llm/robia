package com.gusanitolabs.robia.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM garment_embeddings")
    fun observeAll(): Flow<List<GarmentEmbeddingEntity>>

    @Query("SELECT * FROM garment_embeddings WHERE model_id = :modelId AND recipe_id = :recipeId")
    suspend fun getForRecipe(modelId: String, recipeId: String): List<GarmentEmbeddingEntity>

    @Query("SELECT * FROM garment_embeddings WHERE garment_id = :garmentId AND kind = :kind")
    suspend fun get(garmentId: String, kind: String): GarmentEmbeddingEntity?

    @Query("SELECT * FROM garment_embeddings WHERE pending_upload = 1")
    suspend fun pendingUpload(): List<GarmentEmbeddingEntity>

    @Upsert
    suspend fun upsert(entity: GarmentEmbeddingEntity)

    @Upsert
    suspend fun upsertAll(entities: List<GarmentEmbeddingEntity>)

    @Query("DELETE FROM garment_embeddings WHERE garment_id = :garmentId AND kind = :kind")
    suspend fun delete(garmentId: String, kind: String)

    @Query("DELETE FROM garment_embeddings WHERE garment_id = :garmentId")
    suspend fun deleteAll(garmentId: String)

    @Query("UPDATE garment_embeddings SET pending_upload = 0 WHERE garment_id = :garmentId AND kind = :kind")
    suspend fun markUploaded(garmentId: String, kind: String)

    /** Active garments with a photo that have no image vector for this model+recipe; newest first. */
    @Query(
        """
        SELECT * FROM clothing_items
        WHERE is_archived = 0
            AND photo_uri IS NOT NULL
            AND NOT EXISTS (
                SELECT 1 FROM garment_embeddings e
                WHERE e.garment_id = clothing_items.id
                    AND e.kind = 'image'
                    AND e.model_id = :modelId
                    AND e.recipe_id = :recipeId
            )
        ORDER BY created_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    suspend fun garmentsNeedingImage(modelId: String, recipeId: String, limit: Int): List<ClothingItemEntity>
}
