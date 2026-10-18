package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.BrowseLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseColumnsTest {
    @Test
    fun photosOnly_rangeIsTwoToFour() {
        assertEquals(2..4, browseColumnRange(BrowseLayout.PhotosOnly))
    }

    @Test
    fun photosWithDetails_rangeIsTwoToThree() {
        assertEquals(2..3, browseColumnRange(BrowseLayout.PhotosWithDetails))
    }

    @Test
    fun pinchOutAtThreshold_removesOneColumn() {
        assertEquals(2, columnsForPinch(3, 1.25f, 2..4))
        assertEquals(3, columnsForPinch(4, 1.3f, 2..4))
    }

    @Test
    fun pinchInAtThreshold_addsOneColumn() {
        assertEquals(4, columnsForPinch(3, 0.8f, 2..4))
        assertEquals(3, columnsForPinch(2, 0.8f, 2..4))
    }

    @Test
    fun partialPinch_keepsCount() {
        assertEquals(3, columnsForPinch(3, 1f, 2..4))
        assertEquals(3, columnsForPinch(3, 1.2f, 2..4))
        assertEquals(3, columnsForPinch(3, 0.85f, 2..4))
    }

    @Test
    fun singleStepFromThree_pinchOutGoesToTwo_pinchInGoesToFour() {
        assertEquals(2, columnsForPinch(3, 1.3f, 2..4))
        assertEquals(4, columnsForPinch(3, 0.7f, 2..4))
    }

    @Test
    fun fourToTwo_inOneContinuousPinchOut() {
        // Ten events of ×1.05 are a cumulative ×1.63: past two steps, reached without lifting the fingers.
        val targets = targetsAlong(start = 4, eventZooms = List(10) { 1.05f }, range = 2..4)

        assertEquals(4, targets[3])
        assertEquals(3, targets[4])
        assertEquals(2, targets.last())
        assertTrue(targets.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun twoToFour_inOneContinuousPinchIn() {
        // Nine events of ×0.95 are a cumulative ×0.63: two steps in, reached without lifting the fingers.
        val targets = targetsAlong(start = 2, eventZooms = List(9) { 0.95f }, range = 2..4)

        assertEquals(3, targets[7])
        assertEquals(4, targets.last())
        assertTrue(targets.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun photosWithDetails_pinchOutClampsAtTwo() {
        assertEquals(2, columnsForPinch(3, 2f, 2..3))
        assertEquals(2, columnsForPinch(3, 10f, 2..3))
    }

    @Test
    fun photosWithDetails_pinchInClampsAtThree() {
        assertEquals(3, columnsForPinch(2, 0.3f, 2..3))
        assertEquals(3, columnsForPinch(3, 0.5f, 2..3))
    }

    @Test
    fun photosOnly_hugeZoomStaysInRange() {
        assertEquals(2, columnsForPinch(4, 10f, 2..4))
        assertEquals(4, columnsForPinch(2, 0.01f, 2..4))
        assertEquals(2, columnsForPinch(3, Float.POSITIVE_INFINITY, 2..4))
    }

    @Test
    fun zoomReversingWithinGesture_returnsToStartCount() {
        // Pinch out one step from 3, then back in to where the fingers started: the count returns to 3.
        assertEquals(3, columnsForPinch(3, 1.3f * 0.7f, 2..4))
        // Pinch out two steps from 4, then back in to the start: the count returns to 4.
        val targets = targetsAlong(start = 4, eventZooms = listOf(1.6f, 0.625f), range = 2..4)
        assertEquals(listOf(2, 4), targets)
    }

    @Test
    fun pinchResidual_isBoundedWithinOneStep() {
        assertEquals(1f, pinchResidual(1f), 1e-6f)
        assertEquals(1.04f, pinchResidual(1.3f), 1e-3f)
        for (zoom in listOf(0.2f, 0.7f, 0.85f, 1.1f, 1.3f, 2.5f, 4f)) {
            val residual = pinchResidual(zoom)
            assertTrue("residual $residual for zoom $zoom", residual in 0.79f..1.26f)
        }
    }

    @Test
    fun favoriteBadge_twoColumnsKeepsTodaysValues() {
        assertEquals(
            FavoriteBadgeMetrics(outerPaddingDp = 8, iconSizeDp = 24, innerPaddingDp = 6),
            favoriteBadgeMetrics(2),
        )
    }

    @Test
    fun favoriteBadge_shrinksMonotonicallyWithColumns() {
        val metrics = (1..6).map(::favoriteBadgeMetrics)

        assertTrue(metrics.zipWithNext().all { (a, b) -> b.outerPaddingDp <= a.outerPaddingDp })
        assertTrue(metrics.zipWithNext().all { (a, b) -> b.iconSizeDp <= a.iconSizeDp })
        assertTrue(metrics.zipWithNext().all { (a, b) -> b.innerPaddingDp <= a.innerPaddingDp })
    }

    @Test
    fun favoriteBadge_hasNoSizeAtOrBelowZero() {
        for (columns in 1..6) {
            val metrics = favoriteBadgeMetrics(columns)
            assertTrue(metrics.outerPaddingDp > 0)
            assertTrue(metrics.iconSizeDp > 0)
            assertTrue(metrics.innerPaddingDp > 0)
        }
    }

    @Test
    fun outOfRangeCurrent_isClampedIntoRange() {
        assertEquals(3, columnsForPinch(5, 1f, 2..3))
        assertEquals(2, columnsForPinch(1, 1f, 2..4))
    }

    /** Column count after each event of a gesture, with the zoom accumulated from the gesture start. */
    private fun targetsAlong(start: Int, eventZooms: List<Float>, range: IntRange): List<Int> {
        var cumulativeZoom = 1f
        return eventZooms.map { zoom ->
            cumulativeZoom *= zoom
            columnsForPinch(start, cumulativeZoom, range)
        }
    }
}
