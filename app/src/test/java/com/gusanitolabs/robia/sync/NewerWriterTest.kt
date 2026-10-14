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
    fun newerRevisionFromAnotherPhoneGoesToMergeProposalWithoutUploading() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData(writerDeviceId = "other-phone")
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))

        assertTrue(drive.upserts.isEmpty())
        assertEquals(false, settings.syncConflict.first())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun backupWithoutWriterIdCountsAsAnotherWriter() = runBlocking {
        // Every backup written before S04 has no writer id (PLAN-CHANGE 7).
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData(writerDeviceId = null)
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))

        assertTrue(drive.upserts.isEmpty())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun backgroundRunsBeforeTheMergeDecisionNeverUpload() = runBlocking {
        val settings = FakeSettingsRepository()
        val drive = otherPhoneBackupWithData(writerDeviceId = null)
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = FakeWardrobeRepository().apply { addQueued("a") },
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Background))
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Background))

        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun restartAfterCancelMergeStaysBlockedFromPersistedState() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData(writerDeviceId = null)
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a"))
        val beforeRestart = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)
        assertEquals(SyncRunResult.NeedsUser, beforeRestart.runOnce(SyncTrigger.Foreground))
        beforeRestart.cancelMerge()

        // A new processor has no in-memory state; only the persisted settings carry the decision.
        val afterRestart = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)

        assertEquals(SyncRunResult.Conflict, afterRestart.runOnce(SyncTrigger.Background))
        assertTrue(drive.upserts.isEmpty())
        assertTrue(settings.syncConflict.first())
    }

    @Test
    fun nullWriterRevisionAlreadySeenByThisPhoneUploads() = runBlocking {
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(5L) }
        val drive = FakeDriveWardrobeRepository(
            manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = null)),
            snapshot = DriveSyncResult.Success(snapshotWithGarments("remote")),
        )
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = FakeWardrobeRepository().apply { addQueued("a") },
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
    }

    private fun otherPhoneBackupWithData(writerDeviceId: String?): FakeDriveWardrobeRepository = FakeDriveWardrobeRepository(
        manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = writerDeviceId)),
        snapshot = DriveSyncResult.Success(snapshotWithGarments("remote")),
    )

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
