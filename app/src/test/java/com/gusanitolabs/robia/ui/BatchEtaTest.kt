package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchEtaTest {
    private var now = 0L
    private val estimator = BatchEtaEstimator { now }

    private fun completeAfter(gapMs: Long) {
        now += gapMs
        estimator.onTerminal()
    }

    @Test
    fun noEstimateBeforeTwoCompletions() {
        assertNull(estimator.estimate(5))
        estimator.onTerminal()
        assertNull(estimator.estimate(5))
    }

    @Test
    fun estimateIsMeanOfLastFourGapsTimesRemaining() {
        estimator.onTerminal()
        listOf(1_000L, 2_000L, 1_000L, 4_000L, 1_000L).forEach(::completeAfter)

        // Last four gaps: 2000, 1000, 4000, 1000 → mean 2000 ms; 10 left → 20 s.
        assertEquals(BatchEta.Seconds(20), estimator.estimate(10))
    }

    @Test
    fun shownValueRisesAtMostTwentyPercentPerUpdate() {
        estimator.onTerminal()
        completeAfter(1_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(10))

        // Raw estimate jumps to 50 s; the shown value may only rise to 12 s (10 s × 1.2).
        completeAfter(9_000L)
        assertEquals(BatchEta.Seconds(20), estimator.estimate(10))
    }

    @Test
    fun shownValueDropsWithoutCap() {
        estimator.onTerminal()
        completeAfter(1_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(10))

        completeAfter(9_000L)
        estimator.estimate(10)
        completeAfter(100L)
        assertEquals(BatchEta.AlmostDone, estimator.estimate(1))
    }

    @Test
    fun secondsAreRoundedUpToTen() {
        estimator.onTerminal()
        completeAfter(23_000L)
        assertEquals(BatchEta.Seconds(30), estimator.estimate(1))
    }

    @Test
    fun underTenSecondsIsAlmostDone() {
        estimator.onTerminal()
        completeAfter(9_000L)
        assertEquals(BatchEta.AlmostDone, estimator.estimate(1))
    }

    @Test
    fun tenSecondsIsTheFirstSecondsValue() {
        estimator.onTerminal()
        completeAfter(10_000L)
        assertEquals(BatchEta.Seconds(10), estimator.estimate(1))
    }

    @Test
    fun ninetySecondsOrMoreShowsRoundedMinutes() {
        estimator.onTerminal()
        completeAfter(89_000L)
        assertEquals(BatchEta.Seconds(90), estimator.estimate(1))
    }

    @Test
    fun ninetySecondsIsTheFirstMinutesValue() {
        estimator.onTerminal()
        completeAfter(90_000L)
        assertEquals(BatchEta.Minutes(2), estimator.estimate(1))
    }

    @Test
    fun minutesRoundToTheNearestMinute() {
        estimator.onTerminal()
        completeAfter(170_000L)
        assertEquals(BatchEta.Minutes(3), estimator.estimate(1))
    }
}
