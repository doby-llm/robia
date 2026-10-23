package com.gusanitolabs.robia.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMappingTest {
    private val browseLayoutKey = stringPreferencesKey("browse_layout")
    private val browseColumnsKey = intPreferencesKey("browse_columns")

    /** V-K00-1: absent cutout_engine → Standard; the one-time migration sets Standard and writes the flag; an applied flag keeps the stored choice. */
    @Test
    fun absentCutoutEngine_defaultsToStandard() {
        assertEquals(CutoutEngineChoice.Standard, mutablePreferencesOf().toRobiaSettings().cutoutEngine)
    }

    @Test
    fun migration_storedAutomaticWithoutFlag_becomesStandardAndWritesTheFlag() {
        assertEquals(CutoutEngineChoice.Standard to true, migratedCutoutEngine(CutoutEngineChoice.Automatic, flagApplied = false))
    }

    @Test
    fun migration_absentEngineWithoutFlag_writesStandardAndTheFlag() {
        assertEquals(CutoutEngineChoice.Standard to true, migratedCutoutEngine(null, flagApplied = false))
    }

    @Test
    fun migration_flagAppliedAndAutomatic_staysAutomatic() {
        assertEquals(CutoutEngineChoice.Automatic to false, migratedCutoutEngine(CutoutEngineChoice.Automatic, flagApplied = true))
    }

    @Test
    fun migration_flagAppliedAndStandard_staysStandard() {
        assertEquals(CutoutEngineChoice.Standard to false, migratedCutoutEngine(CutoutEngineChoice.Standard, flagApplied = true))
    }

    @Test
    fun migration_flagAppliedAndAbsentEngine_isStandardWithoutWrite() {
        assertEquals(CutoutEngineChoice.Standard to false, migratedCutoutEngine(null, flagApplied = true))
    }

    @Test
    fun absentBrowseLayout_defaultsToPhotosOnly() {
        assertEquals(BrowseLayout.PhotosOnly, mutablePreferencesOf().toRobiaSettings().browseLayout)
    }

    @Test
    fun storedPhotosWithDetails_isRead() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "PhotosWithDetails")

        assertEquals(BrowseLayout.PhotosWithDetails, preferences.toRobiaSettings().browseLayout)
    }

    @Test
    fun storedPhotosOnly_isRead() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "PhotosOnly")

        assertEquals(BrowseLayout.PhotosOnly, preferences.toRobiaSettings().browseLayout)
    }

    @Test
    fun unknownBrowseLayout_fallsBackToPhotosOnly() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "Garbage")

        assertEquals(BrowseLayout.PhotosOnly, preferences.toRobiaSettings().browseLayout)
    }

    @Test
    fun absentBrowseColumns_defaultsToTwo() {
        assertEquals(2, mutablePreferencesOf().toRobiaSettings().browseColumns)
    }

    @Test
    fun browseColumnsOutOfRange_fallsBackToTwo() {
        assertEquals(2, mutablePreferencesOf(browseColumnsKey to 5).toRobiaSettings().browseColumns)
        assertEquals(2, mutablePreferencesOf(browseColumnsKey to 1).toRobiaSettings().browseColumns)
    }

    @Test
    fun storedBrowseColumnsInRange_isRead() {
        assertEquals(3, mutablePreferencesOf(browseColumnsKey to 3).toRobiaSettings().browseColumns)
    }
}
