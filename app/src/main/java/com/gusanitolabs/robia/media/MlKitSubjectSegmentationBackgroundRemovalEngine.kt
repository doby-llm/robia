package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private const val SEGMENTATION_MAX_EDGE_PX = 2048

/** ML Kit-backed best-effort foreground extraction. Failures always fall back to the original URI. */
class MlKitSubjectSegmentationBackgroundRemovalEngine : BackgroundRemovalEngine {
    override suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult = withContext(Dispatchers.IO) {
        if (!context.hasGooglePlayServices()) {
            return@withContext sourceUri.originalFallback(
                BackgroundRemovalFailure(BackgroundRemovalFailureReason.PLAY_SERVICES_UNAVAILABLE),
            )
        }

        val source = runCatching { decodeForSegmentation(context, sourceUri) }
            .getOrElse { throwable ->
                return@withContext sourceUri.originalFallback(
                    failure = throwable.toBackgroundRemovalFailure(BackgroundRemovalFailureReason.SOURCE_DECODE_FAILED),
                )
            }
        val gate = SegmentationModuleGates.get(context)
        val segmenter = SubjectSegmentation.getClient(subjectSegmenterOptions)

        try {
            val result = runCatching {
                runWithModuleRetry(
                    ensureModule = { gate.ensureAvailable(timeout = MODULE_WAIT_TIMEOUT) },
                    process = { segmenter.process(InputImage.fromBitmap(source, 0)).await() },
                )
            }.getOrElse { throwable ->
                return@withContext sourceUri.originalFallback(throwable.toSegmentationFailure())
            }

            val alpha = runCatching { result.refinedAlpha(source) }
                .getOrElse { throwable ->
                    return@withContext sourceUri.originalFallback(throwable.toSegmentationFailure())
                }
                ?: return@withContext sourceUri.originalFallback(
                    BackgroundRemovalFailure(
                        reason = BackgroundRemovalFailureReason.MODEL_UNAVAILABLE,
                        message = "ML Kit did not return a foreground mask.",
                    ),
                )

            val outputUri = runCatching {
                source.composeWithAlpha(alpha).useForOutput { bitmap ->
                    ClothingImageStore.writeProcessedBitmap(context, bitmap)
                }
            }.getOrElse { throwable ->
                return@withContext sourceUri.originalFallback(
                    throwable.toBackgroundRemovalFailure(BackgroundRemovalFailureReason.OUTPUT_WRITE_FAILED),
                )
            }

            BackgroundRemovalResult(
                originalUri = sourceUri,
                outputUri = outputUri,
                status = BackgroundRemovalStatus.REMOVED,
            )
        } finally {
            segmenter.close()
            source.recycle()
        }
    }

    private fun Context.hasGooglePlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

    private fun Throwable.toSegmentationFailure(): BackgroundRemovalFailure = toBackgroundRemovalFailure(
        reason = segmentationFailureReason(this),
    )

    private fun Throwable.toBackgroundRemovalFailure(reason: BackgroundRemovalFailureReason): BackgroundRemovalFailure =
        BackgroundRemovalFailure(
            reason = reason,
            message = message,
            causeClass = this::class.java.name,
        )

    private inline fun <T> Bitmap.useForOutput(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }
}

private val MODULE_WAIT_TIMEOUT = 60.seconds

internal fun segmentationFailureReason(throwable: Throwable): BackgroundRemovalFailureReason =
    if (throwable is MlKitException && throwable.errorCode == MlKitException.UNAVAILABLE) {
        BackgroundRemovalFailureReason.MODEL_UNAVAILABLE
    } else {
        BackgroundRemovalFailureReason.SEGMENTATION_FAILED
    }

/**
 * Waits for the module, runs [process], and on UNAVAILABLE waits once more and runs it once more.
 * A second UNAVAILABLE (or any other error) propagates to the caller's fallback.
 */
internal suspend fun <T> runWithModuleRetry(
    ensureModule: suspend () -> Unit,
    process: suspend () -> T,
): T {
    ensureModule()
    return try {
        process()
    } catch (throwable: MlKitException) {
        if (throwable.errorCode != MlKitException.UNAVAILABLE) throw throwable
        ensureModule()
        process()
    }
}

/** Decodes the photo with EXIF orientation applied and its long edge at most [SEGMENTATION_MAX_EDGE_PX]. */
private fun decodeForSegmentation(context: Context, sourceUri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: error("Unable to open source photo")
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Source photo has no readable size" }

    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = segmentationSampleSize(bounds.outWidth, bounds.outHeight)
    }
    val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: error("Unable to decode source photo")
    val orientation = context.contentResolver.openInputStream(sourceUri)?.use { input ->
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = orientationMatrix(orientation) ?: return decoded

    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
        decoded.recycle()
    }
}

private fun segmentationSampleSize(width: Int, height: Int): Int {
    val maxDimension = maxOf(width, height).coerceAtLeast(1)
    var sampleSize = 1
    while (maxDimension / sampleSize > SEGMENTATION_MAX_EDGE_PX) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun orientationMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return null
    }
    return matrix
}

/** Refined per-pixel alpha for [source], from the confidence mask or, failing that, the foreground bitmap's alpha. */
private fun SubjectSegmentationResult.refinedAlpha(source: Bitmap): ByteArray? {
    val width = source.width
    val height = source.height
    val count = width * height
    val confidence = foregroundConfidenceMask?.takeIf { it.remaining() == count }?.toFloatArray(count)
        ?: foregroundBitmap?.let { foreground ->
            check(foreground.width == width && foreground.height == height) {
                "Foreground bitmap ${foreground.width}x${foreground.height} does not match input ${width}x$height"
            }
            val pixels = IntArray(count)
            foreground.getPixels(pixels, 0, width, 0, 0, width, height)
            FloatArray(count) { i -> (pixels[i] ushr 24) / 255f }
        }
        ?: return null

    val argb = IntArray(count)
    source.getPixels(argb, 0, width, 0, 0, width, height)
    return AlphaRefiner.refine(argb, confidence, width, height)
}

private fun FloatBuffer.toFloatArray(count: Int): FloatArray = FloatArray(count).also { array ->
    duplicate().get(array)
}

/** Original RGB with [alpha] as the alpha channel; the output is a new bitmap, [source] is untouched. */
private fun Bitmap.composeWithAlpha(alpha: ByteArray): Bitmap {
    val argb = IntArray(width * height)
    getPixels(argb, 0, width, 0, 0, width, height)
    for (i in argb.indices) {
        argb[i] = ((alpha[i].toInt() and 0xFF) shl 24) or (argb[i] and 0xFFFFFF)
    }
    return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { throwable -> continuation.resumeWithException(throwable) }
    addOnCanceledListener { continuation.cancel() }
}
