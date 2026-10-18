package com.gusanitolabs.robia.media.cutout

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Brush radius in photo pixels for a radius in dp: the brush keeps its size on screen at any zoom (SG3). */
internal fun brushRadiusPx(radiusDp: Float, density: Float, zoom: Float): Float = radiusDp * density / zoom

/**
 * Soft round discs along [points] (photo pixels), max-combined: 1 within [radiusPx], falling linearly to 0 across
 * [featherPx] and 0 beyond. Consecutive points are joined by discs every half radius, so a fast drag leaves no gaps.
 */
internal fun brushMask(
    points: List<Pair<Float, Float>>,
    radiusPx: Float,
    featherPx: Float,
    w: Int,
    h: Int,
): FloatArray {
    require(w > 0 && h > 0) { "Mask size must be positive" }
    require(radiusPx > 0f) { "Brush radius must be positive" }
    val mask = FloatArray(w * h)
    if (points.isEmpty()) return mask

    val (startX, startY) = points.first()
    stampDisc(mask, startX, startY, radiusPx, featherPx, w, h)
    val step = max(radiusPx * 0.5f, 1f)
    for (index in 1 until points.size) {
        val (fromX, fromY) = points[index - 1]
        val (toX, toY) = points[index]
        val segments = ceil(hypot(toX - fromX, toY - fromY) / step).toInt().coerceAtLeast(1)
        for (s in 1..segments) {
            val t = s / segments.toFloat()
            stampDisc(mask, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, radiusPx, featherPx, w, h)
        }
    }
    return mask
}

/** Max-combines one soft disc centred at ([cx], [cy]) into [mask]. Pixel centres sit at +0.5. */
private fun stampDisc(
    mask: FloatArray,
    cx: Float,
    cy: Float,
    radiusPx: Float,
    featherPx: Float,
    w: Int,
    h: Int,
) {
    val reach = radiusPx + max(featherPx, 0f)
    val x0 = max(0, floor(cx - reach).toInt())
    val x1 = min(w - 1, ceil(cx + reach).toInt())
    val y0 = max(0, floor(cy - reach).toInt())
    val y1 = min(h - 1, ceil(cy + reach).toInt())
    for (y in y0..y1) {
        for (x in x0..x1) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            val distance = sqrt(dx * dx + dy * dy)
            val value = when {
                distance <= radiusPx -> 1f
                featherPx > 0f && distance < radiusPx + featherPx -> 1f - (distance - radiusPx) / featherPx
                else -> 0f
            }
            val index = y * w + x
            if (value > mask[index]) mask[index] = value
        }
    }
}
