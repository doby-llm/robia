package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.gusanitolabs.robia.core.model.CutoutRecipe

/**
 * Port for best-effort photo background removal.
 *
 * Failures keep the original URI available so users do not lose the photo, but callers must
 * treat [BackgroundRemovalStatus.ORIGINAL_FALLBACK] as a reviewable warning state instead of a
 * normal processed success.
 */
interface BackgroundRemovalEngine {
    suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult
}

data class BackgroundRemovalResult(
    val originalUri: Uri,
    val outputUri: Uri,
    val status: BackgroundRemovalStatus,
    val failure: BackgroundRemovalFailure? = null,
    /** Per-stage milliseconds for engines that report them (the precise cutout pipeline); empty otherwise. */
    val timings: Map<String, Long> = emptyMap(),
    /**
     * The exact frame the engine processed, before any mask (EXIF applied, within the engine's work size). Set only with
     * [BackgroundRemovalStatus.REMOVED]. The caller owns it: it saves the original and recycles the frame. Null on fallback.
     */
    val inputFrame: Bitmap? = null,
    /** How to reproduce [outputUri] from [inputFrame] (REQ-OR-02). Null whenever [inputFrame] is null. */
    val recipe: CutoutRecipe? = null,
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
