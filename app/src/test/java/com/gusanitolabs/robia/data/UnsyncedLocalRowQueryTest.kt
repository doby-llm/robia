package com.gusanitolabs.robia.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * [WardrobeDao.hasAnyUnsyncedLocalRow] on sqlite-jdbc. Room does not run in JVM tests, so the statement is read from Dao.kt
 * and executed as written: every state a row can be in must count, and only a fully synced wardrobe must not.
 */
class UnsyncedLocalRowQueryTest {
    @Test
    fun aFullySyncedWardrobeHasNoUnsyncedRow() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insert(connection, "clothing_items", "a", "Synced")
            insert(connection, "tag_categories", "c", "Synced")
            insert(connection, "garment_tags", "t", "Synced")
            insert(connection, "main_colors", "m", "Synced")
            insertTombstone(connection, "garment:x", "Synced")

            assertFalse(hasUnsynced(connection))
        }
    }

    @Test
    fun everyNonSyncedStateInEveryTableCounts() {
        for (table in TABLES) {
            for (status in STATUSES_NOT_AT_DRIVE) {
                DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
                    createTables(connection)
                    if (table == "sync_tombstones") insertTombstone(connection, "garment:row", status) else insert(connection, table, "row", status)
                    assertTrue("$table/$status", hasUnsynced(connection))
                }
            }
        }
    }

    @Test
    fun aTombstoneNotAtDriveCountsWhateverItsEntityType() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insertTombstone(connection, "garment:x", "Queued")

            assertTrue(hasUnsynced(connection))
        }
    }

    @Test
    fun aFailedRowInsideItsBackoffStillCounts() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insert(connection, "clothing_items", "a", "FailedRetryable", retryAfter = Long.MAX_VALUE)

            assertTrue(hasUnsynced(connection))
        }
    }

    private fun hasUnsynced(connection: Connection): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery(sqlOf("hasAnyUnsyncedLocalRow")).use { resultSet ->
                resultSet.next()
                resultSet.getInt(1) == 1
            }
        }

    private fun insert(connection: Connection, table: String, id: String, status: String, retryAfter: Long? = null) {
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO $table (id, sync_status, retry_after_epoch_millis) VALUES ('$id', '$status', ${retryAfter ?: "NULL"})",
            )
        }
    }

    private fun insertTombstone(connection: Connection, id: String, status: String) {
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO sync_tombstones (id, entity_type, entity_id, deleted_at_epoch_millis, revision, sync_status) " +
                    "VALUES ('$id', '${id.substringBefore(':')}', '${id.substringAfter(':')}', 0, 1, '$status')",
            )
        }
    }

    private fun createTables(connection: Connection) {
        connection.createStatement().use { statement ->
            TABLES.filterNot { it == "sync_tombstones" }.forEach { table ->
                statement.execute(
                    "CREATE TABLE $table (id TEXT PRIMARY KEY, sync_status TEXT NOT NULL, retry_after_epoch_millis INTEGER)",
                )
            }
            statement.execute(
                "CREATE TABLE sync_tombstones (id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, " +
                    "deleted_at_epoch_millis INTEGER NOT NULL, revision INTEGER NOT NULL, sync_status TEXT NOT NULL, " +
                    "retry_after_epoch_millis INTEGER)",
            )
        }
    }

    /** The first @Query before `suspend fun <method>(`: single-line or triple-quoted. */
    private fun sqlOf(method: String): String {
        val annotation = source(DAO_PATH).substringBefore("suspend fun $method(").substringAfterLast("@Query(")
        return if (annotation.trimStart().startsWith("\"\"\"")) {
            annotation.substringAfter("\"\"\"").substringBefore("\"\"\"").trim()
        } else {
            annotation.substringAfter("\"").substringBefore("\"")
        }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }

    private companion object {
        const val DAO_PATH = "app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt"
        val TABLES = listOf("clothing_items", "tag_categories", "garment_tags", "main_colors", "sync_tombstones")
        val STATUSES_NOT_AT_DRIVE = listOf("LocalOnly", "Dirty", "Queued", "Running", "FailedRetryable", "NeedsUserAction", "AuthBlocked")
    }
}
