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
        // The S2-only denominator of the first design would have kept it: with S1 taken as S2, the same result passes.
        assertFalse(garmentLost(opaque, s1Area = s2Area, s2Area = s2Area))
    }

    @Test
    fun anOversizedS1RejectsAGoodResult_theKnownTradeOffOfTheLargerDenominator() {
        // R3-DEC-022: the denominator is max(S1, S2), so an S1 that covers twice the garment rejects a good result (kept 1000 of 1000).
        // The rejection falls back to ML Kit, which is safe; it does not lose data.
        val goodOpaque = 1000
        val oversizedS1 = 2000
        val s2Area = 1000

        assertTrue(garmentLost(goodOpaque, s1Area = oversizedS1, s2Area = s2Area))
        assertFalse(garmentLost(goodOpaque, s1Area = s2Area, s2Area = s2Area))
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
