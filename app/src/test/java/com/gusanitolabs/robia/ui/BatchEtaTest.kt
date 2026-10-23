package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchEtaTest {
    private var now = 0L
    // The batch starts at 0 ms; the estimator stamps that as its first completion time.
    private val estimator = BatchEtaEstimator { now }

    private fun completeAfter(gapMs: Long) {
        now += gapMs
        estimator.onTerminal()
    }

    private fun etaMs(eta: BatchEta?): Long = when (eta) {
        null, BatchEta.AlmostDone -> 0L
        is BatchEta.Seconds -> eta.s * 1_000L
        is BatchEta.Minutes -> eta.m * 60_000L
    }

    @Test
    fun noEstimateBeforeFirstCompletion() {
        assertNull(estimator.estimate(5))
    }

    @Test
    fun firstEstimateUsesTheBatchStartAsTheFirstGap() {
        completeAfter(30_000L)

        // One gap of 30 s from the batch start; 10 left → 300 s.
        assertEquals(BatchEta.Minutes(5), estimator.estimate(10))
    }

    @Test
    fun firstValueIsNotLowWithTwoParallelCutouts() {
        // Reference sequence: cutout 40 s, suggest 2 s, two cutouts in parallel, 58 photos left.
        // Steady state is one garment every 20 s, so the true time left is 1 160 s.
        completeAfter(42_000L)

        val firstEta = estimator.estimate(58)

        assertTrue(etaMs(firstEta) >= 0.8 * 1_160_000L)
    }

    @Test
    fun estimateIsMeanOfLastFourGapsTimesRemaining() {
        completeAfter(1_000L)
        listOf(2_000L, 1_000L, 4_000L, 1_000L).forEach(::completeAfter)

        // Last four gaps: 2000, 1000, 4000, 1000 → mean 2000 ms; 10 left → 20 s.
        assertEquals(BatchEta.Seconds(20), estimator.estimate(10))
    }

    @Test
    fun shownValueRisesAtMostTwentyPercentPerUpdate() {
        completeAfter(1_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(10))

        // Raw estimate jumps to 50 s; the shown value may only rise to 12 s (10 s × 1.2).
        completeAfter(9_000L)
        assertEquals(BatchEta.Seconds(20), estimator.estimate(10))
    }

    @Test
    fun shownValueDropsWithoutCap() {
        completeAfter(1_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(10))

        completeAfter(9_000L)
        assertEquals(BatchEta.Seconds(20), estimator.estimate(10))

        completeAfter(6_000L)
        assertEquals(BatchEta.AlmostDone, estimator.estimate(1))
    }

    @Test
    fun displayedTextChangesAtMostOncePerFiveSeconds() {
        completeAfter(10_000L)
        assertEquals(BatchEta.Minutes(2), estimator.estimate(10))

        // 1 s later the raw value would read 50 s, but the text is only 1 s old.
        completeAfter(1_000L)
        assertEquals(BatchEta.Minutes(2), estimator.estimate(9))

        completeAfter(1_000L)
        assertEquals(BatchEta.Minutes(2), estimator.estimate(8))

        // 6 s after the last text change the new text is shown.
        completeAfter(4_000L)
        assertEquals(BatchEta.Seconds(30), estimator.estimate(7))
    }

    @Test
    fun secondsAreRoundedUpToTen() {
        completeAfter(23_000L)
        assertEquals(BatchEta.Seconds(30), estimator.estimate(1))
    }

    @Test
    fun underTenSecondsIsAlmostDone() {
        completeAfter(9_000L)
        assertEquals(BatchEta.AlmostDone, estimator.estimate(1))
    }

    @Test
    fun tenSecondsIsTheFirstSecondsValue() {
        completeAfter(10_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(1))
    }

    @Test
    fun ninetySecondsOrMoreShowsRoundedMinutes() {
        completeAfter(89_000L)
        assertEquals(BatchEta.Seconds(90), estimator.estimate(1))
    }

    @Test
    fun ninetySecondsIsTheFirstMinutesValue() {
        completeAfter(90_000L)
        assertEquals(BatchEta.Minutes(2), estimator.estimate(1))
    }

    @Test
    fun minutesRoundToTheNearestMinute() {
        completeAfter(170_000L)
        assertEquals(BatchEta.Minutes(3), estimator.estimate(1))
    }
}
