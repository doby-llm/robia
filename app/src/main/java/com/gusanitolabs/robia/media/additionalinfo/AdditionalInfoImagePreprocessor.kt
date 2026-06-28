package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoTensorStats
import java.nio.ByteBuffer
import kotlin.math.max

object AdditionalInfoImagePreprocessor {
    data class PreprocessedInput(
        val tensor: ByteBuffer,
        val stats: AdditionalInfoTensorStats,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val preprocessing: String,
    )

    data class ExactInputBitmap(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val preprocessing: String,
    )

    fun preprocess(context: Context, imageUri: Uri, inputSpec: AdditionalInfoInputSpec): ByteBuffer? =
        preprocessWithDiagnostics(context, imageUri, inputSpec)?.tensor

    fun preprocessWithDiagnostics(
        context: Context,
        imageUri: Uri,
        inputSpec: AdditionalInfoInputSpec,
    ): PreprocessedInput? {
        if (inputSpec.shape != AdditionalInfoPreprocessingPolicy.expectedShape) return null
        if (inputSpec.normalizationType != AdditionalInfoPreprocessingPolicy.normalizationType) return null

        val exactInput = createExactInputBitmap(context, imageUri, inputSpec) ?: return null
        return try {
            val tensorWithStats = AdditionalInfoTensorBuilder.fromRgbPixels(exactInput.bitmap.toRgbPixels())
            PreprocessedInput(
                tensor = tensorWithStats.tensor,
                stats = tensorWithStats.stats,
                sourceWidth = exactInput.sourceWidth,
                sourceHeight = exactInput.sourceHeight,
                preprocessing = exactInput.preprocessing,
            )
        } finally {
            exactInput.bitmap.recycle()
        }
    }

    fun createExactInputBitmap(
        context: Context,
        imageUri: Uri,
        inputSpec: AdditionalInfoInputSpec,
    ): ExactInputBitmap? {
        if (inputSpec.shape != AdditionalInfoPreprocessingPolicy.expectedShape) return null
        if (inputSpec.normalizationType != AdditionalInfoPreprocessingPolicy.normalizationType) return null

        val decoded = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return null
        return try {
            val source = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
            val background = chooseCompositeBackground(source)
            val square = squarePadAndComposite(source, background.color)
            val resized = Bitmap.createScaledBitmap(
                square,
                AdditionalInfoPreprocessingPolicy.inputSize,
                AdditionalInfoPreprocessingPolicy.inputSize,
                true,
            )
            if (resized !== square) square.recycle()
            if (source !== decoded) source.recycle()
            ExactInputBitmap(
                bitmap = resized,
                sourceWidth = decoded.width,
                sourceHeight = decoded.height,
                preprocessing = "${AdditionalInfoPreprocessingPolicy.description}__background_${background.descriptionName}",
            )
        } finally {
            decoded.recycle()
        }
    }

    private fun squarePadAndComposite(source: Bitmap, backgroundColor: Int): Bitmap {
        val size = max(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)
        val left = (size - source.width) / 2
        val top = (size - source.height) / 2
        canvas.drawBitmap(source, null, Rect(left, top, left + source.width, top + source.height), null)
        return output
    }

    private fun chooseCompositeBackground(source: Bitmap): CompositeBackground {
        val sampleStep = max(1, max(source.width, source.height) / FOREGROUND_SAMPLE_TARGET)
        var weightedLuminance = 0.0
        var weightSum = 0.0
        for (y in 0 until source.height step sampleStep) {
            for (x in 0 until source.width step sampleStep) {
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha < FOREGROUND_ALPHA_THRESHOLD) continue
                val weight = alpha / 255.0
                weightedLuminance += relativeLuminance(pixel) * weight
                weightSum += weight
            }
        }
        if (weightSum < MIN_FOREGROUND_SAMPLE_WEIGHT) return CompositeBackground.White

        val foregroundLuminance = (weightedLuminance / weightSum).coerceIn(0.0, 1.0)
        val contrastAgainstWhite = contrastRatio(foregroundLuminance, WHITE_LUMINANCE)
        val contrastAgainstBlack = contrastRatio(foregroundLuminance, BLACK_LUMINANCE)
        return if (
            foregroundLuminance >= LIGHT_FOREGROUND_LUMINANCE &&
            contrastAgainstBlack - contrastAgainstWhite >= MIN_CONTRAST_DELTA
        ) {
            CompositeBackground.Black
        } else {
            CompositeBackground.White
        }
    }

    private fun relativeLuminance(pixel: Int): Double =
        (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)) / 255.0

    private fun contrastRatio(first: Double, second: Double): Double {
        val lighter = max(first, second)
        val darker = minOf(first, second)
        return (lighter + CONTRAST_EPSILON) / (darker + CONTRAST_EPSILON)
    }

    private fun Bitmap.toRgbPixels(): IntArray {
        val size = AdditionalInfoPreprocessingPolicy.inputSize
        val pixels = IntArray(size * size)
        getPixels(pixels, 0, size, 0, 0, size, size)
        return pixels
    }

    private enum class CompositeBackground(val color: Int, val descriptionName: String) {
        White(Color.WHITE, "white"),
        Black(Color.BLACK, "black"),
    }

    private const val FOREGROUND_ALPHA_THRESHOLD = 64
    private const val FOREGROUND_SAMPLE_TARGET = 128
    private const val MIN_FOREGROUND_SAMPLE_WEIGHT = 8.0
    private const val LIGHT_FOREGROUND_LUMINANCE = 0.58
    private const val MIN_CONTRAST_DELTA = 1.0
    private const val WHITE_LUMINANCE = 1.0
    private const val BLACK_LUMINANCE = 0.0
    private const val CONTRAST_EPSILON = 0.05
}
