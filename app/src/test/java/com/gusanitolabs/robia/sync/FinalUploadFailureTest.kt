package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Local rows claimed by a restore must not be marked Synced unless the merged snapshot really reached Drive. */
class FinalUploadFailureTest {
    @Test
    fun failingFinalUploadLeavesClaimedRowsQueued() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = FakeDriveWardrobeRepository(
            snapshot = DriveSyncResult.Success(snapshotWithGarments("b")),
            upsertResult = DriveSyncResult.Failure(IOException("Drive unreachable")),
        )
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        processor.confirmMerge()

        assertEquals(1, drive.upserts.size)
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun guardSkippedFinalUploadLeavesClaimedRowsQueued() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val guardedRemote = snapshotWithGarments("b").copy(
            photos = listOf(
                GarmentPhotoRecord(
                    garmentId = "b",
                    localUri = "",
                    restoreFailureCategory = "remote_photo_transient",
                ),
            ),
        )
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(guardedRemote))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        processor.confirmMerge()

        assertTrue(drive.upserts.isEmpty())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }
}
