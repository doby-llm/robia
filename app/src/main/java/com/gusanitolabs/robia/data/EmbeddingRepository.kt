package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.data.local.EmbeddingDao
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.toFloatArrayLe
import com.gusanitolabs.robia.suggestions.toLeBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GarmentVectors(
    val garmentId: String,
    val image: FloatArray?,
    val text: FloatArray?,
)

/** Identifies one stored vector; used to find and mark rows awaiting Drive upload. */
data class EmbeddingKey(val garmentId: String, val kind: String)

/**
 * Stores garment vectors in `garment_embeddings` only. It holds no reference to the wardrobe DAO, so
 * writing a vector cannot change a garment row's sync revision, status or timestamps.
 */
class EmbeddingRepository(
    private val embeddingDao: EmbeddingDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Decoded vectors for one model+recipe only; rows from other recipes are ignored. */
    fun observeVectors(modelId: String, recipeId: String): Flow<Map<String, GarmentVectors>> =
        embeddingDao.observeAll().map { rows ->
            rows
                .filter { row -> row.modelId == modelId && row.recipeId == recipeId }
                .groupBy(GarmentEmbeddingEntity::garmentId)
                .mapValues { (garmentId, garmentRows) ->
                    GarmentVectors(
                        garmentId = garmentId,
                        image = garmentRows.vectorOf(EmbeddingKind.IMAGE),
                        text = garmentRows.vectorOf(EmbeddingKind.TEXT),
                    )
                }
        }

    /** Stores a vector for the current model+recipe and marks it pending upload. */
    suspend fun upsert(garmentId: String, kind: String, vector: FloatArray, inputHash: String, durationMs: Long) {
        require(vector.size == EmbeddingRecipe.DIMENSION) {
            "Expected ${EmbeddingRecipe.DIMENSION} dimensions, got ${vector.size}"
        }
        embeddingDao.upsert(
            GarmentEmbeddingEntity(
                garmentId = garmentId,
                kind = kind,
                modelId = EmbeddingRecipe.MODEL_ID,
                recipeId = EmbeddingRecipe.RECIPE_ID,
                dim = EmbeddingRecipe.DIMENSION,
                vector = vector.toLeBytes(),
                inputHash = inputHash,
                computedAtEpochMillis = clock(),
                durationMillis = durationMs,
                pendingUpload = true,
            ),
        )
    }

    suspend fun delete(garmentId: String, kind: String) {
        embeddingDao.delete(garmentId, kind)
    }

    suspend fun deleteAll(garmentId: String) {
        embeddingDao.deleteAll(garmentId)
    }

    suspend fun pendingUploadKeys(): List<EmbeddingKey> =
        embeddingDao.pendingUpload().map { row -> EmbeddingKey(row.garmentId, row.kind) }

    suspend fun markUploaded(keys: List<EmbeddingKey>) {
        keys.forEach { key -> embeddingDao.markUploaded(key.garmentId, key.kind) }
    }
}

private fun List<GarmentEmbeddingEntity>.vectorOf(kind: String): FloatArray? =
    firstOrNull { row -> row.kind == kind }?.vector?.let { bytes -> bytes.toFloatArrayLe() }
