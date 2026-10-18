package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewCompleteTextTest {
    @Test
    fun countOfChangedGarmentsIsShownWithItsKind() {
        assertEquals(ReviewCompleteText(ReviewCompleteKind.Tags, 5), reviewCompleteText(ReviewCompleteKind.Tags, 5))
        assertEquals(ReviewCompleteText(ReviewCompleteKind.Colors, 1), reviewCompleteText(ReviewCompleteKind.Colors, 1))
    }

    @Test
    fun noChangedGarmentsMeansNoCompletionScreen() {
        assertNull(reviewCompleteText(ReviewCompleteKind.Tags, 0))
        assertNull(reviewCompleteText(ReviewCompleteKind.Colors, 0))
        assertNull(reviewCompleteText(ReviewCompleteKind.Colors, -1))
    }

    @Test
    fun screenLeavesAfterOneAndSixTenthsSecondsOrOneAndTwoTenthsWithReducedMotion() {
        assertEquals(1600L, reviewCompleteDurationMs(reducedMotion = false))
        assertEquals(1200L, reviewCompleteDurationMs(reducedMotion = true))
    }
}
