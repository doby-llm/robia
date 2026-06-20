package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ML Kit-backed best-effort foreground extraction. Failures always fall back to the original URI. */
class MlKitSubjectSegmentationBackgroundRemovalEngine : BackgroundRemovalEngine {
    override suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult = withContext(Dispatchers.IO) {
        if (!context.hasGooglePlayServices()) {
            return@withContext sourceUri.originalFallback(
                BackgroundRemovalFailure(BackgroundRemovalFailureReason.PLAY_SERVICES_UNAVAILABLE),
            )
        }

        val inputImage = runCatching { InputImage.fromFilePath(context, sourceUri) }
            .getOrElse { throwable ->
                return@withContext sourceUri.originalFallback(
                    failure = throwable.toBackgroundRemovalFailure(BackgroundRemovalFailureReason.SOURCE_DECODE_FAILED),
                )
            }

        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build(),
        )

        try {
            val result = runCatching { segmenter.process(inputImage).await() }
                .getOrElse { throwable ->
                    return@withContext sourceUri.originalFallback(throwable.toSegmentationFailure())
                }

            val foreground = result.foregroundBitmap
                ?: return@withContext sourceUri.originalFallback(
                    BackgroundRemovalFailure(
                        reason = BackgroundRemovalFailureReason.MODEL_UNAVAILABLE,
                        message = "ML Kit did not return a foreground bitmap.",
                    ),
                )

            val outputUri = foreground.useForOutput { bitmap ->
                runCatching { ClothingImageStore.writeProcessedBitmap(context, bitmap) }
                    .getOrElse { throwable ->
                        return@withContext sourceUri.originalFallback(
                            throwable.toBackgroundRemovalFailure(BackgroundRemovalFailureReason.OUTPUT_WRITE_FAILED),
                        )
                    }
            }

            BackgroundRemovalResult(
                originalUri = sourceUri,
                outputUri = outputUri,
                status = BackgroundRemovalStatus.REMOVED,
            )
        } finally {
            segmenter.close()
        }
    }

    private fun Context.hasGooglePlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

    private fun Throwable.toSegmentationFailure(): BackgroundRemovalFailure = toBackgroundRemovalFailure(
        reason = if (this is MlKitException && errorCode == MlKitException.UNAVAILABLE) {
            BackgroundRemovalFailureReason.MODEL_UNAVAILABLE
        } else {
            BackgroundRemovalFailureReason.SEGMENTATION_FAILED
        },
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

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { throwable -> continuation.resumeWithException(throwable) }
    addOnCanceledListener { continuation.cancel() }
}
