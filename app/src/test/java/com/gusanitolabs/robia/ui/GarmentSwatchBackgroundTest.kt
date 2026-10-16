package com.gusanitolabs.robia.ui

import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GarmentSwatchBackgroundTest {
    @Test
    fun garmentSwatchLabelMapsStoredHexToFixedLabel() {
        assertEquals(DisplayColorLabel.Red, garmentSwatchLabel("#9E3D35"))
        assertEquals(DisplayColorLabel.White, garmentSwatchLabel("#F8F9FA"))
        assertEquals(DisplayColorLabel.Gray, garmentSwatchLabel("beige"))
    }

    @Test
    fun garmentSwatchLabelFallsBackToUnknownWhenMissingOrUnparsed() {
        assertEquals(DisplayColorLabel.Unknown, garmentSwatchLabel(null))
        assertEquals(DisplayColorLabel.Unknown, garmentSwatchLabel(""))
        assertEquals(DisplayColorLabel.Unknown, garmentSwatchLabel("not-a-colour"))
    }

    @Test
    fun unknownSwatchIsTheNeutralGrey() {
        assertEquals(Color(0xFFDADADA), DisplayColorLabel.Unknown.swatchColor())
    }

    @Test
    fun brushStopsAreSurfaceTopThenSwatchAtFixedAlpha() {
        val surfaceTop = Color(0xFFF1F3F4)
        val swatch = DisplayColorLabel.Red.swatchColor()

        val stops = garmentSwatchStops(swatch, surfaceTop)

        assertEquals(listOf(surfaceTop, swatch.copy(alpha = 0.26f)), stops)
    }

    @Test
    fun brushStopsFollowTheSwatchNotTheSurface() {
        val surfaceTop = Color(0xFFF1F3F4)

        val red = garmentSwatchStops(DisplayColorLabel.Red.swatchColor(), surfaceTop)
        val blue = garmentSwatchStops(DisplayColorLabel.Blue.swatchColor(), surfaceTop)

        assertEquals(red[0], blue[0])
        assertNotEquals(red[1], blue[1])
    }
}
