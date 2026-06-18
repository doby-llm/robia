package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.gusanitolabs.robia.core.model.MainColor
import java.io.File
import java.util.UUID
import kotlin.math.pow

object ClothingImageStore {
    private const val IMAGE_DIRECTORY = "robia_clothing_images"

    fun createCaptureUri(context: Context): Uri {
        val imageFile = createImageFile(context, "camera")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
    }

    fun copyContentUriToPrivateStorage(context: Context, sourceUri: Uri): Uri {
        val imageFile = createImageFile(context, "gallery")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            imageFile.outputStream().use(input::copyTo)
        } ?: error("Unable to open selected image")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
    }

    fun extractNearestPaletteColors(
        context: Context,
        imageUri: Uri,
        palette: List<MainColor>,
    ): List<MainColor> {
        if (palette.isEmpty()) return emptyList()
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return emptyList()
        return bitmap.useForColors { source ->
            dominantRgbBuckets(source)
                .mapNotNull { rgb -> palette.nearestTo(rgb) }
                .distinctBy(MainColor::id)
                .take(2)
        }
    }

    private fun createImageFile(context: Context, prefix: String): File {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val imageDir = File(picturesDir, IMAGE_DIRECTORY).apply { mkdirs() }
        return File(imageDir, "$prefix-${UUID.randomUUID()}.jpg")
    }

    private inline fun <T> Bitmap.useForColors(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    private fun dominantRgbBuckets(bitmap: Bitmap): List<Rgb> {
        val maxDimension = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val sampleStep = (maxDimension / 96).coerceAtLeast(1)
        val buckets = mutableMapOf<Int, Bucket>()

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val alpha = (color ushr 24) and 0xFF
                if (alpha > 180) {
                    val red = (color ushr 16) and 0xFF
                    val green = (color ushr 8) and 0xFF
                    val blue = color and 0xFF
                    val key = ((red / 32) shl 16) or ((green / 32) shl 8) or (blue / 32)
                    buckets.getOrPut(key) { Bucket() }.add(red, green, blue)
                }
                x += sampleStep
            }
            y += sampleStep
        }

        return buckets.values
            .sortedByDescending(Bucket::count)
            .map(Bucket::average)
            .take(8)
    }

    private fun List<MainColor>.nearestTo(rgb: Rgb): MainColor? = minByOrNull { color ->
        val paletteRgb = color.hex.toRgbOrNull() ?: return@minByOrNull Double.MAX_VALUE
        (rgb.red - paletteRgb.red).toDouble().pow(2) +
            (rgb.green - paletteRgb.green).toDouble().pow(2) +
            (rgb.blue - paletteRgb.blue).toDouble().pow(2)
    }

    private fun String.toRgbOrNull(): Rgb? {
        val normalized = trim().removePrefix("#")
        if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            return null
        }
        return Rgb(
            red = normalized.substring(0, 2).toInt(16),
            green = normalized.substring(2, 4).toInt(16),
            blue = normalized.substring(4, 6).toInt(16),
        )
    }

    private data class Rgb(val red: Int, val green: Int, val blue: Int)

    private class Bucket {
        var red: Long = 0
        var green: Long = 0
        var blue: Long = 0
        var count: Int = 0

        fun add(red: Int, green: Int, blue: Int) {
            this.red += red.toLong()
            this.green += green.toLong()
            this.blue += blue.toLong()
            count += 1
        }

        fun average(): Rgb = Rgb(
            red = (red / count.coerceAtLeast(1)).toInt(),
            green = (green / count.coerceAtLeast(1)).toInt(),
            blue = (blue / count.coerceAtLeast(1)).toInt(),
        )
    }
}
