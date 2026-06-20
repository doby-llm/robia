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
    ): List<MainColor> = extractPaletteColorMatches(context, imageUri, palette)
        .map(PaletteColorMatch::color)
        .take(2)

    fun extractPaletteColorMatches(
        context: Context,
        imageUri: Uri,
        palette: List<MainColor>,
    ): List<PaletteColorMatch> {
        if (palette.isEmpty()) return emptyList()
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return emptyList()
        return bitmap.useForColors { source -> paletteMatches(source, palette) }
    }

    fun cropTransparentPixels(
        context: Context,
        imageUri: Uri,
    ): Uri {
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream) ?: return imageUri
        return bitmap.useForColors { source ->
            val cropBounds = transparentContentBounds(source) ?: return@useForColors imageUri
            if (cropBounds.isFullSize(source.width, source.height)) return@useForColors imageUri
            val cropped = Bitmap.createBitmap(source, cropBounds.left, cropBounds.top, cropBounds.width, cropBounds.height)
            try {
                writeProcessedBitmap(context, cropped, prefix = "cropped-subject")
            } finally {
                cropped.recycle()
            }
        }
    }

    fun writeProcessedBitmap(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "subject",
    ): Uri {
        val imageFile = createImageFile(context, prefix, extension = "png")
        imageFile.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode processed image" }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
    }

    private fun createImageFile(context: Context, prefix: String, extension: String = "jpg"): File {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val imageDir = File(picturesDir, IMAGE_DIRECTORY).apply { mkdirs() }
        return File(imageDir, "$prefix-${UUID.randomUUID()}.$extension")
    }

    private inline fun <T> Bitmap.useForColors(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    private fun paletteMatches(bitmap: Bitmap, palette: List<MainColor>): List<PaletteColorMatch> {
        val maxDimension = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val sampleStep = (maxDimension / 96).coerceAtLeast(1)
        val counts = mutableMapOf<MainColor, Int>()
        var visiblePixels = 0

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
                    val nearest = palette.nearestTo(Rgb(red, green, blue))
                    if (nearest != null) {
                        counts[nearest] = (counts[nearest] ?: 0) + 1
                        visiblePixels += 1
                    }
                }
                x += sampleStep
            }
            y += sampleStep
        }

        if (visiblePixels == 0) return emptyList()
        return counts.entries
            .sortedByDescending { it.value }
            .map { (color, count) ->
                PaletteColorMatch(
                    color = color,
                    pixelCount = count,
                    ratio = count.toFloat() / visiblePixels.toFloat(),
                )
            }
    }

    private fun transparentContentBounds(bitmap: Bitmap): CropBounds? {
        if (!bitmap.hasAlpha()) return null
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha > TRANSPARENT_CROP_ALPHA_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) return null
        val contentWidth = maxX - minX + 1
        val contentHeight = maxY - minY + 1
        val bitmapArea = bitmap.width * bitmap.height
        val contentArea = contentWidth * contentHeight
        if (contentWidth < MIN_CROP_CONTENT_SIZE || contentHeight < MIN_CROP_CONTENT_SIZE) return null
        if (contentArea < bitmapArea / 100) return null

        val padding = (maxOf(bitmap.width, bitmap.height) * CROP_PADDING_RATIO).toInt().coerceAtLeast(MIN_CROP_PADDING_PX)
        val left = (minX - padding).coerceAtLeast(0)
        val top = (minY - padding).coerceAtLeast(0)
        val right = (maxX + padding).coerceAtMost(bitmap.width - 1)
        val bottom = (maxY + padding).coerceAtMost(bitmap.height - 1)
        return CropBounds(left = left, top = top, width = right - left + 1, height = bottom - top + 1)
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

    data class PaletteColorMatch(
        val color: MainColor,
        val pixelCount: Int,
        val ratio: Float,
    )

    private data class CropBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        fun isFullSize(bitmapWidth: Int, bitmapHeight: Int): Boolean =
            left == 0 && top == 0 && width == bitmapWidth && height == bitmapHeight
    }

    private const val TRANSPARENT_CROP_ALPHA_THRESHOLD = 24
    private const val MIN_CROP_CONTENT_SIZE = 16
    private const val MIN_CROP_PADDING_PX = 8
    private const val CROP_PADDING_RATIO = 0.04f
}
