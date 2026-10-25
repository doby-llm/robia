package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewProgressTest {
    private val total = 222

    @Test
    fun progressIsMonotoneWhileScanning() {
        val values = (0..total).map { done -> reviewScanProgress(done, total, scanning = true) }
        values.zipWithNext().forEach { (before, after) -> assertTrue(before <= after) }
    }

    @Test
    fun progressEqualsDoneOverTotalWhileScanning() {
        for (done in 0..total) {
            assertEquals(done.toFloat() / total, reviewScanProgress(done, total, scanning = true), 1e-6f)
        }
        assertEquals(219f / 222f, reviewScanProgress(219, total, scanning = true), 1e-6f)
    }

    @Test
    fun progressReachesOneOnlyWhenFinished() {
        assertTrue(reviewScanProgress(total - 1, total, scanning = true) < 1f)
        assertEquals(1f, reviewScanProgress(total, total, scanning = false), 0f)
        assertEquals(1f, reviewScanProgress(total - 3, total, scanning = false), 0f)
    }

    @Test
    fun zeroTotalIsSafe() {
        assertEquals(0f, reviewScanProgress(0, 0, scanning = true), 0f)
        assertEquals(1f, reviewScanProgress(0, 0, scanning = false), 0f)
    }

    @Test
    fun countsOutsideTheRangeAreClamped() {
        assertEquals(1f, reviewScanProgress(total + 8, total, scanning = true), 0f)
        assertEquals(0f, reviewScanProgress(-3, total, scanning = true), 0f)
    }
}
