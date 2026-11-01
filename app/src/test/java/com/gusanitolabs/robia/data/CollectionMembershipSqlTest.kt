package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.planTagMembership
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * `TagDao.setTagMembership` on sqlite-jdbc. Room does not run in JVM tests, so the transaction body is replayed here: the SELECT,
 * DELETE, UPDATE and the link check are the Dao.kt statements read as written ([daoStatement]), and the plan is the production
 * [planTagMembership]. `insertItemTagRefs` is Room `@Insert(IGNORE)`, so its insert is written here as the same IGNORE insert.
 */
class CollectionMembershipSqlTest {
    @Test
    fun membershipChangeTouchesOnlyTheChosenTagAndOnlyChangedLiveGarments() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            seedFixture(connection)

            // G3 (live, not a member) is added; G1 (live member) is removed; G2 (archived member) and G4 (archived, not a member) are dropped.
            val changed = setTagMembership(connection, tagId = "collection-a", add = setOf("G3", "G4"), remove = setOf("G1", "G2"), now = 1000L)

            assertEquals(2, changed)
            assertEquals(listOf("category-shorts", "collection-b", "occasion-formal"), tagIdsOf(connection, "G1"))
            assertEquals(listOf("collection-a"), tagIdsOf(connection, "G2"))
            assertEquals(listOf("category-jackets", "collection-a"), tagIdsOf(connection, "G3"))
            assertEquals(listOf("category-tops"), tagIdsOf(connection, "G4"))

            // Changed garments are queued with the new revision; photo-guard and retry columns are not touched (G1 keeps its guard).
            assertEquals(listOf("Queued", "1000", "1000", null, "500", "1", "777", "888", "2"), stateOf(connection, "G1"))
            assertEquals(listOf("Queued", "1000", "1000", null, "500", "0", null, null, "0"), stateOf(connection, "G3"))
            // Archived garments and unchanged garments are not written at all.
            assertEquals(listOf("Synced", "10", null, null, "500", "0", null, null, "0"), stateOf(connection, "G2"))
            assertEquals(listOf("Synced", "10", null, null, "500", "0", null, null, "0"), stateOf(connection, "G4"))
        }
    }

    @Test
    fun aChangeThatAltersNothingWritesNothing() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            seedFixture(connection)

            // G1 is already a member and G3 is not, so neither link changes and nothing is queued.
            val alreadyApplied = setTagMembership(connection, tagId = "collection-a", add = setOf("G1"), remove = setOf("G3"), now = 2000L)
            val empty = setTagMembership(connection, tagId = "collection-a", add = emptySet(), remove = emptySet(), now = 2000L)

            assertEquals(0, alreadyApplied)
            assertEquals(0, empty)
            assertEquals(listOf("FailedRetryable", "10", null, "old failure", "500", "1", "777", "888", "2"), stateOf(connection, "G1"))
            assertEquals(listOf("category-shorts", "collection-a", "collection-b", "occasion-formal"), tagIdsOf(connection, "G1"))
            assertEquals(listOf("category-jackets"), tagIdsOf(connection, "G3"))
            assertEquals(listOf("Synced", "10", null, null, "500", "0", null, null, "0"), stateOf(connection, "G3"))
        }
    }

    /**
     * The `setTagMembership` body on sqlite-jdbc: members read with `itemRefsForTag`, add/remove restricted to live garments by
     * `activeItemIds`, the plan, then the insert, the delete and the touch. Empty lists skip their statement, as in Dao.kt.
     */
    private fun setTagMembership(connection: Connection, tagId: String, add: Set<String>, remove: Set<String>, now: Long): Int {
        if (add.isEmpty() && remove.isEmpty()) return 0
        val members = query(connection, daoStatement("itemRefsForTag", ":tagId" to quoted(tagId))) { row ->
            row.getString("clothing_item_id")
        }.toSet()
        val plan = planTagMembership(
            currentMembers = members,
            add = activeItemIds(connection, add),
            remove = activeItemIds(connection, remove),
        )
        plan.add.forEach { itemId -> insertItemTagRef(connection, itemId, tagId) }
        if (plan.remove.isNotEmpty()) {
            execute(connection, daoStatement("deleteTagRefsForItems", ":tagId" to quoted(tagId), ":itemIds" to quotedList(plan.remove)))
        }
        if (plan.changed.isNotEmpty()) {
            execute(connection, daoStatement("touchItemsForMembershipSync", ":itemIds" to quotedList(plan.changed), ":now" to now.toString()))
        }
        return plan.changed.size
    }

    private fun activeItemIds(connection: Connection, ids: Set<String>): Set<String> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            query(connection, daoStatement("activeItemIds", ":ids" to quotedList(ids.toList()))) { row -> row.getString(1) }.toSet()
        }

    /** `insertItemTagRefs` is Room `@Insert(IGNORE)`, so the insert is written here as the same IGNORE insert. */
    private fun insertItemTagRef(connection: Connection, itemId: String, tagId: String) {
        connection.prepareStatement("INSERT OR IGNORE INTO clothing_item_tags (clothing_item_id, tag_id) VALUES (?, ?)").use { statement ->
            statement.setString(1, itemId)
            statement.setString(2, tagId)
            statement.executeUpdate()
        }
    }

    private fun tagIdsOf(connection: Connection, garmentId: String): List<String> =
        query(connection, "SELECT tag_id FROM clothing_item_tags WHERE clothing_item_id = '$garmentId' ORDER BY tag_id") { row ->
            row.getString(1)
        }

    /** sync_status, sync_revision, sync_dirty_at, sync_failure_message, updated_at, photo guard, guard deadline, retry after, attempts. */
    private fun stateOf(connection: Connection, garmentId: String): List<String?> =
        query(
            connection,
            "SELECT sync_status, sync_revision, sync_dirty_at_epoch_millis, sync_failure_message, updated_at_epoch_millis, " +
                "photo_restore_guarded, photo_restore_retry_deadline_epoch_millis, retry_after_epoch_millis, retry_attempt_count " +
                "FROM clothing_items WHERE id = '$garmentId'",
        ) { row -> (1..9).map { column -> row.getString(column) } }.single()

    /**
     * G1: category, occasion, collection A and B; a failed sync with a photo guard and retry state. G2: archived member of A.
     * G3: live, not a member. G4: archived, not a member.
     */
    private fun seedFixture(connection: Connection) {
        execute(
            connection,
            "INSERT INTO clothing_items (id, is_archived, sync_status, sync_revision, sync_dirty_at_epoch_millis, sync_failure_message, " +
                "updated_at_epoch_millis, photo_restore_guarded, photo_restore_retry_deadline_epoch_millis, retry_after_epoch_millis, retry_attempt_count) " +
                "VALUES ('G1', 0, 'FailedRetryable', 10, NULL, 'old failure', 500, 1, 777, 888, 2)",
        )
        execute(connection, "INSERT INTO clothing_items (id, is_archived, sync_status, sync_revision, updated_at_epoch_millis) VALUES ('G2', 1, 'Synced', 10, 500)")
        execute(connection, "INSERT INTO clothing_items (id, is_archived, sync_status, sync_revision, updated_at_epoch_millis) VALUES ('G3', 0, 'Synced', 10, 500)")
        execute(connection, "INSERT INTO clothing_items (id, is_archived, sync_status, sync_revision, updated_at_epoch_millis) VALUES ('G4', 1, 'Synced', 10, 500)")
        listOf(
            "G1" to "category-shorts",
            "G1" to "occasion-formal",
            "G1" to "collection-a",
            "G1" to "collection-b",
            "G2" to "collection-a",
            "G3" to "category-jackets",
            "G4" to "category-tops",
        ).forEach { (itemId, tagId) -> insertItemTagRef(connection, itemId, tagId) }
    }

    /**
     * The `@Query` SQL of a [DAO_PATH] method, read as written (raw `"""` or one-line), with named parameters bound to
     * literals. Same approach as `DefaultCategorySeedingTest.daoStatement`.
     */
    private fun daoStatement(method: String, vararg bindings: Pair<String, String>): String {
        val annotation = source(DAO_PATH).substringBefore("suspend fun $method(").substringAfterLast("@Query(").trimStart()
        val query = if (annotation.startsWith(RAW_QUOTES)) {
            annotation.removePrefix(RAW_QUOTES).substringBefore(RAW_QUOTES)
        } else {
            annotation.removePrefix("\"").substringBefore("\"")
        }
        return bindings.fold(query) { sql, (name, literal) -> sql.replace(name, literal) }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }

    private fun quoted(value: String): String = "'$value'"

    private fun quotedList(values: List<String>): String = values.joinToString(",") { value -> quoted(value) }

    private fun <T> query(connection: Connection, sql: String, map: (ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                generateSequence { if (rows.next()) rows else null }.map(map).toList()
            }
        }

    private fun execute(connection: Connection, sql: String) {
        connection.createStatement().use { statement -> statement.executeUpdate(sql) }
    }

    /** Only the columns the membership write reads or changes, with the constraints Room declares for them. */
    private fun createTables(connection: Connection) {
        execute(
            connection,
            "CREATE TABLE clothing_items (id TEXT PRIMARY KEY, is_archived INTEGER NOT NULL DEFAULT 0, " +
                "sync_status TEXT NOT NULL DEFAULT 'LocalOnly', sync_revision INTEGER NOT NULL DEFAULT 0, " +
                "sync_dirty_at_epoch_millis INTEGER, sync_failure_message TEXT, updated_at_epoch_millis INTEGER NOT NULL DEFAULT 0, " +
                "photo_restore_guarded INTEGER NOT NULL DEFAULT 0, photo_restore_retry_deadline_epoch_millis INTEGER, " +
                "retry_after_epoch_millis INTEGER, retry_attempt_count INTEGER NOT NULL DEFAULT 0)",
        )
        execute(connection, "CREATE TABLE clothing_item_tags (clothing_item_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (clothing_item_id, tag_id))")
    }

    private companion object {
        const val DAO_PATH = "app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt"
        const val RAW_QUOTES = "\"\"\""
    }
}
