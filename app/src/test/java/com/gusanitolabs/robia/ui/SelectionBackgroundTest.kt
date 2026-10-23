package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Smart selection runs on the cutout over a neutral grey, so a transparent area is not black (see [SELECTION_BACKGROUND_GREY]). */
class SelectionBackgroundTest {
    @Test
    fun opaquePixelsKeepTheirColour() {
        assertEquals(listOf(0xFF336699.toInt()), compositeOverGrey(intArrayOf(0xFF336699.toInt()), floatArrayOf(1f)).toList())
    }

    @Test
    fun transparentPixelsBecomeGreyWhateverTheirStoredColour() {
        // A stored PNG has RGB 0 under alpha 0, so a black garment that was removed must not stay black here.
        val out = compositeOverGrey(intArrayOf(0x00000000, 0x00FFFFFF), floatArrayOf(0f, 0f))
        val grey = 0xFF000000.toInt() or (SELECTION_BACKGROUND_GREY shl 16) or (SELECTION_BACKGROUND_GREY shl 8) or SELECTION_BACKGROUND_GREY
        assertEquals(listOf(grey, grey), out.toList())
    }

    @Test
    fun partialAlphaBlendsTheColourWithTheGrey() {
        // 128/255 of black over grey 128 is 128 * 127 / 255 ≈ 64; of white it is 255 * 128 / 255 + 128 * 127 / 255 ≈ 192.
        val alpha = 128f / 255f
        val out = compositeOverGrey(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), floatArrayOf(alpha, alpha))
        assertEquals(listOf(0xFF404040.toInt(), 0xFFC0C0C0.toInt()), out.toList())
    }

    @Test
    fun theCurrentAlphaDecidesNotTheStoredAlpha() {
        // The stored alpha of this pixel is 0 but the session has it kept: the colour shows, as the session says.
        assertEquals(listOf(0xFFFF0000.toInt()), compositeOverGrey(intArrayOf(0x00FF0000), floatArrayOf(1f)).toList())
    }

    @Test
    fun sizeMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            compositeOverGrey(intArrayOf(0xFF000000.toInt()), floatArrayOf(1f, 1f))
        }
    }
}
