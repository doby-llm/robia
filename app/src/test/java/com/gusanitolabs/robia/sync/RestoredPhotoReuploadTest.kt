package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A restored photo's local file holds the remote blob's WebP bytes. The planner must reuse that blob when the bytes
 * still match, upload an edited photo, and upload when the remote hash is unknown.
 */
class RestoredPhotoReuploadTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()

    @Test
    fun photoRestoredFromTheRemoteBlobIsNotReuploaded() = runBlocking {
        val remote = uploadedOnce("g1")
        // Restore wrote the downloaded blob bytes to the local file, so the local hash is the WebP's hash.
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1"
        val uploadsBefore = api.blobUploads.get()

        val outcome = upload(remote)

        assertEquals(0, api.blobUploads.get() - uploadsBefore)
        assertEquals(remote.blobPath, outcome.uploadedPhotos.single().blobPath)
    }

    @Test
    fun locallyEditedRestoredPhotoIsUploaded() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1 edited on this phone"
        val uploadsBefore = api.blobUploads.get()

        val outcome = upload(remote)

        assertEquals(1, api.blobUploads.get() - uploadsBefore)
        assertNotEquals(remote.blobPath, outcome.uploadedPhotos.single().blobPath)
    }

    @Test
    fun photoWithUnknownRemoteContentHashIsUploaded() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1"
        val unknownHash = remote.toLastUploadedPhoto().copy(contentHash = null)
        val uploadsBefore = api.blobUploads.get()

        upload(remote, previousOverride = unknownHash)

        assertEquals(1, api.blobUploads.get() - uploadsBefore)
    }

    @Test
    fun photoWhoseRemoteBlobIsMissingIsUploaded() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1"
        api.files.remove(remote.blobPath)
        val uploadsBefore = api.blobUploads.get()

        upload(remote)

        assertEquals(1, api.blobUploads.get() - uploadsBefore)
    }

    @Test
    fun unreadableLocalPhotoKeepsItsRecordWithoutAnUpload() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources.remove(localPhoto("g1").localUri)
        val uploadsBefore = api.blobUploads.get()

        val outcome = upload(remote)

        assertEquals(0, api.blobUploads.get() - uploadsBefore)
        assertEquals(remote.blobPath, outcome.uploadedPhotos.single().blobPath)
    }

    /** First upload of one photo: the record it leaves in Drive is what a restore brings back. */
    private suspend fun uploadedOnce(garmentId: String): GarmentPhotoRecord {
        val photo = localPhoto(garmentId)
        phone.sources[photo.localUri] = "pixels-$garmentId"
        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = listOf(photo)), remote = null, previous = emptyMap())
        return (result as DriveSyncResult.Success).value.uploadedPhotos.single()
    }

    /** Upload after restore: the remote record is the previous state, the local file is whatever restore wrote. */
    private suspend fun upload(remote: GarmentPhotoRecord, previousOverride: LastUploadedPhoto? = null): PhotoUploadOutcome {
        val previous = previousOverride ?: remote.toLastUploadedPhoto()
        val result = phone.pipeline(api).upload(
            "token",
            WardrobeSyncSnapshot(photos = listOf(localPhoto(remote.garmentId))),
            remote = WardrobeSyncSnapshot(photos = listOf(remote)),
            previous = mapOf(remote.garmentId to previous),
        )
        return (result as DriveSyncResult.Success).value
    }
}
