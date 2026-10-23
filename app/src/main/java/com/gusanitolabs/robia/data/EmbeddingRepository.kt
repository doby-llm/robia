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
    /** `input_hash` stored with the IMAGE vector; a duplicate scan compares it with the current photo. */
    val imageInputHash: String? = null,
)

/**
 * Identifies one stored vector awaiting Drive upload. [computedAtEpochMillis] is the version that was exported,
 * so marking it uploaded cannot clear a vector that was re-computed after the export.
 */
data class EmbeddingKey(val garmentId: String, val kind: String, val computedAtEpochMillis: Long)

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
        embeddingDao.observeAll().map { rows -> rows.toGarmentVectors(modelId, recipeId) }

    /** One-shot read for a scan; unlike [observeVectors] it does not re-emit on every vector write. */
    suspend fun snapshotVectors(modelId: String, recipeId: String): Map<String, GarmentVectors> =
        embeddingDao.getForRecipe(modelId, recipeId).toGarmentVectors(modelId, recipeId)

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
        embeddingDao.pendingUpload().map { row -> EmbeddingKey(row.garmentId, row.kind, row.computedAtEpochMillis) }

    suspend fun markUploaded(keys: List<EmbeddingKey>) {
        keys.forEach { key -> embeddingDao.markUploaded(key.garmentId, key.kind, key.computedAtEpochMillis) }
    }
}

private fun List<GarmentEmbeddingEntity>.toGarmentVectors(modelId: String, recipeId: String): Map<String, GarmentVectors> =
    filter { row -> row.modelId == modelId && row.recipeId == recipeId }
        .groupBy(GarmentEmbeddingEntity::garmentId)
        .mapValues { (garmentId, garmentRows) ->
            GarmentVectors(
                garmentId = garmentId,
                image = garmentRows.vectorOf(EmbeddingKind.IMAGE),
                text = garmentRows.vectorOf(EmbeddingKind.TEXT),
                imageInputHash = garmentRows.firstOrNull { row -> row.kind == EmbeddingKind.IMAGE }?.inputHash,
            )
        }

private fun List<GarmentEmbeddingEntity>.vectorOf(kind: String): FloatArray? =
    firstOrNull { row -> row.kind == kind }?.vector?.let { bytes -> bytes.toFloatArrayLe() }
