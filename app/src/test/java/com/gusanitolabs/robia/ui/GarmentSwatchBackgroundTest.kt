package com.gusanitolabs.robia.ui

import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun nearWhiteSwatchesGetTheWarmNeutralStops() {
        val surfaceTop = Color(0xFFF1F3F4)
        val neutral = listOf(Color(0xFFE6E0D9), Color(0xFFCFC5BA))

        assertEquals(neutral, garmentSwatchStops(DisplayColorLabel.White.swatchColor(), surfaceTop))
        assertEquals(neutral, garmentSwatchStops(DisplayColorLabel.Unknown.swatchColor(), surfaceTop))
        assertEquals(
            neutral,
            garmentSwatchStops(
                DisplayColorLabel.White.swatchColor(),
                surfaceTop,
                nearWhite = isNearWhite(DisplayColorLabel.White, "#F8F9FA"),
            ),
        )
    }

    @Test
    fun whiteCreamAndUnsetGarmentsAreNearWhite() {
        assertTrue(isNearWhite(DisplayColorLabel.White, null))
        assertTrue(isNearWhite(DisplayColorLabel.Unknown, null))
        // A light beige is labelled by its hue (Yellow here), but its hex is lighter than the threshold.
        assertTrue(isNearWhite(DisplayColorLabel.Yellow, "#EFE6D2"))
    }

    @Test
    fun darkAndSaturatedGarmentsKeepTheGradient() {
        val surfaceTop = Color(0xFFF1F3F4)
        val cases = listOf(
            DisplayColorLabel.Black to "#1F1F1F",
            DisplayColorLabel.Blue to "#1F2A44",
            DisplayColorLabel.Red to "#800020",
            DisplayColorLabel.Yellow to null,
        )

        cases.forEach { (label, hex) ->
            assertFalse("$label $hex", isNearWhite(label, hex))
            assertEquals(
                "$label $hex",
                listOf(surfaceTop, label.swatchColor().copy(alpha = 0.26f)),
                garmentSwatchStops(label.swatchColor(), surfaceTop, nearWhite = isNearWhite(label, hex)),
            )
        }
    }
}
