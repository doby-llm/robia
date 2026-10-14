package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewerWriterTest {
    @Test
    fun newerRevisionFromAnotherPhoneBlocksUploadAndSetsConflict() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = FakeDriveWardrobeRepository(
            manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = "other-phone")),
        )
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Conflict, processor.runOnce(SyncTrigger.Foreground))

        assertTrue(drive.upserts.isEmpty())
        assertTrue(settings.syncConflict.first())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun ownWriterDoesNotBlockUpload() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = FakeDriveWardrobeRepository(
            manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = FAKE_DEVICE_ID)),
        )
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
        assertEquals(false, settings.syncConflict.first())
    }

    @Test
    fun revisionAlreadySeenByThisPhoneDoesNotBlockUpload() = runBlocking {
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(5L) }
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = FakeDriveWardrobeRepository(
            manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = "other-phone")),
        )
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
    }
}
