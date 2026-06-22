package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.DefaultTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentColorAnalyzerTest {
    private val palette = DefaultTags.mainColors

    @Test
    fun ignoresLowAlphaFringePixelsAndKeepsDominantDenimPrimaryOnly() {
        val samples = sequenceOf(
            repeated("#425B77", count = 80, alpha = 255),
            repeated("#765A91", count = 40, alpha = 120),
            repeated("#F8F9FA", count = 8, alpha = 255),
        ).flatten()

        val matches = GarmentColorAnalyzer.dominantPaletteMatches(samples, palette)

        assertEquals(listOf("navy-blue"), matches.map { it.color.id })
    }

    @Test
    fun returnsSecondaryOnlyForMeaningfulDistinctRegions() {
        val samples = sequenceOf(
            repeated("#315F8E", count = 70, alpha = 255),
            repeated("#D8C3A5", count = 30, alpha = 255),
            repeated("#9E3D35", count = 5, alpha = 255),
        ).flatten()

        val matches = GarmentColorAnalyzer.dominantPaletteMatches(samples, palette)

        assertEquals(listOf("navy-blue", "gray-charcoal"), matches.map { it.color.id })
    }

    @Test
    fun doesNotInventSecondaryFromHighlightsOnDarkGarment() {
        val samples = sequenceOf(
            repeated("#202124", count = 90, alpha = 255),
            repeated("#F8F9FA", count = 10, alpha = 255),
        ).flatten()

        val matches = GarmentColorAnalyzer.dominantPaletteMatches(samples, palette)

        assertEquals(listOf("black"), matches.map { it.color.id })
    }

    @Test
    fun returnsEmptyWhenThereAreTooFewReliablePixels() {
        val samples = repeated("#315F8E", count = 10, alpha = 255)

        val matches = GarmentColorAnalyzer.dominantPaletteMatches(samples, palette)

        assertTrue(matches.isEmpty())
    }

    private fun repeated(hex: String, count: Int, alpha: Int): Sequence<RgbColor> = sequence {
        val base = RgbColor.fromHexOrNull(hex)!!
        repeat(count) { yield(base.copy(alpha = alpha)) }
    }
}
