package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.media.ImageBlob
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.localPhoto
import com.gusanitolabs.robia.sync.fakes.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DriveAuthRetryTest {
    private val api = FakeDriveSnapshotApi()
    private val sources = mutableMapOf<String, String>()

    @Test
    fun unauthorizedThenSuccessRefreshesTheTokenOnceAndSucceeds() = runBlocking {
        api.script("listAll", DriveApiResult.Unauthorized)
        val auth = DriveCallAuth("stale-token", refresh = api::refreshAccessToken)

        val result = auth.call { token -> api.listAll(token) }

        assertTrue(result is DriveApiResult.Success)
        assertEquals(1, api.authorizeCalls.get())
        assertEquals(listOf("stale-token", FakeDriveSnapshotApi.FRESH_TOKEN), api.tokensSeen.toList())
        assertEquals(FakeDriveSnapshotApi.FRESH_TOKEN, auth.accessToken)
    }

    @Test
    fun unauthorizedTwiceIsRetriedOnceAndThenReturnedAsUnauthorized() = runBlocking {
        api.script("listAll", DriveApiResult.Unauthorized, DriveApiResult.Unauthorized)
        val auth = DriveCallAuth("stale-token", refresh = api::refreshAccessToken)

        val result = auth.call { token -> api.listAll(token) }

        assertEquals(DriveApiResult.Unauthorized, result)
        assertEquals(1, api.authorizeCalls.get())
        assertEquals(2, api.tokensSeen.size)
    }

    @Test
    fun noFreshTokenLeavesTheUnauthorizedResultWithoutAnyRetry() = runBlocking {
        api.script("listAll", DriveApiResult.Unauthorized)
        val auth = DriveCallAuth("stale-token", refresh = { null })

        val result = auth.call { token -> api.listAll(token) }

        assertEquals(DriveApiResult.Unauthorized, result)
        assertEquals(1, api.tokensSeen.size)
    }

    @Test
    fun otherFailuresAreNotRetriedWithAFreshToken() = runBlocking {
        api.script("listAll", DriveApiResult.Transient(IOException("HTTP 503"), retryAfterMillis = 2_000L))
        val auth = DriveCallAuth("token", refresh = api::refreshAccessToken)

        val result = auth.call { token -> api.listAll(token) }

        assertTrue(result is DriveApiResult.Transient)
        assertEquals(0, api.authorizeCalls.get())
    }

    @Test
    fun snapshotUploadAnsweredWith401IsRetriedOnceWithAFreshToken() = runBlocking {
        api.script("upsertSnapshot", DriveApiResult.Unauthorized)

        val result = pipeline().upload("stale-token", WardrobeSyncSnapshot(), remote = null, previous = emptyMap())

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(1, api.authorizeCalls.get())
        // The first PUT was answered 401 and stored nothing; the retry is the one upload Drive counts.
        assertEquals(1, api.snapshotUploads.get())
    }

    @Test
    fun photoUploadAnsweredWith401IsRetriedAndTheRunContinues() = runBlocking {
        val photo = localPhoto("g1").also { sources[it.localUri] = "photo 1" }
        api.script("upsertBlob", DriveApiResult.Unauthorized)

        val result = pipeline().upload("stale-token", WardrobeSyncSnapshot(photos = listOf(photo)), remote = null, previous = emptyMap())

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(1, api.authorizeCalls.get())
        assertEquals(1, api.blobUploads.get())
    }

    @Test
    fun photoUploadAnsweredWith401TwiceEndsTheRunBlocked() = runBlocking {
        val photo = localPhoto("g1").also { sources[it.localUri] = "photo 1" }
        api.script("upsertBlob", DriveApiResult.Unauthorized, DriveApiResult.Unauthorized)

        val result = pipeline().upload("stale-token", WardrobeSyncSnapshot(photos = listOf(photo)), remote = null, previous = emptyMap())

        assertTrue(result is DriveSyncResult.Blocked)
        assertEquals(1, api.authorizeCalls.get())
        assertEquals(0, api.snapshotUploads.get())
    }

    private fun pipeline(): PhotoUploadPipeline = PhotoUploadPipeline(
        api = api,
        sourceHashOf = { photo -> sha256Hex(sources.getValue(photo.localUri).toByteArray()).take(16) },
        encode = { photo: GarmentPhotoRecord ->
            val webp = "webp:${sources.getValue(photo.localUri)}".toByteArray()
            ImageBlob(bytes = webp, mimeType = "image/webp", contentHash = sha256Hex(webp), decodedWidth = 2, decodedHeight = 2)
        },
        refreshAccessToken = api::refreshAccessToken,
    )
}
