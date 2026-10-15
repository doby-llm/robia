package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSkipTest {
    private val photo = GarmentPhotoRecord(
        garmentId = "g1",
        localUri = "content://g1",
        blobPath = "photos/g1/0123456789abcdef.webp",
        mimeType = "image/webp",
        contentHash = "hash-1",
    )

    @Test
    fun equivalentSnapshotWithNothingPendingIsSkipped() {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))
        val local = remote.copy(metadata = remote.metadata.copy(revision = 9L, writerDeviceId = "this-phone"))

        assertTrue(local.remoteAlreadyHolds(remote, hasPendingEmbeddings = false, hasPendingCloudDeletion = false))
    }

    @Test
    fun anyDifferenceInUserDataKeepsTheUpload() {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))

        assertFalse(snapshotWithGarments("g1", "g2").copy(photos = listOf(photo)).remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.copy(photos = listOf(photo.copy(contentHash = "hash-2"))).remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.copy(photos = emptyList()).remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun pendingVectorsKeepTheUpload() {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))

        assertFalse(remote.remoteAlreadyHolds(remote, hasPendingEmbeddings = true, hasPendingCloudDeletion = false))
    }

    @Test
    fun pendingCloudDeletionKeepsTheUpload() {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))

        assertFalse(remote.remoteAlreadyHolds(remote, hasPendingEmbeddings = false, hasPendingCloudDeletion = true))
    }

    @Test
    fun guardedPhotoOnEitherSideKeepsTheUpload() {
        val guarded = photo.copy(restoreFailureCategory = "remote_photo_transient")
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))

        assertFalse(remote.copy(photos = listOf(guarded)).remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.remoteAlreadyHolds(remote.copy(photos = listOf(guarded)), false, false))
    }

    @Test
    fun legacyRemotePhotoKeyKeepsTheUpload() {
        val legacy = photo.copy(blobPath = "photos/g1/original")
        val remote = snapshotWithGarments("g1").copy(photos = listOf(legacy))

        assertFalse(remote.remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun unchangedCycleWritesNothingToDriveAndReportsItsOutcome() = runBlocking {
        val snapshot = snapshotWithGarments("g1").copy(photos = listOf(photo))
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshot))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshot),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertTrue(drive.upserts.isEmpty())
        assertEquals(1, drive.recordsFetches)
        assertEquals("Synced", wardrobe.statusOf("g1"))
        val cycle = assertNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Success, cycle.result)
        assertEquals(1, cycle.pendingAtStart)
        assertNull(cycle.errorClass)
    }

    @Test
    fun changedCycleStillUploads() = runBlocking {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))
        val local = snapshotWithGarments("g1").copy(photos = listOf(photo.copy(contentHash = "hash-2")))
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(remote))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = local),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
    }

    @Test
    fun staleRunningRecoveryRunsUnderTheMutexAndSkipsGuardedRows() {
        // The SQL itself is executed in data/StaleClaimRecoveryTest; this guards the call order and the aggregate wiring.
        val processor = java.io.File("src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt").readText()
        val runCycle = processor.substringAfter("private suspend fun runCycle(").substringBefore("private suspend fun executeDirection(")
        assertTrue(runCycle.indexOf("recoverStaleRunningSyncWork(System.currentTimeMillis())") in 0 until runCycle.indexOf("pendingGarmentSyncWork()"))

        val dao = java.io.File("src/main/java/com/gusanitolabs/robia/data/local/Dao.kt").readText()
        val aggregate = dao.substringAfter("suspend fun recoverStaleRunningSyncWork(").substringBefore("/**")
        assertTrue(aggregate.contains("recoverStaleUnguardedGarmentSyncWork(staleBeforeEpochMillis)"))
    }

    @Test
    fun cycleWithoutPendingWorkLeavesLastCycleUnset() = runBlocking {
        val processor = newTestProcessor()

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertNull(processor.state.first().lastCycle)
    }
}
