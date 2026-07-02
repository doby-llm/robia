package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import kotlin.math.roundToInt

/** Lightweight photo adjustments for the post-background-removal Quick Edit flow. */
object QuickEditImageProcessor {
    fun applyAdjustments(
        context: Context,
        sourceUri: Uri,
        adjustments: QuickEditAdjustments,
    ): Uri {
        if (!adjustments.hasColorAdjustment) return sourceUri
        val source = decodeBitmap(context, sourceUri) ?: return sourceUri
        return source.useForQuickEdit { bitmap ->
            writeAdjustedBitmap(context, bitmap, adjustments, prefix = "quick-edit")
        }
    }

    /**
     * Builds the dialog preview from the same draft state that Save consumes.
     *
     * The preview is intentionally persisted as a cached processed Uri so the Compose layer can keep
     * using a lightweight ImageView while preview jobs are cancelled and generation-guarded upstream.
     */
    fun renderDraftPreview(
        context: Context,
        draft: QuickEditDraftState,
    ): Uri {
        var currentUri = applyAdjustments(context, draft.sourceUri, draft.adjustments)
        draft.committedSegment?.let { segment ->
            currentUri = eraseSegmentMask(context, currentUri, segment)
        }
        return currentUri
    }

    fun eraseCircle(
        context: Context,
        sourceUri: Uri,
        point: NormalizedImagePoint,
        radiusRatio: Float = DEFAULT_ERASER_RADIUS_RATIO,
    ): Uri = eraseBrushPath(context, sourceUri, listOf(point), radiusRatio)

    fun eraseBrushPath(
        context: Context,
        sourceUri: Uri,
        points: List<NormalizedImagePoint>,
        radiusRatio: Float = DEFAULT_ERASER_RADIUS_RATIO,
    ): Uri {
        val constrainedPoints = points.ifEmpty { return sourceUri }
        val source = decodeBitmap(context, sourceUri) ?: return sourceUri
        return source.useForQuickEdit { bitmap ->
            val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            try {
                val radius = maxOf(output.width, output.height) * radiusRatio.coerceIn(0.02f, 0.2f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    strokeWidth = radius * 2f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val canvas = Canvas(output)
                constrainedPoints.forEach { point ->
                    canvas.drawCircle(
                        point.x.coerceIn(0f, 1f) * output.width,
                        point.y.coerceIn(0f, 1f) * output.height,
                        radius,
                        paint,
                    )
                }
                ClothingImageStore.writeProcessedBitmap(context, output, prefix = "quick-edit-erased")
            } finally {
                output.recycle()
            }
        }
    }

    fun eraseSegmentMask(
        context: Context,
        sourceUri: Uri,
        segment: InteractiveSegmentResult,
    ): Uri {
        val mask = segment.mask ?: return eraseBrushPath(context, sourceUri, segment.brushPoints)
        val source = decodeBitmap(context, sourceUri) ?: return sourceUri
        return source.useForQuickEdit { bitmap ->
            val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            try {
                for (y in 0 until output.height) {
                    val maskY = (y * mask.height / output.height).coerceIn(0, mask.height - 1)
                    for (x in 0 until output.width) {
                        val maskX = (x * mask.width / output.width).coerceIn(0, mask.width - 1)
                        if (mask.isSelected(maskX, maskY)) {
                            output.setPixel(x, y, output.getPixel(x, y) and 0x00FFFFFF)
                        }
                    }
                }
                ClothingImageStore.writeProcessedBitmap(context, output, prefix = "quick-edit-erased")
            } finally {
                output.recycle()
            }
        }
    }

    fun estimateCenterLuminance(context: Context, sourceUri: Uri): Float? {
        val bitmap = decodeBitmap(context, sourceUri) ?: return null
        return bitmap.useForQuickEdit { source ->
            val left = (source.width * 0.25f).roundToInt().coerceIn(0, source.width - 1)
            val right = (source.width * 0.75f).roundToInt().coerceIn(left + 1, source.width)
            val top = (source.height * 0.25f).roundToInt().coerceIn(0, source.height - 1)
            val bottom = (source.height * 0.75f).roundToInt().coerceIn(top + 1, source.height)
            val step = (maxOf(source.width, source.height) / 64).coerceAtLeast(1)
            var total = 0.0
            var count = 0
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val color = source.getPixel(x, y)
                    val alpha = (color ushr 24) and 0xFF
                    if (alpha >= OPAQUE_SAMPLE_ALPHA) {
                        val red = (color ushr 16) and 0xFF
                        val green = (color ushr 8) and 0xFF
                        val blue = color and 0xFF
                        total += 0.2126 * red + 0.7152 * green + 0.0722 * blue
                        count += 1
                    }
                    x += step
                }
                y += step
            }
            if (count == 0) null else (total / (count * 255.0)).toFloat()
        }
    }

    private fun writeAdjustedBitmap(
        context: Context,
        bitmap: Bitmap,
        adjustments: QuickEditAdjustments,
        prefix: String,
    ): Uri {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(adjustments.colorMatrix())
            }
            Canvas(output).drawBitmap(bitmap, 0f, 0f, paint)
            ClothingImageStore.writeProcessedBitmap(context, output, prefix = prefix)
        } finally {
            output.recycle()
        }
    }

    private fun QuickEditAdjustments.colorMatrix(): ColorMatrix {
        val brightnessOffset = brightness.coerceIn(-1f, 1f) * MAX_BRIGHTNESS_OFFSET
        val warmth = temperature.coerceIn(-1f, 1f) * MAX_TEMPERATURE_OFFSET
        return ColorMatrix().apply {
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, warmth,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, -warmth,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, brightnessOffset,
                        0f, 1f, 0f, 0f, brightnessOffset,
                        0f, 0f, 1f, 0f, brightnessOffset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }

    private fun decodeBitmap(context: Context, sourceUri: Uri): Bitmap? =
        context.contentResolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream)

    private inline fun <T> Bitmap.useForQuickEdit(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    private const val MAX_BRIGHTNESS_OFFSET = 110f
    private const val MAX_TEMPERATURE_OFFSET = 46f
    private const val DEFAULT_ERASER_RADIUS_RATIO = 0.065f
    private const val OPAQUE_SAMPLE_ALPHA = 64
}

data class QuickEditAdjustments(
    val brightness: Float = 0f,
    val temperature: Float = 0f,
) {
    val hasColorAdjustment: Boolean = brightness != 0f || temperature != 0f
}

/** Draft state for the live Quick Edit preview and the full-resolution Save path. */
data class QuickEditDraftState(
    val sourceUri: Uri,
    val adjustments: QuickEditAdjustments = QuickEditAdjustments(),
    val pendingSegment: InteractiveSegmentResult? = null,
    val committedSegment: InteractiveSegmentResult? = null,
    val previewGenerationId: Long = 0L,
)

data class NormalizedImagePoint(
    val x: Float,
    val y: Float,
)

interface InteractiveGarmentSegmenter {
    val isAvailable: Boolean
    suspend fun highlightSegment(context: Context, imageUri: Uri, point: NormalizedImagePoint): InteractiveSegmentResult
    suspend fun eraseSegment(context: Context, imageUri: Uri, segment: InteractiveSegmentResult): Uri
}

data class InteractiveSegmentResult(
    val point: NormalizedImagePoint,
    val mask: InteractiveSegmentMask? = null,
    val brushPoints: List<NormalizedImagePoint> = listOf(point),
)

data class InteractiveSegmentMask(
    val width: Int,
    val height: Int,
    val alpha: ByteArray,
) {
    fun isSelected(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return alpha[y * width + x].toInt() and 0xFF >= MASK_SELECTED_ALPHA
    }

    fun selectedRatio(): Float {
        if (alpha.isEmpty()) return 0f
        val selected = alpha.count { (it.toInt() and 0xFF) >= MASK_SELECTED_ALPHA }
        return selected.toFloat() / alpha.size.toFloat()
    }

    fun isSelected(point: NormalizedImagePoint): Boolean {
        val x = (point.x.coerceIn(0f, 1f) * (width - 1)).roundToInt()
        val y = (point.y.coerceIn(0f, 1f) * (height - 1)).roundToInt()
        return isSelected(x, y)
    }

    fun isBroadOrInvariantFor(point: NormalizedImagePoint): Boolean {
        val ratio = selectedRatio()
        return ratio <= MIN_USEFUL_SELECTED_RATIO || ratio >= MAX_USEFUL_SELECTED_RATIO || !isSelected(point)
    }

    override fun equals(other: Any?): Boolean =
        other is InteractiveSegmentMask &&
            width == other.width &&
            height == other.height &&
            alpha.contentEquals(other.alpha)

    override fun hashCode(): Int =
        31 * (31 * width + height) + alpha.contentHashCode()

    private companion object {
        const val MASK_SELECTED_ALPHA = 96
        const val MIN_USEFUL_SELECTED_RATIO = 0.002f
        const val MAX_USEFUL_SELECTED_RATIO = 0.72f
    }
}

/** MediaPipe Interactive Segmenter is not bundled yet; this keeps the UI non-blocking. */
class UnavailableInteractiveGarmentSegmenter : InteractiveGarmentSegmenter {
    override val isAvailable: Boolean = false

    override suspend fun highlightSegment(
        context: Context,
        imageUri: Uri,
        point: NormalizedImagePoint,
    ): InteractiveSegmentResult = InteractiveSegmentResult(point)

    override suspend fun eraseSegment(
        context: Context,
        imageUri: Uri,
        segment: InteractiveSegmentResult,
    ): Uri = imageUri
}
