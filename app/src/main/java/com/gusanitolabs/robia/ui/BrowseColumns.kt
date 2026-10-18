package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.BrowseLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** Column counts a pinch can reach: photos-only tiles go to 4 columns, tiles with details stop at 3. */
internal fun browseColumnRange(layout: BrowseLayout): IntRange = when (layout) {
    BrowseLayout.PhotosOnly -> 2..4
    BrowseLayout.PhotosWithDetails -> 2..3
}

/** Each pinch step is ×1.25 of zoom: pinch out past it means one column fewer, pinch in below ×0.8 one more. */
private const val PINCH_STEP = 1.25

// 0.8f is not exactly 1 / 1.25 in binary, so a pinch that lands on a threshold must still count as crossing it.
private const val PINCH_STEP_TOLERANCE = 1e-6

// Far beyond any range (the widest is 2..4): keeps the arithmetic in columnsForPinch away from overflow.
private const val MAX_PINCH_STEPS = 8

/**
 * Signed number of steps a cumulative pinch zoom has crossed: positive for pinch out (fewer columns),
 * negative for pinch in (more columns), zero while the zoom stays within one step of the start.
 */
internal fun pinchSteps(cumulativeZoom: Float): Int {
    if (!(cumulativeZoom > 0f)) return 0
    val steps = ln(cumulativeZoom.toDouble()) / ln(PINCH_STEP)
    val whole = if (steps >= 0) floor(steps + PINCH_STEP_TOLERANCE) else ceil(steps - PINCH_STEP_TOLERANCE)
    return whole.coerceIn(-MAX_PINCH_STEPS.toDouble(), MAX_PINCH_STEPS.toDouble()).toInt()
}

/**
 * Column count for a whole pinch gesture, measured from the count at its start: every ×1.25 of pinch out
 * removes a column, every ×0.8 of pinch in adds one, and the result stays inside [range].
 */
internal fun columnsForPinch(startColumns: Int, cumulativeZoom: Float, range: IntRange): Int =
    (startColumns - pinchSteps(cumulativeZoom)).coerceIn(range)

/**
 * Zoom left over inside the current step (about 0.8 to 1.25), so the pinch feedback stays bounded
 * however far the gesture has travelled.
 */
internal fun pinchResidual(cumulativeZoom: Float): Float {
    if (!(cumulativeZoom > 0f)) return 1f
    return (cumulativeZoom / PINCH_STEP.pow(pinchSteps(cumulativeZoom))).toFloat()
}

/** Favourite heart on a photo card, in dp: fewer columns mean larger cards, so the heart grows with them. */
internal data class FavoriteBadgeMetrics(
    val outerPaddingDp: Int,
    val iconSizeDp: Int,
    val innerPaddingDp: Int,
)

internal fun favoriteBadgeMetrics(columns: Int): FavoriteBadgeMetrics = when (columns.coerceIn(2..4)) {
    2 -> FavoriteBadgeMetrics(outerPaddingDp = 8, iconSizeDp = 24, innerPaddingDp = 6)
    3 -> FavoriteBadgeMetrics(outerPaddingDp = 6, iconSizeDp = 18, innerPaddingDp = 5)
    else -> FavoriteBadgeMetrics(outerPaddingDp = 4, iconSizeDp = 14, innerPaddingDp = 4)
}
