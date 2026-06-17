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
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
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
    }
}
