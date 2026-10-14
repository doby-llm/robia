package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class HydrateConcurrencyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun thirtyFourPhotosWithOneTransientFailureRestoreThirtyThreeAndGuardOne() = runBlocking {
        val api = FakeDriveSnapshotApi(downloadDelayMillis = 20L)
        val photos = (1..34).map { number -> photoWithBlob(api, number) }
        val failingPhoto = photos[4]
        api.failures[failingPhoto.blobPath] = DriveApiResult.Transient(IOException("connection reset"))

        val restored = restoredPhotos(api, photos)

        assertEquals(34, restored.size)
        assertEquals(33, restored.count { photo -> photo.restoredLocalUri != null })
        val guarded = restored.single { photo -> photo.restoreFailureCategory != null }
        assertEquals(failingPhoto.garmentId, guarded.garmentId)
        assertEquals(REMOTE_PHOTO_TRANSIENT, guarded.restoreFailureCategory)
        assertEquals(1, api.listCalls.get())
        assertEquals(4, api.maxInFlight.get())
    }

    @Test
    fun storageFullFromAnyPhotoEndsTheWholeRestore() = runBlocking {
        val api = FakeDriveSnapshotApi(downloadDelayMillis = 20L)
        val photos = (1..10).map { number -> photoWithBlob(api, number) }
        api.failures[photos[2].blobPath] = DriveApiResult.StorageFull

        assertEquals(DriveApiResult.StorageFull, restore(api, photos))
    }

    @Test
    fun missingBlobBecomesAGuardedMissingPhoto() = runBlocking {
        val api = FakeDriveSnapshotApi()
        val photo = GarmentPhotoRecord(
            garmentId = "garment-missing",
            localUri = "content://local/missing",
            blobPath = PhotoBlobKeys.driveKey("garment-missing", "0123456789abcdef"),
        )

        val restored = restoredPhotos(api, listOf(photo)).single()

        assertEquals(REMOTE_PHOTO_MISSING, restored.restoreFailureCategory)
        assertEquals(null, restored.restoredLocalUri)
    }

    @Test
    fun legacyBlobPathFallsBackToTheSingleCandidateUnderTheGarmentPrefix() = runBlocking {
        val api = FakeDriveSnapshotApi()
        val webpName = PhotoBlobKeys.driveKey("garment-legacy", "fedcba9876543210")
        api.files[webpName] = "legacy".toByteArray()
        val photo = GarmentPhotoRecord(
            garmentId = "garment-legacy",
            localUri = "content://local/legacy",
            blobPath = "photos/garment-legacy/original",
        )

        val restored = restoredPhotos(api, listOf(photo)).single()

        assertTrue(restored.restoredLocalUri != null)
        assertEquals(null, restored.restoreFailureCategory)
    }

    private fun photoWithBlob(api: FakeDriveSnapshotApi, number: Int): GarmentPhotoRecord {
        val garmentId = "garment-$number"
        val bytes = "photo-$number".toByteArray()
        val blobPath = PhotoBlobKeys.driveKey(garmentId, PhotoBlobKeys.hash16(bytes))
        api.files[blobPath] = bytes
        return GarmentPhotoRecord(garmentId = garmentId, localUri = "content://local/$number", blobPath = blobPath)
    }

    private suspend fun restoredPhotos(
        api: FakeDriveSnapshotApi,
        photos: List<GarmentPhotoRecord>,
    ): List<GarmentPhotoRecord> = (restore(api, photos) as DriveApiResult.Success).value

    private suspend fun restore(
        api: FakeDriveSnapshotApi,
        photos: List<GarmentPhotoRecord>,
    ): DriveApiResult<List<GarmentPhotoRecord>> {
        val workDir = temporaryFolder.newFolder()
        val outputDir = temporaryFolder.newFolder()
        val result = restorePhotoBlobs(
            api = api,
            accessToken = "token",
            photos = photos,
            workDir = workDir,
            decodeBounds = { file -> if (file.length() > 0L) 64 to 48 else null },
            moveToFinal = { garmentId, hash16, source ->
                val target = File(outputDir, PhotoBlobKeys.restoredLocalFileName(garmentId, hash16))
                check(source.renameTo(target)) { "rename failed for $garmentId" }
                target.toURI().toString()
            },
        )
        assertTrue(workDir.listFiles().orEmpty().none { file -> file.name.endsWith(".part") })
        return result
    }
}
