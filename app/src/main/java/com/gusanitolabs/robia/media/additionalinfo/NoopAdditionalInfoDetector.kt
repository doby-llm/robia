package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoDetectionResult
import com.gusanitolabs.robia.core.model.GarmentTag

object NoopAdditionalInfoDetector : AdditionalInfoDetector {
    override suspend fun detect(
        context: Context,
        imageUri: Uri,
        availableTags: List<GarmentTag>,
    ): AdditionalInfoDetectionResult = AdditionalInfoDetectionResult(
        failureReason = AdditionalInfoDetectionResult.FailureReason.ModelUnavailable,
    )
}
