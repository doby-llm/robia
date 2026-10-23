package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider

/** Keeps the original of a processed photo: the frame the cutout engine saw, next to the cutouts (REQ-OR-01). */
object OriginalPhotoStore {
    private const val JPEG_QUALITY = 90

    /** Writes [bitmap] as `original-<uuid>.jpg` in the image folder. The caller keeps ownership of [bitmap]. */
    fun save(context: Context, bitmap: Bitmap): Uri {
        val imageFile = ClothingImageStore.createImageFile(context, "original", extension = "jpg")
        imageFile.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) { "Unable to encode original photo" }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
    }

    /**
     * [save] for the frame of a processed photo, which may be missing or fail to write: null then, so the cutout is kept
     * without an original. Always recycles [frame], which the caller hands over.
     */
    fun saveAndRecycle(context: Context, frame: Bitmap?): Uri? {
        if (frame == null) return null
        return try {
            runCatching { save(context, frame) }.getOrNull()
        } finally {
            frame.recycle()
        }
    }
}
