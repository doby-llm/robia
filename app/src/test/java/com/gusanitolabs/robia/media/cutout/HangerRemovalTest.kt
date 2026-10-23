package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG4-3 (threshold part): the S3 threshold reaches both detector passes, with a fake detector. */
class HangerRemovalTest {
    private val w = 200
    private val h = 400
    private val garment = MaskBox(10, 100, 90, 300)

    /** Plausible for [garment]: wide enough, starts in its top quarter, ends above its middle. */
    private val plausibleHanger = MaskBox(20, 110, 80, 130)

    @Test
    fun threshold_reachesBothPasses_whenTheSecondPassFindsTheHanger() {
        val thresholds = mutableListOf<Float>()
        val regions = mutableListOf<IntArray?>()
        val region = HangerRemoval.hangerSearchRegion(garment, w, h)
        val found = HangerRemoval.findHanger(garment, w, h, threshold = 0.12f) { searchRegion, threshold ->
            thresholds += threshold
            regions += searchRegion
            // The second pass sees the cropped region, so its box is in region coordinates.
            if (searchRegion == null) {
                emptyList()
            } else {
                listOf(0.5f to MaskBox(plausibleHanger.x0 - region[0], plausibleHanger.y0 - region[1], plausibleHanger.x1 - region[0], plausibleHanger.y1 - region[1]))
            }
        }

        assertEquals(listOf(0.12f, 0.12f), thresholds)
        assertNull(regions[0])
        assertEquals(region.toList(), regions[1]?.toList())
        assertEquals(plausibleHanger, found)
    }

    @Test
    fun threshold_isNotCalledAgain_whenTheFirstPassFindsAHanger() {
        val thresholds = mutableListOf<Float>()
        val found = HangerRemoval.findHanger(garment, w, h, threshold = 0.25f) { _, threshold ->
            thresholds += threshold
            listOf(0.4f to plausibleHanger)
        }

        assertEquals(listOf(0.25f), thresholds)
        assertEquals(plausibleHanger, found)
    }

    @Test
    fun implausibleDetections_areIgnored() {
        // Too narrow for the garment (width 10 < 35 % of 80) in both passes.
        val narrow = MaskBox(40, 110, 50, 120)

        val found = HangerRemoval.findHanger(garment, w, h, threshold = 0.25f) { _, _ -> listOf(0.9f to narrow) }

        assertNull(found)
    }

    @Test
    fun hangerSearchRegion_isTheTopBandOfTheGarmentWidenedByTenAndFifteenPercent() {
        val region = HangerRemoval.hangerSearchRegion(garment, w, h)

        assertEquals(listOf(2, 70, 98, 170), region.toList())
    }

    /** A 10×10 photo: the band is rows 0–4 (indices 0–49); [mask] sets the listed indices to 1. */
    private val bandW = 10
    private val bandBottom = 5
    private val noSupport = BooleanArray(100)

    private fun mask(vararg indices: IntRange): FloatArray = FloatArray(100) { i -> if (indices.any { i in it }) 1f else 0f }

    @Test
    fun guardedBand_keepsTheFirstSelectionInTheBandWhenTheReselectionKeepsLessThan85Percent() {
        val previous = mask(0..9, 60..69)
        val reselected = mask(0..7, 20..21) // keeps 8 of the 10 opaque band pixels

        val outcome = HangerRemoval.guardedBand(reselected, previous, noSupport, bandBottom, bandW)

        assertTrue(outcome.guardTripped)
        assertArrayEquals(previous, outcome.selection, 0f)
    }

    @Test
    fun guardedBand_keepsTheReselectionInTheBandWhenItKeeps85PercentOrMore() {
        val previous = mask(0..9, 60..69)
        val reselected = mask(0..8, 20..21) // keeps 9 of 10; the new pixels at 20–21 stay

        val outcome = HangerRemoval.guardedBand(reselected, previous, noSupport, bandBottom, bandW)

        assertFalse(outcome.guardTripped)
        assertArrayEquals(mask(0..8, 20..21, 60..69), outcome.selection, 0f)
    }

    @Test
    fun guardedBand_exactly85PercentKeptIsNotTripped() {
        val previous = mask(0..19)
        val reselected = mask(0..16) // keeps 17 of 20

        assertFalse(HangerRemoval.guardedBand(reselected, previous, noSupport, bandBottom, bandW).guardTripped)
    }

    @Test
    fun guardedBand_countsOnlyPixelsOutsideTheSupport() {
        val support = BooleanArray(100) { it in 0..4 }
        val previous = mask(0..9)
        val reselected = mask(5..8) // of the 5 opaque pixels outside the support, 4 are kept

        val outcome = HangerRemoval.guardedBand(reselected, previous, support, bandBottom, bandW)

        assertTrue(outcome.guardTripped)
        assertArrayEquals(mask(5..9), outcome.selection, 0f)
    }

    @Test
    fun guardedBand_withNoOpaquePixelInTheBandIsNotTripped() {
        val previous = mask(60..69)
        val reselected = mask(0..4)

        val outcome = HangerRemoval.guardedBand(reselected, previous, noSupport, bandBottom, bandW)

        assertFalse(outcome.guardTripped)
        assertArrayEquals(mask(0..4, 60..69), outcome.selection, 0f)
    }

    private fun argbOf(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    @Test
    fun ringColour_isThePerChannelMedianOfThe25PxRingOnly() {
        val w = 60
        val h = 60
        val support = BooleanArray(w * h) { i -> i % w in 18..21 && i / w in 18..21 }
        // Ring pixels (within 12 px of the support, outside it, 768 px) are 200/100/50. The support and the far corners
        // (2,832 px) are white, so a whole-image median would be white (255) and the test would fail if the ring were widened.
        val argb = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inRing = !support[i] && x in 6..33 && y in 6..33
            if (inRing) argbOf(200, 100, 50) else argbOf(255, 255, 255)
        }

        assertEquals(argbOf(200, 100, 50), HangerRemoval.ringColour(argb, w, h, support))
    }

    /** V-K03-2: within 2 px of the kill support, with alpha above 0, the RGB is the fill colour; farther pixels are untouched. */
    @Test
    fun decontaminate_givesTheFillColourWithinTwoPixelsOfTheSupportAndLeavesTheRestAlone() {
        val w = 20
        val h = 20
        val support = BooleanArray(w * h) { i -> i % w in 8..11 && i / w in 8..11 }
        val background = argbOf(240, 240, 240)
        val argb = IntArray(w * h) { background }
        val alpha = ByteArray(w * h) { 255.toByte() }
        alpha[9 * w + 7] = 0 // transparent: keeps its RGB even within 2 px
        val fillColour = argbOf(10, 200, 30)

        val out = HangerRemoval.decontaminate(argb, alpha, support, fillColour, w, h)

        fun at(x: Int, y: Int): Int = out[y * w + x] and 0xFFFFFF
        assertEquals(fillColour and 0xFFFFFF, at(9, 9)) // inside the support
        assertEquals(fillColour and 0xFFFFFF, at(6, 9)) // 2 px left
        assertEquals(background and 0xFFFFFF, at(7, 9)) // alpha 0: untouched
        assertEquals(fillColour and 0xFFFFFF, at(13, 9)) // 2 px right
        assertEquals(background and 0xFFFFFF, at(14, 9)) // 3 px right
        assertEquals(background and 0xFFFFFF, at(9, 5)) // 3 px above
        assertEquals(fillColour and 0xFFFFFF, at(6, 6)) // diagonal, 2 px in both directions
        assertEquals(background and 0xFFFFFF, at(5, 5)) // diagonal, 3 px
    }

    /** K03 step 1: the S5 guide is the filled photo's luminance above the band bottom and the original's below it. */
    @Test
    fun bandGuide_takesTheFilledLuminanceAboveTheBandBottomAndTheOriginalBelowIt() {
        val original = FloatArray(100) { 0.2f }
        val filled = FloatArray(100) { 0.8f }

        val guide = HangerRemoval.bandGuide(original, filled, bandBottom = bandBottom, w = bandW)

        assertEquals(0.8f, guide[4 * bandW + 9], 0f) // last row of the band
        assertEquals(0.2f, guide[bandBottom * bandW], 0f) // first row below it
    }
}
