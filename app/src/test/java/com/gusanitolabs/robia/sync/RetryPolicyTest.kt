package com.gusanitolabs.robia.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {
    @Test
    fun transientResultIsRetriedFiveTimesWithDoublingDelays() = runBlocking {
        val delays = mutableListOf<Long>()
        var attempts = 0

        val result = withRetry(
            sleeper = { delays += it },
            random = { 0.5 },
        ) {
            attempts++
            DriveApiResult.Transient(IOException("HTTP 503"))
        }

        assertEquals(5, attempts)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), delays)
        assertTrue(result is DriveApiResult.Transient)
    }

    @Test
    fun jitterStaysWithinTwentyPercentOfEachDelay() = runBlocking {
        val lowDelays = mutableListOf<Long>()
        withRetry(sleeper = { lowDelays += it }, random = { 0.0 }) {
            DriveApiResult.Transient(null)
        }
        val highDelays = mutableListOf<Long>()
        withRetry(sleeper = { highDelays += it }, random = { 1.0 }) {
            DriveApiResult.Transient(null)
        }

        assertEquals(listOf(800L, 1_600L, 3_200L, 6_400L), lowDelays)
        assertEquals(listOf(1_200L, 2_400L, 4_800L, 9_600L), highDelays)
    }

    @Test
    fun successAfterTransientFailuresStopsRetrying() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = withRetry(sleeper = { delays += it }, random = { 0.5 }) {
            attempts++
            if (attempts < 3) DriveApiResult.Transient(null) else DriveApiResult.Success("ok")
        }

        assertEquals(3, attempts)
        assertEquals(2, delays.size)
        assertEquals(DriveApiResult.Success("ok"), result)
    }

    @Test
    fun nonTransientResultsAreNotRetried() = runBlocking {
        val terminal = listOf<DriveApiResult<Unit>>(
            DriveApiResult.Unauthorized,
            DriveApiResult.NotFound,
            DriveApiResult.StorageFull,
            DriveApiResult.Failure(IllegalStateException("permanent")),
        )
        for (outcome in terminal) {
            var attempts = 0
            val result = withRetry(sleeper = { error("must not sleep") }) {
                attempts++
                outcome
            }
            assertEquals(1, attempts)
            assertEquals(outcome, result)
        }
    }
}
