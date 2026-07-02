package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
    ): Uri = applyAdjustments(context, draft.sourceUri, draft.adjustments)

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
    val previewGenerationId: Long = 0L,
)
