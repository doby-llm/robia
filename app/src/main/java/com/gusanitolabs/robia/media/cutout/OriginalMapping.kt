package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CanvasOffset
import com.gusanitolabs.robia.core.model.CutoutRecipe

/**
 * Maps a stored cutout back to its original (REQ-OR-05). Canvas pixel (x, y) is work pixel (x + offset.left, y + offset.top),
 * and the work frame is the original frame, scaled to the original's size when the two differ. The recipe's rotation does not
 * enter the mapping: the saved original is already oriented. Pure, so the mapping is tested on the JVM.
 */
internal class OriginalMapping(
    private val recipe: CutoutRecipe,
    private val originalW: Int,
    private val originalH: Int,
) {
    private val offset: CanvasOffset = checkNotNull(recipe.storedCanvasOffset) { "The recipe has no stored-canvas offset" }

    init {
        require(recipe.workW > 0 && recipe.workH > 0 && originalW > 0 && originalH > 0) { "Frame sizes must be positive" }
    }

    /** The index in the original of the pixel under canvas pixel ([x], [y]), or -1 when it lies outside the original frame. */
    fun originalIndex(x: Int, y: Int): Int {
        val workX = x + offset.left
        val workY = y + offset.top
        if (workX < 0 || workY < 0 || workX >= recipe.workW || workY >= recipe.workH) return -1
        // The centre of the work pixel, scaled and floored: the identity when the sizes match.
        val originalX = (2 * workX + 1) * originalW / (2 * recipe.workW)
        val originalY = (2 * workY + 1) * originalH / (2 * recipe.workH)
        return originalY * originalW + originalX
    }
}

/**
 * The original in the cutout's canvas: ARGB with the original's colour where the canvas pixel maps into the original, and alpha
 * 0 outside it. [original] is the ARGB of the original frame, [originalW] × [originalH].
 */
internal fun originalLayer(
    original: IntArray,
    originalW: Int,
    originalH: Int,
    canvasW: Int,
    canvasH: Int,
    recipe: CutoutRecipe,
): IntArray {
    val mapping = OriginalMapping(recipe, originalW, originalH)
    return IntArray(canvasW * canvasH) { i ->
        val index = mapping.originalIndex(i % canvasW, i / canvasW)
        if (index < 0) 0 else (ALPHA_MASK or (original[index] and RGB_MASK))
    }
}

/** The recipe of a cutout made by an engine run, whose canvas was cropped by [trim] after the engine's own crop. */
internal fun CutoutRecipe.atEngineCanvas(trim: CanvasOffset): CutoutRecipe =
    copy(storedCanvasOffset = CanvasOffset(cropLeft + trim.left, cropTop + trim.top))

/** The recipe after the stored canvas was cropped again by [trim]. A recipe without an offset stays without one. */
internal fun CutoutRecipe.trimmedBy(trim: CanvasOffset): CutoutRecipe {
    val offset = storedCanvasOffset ?: return this
    return copy(storedCanvasOffset = CanvasOffset(offset.left + trim.left, offset.top + trim.top))
}

internal const val ALPHA_MASK = 0xFF shl 24
internal const val RGB_MASK = 0xFFFFFF
