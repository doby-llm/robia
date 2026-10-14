package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.media.ImageBlob
import com.gusanitolabs.robia.sync.PhotoUploadPipeline

/**
 * Phone-side photo files for upload tests: [sources] maps a local URI to the file's content.
 * The "WebP" encoder prefixes the content, so encoded bytes differ from the source bytes as in production.
 */
internal class FakePhotoSources {
    val sources = mutableMapOf<String, String>()

    fun pipeline(api: FakeDriveSnapshotApi, onPurgeDeferred: (String) -> Unit = {}): PhotoUploadPipeline = PhotoUploadPipeline(
        api = api,
        sourceHashOf = { photo -> sha256Hex(sources.getValue(photo.localUri).toByteArray()).take(16) },
        encode = { photo ->
            val webp = "webp:${sources.getValue(photo.localUri)}".toByteArray()
            ImageBlob(bytes = webp, mimeType = "image/webp", contentHash = sha256Hex(webp), decodedWidth = 2, decodedHeight = 2)
        },
        onPurgeDeferred = onPurgeDeferred,
    )
}

internal fun localPhoto(garmentId: String): GarmentPhotoRecord = GarmentPhotoRecord(
    garmentId = garmentId,
    localUri = "content://photos/$garmentId",
    blobPath = "photos/$garmentId/original",
)

internal fun sha256Hex(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
