package com.gusanitolabs.robia.media.cutout

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A selection value at or above this counts as selected: the same 0.5 that [RefineSession] thresholds a hard mask at. */
internal const val SELECTION_THRESHOLD = 0.5f

/**
 * An alpha above this is opaque for [removesMostOfGarment] and the pieces of [componentsToRemove]; a half-transparent pixel
 * is not garment to protect.
 */
private const val OPAQUE_ALPHA = 0.5f

/** The snap radius of a smart tap, in photo pixels: never below 2 (a thin piece can be hit) and never above 48 (a tap is not a region). */
private const val SNAP_RADIUS_MIN_PX = 2
private const val SNAP_RADIUS_MAX_PX = 48

/**
 * What one smart tap picked: [mask] is 1 on every pixel of the non-primary components the touches reached and 0 elsewhere;
 * [touchedPrimary] is true when a touch reached the primary (largest) component; [removedComponents] counts the pieces in [mask].
 */
internal class ComponentPick(val mask: FloatArray, val touchedPrimary: Boolean, val removedComponents: Int)

/**
 * The loose pieces that the [touched] (x, y) pixels pick. The components are the 8-connected parts of `alpha > 0.5`; the primary one
 * (largest area, the first in raster order on a tie) is never removed. A touch resolves to the component under its pixel; a touch on
 * a transparent pixel resolves to the nearest component pixel within [snapRadiusPx] (Euclidean, ties by smaller y then smaller x), and
 * a touch further away resolves to nothing. Pure Kotlin on primitive arrays so it runs on the JVM test classpath.
 */
internal fun componentsToRemove(
    alpha: FloatArray,
    w: Int,
    h: Int,
    touched: List<Pair<Int, Int>>,
    snapRadiusPx: Int,
): ComponentPick {
    require(alpha.size == w * h) { "Alpha must be ${w}x$h" }
    require(snapRadiusPx >= 0) { "The snap radius cannot be negative" }
    val labeling = MaskOps.labelComponents(BooleanArray(alpha.size) { i -> alpha[i] > OPAQUE_ALPHA }, w, h)
    val labels = labeling.labels
    // Label 0 is the background. The strict comparison keeps the lowest label when two areas are equal.
    var primary = 0
    for (label in 1 until labeling.areas.size) {
        if (primary == 0 || labeling.areas[label] > labeling.areas[primary]) primary = label
    }
    val chosen = BooleanArray(labeling.areas.size)
    var touchedPrimary = false
    for ((x, y) in touched) {
        if (x !in 0 until w || y !in 0 until h) continue
        val label = componentAt(labels, w, h, x, y, snapRadiusPx)
        if (label == 0) continue
        if (label == primary) touchedPrimary = true else chosen[label] = true
    }
    return ComponentPick(
        mask = FloatArray(labels.size) { i -> if (chosen[labels[i]]) 1f else 0f },
        touchedPrimary = touchedPrimary,
        removedComponents = chosen.count { it },
    )
}

/**
 * The label that a touch at (x, y) resolves to: the pixel's own component, else the nearest labelled pixel within [radius]
 * (ties by smaller y, then smaller x: the scan order keeps the first of equal distances). 0 when there is none.
 */
private fun componentAt(labels: IntArray, w: Int, h: Int, x: Int, y: Int, radius: Int): Int {
    if (labels[y * w + x] > 0) return labels[y * w + x]
    var best = 0
    var bestDistance = Int.MAX_VALUE
    val reach = radius * radius
    for (yy in max(0, y - radius)..min(h - 1, y + radius)) {
        for (xx in max(0, x - radius)..min(w - 1, x + radius)) {
            val label = labels[yy * w + xx]
            if (label == 0) continue
            val distance = (xx - x) * (xx - x) + (yy - y) * (yy - y)
            if (distance <= reach && distance < bestDistance) {
                best = label
                bestDistance = distance
            }
        }
    }
    return best
}

/**
 * The snap radius of a smart tap in photo pixels: [dp] on screen, where [fitScale] is the screen pixels per photo pixel at zoom 1
 * and [zoom] the pinch zoom. Clamped to 2..48.
 */
internal fun snapRadiusPhotoPx(dp: Float, density: Float, fitScale: Float, zoom: Float): Int =
    ceil(dp * density / (fitScale * zoom)).toInt().coerceIn(SNAP_RADIUS_MIN_PX, SNAP_RADIUS_MAX_PX)

/** What a smart tap does with its pick: remove it, show a hint, or change nothing. */
internal enum class SmartTapOutcome { Remove, WholeGarmentHint, KeepHint, NothingToRemove }

/**
 * The outcome of a smart tap. Keep never removes: it shows the hint, and the brush is the Keep tool. In Remove, a pick that removes
 * nothing shows the whole-garment hint when it reached the primary, and nothing otherwise; so does a pick the 60 % guard refuses.
 */
internal fun smartTapOutcome(mode: RefineSession.Mode, pick: ComponentPick, alpha: FloatArray): SmartTapOutcome = when {
    mode == RefineSession.Mode.Keep -> SmartTapOutcome.KeepHint
    pick.removedComponents == 0 -> if (pick.touchedPrimary) SmartTapOutcome.WholeGarmentHint else SmartTapOutcome.NothingToRemove
    removesMostOfGarment(pick.mask, alpha) -> SmartTapOutcome.WholeGarmentHint
    else -> SmartTapOutcome.Remove
}

/**
 * True when [selection] covers at least [fraction] of the opaque garment ([alpha] above 0.5), counting a pixel as selected at
 * [SELECTION_THRESHOLD]. Removing that would take most of the garment, not a leftover piece. False when there is no opaque pixel.
 */
internal fun removesMostOfGarment(selection: FloatArray, alpha: FloatArray, fraction: Float = 0.60f): Boolean {
    require(selection.size == alpha.size) { "Selection and alpha must be the same size" }
    var opaque = 0
    var selectedOpaque = 0
    for (i in alpha.indices) {
        if (alpha[i] <= OPAQUE_ALPHA) continue
        opaque++
        if (selection[i] >= SELECTION_THRESHOLD) selectedOpaque++
    }
    return opaque > 0 && selectedOpaque.toFloat() / opaque >= fraction
}
