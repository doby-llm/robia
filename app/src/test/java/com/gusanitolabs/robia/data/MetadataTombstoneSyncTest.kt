package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.data.local.MetadataSyncTable
import com.gusanitolabs.robia.data.local.PendingMetadataSyncWorkEntity
import com.gusanitolabs.robia.data.local.metadataSyncTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Tombstones on sqlite-jdbc. A tombstone keeps the entity type of the row it deletes ("garment_tag:x" for tag x),
 * so claim, settle and release must hit sync_tombstones and never the live table. Room does not run in JVM tests:
 * each statement is read from Dao.kt and executed as written, and routing comes from metadataSyncTable() that Dao uses.
 */
class MetadataTombstoneSyncTest {
    @Test
    fun garmentTagTombstoneWithoutLiveRowIsClaimedSettledAndLeavesPending() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insertRow(connection, "sync_tombstones", id = "garment_tag:x", revision = REVISION)

            val work = pendingWork(connection, NOW).single()
            assertEquals(PendingMetadataSyncWorkEntity("garment_tag", "garment_tag:x", REVISION), work)
            assertEquals(MetadataSyncTable.Tombstone, work.metadataSyncTable())

            assertEquals(1, claim(connection, work))
            assertEquals("Running", status(connection, "sync_tombstones", "garment_tag:x"))

            assertEquals(1, settle(connection, work, status = "Synced"))
            assertEquals("Synced", status(connection, "sync_tombstones", "garment_tag:x"))
            assertEquals(emptyList<PendingMetadataSyncWorkEntity>(), pendingWork(connection, NOW))
        }
    }

    @Test
    fun failedTombstoneWaitsForItsRetryTimeAndThenIsPendingAgain() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insertRow(connection, "sync_tombstones", id = "main_color:c", revision = REVISION)
            val work = pendingWork(connection, NOW).single()

            assertEquals(1, claim(connection, work))
            assertEquals(1, failRetryable(connection, work, message = "boom", now = NOW))

            assertEquals("FailedRetryable", status(connection, "sync_tombstones", "main_color:c"))
            assertEquals(emptyList<PendingMetadataSyncWorkEntity>(), pendingWork(connection, NOW))
            assertEquals(listOf(work), pendingWork(connection, NOW + RETRY_DELAY))
        }
    }

    @Test
    fun releasedTombstoneReturnsToQueuedAndStaysPending() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insertRow(connection, "sync_tombstones", id = "tag_category:c", revision = REVISION)
            val work = pendingWork(connection, NOW).single()

            assertEquals(1, claim(connection, work))
            assertEquals(1, release(connection, work))

            assertEquals("Queued", status(connection, "sync_tombstones", "tag_category:c"))
            assertEquals(listOf(work), pendingWork(connection, NOW))
        }
    }

    @Test
    fun everyTombstoneKindIsSettledInSyncTombstonesOnly() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            val tombstoneIds = listOf("garment:g1", "tag_category:c2", "main_color:m3", "garment_tag:t4")
            tombstoneIds.forEachIndexed { index, id -> insertRow(connection, "sync_tombstones", id, REVISION + index) }

            val pending = pendingWork(connection, NOW)
            assertEquals(tombstoneIds.size, pending.size)
            pending.forEach { work ->
                assertEquals(MetadataSyncTable.Tombstone, work.metadataSyncTable())
                assertEquals(1, claim(connection, work))
                assertEquals(1, settle(connection, work, status = "Synced"))
            }
            assertEquals(emptyList<PendingMetadataSyncWorkEntity>(), pendingWork(connection, NOW))
            LIVE_TABLES.forEach { table -> assertEquals(0, count(connection, table)) }
        }
    }

    @Test
    fun liveTagAndColourRowsStillSettleInTheirOwnTables() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            insertRow(connection, "garment_tags", id = "abc", revision = 3)
            insertRow(connection, "main_colors", id = "c1", revision = 4)

            val pending = pendingWork(connection, NOW)
            assertEquals(
                listOf(
                    PendingMetadataSyncWorkEntity("garment_tag", "abc", 3),
                    PendingMetadataSyncWorkEntity("main_color", "c1", 4),
                ),
                pending,
            )
            pending.forEach { work ->
                assertEquals(1, claim(connection, work))
                assertEquals(1, settle(connection, work, status = "Synced"))
            }
            assertEquals("Synced", status(connection, "garment_tags", "abc"))
            assertEquals("Synced", status(connection, "main_colors", "c1"))
            assertEquals(0, count(connection, "sync_tombstones"))
        }
    }

    @Test
    fun routingKeepsTombstonesApartFromLiveRowsOfTheSameEntityType() {
        val routes = mapOf(
            PendingMetadataSyncWorkEntity("tag_category", "tag_category:c", 1) to MetadataSyncTable.Tombstone,
            PendingMetadataSyncWorkEntity("garment_tag", "garment_tag:x", 1) to MetadataSyncTable.Tombstone,
            PendingMetadataSyncWorkEntity("main_color", "main_color:x", 1) to MetadataSyncTable.Tombstone,
            PendingMetadataSyncWorkEntity("garment", "garment:x", 1) to MetadataSyncTable.Tombstone,
            PendingMetadataSyncWorkEntity("garment_tag", "category-shorts", 1) to MetadataSyncTable.GarmentTag,
            PendingMetadataSyncWorkEntity("tag", "abc", 1) to MetadataSyncTable.GarmentTag,
            PendingMetadataSyncWorkEntity("category", "category", 1) to MetadataSyncTable.TagCategory,
            PendingMetadataSyncWorkEntity("color", "c1", 1) to MetadataSyncTable.MainColor,
        )
        routes.forEach { (work, table) -> assertEquals(work.id, table, work.metadataSyncTable()) }
    }

    @Test
    fun dispatchDoesNotBranchOnTheRawEntityType() {
        assertFalse(source(DAO_PATH).contains("when (work.entityType)"))
    }

    private fun claim(connection: Connection, work: PendingMetadataSyncWorkEntity): Int =
        update(connection, sqlOf(work.metadataSyncTable().claimMethod()), ":id" to quoted(work.id), ":revision" to work.revision, ":startedAtEpochMillis" to NOW)

    private fun settle(connection: Connection, work: PendingMetadataSyncWorkEntity, status: String): Int = update(
        connection,
        sqlOf(work.metadataSyncTable().settleMethod()),
        ":id" to quoted(work.id),
        ":revision" to work.revision,
        ":status" to quoted(status),
        ":syncedAtEpochMillis" to NOW,
        ":clearDirty" to 1,
        ":message" to "NULL",
        ":matchRevision" to 1,
    )

    private fun failRetryable(connection: Connection, work: PendingMetadataSyncWorkEntity, message: String, now: Long): Int = update(
        connection,
        sqlOf(work.metadataSyncTable().failMethod()),
        ":id" to quoted(work.id),
        ":revision" to work.revision,
        ":message" to quoted(message),
        ":now" to now,
    )

    private fun release(connection: Connection, work: PendingMetadataSyncWorkEntity): Int =
        update(connection, sqlOf(work.metadataSyncTable().releaseMethod()), ":id" to quoted(work.id), ":revision" to work.revision)

    private fun MetadataSyncTable.claimMethod(): String = when (this) {
        MetadataSyncTable.TagCategory -> "claimTagCategoryMetadataSync"
        MetadataSyncTable.GarmentTag -> "claimGarmentTagMetadataSync"
        MetadataSyncTable.MainColor -> "claimMainColorMetadataSync"
        MetadataSyncTable.Tombstone -> "claimTombstoneMetadataSync"
    }

    private fun MetadataSyncTable.settleMethod(): String = when (this) {
        MetadataSyncTable.TagCategory -> "updateTagCategorySyncStatus"
        MetadataSyncTable.GarmentTag -> "updateGarmentTagSyncStatus"
        MetadataSyncTable.MainColor -> "updateMainColorSyncStatus"
        MetadataSyncTable.Tombstone -> "updateTombstoneSyncStatus"
    }

    private fun MetadataSyncTable.failMethod(): String = when (this) {
        MetadataSyncTable.TagCategory -> "updateTagCategorySyncFailedRetryable"
        MetadataSyncTable.GarmentTag -> "updateGarmentTagSyncFailedRetryable"
        MetadataSyncTable.MainColor -> "updateMainColorSyncFailedRetryable"
        MetadataSyncTable.Tombstone -> "updateTombstoneSyncFailedRetryable"
    }

    private fun MetadataSyncTable.releaseMethod(): String = when (this) {
        MetadataSyncTable.TagCategory -> "releaseTagCategoryToQueued"
        MetadataSyncTable.GarmentTag -> "releaseGarmentTagToQueued"
        MetadataSyncTable.MainColor -> "releaseMainColorToQueued"
        MetadataSyncTable.Tombstone -> "releaseTombstoneToQueued"
    }

    private fun pendingWork(connection: Connection, now: Long): List<PendingMetadataSyncWorkEntity> =
        connection.createStatement().use { statement ->
            statement.executeQuery(bound(sqlOf("pendingMetadataSyncWork"), ":now" to now)).use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet else null }.map { row ->
                    PendingMetadataSyncWorkEntity(row.getString(1), row.getString(2), row.getLong(3))
                }.toList()
            }
        }

    private fun update(connection: Connection, sql: String, vararg bindings: Pair<String, Any>): Int =
        connection.createStatement().use { statement -> statement.executeUpdate(bound(sql, *bindings)) }

    private fun bound(sql: String, vararg bindings: Pair<String, Any>): String =
        bindings.fold(sql) { acc, (name, value) -> acc.replace(name, value.toString()) }

    private fun status(connection: Connection, table: String, id: String): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT sync_status FROM $table WHERE id = ${quoted(id)}").use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }

    private fun count(connection: Connection, table: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }

    private fun insertRow(connection: Connection, table: String, id: String, revision: Long) {
        val sql = if (table == "sync_tombstones") {
            "INSERT INTO sync_tombstones (id, entity_type, entity_id, deleted_at_epoch_millis, revision, sync_status) " +
                "VALUES (${quoted(id)}, ${quoted(id.substringBefore(':'))}, ${quoted(id.substringAfter(':'))}, 0, $revision, 'Queued')"
        } else {
            "INSERT INTO $table (id, sync_revision, sync_status) VALUES (${quoted(id)}, $revision, 'Queued')"
        }
        connection.createStatement().use { statement -> statement.execute(sql) }
    }

    private fun createTables(connection: Connection) {
        connection.createStatement().use { statement ->
            LIVE_TABLES.forEach { table ->
                statement.execute(
                    "CREATE TABLE $table (id TEXT PRIMARY KEY, sync_revision INTEGER NOT NULL, sync_status TEXT NOT NULL, " +
                        "sync_started_at_epoch_millis INTEGER, retry_attempt_count INTEGER NOT NULL DEFAULT 0, " +
                        "retry_after_epoch_millis INTEGER, sync_failure_message TEXT, last_synced_at_epoch_millis INTEGER, " +
                        "sync_dirty_at_epoch_millis INTEGER)",
                )
            }
            statement.execute(
                "CREATE TABLE sync_tombstones (id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, " +
                    "deleted_at_epoch_millis INTEGER NOT NULL, revision INTEGER NOT NULL, sync_status TEXT NOT NULL, " +
                    "sync_started_at_epoch_millis INTEGER, retry_attempt_count INTEGER NOT NULL DEFAULT 0, " +
                    "retry_after_epoch_millis INTEGER, sync_failure_message TEXT, last_synced_at_epoch_millis INTEGER, " +
                    "sync_dirty_at_epoch_millis INTEGER)",
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

    private fun quoted(value: String): String = "'$value'"

    private companion object {
        const val DAO_PATH = "app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt"
        const val NOW = 1_000_000L
        const val REVISION = 7L
        const val RETRY_DELAY = 60_000L
        val LIVE_TABLES = listOf("tag_categories", "garment_tags", "main_colors")
    }
}
