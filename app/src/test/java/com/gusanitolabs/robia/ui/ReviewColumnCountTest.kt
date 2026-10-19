package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewColumnCountTest {
    @Test
    fun reviewCandidatesAreOneFullWidthColumn() {
        assertEquals(1, reviewColumnCount())
    }
}
