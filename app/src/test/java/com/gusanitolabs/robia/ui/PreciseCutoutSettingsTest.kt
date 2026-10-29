package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import com.gusanitolabs.robia.suggestions.model.ModelFailure
import com.gusanitolabs.robia.suggestions.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SG2b Settings row and switch: the download percentage and the On/Off reading. */
class PreciseCutoutSettingsTest {
    @Test
    fun downloadPercent_isWholePercentOfTheCurrentFile() {
        assertEquals(50, cutoutDownloadPercent(ModelState.Downloading(bytes = 50, total = 100)))
        assertEquals(0, cutoutDownloadPercent(ModelState.Downloading(bytes = 0, total = 100)))
        assertEquals(99, cutoutDownloadPercent(ModelState.Downloading(bytes = 991, total = 1000)))
    }

    @Test
    fun downloadPercent_isZeroWhenTheTotalIsUnknownAndCapsAt100() {
        assertEquals(0, cutoutDownloadPercent(ModelState.Downloading(bytes = 10, total = -1)))
        assertEquals(100, cutoutDownloadPercent(ModelState.Downloading(bytes = 120, total = 100)))
    }

    @Test
    fun downloadPercent_verifyingIs100AndOtherStatesHaveNoPercent() {
        assertEquals(100, cutoutDownloadPercent(ModelState.Verifying))
        assertNull(cutoutDownloadPercent(ModelState.NotDownloaded))
        assertNull(cutoutDownloadPercent(ModelState.WaitingForWifi))
        assertNull(cutoutDownloadPercent(ModelState.Ready(path = "pack")))
        assertNull(cutoutDownloadPercent(ModelState.Failed(ModelFailure.NoNetwork)))
    }

    @Test
    fun on_onlyWhenThePackIsReadyAndTheSettingIsOn() {
        val ready = ModelState.Ready(path = "pack")
        assertTrue(preciseCutoutOn(ready, enabled = true))
        assertFalse(preciseCutoutOn(ready, enabled = false))
        assertFalse(preciseCutoutOn(ModelState.Downloading(bytes = 1, total = 2), enabled = true))
        assertFalse(preciseCutoutOn(ModelState.NotDownloaded, enabled = true))
        assertFalse(preciseCutoutOn(ModelState.WaitingForWifi, enabled = true))
    }

    @Test
    fun hangerDetection_isShownOnlyWhenRemoveHangersIsOnAndTheHangerOptionsAreAvailable() {
        // Truth table over (pack ready, method, Remove hangers): Precise or Automatic + ready pack + Remove hangers on shows the row.
        val auto = CutoutEngineChoice.Automatic
        val precise = CutoutEngineChoice.Precise
        val standard = CutoutEngineChoice.Standard
        assertTrue(hangerDetectionVisible(packReady = true, engine = auto, removeHangers = true))
        assertTrue(hangerDetectionVisible(packReady = true, engine = precise, removeHangers = true))
        assertFalse(hangerDetectionVisible(packReady = true, engine = auto, removeHangers = false))
        assertFalse(hangerDetectionVisible(packReady = true, engine = precise, removeHangers = false))
        assertFalse(hangerDetectionVisible(packReady = true, engine = standard, removeHangers = true))
        assertFalse(hangerDetectionVisible(packReady = true, engine = standard, removeHangers = false))
        assertFalse(hangerDetectionVisible(packReady = false, engine = auto, removeHangers = true))
        assertFalse(hangerDetectionVisible(packReady = false, engine = precise, removeHangers = true))
        assertFalse(hangerDetectionVisible(packReady = false, engine = auto, removeHangers = false))
        assertFalse(hangerDetectionVisible(packReady = false, engine = standard, removeHangers = true))
        assertFalse(hangerDetectionVisible(packReady = false, engine = standard, removeHangers = false))
    }

    /** V-K00-2, V-FB3-CUT-NAMES-2: Precise and Automatic carry the Experimental label; Standard carries none. */
    @Test
    fun preciseAndAutomaticCarryTheExperimentalLabel_standardDoesNot() {
        assertEquals(R.string.cutout_method_experimental, cutoutMethodExperimentalRes(CutoutEngineChoice.Precise))
        assertEquals(R.string.cutout_method_experimental, cutoutMethodExperimentalRes(CutoutEngineChoice.Automatic))
        assertNull(cutoutMethodExperimentalRes(CutoutEngineChoice.Standard))
    }

    /** V-FB3-CUT-NAMES-2: Precise is selectable only with the pack ready, or when it is the stored method; the others always. */
    @Test
    fun precise_isSelectableOnlyWithThePackOrWhenStored_standardAndAutomaticAlways() {
        val precise = CutoutEngineChoice.Precise
        assertTrue(cutoutMethodSelectable(precise, current = CutoutEngineChoice.Standard, packReady = true))
        assertFalse(cutoutMethodSelectable(precise, current = CutoutEngineChoice.Standard, packReady = false))
        assertFalse(cutoutMethodSelectable(precise, current = CutoutEngineChoice.Automatic, packReady = false))
        assertTrue(cutoutMethodSelectable(precise, current = precise, packReady = false))
        for (current in CutoutEngineChoice.entries) {
            for (packReady in listOf(true, false)) {
                assertTrue(cutoutMethodSelectable(CutoutEngineChoice.Standard, current, packReady))
                assertTrue(cutoutMethodSelectable(CutoutEngineChoice.Automatic, current, packReady))
            }
        }
    }
}
