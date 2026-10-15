package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.suggestions.model.ModelFailure
import com.gusanitolabs.robia.suggestions.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPromptPolicyTest {
    @Test
    fun prompt_isShownOnceWhileModelIsMissing() {
        assertTrue(shouldShowModelPrompt(promptShown = false, state = ModelState.NotDownloaded))
        assertFalse(shouldShowModelPrompt(promptShown = true, state = ModelState.NotDownloaded))
    }

    @Test
    fun prompt_isNotShownWhenModelIsReady() {
        assertFalse(shouldShowModelPrompt(promptShown = false, state = ModelState.Ready("model.tflite")))
    }

    @Test
    fun prompt_isNotShownWhileDownloadingOrFailing() {
        assertFalse(shouldShowModelPrompt(promptShown = false, state = ModelState.WaitingForWifi))
        assertFalse(shouldShowModelPrompt(promptShown = false, state = ModelState.Downloading(1L, 10L)))
        assertFalse(shouldShowModelPrompt(promptShown = false, state = ModelState.Verifying))
        assertFalse(shouldShowModelPrompt(promptShown = false, state = ModelState.Failed(ModelFailure.NoNetwork)))
    }

    @Test
    fun browseSearchFieldIsShownOnlyWhenModelIsReady() {
        assertTrue(browseSearchVisible(ModelState.Ready("model.tflite")))
        assertFalse(browseSearchVisible(ModelState.NotDownloaded))
        assertFalse(browseSearchVisible(ModelState.WaitingForWifi))
        assertFalse(browseSearchVisible(ModelState.Downloading(1L, 10L)))
        assertFalse(browseSearchVisible(ModelState.Verifying))
        assertFalse(browseSearchVisible(ModelState.Failed(ModelFailure.CorruptDownload)))
    }

    @Test
    fun queuedDownloadHeldByNetworkConstraintsShowsWaitingForWifi() {
        assertEquals(ModelState.WaitingForWifi, settingsModelState(ModelState.NotDownloaded, queued = true))
        assertEquals(ModelState.NotDownloaded, settingsModelState(ModelState.NotDownloaded, queued = false))
        assertEquals(ModelState.Ready("model.tflite"), settingsModelState(ModelState.Ready("model.tflite"), queued = true))
    }
}
