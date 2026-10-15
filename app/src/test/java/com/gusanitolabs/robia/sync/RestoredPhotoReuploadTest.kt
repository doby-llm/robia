package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    @Test
    fun listingFailureFailsTheCycleWithoutReuseOrUpload() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1"
        // Counters after the setup upload: the cycle under test must not add to them.
        val blobUploadsBefore = api.blobUploads.get()
        val snapshotUploadsBefore = api.snapshotUploads.get()
        // The listing runs first, so the hash and encode lambdas must never be reached.
        val listingDown = object : DriveSnapshotApi by api {
            override suspend fun listAll(accessToken: String): DriveApiResult<List<DriveFileMetadata>> =
                DriveApiResult.Failure(IOException("Drive listing unavailable"))
        }
        val pipeline = PhotoUploadPipeline(
            api = listingDown,
            sourceHashOf = { error("hashed before the listing") },
            encode = { error("encoded before the listing") },
        )

        val result = pipeline.upload(
            "token",
            WardrobeSyncSnapshot(photos = listOf(localPhoto("g1"))),
            remote = WardrobeSyncSnapshot(photos = listOf(remote)),
            previous = mapOf("g1" to remote.toLastUploadedPhoto()),
        )

        assertTrue("expected the cycle to fail, but was $result", result is DriveSyncResult.Failure)
        assertEquals(0, api.blobUploads.get() - blobUploadsBefore)
        assertEquals(0, api.snapshotUploads.get() - snapshotUploadsBefore)
    }

    @Test
    fun identicalBlobBytesAreReusedEvenWhenStoredSourceHashIsStale() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "webp:pixels-g1"
        val staleSourceHash = remote.toLastUploadedPhoto().copy(sourceHash = "stale-source-hash")
        val uploadsBefore = api.blobUploads.get()

        val outcome = upload(remote, previousOverride = staleSourceHash)

        assertEquals(0, api.blobUploads.get() - uploadsBefore)
        assertEquals(remote.blobPath, outcome.uploadedPhotos.single().blobPath)
    }

    @Test
    fun editedPhotoWithAStaleRecordIsUploadedNotReused() = runBlocking {
        val remote = uploadedOnce("g1")
        phone.sources[localPhoto("g1").localUri] = "pixels-g1 v2"
        val uploadsBefore = api.blobUploads.get()

        val outcome = upload(remote)

        assertEquals(1, api.blobUploads.get() - uploadsBefore)
        val uploaded = outcome.uploadedPhotos.single()
        assertNotEquals(remote.blobPath, uploaded.blobPath)
        assertTrue(api.files.containsKey(uploaded.blobPath))
    }

    @Test
    fun reusedBlobsSurvivePurgeWhileTheReplacedBlobIsDeleted() = runBlocking {
        val first = uploadedFirstTime(listOf("g1", "g2"))
        val g1Old = first.single { record -> record.garmentId == "g1" }
        val g2 = first.single { record -> record.garmentId == "g2" }
        // g1 was edited on this phone; g2 was restored and its local bytes are its blob's WebP.
        phone.sources[localPhoto("g1").localUri] = "pixels-g1 v2"
        phone.sources[localPhoto("g2").localUri] = "webp:pixels-g2"

        val result = phone.pipeline(api).upload(
            "token",
            WardrobeSyncSnapshot(photos = listOf(localPhoto("g1"), localPhoto("g2"))),
            remote = WardrobeSyncSnapshot(photos = first),
            previous = first.associate { record -> record.garmentId to record.toLastUploadedPhoto() },
        )

        val uploaded = (result as DriveSyncResult.Success).value.uploadedPhotos
        val g1New = uploaded.single { record -> record.garmentId == "g1" }
        val g2Reused = uploaded.single { record -> record.garmentId == "g2" }
        assertEquals(g2.blobPath, g2Reused.blobPath)
        assertTrue(api.files.containsKey(g2.blobPath))
        assertTrue(api.files.containsKey(g1New.blobPath))
        assertFalse(api.files.containsKey(g1Old.blobPath))
    }

    /** First upload of one photo: the record it leaves in Drive is what a restore brings back. */
    private suspend fun uploadedOnce(garmentId: String): GarmentPhotoRecord = uploadedFirstTime(listOf(garmentId)).single()

    /** First upload of several photos, each with its own pixels, so every blob is a fresh WebP. */
    private suspend fun uploadedFirstTime(garmentIds: List<String>): List<GarmentPhotoRecord> {
        val photos = garmentIds.map { garmentId ->
            val photo = localPhoto(garmentId)
            phone.sources[photo.localUri] = "pixels-$garmentId"
            photo
        }
        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = photos), remote = null, previous = emptyMap())
        return (result as DriveSyncResult.Success).value.uploadedPhotos
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
