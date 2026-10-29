package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewProgressTest {
    private val total = 222

    @Test
    fun progressIsMonotoneWhileScanning() {
        val values = (0..total).map { done -> reviewScanProgress(done, total, scanning = true) }
        values.zipWithNext().forEach { (before, after) -> assertTrue(before <= after) }
    }

    @Test
    fun progressEqualsDoneOverTotalWhileScanning() {
        for (done in 0..total) {
            assertEquals(done.toFloat() / total, reviewScanProgress(done, total, scanning = true), 1e-6f)
        }
        assertEquals(219f / 222f, reviewScanProgress(219, total, scanning = true), 1e-6f)
    }

    @Test
    fun progressReachesOneOnlyWhenFinished() {
        assertTrue(reviewScanProgress(total - 1, total, scanning = true) < 1f)
        assertEquals(1f, reviewScanProgress(total, total, scanning = false), 0f)
        assertEquals(1f, reviewScanProgress(total - 3, total, scanning = false), 0f)
    }

    @Test
    fun zeroTotalIsSafe() {
        assertEquals(0f, reviewScanProgress(0, 0, scanning = true), 0f)
        assertEquals(1f, reviewScanProgress(0, 0, scanning = false), 0f)
    }

    @Test
    fun countsOutsideTheRangeAreClamped() {
        assertEquals(1f, reviewScanProgress(total + 8, total, scanning = true), 0f)
        assertEquals(0f, reviewScanProgress(-3, total, scanning = true), 0f)
    }

    @Test
    fun leftBarGlidesFor400MillisWithoutReducedMotion() {
        assertEquals(400, reviewProgressAnimationMillis(reducedMotion = false))
    }

    @Test
    fun leftBarJumpsUnderReducedMotion() {
        assertEquals(0, reviewProgressAnimationMillis(reducedMotion = true))
    }

    @Test
    fun scanHeaderBodyHasNoAnimation() {
        val body = functionBody(
            resource("app/src/main/java/com/gusanitolabs/robia/ui/ReviewScaffold.kt"),
            "internal fun ReviewProgressHeader(",
        )
        assertFalse(body.contains("animateFloatAsState"))
        assertFalse(body.contains("tween("))
    }

    @Test
    fun leftBarAnimatesWithSnapUnderReducedMotion() {
        val body = functionBody(
            resource("app/src/main/java/com/gusanitolabs/robia/ui/TagReviewCard.kt"),
            "internal fun ReviewLeftHeader(",
        )
        assertTrue(body.contains("animateFloatAsState"))
        assertTrue(body.contains("LocalReducedMotion.current"))
        assertTrue(body.contains("snap()"))
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature not found", start >= 0)
        val next = source.indexOf("\n@Composable", start)
        return source.substring(start, if (next > start) next else source.length)
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
