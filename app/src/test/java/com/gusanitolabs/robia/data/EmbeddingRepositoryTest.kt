package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.EmbeddingDao
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.toLeBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingRepositoryTest {
    private val dao = InMemoryEmbeddingDao()
    private val repository = EmbeddingRepository(dao, clock = { 1_234L })

    /** The repository is constructed from the embedding DAO alone, so it cannot write garment rows. */
    @Test
    fun upsertStoresLittleEndianVectorForCurrentRecipeAndMarksPending() = runBlocking {
        val vector = FloatArray(EmbeddingRecipe.DIMENSION) { index -> index.toFloat() }

        repository.upsert("garment-1", EmbeddingKind.IMAGE, vector, inputHash = "hash", durationMs = 42L)

        val stored = dao.rows.getValue("garment-1" to EmbeddingKind.IMAGE)
        assertEquals(EmbeddingRecipe.MODEL_ID, stored.modelId)
        assertEquals(EmbeddingRecipe.RECIPE_ID, stored.recipeId)
        assertEquals(EmbeddingRecipe.DIMENSION, stored.dim)
        assertArrayEquals(vector.toLeBytes(), stored.vector)
        assertEquals("hash", stored.inputHash)
        assertEquals(1_234L, stored.computedAtEpochMillis)
        assertEquals(42L, stored.durationMillis)
        assertTrue(stored.pendingUpload)
    }

    @Test
    fun upsertRejectsVectorsOfTheWrongDimension() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.upsert("garment-1", EmbeddingKind.TEXT, FloatArray(3), "hash", 1L) }
        }
    }

    @Test
    fun observeVectorsDecodesCurrentRecipeOnly() = runBlocking {
        val image = FloatArray(EmbeddingRecipe.DIMENSION) { index -> index * 0.25f }
        repository.upsert("garment-1", EmbeddingKind.IMAGE, image, "hash", 1L)
        dao.upsert(
            dao.rows.getValue("garment-1" to EmbeddingKind.IMAGE).copy(
                garmentId = "garment-2",
                kind = EmbeddingKind.TEXT,
                recipeId = "older-recipe",
            ),
        )

        val vectors = repository.observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID).first()

        assertEquals(setOf("garment-1"), vectors.keys)
        assertArrayEquals(image, vectors.getValue("garment-1").image, 0f)
        assertNull(vectors.getValue("garment-1").text)
    }

    @Test
    fun markUploadedClearsPendingKeys() = runBlocking {
        val vector = FloatArray(EmbeddingRecipe.DIMENSION)
        repository.upsert("garment-1", EmbeddingKind.IMAGE, vector, "hash", 1L)
        repository.upsert("garment-1", EmbeddingKind.TEXT, vector, "hash", 1L)

        assertEquals(
            listOf(EmbeddingKey("garment-1", EmbeddingKind.IMAGE), EmbeddingKey("garment-1", EmbeddingKind.TEXT)),
            repository.pendingUploadKeys(),
        )
        repository.markUploaded(listOf(EmbeddingKey("garment-1", EmbeddingKind.IMAGE)))

        assertEquals(listOf(EmbeddingKey("garment-1", EmbeddingKind.TEXT)), repository.pendingUploadKeys())
    }

    @Test
    fun deleteRemovesOneKindAndDeleteAllRemovesGarment() = runBlocking {
        val vector = FloatArray(EmbeddingRecipe.DIMENSION)
        repository.upsert("garment-1", EmbeddingKind.IMAGE, vector, "hash", 1L)
        repository.upsert("garment-1", EmbeddingKind.TEXT, vector, "hash", 1L)
        repository.upsert("garment-2", EmbeddingKind.IMAGE, vector, "hash", 1L)

        repository.delete("garment-1", EmbeddingKind.TEXT)
        assertEquals(setOf("garment-1" to EmbeddingKind.IMAGE, "garment-2" to EmbeddingKind.IMAGE), dao.rows.keys)

        repository.deleteAll("garment-1")
        assertEquals(setOf("garment-2" to EmbeddingKind.IMAGE), dao.rows.keys)
    }

}

/** Keyed by (garmentId, kind) like the real table's primary key. Unused DAO queries fail loudly. */
private class InMemoryEmbeddingDao : EmbeddingDao {
    val rows = linkedMapOf<Pair<String, String>, GarmentEmbeddingEntity>()
    private val state = MutableStateFlow<List<GarmentEmbeddingEntity>>(emptyList())

    private fun publish() {
        state.value = rows.values.toList()
    }

    override fun observeAll(): Flow<List<GarmentEmbeddingEntity>> = state

    override suspend fun getForRecipe(modelId: String, recipeId: String): List<GarmentEmbeddingEntity> =
        error("unused in EmbeddingRepositoryTest")

    override suspend fun get(garmentId: String, kind: String): GarmentEmbeddingEntity? = rows[garmentId to kind]

    override suspend fun pendingUpload(): List<GarmentEmbeddingEntity> = rows.values.filter { row -> row.pendingUpload }

    override suspend fun upsert(entity: GarmentEmbeddingEntity) {
        rows[entity.garmentId to entity.kind] = entity
        publish()
    }

    override suspend fun upsertAll(entities: List<GarmentEmbeddingEntity>) {
        entities.forEach { entity -> rows[entity.garmentId to entity.kind] = entity }
        publish()
    }

    override suspend fun delete(garmentId: String, kind: String) {
        rows.remove(garmentId to kind)
        publish()
    }

    override suspend fun deleteAll(garmentId: String) {
        rows.keys.removeAll { key -> key.first == garmentId }
        publish()
    }

    override suspend fun markUploaded(garmentId: String, kind: String) {
        rows[garmentId to kind]?.let { row -> rows[garmentId to kind] = row.copy(pendingUpload = false) }
        publish()
    }

    override suspend fun garmentsNeedingImage(modelId: String, recipeId: String, limit: Int): List<ClothingItemEntity> =
        error("unused in EmbeddingRepositoryTest")
}
