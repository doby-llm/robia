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
}
