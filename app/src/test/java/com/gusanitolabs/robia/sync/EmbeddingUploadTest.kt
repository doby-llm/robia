package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.data.EmbeddingKey
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.InMemoryEmbeddingDao
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** V-E06b: an embedding-only change is uploaded as a snapshot and marked uploaded only after the upload succeeds. */
class EmbeddingUploadTest {
    /** Computed-at time of each vector this test stores; a test moves it to model a recompute. */
    private var clockMillis = 1_000L
    private val embeddings = EmbeddingRepository(InMemoryEmbeddingDao(), clock = { clockMillis })
    private val drive = FakeDriveWardrobeRepository()
    private val wardrobe = FakeWardrobeRepository()
    private val store = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1"))

    private val scheduler = FakeSyncWorkScheduler()

    /** The clock and the deferred wait are injected: tests move time and release a deferred run explicitly. */
    private fun processor(
        snapshotStore: WardrobeSnapshotStore = store,
        monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
        awaitDeferredRequest: suspend (Long) -> Unit = { delayMillis -> delay(delayMillis) },
    ): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
        settingsRepository = FakeSettingsRepository(),
        wardrobeRepository = wardrobe,
        snapshotRepository = snapshotStore,
        driveRepository = drive,
        syncWorkScheduler = scheduler,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        dispatcher = Dispatchers.Unconfined,
        embeddingRepository = embeddings,
        monotonicMillis = monotonicMillis,
        awaitDeferredRequest = awaitDeferredRequest,
    )

    /** Mirrors what the real exportSnapshot returns once [key]'s vector is stored: the garment carries it. */
    private fun exportWithVector(key: EmbeddingKey) {
        val garment = store.local.garments.single()
        val record = EmbeddingSyncRecord(
            kind = key.kind,
            modelId = EmbeddingRecipe.MODEL_ID,
            recipeId = EmbeddingRecipe.RECIPE_ID,
            dim = EmbeddingRecipe.DIMENSION,
            inputHash = "hash-1",
            computedAtEpochMillis = key.computedAtEpochMillis,
            b64 = Base64.getEncoder().encodeToString(ByteArray(EmbeddingRecipe.DIMENSION * Float.SIZE_BYTES)),
        )
        store.local = store.local.copy(garments = listOf(garment.copy(embeddings = listOf(record))))
    }

    @Test
    fun embeddingOnlyChangeUploadsSnapshotAndMarksRowUploaded() = runBlocking {
        val revisionBefore = store.local.garments.single().revision
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())

        val result = processor().runOnce(SyncTrigger.Foreground)

        assertEquals(SyncRunResult.Success, result)
        assertEquals(1, drive.upserts.size)
        val uploaded = drive.upserts.single().garments.single()
        assertEquals(listOf(EmbeddingKind.IMAGE), uploaded.embeddings.map { it.kind })
        assertEquals(revisionBefore, uploaded.revision)
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
    }

    @Test
    fun failedUploadLeavesEmbeddingPending() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())
        drive.upsertResult = DriveSyncResult.Failure(IllegalStateException("network"))

        val result = processor().runOnce(SyncTrigger.Foreground)

        assertEquals(SyncRunResult.Retry, result)
        assertEquals(1, embeddings.pendingUploadKeys().size)
    }

    /** The restore path reports "remote_equivalent" for this snapshot; SnapshotEquivalence ignores embeddings, so the vector must still go up. */
    @Test
    fun equivalentRestoreStillUploadsPendingVector() = runBlocking {
        drive.snapshot = DriveSyncResult.Success(snapshotWithGarments("garment-1"))
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())

        processor().confirmMerge()

        assertEquals(1, drive.upserts.size)
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
    }

    /**
     * H01: a live garment's vector is uploaded once; the next cycle sends nothing and a later request is dropped.
     * This does NOT reproduce the loop: it passes without the H01 change too, because the upload path already marks the
     * vector. The orphan-state test below builds the state that actually looped.
     */
    @Test
    fun secondCycleAfterLiveVectorUploadSendsNothing() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())
        val processor = processor()

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background, reason = "backfill"))
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background, reason = "periodic"))
        assertEquals(1, drive.upserts.size)

        // Building the processor with a vector pending already requested "init"; the count is taken after that.
        val requestsBefore = scheduler.requestSyncCount
        processor.requestSyncInBackground("periodic")

        assertEquals(requestsBefore, scheduler.requestSyncCount)
    }

    /** H01 finding 2: a deferred run lives only in memory, so init must re-request when only vectors are pending. */
    @Test
    fun initRequestsARunWhenOnlyEmbeddingsArePending() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)

        processor()

        assertEquals(listOf("init"), scheduler.requestReasons)
    }

    /**
     * H01 finding 3: pending vectors whose garment is gone (no user data) are cleared by one cycle, so they cannot keep
     * every later cycle alive. Nothing is uploaded for them.
     */
    @Test
    fun orphanPendingVectorsWithNoGarmentsAreClearedByOneCycle() = runBlocking {
        embeddings.upsert("garment-gone", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        assertEquals(1, embeddings.pendingUploadKeys().size)

        val result = processor(snapshotStore = FakeWardrobeSnapshotStore()).runOnce(SyncTrigger.Background, reason = "backfill")

        assertEquals(SyncRunResult.Success, result)
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
        assertEquals(0, drive.upserts.size)
    }

    /**
     * H01 finding 4: the first write enqueues at once; a write inside the interval defers one run that starts when the
     * interval ends. The clock and the wait are driven by the test, so the deferred run is observed, not assumed.
     */
    @Test
    fun writeInsideTheIntervalDefersOneRunForTheRemainder() = runBlocking {
        var now = 0L
        val release = CompletableDeferred<Unit>()
        val waits = mutableListOf<Long>()
        val processor = processor(
            monotonicMillis = { now },
            awaitDeferredRequest = { delayMillis ->
                waits += delayMillis
                release.await()
            },
        )
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        processor.requestSyncInBackground("backfill")
        now = 1_000L
        embeddings.upsert("garment-2", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-2", 6L)
        processor.requestSyncInBackground("backfill")

        assertEquals(listOf("backfill"), scheduler.requestReasons)
        assertEquals(listOf(MIN_SYNC_REQUEST_INTERVAL_MILLIS - 1_000L), waits)

        release.complete(Unit)

        assertEquals(listOf("backfill", "deferred:backfill"), scheduler.requestReasons)
    }

    /** H01 finding 4: a write while the deferred run is still waiting merges into it: one run, not one per write. */
    @Test
    fun writesWhileTheDeferredRunWaitsCoalesceIntoIt() = runBlocking {
        var now = 0L
        val release = CompletableDeferred<Unit>()
        val waits = mutableListOf<Long>()
        val processor = processor(
            monotonicMillis = { now },
            awaitDeferredRequest = { delayMillis ->
                waits += delayMillis
                release.await()
            },
        )
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        processor.requestSyncInBackground("backfill")
        now = 1_000L
        embeddings.upsert("garment-2", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-2", 6L)
        processor.requestSyncInBackground("backfill")
        now = 2_000L
        embeddings.upsert("garment-3", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-3", 7L)
        processor.requestSyncInBackground("backfill")

        assertEquals(listOf("backfill"), scheduler.requestReasons)
        assertEquals(1, waits.size)

        release.complete(Unit)

        assertEquals(listOf("backfill", "deferred:backfill"), scheduler.requestReasons)
    }

    /** H01 finding 1: a clock that moved back must not hold the next request for longer than one interval. */
    @Test
    fun clockMovingBackwardsDoesNotStretchTheDeferral() = runBlocking {
        var now = 5_000L
        val waits = mutableListOf<Long>()
        val processor = processor(
            monotonicMillis = { now },
            awaitDeferredRequest = { delayMillis -> waits += delayMillis },
        )
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        processor.requestSyncInBackground("backfill")
        now = 1_000L
        embeddings.upsert("garment-2", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-2", 6L)
        processor.requestSyncInBackground("backfill")

        assertEquals(listOf(MIN_SYNC_REQUEST_INTERVAL_MILLIS), waits)
    }

    /** H01: a vector saved by the backfill asks for a run, and the run carries the reason into the log line. */
    @Test
    fun backfillSaveRequestsOneRunWithItsReason() = runBlocking {
        val processor = processor()
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)

        processor.requestSyncInBackground("backfill")

        assertEquals(listOf("backfill"), scheduler.requestReasons)
    }

    /** H01: a request with nothing pending sends nothing to WorkManager. */
    @Test
    fun requestWithNothingPendingIsDropped() = runBlocking {
        processor().requestSyncInBackground("backfill")

        assertEquals(0, scheduler.requestSyncCount)
    }

    /**
     * H01 finding C: the orphan state. Pending vectors whose garment id is absent from clothing_items, with no garments,
     * tombstones or custom tags. One cycle clears them, and no second request is made afterwards.
     */
    @Test
    fun orphanVectorsOfAMissingGarmentAreClearedAndNoSecondRequestIsMade() = runBlocking {
        embeddings.upsert("garment-gone", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        val processor = processor(snapshotStore = FakeWardrobeSnapshotStore())
        assertEquals(listOf("init"), scheduler.requestReasons)

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background, reason = "periodic"))
        assertTrue(embeddings.pendingUploadKeys().isEmpty())

        processor.requestSyncInBackground("periodic")
        assertEquals(listOf("init"), scheduler.requestReasons)
    }

    /**
     * H01 finding D: an archived-only wardrobe carries its tombstone, so it is uploaded, not skipped as empty. A failed upload
     * must leave the pending vector pending; marking it in the skip branch would drop it with nothing uploaded.
     */
    @Test
    fun archivedOnlyWardrobeWithTombstoneDoesNotMarkPendingVectorsUploaded() = runBlocking {
        val archivedOnly = WardrobeSyncSnapshot(
            tombstones = listOf(SyncTombstoneRecord(entityType = "garment", entityId = "garment-1", deletedAtEpochMillis = 2L, revision = 2L)),
        )
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        drive.upsertResult = DriveSyncResult.Failure(IllegalStateException("network"))

        val result = processor(snapshotStore = FakeWardrobeSnapshotStore(local = archivedOnly)).runOnce(SyncTrigger.Background, reason = "backfill")

        assertEquals(SyncRunResult.Retry, result)
        assertEquals(1, drive.upserts.size)
        assertEquals(1, embeddings.pendingUploadKeys().size)
    }

    /**
     * H01 finding D: a vector recomputed after the cycle captured its keys is not in that cycle's keys, so marking the cycle
     * uploaded leaves it pending with its new computed-at time.
     */
    @Test
    fun vectorRecomputedDuringTheCycleStaysPending() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())
        // Recomputes the vector while the cycle exports its snapshot, which runs after the keys were captured.
        val recomputingDuringExport = object : WardrobeSnapshotStore by store {
            override suspend fun exportSnapshot(generatedAtEpochMillis: Long): WardrobeSyncSnapshot {
                clockMillis = 2_000L
                embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-2", 6L)
                return store.exportSnapshot(generatedAtEpochMillis)
            }
        }

        val result = processor(snapshotStore = recomputingDuringExport).runOnce(SyncTrigger.Background, reason = "backfill")

        assertEquals(SyncRunResult.Success, result)
        assertEquals(listOf(2_000L), embeddings.pendingUploadKeys().map { it.computedAtEpochMillis })
    }

    /** V-E06c: a Drive embedding for another model or recipe is not imported, so nothing is stored or marked. */
    @Test
    fun importDropsEmbeddingsFromAnotherModelOrRecipe() {
        assertNotNull(embeddingRecord().toImportEntity("garment-1"))
        assertNull(embeddingRecord(modelId = "other-model").toImportEntity("garment-1"))
        assertNull(embeddingRecord(recipeId = "other-recipe").toImportEntity("garment-1"))
    }

    /** A vector re-computed during the download is newer than the imported one: the import must not replace it. */
    @Test
    fun importKeepsLocalVectorComputedAfterTheImportedOne() {
        val imported = importedRow(computedAtEpochMillis = 100L)

        assertEquals(emptyList<GarmentEmbeddingEntity>(), listOf(imported).withoutOlderThanLocal(listOf(importedRow(200L, pending = true))))
        assertEquals(listOf(imported), listOf(imported).withoutOlderThanLocal(listOf(importedRow(50L, pending = true))))
        assertEquals(listOf(imported), listOf(imported).withoutOlderThanLocal(emptyList()))
    }

    private fun embeddingRecord(
        modelId: String = EmbeddingRecipe.MODEL_ID,
        recipeId: String = EmbeddingRecipe.RECIPE_ID,
    ) = EmbeddingSyncRecord(
        kind = EmbeddingKind.IMAGE,
        modelId = modelId,
        recipeId = recipeId,
        dim = EmbeddingRecipe.DIMENSION,
        inputHash = "hash-1",
        computedAtEpochMillis = 100L,
        b64 = Base64.getEncoder().encodeToString(ByteArray(EmbeddingRecipe.DIMENSION * Float.SIZE_BYTES)),
    )

    private fun importedRow(computedAtEpochMillis: Long, pending: Boolean = false) = GarmentEmbeddingEntity(
        garmentId = "garment-1",
        kind = EmbeddingKind.IMAGE,
        modelId = EmbeddingRecipe.MODEL_ID,
        recipeId = EmbeddingRecipe.RECIPE_ID,
        dim = EmbeddingRecipe.DIMENSION,
        vector = ByteArray(EmbeddingRecipe.DIMENSION * Float.SIZE_BYTES),
        inputHash = "hash-1",
        computedAtEpochMillis = computedAtEpochMillis,
        durationMillis = 0L,
        pendingUpload = pending,
    )
}
