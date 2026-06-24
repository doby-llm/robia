package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoTensorStats
import java.nio.ByteBuffer

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
        if (inputSpec.shape != AdditionalInfoPreprocessingPolicy.expectedShape) return null
        if (inputSpec.normalizationType != AdditionalInfoPreprocessingPolicy.normalizationType) return null

        val decoded = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return null
        return try {
            val source = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
            val composited = compositeAlphaOverWhite(source)
            val resized = Bitmap.createScaledBitmap(
                composited,
                AdditionalInfoPreprocessingPolicy.inputSize,
                AdditionalInfoPreprocessingPolicy.inputSize,
                true,
            )
            try {
                val tensorWithStats = AdditionalInfoTensorBuilder.fromRgbPixels(resized.toRgbPixels())
                PreprocessedInput(
                    tensor = tensorWithStats.tensor,
                    stats = tensorWithStats.stats,
                    sourceWidth = decoded.width,
                    sourceHeight = decoded.height,
                    preprocessing = AdditionalInfoPreprocessingPolicy.description,
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

    private fun Bitmap.toRgbPixels(): IntArray {
        val size = AdditionalInfoPreprocessingPolicy.inputSize
        val pixels = IntArray(size * size)
        getPixels(pixels, 0, size, 0, 0, size, size)
        return pixels
    }
}
