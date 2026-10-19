package com.gusanitolabs.robia.media

import com.gusanitolabs.robia.media.cutout.MaskOps
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The edge formulas as they were before SG4, copied from `origin/main` (AlphaRefiner.refine and MaskOps.refineAlpha). The
 * Soft default of the production code must match these byte for byte; the copy is frozen here so the test does not compare
 * the production code with itself. The box mean and the guided filter of MaskOps are called, not copied: SG4 did not change them.
 */
internal object PreSg4EdgeReference {
    private const val SMOOTH_LOW = 0.35f
    private const val SMOOTH_HIGH = 0.65f
    private const val ALPHA_INTERIOR = 0.97f
    private const val ALPHA_GUIDED_EPS = 1e-3f
    private const val ALPHA_REFERENCE_EDGE_PX = 1024f
    private const val MASK_GUIDE_RADIUS = 3
    private const val MASK_GUIDE_EPS = 1e-3f
    private const val MASK_INTERIOR = 0.97f
    private const val MASK_SUPPORT = 0.05f

    /** AlphaRefiner.refine before SG4 (the SG1 soft edges). */
    fun alphaRefine(argb: IntArray, conf: FloatArray, w: Int, h: Int): ByteArray {
        val count = w * h
        require(argb.size == count && conf.size == count) { "Buffers must be ${w}x$h" }

        val input = FloatArray(count) { i -> smoothstep(SMOOTH_LOW, SMOOTH_HIGH, conf[i]) }
        val guide = FloatArray(count) { i ->
            val color = argb[i]
            (0.299f * ((color shr 16) and 0xFF) + 0.587f * ((color shr 8) and 0xFF) + 0.114f * (color and 0xFF)) / 255f
        }
        val radius = (3f * max(w, h) / ALPHA_REFERENCE_EDGE_PX).roundToInt()
        val filtered = alphaGuidedFilter(guide, input, w, h, radius, ALPHA_GUIDED_EPS)

        return ByteArray(count) { i ->
            val alpha = if (filtered[i] > ALPHA_INTERIOR) 1f else filtered[i].coerceIn(0f, 1f)
            (alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    /** MaskOps.refineAlpha before SG4 (the S5 edges). */
    fun maskRefineAlpha(luminance: FloatArray, selection: FloatArray, w: Int, h: Int): ByteArray {
        val guided = MaskOps.guidedFilter(luminance, selection, w, h, MASK_GUIDE_RADIUS, MASK_GUIDE_EPS)
        val support = MaskOps.dilate(BooleanArray(w * h) { selection[it] > MASK_SUPPORT }, w, h, 5)
        return ByteArray(w * h) { i ->
            val value = if (selection[i] > MASK_INTERIOR) 1f else guided[i].coerceIn(0f, 1f)
            val alpha = if (support[i]) value else 0f
            (alpha * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun alphaGuidedFilter(guide: FloatArray, input: FloatArray, w: Int, h: Int, radius: Int, eps: Float): FloatArray {
        val count = w * h
        val meanGuide = alphaBoxMean(guide, w, h, radius)
        val meanInput = alphaBoxMean(input, w, h, radius)
        val meanGuideSquared = alphaBoxMean(FloatArray(count) { i -> guide[i] * guide[i] }, w, h, radius)
        val meanGuideInput = alphaBoxMean(FloatArray(count) { i -> guide[i] * input[i] }, w, h, radius)

        val slope = FloatArray(count)
        val offset = FloatArray(count)
        for (i in 0 until count) {
            val variance = meanGuideSquared[i] - meanGuide[i] * meanGuide[i]
            val covariance = meanGuideInput[i] - meanGuide[i] * meanInput[i]
            slope[i] = covariance / (variance + eps)
            offset[i] = meanInput[i] - slope[i] * meanGuide[i]
        }

        val meanSlope = alphaBoxMean(slope, w, h, radius)
        val meanOffset = alphaBoxMean(offset, w, h, radius)
        return FloatArray(count) { i -> meanSlope[i] * guide[i] + meanOffset[i] }
    }

    /** The AlphaRefiner box mean before SG4: a (2r+1)² window clipped at the borders, on a summed-area table. */
    private fun alphaBoxMean(source: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
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
