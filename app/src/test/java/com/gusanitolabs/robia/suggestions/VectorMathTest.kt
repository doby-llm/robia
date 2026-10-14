package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {
    @Test
    fun littleEndianRoundTripKeepsValues() {
        val values = floatArrayOf(1.0f, -2.5f, 0.125f, Float.MIN_VALUE)

        val bytes = values.toLeBytes()

        assertEquals(values.size * 4, bytes.size)
        // 1.0f is 0x3F800000; little-endian stores the high byte last.
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x3F.toByte(), bytes[3])
        assertArrayEquals(values, bytes.toFloatArrayLe(), 0f)
    }

    @Test
    fun base64RoundTripKeepsValues() {
        val values = FloatArray(768) { index -> (index - 384) / 7f }

        assertArrayEquals(values, floatArrayFromBase64(values.toBase64()), 0f)
    }

    @Test
    fun dotAndNormalizedProduceUnitLength() {
        assertEquals(11f, dot(floatArrayOf(1f, 2f), floatArrayOf(3f, 4f)), 1e-6f)

        val unit = floatArrayOf(3f, 4f).normalized()

        assertArrayEquals(floatArrayOf(0.6f, 0.8f), unit, 1e-6f)
        assertEquals(1f, dot(unit, unit), 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f).normalized(), 0f)
    }

    @Test
    fun zScoresUsePopulationStdAndVanishForConstantVectors() {
        val z = zScores(floatArrayOf(1f, 2f, 3f, 4f, 5f))

        // population std of 1..5 is sqrt(2)
        assertArrayEquals(floatArrayOf(-1.41421f, -0.70711f, 0f, 0.70711f, 1.41421f), z, 1e-4f)
        assertEquals(0f, z.sum(), 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), zScores(floatArrayOf(0.5f, 0.5f, 0.5f)), 0f)
        assertTrue(zScores(FloatArray(0)).isEmpty())
    }
}
