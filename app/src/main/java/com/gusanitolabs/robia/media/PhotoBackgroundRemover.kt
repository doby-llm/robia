package com.gusanitolabs.robia.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gusanitolabs.robia.media.cutout.CUTOUT_LOG_TAG
import com.gusanitolabs.robia.media.cutout.CutoutEngineRegistry
import com.gusanitolabs.robia.media.cutout.FALLBACK_NOT_ACTIVE
import com.gusanitolabs.robia.media.cutout.FALLBACK_TRIPPED
import com.gusanitolabs.robia.media.cutout.cutoutFallbackChain
import kotlinx.coroutines.CancellationException

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
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
            skipReason = { preciseSkipReason() },
            onOutcome = { Log.i(CUTOUT_LOG_TAG, "cutout_result engine=${it.engine} fallback=${it.fallback} ms=${it.ms}") },
        )

    /** A tripped latch is reported as tripped; the engine being off or the pack missing counts as not_active. */
    private fun preciseSkipReason(): String =
        if (CutoutEngineRegistry.isPreciseActive() && CutoutEngineRegistry.preciseLatch.isTripped) FALLBACK_TRIPPED else FALLBACK_NOT_ACTIVE

    /**
     * Null when the precise engine is off or unavailable (a tripped [CutoutEngineRegistry.preciseLatch] counts as unavailable);
     * the chain applies the timeout and the REMOVED check.
     */
    private suspend fun preciseResult(context: Context, sourceUri: Uri): BackgroundRemovalResult? {
        if (!CutoutEngineRegistry.isPreciseActive()) return null
        if (CutoutEngineRegistry.preciseLatch.isTripped) return null
        val preciseEngine = CutoutEngineRegistry.precise?.invoke() ?: return null
        return preciseEngine.removeBackground(context, sourceUri)
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
