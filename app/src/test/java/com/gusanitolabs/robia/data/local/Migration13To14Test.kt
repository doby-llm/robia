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

class Migration13To14Test {
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV13Schema()
        seedV13Rows()
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrationAddsNullableOriginalColumns() {
        runMigration()

        val originalUri = columns("clothing_items").single { column -> column.name == "original_uri" }
        val originalRecipe = columns("clothing_items").single { column -> column.name == "original_recipe" }
        assertEquals("TEXT", originalUri.type)
        assertEquals(false, originalUri.notNull)
        assertNull(originalUri.defaultValue)
        assertEquals("TEXT", originalRecipe.type)
        assertEquals(false, originalRecipe.notNull)
        assertNull(originalRecipe.defaultValue)
    }

    @Test
    fun migrationKeepsExistingRowsWithNullOriginals() {
        runMigration()

        assertEquals(
            listOf("with-photo" to "content://with-photo", "without-photo" to null),
            query("SELECT id, photo_uri FROM clothing_items ORDER BY id") { rs -> rs.getString(1) to rs.getString(2) },
        )
        assertEquals(
            listOf(
                Triple("with-photo", null, null),
                Triple("without-photo", null, null),
            ),
            query("SELECT id, original_uri, original_recipe FROM clothing_items ORDER BY id") { rs ->
                Triple(rs.getString(1), rs.getString(2), rs.getString(3))
            },
        )
    }

    @Test
    fun migratedColumnsStoreOriginalAndRecipeJson() {
        runMigration()
        val recipeJson = """{"engine":"mlkit","version":1}"""

        execute(
            """
            UPDATE clothing_items
            SET original_uri = 'content://original', original_recipe = '$recipeJson'
            WHERE id = 'with-photo'
            """,
        )

        assertEquals(
            listOf("content://original" to recipeJson),
            query("SELECT original_uri, original_recipe FROM clothing_items WHERE id = 'with-photo'") { rs ->
                rs.getString(1) to rs.getString(2)
            },
        )
    }

    private fun createV13Schema() {
        val schema = JSONObject(File("schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/13.json").readText())
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

    private fun seedV13Rows() {
        execute(
            """
            INSERT INTO clothing_items (
                id, name, notes, photo_uri, is_favorite, is_archived, created_at_epoch_millis, updated_at_epoch_millis,
                sync_status, sync_revision, retry_attempt_count, photo_restore_guarded
            ) VALUES
                ('with-photo', 'Shirt', '', 'content://with-photo', 0, 0, 1, 1, 'Synced', 1, 0, 0),
                ('without-photo', 'Plain', '', NULL, 0, 0, 1, 1, 'Synced', 1, 0, 0)
            """,
        )
    }

    private fun runMigration() {
        MIGRATION_13_14_SQL.forEach(::execute)
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
                type = rs.getString("type"),
                notNull = rs.getInt("notnull") == 1,
                defaultValue = rs.getString("dflt_value"),
            )
        }

    private data class Column(val name: String, val type: String, val notNull: Boolean, val defaultValue: String?)
}
