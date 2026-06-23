package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context
import android.net.Uri
import com.gusanitolabs.robia.core.model.AdditionalInfoDetectionResult
import com.gusanitolabs.robia.core.model.GarmentTag

interface AdditionalInfoDetector {
    suspend fun detect(
        context: Context,
        imageUri: Uri,
        availableTags: List<GarmentTag>,
    ): AdditionalInfoDetectionResult
}
