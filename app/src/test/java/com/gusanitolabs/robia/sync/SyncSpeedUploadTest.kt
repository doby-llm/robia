package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** REQ-FB-01 (b): fewer calls per photo, bounded concurrency, and no duplicate blob or lost garment. */
class SyncSpeedUploadTest {
    private val api = FakeDriveSnapshotApi(uploadDelayMillis = 50L)
    private val phone = FakePhotoSources()

    @Test
    fun sameNameAndSizeIsNotPostedAgain() = runBlocking {
        val snapshot = garments(1)
        upload(snapshot)
        val blobsBefore = api.blobUploads.get()

        val second = upload(snapshot, previous = emptyMap())

        assertTrue(second is DriveSyncResult.Success)
        assertEquals(blobsBefore, api.blobUploads.get())
        assertEquals(1, api.creates.values.sumOf { it.get() })
    }

    @Test
    fun fullListingWithoutTheNameIsCreatedWithoutALookup() = runBlocking {
        val result = upload(garments(2))

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(0, api.findLookups.get())
        assertEquals(2, api.creates.size)
    }

    @Test
    fun incompleteListingLooksUpEachNameBeforeCreating() = runBlocking {
        api.script("listAll", DriveApiResult.NotFound)

        val result = upload(garments(2))

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(2, api.findLookups.get())
    }

    @Test
    fun failedLookupIsNeverReadAsAbsentAndNeverBecomesAPost() = runBlocking {
        api.script("listAll", DriveApiResult.NotFound)
        val name = blobNameOf("g1")
        api.findFailures[name] = DriveApiResult.Transient(IOException("lookup down"))

        val result = upload(garments(1))

        assertTrue(result is DriveSyncResult.Failure)
        assertNull(api.creates[name])
        assertTrue(!api.files.containsKey(name))
    }

    @Test
    fun concurrentUploadsCreateEachBlobOnceAndKeepEveryGarment() = runBlocking {
        val result = upload(garments(6))

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(6, (result as DriveSyncResult.Success).value.uploadedPhotos.size)
        assertEquals(6, api.creates.size)
        assertTrue(api.creates.values.all { count -> count.get() == 1 })
        assertTrue(api.maxUploadInFlight.get() in 2..UPLOAD_CONCURRENCY)
    }

    @Test
    fun progressIsMonotonicAndEndsAtTheTotal() = runBlocking {
        val seen = mutableListOf<Triple<Int, Int, Int>>()

        upload(garments(6)) { completed, total, reused -> seen += Triple(completed, total, reused) }

        assertEquals(Triple(0, 6, 0), seen.first())
        assertEquals(Triple(6, 6, 0), seen.last())
        assertEquals(7, seen.size)
        assertTrue(seen.zipWithNext().all { (before, after) -> after.first >= before.first })
    }

    @Test
    fun unauthorizedOnOneGarmentCancelsTheSiblingsAndBlocksTheCycle() = runBlocking {
        api.blobFailures[blobNameOf("g1")] = DriveApiResult.Unauthorized

        val result = upload(garments(3))

        assertTrue(result is DriveSyncResult.Blocked)
        assertEquals(0, api.blobUploads.get())
    }

    @Test
    fun transientOnOneGarmentCancelsTheSiblingsAndFailsTheCycle() = runBlocking {
        api.blobFailures[blobNameOf("g1")] = DriveApiResult.Transient(IOException("down"))

        val result = upload(garments(3))

        assertTrue(result is DriveSyncResult.Failure)
        assertEquals(0, api.blobUploads.get())
    }

    @Test
    fun rateLimitDropsTheCycleToOneUploadAtATime() = runBlocking {
        api.rateLimitOnFirstWrite = true

        val result = upload(garments(6))

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(UPLOAD_CONCURRENCY, api.concurrencySeen.first())
        assertEquals(1, api.concurrencySeen.last())
        assertTrue(api.creates.values.all { count -> count.get() == 1 })
    }

    @Test
    fun cancellationStopsTheCycleAndIsNotReturnedAsAResult() = runBlocking {
        var result: DriveSyncResult<PhotoUploadOutcome>? = null
        val snapshot = garments(4)

        val job = launch { result = phone.pipeline(api).upload("token", snapshot, remote = null, previous = emptyMap()) }
        delay(20L)
        job.cancelAndJoin()

        assertNull(result)
    }

    @Test
    fun failedCycleRetryUploadsNoBlobAlreadyListed() = runBlocking {
        val snapshot = garments(3)
        api.snapshotFailure = DriveApiResult.Transient(IOException("snapshot down"))
        val first = upload(snapshot)
        val uploadedByFirstCycle = api.blobUploads.get()
        api.snapshotFailure = null

        val second = upload(snapshot)

        assertTrue(first is DriveSyncResult.Failure)
        assertTrue(second is DriveSyncResult.Success)
        assertEquals(3, uploadedByFirstCycle)
        assertEquals(uploadedByFirstCycle, api.blobUploads.get())
    }

    @Test
    fun retriedRateLimitIsReportedToTheCycleAndWaitsForRetryAfter() = runBlocking {
        val gate = DriveRateLimitGate(maxConcurrency = 3)
        val delays = mutableListOf<Long>()
        var calls = 0

        val result = withContext(gate) {
            withRetry(sleeper = { delayMillis -> delays += delayMillis }, random = { 0.5 }) {
                if (calls++ == 0) DriveApiResult.Transient(null, retryAfterMillis = 5_000L, rateLimited = true) else DriveApiResult.Success("ok")
            }
        }

        assertTrue(result is DriveApiResult.Success)
        assertEquals(listOf(5_000L), delays)
        assertEquals(1, gate.concurrency)
    }

    @Test
    fun ordinaryTransientKeepsTheCycleConcurrent() = runBlocking {
        val gate = DriveRateLimitGate(maxConcurrency = 3)
        var calls = 0

        withContext(gate) {
            withRetry(sleeper = {}, random = { 0.5 }) {
                if (calls++ == 0) DriveApiResult.Transient(null) else DriveApiResult.Success("ok")
            }
        }

        assertEquals(3, gate.concurrency)
    }

    @Test
    fun indexKnowsAbsenceOnlyFromACompleteListing() {
        val listed = DriveFileMetadata(id = "id-a", name = "a")

        assertTrue(DriveFileIndex(listOf(listed), complete = true).knownAbsent("b"))
        assertTrue(!DriveFileIndex(listOf(listed), complete = true).knownAbsent("a"))
        assertTrue(!DriveFileIndex(listOf(listed)).knownAbsent("b"))
    }

    private fun garments(count: Int): WardrobeSyncSnapshot = WardrobeSyncSnapshot(
        photos = (1..count).map { index ->
            localPhoto("g$index").also { photo -> phone.sources[photo.localUri] = "pixels-g$index" }
        },
    )

    private fun blobNameOf(garmentId: String): String {
        val webp = "webp:pixels-$garmentId".toByteArray()
        return PhotoBlobKeys.driveKey(garmentId, PhotoBlobKeys.hash16(webp))
    }

    private suspend fun upload(
        snapshot: WardrobeSyncSnapshot,
        previous: Map<String, LastUploadedPhoto> = emptyMap(),
        onPhotoProgress: (completedPhotos: Int, totalPhotos: Int, reusedPhotos: Int) -> Unit = { _, _, _ -> },
    ): DriveSyncResult<PhotoUploadOutcome> = phone.pipeline(api).upload(
        "token",
        snapshot,
        remote = null,
        previous = previous,
        onPhotoProgress = onPhotoProgress,
    )
}
