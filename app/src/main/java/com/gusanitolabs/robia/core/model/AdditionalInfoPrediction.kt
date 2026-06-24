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

data class AdditionalInfoTensorStats(
    val min: Float,
    val max: Float,
    val mean: Float,
    val standardDeviation: Float,
    val channelMeans: List<Float>,
    val channelMins: List<Float>,
    val channelMaxs: List<Float>,
    val nonFiniteCount: Int,
    val checksum: String,
)

data class AdditionalInfoDetectionDebug(
    val sourceUri: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val modelVersion: String,
    val modelFile: String,
    val inputShape: List<Int>,
    val normalizationType: String,
    val preprocessing: String,
    val tensorStats: AdditionalInfoTensorStats,
    val outputShapes: Map<String, List<Int>> = emptyMap(),
)

data class AdditionalInfoDetectionResult(
    val prediction: AdditionalInfoPrediction? = null,
    val failureReason: FailureReason? = null,
    val debug: AdditionalInfoDetectionDebug? = null,
) {
    enum class FailureReason {
        ModelUnavailable,
        ManifestInvalid,
        PreprocessingFailed,
        OutputShapeMismatch,
        MappingFailed,
    }
}
