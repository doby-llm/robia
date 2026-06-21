package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.DisplayColorLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorLabelResolverTest {
    @Test
    fun resolvesHexColorsToFixedLabels() {
        assertEquals(DisplayColorLabel.Red, ColorLabelResolver.fromRawValue("#cc2020"))
        assertEquals(DisplayColorLabel.Blue, ColorLabelResolver.fromRawValue("#3366cc"))
        assertEquals(DisplayColorLabel.Gray, ColorLabelResolver.fromRawValue("#777777"))
    }

    @Test
    fun preservesDefaultPaletteRedAndPinkLabels() {
        assertEquals(DisplayColorLabel.Pink, ColorLabelResolver.fromRawValue("#D4879A"))
        assertEquals(DisplayColorLabel.Red, ColorLabelResolver.fromRawValue("#9E3D35"))
    }

    @Test
    fun preservesKnownTextLabels() {
        assertEquals(DisplayColorLabel.Gray, ColorLabelResolver.fromRawValue("beige"))
        assertEquals(DisplayColorLabel.Brown, ColorLabelResolver.fromRawValue("brown"))
        assertEquals(DisplayColorLabel.Multicolor, ColorLabelResolver.fromRawValue("multi-color"))
    }

    @Test
    fun returnsUnknownForBlankOrUnparsedValues() {
        assertEquals(DisplayColorLabel.Unknown, ColorLabelResolver.fromRawValue(""))
        assertEquals(DisplayColorLabel.Unknown, ColorLabelResolver.fromRawValue("not-a-color"))
    }
}
