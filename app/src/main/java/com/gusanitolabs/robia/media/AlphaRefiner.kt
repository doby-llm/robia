package com.gusanitolabs.robia.media

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns ML Kit's soft foreground confidence into a cleaner alpha channel (SG1, soft edges).
 * Pure Kotlin on primitive arrays so it can run on the JVM test classpath.
 */
internal object AlphaRefiner {
    private const val SMOOTH_LOW = 0.35f
    private const val SMOOTH_HIGH = 0.65f
    private const val INTERIOR = 0.97f
    private const val GUIDED_EPS = 1e-3f
    private const val REFERENCE_EDGE_PX = 1024f

    /** [argb] supplies the guide (luminance), [conf] is the foreground confidence in 0..1, both w×h. */
    fun refine(argb: IntArray, conf: FloatArray, w: Int, h: Int): ByteArray {
        val count = w * h
        require(argb.size == count && conf.size == count) { "Buffers must be ${w}x$h" }

        val input = FloatArray(count) { i -> smoothstep(SMOOTH_LOW, SMOOTH_HIGH, conf[i]) }
        val guide = FloatArray(count) { i ->
            val color = argb[i]
            (0.299f * ((color shr 16) and 0xFF) + 0.587f * ((color shr 8) and 0xFF) + 0.114f * (color and 0xFF)) / 255f
        }
        val radius = (3f * max(w, h) / REFERENCE_EDGE_PX).roundToInt()
        val filtered = guidedFilter(guide, input, w, h, radius, GUIDED_EPS)

        return ByteArray(count) { i ->
            val alpha = if (filtered[i] > INTERIOR) 1f else filtered[i].coerceIn(0f, 1f)
            (alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun guidedFilter(guide: FloatArray, input: FloatArray, w: Int, h: Int, radius: Int, eps: Float): FloatArray {
        val count = w * h
        val meanGuide = boxMean(guide, w, h, radius)
        val meanInput = boxMean(input, w, h, radius)
        val meanGuideSquared = boxMean(FloatArray(count) { i -> guide[i] * guide[i] }, w, h, radius)
        val meanGuideInput = boxMean(FloatArray(count) { i -> guide[i] * input[i] }, w, h, radius)

        val slope = FloatArray(count)
        val offset = FloatArray(count)
        for (i in 0 until count) {
            val variance = meanGuideSquared[i] - meanGuide[i] * meanGuide[i]
            val covariance = meanGuideInput[i] - meanGuide[i] * meanInput[i]
            slope[i] = covariance / (variance + eps)
            offset[i] = meanInput[i] - slope[i] * meanGuide[i]
        }

        val meanSlope = boxMean(slope, w, h, radius)
        val meanOffset = boxMean(offset, w, h, radius)
        return FloatArray(count) { i -> meanSlope[i] * guide[i] + meanOffset[i] }
    }

    /** Mean over a (2r+1)² window clipped at the borders, using a summed-area table. */
    private fun boxMean(source: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val stride = w + 1
        val integral = DoubleArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += source[y * w + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }

        val result = FloatArray(w * h)
        for (y in 0 until h) {
            val top = max(0, y - radius)
            val bottom = min(h, y + radius + 1)
            for (x in 0 until w) {
                val left = max(0, x - radius)
                val right = min(w, x + radius + 1)
                val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                    integral[bottom * stride + left] + integral[top * stride + left]
                result[y * w + x] = (sum / ((bottom - top) * (right - left))).toFloat()
            }
        }
        return result
    }
}
