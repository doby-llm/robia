package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NeedsUserActionRetryTest {
    private suspend fun parkedWardrobe(): FakeWardrobeRepository = FakeWardrobeRepository().apply {
        addQueued("drive-full")
        markGarmentNeedsUserAction("drive-full", 1L, "drive_full")
        addQueued("photo-failed")
        markGarmentNeedsUserAction("photo-failed", 1L, "photo_upload_failed")
    }

    @Test
    fun automaticCyclesNeverRequeueParkedRows() = runBlocking {
        val wardrobe = parkedWardrobe()
        val processor = newTestProcessor(wardrobe = wardrobe)

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Background))
        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals("NeedsUserAction", wardrobe.statusOf("drive-full"))
        assertEquals("NeedsUserAction", wardrobe.statusOf("photo-failed"))
    }

    @Test
    fun explicitRetryReleasesOnlyItsOwnReasonAndUploadsIt() = runBlocking {
        val wardrobe = parkedWardrobe()
        val processor = newTestProcessor(wardrobe = wardrobe)

        processor.enqueue(WardrobeSyncOperation.RetryAttention(SyncAttentionKind.DriveFull))

        assertEquals("Synced", wardrobe.statusOf("drive-full"))
        assertEquals("NeedsUserAction", wardrobe.statusOf("photo-failed"))
    }

    @Test
    fun keepWithoutPhotoClearsOnlyTheChosenGuardAndUploads() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply {
            addQueued("guarded")
            markGarmentNeedsUserAction("guarded", 1L, "remote_photo_guarded")
            markGuarded("guarded")
            addQueued("other-guarded")
            markGarmentNeedsUserAction("other-guarded", 1L, "remote_photo_guarded")
            markGuarded("other-guarded")
        }
        val processor = newTestProcessor(wardrobe = wardrobe)

        processor.keepWithoutPhoto("guarded")

        assertEquals("Synced", wardrobe.statusOf("guarded"))
        assertEquals("NeedsUserAction", wardrobe.statusOf("other-guarded"))
        assertEquals(1, wardrobe.observeGuardedPhotoRestoreCount().first())
    }
}
