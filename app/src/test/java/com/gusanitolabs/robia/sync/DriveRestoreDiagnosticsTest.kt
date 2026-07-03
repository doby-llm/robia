package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class DriveRestoreDiagnosticsTest {
    @Test
    fun guardedPhotoRestoreIssues_reportsMissingAndUnreadableRemotePhotoBlobs() {
        val snapshot = WardrobeSyncSnapshot(
            photos = listOf(
                GarmentPhotoRecord(
                    garmentId = "garment-missing",
                    localUri = "content://old-device/missing.jpg",
                    blobPath = "photos/garment-missing/original",
                    restoreFailureCategory = REMOTE_PHOTO_MISSING,
                    restoreFailureMessage = "Drive photo blob photos/garment-missing/original was not found.",
                ),
                GarmentPhotoRecord(
                    garmentId = "garment-unreadable",
                    localUri = "content://old-device/corrupt.jpg",
                    blobPath = "photos/garment-unreadable/original",
                    restoreFailureCategory = REMOTE_PHOTO_UNREADABLE,
                    restoreFailureMessage = "Remote Drive photo blob photos/garment-unreadable/original is corrupt or unreadable.",
                ),
                GarmentPhotoRecord(
                    garmentId = "garment-restored",
                    localUri = "content://old-device/restored.jpg",
                    blobPath = "photos/garment-restored/original",
                    restoredLocalUri = "content://local/restored.jpg",
                ),
            ),
        )

        val issues = snapshot.guardedPhotoRestoreIssues()

        assertEquals(2, issues.size)
        assertEquals(
            listOf(REMOTE_PHOTO_MISSING, REMOTE_PHOTO_UNREADABLE),
            issues.map(PhotoRestoreIssue::category),
        )
        assertEquals(listOf("garment-missing", "garment-unreadable"), issues.map(PhotoRestoreIssue::garmentId))
    }

    @Test
    fun guardedPhotoRestoreIssues_isEmptyForNoRemoteBackupOrFullyRestoredSnapshot() {
        assertTrue(WardrobeSyncSnapshot().guardedPhotoRestoreIssues().isEmpty())

        val fullyRestored = WardrobeSyncSnapshot(
            photos = listOf(
                GarmentPhotoRecord(
                    garmentId = "garment-restored",
                    localUri = "content://old-device/restored.jpg",
                    restoredLocalUri = "content://local/restored.jpg",
                ),
            ),
        )

        assertTrue(fullyRestored.guardedPhotoRestoreIssues().isEmpty())
    }

    @Test
    fun toImportPhotoRestoreIssue_marksOnlyGuardedPhotosAsVisibleMissingPhotoState() {
        val guarded = GarmentPhotoRecord(
            garmentId = "garment-missing",
            localUri = "content://old-device/missing.jpg",
            restoreFailureCategory = REMOTE_PHOTO_MISSING,
            restoreFailureMessage = "Drive photo blob was not found.",
        )
        val restored = guarded.copy(
            restoreFailureCategory = null,
            restoreFailureMessage = null,
            restoredLocalUri = "content://local/restored.jpg",
        )

        val issue = guarded.toImportPhotoRestoreIssue()

        assertEquals(REMOTE_PHOTO_MISSING, issue?.category)
        assertEquals(MISSING_RESTORED_PHOTO_MESSAGE, issue?.userVisibleMessage)
        assertEquals(null, restored.toImportPhotoRestoreIssue())
    }

    @Test
    fun restoreDiagnosticEvent_includesPerPhotoBlobEvidenceWithoutFullHash() {
        val event = GarmentPhotoRecord(
            garmentId = "garment-restored",
            localUri = "content://old-device/restored.jpg",
            blobPath = "photos/garment-restored/original",
            byteSize = 1234,
            contentHash = "abcdef1234567890",
            restoredLocalUri = "content://local/restored.jpg",
        ).restoreDiagnosticEvent()

        assertTrue(event.contains("status=fetched_importable"))
        assertTrue(event.contains("garmentId=garment-restored"))
        assertTrue(event.contains("blobPath=photos/garment-restored/original"))
        assertTrue(event.contains("byteSize=1234"))
        assertTrue(event.contains("contentHash=abcdef123456"))
        assertTrue(event.contains("restoredLocalUri=true"))
    }

    @Test
    fun restoreFetchFailureStatus_onlyReportsOfflineForNetworkFailures() {
        assertEquals(CloudRestoreStatus.Offline, UnknownHostException("dns").restoreFetchFailureStatus())
        assertEquals(CloudRestoreStatus.Offline, IOException("socket closed").restoreFetchFailureStatus())
        assertEquals(
            CloudRestoreStatus.Failed,
            IllegalStateException("Remote schema is newer than supported schema.").restoreFetchFailureStatus(),
        )
        assertEquals(
            CloudRestoreStatus.Failed,
            IOException("Drive API returned HTTP 404: not found").restoreFetchFailureStatus(),
        )
    }
}
