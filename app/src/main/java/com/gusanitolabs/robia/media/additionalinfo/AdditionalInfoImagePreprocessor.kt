package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoTensorStats
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object AdditionalInfoImagePreprocessor {
    data class PreprocessedInput(
        val tensor: ByteBuffer,
        val stats: AdditionalInfoTensorStats,
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
        if (inputSpec.shape != EXPECTED_SHAPE) return null
        if (inputSpec.normalizationType != NORMALIZATION_TYPE) return null

        val decoded = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return null
        return try {
            val source = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
            val composited = compositeAlphaOverWhite(source)
            val resized = Bitmap.createScaledBitmap(composited, INPUT_SIZE, INPUT_SIZE, true)
            try {
                val tensorWithStats = toMobileNetV3Tensor(resized)
                PreprocessedInput(
                    tensor = tensorWithStats.tensor,
                    stats = tensorWithStats.stats,
                    sourceWidth = decoded.width,
                    sourceHeight = decoded.height,
                    preprocessing = PREPROCESSING_DESCRIPTION,
                )
            } finally {
                if (resized !== composited) resized.recycle()
                if (composited !== source) composited.recycle()
                if (source !== decoded) source.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun compositeAlphaOverWhite(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return output
    }

    private data class TensorWithStats(
        val tensor: ByteBuffer,
        val stats: AdditionalInfoTensorStats,
    )

    private fun toMobileNetV3Tensor(bitmap: Bitmap): TensorWithStats {
        val buffer = ByteBuffer.allocateDirect(FLOAT_BYTES * INPUT_SIZE * INPUT_SIZE * RGB_CHANNELS)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val channelSums = DoubleArray(RGB_CHANNELS)
        val channelMins = FloatArray(RGB_CHANNELS) { Float.POSITIVE_INFINITY }
        val channelMaxs = FloatArray(RGB_CHANNELS) { Float.NEGATIVE_INFINITY }
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0
        var sumSquares = 0.0
        var nonFiniteCount = 0
        var checksum = CHECKSUM_SEED

        pixels.forEach { pixel ->
            val values = floatArrayOf(
                normalize(Color.red(pixel)),
                normalize(Color.green(pixel)),
                normalize(Color.blue(pixel)),
            )
            values.forEachIndexed { channel, value ->
                buffer.putFloat(value)
                if (value.isFinite()) {
                    min = minOf(min, value)
                    max = maxOf(max, value)
                    sum += value
                    sumSquares += value * value
                    channelSums[channel] += value
                    channelMins[channel] = minOf(channelMins[channel], value)
                    channelMaxs[channel] = maxOf(channelMaxs[channel], value)
                } else {
                    nonFiniteCount += 1
                }
                checksum = checksum * CHECKSUM_MULTIPLIER + java.lang.Float.floatToIntBits(value)
            }
        }
        buffer.rewind()

        val valueCount = INPUT_SIZE * INPUT_SIZE * RGB_CHANNELS
        val finiteCount = valueCount - nonFiniteCount
        val mean = if (finiteCount > 0) (sum / finiteCount).toFloat() else Float.NaN
        val variance = if (finiteCount > 0) (sumSquares / finiteCount) - (mean * mean) else Double.NaN
        val pixelsPerChannel = INPUT_SIZE * INPUT_SIZE
        val stats = AdditionalInfoTensorStats(
            min = if (finiteCount > 0) min else Float.NaN,
            max = if (finiteCount > 0) max else Float.NaN,
            mean = mean,
            standardDeviation = sqrt(maxOf(variance, 0.0)).toFloat(),
            channelMeans = channelSums.map { (it / pixelsPerChannel).toFloat() },
            channelMins = channelMins.toList(),
            channelMaxs = channelMaxs.toList(),
            nonFiniteCount = nonFiniteCount,
            checksum = checksum.toString(radix = 16),
        )
        return TensorWithStats(buffer, stats)
    }

    private fun normalize(channel: Int): Float = channel / 127.5f - 1f

    private const val INPUT_SIZE = 224
    private const val RGB_CHANNELS = 3
    private const val FLOAT_BYTES = 4
    private const val NORMALIZATION_TYPE = "mobilenet_v3_preprocess_input"
    private const val PREPROCESSING_DESCRIPTION = "decode_argb8888__alpha_over_white_before_resize__rgb_float32_nhwc__mobilenet_v3"
    private val EXPECTED_SHAPE = listOf(1, INPUT_SIZE, INPUT_SIZE, RGB_CHANNELS)
    private const val CHECKSUM_SEED = 1125899906842597L
    private const val CHECKSUM_MULTIPLIER = 31L
}
