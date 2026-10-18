package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot

/** A garment photo on this phone. [fileLength] is null when its local file is missing or cannot be read. */
data class LocalPhotoRef(
    val garmentId: String,
    val fileLength: Long?,
)

enum class PhotoIntegrityStatus { Ok, MissingLocalFile, MissingOnDrive, SizeMismatch, NotYetUploaded }

data class PhotoIntegrityEntry(
    val garmentId: String,
    val status: PhotoIntegrityStatus,
    /** The garment's name from the snapshot, for the result list; empty when the snapshot does not carry it. */
    val garmentName: String = "",
)

/** Per-garment statuses plus the number of photo blobs in Drive that no snapshot record refers to. */
data class PhotoIntegrityReport(
    val entries: List<PhotoIntegrityEntry> = emptyList(),
    val orphanBlobCount: Int = 0,
) {
    val okCount: Int
        get() = entries.count { entry -> entry.status == PhotoIntegrityStatus.Ok }

    val problems: List<PhotoIntegrityEntry>
        get() = entries.filter { entry -> entry.status != PhotoIntegrityStatus.Ok }
}

private const val PHOTO_BLOB_PREFIX = "photos/"

/**
 * Read-only comparison of the phone's photos with the Drive listing and the remote snapshot. Pure: no Drive or Android calls.
 * Drive reports a size but no checksum in a listing, so [PhotoIntegrityStatus.SizeMismatch] is the only damage it can see.
 * Nothing is deleted or rewritten; orphan blobs are counted, never removed.
 */
fun checkPhotoIntegrity(
    local: List<LocalPhotoRef>,
    files: List<DriveFileMetadata>,
    snapshot: WardrobeSyncSnapshot?,
): PhotoIntegrityReport {
    val filesByName = files.filter { file -> file.name.isNotEmpty() }.associateBy(DriveFileMetadata::name)
    val localByGarmentId = local.associateBy(LocalPhotoRef::garmentId)
    val recordByGarmentId = snapshot?.photos.orEmpty().associateBy(GarmentPhotoRecord::garmentId)
    val namesByGarmentId = snapshot?.garments.orEmpty().associate { garment -> garment.id to garment.name }
    val entries = (localByGarmentId.keys + recordByGarmentId.keys).sorted().map { garmentId ->
        PhotoIntegrityEntry(
            garmentId = garmentId,
            garmentName = namesByGarmentId[garmentId].orEmpty(),
            status = statusOf(
                localLength = localByGarmentId[garmentId]?.fileLength,
                record = recordByGarmentId[garmentId],
                filesByName = filesByName,
            ),
        )
    }
    val referencedBlobPaths = recordByGarmentId.values.mapNotNull { record -> record.blobPath.takeIf(String::isNotBlank) }.toSet()
    val orphanBlobCount = files.count { file -> file.name.startsWith(PHOTO_BLOB_PREFIX) && file.name !in referencedBlobPaths }
    return PhotoIntegrityReport(entries = entries, orphanBlobCount = orphanBlobCount)
}

private fun statusOf(
    localLength: Long?,
    record: GarmentPhotoRecord?,
    filesByName: Map<String, DriveFileMetadata>,
): PhotoIntegrityStatus {
    val localPresent = localLength != null
    // No record, or a record whose blob is not in Drive: the phone can back the photo up only if it still holds the file.
    val missingRemote = if (localPresent) PhotoIntegrityStatus.NotYetUploaded else PhotoIntegrityStatus.MissingOnDrive
    val photoRecord = record ?: return missingRemote
    val remote = photoRecord.blobPath.takeIf(String::isNotBlank)?.let(filesByName::get) ?: return missingRemote
    val expectedSize = photoRecord.byteSize
    return when {
        expectedSize != null && remote.size != null && remote.size != expectedSize -> PhotoIntegrityStatus.SizeMismatch
        !localPresent -> PhotoIntegrityStatus.MissingLocalFile
        else -> PhotoIntegrityStatus.Ok
    }
}
