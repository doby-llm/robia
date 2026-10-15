package com.gusanitolabs.robia.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Stale-claim recovery on sqlite-jdbc. Room does not run in JVM tests, so each recovery statement is read from
 * Dao.kt and executed as written; only the named parameters are bound to literals.
 */
class StaleClaimRecoveryTest {
    @Test
    fun interruptedClaimsReturnToQueuedWithoutCountingAnAttemptOrParking() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            TABLES.forEach { table -> createTable(connection, table) }
            TABLES.forEach { table ->
                // A guarded photo used to park as NeedsUserAction on recovery; it must now requeue like any other claim.
                insertRow(connection, table, id = "stale", startedAt = STALE_STARTED_AT, guarded = 1)
                insertRow(connection, table, id = "fresh", startedAt = FRESH_STARTED_AT, guarded = 0)
            }

            RECOVERY_METHODS.forEach { method ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        recoveryStatement(method)
                            .replace(":staleBeforeEpochMillis", STALE_BEFORE.toString()),
                    )
                }
            }

            TABLES.forEach { table ->
                assertEquals(
                    listOf(
                        "fresh|Running|2|5000|$FRESH_STARTED_AT",
                        "stale|Queued|2|null|null",
                    ),
                    rows(connection, table),
                )
            }
        }
    }

    @Test
    fun cycleStartRecoveryRequeuesUnguardedClaimsAndLeavesGuardedOnesRunning() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTable(connection, "clothing_items")
            insertRow(connection, "clothing_items", id = "guarded", startedAt = STALE_STARTED_AT, guarded = 1)
            insertRow(connection, "clothing_items", id = "unguarded", startedAt = STALE_STARTED_AT, guarded = 0)

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    recoveryStatement("recoverStaleUnguardedGarmentSyncWork")
                        .replace(":staleBeforeEpochMillis", STALE_BEFORE.toString()),
                )
            }

            assertEquals(
                listOf(
                    "guarded|Running|2|5000|$STALE_STARTED_AT",
                    "unguarded|Queued|2|null|null",
                ),
                rows(connection, "clothing_items"),
            )
        }
    }

    private fun recoveryStatement(method: String): String {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        return dao.substringBefore("suspend fun $method(").substringAfterLast("@Query(\"").substringBefore("\")")
    }

    private fun createTable(connection: Connection, table: String) {
        val guardedColumn = if (table == "clothing_items") ", photo_restore_guarded INTEGER NOT NULL" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE $table (id TEXT NOT NULL, sync_status TEXT NOT NULL, retry_attempt_count INTEGER NOT NULL, " +
                    "retry_after_epoch_millis INTEGER, sync_started_at_epoch_millis INTEGER$guardedColumn)",
            )
        }
    }

    private fun insertRow(connection: Connection, table: String, id: String, startedAt: Long, guarded: Int) {
        val guardedColumn = if (table == "clothing_items") ", photo_restore_guarded" else ""
        val guardedValue = if (table == "clothing_items") ", $guarded" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO $table (id, sync_status, retry_attempt_count, retry_after_epoch_millis, " +
                    "sync_started_at_epoch_millis$guardedColumn) VALUES ('$id', 'Running', 2, 5000, $startedAt$guardedValue)",
            )
        }
    }

    private fun rows(connection: Connection, table: String): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT id, sync_status, retry_attempt_count, retry_after_epoch_millis, sync_started_at_epoch_millis " +
                "FROM $table ORDER BY id",
        ).use { resultSet ->
            generateSequence { if (resultSet.next()) resultSet else null }.map { row ->
                listOf(
                    row.getString(1),
                    row.getString(2),
                    row.getInt(3),
                    row.getObject(4)?.toString() ?: "null",
                    row.getObject(5)?.toString() ?: "null",
                ).joinToString("|")
            }.toList()
        }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }

    private companion object {
        const val STALE_STARTED_AT = 1_000L
        const val FRESH_STARTED_AT = 9_000L
        const val STALE_BEFORE = 5_000L
        val TABLES = listOf("clothing_items", "tag_categories", "garment_tags", "main_colors", "sync_tombstones")
        val RECOVERY_METHODS = listOf(
            "recoverStaleGarmentSyncWork",
            "recoverStaleTagCategorySyncWork",
            "recoverStaleGarmentTagSyncWork",
            "recoverStaleMainColorSyncWork",
            "recoverStaleTombstoneSyncWork",
        )
    }
}
