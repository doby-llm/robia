package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.DedupeTag
import com.gusanitolabs.robia.core.model.DefaultTagSeeds
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.TagDedupePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Socks and Underwear as built-in categories on sqlite-jdbc. Room does not run in JVM tests, so [seedDefaults] mirrors
 * `TagDao.seedTags` (INSERT IGNORE of [DefaultTagSeeds.withoutDeleted]) and [mergeDuplicates] mirrors
 * `TagDao.applyTagMerges` (repoint the garments, then delete the dropped row). Both use the real planner and filters.
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

    /** INSERT IGNORE of the defaults the seed keeps: every default except the tombstoned ones. */
    private fun seedDefaults(connection: Connection) {
        val deletedRevisions = garmentTagTombstoneRevisions(connection)
        val tags = DefaultTagSeeds.withoutDeleted(DefaultTags.tags, deletedRevisions.keys)
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

    /** Same plan as `mergeDuplicateTags`; each dropped row loses its garment references to the kept id, then the row. */
    private fun mergeDuplicates(connection: Connection) {
        val tags = query(connection, "SELECT id, category_id, name, is_system, sync_revision FROM garment_tags") { row ->
            DedupeTag(
                id = row.getString(1),
                categoryId = row.getString(2),
                name = row.getString(3),
                isSystem = row.getInt(4) == 1,
                syncRevision = row.getLong(5),
            )
        }
        TagDedupePlanner.plan(tags, garmentTagTombstoneRevisions(connection)).forEach { merge ->
            connection.prepareStatement(
                "INSERT OR IGNORE INTO clothing_item_tags (clothing_item_id, tag_id) SELECT clothing_item_id, ? FROM clothing_item_tags WHERE tag_id = ?",
            ).use { statement ->
                statement.setString(1, merge.keepId)
                statement.setString(2, merge.dropId)
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM clothing_item_tags WHERE tag_id = ?").use { statement ->
                statement.setString(1, merge.dropId)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "DELETE FROM garment_tags WHERE id = ? AND (is_system = 0 OR category_id IN ('category', 'season', 'occasion', 'location'))",
            ).use { statement ->
                statement.setString(1, merge.dropId)
                statement.executeUpdate()
            }
        }
    }

    private fun garmentTagTombstoneRevisions(connection: Connection): Map<String, Long> =
        query(connection, "SELECT entity_id, revision FROM sync_tombstones WHERE entity_type = 'garment_tag'") { row ->
            row.getString(1) to row.getLong(2)
        }.toMap()

    private fun categoryRowIds(connection: Connection): List<String> =
        query(connection, "SELECT id FROM garment_tags WHERE category_id = 'category' ORDER BY sort_order") { row -> row.getString(1) }

    private fun <T> query(connection: Connection, sql: String, map: (ResultSet) -> T): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                generateSequence { if (rows.next()) rows else null }.map(map).toList()
            }
        }

    private fun execute(connection: Connection, sql: String) {
        connection.createStatement().use { statement -> statement.executeUpdate(sql) }
    }

    /** Only the columns the seed and the merge touch, with the constraints Room declares for them. */
    private fun createTables(connection: Connection) {
        execute(
            connection,
            "CREATE TABLE garment_tags (id TEXT PRIMARY KEY, category_id TEXT NOT NULL, name TEXT NOT NULL, " +
                "sort_order INTEGER NOT NULL, is_system INTEGER NOT NULL, sync_revision INTEGER NOT NULL DEFAULT 0, " +
                "description TEXT, suggest_occasions INTEGER NOT NULL DEFAULT 1)",
        )
        execute(connection, "CREATE TABLE clothing_item_tags (clothing_item_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (clothing_item_id, tag_id))")
        execute(
            connection,
            "CREATE TABLE sync_tombstones (id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, revision INTEGER NOT NULL)",
        )
    }
}
