package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.DefaultTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteColorClassifierTest {
    private val palette = DefaultTags.mainColors

    @Test
    fun labDefaultMapsDenimBlueToNavyBlueNotPurple() {
        val match = PaletteColorClassifier.Default.nearestColor(palette, RgbColor.fromHexOrNull("#425B77")!!)

        assertEquals("navy-blue", match?.color?.id)
        assertNotEquals("purple", match?.color?.id)
    }

    @Test
    fun lowSaturationSamplesPreferNeutralPaletteColors() {
        val classifier = PaletteColorClassifier.Default

        val gray = classifier.nearestColor(palette, RgbColor.fromHexOrNull("#777777")!!)
        val cream = classifier.nearestColor(palette, RgbColor.fromHexOrNull("#D9CBB5")!!)
        val nearWhite = classifier.nearestColor(palette, RgbColor.fromHexOrNull("#EEEEEE")!!)

        assertTrue(gray?.color?.id in setOf("black", "white", "gray-charcoal", "brown"))
        assertEquals("gray-charcoal", cream?.color?.id)
        assertEquals("white", nearWhite?.color?.id)
    }

    @Test
    fun legacyRgbStrategyRemainsAvailableForComparisons() {
        val sample = RgbColor.fromHexOrNull("#425B77")!!
        val legacy = PaletteColorClassifier.LegacyRgb.nearestColor(palette, sample)
        val lab = PaletteColorClassifier.Default.nearestColor(palette, sample)

        assertEquals(ColorClassificationStrategy.CieLabDeltaE76, PaletteColorClassifier.DEFAULT_STRATEGY)
        assertTrue(legacy != null)
        assertEquals("navy-blue", lab?.color?.id)
    }

    @Test
    fun exactPaletteHexMapsBackToItself() {
        val classifier = PaletteColorClassifier.Default

        palette.forEach { color ->
            assertEquals(color.id, classifier.nearestColor(palette, RgbColor.fromHexOrNull(color.hex)!!)?.color?.id)
        }
    }
}
