package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardedIsolationTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()

    private val remoteBlobPath = "photos/g1/0123456789abcdef.webp"
    private val remoteRecord = GarmentPhotoRecord(
        garmentId = "g1",
        localUri = "content://photos/g1",
        blobPath = remoteBlobPath,
        mimeType = "image/webp",
        contentHash = "remote-content-hash",
        byteSize = 42L,
        sourceHash = "remote-source-hash",
    )

    @Test
    fun guardedGarmentRecordIsCopiedFromRemoteAndItsBlobsAreKept() = runBlocking {
        api.files[remoteBlobPath] = "remote".toByteArray()
        api.files["photos/g1/fedcba9876543210.webp"] = "older".toByteArray()
        val guarded = localPhoto("g1").copy(localUri = "", restoreFailureCategory = "local_photo_missing")
        val remote = WardrobeSyncSnapshot(photos = listOf(remoteRecord))

        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = emptyMap())

        val uploaded = (result as DriveSyncResult.Success).value.uploadedPhotos
        assertEquals(listOf(remoteRecord), uploaded)
        assertEquals(0, api.blobUploads.get())
        assertTrue(api.files.containsKey(remoteBlobPath))
        assertTrue(api.files.containsKey("photos/g1/fedcba9876543210.webp"))
    }

    @Test
    fun guardedGarmentWithoutRemoteRecordIsOmittedButNeverPurged() = runBlocking {
        api.files["photos/g1/fedcba9876543210.webp"] = "older".toByteArray()
        val guarded = localPhoto("g1").copy(localUri = "", restoreFailureCategory = "local_photo_missing")

        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote = null, previous = emptyMap())

        assertTrue(result is DriveSyncResult.Success)
        assertTrue((result as DriveSyncResult.Success).value.uploadedPhotos.isEmpty())
        assertTrue(api.files.containsKey("photos/g1/fedcba9876543210.webp"))
    }

    @Test
    fun guardedGarmentWithoutRemoteRecordKeepsPreviousBlobAndNeverPurgesIt() = runBlocking {
        val previousBlobPath = "photos/g1/fedcba9876543210.webp"
        api.files[previousBlobPath] = "older".toByteArray()
        val previous = LastUploadedPhoto(
            garmentId = "g1",
            sourceHash = "previous-source-hash",
            blobPath = previousBlobPath,
            contentHash = "previous-content-hash",
            byteSize = 5L,
            mimeType = "image/webp",
        )
        val guarded = localPhoto("g1").copy(localUri = "", restoreFailureCategory = "local_photo_missing")

        val result = phone.pipeline(api).upload(
            "token",
            WardrobeSyncSnapshot(photos = listOf(guarded)),
            remote = null,
            previous = mapOf("g1" to previous),
        )

        val uploaded = (result as DriveSyncResult.Success).value.uploadedPhotos
        assertEquals(listOf(previousBlobPath), uploaded.map { it.blobPath })
        assertEquals("previous-content-hash", uploaded.single().contentHash)
        assertEquals(0, api.blobUploads.get())
        assertTrue(api.files.containsKey(previousBlobPath))
    }

    @Test
    fun permanentPhotoFailureKeepsPreviousRecordAndContinues() = runBlocking {
        api.files[remoteBlobPath] = "remote".toByteArray()
        phone.sources[localPhoto("g2").localUri] = "pixels-g2"
        // g1 has no readable local file, so it fails per photo while g2 uploads normally.
        val remote = WardrobeSyncSnapshot(photos = listOf(remoteRecord))
        val photos = listOf(localPhoto("g1"), localPhoto("g2"))

        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = photos), remote, previous = emptyMap())

        val outcome = (result as DriveSyncResult.Success).value
        assertEquals(listOf("g1"), outcome.manifest.photoUploadFailures.map { it.garmentId })
        assertEquals(remoteRecord, outcome.uploadedPhotos.single { it.garmentId == "g1" })
        assertTrue(outcome.uploadedPhotos.any { it.garmentId == "g2" && it.blobPath.startsWith("photos/g2/") })
        assertTrue(api.files.containsKey(remoteBlobPath))
    }
}
