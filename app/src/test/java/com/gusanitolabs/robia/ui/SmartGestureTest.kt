package com.gusanitolabs.robia.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** The smart gesture: a tap selects its first point, a drag any length selects its path sampled to at most 12 points. */
class SmartGestureTest {
    private val tolerance = 1e-3

    private fun assertNear(expected: Float, actual: Float) {
        assertEquals(expected.toDouble(), actual.toDouble(), tolerance)
    }

    @Test
    fun aTapSelectsOnePoint() {
        // A tap may move a little inside the touch slop; it still selects only where the touch began.
        val tap = listOf(Offset(5f, 5f), Offset(5.2f, 5.1f), Offset(5.1f, 5.3f))
        assertEquals(listOf(Offset(5f, 5f)), smartSelectionPoints(tap, moved = false))
    }

    @Test
    fun aTwoSecondDragStillMakesASelectionRequest() {
        // 2 s of pointer events at 60 Hz, with no time cap: the drag selects its sampled path.
        val drag = (0..120).map { Offset(it * 2f, it * 1f) }
        val points = smartSelectionPoints(drag, moved = true)
        assertEquals(12, points.size)
    }

    @Test
    fun aDragIsSampledToTwelvePointsEvenlyByLength() {
        // 100 points along a line of length 99: the twelve samples sit every 9 pixels, first and last included.
        val path = (0..99).map { Offset(it.toFloat(), 0f) }
        val sampled = samplePath(path, max = 12)
        assertEquals(12, sampled.size)
        for (k in 0 until 12) assertNear(9f * k, sampled[k].x)
    }

    @Test
    fun samplingIsEvenByLengthNotByIndex() {
        // 19 points crowd the first 18 pixels, then one point sits 100 pixels out. Index sampling would bunch the samples at the start.
        val path = (0..18).map { Offset(it.toFloat(), 0f) } + Offset(100f, 0f)
        val sampled = samplePath(path, max = 12)
        for (k in 0 until 12) assertNear(100f * k / 11f, sampled[k].x)
    }

    @Test
    fun samplingKeepsTheFirstAndLastPoint() {
        val path = (0..50).map { Offset(it * 3f, it * 1f) }
        val sampled = samplePath(path, max = 12)
        assertEquals(path.first(), sampled.first())
        assertNear(path.last().x, sampled.last().x)
        assertNear(path.last().y, sampled.last().y)
    }

    @Test
    fun aPathWithinTheLimitIsKeptAsItIs() {
        val path = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 10f))
        assertEquals(path, samplePath(path, max = 12))
    }

    @Test
    fun aStationaryDragSelectsItsStartOnly() {
        val still = List(30) { Offset(7f, 7f) }
        assertEquals(listOf(Offset(7f, 7f)), samplePath(still, max = 12))
        assertEquals(1, smartSelectionPoints(still, moved = true).size)
    }
}
