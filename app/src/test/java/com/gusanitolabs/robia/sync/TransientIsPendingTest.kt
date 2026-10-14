package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TransientIsPendingTest {
    @Test
    fun tenTransientFailuresKeepTheGarmentQueuedWithoutAttentionOrAttemptCount() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
        val drive = FakeDriveWardrobeRepository(upsertResult = DriveSyncResult.Failure(IOException("network unreachable")))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1")),
            drive = drive,
        )

        repeat(10) {
            assertEquals(SyncRunResult.Retry, processor.runOnce(SyncTrigger.Background))
            assertEquals("Queued", wardrobe.statusOf("garment-1"))
        }

        assertEquals(10, drive.upserts.size)
        assertTrue("transient failures must not increment the attempt counter", wardrobe.failedRetryableCalls.isEmpty())
        assertTrue("transient failures must not park the garment for the user", wardrobe.needsUserActions.isEmpty())
        assertTrue(processor.state.first().attention.isEmpty())
    }
}
