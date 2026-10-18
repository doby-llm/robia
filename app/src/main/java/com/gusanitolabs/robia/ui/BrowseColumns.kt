package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.BrowseLayout

/** Column counts a pinch can reach: photos-only tiles go to 4 columns, tiles with details stop at 3. */
internal fun browseColumnRange(layout: BrowseLayout): IntRange = when (layout) {
    BrowseLayout.PhotosOnly -> 2..4
    BrowseLayout.PhotosWithDetails -> 2..3
}

/** Pinch out past ×1.25 means one column fewer, pinch in below ×0.8 one more; anything in between keeps the count. */
internal fun nextColumnCount(current: Int, cumulativeZoom: Float, range: IntRange): Int {
    val stepped = when {
        cumulativeZoom >= 1.25f -> current - 1
        cumulativeZoom <= 0.8f -> current + 1
        else -> current
    }
    return stepped.coerceIn(range)
}
