package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** V-E06b: an embedding-only change is uploaded as a snapshot and marked uploaded only after the upload succeeds. */
class EmbeddingUploadTest {
    private val embeddings = EmbeddingRepository(InMemoryEmbeddingDao())
    private val drive = FakeDriveWardrobeRepository()
    private val wardrobe = FakeWardrobeRepository()
    private val store = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1"))

    private val scheduler = FakeSyncWorkScheduler()

    private fun processor(): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
        settingsRepository = FakeSettingsRepository(),
        wardrobeRepository = wardrobe,
        snapshotRepository = store,
        driveRepository = drive,
        syncWorkScheduler = scheduler,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        dispatcher = Dispatchers.Unconfined,
        embeddingRepository = embeddings,
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
     * H01 reproduction: once the cycle that uploaded a pending vector has stored its snapshot, nothing is pending any more.
     * The next cycle then sends nothing, and no cycle asks for another run on its own.
     */
    @Test
    fun secondCycleAfterUploadFindsNothingPendingAndSendsNothing() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        exportWithVector(embeddings.pendingUploadKeys().single())
        val processor = processor()

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background, reason = "backfill"))
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background, reason = "periodic"))

        assertEquals(1, drive.upserts.size)
        assertEquals(0, scheduler.requestSyncCount)
    }

    /** H01: a vector saved by the backfill asks for a run, and the run carries the reason into the log line. */
    @Test
    fun backfillSaveRequestsOneRunWithItsReason() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)

        processor().requestSyncInBackground("backfill")

        assertEquals(listOf("backfill"), scheduler.requestReasons)
    }

    /** H01: two backfill requests inside the interval become one WorkManager request; the second is deferred, not dropped. */
    @Test
    fun backfillRequestsInsideTheIntervalCoalesceIntoOneRun() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        val processor = processor()

        processor.requestSyncInBackground("backfill")
        embeddings.upsert("garment-2", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-2", 6L)
        processor.requestSyncInBackground("backfill")

        assertEquals(listOf("backfill"), scheduler.requestReasons)
    }

    /** H01: a request with nothing pending sends nothing to WorkManager. */
    @Test
    fun requestWithNothingPendingIsDropped() = runBlocking {
        processor().requestSyncInBackground("backfill")

        assertEquals(0, scheduler.requestSyncCount)
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
