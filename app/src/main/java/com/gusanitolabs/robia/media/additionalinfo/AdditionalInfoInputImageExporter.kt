package com.gusanitolabs.robia.media.additionalinfo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdditionalInfoInputImageExporter {
    data class ExportResult(
        val uri: Uri,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val inputWidth: Int,
        val inputHeight: Int,
        val preprocessing: String,
    )

    fun exportToGallery(
        context: Context,
        sourceUri: Uri,
    ): ExportResult? {
        val config = AdditionalInfoModelConfigLoader.load(context)
        if (!AdditionalInfoModelConfigLoader.validate(config)) return null

        val exactInput = AdditionalInfoImagePreprocessor.createExactInputBitmap(context, sourceUri, config.input)
            ?: return null
        return try {
            val outputUri = createPendingImage(context) ?: return null
            var completed = false
            try {
                context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    check(exactInput.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                        "Unable to encode exact additional-info input image"
                    }
                } ?: error("Unable to open exact additional-info input image destination")
                completed = true
                ExportResult(
                    uri = outputUri,
                    sourceWidth = exactInput.sourceWidth,
                    sourceHeight = exactInput.sourceHeight,
                    inputWidth = exactInput.bitmap.width,
                    inputHeight = exactInput.bitmap.height,
                    preprocessing = exactInput.preprocessing,
                )
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, if (completed) 0 else 1)
                    }
                    context.contentResolver.update(outputUri, values, null, null)
                }
                if (!completed) {
                    context.contentResolver.delete(outputUri, null, null)
                }
            }
        } finally {
            exactInput.bitmap.recycle()
        }
    }

    private fun createPendingImage(context: Context): Uri? {
        val fileName = "robia-additional-info-input-${timestamp()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Robia")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return context.contentResolver.insert(collection, values)
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
