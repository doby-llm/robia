package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** V-D02-1: a downloaded photo that does not match its record is guarded, never moved into place. */
class PhotoIntegrityRestoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val api = FakeDriveSnapshotApi()
    private val bytes = "webp:pixels-g1-complete".toByteArray()
    private val blobPath = PhotoBlobKeys.driveKey("g1", PhotoBlobKeys.hash16(bytes))

    private fun record(
        contentHash: String? = sha256Hex(bytes),
        byteSize: Long? = bytes.size.toLong(),
    ) = GarmentPhotoRecord(
        garmentId = "g1",
        localUri = "content://phone/g1",
        blobPath = blobPath,
        contentHash = contentHash,
        byteSize = byteSize,
    )

    @Test
    fun wrongHashIsGuardedAndTheTempFileIsDeleted() = runBlocking {
        api.files[blobPath] = bytes
        val result = restore(record(contentHash = "0".repeat(64)))

        assertEquals(REMOTE_PHOTO_MISMATCH, result.single().restoreFailureCategory)
        assertNull(result.single().restoredLocalUri)
    }

    @Test
    fun wrongSizeIsGuarded() = runBlocking {
        api.files[blobPath] = bytes
        val result = restore(record(byteSize = bytes.size + 1L))

        assertEquals(REMOTE_PHOTO_MISMATCH, result.single().restoreFailureCategory)
        assertNull(result.single().restoredLocalUri)
    }

    @Test
    fun truncatedDownloadIsGuarded() = runBlocking {
        api.files[blobPath] = bytes.copyOf(bytes.size - 3)
        val result = restore(record())

        assertEquals(REMOTE_PHOTO_MISMATCH, result.single().restoreFailureCategory)
        assertNull(result.single().restoredLocalUri)
    }

    @Test
    fun matchingHashAndSizeAreRestored() = runBlocking {
        api.files[blobPath] = bytes
        val result = restore(record())

        assertEquals(null, result.single().restoreFailureCategory)
        assertTrue(result.single().restoredLocalUri != null)
    }

    @Test
    fun legacyRecordWithoutHashOrSizeIsAccepted() = runBlocking {
        api.files[blobPath] = bytes
        val result = restore(record(contentHash = null, byteSize = null))

        assertEquals(null, result.single().restoreFailureCategory)
        assertTrue(result.single().restoredLocalUri != null)
    }

    @Test
    fun aMismatchedDownloadNeverReplacesThePhonesOwnFile() = runBlocking {
        api.files[blobPath] = bytes
        val movedFiles = mutableListOf<String>()
        val photo = record(contentHash = "0".repeat(64))

        val result = restore(photo, movedFiles)

        assertTrue(movedFiles.isEmpty())
        assertEquals(photo.localUri, result.single().localUri)
    }

    private suspend fun restore(
        photo: GarmentPhotoRecord,
        movedFiles: MutableList<String> = mutableListOf(),
    ): List<GarmentPhotoRecord> {
        val workDir = temporaryFolder.newFolder()
        val outputDir = temporaryFolder.newFolder()
        val restored = restorePhotoBlobs(
            api = api,
            accessToken = "token",
            photos = listOf(photo),
            workDir = workDir,
            decodeBounds = { file -> if (file.length() > 0L) 64 to 48 else null },
            moveToFinal = { garmentId, hash16, source ->
                movedFiles += garmentId
                val target = File(outputDir, PhotoBlobKeys.restoredLocalFileName(garmentId, hash16))
                check(source.renameTo(target)) { "rename failed for $garmentId" }
                target.toURI().toString()
            },
        )
        assertTrue(workDir.listFiles().orEmpty().none { file -> file.name.endsWith(".part") })
        return (restored as DriveApiResult.Success).value
    }
}
