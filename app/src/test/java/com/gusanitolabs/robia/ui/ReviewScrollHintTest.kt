package com.gusanitolabs.robia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewScrollHintTest {
    @Test
    fun hintShowsWhileOptionsAreBelowTheVisiblePart() {
        assertTrue(scrollHintVisible(value = 0, max = 300))
        assertTrue(scrollHintVisible(value = 150, max = 300))
    }

    @Test
    fun hintHidesWhenTheListFitsOrIsScrolledToTheEnd() {
        assertFalse(scrollHintVisible(value = 0, max = 0))
        assertFalse(scrollHintVisible(value = 300, max = 300))
    }
}
