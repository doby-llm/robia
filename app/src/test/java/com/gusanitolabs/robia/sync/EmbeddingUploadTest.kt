package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.InMemoryEmbeddingDao
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-E06b: an embedding-only change is uploaded as a snapshot and marked uploaded only after the upload succeeds. */
class EmbeddingUploadTest {
    private val embeddings = EmbeddingRepository(InMemoryEmbeddingDao())
    private val drive = FakeDriveWardrobeRepository()
    private val wardrobe = FakeWardrobeRepository()

    private fun processor(): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
        settingsRepository = FakeSettingsRepository(),
        wardrobeRepository = wardrobe,
        snapshotRepository = FakeWardrobeSnapshotStore(),
        driveRepository = drive,
        syncWorkScheduler = FakeSyncWorkScheduler(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        dispatcher = Dispatchers.Unconfined,
        embeddingRepository = embeddings,
    )

    @Test
    fun embeddingOnlyChangeUploadsSnapshotAndMarksRowUploaded() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)

        val result = processor().runOnce(SyncTrigger.Foreground)

        assertEquals(SyncRunResult.Success, result)
        assertEquals(1, drive.upserts.size)
        assertTrue(embeddings.pendingUploadKeys().isEmpty())
    }

    @Test
    fun failedUploadLeavesEmbeddingPending() = runBlocking {
        embeddings.upsert("garment-1", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-1", 5L)
        drive.upsertResult = DriveSyncResult.Failure(IllegalStateException("network"))

        processor().runOnce(SyncTrigger.Foreground)

        assertEquals(1, embeddings.pendingUploadKeys().size)
    }
}
