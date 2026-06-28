package com.gusanitolabs.robia.media.additionalinfo

import com.gusanitolabs.robia.core.model.AdditionalInfoTensorStats
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object AdditionalInfoPreprocessingPolicy {
    const val inputSize = 224
    const val rgbChannels = 3
    const val floatBytes = 4
    const val normalizationType = "mobilenet_v3_preprocess_input"
    const val description = "decode_argb8888__square_pad_preserve_aspect__auto_background_composite__resize__rgb_float32_nhwc__mobilenet_v3"
    val expectedShape = listOf(1, inputSize, inputSize, rgbChannels)
}

data class AdditionalInfoTensorWithStats(
    val tensor: ByteBuffer,
    val stats: AdditionalInfoTensorStats,
)

object AdditionalInfoTensorBuilder {
    fun fromRgbPixels(rgbPixels: IntArray): AdditionalInfoTensorWithStats {
        require(rgbPixels.size == AdditionalInfoPreprocessingPolicy.inputSize * AdditionalInfoPreprocessingPolicy.inputSize) {
            "Expected ${AdditionalInfoPreprocessingPolicy.inputSize}x${AdditionalInfoPreprocessingPolicy.inputSize} RGB pixels, got ${rgbPixels.size}"
        }
        val buffer = ByteBuffer
            .allocateDirect(
                AdditionalInfoPreprocessingPolicy.floatBytes *
                    AdditionalInfoPreprocessingPolicy.inputSize *
                    AdditionalInfoPreprocessingPolicy.inputSize *
                    AdditionalInfoPreprocessingPolicy.rgbChannels,
            )
            .order(ByteOrder.nativeOrder())

        val channelSums = DoubleArray(AdditionalInfoPreprocessingPolicy.rgbChannels)
        val channelMins = FloatArray(AdditionalInfoPreprocessingPolicy.rgbChannels) { Float.POSITIVE_INFINITY }
        val channelMaxs = FloatArray(AdditionalInfoPreprocessingPolicy.rgbChannels) { Float.NEGATIVE_INFINITY }
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0
        var sumSquares = 0.0
        var nonFiniteCount = 0
        var checksum = CHECKSUM_SEED

        rgbPixels.forEach { pixel ->
            val values = floatArrayOf(
                normalize((pixel shr 16) and 0xff),
                normalize((pixel shr 8) and 0xff),
                normalize(pixel and 0xff),
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

        val valueCount = rgbPixels.size * AdditionalInfoPreprocessingPolicy.rgbChannels
        val finiteCount = valueCount - nonFiniteCount
        val mean = if (finiteCount > 0) (sum / finiteCount).toFloat() else Float.NaN
        val variance = if (finiteCount > 0) (sumSquares / finiteCount) - (mean * mean) else Double.NaN
        val pixelsPerChannel = AdditionalInfoPreprocessingPolicy.inputSize * AdditionalInfoPreprocessingPolicy.inputSize
        return AdditionalInfoTensorWithStats(
            tensor = buffer,
            stats = AdditionalInfoTensorStats(
                min = if (finiteCount > 0) min else Float.NaN,
                max = if (finiteCount > 0) max else Float.NaN,
                mean = mean,
                standardDeviation = sqrt(maxOf(variance, 0.0)).toFloat(),
                channelMeans = channelSums.map { (it / pixelsPerChannel).toFloat() },
                channelMins = channelMins.toList(),
                channelMaxs = channelMaxs.toList(),
                nonFiniteCount = nonFiniteCount,
                checksum = checksum.toString(radix = 16),
            ),
        )
    }

    fun normalize(channel: Int): Float = channel / 127.5f - 1f

    private const val CHECKSUM_SEED = 1125899906842597L
    private const val CHECKSUM_MULTIPLIER = 31L
}
