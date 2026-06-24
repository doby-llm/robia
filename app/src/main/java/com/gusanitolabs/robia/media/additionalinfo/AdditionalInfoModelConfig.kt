package com.gusanitolabs.robia.media.additionalinfo

import android.content.Context

object AdditionalInfoModelConfigLoader {
    fun load(context: Context): AdditionalInfoModelConfig = context.assets
        .open(AdditionalInfoModelAssets.MANIFEST_FILE)
        .bufferedReader()
        .use { reader -> AdditionalInfoModelManifest.parse(reader.readText()) }

    fun validate(config: AdditionalInfoModelConfig): Boolean = AdditionalInfoModelManifest.validate(config)
}
