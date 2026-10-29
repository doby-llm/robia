package com.gusanitolabs.robia.media.cutout

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.BatchCutoutMode
import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import com.gusanitolabs.robia.core.model.HangerSensitivity
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.data.toRobiaSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-SG4-1: the engine × pack × sensitivity table, `batchCutouts`, and the legacy `preciseCutoutEnabled` migration. */
class CutoutOptionsTest {
    private val legacyKey = booleanPreferencesKey("precise_cutout_enabled")
    private val engineKey = stringPreferencesKey("cutout_engine")

    /** V-FB3-CUT-NAMES-1: Standard never; Precise and Automatic only with the pack ready. */
    @Test
    fun usePrecise_isNeverStandard_andPreciseAndAutomaticOnlyWithAReadyPack() {
        for (engine in CutoutEngineChoice.entries) {
            for (packReady in listOf(true, false)) {
                val options = cutoutOptionsOf(RobiaSettings(cutoutEngine = engine), packReady)

                assertEquals("engine=$engine pack=$packReady", engine != CutoutEngineChoice.Standard && packReady, options.usePrecise)
            }
        }
    }

    /** V-FB3-CUT-NAMES-1: the developer switch reads on for Precise and Automatic, off for Standard. */
    @Test
    fun preciseCutoutEnabled_isOnForPreciseAndAutomatic() {
        assertEquals(false, RobiaSettings(cutoutEngine = CutoutEngineChoice.Standard).preciseCutoutEnabled)
        assertEquals(true, RobiaSettings(cutoutEngine = CutoutEngineChoice.Precise).preciseCutoutEnabled)
        assertEquals(true, RobiaSettings(cutoutEngine = CutoutEngineChoice.Automatic).preciseCutoutEnabled)
    }

    /** V-FB3-CUT-NAMES-1: stored names round-trip; an unknown stored name reads Automatic. */
    @Test
    fun storedEngineNames_roundTrip_andUnknownReadsAutomatic() {
        for (engine in CutoutEngineChoice.entries) {
            assertEquals(engine, mutablePreferencesOf(engineKey to engine.name).toRobiaSettings().cutoutEngine)
        }
        assertEquals(CutoutEngineChoice.Automatic, mutablePreferencesOf(engineKey to "Garbage").toRobiaSettings().cutoutEngine)
    }

    @Test
    fun hangerThreshold_isZeroPointTwentyFiveForNormalAndZeroPointTwelveForHigh() {
        val normal = cutoutOptionsOf(RobiaSettings(cutoutHangerSensitivity = HangerSensitivity.Normal), packReady = true)
        val high = cutoutOptionsOf(RobiaSettings(cutoutHangerSensitivity = HangerSensitivity.High), packReady = true)

        assertEquals(0.25f, normal.hangerThreshold, 0f)
        assertEquals(0.12f, high.hangerThreshold, 0f)
    }

    @Test
    fun defaults_giveStandardWithHangersNormalAndSoft() {
        val options = cutoutOptionsOf(RobiaSettings(), packReady = true)

        assertEquals(CutoutOptions(usePrecise = false, removeHanger = true, hangerThreshold = 0.25f, edges = CutoutEdges.Soft), options)
    }

    @Test
    fun removeHangerAndEdges_areCarriedFromSettings() {
        val options = cutoutOptionsOf(
            RobiaSettings(cutoutRemoveHanger = false, cutoutEdges = CutoutEdges.Sharp),
            packReady = true,
        )

        assertEquals(false, options.removeHanger)
        assertEquals(CutoutEdges.Sharp, options.edges)
    }

    @Test
    fun batchCutouts_isOneForMemorySaverAndForThePreciseEngine_otherwiseTwo() {
        assertEquals(1, batchCutouts(BatchCutoutMode.MemorySaver, preciseActive = false))
        assertEquals(1, batchCutouts(BatchCutoutMode.MemorySaver, preciseActive = true))
        assertEquals(1, batchCutouts(BatchCutoutMode.Balanced, preciseActive = true))
        assertEquals(2, batchCutouts(BatchCutoutMode.Balanced, preciseActive = false))
    }

    @Test
    fun legacyTrue_migratesToAutomatic() {
        val settings = mutablePreferencesOf(legacyKey to true).toRobiaSettings()

        assertEquals(CutoutEngineChoice.Automatic, settings.cutoutEngine)
    }

    @Test
    fun legacyFalse_migratesToStandard() {
        val settings = mutablePreferencesOf(legacyKey to false).toRobiaSettings()

        assertEquals(CutoutEngineChoice.Standard, settings.cutoutEngine)
    }

    @Test
    fun nothingStored_isStandard() {
        assertEquals(CutoutEngineChoice.Standard, mutablePreferencesOf().toRobiaSettings().cutoutEngine)
    }

    @Test
    fun storedCutoutEngine_winsOverTheLegacyBoolean() {
        val settings = mutablePreferencesOf(engineKey to "Standard", legacyKey to true).toRobiaSettings()

        assertEquals(CutoutEngineChoice.Standard, settings.cutoutEngine)
    }

    @Test
    fun unknownStoredValues_fallBackToTheDefaults() {
        val settings = mutablePreferencesOf(
            engineKey to "BiRefNet",
            stringPreferencesKey("cutout_hanger_sensitivity") to "Extreme",
            stringPreferencesKey("cutout_edges") to "Blurry",
            stringPreferencesKey("batch_cutout_mode") to "Turbo",
        ).toRobiaSettings()

        assertEquals(CutoutEngineChoice.Automatic, settings.cutoutEngine)
        assertEquals(HangerSensitivity.Normal, settings.cutoutHangerSensitivity)
        assertEquals(CutoutEdges.Soft, settings.cutoutEdges)
        assertEquals(BatchCutoutMode.Balanced, settings.batchCutoutMode)
    }

    @Test
    fun storedNewKeys_areRead() {
        val settings = mutablePreferencesOf(
            booleanPreferencesKey("cutout_remove_hanger") to false,
            stringPreferencesKey("cutout_hanger_sensitivity") to "High",
            stringPreferencesKey("cutout_edges") to "Sharp",
            stringPreferencesKey("batch_cutout_mode") to "MemorySaver",
        ).toRobiaSettings()

        assertEquals(false, settings.cutoutRemoveHanger)
        assertEquals(HangerSensitivity.High, settings.cutoutHangerSensitivity)
        assertEquals(CutoutEdges.Sharp, settings.cutoutEdges)
        assertEquals(BatchCutoutMode.MemorySaver, settings.batchCutoutMode)
    }
}
