package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import com.gusanitolabs.robia.sync.fakes.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UploadChangeDetectionTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()

    @Test
    fun unchangedGarmentsAreNotReuploaded() = runBlocking {
        val photos = seedGarments(30)
        val first = upload(photos, previous = emptyMap())
        val previous = first.uploaded().associate { photo -> photo.garmentId to photo.toLastUploadedPhoto() }

        val uploadsBefore = api.blobUploads.get()
        val second = upload(photos, previous)

        assertEquals(0, api.blobUploads.get() - uploadsBefore)
        assertEquals(first.uploaded().map(GarmentPhotoRecord::blobPath), second.uploaded().map(GarmentPhotoRecord::blobPath))
    }

    @Test
    fun changedPhotoIsUploadedOnceAndItsOldBlobPurgedAfterTheSnapshot() = runBlocking {
        val photos = seedGarments(3)
        val first = upload(photos, previous = emptyMap())
        val previous = first.uploaded().associate { photo -> photo.garmentId to photo.toLastUploadedPhoto() }
        val oldBlobPath = first.uploaded().single { it.garmentId == "g1" }.blobPath

        phone.sources[localPhoto("g1").localUri] = "edited"
        val uploadsBefore = api.blobUploads.get()
        val second = upload(photos, previous)

        assertEquals(1, api.blobUploads.get() - uploadsBefore)
        val newBlobPath = second.uploaded().single { it.garmentId == "g1" }.blobPath
        assertTrue(newBlobPath != oldBlobPath)
        assertFalse(api.files.containsKey(oldBlobPath))
        assertTrue(api.files.containsKey(newBlobPath))
    }

    @Test
    fun failedSnapshotUploadDeletesNothing() = runBlocking {
        val photos = seedGarments(2)
        val first = upload(photos, previous = emptyMap())
        val previous = first.uploaded().associate { photo -> photo.garmentId to photo.toLastUploadedPhoto() }
        val oldBlobPath = first.uploaded().single { it.garmentId == "g1" }.blobPath

        phone.sources[localPhoto("g1").localUri] = "edited"
        api.snapshotFailure = DriveApiResult.Transient(IOException("network down"))
        val result = phone.pipeline(api).upload("token", snapshotOf(photos), remote = null, previous = previous)

        assertTrue(result is DriveSyncResult.Failure)
        assertTrue(api.files.containsKey(oldBlobPath))
    }

    @Test
    fun legacyLongKeyIsRekeyedToShortKeyAndDeletedAfterTheSnapshot() = runBlocking {
        val photos = seedGarments(1)
        val legacyPath = "photos/g1/original"
        api.files[legacyPath] = "legacy".toByteArray()
        val previous = mapOf(
            "g1" to LastUploadedPhoto(
                garmentId = "g1",
                sourceHash = sha256Hex("pixels-g1".toByteArray()).take(16),
                blobPath = legacyPath,
                contentHash = null,
                byteSize = null,
                mimeType = "image/webp",
            ),
        )

        val result = upload(photos, previous)

        val newBlobPath = result.uploaded().single().blobPath
        assertEquals(PhotoBlobKeys.driveKey("g1", PhotoBlobKeys.hash16("webp:pixels-g1".toByteArray())), newBlobPath)
        assertFalse(api.files.containsKey(legacyPath))
        assertTrue(api.files.containsKey(newBlobPath))
    }

    @Test
    fun planReusesOnlyMatchingShortKeysThatStillExistInDrive() {
        val index = DriveFileIndex(listOf(DriveFileMetadata(id = "id-short", name = "photos/g1/0123456789abcdef.webp")))
        val short = LastUploadedPhoto("g1", "hash", "photos/g1/0123456789abcdef.webp", null, null, "image/webp")

        val actions = planPhotoUploads(
            local = listOf(
                LocalPhotoState.Readable("g1", "hash"),
                LocalPhotoState.Readable("g2", "hash"),
                LocalPhotoState.Readable("g3", "other"),
                LocalPhotoState.Guarded("g4"),
                LocalPhotoState.Unreadable("g5"),
            ),
            previous = mapOf("g1" to short),
            index = index,
        )

        assertEquals(
            listOf(
                PhotoUploadAction.Reuse("g1", short),
                PhotoUploadAction.Upload("g2", "hash"),
                PhotoUploadAction.Upload("g3", "other"),
                PhotoUploadAction.CopyRemote("g4"),
                PhotoUploadAction.Fail("g5"),
            ),
            actions,
        )
    }

    private fun seedGarments(count: Int): List<GarmentPhotoRecord> = (1..count).map { number ->
        val photo = localPhoto("g$number")
        phone.sources[photo.localUri] = "pixels-g$number"
        photo
    }

    private suspend fun upload(photos: List<GarmentPhotoRecord>, previous: Map<String, LastUploadedPhoto>): PhotoUploadOutcome {
        val result = phone.pipeline(api).upload("token", snapshotOf(photos), remote = null, previous = previous)
        return (result as DriveSyncResult.Success).value
    }

    private fun snapshotOf(photos: List<GarmentPhotoRecord>) = WardrobeSyncSnapshot(photos = photos)

    private fun PhotoUploadOutcome.uploaded(): List<GarmentPhotoRecord> = uploadedPhotos.sortedBy(GarmentPhotoRecord::garmentId)
}
