package com.gusanitolabs.robia.ui

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
}
