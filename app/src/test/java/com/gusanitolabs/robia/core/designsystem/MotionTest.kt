package com.gusanitolabs.robia.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class MotionTest {
    @Test
    fun reducedMotion_isOnlyTheZeroAnimatorScale() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
    }

    @Test
    fun sparklePositions_isDeterministicForASeed() {
        assertEquals(sparklePositions(7, 0, 0.5f), sparklePositions(7, 0, 0.5f))
        assertEquals(sparklePositions(7, 3, 1f), sparklePositions(7, 3, 1f))
    }

    @Test
    fun sparklePositions_seedRotatesTheFixedAngles() {
        assertNotEquals(sparklePositions(7, 0, 0f).first(), sparklePositions(7, 1, 0f).first())
    }

    @Test
    fun sparklePositions_radiiStayWithin24To56Dp() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            sparklePositions(count = 7, seed = 0, progress = progress).forEach { (x, y) ->
                val radius = hypot(x.toDouble(), y.toDouble())
                assertTrue("radius $radius at progress $progress", radius >= 24.0 - EPSILON && radius <= 56.0 + EPSILON)
            }
        }
    }

    @Test
    fun sparklePositions_movesFromInnerToOuterRadiusAcrossTheBurst() {
        val start = sparklePositions(count = 7, seed = 0, progress = 0f).map { (x, y) -> hypot(x.toDouble(), y.toDouble()) }
        val end = sparklePositions(count = 7, seed = 0, progress = 1f).map { (x, y) -> hypot(x.toDouble(), y.toDouble()) }
        start.forEach { assertTrue(abs(it - 24.0) < EPSILON) }
        end.forEach { assertTrue(abs(it - 56.0) < EPSILON) }
    }

    @Test
    fun sparklePositions_returnsOneGlyphPerCount() {
        assertEquals(8, sparklePositions(count = 8, seed = 0, progress = 0.5f).size)
    }

    @Test
    fun sparkleBurst_doesNotPlayWhenReducedMotionIsOn() {
        assertTrue(sparkleBurstPlays(reducedMotion = false))
        assertFalse(sparkleBurstPlays(reducedMotion = true))
    }

    @Test
    fun sparkleBurst_lastsAtMostNineHundredMillis() {
        assertTrue(SPARKLE_DURATION_MILLIS <= 900)
    }

    @Test
    fun sparkleBurstAfterProcessing_playsOnlyWhenProcessingEndsWithTheButtonShown() {
        assertTrue(sparkleBurstAfterProcessing(wasProcessing = true, isProcessing = false, buttonVisible = true))
        assertFalse(sparkleBurstAfterProcessing(wasProcessing = true, isProcessing = false, buttonVisible = false))
        assertFalse(sparkleBurstAfterProcessing(wasProcessing = false, isProcessing = true, buttonVisible = true))
        assertFalse(sparkleBurstAfterProcessing(wasProcessing = true, isProcessing = true, buttonVisible = true))
        assertFalse(sparkleBurstAfterProcessing(wasProcessing = false, isProcessing = false, buttonVisible = true))
    }

    private companion object {
        const val EPSILON = 1e-3
    }
}
