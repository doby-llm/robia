package com.gusanitolabs.robia.media

/**
 * Explicit source-of-truth for the add/edit photo pipeline.
 *
 * The raw camera/gallery URI is retained only for retries and diagnostics. The
 * editor, preview, save, palette extraction, and additional-info inference should
 * use [currentEditedUri], which is advanced from the background-removed/cropped
 * foreground artifact after each successful processing step.
 */
data class EditorPhotoState(
    val rawSourceUri: String? = null,
    val foregroundUri: String? = null,
    val croppedForegroundUri: String? = null,
    val currentEditedUri: String? = null,
    val additionalInfoSourceUri: String? = null,
    val backgroundRemovalStatus: EditorBackgroundRemovalStatus = EditorBackgroundRemovalStatus.None,
    val processingEventId: Long? = null,
    val processingToken: Int = 0,
) {
    val canonicalEditorUri: String?
        get() = currentEditedUri ?: croppedForegroundUri ?: foregroundUri ?: rawSourceUri

    val quickEditComparisonBaseUri: String?
        get() = currentEditedUri ?: croppedForegroundUri ?: foregroundUri ?: rawSourceUri

    val canonicalAdditionalInfoUri: String?
        get() = additionalInfoSourceUri ?: canonicalEditorUri

    val usesOriginalPhotoFallback: Boolean
        get() = backgroundRemovalStatus == EditorBackgroundRemovalStatus.OriginalFallback
}

enum class EditorBackgroundRemovalStatus {
    None,
    Queued,
    RemovingBackground,
    ForegroundReady,
    CroppedForegroundReady,
    OriginalFallback,
    QuickEdited,
}
