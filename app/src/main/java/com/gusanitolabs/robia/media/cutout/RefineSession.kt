package com.gusanitolabs.robia.media.cutout

import kotlin.math.max

private const val HARD_THRESHOLD = 0.5f
private const val HARD_DILATION_PX = 2
private const val UNDO_DEPTH = 10

/**
 * Pixels whose stored alpha is below this have no colour to restore: the stored PNG has RGB 0 under alpha 0, and the
 * original photo is not kept with the garment. Keep never raises them, so they cannot turn into black.
 */
internal const val UNRESTORABLE_ALPHA = 0.05f

/** The alpha after a Keep mask [mask] (0..1) on a pixel with [current] alpha and [base] stored alpha. */
internal fun keepAlpha(current: Float, base: Float, mask: Float): Float =
    if (base < UNRESTORABLE_ALPHA) current else max(current, mask)

/**
 * Cutout state of the photo editor (SG3): the photo's alpha, changed by Keep and Remove masks, with a bounded undo stack.
 * Each mask covers the whole photo (w×h, row-major). Pure Kotlin on primitive arrays so it runs on the JVM test classpath.
 */
internal class RefineSession(
    private val w: Int,
    private val h: Int,
    baseAlpha: FloatArray,
) {
    enum class Mode { Keep, Remove }

    /** Automatic result; replaced whole by [replaceBase], never changed in place, so undo entries can keep a reference. */
    private var base: FloatArray
    private var current: FloatArray
    private val undoStack = ArrayDeque<Snapshot>()

    init {
        require(w > 0 && h > 0) { "Photo size must be positive" }
        require(baseAlpha.size == w * h) { "Alpha must be ${w}x$h" }
        base = baseAlpha.copyOf()
        current = baseAlpha.copyOf()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** True when the alpha differs from the automatic result. */
    val isChanged: Boolean get() = !current.contentEquals(base)

    fun currentAlpha(): FloatArray = current.copyOf()

    /**
     * Keep raises the alpha to the mask (`max(alpha, m)`, see [keepAlpha]); Remove scales it by `1 - m`. A [hard] mask
     * (smart selection) is thresholded at 0.5 and dilated by a disc of [HARD_DILATION_PX]; a soft mask (brush) is used as it is.
     */
    fun apply(mode: Mode, mask: FloatArray, hard: Boolean) {
        require(mask.size == w * h) { "Mask must be ${w}x$h" }
        val effective = if (hard) dilate(threshold(mask), HARD_DILATION_PX) else mask
        pushUndo()
        current = FloatArray(current.size) { i ->
            val m = effective[i].coerceIn(0f, 1f)
            when (mode) {
                Mode.Keep -> keepAlpha(current[i], base[i], m)
                Mode.Remove -> current[i] * (1f - m)
            }
        }
    }

    /** Returns the most recent change, including a [replaceBase]; a no-op when nothing is left to undo. */
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        base = previous.base
        current = previous.current.decode(w * h)
    }

    /** Back to the automatic result. The reset is itself undoable. */
    fun reset() {
        if (!isChanged) return
        pushUndo()
        current = base.copyOf()
    }

    /**
     * Makes [alpha] (w×h) the automatic result, as a new cutout of the same photo; the edits are cleared. Undo restores the
     * previous base together with the edits that were on it.
     */
    fun replaceBase(alpha: FloatArray) {
        require(alpha.size == w * h) { "Alpha must be ${w}x$h" }
        pushUndo()
        base = alpha.copyOf()
        current = alpha.copyOf()
    }

    private fun pushUndo() {
        if (undoStack.size == UNDO_DEPTH) undoStack.removeFirst()
        undoStack.addLast(Snapshot(base, RunLengthAlpha(current)))
    }

    private fun threshold(mask: FloatArray): FloatArray =
        FloatArray(mask.size) { i -> if (mask[i] >= HARD_THRESHOLD) 1f else 0f }

    /** Disc max filter of [radius] (dx² + dy² ≤ radius²), so the grown edge is round rather than square. */
    private fun dilate(mask: FloatArray, radius: Int): FloatArray {
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
}

/** One undo entry: the automatic result and the edited alpha at that time. */
private class Snapshot(val base: FloatArray, val current: RunLengthAlpha)

/** An alpha snapshot as runs of equal values, so an undo entry stays small for flat masks. Equality is by bit pattern. */
private class RunLengthAlpha(source: FloatArray) {
    private val values: FloatArray
    private val lengths: IntArray

    init {
        var runs = 0
        for (i in source.indices) if (i == 0 || source[i].toRawBits() != source[i - 1].toRawBits()) runs++
        values = FloatArray(runs)
        lengths = IntArray(runs)
        var run = -1
        for (i in source.indices) {
            if (i == 0 || source[i].toRawBits() != source[i - 1].toRawBits()) {
                run++
                values[run] = source[i]
            }
            lengths[run]++
        }
    }

    fun decode(size: Int): FloatArray {
        val out = FloatArray(size)
        var position = 0
        for (run in values.indices) {
            out.fill(values[run], position, position + lengths[run])
            position += lengths[run]
        }
        return out
    }
}
