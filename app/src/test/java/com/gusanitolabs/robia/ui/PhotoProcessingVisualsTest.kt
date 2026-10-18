package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoProcessingVisualsTest {
    @Test
    fun rawPhotoFadesToEighteenPercentOnlyWhenForegroundIsReady() {
        assertEquals(0.18f, rawPhotoAlphaTarget(fadeRaw = true), 0.0001f)
        assertEquals(1f, rawPhotoAlphaTarget(fadeRaw = false), 0.0001f)
    }

    @Test
    fun swatchesShowOnlyAfterProcessingWithAPhotoAndAColour() {
        assertFalse(photoSwatchesVisible(isProcessing = true, hasPhoto = true, swatchCount = 2))
        assertFalse(photoSwatchesVisible(isProcessing = false, hasPhoto = false, swatchCount = 2))
        assertFalse(photoSwatchesVisible(isProcessing = false, hasPhoto = true, swatchCount = 0))
        assertTrue(photoSwatchesVisible(isProcessing = false, hasPhoto = true, swatchCount = 1))
        assertTrue(photoSwatchesVisible(isProcessing = false, hasPhoto = true, swatchCount = 2))
    }

    @Test
    fun glowIsSteadyWithoutPhaseAndPulsesWithinItsRangeWithPhase() {
        assertEquals(0.45f, glowAlpha(null), 0.0001f)
        assertEquals(0.45f, glowAlpha(0.25f), 0.0001f)
        assertEquals(0.27f, glowAlpha(0.75f), 0.0001f)
        val sampled = (0..100).map { glowAlpha(it / 100f) }
        assertTrue(sampled.all { it in 0.27f - 0.0001f..0.45f + 0.0001f })
    }
}
