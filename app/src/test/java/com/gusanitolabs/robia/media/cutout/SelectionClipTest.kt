package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionClipTest {
    private val w = 10
    private val h = 10

    /** A selection that is 1 inside the blocks (inclusive x0..x1, y0..y1) and [outside] everywhere else. */
    private fun blobs(outside: Float, vararg blocks: IntArray): FloatArray = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (blocks.any { (x0, y0, x1, y1) -> x in x0..x1 && y in y0..y1 }) 1f else outside
    }

    private val blobA = intArrayOf(1, 1, 3, 3)
    private val blobB = intArrayOf(6, 6, 8, 8)

    @Test
    fun tapInsideOneBlobKeepsOnlyThatBlob() {
        val union = blobs(0f, blobA, blobB)
        val clipped = clipToTouched(union, w, h, listOf(2 to 2))
        assertEquals(blobs(0f, blobA).toList(), clipped.toList())
    }

    @Test
    fun tapsInBothBlobsKeepBoth() {
        val union = blobs(0f, blobA, blobB)
        val clipped = clipToTouched(union, w, h, listOf(2 to 2, 7 to 7))
        assertEquals(union.toList(), clipped.toList())
    }

    @Test
    fun tapOnPixelOutsideEveryBlobKeepsNothing() {
        val union = blobs(0f, blobA, blobB)
        val clipped = clipToTouched(union, w, h, listOf(0 to 0))
        assertTrue(clipped.all { it == 0f })
    }

    @Test
    fun componentsAreEightConnected() {
        // Two pixels that touch only at a corner are one component: a tap on one keeps the other.
        val union = FloatArray(w * h)
        union[1 * w + 1] = 1f
        union[2 * w + 2] = 1f
        val clipped = clipToTouched(union, w, h, listOf(1 to 1))
        assertEquals(1f, clipped[2 * w + 2], 0f)
    }

    @Test
    fun softValuesInsideAKeptComponentArePreserved() {
        val union = blobs(0f, blobA)
        union[2 * w + 2] = 0.7f
        union[3 * w + 3] = 0.9f
        val clipped = clipToTouched(union, w, h, listOf(1 to 1))
        assertEquals(0.7f, clipped[2 * w + 2], 0f)
        assertEquals(0.9f, clipped[3 * w + 3], 0f)
    }

    @Test
    fun valuesBelowThresholdAreDropped() {
        val union = blobs(0f, blobA)
        union[5 * w + 5] = 0.4f
        val clipped = clipToTouched(union, w, h, listOf(2 to 2))
        assertEquals(0f, clipped[5 * w + 5], 0f)
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
