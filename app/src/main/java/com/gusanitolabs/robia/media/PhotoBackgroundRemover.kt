package com.gusanitolabs.robia.media

import android.content.Context
import android.net.Uri
import com.gusanitolabs.robia.media.cutout.CutoutEngineRegistry
import com.gusanitolabs.robia.media.cutout.cutoutFallbackChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val PRECISE_TIMEOUT = 30.seconds

/**
 * Coordinates background removal while preserving original-photo fallback semantics.
 * Order: the precise engine when it is active (SG2a), then ML Kit, then the original photo.
 */
class PhotoBackgroundRemover(
    private val engine: BackgroundRemovalEngine = MlKitSubjectSegmentationBackgroundRemovalEngine(),
) {
    suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult =
        cutoutFallbackChain(
            precise = { preciseResult(context, sourceUri) },
            mlKit = { mlKitResult(context, sourceUri) },
        )

    /** Null when the precise engine is off, unavailable, timed out, or did not remove the background. */
    private suspend fun preciseResult(context: Context, sourceUri: Uri): BackgroundRemovalResult? {
        if (!CutoutEngineRegistry.isPreciseActive()) return null
        val preciseEngine = CutoutEngineRegistry.precise?.invoke() ?: return null
        return withTimeoutOrNull(PRECISE_TIMEOUT) { preciseEngine.removeBackground(context, sourceUri) }
            ?.takeIf { it.status == BackgroundRemovalStatus.REMOVED }
    }

    private suspend fun mlKitResult(context: Context, sourceUri: Uri): BackgroundRemovalResult =
        try {
            engine.removeBackground(context, sourceUri)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
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
