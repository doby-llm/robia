package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import java.io.Closeable
import java.nio.ByteOrder

fun createInteractiveGarmentSegmenter(context: Context): InteractiveGarmentSegmenter =
    if (MediaPipeInteractiveGarmentSegmenter.isModelAssetBundled(context)) {
        MediaPipeInteractiveGarmentSegmenter()
    } else {
        UnavailableInteractiveGarmentSegmenter()
    }

/**
 * MediaPipe Tasks Vision adapter for the Quick Edit segment eraser.
 *
 * The UI depends only on [InteractiveGarmentSegmenter]. This adapter stays inert
 * until an approved model asset is bundled at [modelAssetPath], so the rest of
 * Quick Edit continues to work when MediaPipe/model initialization is unavailable.
 */
class MediaPipeInteractiveGarmentSegmenter(
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
) : InteractiveGarmentSegmenter, Closeable {
    private val lock = Any()
    private var cachedSegmenter: InteractiveSegmenter? = null
    private var initializationFailed = false

    override val isAvailable: Boolean = true

    override suspend fun highlightSegment(
        context: Context,
        imageUri: Uri,
        point: NormalizedImagePoint,
    ): InteractiveSegmentResult {
        val segmenter = getOrCreateSegmenter(context) ?: return InteractiveSegmentResult(point)
        val sourceBitmap = context.contentResolver.openInputStream(imageUri)?.use(BitmapFactory::decodeStream)
            ?: return InteractiveSegmentResult(point)

        val mpImage = BitmapImageBuilder(sourceBitmap).build()
        return try {
            val result = segmenter.segment(
                mpImage,
                InteractiveSegmenter.RegionOfInterest.create(
                    NormalizedKeypoint.create(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f)),
                ),
            )
            InteractiveSegmentResult(point = point, mask = result.toInteractiveMask())
        } finally {
            mpImage.close()
            sourceBitmap.recycle()
        }
    }

    override suspend fun eraseSegment(
        context: Context,
        imageUri: Uri,
        segment: InteractiveSegmentResult,
    ): Uri = QuickEditImageProcessor.eraseSegmentMask(context, imageUri, segment)

    override fun close() {
        synchronized(lock) {
            cachedSegmenter?.close()
            cachedSegmenter = null
        }
    }

    private fun getOrCreateSegmenter(context: Context): InteractiveSegmenter? = synchronized(lock) {
        cachedSegmenter?.let { return@synchronized it }
        if (initializationFailed || !context.assetExists(modelAssetPath)) return@synchronized null
        runCatching {
            InteractiveSegmenter.createFromOptions(
                context.applicationContext,
                InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
                    .setOutputConfidenceMasks(true)
                    .setOutputCategoryMask(false)
                    .build(),
            )
        }.onFailure {
            initializationFailed = true
        }.getOrNull()?.also { created ->
            cachedSegmenter = created
        }
    }

    private fun ImageSegmenterResult.toInteractiveMask(): InteractiveSegmentMask? {
        val confidenceMask = confidenceMasks().orElse(null)?.firstOrNull()
        if (confidenceMask != null) return confidenceMask.toMaskFromConfidence()
        return categoryMask().orElse(null)?.toMaskFromBitmap()
    }

    private fun MPImage.toMaskFromConfidence(): InteractiveSegmentMask? {
        val width = getWidth().takeIf { it > 0 } ?: return null
        val height = getHeight().takeIf { it > 0 } ?: return null
        val buffer = ByteBufferExtractor.extract(this).duplicate().order(ByteOrder.nativeOrder())
        val alpha = ByteArray(width * height)
        return runCatching {
            when {
                buffer.remaining() >= width * height * FLOAT_BYTES -> {
                    for (index in alpha.indices) {
                        val confidence = buffer.float.coerceIn(0f, 1f)
                        alpha[index] = (confidence * 255f).toInt().coerceIn(0, 255).toByte()
                    }
                }
                buffer.remaining() >= width * height -> {
                    for (index in alpha.indices) {
                        alpha[index] = buffer.get()
                    }
                }
                else -> return null
            }
            InteractiveSegmentMask(width = width, height = height, alpha = alpha)
        }.getOrNull()
    }

    private fun MPImage.toMaskFromBitmap(): InteractiveSegmentMask? = runCatching {
        val bitmap = BitmapExtractor.extract(this)
        try {
            val alpha = ByteArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    alpha[y * bitmap.width + x] = bitmap.pixelAlphaAt(x, y).toByte()
                }
            }
            InteractiveSegmentMask(width = bitmap.width, height = bitmap.height, alpha = alpha)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    private fun Context.assetExists(path: String): Boolean =
        runCatching { assets.open(path).use { true } }.getOrDefault(false)

    private fun Bitmap.pixelAlphaAt(x: Int, y: Int): Int {
        val pixel = getPixel(x, y)
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha != 0) return alpha
        return pixel and 0xFF
    }

    companion object {
        fun isModelAssetBundled(context: Context, modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH): Boolean =
            runCatching { context.assets.open(modelAssetPath).use { true } }.getOrDefault(false)

        const val DEFAULT_MODEL_ASSET_PATH = "mediapipe/magic_touch.tflite"
        private const val FLOAT_BYTES = 4
    }
}
