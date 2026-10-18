package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.DedupeTag
import com.gusanitolabs.robia.core.model.DefaultTagSeeds
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.TagDedupePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Socks and Underwear as built-in categories on sqlite-jdbc. Room does not run in JVM tests. Where Dao.kt holds the SQL of
 * a query or delete, that SQL is read from the file as written ([daoStatement]). The seed INSERT and the repoint INSERT are
 * Room `@Insert(IGNORE)` methods with no SQL in Dao.kt, so they are written here. The tombstone filter is the production
 * [DefaultTagSeeds.forSeed] and the plan is the production [TagDedupePlanner.plan].
 */
class DefaultCategorySeedingTest {
    @Test
    fun freshSeedInsertsTwentyOneCategoryRows() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)

            seedDefaults(connection)

            val ids = categoryRowIds(connection)
            assertEquals(21, ids.size)
            assertTrue("category-socks" in ids)
            assertTrue("category-underwear" in ids)
            assertEquals(
                0L,
                scalar(connection, "SELECT COUNT(*) FROM garment_tags WHERE id IN ('category-socks', 'category-underwear') AND sync_status <> 'Synced'"),
            )
        }
    }

    @Test
    fun tombstonedSocksIsNotResurrectedByTheSeed() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            execute(connection, "INSERT INTO sync_tombstones (id, entity_type, entity_id, revision) VALUES ('garment_tag:category-socks', 'garment_tag', 'category-socks', 5)")

            seedDefaults(connection)

            val ids = categoryRowIds(connection)
            assertFalse("category-socks" in ids)
            assertTrue("category-underwear" in ids)
            assertEquals(20, ids.size)
        }
    }

    @Test
    fun customSocksTagMergesIntoTheBuiltInRowAndKeepsItsGarments() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createTables(connection)
            execute(connection, "INSERT INTO garment_tags (id, category_id, name, sort_order, is_system) VALUES ('custom-socks-1', 'category', 'Socks', 900, 0)")
            execute(connection, "INSERT INTO clothing_item_tags (clothing_item_id, tag_id) VALUES ('garment-1', 'custom-socks-1')")

            seedDefaults(connection)
            mergeDuplicates(connection)

            assertEquals(listOf("category-socks"), query(connection, "SELECT id FROM garment_tags WHERE category_id = 'category' AND name = 'Socks'") { row -> row.getString(1) })
            assertEquals(listOf("category-socks"), query(connection, "SELECT tag_id FROM clothing_item_tags WHERE clothing_item_id = 'garment-1'") { row -> row.getString(1) })
        }
    }

    /**
     * INSERT IGNORE of the defaults the seed keeps. `TagDao.seedTags` is Room-generated with no SQL in Dao.kt, so this INSERT
     * is written by hand. It leaves `sync_status` out, as `GarmentTag.toEntity` does, so the table default applies.
     */
    private fun seedDefaults(connection: Connection) {
        val tags = DefaultTagSeeds.forSeed(DefaultTags.tags, garmentTagTombstoneRevisions(connection))
        connection.prepareStatement(
            "INSERT OR IGNORE INTO garment_tags (id, category_id, name, sort_order, is_system, description, suggest_occasions) VALUES (?, ?, ?, ?, ?, ?, ?)",
        ).use { statement ->
            tags.forEach { tag ->
                statement.setString(1, tag.id)
                statement.setString(2, tag.categoryId)
                statement.setString(3, tag.name)
                statement.setInt(4, tag.sortOrder)
                statement.setInt(5, if (tag.isSystem) 1 else 0)
                statement.setString(6, tag.description)
                statement.setInt(7, if (tag.suggestOccasions) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    /**
     * `applyTagMerges` on sqlite-jdbc: the plan, then per dropped row its garment refs move to the kept id and the row is
     * deleted. The SELECTs and DELETEs are the Dao.kt statements. The repoint INSERT is Room `@Insert(IGNORE)`, so it is hand-written.
     */
    private fun mergeDuplicates(connection: Connection) {
        val tags = query(connection, daoStatement("tagsForDedupe", ":categoryId" to "NULL")) { row ->
            DedupeTag(
                id = row.getString("id"),
                categoryId = row.getString("category_id"),
                name = row.getString("name"),
                isSystem = row.getInt("is_system") == 1,
                syncRevision = row.getLong("sync_revision"),
            )
        }
        TagDedupePlanner.plan(tags, garmentTagTombstoneRevisions(connection)).forEach { merge ->
            val itemIds = query(connection, daoStatement("itemRefsForTag", ":tagId" to "'${merge.dropId}'")) { row ->
                row.getString("clothing_item_id")
            }
            itemIds.forEach { itemId -> insertItemTagRef(connection, itemId, merge.keepId) }
            execute(connection, daoStatement("deleteItemRefsForTag", ":id" to "'${merge.dropId}'"))
            execute(connection, daoStatement("deleteEditableTag", ":id" to "'${merge.dropId}'"))
        }
    }

    /** `insertItemTagRefs` is Room `@Insert(IGNORE)`, so the repoint is written here as the same IGNORE insert. */
    private fun insertItemTagRef(connection: Connection, itemId: String, tagId: String) {
        connection.prepareStatement("INSERT OR IGNORE INTO clothing_item_tags (clothing_item_id, tag_id) VALUES (?, ?)").use { statement ->
            statement.setString(1, itemId)
            statement.setString(2, tagId)
            statement.executeUpdate()
        }
    }

    private fun garmentTagTombstoneRevisions(connection: Connection): Map<String, Long> =
        query(connection, daoStatement("garmentTagTombstones")) { row ->
            row.getString("entity_id") to row.getLong("revision")
        }.toMap()

    private fun categoryRowIds(connection: Connection): List<String> =
        query(connection, "SELECT id FROM garment_tags WHERE category_id = 'category' ORDER BY sort_order") { row -> row.getString(1) }

    /**
     * The `@Query` SQL of a [DAO_PATH] method, read as written (raw `"""` or one-line), with named parameters bound to
     * literals. Same approach as `NeedsUserActionDaoTest.statement`, extended to raw strings.
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

    private fun <T> query(connection: Connection, sql: String, map: (ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                generateSequence { if (rows.next()) rows else null }.map(map).toList()
            }
        }

    private fun scalar(connection: Connection, sql: String): Long = query(connection, sql) { row -> row.getLong(1) }.single()

    private fun execute(connection: Connection, sql: String) {
        connection.createStatement().use { statement -> statement.executeUpdate(sql) }
    }

    /** Only the columns the seed and the merge touch, with the constraints Room declares for them. */
    private fun createTables(connection: Connection) {
        execute(
            connection,
            "CREATE TABLE garment_tags (id TEXT PRIMARY KEY, category_id TEXT NOT NULL, name TEXT NOT NULL, " +
                "sort_order INTEGER NOT NULL, is_system INTEGER NOT NULL, sync_status TEXT NOT NULL DEFAULT 'Synced', " +
                "sync_revision INTEGER NOT NULL DEFAULT 0, description TEXT, suggest_occasions INTEGER NOT NULL DEFAULT 1)",
        )
        execute(connection, "CREATE TABLE clothing_item_tags (clothing_item_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (clothing_item_id, tag_id))")
        execute(
            connection,
            "CREATE TABLE sync_tombstones (id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, revision INTEGER NOT NULL)",
        )
    }

    private companion object {
        const val DAO_PATH = "app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt"
        const val RAW_QUOTES = "\"\"\""
    }
}
