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
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

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
            listOf(
                EmbeddingKey("garment-1", EmbeddingKind.IMAGE, 1_234L),
                EmbeddingKey("garment-1", EmbeddingKind.TEXT, 1_234L),
            ),
            repository.pendingUploadKeys(),
        )
        repository.markUploaded(listOf(EmbeddingKey("garment-1", EmbeddingKind.IMAGE, 1_234L)))

        assertEquals(listOf(EmbeddingKey("garment-1", EmbeddingKind.TEXT, 1_234L)), repository.pendingUploadKeys())
    }

    /** The processor exports the pending keys, then a re-compute lands before the upload is marked. */
    @Test
    fun vectorRecomputedAfterExportStaysPendingAfterUpload() = runBlocking {
        var now = 1_000L
        val live = EmbeddingRepository(dao, clock = { now })
        val vector = FloatArray(EmbeddingRecipe.DIMENSION)
        live.upsert("garment-1", EmbeddingKind.IMAGE, vector, "hash-old", 1L)
        val exported = live.pendingUploadKeys()

        now = 2_000L
        live.upsert("garment-1", EmbeddingKind.IMAGE, vector, "hash-new", 1L)
        live.markUploaded(exported)

        assertEquals(listOf(EmbeddingKey("garment-1", EmbeddingKind.IMAGE, 2_000L)), live.pendingUploadKeys())
        assertTrue(dao.rows.getValue("garment-1" to EmbeddingKind.IMAGE).pendingUpload)
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

    /**
     * V-D03 on sqlite-jdbc. [upsertEmbeddingLikeDao] runs the INSERT ... ON CONFLICT upsert that
     * EmbeddingDao.upsert performs; the DAO itself needs Room, which does not run in JVM tests.
     */
    @Test
    fun upsertLeavesGarmentRowUnchangedInSqlite() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTableFromSchema(connection, "clothing_items")
            createTableFromSchema(connection, "garment_embeddings")
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO clothing_items (
                        id, name, notes, is_favorite, is_archived, created_at_epoch_millis, updated_at_epoch_millis,
                        sync_status, sync_revision, retry_attempt_count, photo_restore_guarded
                    ) VALUES ('garment-1', 'Shirt', '', 0, 0, 1, 1000, 'Synced', 7, 0, 0)
                    """.trimIndent(),
                )
            }
            val garmentBefore = garmentRow(connection, "garment-1")

            upsertEmbeddingLikeDao(connection, "garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION) { 1f })
            upsertEmbeddingLikeDao(connection, "garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION) { 2f })

            val garmentAfter = garmentRow(connection, "garment-1")
            assertEquals(garmentBefore, garmentAfter)
            assertEquals(1000L, (garmentAfter.getValue("updated_at_epoch_millis") as Number).toLong())
            assertEquals(7L, (garmentAfter.getValue("sync_revision") as Number).toLong())
            assertEquals("Synced", garmentAfter["sync_status"])
            assertEquals(
                listOf(1L to 1),
                queryLongs(connection, "SELECT COUNT(*), MAX(pending_upload) FROM garment_embeddings"),
            )
        }
    }

    private fun createTableFromSchema(connection: Connection, tableName: String) {
        val entities = JSONObject(File(SCHEMA_13).readText()).getJSONObject("database").getJSONArray("entities")
        val entity = (0 until entities.length())
            .map { index -> entities.getJSONObject(index) }
            .single { it.getString("tableName") == tableName }
        connection.createStatement().use { statement ->
            statement.execute(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
        }
    }

    private fun upsertEmbeddingLikeDao(connection: Connection, garmentId: String, kind: String, vector: FloatArray) {
        connection.prepareStatement(
            """
            INSERT INTO garment_embeddings (
                garment_id, kind, model_id, recipe_id, dim, vector, input_hash,
                computed_at_epoch_millis, duration_millis, pending_upload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (garment_id, kind) DO UPDATE SET
                model_id = excluded.model_id,
                recipe_id = excluded.recipe_id,
                dim = excluded.dim,
                vector = excluded.vector,
                input_hash = excluded.input_hash,
                computed_at_epoch_millis = excluded.computed_at_epoch_millis,
                duration_millis = excluded.duration_millis,
                pending_upload = excluded.pending_upload
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, garmentId)
            statement.setString(2, kind)
            statement.setString(3, EmbeddingRecipe.MODEL_ID)
            statement.setString(4, EmbeddingRecipe.RECIPE_ID)
            statement.setInt(5, EmbeddingRecipe.DIMENSION)
            statement.setBytes(6, vector.toLeBytes())
            statement.setString(7, "hash")
            statement.setLong(8, 1_234L)
            statement.setLong(9, 42L)
            statement.setInt(10, 1)
            statement.executeUpdate()
        }
    }

    private fun garmentRow(connection: Connection, garmentId: String): Map<String, Any?> =
        connection.prepareStatement("SELECT * FROM clothing_items WHERE id = ?").use { statement ->
            statement.setString(1, garmentId)
            statement.executeQuery().use { rs ->
                assertTrue(rs.next())
                (1..rs.metaData.columnCount).associate { column -> rs.metaData.getColumnName(column) to rs.getObject(column) }
            }
        }

    private fun queryLongs(connection: Connection, sql: String): List<Pair<Long, Int>> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) add(rs.getLong(1) to rs.getInt(2))
                }
            }
        }

    private companion object {
        const val SCHEMA_13 = "schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/13.json"
    }
}

/** Keyed by (garmentId, kind) like the real table's primary key. Unused DAO queries fail loudly. */
internal class InMemoryEmbeddingDao : EmbeddingDao {
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

    override suspend fun deleteEverything(): Int {
        val count = rows.size
        rows.clear()
        publish()
        return count
    }

    override suspend fun markUploaded(garmentId: String, kind: String, computedAtEpochMillis: Long) {
        rows[garmentId to kind]?.let { row ->
            if (row.computedAtEpochMillis == computedAtEpochMillis) rows[garmentId to kind] = row.copy(pendingUpload = false)
        }
        publish()
    }

    override suspend fun garmentsNeedingImage(modelId: String, recipeId: String, limit: Int): List<ClothingItemEntity> =
        error("unused in EmbeddingRepositoryTest")
}
