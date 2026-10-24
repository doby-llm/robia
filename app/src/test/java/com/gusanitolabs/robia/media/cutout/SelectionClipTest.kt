package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionClipTest {
    private val w = 10
    private val h = 10

    /** An alpha that is 1 inside the blocks (inclusive x0..x1, y0..y1) and 0 everywhere else. */
    private fun alpha(vararg blocks: IntArray): FloatArray = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (blocks.any { (x0, y0, x1, y1) -> x in x0..x1 && y in y0..y1 }) 1f else 0f
    }

    /** The garment is a 6 × 6 block (36 px); the loose pieces are 4 px each and at least one transparent column away from it. */
    private val garment = intArrayOf(0, 0, 5, 5)
    private val looseA = intArrayOf(7, 1, 8, 2)
    private val looseB = intArrayOf(7, 7, 8, 8)

    private fun pickOf(alpha: FloatArray, touched: List<Pair<Int, Int>>, snapRadiusPx: Int = 0): ComponentPick =
        componentsToRemove(alpha, w, h, touched, snapRadiusPx)

    @Test
    fun tapOnALoosePieceRemovesOnlyThatPiece() {
        val pick = pickOf(alpha(garment, looseA, looseB), listOf(7 to 1))
        assertEquals(alpha(looseA).toList(), pick.mask.toList())
        assertFalse(pick.touchedPrimary)
        assertEquals(1, pick.removedComponents)
    }

    @Test
    fun tapOnTheGarmentRemovesNothingAndSaysSo() {
        val pick = pickOf(alpha(garment, looseA, looseB), listOf(2 to 2))
        assertTrue(pick.mask.all { it == 0f })
        assertTrue(pick.touchedPrimary)
        assertEquals(0, pick.removedComponents)
    }

    @Test
    fun aPixelOfExactlyHalfAlphaIsNotPartOfAPiece() {
        val base = alpha(garment, looseA)
        base[1 * w + 9] = 0.5f
        assertEquals(alpha(looseA).toList(), pickOf(base, listOf(7 to 1)).mask.toList())
    }

    @Test
    fun aPixelJustAboveHalfAlphaJoinsItsPiece() {
        val base = alpha(garment, looseA)
        base[1 * w + 9] = 0.51f
        assertEquals(1f, pickOf(base, listOf(7 to 1)).mask[1 * w + 9], 0f)
    }

    @Test
    fun diagonalPixelsAreOnePiece() {
        // (7, 7) and (8, 8) touch only at a corner: a tap on one removes both.
        val base = alpha(garment)
        base[7 * w + 7] = 1f
        base[8 * w + 8] = 1f
        val pick = pickOf(base, listOf(7 to 7))
        assertEquals(1f, pick.mask[8 * w + 8], 0f)
        assertEquals(1, pick.removedComponents)
    }

    @Test
    fun theLargestPieceIsThePrimaryWhereverItSits() {
        val base = alpha(intArrayOf(0, 0, 1, 1), intArrayOf(5, 5, 8, 8))
        assertTrue(pickOf(base, listOf(6 to 6)).touchedPrimary)
        assertEquals(alpha(intArrayOf(0, 0, 1, 1)).toList(), pickOf(base, listOf(0 to 0)).mask.toList())
    }

    @Test
    fun equalAreasTakeTheFirstPieceInRasterOrderAsPrimary() {
        val base = alpha(intArrayOf(0, 0, 1, 1), intArrayOf(5, 5, 6, 6))
        // Both pieces have 4 px; the one starting at (0, 0) comes first in raster order and is the primary.
        val onPrimary = pickOf(base, listOf(0 to 0))
        assertTrue(onPrimary.touchedPrimary)
        assertEquals(0, onPrimary.removedComponents)
        assertEquals(alpha(intArrayOf(5, 5, 6, 6)).toList(), pickOf(base, listOf(5 to 5)).mask.toList())
    }

    @Test
    fun aTapOnATransparentPixelWithinTheRadiusPicksTheNearestPiece() {
        val base = alpha(garment, looseB)
        // (9, 9) is transparent. The corner (8, 8) of looseB is √2 away; the garment is far beyond 2 px.
        assertEquals(alpha(looseB).toList(), pickOf(base, listOf(9 to 9), snapRadiusPx = 2).mask.toList())
    }

    @Test
    fun aTieBetweenTwoPiecesGoesToTheSmallerY() {
        val upper = intArrayOf(3, 7, 3, 7)
        val lower = intArrayOf(3, 9, 3, 9)
        // (3, 8) is transparent and one pixel from both pieces; the upper one has the smaller y.
        assertEquals(alpha(upper).toList(), pickOf(alpha(garment, upper, lower), listOf(3 to 8), snapRadiusPx = 2).mask.toList())
    }

    @Test
    fun aTapFartherThanTheRadiusSelectsNothingAndIsNotAGarmentTouch() {
        val base = alpha(garment, looseB)
        // (9, 0) is transparent, 4 px from the garment and far from looseB: nothing lies within 2 px.
        val pick = pickOf(base, listOf(9 to 0), snapRadiusPx = 2)
        assertEquals(0, pick.removedComponents)
        assertFalse(pick.touchedPrimary)
    }

    @Test
    fun aGestureRemovesEveryPieceItReachesAndLeavesTheGarment() {
        val pick = pickOf(alpha(garment, looseA, looseB), listOf(2 to 2, 7 to 1, 8 to 8))
        assertEquals(alpha(looseA, looseB).toList(), pick.mask.toList())
        assertTrue(pick.touchedPrimary)
        assertEquals(2, pick.removedComponents)
    }

    @Test
    fun snapRadiusIsSixteenDpInPhotoPixelsAndClampedToTwoTo48() {
        assertEquals(21, snapRadiusPhotoPx(16f, density = 2.625f, fitScale = 2f, zoom = 1f))
        assertEquals(8, snapRadiusPhotoPx(16f, density = 1f, fitScale = 1f, zoom = 2f))
        assertEquals(6, snapRadiusPhotoPx(16f, density = 1f, fitScale = 3f, zoom = 1f))
        assertEquals(2, snapRadiusPhotoPx(1f, density = 1f, fitScale = 1f, zoom = 4f))
        assertEquals(48, snapRadiusPhotoPx(16f, density = 3f, fitScale = 0.5f, zoom = 1f))
    }

    @Test
    fun aRemoveTapOnTheGarmentShowsTheWholeGarmentHint() {
        val base = alpha(garment, looseA)
        val pick = pickOf(base, listOf(2 to 2))
        assertEquals(SmartTapOutcome.WholeGarmentHint, smartTapOutcome(RefineSession.Mode.Remove, pick, base))
    }

    @Test
    fun aRemoveTapOnNothingChangesNothingAndShowsNoHint() {
        val base = alpha(garment, looseA)
        val pick = pickOf(base, listOf(9 to 9))
        assertEquals(SmartTapOutcome.NothingToRemove, smartTapOutcome(RefineSession.Mode.Remove, pick, base))
    }

    @Test
    fun aRemoveTapOnAPieceIsRemoved() {
        // 4 px of garment and one 4 px piece: removing the piece takes 4 of the 8 opaque pixels, under the 60 % guard.
        val base = alpha(intArrayOf(0, 0, 1, 1), intArrayOf(4, 4, 5, 5))
        val pick = pickOf(base, listOf(4 to 4))
        assertEquals(SmartTapOutcome.Remove, smartTapOutcome(RefineSession.Mode.Remove, pick, base))
    }

    @Test
    fun aGestureThatWouldRemoveMostOfTheOpaqueGarmentIsRefused() {
        // 4 px of garment and two 4 px pieces: a drag over both removes 8 of the 12 opaque pixels (67 %), over the 60 % guard.
        val base = alpha(intArrayOf(0, 0, 1, 1), intArrayOf(4, 4, 5, 5), intArrayOf(8, 8, 9, 9))
        val pick = pickOf(base, listOf(4 to 4, 8 to 8))
        assertEquals(2, pick.removedComponents)
        assertEquals(SmartTapOutcome.WholeGarmentHint, smartTapOutcome(RefineSession.Mode.Remove, pick, base))
    }

    @Test
    fun keepNeverRemovesAndShowsTheKeepHint() {
        val base = alpha(garment, looseA)
        val pick = pickOf(base, listOf(7 to 1))
        assertEquals(SmartTapOutcome.KeepHint, smartTapOutcome(RefineSession.Mode.Keep, pick, base))
    }

    @Test
    fun removesMostOfGarmentAtSixtyPercentOfOpaquePixels() {
        val alpha = FloatArray(100) { 1f }
        val selection = FloatArray(100) { i -> if (i < 60) 1f else 0f }
        assertTrue(removesMostOfGarment(selection, alpha))
    }

    @Test
    fun doesNotRemoveMostOfGarmentAtFiftyNinePercentOfOpaquePixels() {
        val alpha = FloatArray(100) { 1f }
        val selection = FloatArray(100) { i -> if (i < 59) 1f else 0f }
        assertFalse(removesMostOfGarment(selection, alpha))
    }

    @Test
    fun transparentPixelsDoNotCountTowardTheGarment() {
        // 100 opaque pixels and 100 transparent ones: selecting all of the transparent ones does not make a whole-garment removal.
        val alpha = FloatArray(200) { i -> if (i < 100) 1f else 0f }
        val selection = FloatArray(200) { i -> if (i < 59 || i >= 100) 1f else 0f }
        assertFalse(removesMostOfGarment(selection, alpha))
    }

    @Test
    fun aHalfTransparentGarmentHasNoOpaquePixelToProtect() {
        val alpha = FloatArray(100) { 0.5f }
        val selection = FloatArray(100) { 1f }
        assertFalse(removesMostOfGarment(selection, alpha))
    }
}
