package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.media.PreSg4EdgeReference
import org.junit.Assert.assertArrayEquals
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
        // A 21×90 block (rows 20..109) with background above and below: the distance maximum is on the centre column.
        // Thirds follow bench_v3.third_points: top + (bottom - top) * k / 3 -> [20,49), [49,79), [79,109).
        val w = 100
        val h = 130
        val hard = canvas(w, h) { x, y -> x in 40..60 && y in 20..109 }

        val points = MaskOps.thirdPoints(hard, w, h)

        assertEquals(3, points.size)
        assertTrue(points.all { it.x == 50 })
        assertTrue(points[0].y in 20 until 49)
        assertTrue(points[1].y in 49 until 79)
        assertTrue(points[2].y in 79 until 109)
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

    /** Pixels in row 3 whose alpha is neither clearly 0 nor clearly 255: the width of the edge. */
    private fun transitionWidth(alpha: ByteArray, w: Int): Int =
        (0 until w).count { x -> (alpha[3 * w + x].toInt() and 0xFF) in 6..249 }

    @Test
    fun refineAlpha_sharpProfileGivesANarrowerTransitionThanSoft_onARamp() {
        val w = 64
        val h = 8
        val luminance = FloatArray(w * h) { 0.5f }
        // Selection rises linearly from 0 to 1 across the row.
        val ramp = FloatArray(w * h) { i -> (i % w) / (w - 1f) }

        val soft = MaskOps.refineAlpha(luminance, ramp, w, h, CutoutEdges.Soft)
        val sharp = MaskOps.refineAlpha(luminance, ramp, w, h, CutoutEdges.Sharp)

        assertTrue("sharp ${transitionWidth(sharp, w)} px vs soft ${transitionWidth(soft, w)} px", transitionWidth(sharp, w) < transitionWidth(soft, w))
    }

    @Test
    fun refineAlpha_defaultProfileIsSoft_byteForByteWithThePreSg4Formula() {
        val w = 40
        val h = 12
        val luminance = FloatArray(w * h) { i -> (i % 7) / 7f }
        val selection = FloatArray(w * h) { i -> ((i * 37) % 101) / 100f }
        val pre = PreSg4EdgeReference.maskRefineAlpha(luminance, selection, w, h)

        assertArrayEquals(pre, MaskOps.refineAlpha(luminance, selection, w, h))
        assertArrayEquals(pre, MaskOps.refineAlpha(luminance, selection, w, h, CutoutEdges.Soft))
    }

    @Test
    fun resizeBilinear_keepsConstantPlanesConstant() {
        val out = MaskOps.resizeBilinear(FloatArray(4) { 0.25f }, 2, 2, 5, 3)

        assertEquals(15, out.size)
        assertTrue(out.all { kotlin.math.abs(it - 0.25f) < 1e-6f })
    }

    /** A checkerboard of [cell]-pixel squares with values 20 (top-left) and 220. */
    private fun checker(n: Int, cell: Int): FloatArray =
        FloatArray(n * n) { i -> if (((i % n) / cell + (i / n) / cell) % 2 == 0) 20f else 220f }

    /**
     * Expected values generated with PIL 11.1.0 (numpy 2.2.4) by these commands, run from any directory:
     * 12×12 with 3 px cells: `python3 -I -c "import numpy as np; from PIL import Image; y, x = np.indices((12, 12)); a = np.where(((x // 3) + (y // 3)) % 2 == 0, 20, 220).astype(np.uint8); print(np.asarray(Image.fromarray(a).resize((4, 4), Image.BILINEAR)).ravel().tolist())"`
     * 10×10 with 2 px cells: the same with `np.indices((10, 10))` and `// 2` in both places.
     * The old resizeBilinear is run against the same 12×12 input in resizeBilinear_doesNotMatchPilBilinearOnTheCheckerDownscale,
     * which shows it misses these values.
     */
    @Test
    fun resizeBilinearAntialias_matchesPilBilinearOnAnIntegerDownscale() {
        // PIL: Image.fromarray(checker 12×12, 3 px cells).resize((4, 4), Image.BILINEAR)
        val expected = intArrayOf(64, 162, 78, 176, 162, 89, 151, 78, 78, 151, 89, 162, 176, 78, 162, 64)

        val out = MaskOps.resizeBilinearAntialias(checker(12, 3), 12, 12, 4, 4)

        for (i in expected.indices) assertEquals("pixel $i", expected[i].toFloat(), out[i], 1f)
    }

    /**
     * D4 proof (R3-DEC-025): the OLD resizeBilinear, on the same 12×12 checker as the PIL test above, must NOT produce the PIL
     * values. It samples each 3×3 cell's centre, so it returns 20/220 where PIL returns 64..176; the largest gap is 69
     * (pixels 5, 6, 9, 10). Checked outside the JVM with a float32 replica of the old loop against PIL 11.1.0 (numpy 2.2.4).
     */
    @Test
    fun resizeBilinear_doesNotMatchPilBilinearOnTheCheckerDownscale() {
        // PIL: Image.fromarray(checker 12×12, 3 px cells).resize((4, 4), Image.BILINEAR), as in the PIL test above.
        val pil = intArrayOf(64, 162, 78, 176, 162, 89, 151, 78, 78, 151, 89, 162, 176, 78, 162, 64)

        val old = MaskOps.resizeBilinear(checker(12, 3), 12, 12, 4, 4)

        val matches = pil.indices.all { kotlin.math.abs(old[it] - pil[it]) <= 1f }
        assertFalse("old resizeBilinear = ${old.toList()}, PIL = ${pil.toList()}", matches)
    }

    @Test
    fun resizeBilinearAntialias_matchesPilBilinearOnANonIntegerDownscale() {
        // PIL: Image.fromarray(checker 10×10, 2 px cells).resize((4, 4), Image.BILINEAR)
        val expected = intArrayOf(100, 125, 125, 100, 125, 119, 119, 125, 125, 119, 119, 125, 100, 125, 125, 100)

        val out = MaskOps.resizeBilinearAntialias(checker(10, 2), 10, 10, 4, 4)

        for (i in expected.indices) assertEquals("pixel $i", expected[i].toFloat(), out[i], 1f)
    }

    @Test
    fun resizeBilinearAntialias_keepsConstantPlanesConstant() {
        val out = MaskOps.resizeBilinearAntialias(FloatArray(800 * 600) { 0.25f }, 800, 600, 320, 320)

        assertTrue(out.all { kotlin.math.abs(it - 0.25f) < 1e-5f })
    }

    @Test
    fun samInputSize_scalesTheLongEdgeToExactly1024AndRoundsTheOtherSide() {
        // Work sizes: already 1024 long edge (no change), 1024 × 768 (no change), 800 × 600 (scaled by 1.28), portrait 600 × 800.
        assertEquals(576 to 1024, MaskOps.samInputSize(576, 1024))
        assertEquals(1024 to 768, MaskOps.samInputSize(1024, 768))
        assertEquals(1024 to 768, MaskOps.samInputSize(800, 600))
        assertEquals(768 to 1024, MaskOps.samInputSize(600, 800))
    }

    @Test
    fun samScale_isOneForA1024LongEdgeAndOneDotTwoEightForA800x600() {
        assertEquals(1.28, MaskOps.samScale(800, 600), 1e-9)
        assertEquals(1.0, MaskOps.samScale(576, 1024), 1e-12)
        assertEquals(1.0, MaskOps.samScale(1024, 768), 1e-12)
    }

    @Test
    fun samPrompt_scalesPointsAndBoxCornersForWorkSizes576x1024_1024x768_and_800x600() {
        // bench_v6.MobileSam: points and box corners are multiplied by samScale; positive labels 1, negative 0, box 2 and 3.
        val positive = listOf(MaskPoint(100, 50))
        val negative = listOf(MaskPoint(7, 9))
        val box = MaskBox(10, 20, 30, 40)

        val portrait = MaskOps.samPrompt(576, 1024, positive, negative, box)
        assertArrayEquals(floatArrayOf(100f, 50f, 7f, 9f, 10f, 20f, 30f, 40f), portrait.coords, 1e-4f)
        assertArrayEquals(floatArrayOf(1f, 0f, 2f, 3f), portrait.labels, 0f)

        val landscape = MaskOps.samPrompt(1024, 768, positive, negative, box)
        assertArrayEquals(floatArrayOf(100f, 50f, 7f, 9f, 10f, 20f, 30f, 40f), landscape.coords, 1e-4f)

        // 800 × 600: scale 1.28, so 100 → 128, 50 → 64, and the box corners 10 → 12.8 … 40 → 51.2.
        val scaled = MaskOps.samPrompt(800, 600, positive, negative, box)
        assertArrayEquals(floatArrayOf(128f, 64f, 8.96f, 11.52f, 12.8f, 25.6f, 38.4f, 51.2f), scaled.coords, 1e-3f)
        assertArrayEquals(floatArrayOf(1f, 0f, 2f, 3f), scaled.labels, 0f)
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
    fun applyBand_blendsTheReselectionIntoThePreviousOverThreeRowsAndClearsTheSupport() {
        val w = 4
        val h = 4
        val reselected = FloatArray(w * h) { 1f }
        val previous = FloatArray(w * h) { 0.5f }
        val support = BooleanArray(w * h).also { it[0] = true; it[1 * w + 1] = true; it[3 * w + 1] = true }

        val out = HangerRemoval.applyBand(reselected, previous, support, bandBottom = 2, w = w)

        assertEquals(0f, out[0], 0f) // support above the band
        assertEquals(1f, out[2], 0f) // re-selection two rows above the band bottom
        assertEquals(5f / 6f, out[w + 2], 1e-6f) // one row above: 2/3 re-selection
        assertEquals(2f / 3f, out[2 * w + 2], 1e-6f) // first row below: 1/3 re-selection
        assertEquals(0.5f, out[3 * w + 2], 0f) // two rows below: previous only
        assertEquals(0f, out[3 * w + 1], 0f) // support below the band
    }

    /** Soft alpha pixels (not clearly 0 or 255) per row, counted in [cols] and averaged over [rows] (the width helper of compare.py). */
    private fun edgeWidth(alpha: ByteArray, w: Int, rows: IntRange, cols: IntRange): Double =
        rows.map { y -> cols.count { x -> (alpha[y * w + x].toInt() and 0xFF) in 6..249 }.toDouble() }.average()

    /**
     * V-K03-1: a garment with a hanger inside its top band, the hanger's edges well away from the garment sides. On each side
     * the band's edge is no wider than the body's (≤ 1.5×), so the band-local guide and the 3-row blend do not soften it.
     */
    @Test
    fun hangerBand_sideEdgesInTheBandAreNoWiderThanInTheBody() {
        val w = 64
        val h = 48
        val bandBottom = 12
        val garmentColumns = 8..55
        val hanger = BooleanArray(w * h) { i -> i / w in 0..9 && i % w in 24..39 }
        // The original photo shows the dark hanger inside the garment; the filled photo has the garment colour there.
        val original = FloatArray(w * h) { i -> if (i % w in garmentColumns && !hanger[i]) 0.6f else 0.1f }
        val filled = FloatArray(w * h) { i -> if (i % w in garmentColumns) 0.6f else 0.1f }
        val selection = FloatArray(w * h) { i -> if (i % w in garmentColumns) 1f else 0f }
        val support = BooleanArray(w * h) { hanger[it] }
        val band = HangerRemoval.applyBand(selection, selection, support, bandBottom, w)

        val alpha = MaskOps.refineAlpha(HangerRemoval.bandGuide(original, filled, bandBottom, w), band, w, h)

        val leftZone = 0 until 16
        val rightZone = 48 until 64
        for (zone in listOf(leftZone, rightZone)) {
            val inBand = edgeWidth(alpha, w, 2 until 10, zone)
            val body = edgeWidth(alpha, w, 20 until 40, zone)
            assertTrue("band $inBand px vs body $body px in $zone", body > 0.0 && inBand <= 1.5 * body)
        }
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
