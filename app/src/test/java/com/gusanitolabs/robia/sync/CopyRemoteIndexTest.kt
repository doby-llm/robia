package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-D02-3: a kept remote record is claimed only when the restore can find its blob; otherwise the garment stays
 * protected, nothing under it is purged, and it is reported once with a PhotoUploadFailure (attention).
 */
class CopyRemoteIndexTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()
    private val recordBlobPath = "photos/g1/0123456789abcdef.webp"
    private val olderBlobPath = "photos/g1/fedcba9876543210.webp"
    private val otherBlobPath = "photos/g1/aaaaaaaaaaaaaaaa.webp"
    private val guarded = localPhoto("g1").copy(localUri = "", restoreFailureCategory = "local_photo_missing")

    @Test
    fun exactBlobPresentIsKeptAsItIs() = runBlocking {
        api.files[recordBlobPath] = "remote".toByteArray()
        val remoteRecord = guarded.copy(blobPath = recordBlobPath, contentHash = "hash-g1")
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), WardrobeSyncSnapshot(photos = listOf(remoteRecord)), previous = emptyMap())

        val success = outcome as DriveSyncResult.Success
        assertEquals(listOf(recordBlobPath), success.value.uploadedPhotos.map { photo -> photo.blobPath })
        assertTrue(success.value.manifest.photoUploadFailures.isEmpty())
        assertTrue(reported.isEmpty())
    }

    @Test
    fun exactBlobMissingButOneLegacyBlobUnderTheGarmentIsKeptAndRelinkedToIt() = runBlocking {
        api.files[olderBlobPath] = "older".toByteArray()
        val remote = WardrobeSyncSnapshot(photos = listOf(guarded.copy(blobPath = recordBlobPath, contentHash = "hash-g1")))
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = emptyMap())

        val success = outcome as DriveSyncResult.Success
        assertEquals(listOf(olderBlobPath), success.value.uploadedPhotos.map { photo -> photo.blobPath })
        assertTrue(success.value.manifest.photoUploadFailures.isEmpty())
        assertTrue(reported.isEmpty())
        assertTrue(api.files.containsKey(olderBlobPath))
    }

    @Test
    fun noBlobResolvesSoTheGarmentIsUnclaimedProtectedAndReportedOnce() = runBlocking {
        val remote = WardrobeSyncSnapshot(photos = listOf(guarded.copy(blobPath = recordBlobPath, contentHash = "hash-g1")))
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = emptyMap())

        val success = outcome as DriveSyncResult.Success
        assertTrue(success.value.uploadedPhotos.none { photo -> photo.garmentId == "g1" })
        assertEquals(listOf("g1"), success.value.manifest.photoUploadFailures.map { failure -> failure.garmentId })
        assertEquals(listOf("g1"), reported)
    }

    @Test
    fun severalLegacyBlobsUnderTheGarmentAreNotClaimedAndNoneOfThemIsPurged() = runBlocking {
        api.files[olderBlobPath] = "older".toByteArray()
        api.files[otherBlobPath] = "other".toByteArray()
        val remote = WardrobeSyncSnapshot(photos = listOf(guarded.copy(blobPath = recordBlobPath, contentHash = "hash-g1")))
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = emptyMap())

        val success = outcome as DriveSyncResult.Success
        assertTrue(success.value.uploadedPhotos.none { photo -> photo.garmentId == "g1" })
        assertEquals(listOf("g1"), success.value.manifest.photoUploadFailures.map { failure -> failure.garmentId })
        assertEquals(listOf("g1"), reported)
        assertTrue(api.files.containsKey(olderBlobPath))
        assertTrue(api.files.containsKey(otherBlobPath))
    }

    @Test
    fun previousLiveBlobIsTriedWhenTheRemoteRecordCannotBeResolved() = runBlocking {
        api.files[olderBlobPath] = "older".toByteArray()
        api.files[otherBlobPath] = "other".toByteArray()
        val remote = WardrobeSyncSnapshot(photos = listOf(guarded.copy(blobPath = recordBlobPath, contentHash = "hash-g1")))
        val previous = mapOf("g1" to LastUploadedPhoto("g1", "hash", olderBlobPath, "hash-g1", 5L, "image/webp"))
        val reported = mutableListOf<String>()

        val outcome = pipeline(reported).upload("token", WardrobeSyncSnapshot(photos = listOf(guarded)), remote, previous = previous)

        val success = outcome as DriveSyncResult.Success
        assertEquals(listOf(olderBlobPath), success.value.uploadedPhotos.map { photo -> photo.blobPath })
        assertTrue(success.value.manifest.photoUploadFailures.isEmpty())
        assertTrue(reported.isEmpty())
    }

    private fun pipeline(reported: MutableList<String>) = PhotoUploadPipeline(
        api = api,
        sourceHashOf = { error("a guarded photo is never hashed") },
        encode = { error("a guarded photo is never encoded") },
        onUnclaimedPhoto = { garmentId -> reported += garmentId },
    )
}
