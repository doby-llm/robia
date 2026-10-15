package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H02: the Syncing indicator appears at once, stays through back-to-back cycles and hides after 1500 ms of idle. */
class CloudSyncHysteresisTest {
    private fun run(startMs: Long, endMs: Long) = listOf(
        SyncActivitySample(startMs, processing = true),
        SyncActivitySample(endMs, processing = false),
    )

    @Test
    fun showsImmediatelyWhileProcessing() {
        val samples = listOf(SyncActivitySample(1_000L, processing = true))
        assertTrue(syncIndicatorShowing(samples, nowMs = 1_000L))
        assertTrue(syncIndicatorShowing(samples, nowMs = 60_000L))
    }

    @Test
    fun staysVisibleForTheHoldAfterTheRunEnds() {
        val samples = run(startMs = 0L, endMs = 1_000L)
        assertTrue(syncIndicatorShowing(samples, nowMs = 1_000L))
        assertTrue(syncIndicatorShowing(samples, nowMs = 2_499L))
        assertFalse(syncIndicatorShowing(samples, nowMs = 2_500L))
    }

    @Test
    fun backToBackCyclesNeverBlink() {
        // Five-second cycles with 20 ms between them, as in the device log.
        val samples = (0 until 6).flatMap { cycle ->
            val start = cycle * 5_020L
            run(startMs = start, endMs = start + 5_000L)
        }
        for (now in 0L..(6 * 5_020L) step 10L) {
            assertTrue("hidden at $now ms", syncIndicatorShowing(samples, nowMs = now))
        }
    }

    @Test
    fun gapLongerThanTheHoldLetsTheIndicatorHide() {
        val samples = run(startMs = 0L, endMs = 1_000L) + run(startMs = 3_000L, endMs = 3_500L)
        assertFalse(syncIndicatorShowing(samples, nowMs = 2_600L))
        assertTrue(syncIndicatorShowing(samples, nowMs = 3_200L))
    }

    @Test
    fun aNewRunCancelsThePendingHide() {
        val samples = run(startMs = 0L, endMs = 1_000L) + SyncActivitySample(2_000L, processing = true)
        assertTrue(syncIndicatorShowing(samples, nowMs = 2_400L))
        assertTrue(syncIndicatorShowing(samples, nowMs = 9_000L))
    }

    @Test
    fun idleWithoutAnEarlierRunShowsNothing() {
        val samples = listOf(SyncActivitySample(0L, processing = false))
        assertFalse(syncIndicatorShowing(samples, nowMs = 0L))
        assertFalse(syncIndicatorShowing(emptyList(), nowMs = 0L))
    }

    @Test
    fun samplesAfterNowAreIgnored() {
        val samples = run(startMs = 5_000L, endMs = 6_000L)
        assertFalse(syncIndicatorShowing(samples, nowMs = 4_999L))
        assertTrue(syncIndicatorShowing(samples, nowMs = 5_000L))
    }

    @Test
    fun holdLengthCanBeOverridden() {
        val samples = run(startMs = 0L, endMs = 1_000L)
        assertTrue(syncIndicatorShowing(samples, nowMs = 1_400L, holdMs = 500L))
        assertFalse(syncIndicatorShowing(samples, nowMs = 1_500L, holdMs = 500L))
    }

    @Test
    fun browseIconOnlyForSyncingAndUserActionStates() {
        val visible = setOf(CloudSyncHeadline.Syncing, CloudSyncHeadline.Attention, CloudSyncHeadline.Error)
        CloudSyncHeadline.values().forEach { headline ->
            assertEquals(headline.name, headline in visible, headline.showsBrowseIndicator)
        }
    }
}
