package com.gusanitolabs.robia.media

import android.content.Context
import android.net.Uri

/** Coordinates background removal while preserving original-photo fallback semantics. */
class PhotoBackgroundRemover(
    private val engine: BackgroundRemovalEngine = MlKitSubjectSegmentationBackgroundRemovalEngine(),
) {
    suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult =
        runCatching { engine.removeBackground(context, sourceUri) }
            .getOrElse { throwable ->
                sourceUri.originalFallback(
                    BackgroundRemovalFailure(
                        reason = BackgroundRemovalFailureReason.SEGMENTATION_FAILED,
                        message = throwable.message,
                        causeClass = throwable::class.java.name,
                    ),
                )
            }
}

fun Uri.originalFallback(failure: BackgroundRemovalFailure): BackgroundRemovalResult = BackgroundRemovalResult(
    originalUri = this,
    outputUri = this,
    status = BackgroundRemovalStatus.ORIGINAL_FALLBACK,
    failure = failure,
)
