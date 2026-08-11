package com.gusanitolabs.robia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClothingItemEntity::class,
        TagCategoryEntity::class,
        GarmentTagEntity::class,
        MainColorEntity::class,
        SyncTombstoneEntity::class,
        ClothingItemTagCrossRef::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(RobiaConverters::class)
abstract class RobiaDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun tagDao(): TagDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao

    companion object {
        @Volatile private var instance: RobiaDatabase? = null

        fun getInstance(context: Context): RobiaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RobiaDatabase::class.java,
                    "robia.db",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                    )
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS main_colors (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        hex TEXT NOT NULL,
                        sort_order INTEGER NOT NULL,
                        is_default INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("DELETE FROM clothing_item_tags WHERE tag_id IN (SELECT id FROM garment_tags WHERE category_id = 'care')")
                database.execSQL("DELETE FROM garment_tags WHERE category_id = 'care'")
                database.execSQL("DELETE FROM tag_categories WHERE id = 'care'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_primary_palette_color_id TEXT")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_primary_palette_color_name TEXT")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_primary_palette_color_hex TEXT")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_secondary_palette_color_id TEXT")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_secondary_palette_color_name TEXT")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN color_secondary_palette_color_hex TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM clothing_item_tags WHERE tag_id IN (SELECT id FROM garment_tags WHERE category_id = 'fit')")
                database.execSQL("DELETE FROM garment_tags WHERE category_id = 'fit'")
                database.execSQL("DELETE FROM tag_categories WHERE id = 'fit'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN fit_value INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE main_colors
                    SET name = 'Beige / Cream', hex = '#D8C3A5', sort_order = 30, is_default = 1
                    WHERE id = 'gray-charcoal'
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE clothing_items
                    SET color_primary_palette_color_id = 'gray-charcoal',
                        color_primary_palette_color_name = 'Beige / Cream',
                        color_primary_palette_color_hex = '#D8C3A5'
                    WHERE color_primary_palette_color_id IN ('gray-charcoal', 'beige-cream')
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE clothing_items
                    SET color_secondary_palette_color_id = 'gray-charcoal',
                        color_secondary_palette_color_name = 'Beige / Cream',
                        color_secondary_palette_color_hex = '#D8C3A5'
                    WHERE color_secondary_palette_color_id IN ('gray-charcoal', 'beige-cream')
                    """.trimIndent(),
                )
                database.execSQL("DELETE FROM main_colors WHERE id = 'beige-cream'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The classifier contract uses Fall. Preserve existing garment selections by
                // moving legacy Autumn references to the stable model-aligned season-fall id.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO garment_tags (id, category_id, name, sort_order, is_system)
                    VALUES ('season-fall', 'season', 'Fall', 30, 1)
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE clothing_item_tags
                    SET tag_id = 'season-fall'
                    WHERE tag_id = 'season-autumn'
                        AND NOT EXISTS (
                            SELECT 1
                            FROM clothing_item_tags existing
                            WHERE existing.clothing_item_id = clothing_item_tags.clothing_item_id
                                AND existing.tag_id = 'season-fall'
                        )
                    """.trimIndent(),
                )
                database.execSQL("DELETE FROM clothing_item_tags WHERE tag_id = 'season-autumn'")
                database.execSQL("DELETE FROM garment_tags WHERE id = 'season-autumn'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        id TEXT NOT NULL PRIMARY KEY,
                        entity_type TEXT NOT NULL,
                        entity_id TEXT NOT NULL,
                        deleted_at_epoch_millis INTEGER NOT NULL,
                        revision INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_tombstones_entity_type_entity_id ON sync_tombstones(entity_type, entity_id)",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LocalOnly'")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN sync_revision INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN sync_dirty_at_epoch_millis INTEGER")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN last_synced_at_epoch_millis INTEGER")
                database.execSQL("ALTER TABLE clothing_items ADD COLUMN sync_failure_message TEXT")
                database.execSQL(
                    """
                    UPDATE clothing_items
                    SET sync_revision = updated_at_epoch_millis
                    WHERE sync_revision = 0
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addMetadataSyncColumns(database, "tag_categories")
                addMetadataSyncColumns(database, "garment_tags")
                addMetadataSyncColumns(database, "main_colors")
                database.execSQL("ALTER TABLE sync_tombstones ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'Queued'")
                database.execSQL("ALTER TABLE sync_tombstones ADD COLUMN sync_dirty_at_epoch_millis INTEGER")
                database.execSQL("ALTER TABLE sync_tombstones ADD COLUMN last_synced_at_epoch_millis INTEGER")
                database.execSQL("ALTER TABLE sync_tombstones ADD COLUMN sync_failure_message TEXT")
                database.execSQL(
                    """
                    UPDATE sync_tombstones
                    SET sync_dirty_at_epoch_millis = revision
                    WHERE sync_dirty_at_epoch_millis IS NULL
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                listOf("clothing_items", "tag_categories", "garment_tags", "main_colors", "sync_tombstones").forEach { tableName ->
                    database.execSQL("ALTER TABLE $tableName ADD COLUMN retry_attempt_count INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE $tableName ADD COLUMN retry_after_epoch_millis INTEGER")
                    database.execSQL("ALTER TABLE $tableName ADD COLUMN sync_started_at_epoch_millis INTEGER")
                    // A process that upgrades cannot still own the legacy in-progress row.
                    database.execSQL("UPDATE $tableName SET sync_status = 'Running', sync_started_at_epoch_millis = 0 WHERE sync_status = 'Syncing'")
                }
            }
        }

        private fun addMetadataSyncColumns(database: SupportSQLiteDatabase, tableName: String) {
            database.execSQL("ALTER TABLE $tableName ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'Synced'")
            database.execSQL("ALTER TABLE $tableName ADD COLUMN sync_revision INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE $tableName ADD COLUMN sync_dirty_at_epoch_millis INTEGER")
            database.execSQL("ALTER TABLE $tableName ADD COLUMN last_synced_at_epoch_millis INTEGER")
            database.execSQL("ALTER TABLE $tableName ADD COLUMN sync_failure_message TEXT")
        }
    }
}
