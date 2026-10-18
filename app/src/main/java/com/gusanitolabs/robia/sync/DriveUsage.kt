package com.gusanitolabs.robia.sync

private const val PHOTO_NAME_PREFIX = "photos/"

/** Space Robia uses in the user's Google Drive app data folder, measured from one listing. */
data class DriveUsage(
    val totalBytes: Long,
    val photoCount: Int,
    val photoBytes: Long,
    val backupCount: Int,
    val measuredAtEpochMillis: Long,
)

/**
 * Photos are the blobs named `photos/...`; every other file in the folder is backup data (the wardrobe snapshot).
 * A file Drive reports without a size counts as 0 bytes.
 */
fun driveUsageOf(files: List<DriveFileMetadata>, now: Long): DriveUsage {
    val (photos, backups) = files.partition { file -> file.name.startsWith(PHOTO_NAME_PREFIX) }
    val photoBytes = photos.sumOf { file -> file.size ?: 0L }
    val backupBytes = backups.sumOf { file -> file.size ?: 0L }
    return DriveUsage(
        totalBytes = photoBytes + backupBytes,
        photoCount = photos.size,
        photoBytes = photoBytes,
        backupCount = backups.size,
        measuredAtEpochMillis = now,
    )
}
