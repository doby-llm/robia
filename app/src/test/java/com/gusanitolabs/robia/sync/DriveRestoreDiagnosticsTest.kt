package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class DriveRestoreDiagnosticsTest {
    @Test
    fun deletedPhotoBlobPurgeCandidates_includeGarmentTombstonesAndExcludeActiveBlobs() {
        val snapshot = WardrobeSyncSnapshot(
            photos = listOf(
                GarmentPhotoRecord(
                    garmentId = "active-garment",
                    localUri = "content://local/active.jpg",
                    blobPath = "photos/active-garment/original",
                ),
            ),
            tombstones = listOf(
                SyncTombstoneRecord(
                    entityType = "garment",
                    entityId = "deleted garment",
                    deletedAtEpochMillis = 10L,
                    revision = 11L,
                ),
                SyncTombstoneRecord(
                    entityType = "garment",
                    entityId = "active-garment",
                    deletedAtEpochMillis = 12L,
                    revision = 13L,
                ),
            ),
        )

        val candidates = snapshot.deletedPhotoBlobPurgeCandidates()

        assertTrue(candidates.contains("photos/deleted_garment/original"))
        assertTrue(candidates.contains("photos/deleted garment/original"))
        assertTrue(candidates.none { it == "photos/active-garment/original" })
    }

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
        assertEquals("guarded_remote_photos count=2", snapshot.finalUploadGuardReasonAfterRestore())
    }

    @Test
    fun guardedPhotoRestoreIssues_isEmptyForNoRemoteBackupOrFullyRestoredSnapshot() {
        assertTrue(WardrobeSyncSnapshot().guardedPhotoRestoreIssues().isEmpty())
        assertEquals(null, WardrobeSyncSnapshot().finalUploadGuardReasonAfterRestore())

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
        assertEquals(null, fullyRestored.finalUploadGuardReasonAfterRestore())
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
            mimeType = "image/jpeg",
            byteSize = 1234,
            byteMagic = "ffd8ffe000104a464946",
            decodedWidth = 640,
            decodedHeight = 480,
            contentHash = "abcdef1234567890",
            restoredLocalUri = "content://local/restored.jpg",
        ).restoreDiagnosticEvent()

        assertTrue(event.contains("status=fetched_importable"))
        assertTrue(event.contains("garmentId=garment-restored"))
        assertTrue(event.contains("blobPath=photos/garment-restored/original"))
        assertTrue(event.contains("byteSize=1234"))
        assertTrue(event.contains("mimeType=image/jpeg"))
        assertTrue(event.contains("byteMagic=ffd8ffe000104a464946"))
        assertTrue(event.contains("decoded=640x480"))
        assertTrue(event.contains("contentHash=abcdef123456"))
        assertTrue(event.contains("restoredLocalUri=true"))
    }

    @Test
    fun perPhotoRestoreDiagnostics_includeLookupFetchWriteDecodeAndImportEvidence() {
        val photo = GarmentPhotoRecord(
            garmentId = "garment-restored",
            localUri = "content://old-device/restored.jpg",
            blobPath = "photos/garment-restored/original",
            mimeType = "image/jpeg",
            byteSize = 1234,
            contentHash = "abcdef1234567890",
        )
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0x00, 0x10)

        val events = photo.restoreFetchStartedEvents(description = "Blue jacket") +
            DriveBlob(
                bytes = bytes,
                file = DriveFileMetadata(
                    id = "drive-file-secret-id",
                    modifiedTime = "2026-07-04T01:02:03Z",
                    mimeType = "image/jpeg",
                    size = 987,
                ),
                httpStatusCode = 200,
                contentType = "image/jpeg",
                contentLength = 6,
            ).restoreFetchResultEvents(photo) +
            photo.localWriteDiagnosticEvent(
                targetExtension = "jpg",
                fileLength = 6,
                readBackBytes = bytes,
            ) +
            photo.decodeDiagnosticEvent(decoderPath = "BitmapFactory", width = 640, height = 480) +
            photo.importDiagnosticEvent(persistedPhotoUriPresent = true, placeholderReason = null)

        assertTrue(events.any { it.contains("photo_restore_fetch_started") })
        assertTrue(events.any { it.contains("description=\"Blue jacket\"") })
        assertTrue(events.any { it.contains("drive_file_lookup_result") && it.contains("found=true") })
        assertTrue(events.any { it.contains("fileIdHash=") })
        assertTrue(events.none { it.contains("drive-file-secret-id") })
        assertTrue(events.any { it.contains("photo_restore_fetch_result") && it.contains("httpStatus=200") })
        assertTrue(events.any { it.contains("bytesLength=6") && it.contains("first32=ffd8ffe00010") })
        assertTrue(events.any { it.contains("photo_restore_local_write_result") && it.contains("readbackByteCount=6") && it.contains("readbackHash=") })
        assertTrue(events.any { it.contains("photo_restore_decode_result") && it.contains("width=640 height=480") })
        assertTrue(events.any { it.contains("photo_restore_import_result") && it.contains("persistedPhotoUriPresent=true") })
    }

    @Test
    fun perPhotoRestoreDiagnostics_includeFullFetchedPngEvidence() {
        val photo = GarmentPhotoRecord(
            garmentId = "garment-restored",
            localUri = "content://old-device/restored.png",
            blobPath = "photos/garment-restored/cropped-subject.png",
            mimeType = "image/png",
        )
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44,
            0xae.toByte(), 0x42, 0x60, 0x82.toByte(),
        )

        val events = DriveBlob(
            bytes = bytes,
            httpStatusCode = 200,
            contentType = "image/png",
            contentLength = bytes.size.toLong(),
        ).restoreFetchResultEvents(photo) +
            photo.localWriteDiagnosticEvent(
                targetExtension = "png",
                fileLength = bytes.size.toLong(),
                readBackBytes = bytes,
            )

        assertTrue(events.any { it.contains("sha256=") && !it.contains("sha256Prefix=") })
        assertTrue(events.any { it.contains("first32=89504e470d0a1a0a0000000049454e44ae426082") })
        assertTrue(events.any { it.contains("last32=89504e470d0a1a0a0000000049454e44ae426082") })
        assertTrue(events.any { it.contains("png=png_signature_iend_present") })
        assertTrue(events.any { it.contains("readbackHash=") && it.contains("readbackFirst32=") && it.contains("readbackLast32=") })
    }

    @Test
    fun perPhotoRestoreDiagnostics_doNotTruncateExactDecodeFailureReason() {
        val event = GarmentPhotoRecord(
            garmentId = "garment-restored",
            localUri = "content://old-device/restored.png",
            blobPath = "photos/garment-restored/cropped-subject.png",
        ).decodeDiagnosticEvent(
            decoderPath = "BitmapFactory.byteArray",
            failure = "java.lang.IllegalStateException: Decoder failed at com.gusanitolabs.robia.sync.GoogleDriveWardrobeRepository.decodeImageBytes:742",
        )

        assertTrue(event.contains("java.lang.IllegalStateException"))
        assertTrue(event.contains("Decoder failed"))
        assertTrue(event.contains("GoogleDriveWardrobeRepository.decodeImageBytes:742"))
    }

    @Test
    fun pngSanityLabelDistinguishesSignatureAndIend() {
        val validPngTail = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44,
            0xae.toByte(), 0x42, 0x60, 0x82.toByte(),
        )

        assertEquals("png_signature_iend_present", validPngTail.pngSanityLabel())
        assertEquals("png_signature_iend_missing", validPngTail.dropLast(1).toByteArray().pngSanityLabel())
        assertEquals("not_png_signature", byteArrayOf(0xff.toByte(), 0xd8.toByte()).pngSanityLabel())
    }

    @Test
    fun perPhotoRestoreDiagnostics_includeMissingBlobRootCauseAndPlaceholderReason() {
        val photo = GarmentPhotoRecord(
            garmentId = "garment-missing",
            localUri = "content://old-device/missing.jpg",
            blobPath = "photos/garment-missing/original",
        )

        val events = photo.restoreFetchStartedEvents(description = null) + listOf(
            photo.driveLookupMissingEvent(),
            photo.fetchMissingEvent(),
            photo.importDiagnosticEvent(
                persistedPhotoUriPresent = false,
                placeholderReason = REMOTE_PHOTO_MISSING,
            ),
        )

        assertTrue(events.any { it.contains("photo_restore_fetch_started") })
        assertTrue(events.any { it.contains("drive_file_lookup_result") && it.contains("found=false") })
        assertTrue(events.any { it.contains("photo_restore_fetch_result") && it.contains("status=not_found") })
        assertTrue(events.any { it.contains("photo_restore_import_result") && it.contains("placeholderReason=$REMOTE_PHOTO_MISSING") })
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

    @Test
    fun restoreSyncLogSanitizer_redactsSecretsAndDevicePaths() {
        val sanitized = sanitizeLogField(
            "Bearer abc.def access_token=secret manu@example.com content://media/p/1 file:///tmp/photo.jpg /data/user/0/app/photo.jpg",
        )

        assertTrue(sanitized.contains("Bearer <redacted>"))
        assertTrue(sanitized.contains("access_token=<redacted>"))
        assertTrue(sanitized.contains("<email-redacted>"))
        assertTrue(sanitized.contains("content://<redacted>"))
        assertTrue(sanitized.contains("file://<redacted>"))
        assertTrue(sanitized.contains("<path-redacted>"))
        assertTrue(!sanitized.contains("secret"))
        assertTrue(!sanitized.contains("manu@example.com"))
    }

    @Test
    fun restoreSyncLogSanitizer_preservesSafeDriveBlobPaths() {
        val sanitized = sanitizeLogField("blobPath=photos/03562857-57a3-4a4f-9c1c-b337d78cac1f/original /data/user/0/app/photo.jpg")

        assertTrue(sanitized.contains("photos/03562857-57a3-4a4f-9c1c-b337d78cac1f/original"))
        assertTrue(sanitized.contains("<path-redacted>"))
    }

    @Test
    fun restoreSyncLogEvent_usesSanitizedCopyableLine() {
        val line = RestoreSyncLogEvent(
            correlationId = "abc123",
            phase = CloudRestorePhase.Downloading,
            status = CloudRestoreStatus.Running,
            message = "fetch Bearer token-value",
            garmentId = "garment-1",
            blobPath = "photos/garment-1/original",
            byteSize = 42,
            contentHash = "abcDEF1234567890",
            restoredUriStatus = "content://private/image.jpg",
            exceptionMessage = "refresh_token=sensitive",
            completedWork = 3,
            totalWork = 12,
        ).toLogLine()

        assertTrue(line.contains("correlation_id=abc123"))
        assertTrue(line.contains("phase=Downloading"))
        assertTrue(line.contains("progress=3/12"))
        assertTrue(line.contains("blob_path=photos/garment-1/original"))
        assertTrue(line.contains("content_hash=abcDEF1234567890"))
        assertTrue(line.contains("content://<redacted>"))
        assertTrue(line.contains("refresh_token=<redacted>"))
        assertTrue(!line.contains("token-value"))
        assertTrue(!line.contains("sensitive"))
    }
}
