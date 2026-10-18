package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-D02-5: every category of the read-only Check photos planner, plus orphan counting. */
class PhotoIntegrityCheckTest {
    private val blobA = "photos/a/0123456789abcdef.webp"
    private val blobB = "photos/b/0123456789abcdef.webp"
    private val blobC = "photos/c/0123456789abcdef.webp"
    private val files = listOf(
        DriveFileMetadata(id = "1", name = blobA, size = 10L),
        DriveFileMetadata(id = "2", name = blobB, size = 12L),
        DriveFileMetadata(id = "3", name = blobC, size = 7L),
    )

    @Test
    fun everyCategoryIsReported() {
        val snapshot = WardrobeSyncSnapshot(
            garments = listOf("a", "b", "c", "d", "e").map { id ->
                GarmentSyncRecord(
                    id = id,
                    name = "Garment $id",
                    notes = "",
                    fitValue = null,
                    isFavorite = false,
                    isArchived = false,
                    createdAtEpochMillis = 0L,
                    updatedAtEpochMillis = 0L,
                )
            },
            photos = listOf(
                photo("a", blobA, size = 10L),
                photo("b", blobB, size = 10L),
                photo("c", "photos/c/fedcba9876543210.webp", size = 7L),
                photo("d", "photos/d/0123456789abcdef.webp", size = 7L),
            ),
        )
        val local = listOf(
            LocalPhotoRef("a", fileLength = 10L),
            LocalPhotoRef("b", fileLength = null),
            LocalPhotoRef("c", fileLength = 7L),
            LocalPhotoRef("e", fileLength = 5L),
        )

        val report = checkPhotoIntegrity(local, files, snapshot)

        assertEquals(
            mapOf(
                "a" to PhotoIntegrityStatus.Ok,
                "b" to PhotoIntegrityStatus.SizeMismatch,
                "c" to PhotoIntegrityStatus.NotYetUploaded,
                "d" to PhotoIntegrityStatus.MissingOnDrive,
                "e" to PhotoIntegrityStatus.NotYetUploaded,
            ),
            report.entries.associate { entry -> entry.garmentId to entry.status },
        )
        assertEquals("Garment a", report.entries.first().garmentName)
        assertEquals(1, report.okCount)
        assertEquals(4, report.problems.size)
    }

    @Test
    fun aMissingLocalFileWithItsBlobInDriveIsRestorable() {
        val snapshot = WardrobeSyncSnapshot(photos = listOf(photo("a", blobA, size = 10L)))

        val report = checkPhotoIntegrity(listOf(LocalPhotoRef("a", fileLength = null)), files, snapshot)

        assertEquals(PhotoIntegrityStatus.MissingLocalFile, report.entries.single().status)
    }

    @Test
    fun blobsNoRecordRefersToAreCountedAsOrphansAndNothingElse() {
        val snapshot = WardrobeSyncSnapshot(photos = listOf(photo("a", blobA, size = 10L)))
        val listing = files + DriveFileMetadata(id = "4", name = "wardrobe_snapshot.json", size = 900L)

        val report = checkPhotoIntegrity(listOf(LocalPhotoRef("a", fileLength = 10L)), listing, snapshot)

        assertEquals(2, report.orphanBlobCount)
    }

    @Test
    fun withoutASnapshotEveryLocalPhotoIsNotYetUploaded() {
        val report = checkPhotoIntegrity(listOf(LocalPhotoRef("a", fileLength = 10L)), emptyList(), snapshot = null)

        assertEquals(PhotoIntegrityStatus.NotYetUploaded, report.entries.single().status)
        assertEquals(0, report.orphanBlobCount)
    }

    private fun photo(garmentId: String, blobPath: String, size: Long) = GarmentPhotoRecord(
        garmentId = garmentId,
        localUri = "content://photos/$garmentId",
        blobPath = blobPath,
        byteSize = size,
    )
}
