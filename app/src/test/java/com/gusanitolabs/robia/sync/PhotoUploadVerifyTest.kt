package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.media.ImageBlob
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import com.gusanitolabs.robia.sync.fakes.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-D02-2: Drive must store exactly the bytes sent; a size or checksum mismatch keeps the previous record and purges nothing. */
class PhotoUploadVerifyTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()

    @Test
    fun sizeMismatchKeepsThePreviousRecordAndNeverPurgesItsBlob() = runBlocking {
        val previous = uploadFirstTime()
        phone.sources[localPhoto("g1").localUri] = "edited"

        val outcome = uploadThrough(lyingApi { info -> info.copy(size = info.size?.plus(1)) }, previous)

        assertEquals(previous.blobPath, outcome.uploadedPhotos.single().blobPath)
        assertEquals(listOf("g1"), outcome.manifest.photoUploadFailures.map { it.garmentId })
        assertTrue(api.files.containsKey(previous.blobPath))
    }

    @Test
    fun checksumMismatchKeepsThePreviousRecordAndNeverPurgesItsBlob() = runBlocking {
        val previous = uploadFirstTime()
        phone.sources[localPhoto("g1").localUri] = "edited"

        val outcome = uploadThrough(lyingApi { info -> info.copy(sha256 = "0".repeat(64)) }, previous)

        assertEquals(previous.blobPath, outcome.uploadedPhotos.single().blobPath)
        assertEquals(listOf("g1"), outcome.manifest.photoUploadFailures.map { it.garmentId })
        assertTrue(api.files.containsKey(previous.blobPath))
    }

    @Test
    fun matchingSizeAndChecksumAreAccepted() = runBlocking {
        val previous = uploadFirstTime()
        phone.sources[localPhoto("g1").localUri] = "edited"

        val outcome = uploadThrough(api, previous)

        assertTrue(outcome.uploadedPhotos.single().blobPath != previous.blobPath)
        assertTrue(outcome.manifest.photoUploadFailures.isEmpty())
    }

    private suspend fun uploadFirstTime(): GarmentPhotoRecord {
        phone.sources[localPhoto("g1").localUri] = "pixels-g1"
        val result = phone.pipeline(api).upload("token", WardrobeSyncSnapshot(photos = listOf(localPhoto("g1"))), remote = null, previous = emptyMap())
        return (result as DriveSyncResult.Success).value.uploadedPhotos.single()
    }

    private suspend fun uploadThrough(drive: com.gusanitolabs.robia.sync.DriveSnapshotApi, previous: GarmentPhotoRecord): PhotoUploadOutcome {
        val pipeline = PhotoUploadPipeline(
            api = drive,
            sourceHashOf = { photo -> sha256Hex(phone.sources.getValue(photo.localUri).toByteArray()).take(16) },
            encode = { photo ->
                val webp = "webp:${phone.sources.getValue(photo.localUri)}".toByteArray()
                ImageBlob(bytes = webp, mimeType = "image/webp", contentHash = sha256Hex(webp), decodedWidth = 2, decodedHeight = 2)
            },
        )
        val result = pipeline.upload(
            "token",
            WardrobeSyncSnapshot(photos = listOf(localPhoto("g1"))),
            remote = null,
            previous = mapOf("g1" to previous.toLastUploadedPhoto()),
        )
        return (result as DriveSyncResult.Success).value
    }

    /** Stores the bytes through [api] but reports [answer] of what Drive stored. */
    private fun lyingApi(answer: (UploadedBlobInfo) -> UploadedBlobInfo): DriveSnapshotApi = object : DriveSnapshotApi by api {
        override suspend fun upsertBlob(
            accessToken: String,
            blobPath: String,
            bytes: ByteArray,
            mimeType: String?,
            fileId: String?,
            knownAbsent: Boolean,
        ): DriveApiResult<UploadedBlobInfo> {
            val stored = api.upsertBlob(accessToken, blobPath, bytes, mimeType, fileId, knownAbsent) as DriveApiResult.Success
            return DriveApiResult.Success(answer(stored.value))
        }
    }
}
