package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Photo record of the last snapshot this phone uploaded; lets the next cycle skip photos that did not change. */
data class LastUploadedPhoto(
    val garmentId: String,
    val sourceHash: String?,
    val blobPath: String,
    val contentHash: String?,
    val byteSize: Long?,
    val mimeType: String?,
)

internal fun GarmentPhotoRecord.toLastUploadedPhoto(): LastUploadedPhoto = LastUploadedPhoto(
    garmentId = garmentId,
    sourceHash = sourceHash,
    blobPath = blobPath,
    contentHash = contentHash,
    byteSize = byteSize,
    mimeType = mimeType,
)

/** Backs `filesDir/sync/last_uploaded_photos.json`. A missing or unreadable file reads as empty. */
class LastUploadedPhotosStore(private val file: File) {
    fun read(): Map<String, LastUploadedPhoto> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { index -> array.getJSONObject(index).toLastUploadedPhoto() }
    }.getOrDefault(emptyList()).associateBy(LastUploadedPhoto::garmentId)

    /** Writes a temp file and renames it over the target, so a crash never leaves a half-written file. */
    fun write(photos: List<LastUploadedPhoto>) {
        file.parentFile?.mkdirs()
        val temp = File("${file.path}.tmp")
        temp.writeText(JSONArray(photos.map(LastUploadedPhoto::toJson)).toString())
        check(temp.renameTo(file)) { "Unable to replace ${file.name}." }
    }
}

private fun LastUploadedPhoto.toJson(): JSONObject = JSONObject()
    .put("garmentId", garmentId)
    .put("sourceHash", sourceHash)
    .put("blobPath", blobPath)
    .put("contentHash", contentHash)
    .put("byteSize", byteSize)
    .put("mimeType", mimeType)

private fun JSONObject.toLastUploadedPhoto(): LastUploadedPhoto = LastUploadedPhoto(
    garmentId = getString("garmentId"),
    sourceHash = nullableString("sourceHash"),
    blobPath = getString("blobPath"),
    contentHash = nullableString("contentHash"),
    byteSize = if (isNull("byteSize")) null else getLong("byteSize"),
    mimeType = nullableString("mimeType"),
)

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
