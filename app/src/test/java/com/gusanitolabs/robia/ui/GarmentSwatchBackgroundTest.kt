package com.gusanitolabs.robia.ui

import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.designsystem.RobiaDarkExtras
import com.gusanitolabs.robia.core.designsystem.RobiaLightExtras
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

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

    /** V-DARK1-4: the photo-well token is #F3F3F4 (today's surfaceContainerLow) in both themes. */
    @Test
    fun brushTopStopComesFromThePhotoWellTokenInBothThemes() {
        assertEquals(Color(0xFFF3F3F4), RobiaLightExtras.photoWellTop)
        assertEquals(RobiaLightExtras.photoWellTop, RobiaDarkExtras.photoWellTop)
        assertEquals(RobiaLightExtras.photoWellBottom, RobiaDarkExtras.photoWellBottom)
        assertEquals(
            RobiaDarkExtras.photoWellTop,
            garmentSwatchStops(DisplayColorLabel.Red.swatchColor(), RobiaDarkExtras.photoWellTop)[0],
        )
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
    fun nearWhiteSwatchesGetTheNeutralGreyStops() {
        val surfaceTop = Color(0xFFF1F3F4)
        val neutral = listOf(Color(0xFFF3F3F4), Color(0xFFD6D5D6))

        assertEquals(neutral, garmentSwatchStops(DisplayColorLabel.White.swatchColor(), surfaceTop))
        assertEquals(neutral, garmentSwatchStops(DisplayColorLabel.Unknown.swatchColor(), surfaceTop))
        assertEquals(
            neutral,
            garmentSwatchStops(
                DisplayColorLabel.Yellow.swatchColor(),
                surfaceTop,
                nearWhite = isNearWhite(DisplayColorLabel.Yellow, "#EFE6D2"),
            ),
        )
        assertEquals(
            neutral,
            garmentSwatchStops(
                DisplayColorLabel.White.swatchColor(),
                surfaceTop,
                nearWhite = isNearWhite(DisplayColorLabel.White, "#F8F9FA"),
            ),
        )
        neutral.forEach { stop -> assertTrue("$stop is not neutral", channelSpread(stop) < 10) }
    }

    private fun channelSpread(colour: Color): Int {
        val channels = listOf(colour.red, colour.green, colour.blue).map { (it * 255).roundToInt() }
        return channels.max() - channels.min()
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
