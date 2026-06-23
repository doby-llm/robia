package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AdditionalInfoImagePreprocessor {
    fun preprocess(context: Context, imageUri: Uri, inputSpec: AdditionalInfoInputSpec): ByteBuffer? {
        if (inputSpec.shape != listOf(1, INPUT_SIZE, INPUT_SIZE, RGB_CHANNELS)) return null
        val decoded = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return null
        return try {
            val source = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
            val resized = Bitmap.createScaledBitmap(source, INPUT_SIZE, INPUT_SIZE, true)
            try {
                toMobileNetV3Tensor(resized)
            } finally {
                if (resized !== source) resized.recycle()
                if (source !== decoded) source.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun toMobileNetV3Tensor(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(FLOAT_BYTES * INPUT_SIZE * INPUT_SIZE * RGB_CHANNELS)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        pixels.forEach { pixel ->
            val alpha = Color.alpha(pixel)
            val red = compositeOverWhite(Color.red(pixel), alpha)
            val green = compositeOverWhite(Color.green(pixel), alpha)
            val blue = compositeOverWhite(Color.blue(pixel), alpha)
            buffer.putFloat(normalize(red))
            buffer.putFloat(normalize(green))
            buffer.putFloat(normalize(blue))
        }
        buffer.rewind()
        return buffer
    }

    private fun compositeOverWhite(channel: Int, alpha: Int): Int =
        ((channel * alpha) + (WHITE_CHANNEL * (WHITE_CHANNEL - alpha))) / WHITE_CHANNEL

    private fun normalize(channel: Int): Float = channel / 127.5f - 1f

    private const val INPUT_SIZE = 224
    private const val RGB_CHANNELS = 3
    private const val FLOAT_BYTES = 4
    private const val WHITE_CHANNEL = 255
}
