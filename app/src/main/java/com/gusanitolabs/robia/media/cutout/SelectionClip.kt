package com.gusanitolabs.robia.media.cutout

/** A selection value at or above this counts as selected: the same 0.5 that [RefineSession] thresholds a hard mask at. */
internal const val SELECTION_THRESHOLD = 0.5f

/** An alpha above this is opaque for [removesMostOfGarment]; a half-transparent pixel is not garment to protect. */
private const val OPAQUE_ALPHA = 0.5f

/**
 * Keeps only the parts of [selection] that the touch reached: the 8-connected components of `selection >= threshold` that hold
 * at least one of the [touched] (x, y) pixels. Inside a kept component the soft values stay as they are; everything else is 0.
 * Pure Kotlin on primitive arrays so it runs on the JVM test classpath.
 */
internal fun clipToTouched(
    selection: FloatArray,
    w: Int,
    h: Int,
    touched: List<Pair<Int, Int>>,
    threshold: Float = SELECTION_THRESHOLD,
): FloatArray {
    require(selection.size == w * h) { "Selection must be ${w}x$h" }
    val labeling = MaskOps.labelComponents(BooleanArray(selection.size) { i -> selection[i] >= threshold }, w, h)
    // Label 0 is the background: a touch there keeps nothing.
    val kept = BooleanArray(labeling.areas.size)
    for ((x, y) in touched) {
        if (x !in 0 until w || y !in 0 until h) continue
        val label = labeling.labels[y * w + x]
        if (label > 0) kept[label] = true
    }
    return FloatArray(selection.size) { i -> if (kept[labeling.labels[i]]) selection[i] else 0f }
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
