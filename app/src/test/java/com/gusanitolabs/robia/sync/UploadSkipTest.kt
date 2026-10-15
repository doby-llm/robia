package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentColorRole
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.InMemoryEmbeddingDao
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UploadSkipTest {
    private val photo = GarmentPhotoRecord(
        garmentId = "g1",
        localUri = "content://g1",
        blobPath = "photos/g1/0123456789abcdef.webp",
        mimeType = "image/webp",
        contentHash = "hash-1",
    )

    /** The local side exactly as export builds it: [ClothingItemEntity.toPhotoRecord] carries no content or source hash. */
    private fun exportedPhoto(photoUri: String? = "content://g1", guarded: Boolean = false): GarmentPhotoRecord? = ClothingItemEntity(
        id = "g1",
        name = "g1",
        notes = "",
        photoUri = photoUri,
        fitValue = null,
        colorMetrics = ColorMetricsEntity(null, null, null, null, null, null, null, null, null, null),
        isFavorite = false,
        isArchived = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        photoRestoreGuarded = guarded,
    ).toPhotoRecord()

    @Test
    fun photoFreeEquivalentSnapshotWithNothingPendingIsSkipped() {
        val remote = snapshotWithGarments("g1")
        val local = remote.copy(metadata = remote.metadata.copy(revision = 9L, writerDeviceId = "this-phone"))

        assertTrue(local.remoteAlreadyHolds(remote, hasPendingEmbeddings = false, hasPendingCloudDeletion = false))
    }

    @Test
    fun garmentDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1")

        assertFalse(snapshotWithGarments("g1", "g2").remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.remoteAlreadyHolds(snapshotWithGarments("g1", "g2"), false, false))
    }

    @Test
    fun tombstoneOnlyDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1")
        val local = remote.copy(tombstones = listOf(SyncTombstoneRecord("garment", "g2", deletedAtEpochMillis = 5L, revision = 5L)))

        assertFalse(local.remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.remoteAlreadyHolds(local, false, false))
    }

    @Test
    fun tagDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1").copy(garmentTags = listOf(GarmentTagMappingRecord("g1", "tag-a")))
        val local = remote.copy(garmentTags = listOf(GarmentTagMappingRecord("g1", "tag-b")))

        assertFalse(local.remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun taxonomyDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1")
        val local = remote.copy(
            taxonomies = WardrobeTaxonomySnapshot(tags = listOf(TagSyncRecord("tag-a", "category", "Coats", sortOrder = 0, isSystem = false))),
        )

        assertFalse(local.remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun paletteDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1").copy(
            garmentColors = listOf(GarmentColorMappingRecord("g1", GarmentColorRole.Primary, "blue", null, "p1", "Blue", "#0000FF")),
        )
        val local = remote.copy(
            garmentColors = listOf(GarmentColorMappingRecord("g1", GarmentColorRole.Primary, "red", null, "p2", "Red", "#FF0000")),
        )

        assertFalse(local.remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun schemaOrWardrobeDifferenceKeepsTheUpload() {
        val remote = snapshotWithGarments("g1")
        val olderSchema = remote.copy(metadata = remote.metadata.copy(schemaVersion = remote.metadata.schemaVersion - 1))
        val otherWardrobe = remote.copy(metadata = remote.metadata.copy(wardrobeId = "other-wardrobe"))

        assertFalse(remote.remoteAlreadyHolds(olderSchema, false, false))
        assertFalse(remote.remoteAlreadyHolds(otherWardrobe, false, false))
    }

    @Test
    fun pendingVectorsKeepTheUpload() {
        val remote = snapshotWithGarments("g1")

        assertFalse(remote.remoteAlreadyHolds(remote, hasPendingEmbeddings = true, hasPendingCloudDeletion = false))
    }

    @Test
    fun pendingCloudDeletionKeepsTheUpload() {
        val remote = snapshotWithGarments("g1")

        assertFalse(remote.remoteAlreadyHolds(remote, hasPendingEmbeddings = false, hasPendingCloudDeletion = true))
    }

    @Test
    fun photoBearingWardrobeIsNeverSkippedEvenWhenTheRecordsMatch() {
        val local = snapshotWithGarments("g1").copy(photos = listOfNotNull(exportedPhoto()))
        val remote = local.copy(photos = listOf(photo.copy(garmentId = "g1", localUri = "content://g1", contentHash = "hash-1", sourceHash = "hash-1")))

        assertFalse(local.remoteAlreadyHolds(remote, false, false))
    }

    @Test
    fun guardedPhotoOnEitherSideKeepsTheUpload() {
        val guarded = photo.copy(restoreFailureCategory = "remote_photo_transient")
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))

        assertFalse(remote.copy(photos = listOf(guarded)).remoteAlreadyHolds(remote, false, false))
        assertFalse(remote.remoteAlreadyHolds(remote.copy(photos = listOf(guarded)), false, false))
    }

    @Test
    fun unchangedPhotoFreeCycleWritesNothingToDriveAndReportsItsOutcome() = runBlocking {
        val snapshot = snapshotWithGarments("g1")
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
        val cycle = requireNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Success, cycle.result)
        assertEquals(1, cycle.pendingAtStart)
        assertNull(cycle.errorClass)
    }

    @Test
    fun unchangedPhotoBearingCycleStillUploads() = runBlocking {
        val remote = snapshotWithGarments("g1").copy(photos = listOf(photo))
        val local = snapshotWithGarments("g1").copy(photos = listOfNotNull(exportedPhoto()))
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
    fun pendingCloudDeletionCycleUploadsAnUnchangedSnapshot() = runBlocking {
        val snapshot = snapshotWithGarments("g1")
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1"); pendingCloudDeletion = true }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshot))
        val processor = newTestProcessor(wardrobe = wardrobe, snapshots = FakeWardrobeSnapshotStore(local = snapshot), drive = drive)

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
    }

    @Test
    fun pendingEmbeddingCycleUploadsAnUnchangedSnapshot() = runBlocking {
        val snapshot = snapshotWithGarments("g1")
        val embeddings = EmbeddingRepository(InMemoryEmbeddingDao())
        embeddings.upsert("g1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshot))
        val processor = WardrobeSyncOutboxProcessor(
            settingsRepository = FakeSettingsRepository(),
            wardrobeRepository = wardrobe,
            snapshotRepository = FakeWardrobeSnapshotStore(local = snapshot),
            driveRepository = drive,
            syncWorkScheduler = FakeSyncWorkScheduler(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
            embeddingRepository = embeddings,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
    }

    @Test
    fun guardedLocalPhotoCycleUploadsAnUnchangedSnapshot() = runBlocking {
        val guardedLocal = snapshotWithGarments("g1").copy(photos = listOfNotNull(exportedPhoto(photoUri = null, guarded = true)))
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(guardedLocal))
        val processor = newTestProcessor(wardrobe = wardrobe, snapshots = FakeWardrobeSnapshotStore(local = guardedLocal), drive = drive)

        processor.runOnce(SyncTrigger.Foreground)

        assertEquals(1, drive.upserts.size)
    }

    @Test
    fun lastCycleCarriesTheDriveFailureClassAndMessage() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(manifest = DriveSyncResult.Failure(IOException("network down")))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("g1")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Retry, processor.runOnce(SyncTrigger.Foreground))

        val cycle = requireNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Retry, cycle.result)
        assertEquals("IOException", cycle.errorClass)
        assertEquals("network down", cycle.errorMessage)
        assertEquals("Queued", wardrobe.statusOf("g1"))
    }

    @Test
    fun lastCycleRecordsAnExceptionThrownDuringTheCycle() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository().apply { manifestThrows = IllegalStateException("boom") }
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("g1")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Retry, processor.runOnce(SyncTrigger.Foreground))

        val cycle = requireNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Retry, cycle.result)
        assertEquals("IllegalStateException", cycle.errorClass)
        assertEquals("boom", cycle.errorMessage)
        assertEquals("Queued", wardrobe.statusOf("g1"))
    }

    @Test
    fun conflictEarlyReturnReplacesThePreviousCycleSummary() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("g1")))
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("g1")),
            drive = drive,
        )
        processor.runOnce(SyncTrigger.Foreground)
        assertEquals(SyncRunResult.Success, requireNotNull(processor.state.first().lastCycle).result)

        wardrobe.addQueued("g2")
        settings.setSyncConflict(true, 3L)
        assertEquals(SyncRunResult.Conflict, processor.runOnce(SyncTrigger.Foreground))

        val cycle = requireNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Conflict, cycle.result)
        assertEquals(1, cycle.pendingAtStart)
        assertNull(cycle.errorClass)
    }

    @Test
    fun idleCycleRecordsASuccessSummaryWithNothingPending() = runBlocking {
        val processor = newTestProcessor()

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        val cycle = requireNotNull(processor.state.first().lastCycle)
        assertEquals(SyncRunResult.Success, cycle.result)
        assertEquals(0, cycle.pendingAtStart)
    }

    @Test
    fun cycleRecoversStaleClaimsAtTheCurrentTime() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("g1") }
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("g1")),
            drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("g1"))),
        )
        val before = System.currentTimeMillis()

        processor.runOnce(SyncTrigger.Foreground)

        // Init recovers at now minus the 15 minute timeout; the cycle recovers at the current time.
        val cycleThreshold = wardrobe.staleRecoveryThresholds.last()
        assertTrue(cycleThreshold >= before)
        assertTrue(cycleThreshold <= System.currentTimeMillis())
    }
}
