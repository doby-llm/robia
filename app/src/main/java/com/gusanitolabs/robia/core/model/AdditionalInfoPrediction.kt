package com.gusanitolabs.robia.core.model

/** Raw score for one classifier label after a softmax output head. */
data class AdditionalInfoLabelScore(
    val label: String,
    val tagId: String?,
    val score: Float,
)

/** Best-effort tag selections produced by the additional-info classifier. */
data class AdditionalInfoPrediction(
    val selectedTagIds: Set<String>,
    val categoryScores: List<AdditionalInfoLabelScore>,
    val seasonScores: List<AdditionalInfoLabelScore>,
    val occasionScores: List<AdditionalInfoLabelScore>,
)

data class AdditionalInfoDetectionResult(
    val prediction: AdditionalInfoPrediction? = null,
    val failureReason: FailureReason? = null,
) {
    enum class FailureReason {
        ModelUnavailable,
        ManifestInvalid,
        PreprocessingFailed,
        OutputShapeMismatch,
        MappingFailed,
    }
}
