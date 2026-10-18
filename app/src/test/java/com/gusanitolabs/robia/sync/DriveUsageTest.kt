package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveUsageTest {
    @Test
    fun sumsPhotosAndBackupBytes() {
        val usage = driveUsageOf(
            listOf(
                DriveFileMetadata(id = "1", name = "photos/garment-1/aaaaaaaaaaaaaaaa.webp", size = 100L),
                DriveFileMetadata(id = "2", name = "photos/garment-2/bbbbbbbbbbbbbbbb.webp", size = 250L),
                DriveFileMetadata(id = "3", name = "wardrobe_snapshot.json", size = 40L),
            ),
            now = 1_000L,
        )

        assertEquals(390L, usage.totalBytes)
        assertEquals(2, usage.photoCount)
        assertEquals(350L, usage.photoBytes)
        assertEquals(1, usage.backupCount)
        assertEquals(1_000L, usage.measuredAtEpochMillis)
    }

    @Test
    fun nullSizesCountAsZeroBytesButStillCountAsFiles() {
        val usage = driveUsageOf(
            listOf(
                DriveFileMetadata(id = "1", name = "photos/garment-1/aaaaaaaaaaaaaaaa.webp", size = null),
                DriveFileMetadata(id = "2", name = "wardrobe_snapshot.json", size = null),
                DriveFileMetadata(id = "3", name = "wardrobe_snapshot.json", size = 7L),
            ),
            now = 0L,
        )

        assertEquals(7L, usage.totalBytes)
        assertEquals(1, usage.photoCount)
        assertEquals(0L, usage.photoBytes)
        assertEquals(2, usage.backupCount)
    }

    @Test
    fun emptyListingIsZeroEverywhere() {
        val usage = driveUsageOf(emptyList(), now = 5L)

        assertEquals(DriveUsage(totalBytes = 0L, photoCount = 0, photoBytes = 0L, backupCount = 0, measuredAtEpochMillis = 5L), usage)
    }

    @Test
    fun photoPrefixMustMatchExactlyAtTheStart() {
        val usage = driveUsageOf(
            listOf(DriveFileMetadata(id = "1", name = "old/photos/garment.webp", size = 9L)),
            now = 0L,
        )

        assertEquals(0, usage.photoCount)
        assertEquals(1, usage.backupCount)
        assertEquals(9L, usage.totalBytes)
    }
}
