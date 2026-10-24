package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.gusanitolabs.robia.core.color.GarmentColorAnalyzer
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.color.RgbColor
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.sync.PhotoBlobKeys
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object ClothingImageStore {
    private const val IMAGE_DIRECTORY = "robia_clothing_images"
    private const val THUMBNAIL_DIRECTORY = "robia_thumbnails"

    /** Names this store gives to the files of a batch draft: the raw copy, the subject, the cropped cutout and the original. */
    private val BATCH_DRAFT_FILE_NAME = Regex("(gallery|camera|subject|cropped-subject|original)-[0-9a-f-]{36}\\.(jpg|png)")

    /** The files a save may release: raw copies, subjects and originals. A cutout (cropped-subject) is never released. */
    private val RELEASABLE_FILE_NAME = Regex("(gallery|camera|subject|original)-[0-9a-f-]{36}\\.(jpg|png)")

    /**
     * Image-folder files a saved photo makes unreferenced (its raw copy, its subject, a replaced original), keyed by the
     * saved photo's URI. Filled before the save and emptied by [releasePhotoFiles] once the save has committed.
     */
    private val filesToReleaseOnSave = ConcurrentHashMap<String, List<File>>()

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

    fun readImageBlob(context: Context, imageUri: Uri): ImageBlob? {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { input -> input.readBytes() } ?: return null
        return ImageBlob(
            bytes = bytes,
            mimeType = context.contentResolver.getType(imageUri),
            contentHash = bytes.sha256Hex(),
            byteMagic = bytes.magicHex(),
        )
    }

    /**
     * Encodes a garment photo as the lossy WebP that Drive stores: longest edge capped at
     * [DRIVE_WEBP_MAX_EDGE_PX], quality [DRIVE_WEBP_QUALITY], alpha preserved.
     */
    fun encodeDriveWebp(context: Context, imageUri: Uri): ImageBlob {
        val sourceMimeType = context.contentResolver.getType(imageUri)
        val bitmap = decodeBitmap(context, imageUri) ?: error(
            "Source garment image is not decodable before Drive upload mime=${sourceMimeType ?: "unknown"}",
        )
        return bitmap.useForColors { source ->
            val (targetWidth, targetHeight) = thumbnailSize(source.width, source.height, DRIVE_WEBP_MAX_EDGE_PX)
            val scaled = if (targetWidth == source.width && targetHeight == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
            }
            try {
                val canonicalBytes = ByteArrayOutputStream().use { output ->
                    check(scaled.compressDriveWebp(output)) { "Unable to encode Drive WebP garment image." }
                    output.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(canonicalBytes, 0, canonicalBytes.size, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "Drive WebP garment image failed post-encode decode verification."
                }
                ImageBlob(
                    bytes = canonicalBytes,
                    mimeType = "image/webp",
                    contentHash = canonicalBytes.sha256Hex(),
                    byteMagic = canonicalBytes.magicHex(),
                    decodedWidth = bounds.outWidth,
                    decodedHeight = bounds.outHeight,
                    sourceMimeType = sourceMimeType,
                )
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        }
    }

    /**
     * Moves a downloaded temp file into the restored-photo slot. The name depends only on the garment
     * id and content hash, so restoring the same photo again replaces the same file.
     */
    fun writeRestoredImageFile(
        context: Context,
        garmentId: String,
        hash16: String,
        source: File,
    ): Uri {
        val imageDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            IMAGE_DIRECTORY,
        ).apply { mkdirs() }
        val target = File(imageDir, PhotoBlobKeys.restoredLocalFileName(garmentId, hash16))
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
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
    ): List<PaletteColorMatch> = extractPaletteColorDiagnostics(context, imageUri, palette).matches

    fun extractPaletteColorDiagnostics(
        context: Context,
        imageUri: Uri,
        palette: List<MainColor>,
    ): PaletteColorDiagnostics {
        if (palette.isEmpty()) return PaletteColorDiagnostics(paletteSize = 0)
        val bitmap = decodeForColors(context, imageUri)
            ?: return PaletteColorDiagnostics(paletteSize = palette.size)
        return bitmap.useForColors { source ->
            val sampleStep = colorSampleStep(source)
            PaletteColorDiagnostics(
                width = source.width,
                height = source.height,
                sampleStep = sampleStep,
                sampleGridEstimate = sampleGridEstimate(source, sampleStep),
                paletteSize = palette.size,
                matches = paletteMatches(source, palette, sampleStep),
            )
        }
    }

    fun readImageAspectRatio(context: Context, imageUri: Uri): Float? {
        val (width, height) = readImageDimensions(context, imageUri) ?: return null
        return width.toFloat() / height.toFloat()
    }

    fun readImageMetrics(context: Context, imageUri: Uri): ImageMetrics? {
        val (width, height) = readImageDimensions(context, imageUri) ?: return null
        val byteSize = runCatching {
            context.contentResolver.openFileDescriptor(imageUri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()
        return ImageMetrics(width = width, height = height, byteSize = byteSize)
    }

    /** Header-only size read. A bounds-only decode returns null, so the size is read from the options it fills. */
    fun readImageDimensions(context: Context, imageUri: Uri): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(imageUri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    fun estimateCentralLuminance(context: Context, imageUri: Uri): Float? {
        val (width, height) = readImageDimensions(context, imageUri) ?: return null

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = luminanceDecodeSampleSize(width, height)
        }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        return bitmap.useForColors { source ->
            estimateCentralLuminance(source) ?: estimateLuminance(source)
        }
    }

    fun exportImageAsPng(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
    ) {
        val bitmap = context.contentResolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream)
            ?: error("Unable to open garment image")
        bitmap.useForColors { source ->
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                check(source.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode PNG" }
            } ?: error("Unable to open export destination")
        }
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

    fun getOrCreateBoundedThumbnail(
        context: Context,
        imageUri: Uri,
        maxEdgePx: Int,
    ): BoundedThumbnail? {
        if (maxEdgePx <= 0) return null
        val metrics = readImageMetrics(context, imageUri) ?: return null
        val cacheKey = thumbnailCacheKey(imageUri, metrics, maxEdgePx)
        val thumbnailDir = File(context.filesDir, THUMBNAIL_DIRECTORY).apply { mkdirs() }
        existingThumbnailFile(thumbnailDir, cacheKey)?.let { existing ->
            val existingUri = contentUriFor(context, existing)
            return BoundedThumbnail(
                uri = existingUri,
                source = metrics,
                thumbnail = readImageMetrics(context, existingUri),
                maxEdgePx = maxEdgePx,
                cacheHit = true,
            )
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = thumbnailDecodeSampleSize(metrics.width, metrics.height, maxEdgePx)
        }
        val decoded = context.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        return decoded.useForColors { sampled ->
            val (thumbnailWidth, thumbnailHeight) = thumbnailSize(sampled.width, sampled.height, maxEdgePx)
            val thumbnail = if (thumbnailWidth == sampled.width && thumbnailHeight == sampled.height) {
                sampled
            } else {
                Bitmap.createScaledBitmap(sampled, thumbnailWidth, thumbnailHeight, true)
            }
            try {
                val hasAlpha = thumbnail.hasAlpha()
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val extension = if (hasAlpha) "png" else "jpg"
                val outputFile = File(thumbnailDir, "$cacheKey.$extension")
                outputFile.outputStream().use { output ->
                    check(thumbnail.compress(format, if (hasAlpha) 100 else THUMBNAIL_JPEG_QUALITY, output)) {
                        "Unable to encode bounded garment thumbnail."
                    }
                }
                BoundedThumbnail(
                    uri = contentUriFor(context, outputFile),
                    source = metrics,
                    thumbnail = ImageMetrics(
                        width = thumbnail.width,
                        height = thumbnail.height,
                        byteSize = outputFile.length().takeIf { it >= 0L },
                    ),
                    maxEdgePx = maxEdgePx,
                    cacheHit = false,
                )
            } finally {
                if (thumbnail !== sampled) thumbnail.recycle()
            }
        }
    }

    /**
     * Deletes the image files a batch draft created (raw copy, subject, cropped cutout), given as URIs. Only names
     * this store creates in the image folder are deleted, so other photos are never touched.
     */
    fun deleteBatchDraftFiles(context: Context, uris: List<String>) {
        val imageDir = imageDirectory(context)
        uris.mapNotNull { uri -> Uri.parse(uri).lastPathSegment }
            .filter(BATCH_DRAFT_FILE_NAME::matches)
            .forEach { name -> File(imageDir, name).delete() }
    }

    /**
     * Registers the files that become unreferenced once the photo at [savedPhotoUri] is saved. Only names this store creates
     * in the image folder are kept, and the saved photo's own file is never registered, so a crop fallback (the raw copy is
     * the photo) deletes nothing. [releasePhotoFiles] deletes them after the save commits.
     */
    fun registerPhotoFilesForRelease(context: Context, savedPhotoUri: String, uris: List<String>) {
        val savedName = Uri.parse(savedPhotoUri).lastPathSegment
        val imageDir = imageDirectory(context)
        val files = uris.mapNotNull { uri -> Uri.parse(uri).lastPathSegment }
            .filter { name -> name != savedName && RELEASABLE_FILE_NAME.matches(name) }
            .map { name -> File(imageDir, name) }
        if (files.isNotEmpty()) filesToReleaseOnSave[savedPhotoUri] = files
    }

    /** Deletes the files registered for [savedPhotoUri]. Call only after the garment with that photo has been saved. */
    fun releasePhotoFiles(savedPhotoUri: String) {
        filesToReleaseOnSave.remove(savedPhotoUri)?.forEach { file -> file.delete() }
    }

    internal fun imageDirectory(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, IMAGE_DIRECTORY)

    internal fun createImageFile(context: Context, prefix: String, extension: String = "jpg"): File {
        val imageDir = imageDirectory(context).apply { mkdirs() }
        return File(imageDir, "$prefix-${UUID.randomUUID()}.$extension")
    }

    private fun contentUriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun existingThumbnailFile(thumbnailDir: File, cacheKey: String): File? =
        listOf("jpg", "png")
            .map { extension -> File(thumbnailDir, "$cacheKey.$extension") }
            .firstOrNull { file -> file.isFile && file.length() > 0L }

    private fun thumbnailCacheKey(imageUri: Uri, metrics: ImageMetrics, maxEdgePx: Int): String {
        val source = "${imageUri}|${metrics.width}x${metrics.height}|${metrics.byteSize ?: -1L}|$maxEdgePx"
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(32)
    }

    private fun thumbnailDecodeSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        val maxDimension = maxOf(width, height).coerceAtLeast(1)
        var sampleSize = 1
        // Keep the decoded long edge >= 2x the target so the bilinear downscale is not a stair-step source (SG1).
        while (maxDimension / (sampleSize * 2) >= maxEdgePx * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun thumbnailSize(width: Int, height: Int, maxEdgePx: Int): Pair<Int, Int> {
        val maxDimension = maxOf(width, height).coerceAtLeast(1)
        if (maxDimension <= maxEdgePx) return width to height
        val scale = maxEdgePx.toFloat() / maxDimension.toFloat()
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    private fun decodeBitmap(context: Context, imageUri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream)
        }
    }.getOrNull()

    private inline fun <T> Bitmap.useForColors(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }

    @Suppress("DEPRECATION")
    private fun Bitmap.compressDriveWebp(output: OutputStream): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            compress(Bitmap.CompressFormat.WEBP_LOSSY, DRIVE_WEBP_QUALITY, output)
        } else {
            compress(Bitmap.CompressFormat.WEBP, DRIVE_WEBP_QUALITY, output)
        }

    private fun decodeForColors(context: Context, imageUri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            readImageDimensions(context, imageUri)?.let { (width, height) -> inSampleSize = colorDecodeSampleSize(width, height) }
        }
        return context.contentResolver.openInputStream(imageUri)?.use { input -> BitmapFactory.decodeStream(input, null, options) }
    }

    private fun colorDecodeSampleSize(width: Int, height: Int): Int {
        val longEdge = maxOf(width, height)
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= COLOR_MIN_DECODE_LONG_EDGE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun colorSampleStep(bitmap: Bitmap): Int {
        val maxDimension = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        return (maxDimension / 96).coerceAtLeast(1)
    }

    private fun sampleGridEstimate(bitmap: Bitmap, sampleStep: Int): Int {
        val xCount = ((bitmap.width - 1 - sampleStep / 2) / sampleStep + 1).coerceAtLeast(0)
        val yCount = ((bitmap.height - 1 - sampleStep / 2) / sampleStep + 1).coerceAtLeast(0)
        return xCount * yCount
    }

    private fun luminanceDecodeSampleSize(width: Int, height: Int): Int {
        val maxDimension = maxOf(width, height).coerceAtLeast(1)
        var sampleSize = 1
        while (maxDimension / sampleSize > LUMINANCE_MAX_DECODE_SIZE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun estimateCentralLuminance(bitmap: Bitmap): Float? {
        val left = bitmap.width / 4
        val top = bitmap.height / 4
        val rightExclusive = (bitmap.width - left).coerceAtLeast(left + 1)
        val bottomExclusive = (bitmap.height - top).coerceAtLeast(top + 1)
        return estimateLuminance(
            bitmap = bitmap,
            left = left,
            top = top,
            rightExclusive = rightExclusive,
            bottomExclusive = bottomExclusive,
        )
    }

    private fun estimateLuminance(
        bitmap: Bitmap,
        left: Int = 0,
        top: Int = 0,
        rightExclusive: Int = bitmap.width,
        bottomExclusive: Int = bitmap.height,
    ): Float? {
        val sampleWidth = rightExclusive - left
        val sampleHeight = bottomExclusive - top
        if (sampleWidth <= 0 || sampleHeight <= 0) return null

        val sampleStep = (maxOf(sampleWidth, sampleHeight) / LUMINANCE_TARGET_SAMPLE_GRID).coerceAtLeast(1)
        var luminanceSum = 0.0
        var samples = 0
        var y = top + sampleStep / 2
        while (y < bottomExclusive) {
            var x = left + sampleStep / 2
            while (x < rightExclusive) {
                val color = bitmap.getPixel(x, y)
                val alpha = (color ushr 24) and 0xFF
                if (alpha > TRANSPARENT_CROP_ALPHA_THRESHOLD) {
                    val red = (color ushr 16) and 0xFF
                    val green = (color ushr 8) and 0xFF
                    val blue = color and 0xFF
                    luminanceSum += 0.2126 * red + 0.7152 * green + 0.0722 * blue
                    samples += 1
                }
                x += sampleStep
            }
            y += sampleStep
        }
        return if (samples > 0) (luminanceSum / samples / 255.0).toFloat() else null
    }

    private fun paletteMatches(bitmap: Bitmap, palette: List<MainColor>, sampleStep: Int): List<PaletteColorMatch> {
        val samples = sequence {
            var y = sampleStep / 2
            while (y < bitmap.height) {
                var x = sampleStep / 2
                while (x < bitmap.width) {
                    val color = bitmap.getPixel(x, y)
                    yield(
                        RgbColor(
                            red = (color ushr 16) and 0xFF,
                            green = (color ushr 8) and 0xFF,
                            blue = color and 0xFF,
                            alpha = (color ushr 24) and 0xFF,
                        ),
                    )
                    x += sampleStep
                }
                y += sampleStep
            }
        }
        return GarmentColorAnalyzer.dominantPaletteMatches(samples, palette)
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

    private data class CropBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        fun isFullSize(bitmapWidth: Int, bitmapHeight: Int): Boolean =
            left == 0 && top == 0 && width == bitmapWidth && height == bitmapHeight
    }

    private const val DRIVE_WEBP_QUALITY = 90
    private const val DRIVE_WEBP_MAX_EDGE_PX = 2048
    private const val THUMBNAIL_JPEG_QUALITY = 82
    private const val TRANSPARENT_CROP_ALPHA_THRESHOLD = 24
    private const val LUMINANCE_MAX_DECODE_SIZE = 192
    private const val COLOR_MIN_DECODE_LONG_EDGE = 512
    private const val LUMINANCE_TARGET_SAMPLE_GRID = 48
    private const val MIN_CROP_CONTENT_SIZE = 16
    private const val MIN_CROP_PADDING_PX = 8
    private const val CROP_PADDING_RATIO = 0.04f
}

data class ImageBlob(
    val bytes: ByteArray,
    val mimeType: String?,
    val contentHash: String,
    val byteMagic: String = bytes.magicHex(),
    val decodedWidth: Int? = null,
    val decodedHeight: Int? = null,
    val sourceMimeType: String? = null,
    val sourceByteMagic: String? = null,
    val sourceByteSize: Long? = null,
) {
    val byteSize: Long = bytes.size.toLong()
}

data class ImageMetrics(
    val width: Int,
    val height: Int,
    val byteSize: Long? = null,
) {
    val aspectRatio: Float = width.toFloat() / height.toFloat()
}

data class BoundedThumbnail(
    val uri: Uri,
    val source: ImageMetrics,
    val thumbnail: ImageMetrics?,
    val maxEdgePx: Int,
    val cacheHit: Boolean,
)

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

fun ByteArray.magicHex(maxBytes: Int = 12): String =
    take(maxBytes.coerceAtLeast(0)).joinToString(separator = "") { byte -> "%02x".format(byte) }

data class PaletteColorDiagnostics(
    val width: Int? = null,
    val height: Int? = null,
    val sampleStep: Int? = null,
    val sampleGridEstimate: Int? = null,
    val paletteSize: Int,
    val matches: List<PaletteColorMatch> = emptyList(),
)
