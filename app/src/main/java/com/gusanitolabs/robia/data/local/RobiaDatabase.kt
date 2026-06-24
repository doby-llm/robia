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
        ClothingItemTagCrossRef::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(RobiaConverters::class)
abstract class RobiaDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun tagDao(): TagDao

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
    }
}
