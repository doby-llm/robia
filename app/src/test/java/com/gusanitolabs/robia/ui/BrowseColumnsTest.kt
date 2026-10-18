package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.BrowseLayout
import org.junit.Assert.assertEquals
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
        assertEquals(2, nextColumnCount(3, 1.25f, 2..4))
        assertEquals(3, nextColumnCount(4, 1.4f, 2..4))
    }

    @Test
    fun pinchInAtThreshold_addsOneColumn() {
        assertEquals(4, nextColumnCount(3, 0.8f, 2..4))
        assertEquals(3, nextColumnCount(2, 0.5f, 2..4))
    }

    @Test
    fun zoomBetweenThresholds_keepsCount() {
        assertEquals(3, nextColumnCount(3, 1f, 2..4))
        assertEquals(3, nextColumnCount(3, 1.24f, 2..4))
        assertEquals(3, nextColumnCount(3, 0.81f, 2..4))
    }

    @Test
    fun pinchOutAtMinimum_staysAtMinimum() {
        assertEquals(2, nextColumnCount(2, 1.5f, 2..4))
        assertEquals(2, nextColumnCount(2, 1.5f, 2..3))
    }

    @Test
    fun pinchInAtMaximum_staysAtMaximum() {
        assertEquals(4, nextColumnCount(4, 0.5f, 2..4))
        assertEquals(3, nextColumnCount(3, 0.5f, 2..3))
    }

    @Test
    fun outOfRangeCurrent_isClampedIntoRange() {
        assertEquals(3, nextColumnCount(5, 1f, 2..3))
        assertEquals(2, nextColumnCount(1, 1f, 2..4))
    }
}
