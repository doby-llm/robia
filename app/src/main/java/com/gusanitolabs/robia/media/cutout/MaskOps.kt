package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.media.AlphaRefiner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/** A pixel position in a w×h row-major mask; x grows right, y grows down. */
internal data class MaskPoint(val x: Int, val y: Int)

/** Inclusive pixel bounds, as `np.where(...).min()/max()` gives them in the Python reference. */
internal data class MaskBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val width: Int get() = x1 - x0
    val height: Int get() = y1 - y0
}

/** Connected components: [labels] is 0 for background, 1.. per component; [areas] is indexed by label (0 unused). */
internal class Labeling(val labels: IntArray, val areas: IntArray)

/**
 * Pure mask operations of the precise cutout pipeline. Ported from `tools/segmentation-bench` (`pipeline_v10.py`,
 * `pipeline_v11.py`, `bench_v2.py`, `bench_v3.py`), with the same thresholds and neighbourhoods.
 * Masks are row-major w×h arrays: BooleanArray when hard, FloatArray in 0..1 when soft.
 */
internal object MaskOps {
    private const val THIRD_MIN_DISTANCE_PX = 3f
    private const val EDGE_GUIDE_RADIUS = 3
    private const val EDGE_GUIDE_EPS = 1e-3f
    private const val INTERIOR = 0.97f
    private const val SUPPORT = 0.05f
    private const val SHARP_LOW = 0.47f
    private const val SHARP_HIGH = 0.53f
    private const val SAM_LONG_EDGE_PX = 1024

    fun thresholded(soft: FloatArray, threshold: Float = 0.5f): BooleanArray = BooleanArray(soft.size) { soft[it] > threshold }

    fun count(mask: BooleanArray): Int = mask.count { it }

    /** Number of soft values above [threshold], as [thresholded] counts them, without allocating a hard mask. */
    fun countAbove(soft: FloatArray, threshold: Float = 0.5f): Int = soft.count { it > threshold }

    /** Share of [selection] pixels that also lie in [reference]; 0 for an empty selection. */
    fun overlap(selection: BooleanArray, reference: BooleanArray): Float {
        var selected = 0
        var shared = 0
        for (i in selection.indices) {
            if (selection[i]) {
                selected++
                if (reference[i]) shared++
            }
        }
        return if (selected == 0) 0f else shared.toFloat() / selected
    }

    /** Euclidean-like distance from each foreground pixel to the nearest background pixel (two-pass chamfer 1/√2). */
    fun distanceTransform(hard: BooleanArray, w: Int, h: Int): FloatArray {
        val d = FloatArray(w * h) { if (hard[it]) INFINITE_DISTANCE else 0f }
        val diagonal = sqrt(2f)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (d[i] == 0f) continue
                var best = d[i]
                if (x > 0) best = min(best, d[i - 1] + 1f)
                if (y > 0) {
                    best = min(best, d[i - w] + 1f)
                    if (x > 0) best = min(best, d[i - w - 1] + diagonal)
                    if (x < w - 1) best = min(best, d[i - w + 1] + diagonal)
                }
                d[i] = best
            }
        }
        for (y in h - 1 downTo 0) {
            for (x in w - 1 downTo 0) {
                val i = y * w + x
                if (d[i] == 0f) continue
                var best = d[i]
                if (x < w - 1) best = min(best, d[i + 1] + 1f)
                if (y < h - 1) {
                    best = min(best, d[i + w] + 1f)
                    if (x < w - 1) best = min(best, d[i + w + 1] + diagonal)
                    if (x > 0) best = min(best, d[i + w - 1] + diagonal)
                }
                d[i] = best
            }
        }
        return d
    }

    /**
     * S2 prompts: the distance-transform maximum of [hard] inside each vertical third of its extent (`pipeline_v10`).
     * A third whose best distance is not above 3 px yields no point.
     */
    fun thirdPoints(hard: BooleanArray, w: Int, h: Int): List<MaskPoint> {
        var top = -1
        var bottom = -1
        for (y in 0 until h) {
            if ((0 until w).any { hard[y * w + it] }) {
                if (top < 0) top = y
                bottom = y
            }
        }
        if (top < 0) return emptyList()
        val distance = distanceTransform(hard, w, h)
        val points = ArrayList<MaskPoint>(3)
        for (k in 0 until 3) {
            val a = top + (bottom - top) * k / 3
            val b = top + (bottom - top) * (k + 1) / 3
            var best = 0f
            var bestIndex = -1
            for (y in a until b) {
                for (x in 0 until w) {
                    val value = distance[y * w + x]
                    if (value > best) {
                        best = value
                        bestIndex = y * w + x
                    }
                }
            }
            if (bestIndex >= 0 && best > THIRD_MIN_DISTANCE_PX) points += MaskPoint(bestIndex % w, bestIndex / w)
        }
        return points
    }

    /** Top-n distance maxima of a soft mask, each one suppressing a disc of max(1.5·dt, 10) px (`bench_v2.core_points`). */
    fun corePoints(soft: FloatArray, w: Int, h: Int, n: Int = 3): List<MaskPoint> {
        val hard = thresholded(soft)
        val distance = distanceTransform(hard, w, h)
        val work = distance.copyOf()
        val points = ArrayList<MaskPoint>(n)
        repeat(n) {
            var best = 0f
            var bestIndex = -1
            for (i in work.indices) {
                if (work[i] > best) {
                    best = work[i]
                    bestIndex = i
                }
            }
            if (bestIndex < 0) return points
            val cx = bestIndex % w
            val cy = bestIndex / w
            points += MaskPoint(cx, cy)
            val radius = max(distance[bestIndex] * 1.5f, 10f)
            clearDisc(work, w, h, cx, cy, radius)
        }
        return points
    }

    /** Labels 8-connected foreground components with an iterative flood fill. */
    fun labelComponents(mask: BooleanArray, w: Int, h: Int): Labeling {
        val labels = IntArray(w * h)
        val areas = ArrayList<Int>()
        areas += 0
        val stack = IntArray(w * h)
        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            val label = areas.size
            areas += 0
            var top = 0
            stack[top++] = start
            labels[start] = label
            var area = 0
            while (top > 0) {
                val index = stack[--top]
                area++
                val x = index % w
                val y = index / w
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val neighbour = ny * w + nx
                        if (mask[neighbour] && labels[neighbour] == 0) {
                            labels[neighbour] = label
                            stack[top++] = neighbour
                        }
                    }
                }
            }
            areas[label] = area
        }
        return Labeling(labels, areas.toIntArray())
    }

    /**
     * S5 island filter (`drop_islands`): when the selection has at least two components, keep those with at least
     * [minFraction] of the largest area, dilated 5×5, and multiply the selection by that support.
     */
    fun dropIslands(selection: FloatArray, w: Int, h: Int, minFraction: Float = 0.10f): FloatArray {
        val labeling = labelComponents(thresholded(selection), w, h)
        if (labeling.areas.size - 1 < 2) return selection.copyOf()
        val largest = labeling.areas.drop(1).max()
        val keep = BooleanArray(w * h) { i ->
            val label = labeling.labels[i]
            label > 0 && labeling.areas[label] >= minFraction * largest
        }
        val support = dilate(keep, w, h, 5)
        return FloatArray(w * h) { i -> if (support[i]) selection[i] else 0f }
    }

    /** Square dilation with an odd [size] kernel; pixels outside the image are never foreground. */
    fun dilate(mask: BooleanArray, w: Int, h: Int, size: Int): BooleanArray {
        val radius = size / 2
        val horizontal = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (xx in max(0, x - radius)..min(w - 1, x + radius)) {
                    if (mask[y * w + xx]) {
                        horizontal[y * w + x] = true
                        break
                    }
                }
            }
        }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (yy in max(0, y - radius)..min(h - 1, y + radius)) {
                    if (horizontal[yy * w + x]) {
                        out[y * w + x] = true
                        break
                    }
                }
            }
        }
        return out
    }

    /** Inclusive bounds of the foreground, or null when it is empty. */
    fun bbox(mask: BooleanArray, w: Int, h: Int): MaskBox? {
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = -1
        var y1 = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y * w + x]) {
                    x0 = min(x0, x)
                    y0 = min(y0, y)
                    x1 = max(x1, x)
                    y1 = max(y1, y)
                }
            }
        }
        return if (x1 < 0) null else MaskBox(x0, y0, x1, y1)
    }

    /** Bilinear resample of a single-channel plane (OpenCV INTER_LINEAR sampling convention). */
    fun resizeBilinear(source: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val scaleX = sw.toFloat() / dw
        val scaleY = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * scaleY - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, sh - 1)
            val wy = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * scaleX - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val wx = fx - x0
                val top = source[y0 * sw + x0] * (1 - wx) + source[y0 * sw + x1] * wx
                val bottom = source[y1 * sw + x0] * (1 - wx) + source[y1 * sw + x1] * wx
                out[y * dw + x] = top * (1 - wy) + bottom * wy
            }
        }
        return out
    }

    /**
     * Bilinear resample as PIL's `BILINEAR` (the reference's u2netp input, D4): a triangle filter whose support grows with the
     * downscale factor, so fine detail is averaged rather than sampled. Upscaling gives the same result as [resizeBilinear].
     */
    fun resizeBilinearAntialias(source: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val horizontal = axisWeights(sw, dw)
        val vertical = axisWeights(sh, dh)
        val rows = FloatArray(sh * dw)
        for (y in 0 until sh) {
            for (x in 0 until dw) {
                var sum = 0f
                for (k in horizontal.weights[x].indices) sum += source[y * sw + horizontal.first[x] + k] * horizontal.weights[x][k]
                rows[y * dw + x] = sum
            }
        }
        val out = FloatArray(dh * dw)
        for (y in 0 until dh) {
            for (x in 0 until dw) {
                var sum = 0f
                for (k in vertical.weights[y].indices) sum += rows[(vertical.first[y] + k) * dw + x] * vertical.weights[y][k]
                out[y * dw + x] = sum
            }
        }
        return out
    }

    /** Scale from a work photo to the MobileSAM input (`bench_v6.MobileSam.embed`): the long edge becomes exactly 1024. */
    fun samScale(width: Int, height: Int): Double = SAM_LONG_EDGE_PX.toDouble() / max(width, height)

    /** MobileSAM input size: both sides times [samScale], rounded half to even as Python's `round()`. */
    fun samInputSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = samScale(width, height)
        return round(width * scale).toInt() to round(height * scale).toInt()
    }

    /**
     * One MobileSAM prompt of a work photo of [width]×[height]: [coords] holds x, y pairs (positive points, negative points,
     * then the two box corners) and [labels] one value per pair (1 positive, 0 negative, 2 and 3 for the box corners).
     */
    internal class SamPrompt(val coords: FloatArray, val labels: FloatArray)

    /** The points and box corners scaled by [samScale] (`bench_v6.MobileSam.predict`); pure, so the scaling is tested per size. */
    fun samPrompt(width: Int, height: Int, positive: List<MaskPoint>, negative: List<MaskPoint>, box: MaskBox): SamPrompt {
        val scale = samScale(width, height).toFloat()
        val coords = ArrayList<Float>()
        val labels = ArrayList<Float>()
        for (p in positive) {
            coords += p.x * scale
            coords += p.y * scale
            labels += 1f
        }
        for (p in negative) {
            coords += p.x * scale
            coords += p.y * scale
            labels += 0f
        }
        coords += listOf(box.x0 * scale, box.y0 * scale, box.x1 * scale, box.y1 * scale)
        labels += listOf(2f, 3f)
        return SamPrompt(coords.toFloatArray(), labels.toFloatArray())
    }

    /** Per output index of one axis: the first source index and the normalised weights (PIL's `BILINEAR` table). */
    private class AxisWeights(val first: IntArray, val weights: Array<FloatArray>)

    private fun axisWeights(inSize: Int, outSize: Int): AxisWeights {
        val scale = inSize.toDouble() / outSize
        val support = max(scale, 1.0)
        val first = IntArray(outSize)
        val weights = Array(outSize) { index ->
            val center = (index + 0.5) * scale
            val start = max((center - support + 0.5).toInt(), 0)
            val end = min((center + support + 0.5).toInt(), inSize)
            val raw = DoubleArray(end - start) { k -> triangle((start + k - center + 0.5) / support) }
            val total = raw.sum()
            first[index] = start
            FloatArray(raw.size) { k -> (raw[k] / total).toFloat() }
        }
        return AxisWeights(first, weights)
    }

    private fun triangle(distance: Double): Double = max(0.0, 1.0 - abs(distance))

    /** Mean over a (2r+1)² window, OpenCV's default reflect-101 border, computed as two separable box sums. */
    fun boxMean(source: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val size = 2 * radius + 1
        val horizontal = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -radius..radius) sum += source[y * w + reflect101(x + k, w)]
                horizontal[y * w + x] = sum
            }
        }
        val out = FloatArray(w * h)
        val norm = (size * size).toFloat()
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -radius..radius) sum += horizontal[reflect101(y + k, h) * w + x]
                out[y * w + x] = sum / norm
            }
        }
        return out
    }

    /** He et al. guided filter with a grey guide (`bench_v2.guided`). */
    fun guidedFilter(guide: FloatArray, input: FloatArray, w: Int, h: Int, radius: Int, eps: Float): FloatArray {
        val n = w * h
        val meanGuide = boxMean(guide, w, h, radius)
        val meanInput = boxMean(input, w, h, radius)
        val meanGuideInput = boxMean(FloatArray(n) { guide[it] * input[it] }, w, h, radius)
        val meanGuideSquared = boxMean(FloatArray(n) { guide[it] * guide[it] }, w, h, radius)
        val slope = FloatArray(n) { i ->
            val covariance = meanGuideInput[i] - meanGuide[i] * meanInput[i]
            val variance = meanGuideSquared[i] - meanGuide[i] * meanGuide[i]
            covariance / (variance + eps)
        }
        val offset = FloatArray(n) { i -> meanInput[i] - slope[i] * meanGuide[i] }
        val meanSlope = boxMean(slope, w, h, radius)
        val meanOffset = boxMean(offset, w, h, radius)
        return FloatArray(n) { i -> meanSlope[i] * guide[i] + meanOffset[i] }
    }

    /**
     * S5 edges (`finish` in `pipeline_v10.py`): guided filter of the selection on luminance (r=3, eps=1e-3), interior
     * above 0.97 forced to 1, support = selection > 0.05 dilated 5×5, then 8-bit alpha (truncated, as the reference does).
     * [CutoutEdges.Sharp] (SG4) replaces the guided filter with smoothstep(0.47, 0.53); the interior and support rules stay.
     */
    fun refineAlpha(luminance: FloatArray, selection: FloatArray, w: Int, h: Int, profile: CutoutEdges = CutoutEdges.Soft): ByteArray {
        val refined = when (profile) {
            CutoutEdges.Soft -> guidedFilter(luminance, selection, w, h, EDGE_GUIDE_RADIUS, EDGE_GUIDE_EPS)
            CutoutEdges.Sharp -> FloatArray(w * h) { i -> AlphaRefiner.smoothstep(SHARP_LOW, SHARP_HIGH, selection[i]) }
        }
        val support = dilate(BooleanArray(w * h) { selection[it] > SUPPORT }, w, h, 5)
        return ByteArray(w * h) { i ->
            val value = if (selection[i] > INTERIOR) 1f else refined[i].coerceIn(0f, 1f)
            val alpha = if (support[i]) value else 0f
            (alpha * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    /** Luminance in 0..1 from ARGB, using the same 0.299/0.587/0.114 weights as the guide in [AlphaRefiner]. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val color = argb[i]
        (0.299f * ((color shr 16) and 0xFF) + 0.587f * ((color shr 8) and 0xFF) + 0.114f * (color and 0xFF)) / 255f
    }

    /** Channel plane of ARGB pixels in 0..255 (shift 16 red, 8 green, 0 blue). */
    fun channel(argb: IntArray, shift: Int): FloatArray = FloatArray(argb.size) { i -> ((argb[i] shr shift) and 0xFF).toFloat() }

    private fun reflect101(index: Int, size: Int): Int {
        if (size == 1) return 0
        var i = index
        while (i < 0 || i >= size) {
            i = if (i < 0) -i else 2 * (size - 1) - i
        }
        return i
    }

    private fun clearDisc(values: FloatArray, w: Int, h: Int, cx: Int, cy: Int, radius: Float) {
        val r = radius.toInt()
        for (y in max(0, cy - r)..min(h - 1, cy + r)) {
            for (x in max(0, cx - r)..min(w - 1, cx + r)) {
                val dx = (x - cx).toFloat()
                val dy = (y - cy).toFloat()
                if (dx * dx + dy * dy <= radius * radius) values[y * w + x] = 0f
            }
        }
    }

    private const val INFINITE_DISTANCE = 1e9f
}
