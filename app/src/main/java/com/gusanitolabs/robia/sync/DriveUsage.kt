package com.gusanitolabs.robia.sync

private const val PHOTO_NAME_PREFIX = "photos/"
private const val ORIGINAL_NAME_PREFIX = "originals/"

/** Space Robia uses in the user's Google Drive app data folder, measured from one listing. */
data class DriveUsage(
    val totalBytes: Long,
    val photoCount: Int,
    val photoBytes: Long,
    val backupCount: Int,
    val measuredAtEpochMillis: Long,
    val originalCount: Int = 0,
    val originalBytes: Long = 0L,
)

/**
 * Photos are the blobs named `photos/...`, originals the blobs named `originals/...` (REQ-OR-04, counted apart);
 * every other file in the folder is backup data (the wardrobe snapshot).
 * A file Drive reports without a size counts as 0 bytes.
 */
fun driveUsageOf(files: List<DriveFileMetadata>, now: Long): DriveUsage {
    val (photos, others) = files.partition { file -> file.name.startsWith(PHOTO_NAME_PREFIX) }
    val (originals, backups) = others.partition { file -> file.name.startsWith(ORIGINAL_NAME_PREFIX) }
    val photoBytes = photos.sumOf { file -> file.size ?: 0L }
    val originalBytes = originals.sumOf { file -> file.size ?: 0L }
    val backupBytes = backups.sumOf { file -> file.size ?: 0L }
    return DriveUsage(
        totalBytes = photoBytes + originalBytes + backupBytes,
        photoCount = photos.size,
        photoBytes = photoBytes,
        backupCount = backups.size,
        measuredAtEpochMillis = now,
        originalCount = originals.size,
        originalBytes = originalBytes,
    )
}
