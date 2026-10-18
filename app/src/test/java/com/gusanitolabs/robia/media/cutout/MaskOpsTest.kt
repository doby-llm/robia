package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG2-2: synthetic masks with known answers for the precise pipeline's pure operations. */
class MaskOpsTest {
    private fun canvas(w: Int, h: Int, fill: (x: Int, y: Int) -> Boolean): BooleanArray =
        BooleanArray(w * h) { i -> fill(i % w, i / w) }

    @Test
    fun thirdPoints_oneCentredPointPerVerticalThird() {
        // A 21×90 block with background above and below: the distance maximum is on the centre column.
        val w = 100
        val h = 130
        val hard = canvas(w, h) { x, y -> x in 40..60 && y in 20..109 }

        val points = MaskOps.thirdPoints(hard, w, h)

        assertEquals(3, points.size)
        assertTrue(points.all { it.x == 50 })
        assertTrue(points[0].y in 20 until 50)
        assertTrue(points[1].y in 50 until 80)
        assertTrue(points[2].y in 80 until 110)
    }

    @Test
    fun thirdPoints_dropsThinStructuresBelowThreeDistancePixels() {
        val w = 40
        val h = 40
        val hard = canvas(w, h) { x, y -> x in 18..21 && y in 5..34 }

        assertTrue(MaskOps.thirdPoints(hard, w, h).isEmpty())
    }

    @Test
    fun overlap_isShareOfTheSelectionInsideTheReference() {
        val w = 10
        val reference = canvas(w, 1) { x, _ -> x < 5 }
        val half = canvas(w, 1) { x, _ -> x < 5 }
        val quarter = canvas(w, 1) { x, _ -> x in 4..7 }

        assertEquals(1f, MaskOps.overlap(half, reference), 0f)
        assertEquals(0.25f, MaskOps.overlap(quarter, reference), 1e-6f)
        assertEquals(0f, MaskOps.overlap(BooleanArray(w), reference), 0f)
    }

    @Test
    fun labelComponents_uses8Connectivity() {
        val w = 6
        val h = 6
        val mask = canvas(w, h) { x, y -> (x == 0 && y == 0) || (x == 1 && y == 1) || (x == 5 && y == 5) }

        val labeling = MaskOps.labelComponents(mask, w, h)

        assertEquals(listOf(0, 2, 1), labeling.areas.toList())
        assertEquals(labeling.labels[0], labeling.labels[w + 1])
        assertFalse(labeling.labels[5 * w + 5] == labeling.labels[0])
    }

    @Test
    fun dropIslands_removesComponentsBelowTenPercentOfTheLargest() {
        val w = 60
        val h = 40
        val selection = FloatArray(w * h)
        for (y in 5 until 15) for (x in 5 until 15) selection[y * w + x] = 0.9f // 100 px
        for (y in 25 until 30) selection[y * w + 50] = 0.9f // 5 px, far away
        val filtered = MaskOps.dropIslands(selection, w, h)

        assertEquals(0.9f, filtered[10 * w + 10], 1e-6f)
        assertEquals(0f, filtered[27 * w + 50], 0f)
    }

    @Test
    fun dropIslands_leavesASingleComponentUntouched() {
        val w = 10
        val h = 10
        val selection = FloatArray(w * h) { if (it % w in 2..7) 0.6f else 0f }

        assertTrue(MaskOps.dropIslands(selection, w, h).contentEquals(selection))
    }

    @Test
    fun dilate_growsSquareKernels() {
        val w = 9
        val h = 9
        val centre = canvas(w, h) { x, y -> x == 4 && y == 4 }

        assertEquals(9, MaskOps.count(MaskOps.dilate(centre, w, h, 3)))
        assertEquals(25, MaskOps.count(MaskOps.dilate(centre, w, h, 5)))
    }

    @Test
    fun bbox_isInclusiveAndNullWhenEmpty() {
        val w = 8
        val h = 8
        val mask = canvas(w, h) { x, y -> x in 2..4 && y in 1..6 }

        assertEquals(MaskBox(2, 1, 4, 6), MaskOps.bbox(mask, w, h))
        assertNull(MaskOps.bbox(BooleanArray(w * h), w, h))
    }

    @Test
    fun refineAlpha_flatInteriorIsOpaqueAndEmptyStaysTransparent() {
        val w = 16
        val h = 16
        val luminance = FloatArray(w * h) { 0.5f }
        val opaque = MaskOps.refineAlpha(luminance, FloatArray(w * h) { 1f }, w, h)
        val empty = MaskOps.refineAlpha(luminance, FloatArray(w * h), w, h)

        assertTrue(opaque.all { it.toInt() and 0xFF == 255 })
        assertTrue(empty.all { it.toInt() == 0 })
    }

    @Test
    fun resizeBilinear_keepsConstantPlanesConstant() {
        val out = MaskOps.resizeBilinear(FloatArray(4) { 0.25f }, 2, 2, 5, 3)

        assertEquals(15, out.size)
        assertTrue(out.all { kotlin.math.abs(it - 0.25f) < 1e-6f })
    }

    @Test
    fun hangerPlausibility_needsWidthAtTheTopOfTheGarment() {
        val garment = MaskBox(0, 100, 99, 400)

        assertTrue(HangerRemoval.isPlausible(MaskBox(10, 110, 70, 120), garment))
        assertFalse(HangerRemoval.isPlausible(MaskBox(10, 110, 30, 120), garment))
        assertFalse(HangerRemoval.isPlausible(MaskBox(10, 300, 90, 330), garment))
    }

    @Test
    fun hangerKill_isRemovedOnlyWhileItIsAtMostFifteenPercentOfTheGarment() {
        val w = 40
        val h = 40
        val box = MaskBox(10, 10, 20, 20)
        val hanger = FloatArray(w * h) { if (it % w in 6..23 && it / w in 6..23) 1f else 0f } // 18×18 inside the margin
        val garment = FloatArray(w * h)

        assertNull(HangerRemoval.killMask(hanger, garment, box, w, h, garmentArea = 1000))
        val kill = HangerRemoval.killMask(hanger, garment, box, w, h, garmentArea = 3000)
        assertNotNull(kill)
        assertEquals(324, kill!!.count { it })
    }

    @Test
    fun bandBottom_isHangerBottomPlusTheLargerOfSixtyPercentAndEightPercent() {
        val box = MaskBox(0, 10, 50, 20)

        assertEquals(60, HangerRemoval.bandBottom(box, garmentHeight = 500, h = 1024))
        assertEquals(28, HangerRemoval.bandBottom(box, garmentHeight = 100, h = 1024))
        assertEquals(30, HangerRemoval.bandBottom(box, garmentHeight = 500, h = 30))
    }

    @Test
    fun applyBand_keepsTheReselectionOnlyAboveTheBandAndClearsTheSupport() {
        val w = 4
        val h = 4
        val reselected = FloatArray(w * h) { 1f }
        val previous = FloatArray(w * h) { 0.5f }
        val support = BooleanArray(w * h).also { it[0] = true; it[1 * w + 1] = true; it[3 * w + 1] = true }

        val out = HangerRemoval.applyBand(reselected, previous, support, bandBottom = 2, w = w)

        assertEquals(0f, out[0], 0f) // support above the band
        assertEquals(1f, out[w + 2], 0f) // re-selection above the band
        assertEquals(0.5f, out[2 * w + 2], 0f) // previous below the band
        assertEquals(0f, out[3 * w + 1], 0f) // support below the band
    }

    @Test
    fun fill_paintsTheSupportWithTheRingColourAndLeavesTheRestAlone() {
        val w = 20
        val h = 20
        val background = (0xFF shl 24) or (200 shl 16) or (100 shl 8) or 50
        val argb = IntArray(w * h) { background }
        val support = canvas(w, h) { x, y -> x in 8..11 && y in 8..11 }

        val filled = HangerRemoval.fill(argb, w, h, support)

        val inside = filled[9 * w + 9]
        assertEquals(200f, ((inside shr 16) and 0xFF).toFloat(), 1f)
        assertEquals(100f, ((inside shr 8) and 0xFF).toFloat(), 1f)
        assertEquals(50f, (inside and 0xFF).toFloat(), 1f)
        assertEquals(background, filled[0])
    }
}
