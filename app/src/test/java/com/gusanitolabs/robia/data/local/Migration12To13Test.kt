package com.gusanitolabs.robia.data.local

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class Migration12To13Test {
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV12Schema()
        seedV12Rows()
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrationCreatesEmbeddingTable() {
        runMigration()

        assertEquals(
            listOf(
                "garment_id", "kind", "model_id", "recipe_id", "dim", "vector", "input_hash",
                "computed_at_epoch_millis", "duration_millis", "pending_upload",
            ),
            columns("garment_embeddings").map { column -> column.name },
        )
        assertEquals(listOf("garment_id", "kind"), primaryKeyColumns("garment_embeddings"))
    }

    @Test
    fun migrationAddsTagColumnsWithDefaults() {
        runMigration()

        val description = columns("garment_tags").single { column -> column.name == "description" }
        val suggestOccasions = columns("garment_tags").single { column -> column.name == "suggest_occasions" }
        assertEquals(false, description.notNull)
        assertNull(description.defaultValue)
        assertEquals(true, suggestOccasions.notNull)
        assertEquals("1", suggestOccasions.defaultValue)
    }

    @Test
    fun migrationTurnsOffOccasionsOnlyForAccessoriesAndJewelry() {
        runMigration()

        assertEquals(
            mapOf(
                "category-accessories" to 0,
                "category-jewelry" to 0,
                "category-shirts" to 1,
            ),
            query("SELECT id, suggest_occasions FROM garment_tags ORDER BY id") { rs ->
                rs.getString(1) to rs.getInt(2)
            }.toMap(),
        )
    }

    @Test
    fun migrationResetsFailedAndNonGuardedUserActionGarments() {
        runMigration()

        assertEquals(
            mapOf(
                "failed" to "Queued",
                "user-action" to "Queued",
                "guarded" to "NeedsUserAction",
                "synced" to "Synced",
            ),
            query("SELECT id, sync_status FROM clothing_items ORDER BY id") { rs -> rs.getString(1) to rs.getString(2) }.toMap(),
        )
        assertEquals(
            listOf(0, 0),
            query("SELECT retry_attempt_count FROM clothing_items WHERE id IN ('failed', 'user-action') ORDER BY id") { rs -> rs.getInt(1) },
        )
    }

    @Test
    fun migrationResetsTaxonomyAndTombstoneStatuses() {
        runMigration()

        assertEquals(
            listOf("Queued", "Queued", "Queued", "Synced"),
            listOf(
                "SELECT sync_status FROM main_colors WHERE id = 'black'",
                "SELECT sync_status FROM sync_tombstones WHERE id = 'garment:old'",
                "SELECT sync_status FROM tag_categories WHERE id = 'category'",
                "SELECT sync_status FROM garment_tags WHERE id = 'category-shirts'",
            ).map { sql -> query(sql) { rs -> rs.getString(1) }.single() },
        )
    }

    private fun createV12Schema() {
        val schema = JSONObject(File("schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/12.json").readText())
            .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val tableName = entity.getString("tableName")
            execute(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
            entity.optJSONArray("indices")?.let { indices ->
                for (indexPosition in 0 until indices.length()) {
                    execute(indices.getJSONObject(indexPosition).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
        }
    }

    private fun seedV12Rows() {
        execute(
            """
            INSERT INTO tag_categories (id, name, sort_order, is_system, sync_status, sync_revision, retry_attempt_count)
            VALUES ('category', 'Category', 10, 1, 'NeedsUserAction', 0, 0)
            """,
        )
        execute(
            """
            INSERT INTO garment_tags (id, category_id, name, sort_order, is_system, sync_status, sync_revision, retry_attempt_count)
            VALUES
                ('category-accessories', 'category', 'Accessories', 180, 0, 'Synced', 0, 0),
                ('category-jewelry', 'category', 'Jewelry', 170, 0, 'Synced', 0, 0),
                ('category-shirts', 'category', 'Shirts', 150, 0, 'Synced', 0, 0)
            """,
        )
        execute(
            """
            INSERT INTO main_colors (id, name, hex, sort_order, is_default, sync_status, sync_revision, retry_attempt_count)
            VALUES ('black', 'Black', '#1F1F1F', 10, 1, 'FailedRetryable', 0, 3)
            """,
        )
        execute(
            """
            INSERT INTO sync_tombstones (id, entity_type, entity_id, deleted_at_epoch_millis, revision, sync_status, retry_attempt_count, retry_after_epoch_millis)
            VALUES ('garment:old', 'garment', 'old', 1, 0, 'NeedsUserAction', 2, 99)
            """,
        )
        execute(
            """
            INSERT INTO clothing_items (
                id, name, notes, photo_uri, is_favorite, is_archived, created_at_epoch_millis, updated_at_epoch_millis,
                sync_status, sync_revision, retry_attempt_count, retry_after_epoch_millis, photo_restore_guarded
            ) VALUES
                ('failed', 'Failed', '', 'content://failed', 0, 0, 1, 1, 'FailedRetryable', 1, 3, 999, 0),
                ('user-action', 'Plain', '', 'content://plain', 0, 0, 1, 1, 'NeedsUserAction', 1, 5, 999, 0),
                ('guarded', 'Guarded', '', NULL, 0, 0, 1, 1, 'NeedsUserAction', 1, 5, 999, 1),
                ('synced', 'Synced', '', 'content://synced', 0, 0, 1, 1, 'Synced', 1, 0, NULL, 0)
            """,
        )
    }

    private fun runMigration() {
        MIGRATION_12_13_SQL.forEach(::execute)
    }

    private fun execute(sql: String) {
        connection.createStatement().use { statement -> statement.execute(sql.trimIndent()) }
    }

    private fun <T> query(sql: String, mapRow: (java.sql.ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(mapRow(rs)) }
            }
        }

    private fun columns(table: String): List<Column> =
        query("PRAGMA table_info($table)") { rs ->
            Column(
                name = rs.getString("name"),
                notNull = rs.getInt("notnull") == 1,
                defaultValue = rs.getString("dflt_value"),
            )
        }

    private fun primaryKeyColumns(table: String): List<String> =
        query("PRAGMA table_info($table)") { rs -> rs.getInt("pk") to rs.getString("name") }
            .filter { (position, _) -> position > 0 }
            .sortedBy { (position, _) -> position }
            .map { (_, name) -> name }

    private data class Column(val name: String, val notNull: Boolean, val defaultValue: String?)
}
