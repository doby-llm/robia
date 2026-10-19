package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
