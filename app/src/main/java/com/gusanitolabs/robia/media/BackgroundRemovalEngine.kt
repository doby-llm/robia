package com.gusanitolabs.robia.media

import android.content.Context
import android.net.Uri

/**
 * Port for best-effort photo background removal.
 *
 * Callers must treat every response as saveable: even failures return the original URI so
 * Add/Edit can continue without making segmentation a required step.
 */
interface BackgroundRemovalEngine {
    suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult
}

data class BackgroundRemovalResult(
    val originalUri: Uri,
    val outputUri: Uri,
    val status: BackgroundRemovalStatus,
    val failure: BackgroundRemovalFailure? = null,
) {
    val usedFallback: Boolean = outputUri == originalUri || status == BackgroundRemovalStatus.ORIGINAL_FALLBACK
}

enum class BackgroundRemovalStatus {
    REMOVED,
    ORIGINAL_FALLBACK,
}

data class BackgroundRemovalFailure(
    val reason: BackgroundRemovalFailureReason,
    val message: String? = null,
    val causeClass: String? = null,
)

enum class BackgroundRemovalFailureReason {
    PLAY_SERVICES_UNAVAILABLE,
    SOURCE_DECODE_FAILED,
    MODEL_UNAVAILABLE,
    SEGMENTATION_FAILED,
    OUTPUT_WRITE_FAILED,
}
