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

/** V-D02-3: a kept remote record is claimed only when its blob is in Drive; the garment stays protected and is reported once. */
class CopyRemoteIndexTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()
    private val missingBlobPath = "photos/g1/0123456789abcdef.webp"
    private val olderBlobPath = "photos/g1/fedcba9876543210.webp"
    private val guarded = localPhoto("g1").copy(localUri = "", restoreFailureCategory = "local_photo_missing")

    @Test
    fun recordWhoseBlobIsAbsentFromDriveIsNotClaimedButItsGarmentIsProtected() = runBlocking {
        api.files[olderBlobPath] = "older".toByteArray()
        val remote = WardrobeSyncSnapshot(photos = listOf(guarded.copy(blobPath = missingBlobPath, contentHash = "hash-g1")))
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = emptyMap())

        val uploaded = (outcome as DriveSyncResult.Success).value.uploadedPhotos
        assertTrue(uploaded.none { photo -> photo.garmentId == "g1" })
        assertEquals(listOf("g1"), reported)
        assertTrue(api.files.containsKey(olderBlobPath))
    }

    @Test
    fun recordWhoseBlobIsInDriveIsKept() = runBlocking {
        api.files[missingBlobPath] = "remote".toByteArray()
        val remoteRecord = guarded.copy(blobPath = missingBlobPath, contentHash = "hash-g1")
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), WardrobeSyncSnapshot(photos = listOf(remoteRecord)), previous = emptyMap())

        val uploaded: List<GarmentPhotoRecord> = (outcome as DriveSyncResult.Success).value.uploadedPhotos
        assertEquals(listOf(missingBlobPath), uploaded.map { photo -> photo.blobPath })
        assertTrue(reported.isEmpty())
    }

    private fun pipeline(reported: MutableList<String>) = PhotoUploadPipeline(
        api = api,
        sourceHashOf = { error("a guarded photo is never hashed") },
        encode = { error("a guarded photo is never encoded") },
        onUnclaimedPhoto = { garmentId -> reported += garmentId },
    )
}
