package com.gusanitolabs.robia.suggestions.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** PNG bytes exactly as they are passed to the engine. */
class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)

/** Saved garment photo composited on white, longest edge at most [EmbeddingRecipe.IMAGE_MAX_EDGE_PX], PNG (DEC-005). */
object ImageInputEncoder {
    suspend fun encode(context: Context, uri: Uri): EncodedImage = withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot open $uri")
        encodeBytes(source)
    }

    fun encodeBytes(source: ByteArray): EncodedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        require(longestEdge > 0) { "Image has no readable size" }

        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longestEdge) },
        ) ?: throw IllegalArgumentException("Image cannot be decoded")

        val scale = minOf(1f, EmbeddingRecipe.IMAGE_MAX_EDGE_PX.toFloat() / max(decoded.width, decoded.height))
        val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(decoded, width, height, true) else decoded

        val composited = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(composited).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        val png = ByteArrayOutputStream().also { composited.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        composited.recycle()
        return EncodedImage(png, width, height)
    }

    /** Largest power of two that keeps the decoded longest edge at or above the target. */
    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= EmbeddingRecipe.IMAGE_MAX_EDGE_PX) sample *= 2
        return sample
    }
}
