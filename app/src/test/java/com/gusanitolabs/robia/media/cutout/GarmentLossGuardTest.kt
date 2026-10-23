package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-K02-2: the garment-loss guard of the precise result, with the S1 comparison of R3-DEC-021 D3. */
class GarmentLossGuardTest {
    @Test
    fun seventyOnePercentOfTheSelectionIsKept() {
        assertFalse(garmentLost(opaque = 701, s1Area = 1000, s2Area = 1000))
    }

    @Test
    fun belowSeventyPercentOfTheSelectionIsGarmentLoss() {
        assertTrue(garmentLost(opaque = 699, s1Area = 1000, s2Area = 1000))
    }

    @Test
    fun aWeakS2CannotHideALostGarment_theS1ComparisonCatchesIt() {
        // Pink-shorts shape (D3): S2 covers about 12 % of the garment, S1 covers all of it, and the result keeps only S2.
        val opaque = 120
        val s1Area = 1000
        val s2Area = 120

        assertTrue(garmentLost(opaque, s1Area, s2Area))
        // The S2-only rule of the first design would have kept this result (opaque is 100 % of S2).
        assertFalse(opaque < 0.70 * s2Area)
    }

    @Test
    fun theLargerOfTheTwoAreasIsTheReference_evenWhenS2IsTheLarger() {
        assertFalse(garmentLost(opaque = 701, s1Area = 100, s2Area = 1000))
        assertTrue(garmentLost(opaque = 699, s1Area = 100, s2Area = 1000))
    }

    @Test
    fun emptyAreasAreNotGarmentLoss() {
        assertFalse(garmentLost(opaque = 0, s1Area = 0, s2Area = 0))
    }
}
