package com.gusanitolabs.robia.media.cutout

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CutoutEngineGateTest {
    @Test
    fun concurrentPreciseRuns_neverOverlap() = runTest {
        var running = 0
        var maxRunning = 0

        List(3) {
            async {
                CutoutEngineRegistry.withPreciseGate {
                    running++
                    maxRunning = maxOf(maxRunning, running)
                    delay(100)
                    running--
                }
            }
        }.awaitAll()

        assertEquals(1, maxRunning)
        assertEquals(0, running)
    }
}
