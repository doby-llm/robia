package com.gusanitolabs.robia.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Explicit Retry and Keep-without-photo on sqlite-jdbc. Room does not run in JVM tests, so each statement is read
 * from Dao.kt and executed as written; only the named parameters are bound to literals.
 */
class NeedsUserActionDaoTest {
    @Test
    fun retryReleasesOnlyUnguardedRowsParkedForTheRequestedReason() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createClothingItems(connection)
            insertGarment(connection, id = "drive", failure = "drive_full")
            insertGarment(connection, id = "photo", failure = "photo_upload_failed")
            insertGarment(connection, id = "guarded", failure = "drive_full", guarded = 1)
            insertGarment(connection, id = "other", failure = "other_reason")
            insertGarment(connection, id = "running", status = "Running", failure = "drive_full")

            assertEquals(1, execute(connection, statement("releaseNeedsUserActionToQueued", ":reasonCode" to "'drive_full'")))
            assertEquals(
                1,
                execute(connection, statement("releaseNeedsUserActionToQueued", ":reasonCode" to "'photo_upload_failed'")),
            )

            assertEquals(
                listOf(
                    "drive|Queued|null|0|null|2|null|null|null|null",
                    "guarded|NeedsUserAction|drive_full|1|null|2|5000|1000|null|null",
                    "other|NeedsUserAction|other_reason|0|null|2|5000|1000|null|null",
                    "photo|Queued|null|0|null|2|null|null|null|null",
                    "running|Running|drive_full|0|null|2|5000|1000|null|null",
                ),
                rows(connection),
            )
        }
    }

    @Test
    fun keepWithoutPhotoClearsOnlyTheGivenGuardedGarmentWithoutAPhoto() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createClothingItems(connection)
            insertGarment(connection, id = "target", failure = "photo_lost", guarded = 1, deadline = DEADLINE)
            insertGarment(connection, id = "guarded_no_photo", failure = "photo_lost", guarded = 1, deadline = DEADLINE)
            insertGarment(
                connection,
                id = "guarded_with_photo",
                failure = "photo_lost",
                guarded = 1,
                photoUri = "file:kept",
                deadline = DEADLINE,
            )
            insertGarment(connection, id = "unguarded", failure = "drive_full", guarded = 0)
            insertGarment(connection, id = "running", status = "Running", guarded = 1, deadline = DEADLINE)
            val before = rows(connection)

            assertEquals(0, keep(connection, "'missing'"))
            assertEquals(0, keep(connection, "''"))
            assertEquals(before, rows(connection))

            assertEquals(1, keep(connection, "'target'"))
            assertEquals(
                listOf(
                    "guarded_no_photo|NeedsUserAction|photo_lost|1|null|2|5000|1000|null|$DEADLINE",
                    "guarded_with_photo|NeedsUserAction|photo_lost|1|file:kept|2|5000|1000|null|$DEADLINE",
                    "running|Running|null|1|null|2|5000|1000|null|$DEADLINE",
                    "target|Queued|null|0|null|0|null|null|$NOW|null",
                    "unguarded|NeedsUserAction|drive_full|0|null|2|5000|1000|null|null",
                ),
                rows(connection),
            )
        }
    }

    private fun keep(connection: Connection, itemId: String): Int =
        execute(connection, statement("keepGarmentWithoutRestoredPhoto", ":itemId" to itemId, ":now" to NOW.toString()))

    private fun statement(method: String, vararg bindings: Pair<String, String>): String {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val query = dao.substringBefore("suspend fun $method(").substringAfterLast("@Query(\"").substringBefore("\")")
        return bindings.fold(query) { sql, (name, literal) -> sql.replace(name, literal) }
    }

    private fun createClothingItems(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE clothing_items (id TEXT NOT NULL, sync_status TEXT NOT NULL, sync_failure_message TEXT, " +
                    "photo_restore_guarded INTEGER NOT NULL, photo_uri TEXT, retry_attempt_count INTEGER NOT NULL, " +
                    "retry_after_epoch_millis INTEGER, sync_started_at_epoch_millis INTEGER, " +
                    "sync_dirty_at_epoch_millis INTEGER, photo_restore_retry_deadline_epoch_millis INTEGER)",
            )
        }
    }

    private fun insertGarment(
        connection: Connection,
        id: String,
        status: String = "NeedsUserAction",
        failure: String? = null,
        guarded: Int = 0,
        photoUri: String? = null,
        deadline: Long? = null,
    ) {
        val values = listOf(
            "'$id'",
            "'$status'",
            failure.sqlLiteral(),
            guarded.toString(),
            photoUri.sqlLiteral(),
            "2",
            "5000",
            "1000",
            "NULL",
            deadline.sqlLiteral(),
        ).joinToString(", ")
        connection.createStatement().use { statement ->
            statement.execute("INSERT INTO clothing_items VALUES ($values)")
        }
    }

    private fun execute(connection: Connection, sql: String): Int = connection.createStatement().use { statement ->
        statement.executeUpdate(sql)
    }

    private fun rows(connection: Connection): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT id, sync_status, sync_failure_message, photo_restore_guarded, photo_uri, retry_attempt_count, " +
                "retry_after_epoch_millis, sync_started_at_epoch_millis, sync_dirty_at_epoch_millis, " +
                "photo_restore_retry_deadline_epoch_millis FROM clothing_items ORDER BY id",
        ).use { resultSet ->
            generateSequence { if (resultSet.next()) resultSet else null }.map { row ->
                (1..COLUMN_COUNT).joinToString("|") { column -> row.getObject(column)?.toString() ?: "null" }
            }.toList()
        }
    }

    private fun Any?.sqlLiteral(): String = when (this) {
        null -> "NULL"
        is String -> "'$this'"
        else -> toString()
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }

    private companion object {
        const val NOW = 7_000L
        const val DEADLINE = 9_000L
        const val COLUMN_COUNT = 10
    }
}
