package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Test

/** Work size of the precise pipeline: shrink to a 1024 long edge, never enlarge (pipeline_v11 thumbnail). */
class PreciseSizingTest {
    @Test
    fun largePhoto_isScaledDownToTheLongEdge() {
        assertEquals(1024 to 768, workSize(4032, 3024))
        assertEquals(768 to 1024, workSize(3024, 4032))
    }

    @Test
    fun photoAtTheLimit_isUnchanged() {
        assertEquals(1024 to 768, workSize(1024, 768))
        assertEquals(1024 to 1024, workSize(1024, 1024))
    }

    @Test
    fun smallPhoto_isNotUpscaled() {
        assertEquals(800 to 600, workSize(800, 600))
        assertEquals(600 to 400, workSize(600, 400))
    }

    @Test
    fun justAboveTheLimit_isScaledDown() {
        assertEquals(1024 to 1, workSize(1025, 1))
    }
}
