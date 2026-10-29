package com.gusanitolabs.robia.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import com.gusanitolabs.robia.core.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsMappingTest {
    private val browseLayoutKey = stringPreferencesKey("browse_layout")
    private val browseColumnsKey = intPreferencesKey("browse_columns")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    @get:Rule
    val folder = TemporaryFolder()

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

    /** V-FB3-CUT-NAMES-1: the K00 migration leaves a stored Precise alone once the flag is written. */
    @Test
    fun migration_flagAppliedAndPrecise_staysPrecise() {
        assertEquals(CutoutEngineChoice.Precise to false, migratedCutoutEngine(CutoutEngineChoice.Precise, flagApplied = true))
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

    /** V-DARK1-1: absent theme_mode reads as Light. */
    @Test
    fun absentThemeMode_defaultsToLight() {
        assertEquals(ThemeMode.Light, mutablePreferencesOf().toRobiaSettings().themeMode)
    }

    @Test
    fun storedThemeModes_areRead() {
        assertEquals(ThemeMode.Light, mutablePreferencesOf(themeModeKey to "light").toRobiaSettings().themeMode)
        assertEquals(ThemeMode.Dark, mutablePreferencesOf(themeModeKey to "dark").toRobiaSettings().themeMode)
        assertEquals(ThemeMode.System, mutablePreferencesOf(themeModeKey to "system").toRobiaSettings().themeMode)
    }

    @Test
    fun unknownThemeMode_fallsBackToLight() {
        assertEquals(ThemeMode.Light, mutablePreferencesOf(themeModeKey to "Garbage").toRobiaSettings().themeMode)
    }

    /** V-DARK1-1: each choice is written through setThemeMode and read back from the store. */
    @Test
    fun themeModeRoundTripsThroughTheStore() {
        runBlocking {
            val store = PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "theme.preferences_pb") })
            val repository = DataStoreSettingsRepository(store)

            assertEquals(ThemeMode.Light, repository.themeMode.first())
            ThemeMode.entries.forEach { mode ->
                repository.setThemeMode(mode)
                assertEquals(mode, repository.themeMode.first())
            }
        }
    }
}
