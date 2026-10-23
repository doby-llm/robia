package com.gusanitolabs.robia.media.cutout

import kotlin.math.max

private const val HARD_THRESHOLD = 0.5f
private const val HARD_DILATION_PX = 2

/**
 * Pixels whose stored alpha is below this have no colour to restore: the stored PNG has RGB 0 under alpha 0, and the
 * original photo is not kept with the garment. Keep never raises them, so they cannot turn into black.
 */
internal const val UNRESTORABLE_ALPHA = 0.05f

/** The alpha after a Keep mask [mask] (0..1) on a pixel with [current] alpha and [base] stored alpha. */
internal fun keepAlpha(current: Float, base: Float, mask: Float): Float =
    if (base < UNRESTORABLE_ALPHA) current else max(current, mask)

/**
 * The smart selection as it is applied: thresholded at 0.5 and dilated by a disc of [HARD_DILATION_PX], so the grown edge is
 * round rather than square. Every value is 0 or 1.
 */
internal fun hardSelection(mask: FloatArray, w: Int, h: Int): FloatArray =
    dilate(threshold(mask), w, h, HARD_DILATION_PX)

/**
 * The alpha after a Keep or Remove of the 0..1 [mask] on [current], with [base] the automatic alpha. Keep raises the alpha
 * to the mask (see [keepAlpha]); Remove scales it by `1 - mask`. Shared by [RefineSession] and the replay of [EditHistory].
 */
internal fun refineAlpha(mode: RefineSession.Mode, mask: FloatArray, current: FloatArray, base: FloatArray): FloatArray =
    FloatArray(current.size) { i ->
        val m = mask[i].coerceIn(0f, 1f)
        when (mode) {
            RefineSession.Mode.Keep -> keepAlpha(current[i], base[i], m)
            RefineSession.Mode.Remove -> current[i] * (1f - m)
        }
    }

/**
 * Cutout state of the photo editor (SG3): the photo's alpha, changed by Keep and Remove masks. Every change is an op of the
 * photo's [EditHistory], so undo and redo have no depth limit. Each mask covers the whole photo (w×h, row-major). Pure Kotlin
 * on primitive arrays so it runs on the JVM test classpath.
 */
internal class RefineSession(
    private val w: Int,
    private val h: Int,
    baseAlpha: FloatArray,
    budget: EditBudget = EditBudget(),
) {
    enum class Mode { Keep, Remove }

    private val history = EditHistory(baseAlpha, w, h, budget)

    val canUndo: Boolean get() = history.canUndo

    val canRedo: Boolean get() = history.canRedo

    /** True when the alpha differs from the automatic result. */
    val isChanged: Boolean get() = history.isEdited

    fun currentAlpha(): FloatArray = history.currentAlpha()

    /**
     * Keep raises the alpha to the mask; Remove scales it by `1 - mask`. A [hard] mask (smart selection) is thresholded at 0.5
     * and dilated by a disc of [HARD_DILATION_PX]; a soft mask is used as it is.
     */
    fun apply(mode: Mode, mask: FloatArray, hard: Boolean) {
        require(mask.size == w * h) { "Mask must be ${w}x$h" }
        history.push(
            if (hard) EditOp.Hard(mode, RunLengthAlpha(hardSelection(mask, w, h))) else EditOp.Soft(mode, RunLengthAlpha(mask)),
        )
    }

    /** A brush stroke ([points] as x, y pairs in photo pixels), stored as the points and not as a mask. */
    fun applyBrush(mode: Mode, points: FloatArray, radiusPx: Float, featherPx: Float) {
        require(points.size % 2 == 0) { "Points must be x, y pairs" }
        history.push(EditOp.Brush(mode, points.copyOf(), radiusPx, featherPx))
    }

    /** Returns the most recent change; a no-op when nothing is left to undo. */
    fun undo() {
        history.undo()
    }

    /** Returns the most recent undone change; a no-op when there is none. */
    fun redo() {
        history.redo()
    }

    /** Back to the automatic result. The reset is itself undoable. */
    fun reset() {
        if (!isChanged) return
        history.push(EditOp.Reset)
    }

    /**
     * Makes [alpha] (w×h) the automatic result, as a new cutout of the same photo; the edits are cleared. Undo restores the
     * previous base together with the edits that were on it.
     */
    fun replaceBase(alpha: FloatArray) {
        require(alpha.size == w * h) { "Alpha must be ${w}x$h" }
        history.push(EditOp.Base(RunLengthAlpha(alpha)))
    }

    /** Releases the history: its bytes leave the editor's budget. */
    fun close() {
        history.close()
    }
}

private fun threshold(mask: FloatArray): FloatArray =
    FloatArray(mask.size) { i -> if (mask[i] >= HARD_THRESHOLD) 1f else 0f }

/** Disc max filter of [radius] (dx² + dy² ≤ radius²) over a w×h row-major mask. */
private fun dilate(mask: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
    val reach = radius * radius
    val out = FloatArray(mask.size)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var best = 0f
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -radius..radius) {
                    val xx = x + dx
                    if (dx * dx + dy * dy > reach || xx < 0 || xx >= w) continue
                    best = max(best, mask[yy * w + xx])
                }
            }
            out[y * w + x] = best
        }
    }
    return out
}
