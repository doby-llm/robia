package com.gusanitolabs.robia.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AlphaRefinerTest {
    private fun gray(count: Int, value: Int = 128) = IntArray(count) { (0xFF shl 24) or (value shl 16) or (value shl 8) or value }

    @Test
    fun outputStaysInByteRange_forArbitraryInputs() {
        val random = Random(7)
        val w = 37
        val h = 23
        val argb = IntArray(w * h) { random.nextInt() }
        val conf = FloatArray(w * h) { random.nextFloat() }

        val alpha = AlphaRefiner.refine(argb, conf, w, h)

        assertEquals(w * h, alpha.size)
        alpha.forEach { value -> assertTrue(value.toInt() and 0xFF in 0..255) }
    }

    @Test
    fun flatFullConfidence_becomesOpaque() {
        val w = 16
        val h = 16
        val alpha = AlphaRefiner.refine(gray(w * h), FloatArray(w * h) { 1f }, w, h)

        alpha.forEach { value -> assertEquals(255, value.toInt() and 0xFF) }
    }

    @Test
    fun flatZeroConfidence_becomesTransparent() {
        val w = 16
        val h = 16
        val alpha = AlphaRefiner.refine(gray(w * h), FloatArray(w * h), w, h)

        alpha.forEach { value -> assertEquals(0, value.toInt() and 0xFF) }
    }

    @Test
    fun stepEdge_staysMonotoneAcrossTheRow() {
        val w = 64
        val h = 8
        val conf = FloatArray(w * h) { i -> if (i % w < 32) 0f else 1f }

        val alpha = AlphaRefiner.refine(gray(w * h), conf, w, h)

        val row = IntArray(w) { x -> alpha[3 * w + x].toInt() and 0xFF }
        for (x in 1 until w) assertTrue("row not monotone at x=$x: ${row[x - 1]} > ${row[x]}", row[x - 1] <= row[x])
        assertTrue(row.first() < row.last())
    }

    @Test
    fun smoothstep_clampsOutsideEdgesAndIsHalfAtMidpoint() {
        assertEquals(0f, AlphaRefiner.smoothstep(0.35f, 0.65f, 0.2f), 0f)
        assertEquals(0f, AlphaRefiner.smoothstep(0.35f, 0.65f, 0.35f), 0f)
        assertEquals(0.5f, AlphaRefiner.smoothstep(0.35f, 0.65f, 0.5f), 1e-6f)
        assertEquals(1f, AlphaRefiner.smoothstep(0.35f, 0.65f, 0.65f), 0f)
        assertEquals(1f, AlphaRefiner.smoothstep(0.35f, 0.65f, 0.9f), 0f)
    }

    // Coarse smoke bound to catch accidental O(n^2) regressions, not a benchmark (a wall-clock budget below ~1 s is flaky on CI).
    @Test
    fun megapixel_runsWellUnderBudget_smoke() {
        val w = 1024
        val h = 1024
        val random = Random(11)
        val argb = IntArray(w * h) { random.nextInt() }
        val conf = FloatArray(w * h) { random.nextFloat() }
        AlphaRefiner.refine(argb, conf, w, h)

        val started = System.nanoTime()
        AlphaRefiner.refine(argb, conf, w, h)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("1024x1024 refine took ${elapsedMs}ms", elapsedMs < 3000)
    }
}
