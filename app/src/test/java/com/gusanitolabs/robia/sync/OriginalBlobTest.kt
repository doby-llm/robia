package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** V-OR2-2 and V-OR2-4: original blob keys, the purge rule, and the restore pass that never blocks a garment. */
class OriginalBlobTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val hash16 = "0123456789abcdef"
    private val originalPath = "originals/g1/$hash16.jpg"
    private val otherOriginalPath = "originals/g1/fedcba9876543210.jpg"

    @Test
    fun originalKeyIsUnderItsOwnPrefixAndRoundTripsItsGarment() {
        assertEquals(originalPath, PhotoBlobKeys.originalDriveKey("g1", hash16))
        assertEquals("g1", PhotoBlobKeys.originalGarmentId(originalPath))
        assertNull(PhotoBlobKeys.originalGarmentId("photos/g1/$hash16.webp"))
        assertNull(PhotoBlobKeys.originalGarmentId("originals/g1/short.jpg"))
        assertNull(PhotoBlobKeys.originalGarmentId("originals/g1/$hash16.webp"))
    }

    @Test
    fun cutoutKeysAreUnchangedByOriginals() {
        assertFalse(PhotoBlobKeys.isLegacyKey("photos/g1/$hash16.webp"))
        assertTrue(PhotoBlobKeys.isLegacyKey("photos/g1/original"))
        assertEquals("restored-original-g1-$hash16.jpg", PhotoBlobKeys.restoredOriginalFileName("g1", hash16))
    }

    @Test
    fun originalsAreCountedApartFromPhotosAndBackup() {
        val usage = driveUsageOf(
            listOf(
                DriveFileMetadata(id = "1", name = "photos/g1/$hash16.webp", size = 100L),
                DriveFileMetadata(id = "2", name = originalPath, size = 900L),
                DriveFileMetadata(id = "3", name = "wardrobe_snapshot.json", size = 10L),
            ),
            now = 0L,
        )

        assertEquals(1, usage.originalCount)
        assertEquals(900L, usage.originalBytes)
        assertEquals(1, usage.photoCount)
        assertEquals(1, usage.backupCount)
        assertEquals(1_010L, usage.totalBytes)
    }

    @Test
    fun originalOfAGarmentWithARecordIsKeptEvenWhenTheRecordNamesNoOriginal() {
        val deleted = unreferencedOriginalBlobs(
            names = listOf(originalPath),
            snapshotPhotos = listOf(photo()),
            verifiedPaths = emptySet(),
            protectedGarmentIds = emptySet(),
        )

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun originalOfAGarmentWithoutARecordIsDeleted() {
        val deleted = unreferencedOriginalBlobs(
            names = listOf(originalPath),
            snapshotPhotos = emptyList(),
            verifiedPaths = emptySet(),
            protectedGarmentIds = emptySet(),
        )

        assertEquals(listOf(originalPath), deleted)
    }

    @Test
    fun protectedGarmentKeepsItsOriginalsEvenWithoutARecord() {
        val deleted = unreferencedOriginalBlobs(
            names = listOf(originalPath),
            snapshotPhotos = emptyList(),
            verifiedPaths = emptySet(),
            protectedGarmentIds = setOf("g1"),
        )

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun replacedOriginalIsDeletedOnlyOnceTheNewOneIsVerified() {
        val names = listOf(originalPath, otherOriginalPath)
        val replaced = photo(originalBlobPath = otherOriginalPath)

        val beforeVerified = unreferencedOriginalBlobs(names, listOf(replaced), emptySet(), emptySet())
        val afterVerified = unreferencedOriginalBlobs(names, listOf(replaced), setOf(otherOriginalPath), emptySet())

        assertEquals(emptyList<String>(), beforeVerified)
        assertEquals(listOf(originalPath), afterVerified)
    }

    @Test
    fun namesThatAreNotOriginalKeysAreNeverDeleted() {
        val deleted = unreferencedOriginalBlobs(
            names = listOf("originals/g1/notes.txt"),
            snapshotPhotos = emptyList(),
            verifiedPaths = emptySet(),
            protectedGarmentIds = emptySet(),
        )

        assertEquals(emptyList<String>(), deleted)
    }

    @Test
    fun originalIsReusedOnlyWhenDriveListsTheSameNameAndSize() {
        val index = DriveFileIndex(listOf(DriveFileMetadata(id = "1", name = originalPath, size = 5L)))

        assertTrue(originalReusable(index, originalPath, 5L))
        assertFalse(originalReusable(index, originalPath, 6L))
        assertFalse(originalReusable(index, otherOriginalPath, 5L))
    }

    @Test
    fun restoreDownloadsTheOriginalAfterTheCutoutAndKeepsTheRecord() = runBlocking {
        val api = FakeDriveSnapshotApi()
        val cutout = "webp:cutout".toByteArray()
        val original = "jpeg:original-frame".toByteArray()
        val cutoutPath = PhotoBlobKeys.driveKey("g1", PhotoBlobKeys.hash16(cutout))
        api.files[cutoutPath] = cutout
        api.files[originalPath] = original
        val failures = mutableListOf<String>()

        val restored = restoreWithOriginals(
            api = api,
            photo = photo(
                blobPath = cutoutPath,
                contentHash = sha256Hex(cutout),
                byteSize = cutout.size.toLong(),
                originalBlobPath = originalPath,
                originalHash = sha256Hex(original).take(16),
                originalByteSize = original.size.toLong(),
            ),
            onFailure = { message -> failures += message },
        ).single()

        assertNull(restored.restoreFailureCategory)
        assertNotNull(restored.restoredLocalUri)
        assertNotNull(restored.restoredOriginalLocalUri)
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun missingOriginalIsLoggedAndTheGarmentRestoresWithItsCutout() = runBlocking {
        val api = FakeDriveSnapshotApi()
        val cutout = "webp:cutout".toByteArray()
        val cutoutPath = PhotoBlobKeys.driveKey("g1", PhotoBlobKeys.hash16(cutout))
        api.files[cutoutPath] = cutout
        val failures = mutableListOf<String>()

        val restored = restoreWithOriginals(
            api = api,
            photo = photo(
                blobPath = cutoutPath,
                contentHash = sha256Hex(cutout),
                byteSize = cutout.size.toLong(),
                originalBlobPath = originalPath,
                originalHash = hash16,
                originalByteSize = 4L,
            ),
            onFailure = { message -> failures += message },
        ).single()

        assertNull(restored.restoreFailureCategory)
        assertNotNull(restored.restoredLocalUri)
        assertNull(restored.restoredOriginalLocalUri)
        assertEquals(1, failures.size)
        assertTrue(failures.single().startsWith("original_restore_failed garmentId=g1"))
    }

    private suspend fun restoreWithOriginals(
        api: FakeDriveSnapshotApi,
        photo: GarmentPhotoRecord,
        onFailure: (String) -> Unit,
    ): List<GarmentPhotoRecord> {
        val moved = File(temporaryFolder.root, "moved").apply { mkdirs() }
        val result = restorePhotoBlobs(
            api = api,
            accessToken = "token",
            photos = listOf(photo),
            workDir = File(temporaryFolder.root, "work"),
            decodeBounds = { Pair(10, 10) },
            moveToFinal = { _, hash, source -> File(moved, "cutout-$hash").also { target -> source.renameTo(target) }.path },
            moveOriginalToFinal = { _, hash, source -> File(moved, "original-$hash").also { target -> source.renameTo(target) }.path },
            onOriginalFailure = onFailure,
        )
        return (result as DriveApiResult.Success).value
    }

    private fun photo(
        blobPath: String = "photos/g1/$hash16.webp",
        contentHash: String? = null,
        byteSize: Long? = null,
        originalBlobPath: String? = null,
        originalHash: String? = null,
        originalByteSize: Long? = null,
    ) = GarmentPhotoRecord(
        garmentId = "g1",
        localUri = "content://phone/g1",
        blobPath = blobPath,
        contentHash = contentHash,
        byteSize = byteSize,
        originalBlobPath = originalBlobPath,
        originalHash = originalHash,
        originalByteSize = originalByteSize,
    )
}
