package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The process-wide latch: a LinkageError trips it, and a tripped latch never runs the precise code again. */
class PreciseEngineLatchTest {
    @Test
    fun successfulBlock_returnsItsValueAndLeavesTheLatchUntripped() {
        val latch = PreciseEngineLatch()

        assertEquals("ok", latch.guard { "ok" })
        assertFalse(latch.isTripped)
    }

    @Test
    fun linkageError_isRethrownAndTripsTheLatch() {
        val latch = PreciseEngineLatch()
        val error = ExceptionInInitializerError(IllegalStateException("no caller found on the stack"))

        val thrown = assertThrows(ExceptionInInitializerError::class.java) { latch.guard { throw error } }

        assertSame(error, thrown)
        assertTrue(latch.isTripped)
    }

    @Test
    fun trippedLatch_throwsWithoutRunningTheBlockAgain() {
        val latch = PreciseEngineLatch()
        val first = NoClassDefFoundError("first")
        assertThrows(NoClassDefFoundError::class.java) { latch.guard { throw first } }
        var blockRuns = 0

        val thrown = assertThrows(IllegalStateException::class.java) { latch.guard { blockRuns++ } }

        assertEquals(0, blockRuns)
        assertSame(first, thrown.cause)
        assertEquals("Precise cutout unavailable after NoClassDefFoundError", thrown.message)
    }

    @Test
    fun otherThrowables_passThroughWithoutTrippingTheLatch() {
        val latch = PreciseEngineLatch()

        assertThrows(OutOfMemoryError::class.java) { latch.guard { throw OutOfMemoryError("heap") } }
        assertThrows(IllegalStateException::class.java) { latch.guard { error("selector") } }

        assertFalse(latch.isTripped)
    }
}
