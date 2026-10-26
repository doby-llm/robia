package com.gusanitolabs.robia.data.local

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class Migration14To15Test {
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        createV14Schema()
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrationCreatesTheDismissedPairsTableWithItsKey() {
        runMigration()

        val columns = columns("dismissed_duplicate_pairs")
        assertEquals(
            listOf(
                Column("garment_a", "TEXT", notNull = true, primaryKeyIndex = 1),
                Column("garment_b", "TEXT", notNull = true, primaryKeyIndex = 2),
                Column("dismissed_at", "INTEGER", notNull = true, primaryKeyIndex = 0),
            ),
            columns,
        )
    }

    /** The statements are the exact ones the app runs: the table is created once, a second run leaves it alone. */
    @Test
    fun migrationCanRunAgainOnATableThatExists() {
        runMigration()
        execute("INSERT INTO dismissed_duplicate_pairs VALUES ('a', 'b', 1)")

        runMigration()

        assertEquals(listOf("a" to "b"), query("SELECT garment_a, garment_b FROM dismissed_duplicate_pairs") { rs -> rs.getString(1) to rs.getString(2) })
    }

    /** The import union: the insert ignores a pair the phone already holds, so the first dismissal time is kept. */
    @Test
    fun insertOrIgnoreKeepsTheFirstDismissalOfAPair() {
        runMigration()

        execute("INSERT OR IGNORE INTO dismissed_duplicate_pairs VALUES ('a', 'b', 10)")
        execute("INSERT OR IGNORE INTO dismissed_duplicate_pairs VALUES ('a', 'b', 20)")
        execute("INSERT OR IGNORE INTO dismissed_duplicate_pairs VALUES ('a', 'c', 30)")

        assertEquals(
            listOf(Triple("a", "b", 10L), Triple("a", "c", 30L)),
            query("SELECT garment_a, garment_b, dismissed_at FROM dismissed_duplicate_pairs ORDER BY garment_b") { rs ->
                Triple(rs.getString(1), rs.getString(2), rs.getLong(3))
            },
        )
    }

    @Test
    fun aPairCannotBeStoredTwiceAsAPlainInsert() {
        runMigration()
        execute("INSERT INTO dismissed_duplicate_pairs VALUES ('a', 'b', 10)")

        val error = runCatching { execute("INSERT INTO dismissed_duplicate_pairs VALUES ('a', 'b', 20)") }.exceptionOrNull()

        assertTrue(error != null)
    }

    private fun createV14Schema() {
        val schema = JSONObject(File("schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/14.json").readText())
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

    private fun runMigration() {
        MIGRATION_14_15_SQL.forEach(::execute)
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
                primaryKeyIndex = rs.getInt("pk"),
            )
        }

    private data class Column(val name: String, val type: String, val notNull: Boolean, val primaryKeyIndex: Int)
}
