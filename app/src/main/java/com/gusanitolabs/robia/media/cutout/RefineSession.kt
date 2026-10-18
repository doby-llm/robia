package com.gusanitolabs.robia.media.cutout

import kotlin.math.max
import kotlin.math.min

private const val HARD_THRESHOLD = 0.5f
private const val HARD_DILATION_PX = 2
private const val UNDO_DEPTH = 10

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

    private val base: FloatArray
    private var current: FloatArray
    private val undoStack = ArrayDeque<RunLengthAlpha>()

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
     * Keep raises the alpha to the mask (`max(alpha, m)`); Remove scales it by `1 - m`. A [hard] mask (smart selection) is
     * thresholded at 0.5 and dilated by [HARD_DILATION_PX]; a soft mask (brush) is used as it is.
     */
    fun apply(mode: Mode, mask: FloatArray, hard: Boolean) {
        require(mask.size == w * h) { "Mask must be ${w}x$h" }
        val effective = if (hard) dilate(threshold(mask), HARD_DILATION_PX) else mask
        pushUndo()
        current = FloatArray(current.size) { i ->
            val m = effective[i].coerceIn(0f, 1f)
            when (mode) {
                Mode.Keep -> max(current[i], m)
                Mode.Remove -> current[i] * (1f - m)
            }
        }
    }

    /** Returns the most recent change; a no-op when nothing is left to undo. */
    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        current = previous.decode(w * h)
    }

    /** Back to the automatic result. The reset is itself undoable. */
    fun reset() {
        if (!isChanged) return
        pushUndo()
        current = base.copyOf()
    }

    private fun pushUndo() {
        if (undoStack.size == UNDO_DEPTH) undoStack.removeFirst()
        undoStack.addLast(RunLengthAlpha(current))
    }

    private fun threshold(mask: FloatArray): FloatArray =
        FloatArray(mask.size) { i -> if (mask[i] >= HARD_THRESHOLD) 1f else 0f }

    /** Square max filter of [radius], separable: a horizontal pass, then a vertical one. */
    private fun dilate(mask: FloatArray, radius: Int): FloatArray {
        val horizontal = FloatArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var best = 0f
                for (xx in max(0, x - radius)..min(w - 1, x + radius)) best = max(best, mask[y * w + xx])
                horizontal[y * w + x] = best
            }
        }
        val out = FloatArray(mask.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var best = 0f
                for (yy in max(0, y - radius)..min(h - 1, y + radius)) best = max(best, horizontal[yy * w + x])
                out[y * w + x] = best
            }
        }
        return out
    }
}

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
