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

    @Test
    fun deadGuardedClaimParksAsNeedsUserActionAndRetryCanClaimItAgain() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE clothing_items (id TEXT NOT NULL, sync_status TEXT NOT NULL, sync_failure_message TEXT, " +
                        "sync_revision INTEGER NOT NULL, retry_attempt_count INTEGER NOT NULL, retry_after_epoch_millis INTEGER, " +
                        "sync_started_at_epoch_millis INTEGER, photo_restore_guarded INTEGER NOT NULL, " +
                        "photo_restore_retry_deadline_epoch_millis INTEGER)",
                )
            }
            // dead: claimed before the threshold by a process that died. live: claimed after it. plain: unguarded, not this query's concern.
            insertGuardedRow(connection, id = "dead", status = "Running", startedAt = STALE_STARTED_AT, guarded = 1)
            insertGuardedRow(connection, id = "live", status = "Running", startedAt = FRESH_STARTED_AT, guarded = 1)
            insertGuardedRow(connection, id = "plain", status = "Running", startedAt = STALE_STARTED_AT, guarded = 0)

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    recoveryStatement("recoverStaleGuardedPhotoSyncWork")
                        .replace(":staleBeforeEpochMillis", STALE_BEFORE.toString()),
                )
            }
            assertEquals(
                listOf(
                    "dead|NeedsUserAction|photo_restore_interrupted|1|1|null|null",
                    "live|Running|null|1|1|null|$FRESH_STARTED_AT",
                    "plain|Running|null|1|1|null|$STALE_STARTED_AT",
                ),
                guardedRows(connection),
            )

            // The retry claim takes the parked row again: it is eligible while its attempts and deadline allow.
            val claimed = connection.createStatement().use { statement ->
                statement.executeUpdate(
                    recoveryStatement("markGarmentPhotoRestoreRetrying")
                        .replace(":itemId", "'dead'")
                        .replace(":revision", "1")
                        .replace(":startedAtEpochMillis", RETRY_STARTED_AT.toString())
                        .replace(":now", RETRY_STARTED_AT.toString()),
                )
            }
            assertEquals(1, claimed)
            assertEquals("dead|Running|photo_restore_interrupted|1|1|null|$RETRY_STARTED_AT", guardedRows(connection).first())
        }
    }

    private fun insertGuardedRow(connection: Connection, id: String, status: String, startedAt: Long, guarded: Int) {
        connection.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO clothing_items VALUES ('$id', '$status', NULL, 1, 1, NULL, $startedAt, $guarded, $DEADLINE)",
            )
        }
    }

    private fun guardedRows(connection: Connection): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery(
            "SELECT id, sync_status, sync_failure_message, sync_revision, retry_attempt_count, retry_after_epoch_millis, " +
                "sync_started_at_epoch_millis FROM clothing_items ORDER BY id",
        ).use { resultSet ->
            generateSequence { if (resultSet.next()) resultSet else null }.map { row ->
                (1..COLUMN_COUNT).joinToString("|") { column -> row.getObject(column)?.toString() ?: "null" }
            }.toList()
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
        const val RETRY_STARTED_AT = 8_000L
        const val DEADLINE = 20_000L
        const val COLUMN_COUNT = 7
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
