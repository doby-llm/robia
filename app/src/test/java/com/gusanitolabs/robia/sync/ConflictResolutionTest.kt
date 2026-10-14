package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConflictResolutionTest {
    @Test
    fun declinedMergeBlocksThenConfirmedMergeClearsConflictAndLaterEditsUpload() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData()
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))
        processor.cancelMerge()
        assertEquals(SyncRunResult.Conflict, processor.runOnce(SyncTrigger.Foreground))
        assertTrue(drive.upserts.isEmpty())

        // The confirmed restore uploads the merge; the remote now holds revision 6 written by this phone.
        drive.upsertResult = DriveSyncResult.Success(DriveManifest(revision = 6L, writerDeviceId = FAKE_DEVICE_ID))
        processor.confirmMerge()

        assertEquals(false, settings.syncConflict.first())
        assertEquals(1, drive.upserts.size)
        assertEquals("Synced", wardrobe.statusOf("a"))

        wardrobe.addQueued("b")
        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))
        assertEquals(2, drive.upserts.size)
        assertEquals("Synced", wardrobe.statusOf("b"))
    }

    @Test
    fun declinedMergeStaysBlockedAfterRestart() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData()
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a"))
        val beforeRestart = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)
        assertEquals(SyncRunResult.NeedsUser, beforeRestart.runOnce(SyncTrigger.Foreground))
        beforeRestart.cancelMerge()

        val afterRestart = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)

        assertEquals(SyncRunResult.Conflict, afterRestart.runOnce(SyncTrigger.Foreground))
        assertEquals(SyncRunResult.Conflict, afterRestart.runOnce(SyncTrigger.Background))
        assertTrue(drive.upserts.isEmpty())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun importWithOpenConflictOnNonEmptyPhoneUploadsNothing() = runBlocking {
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = FakeDriveWardrobeRepository()
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))

        assertTrue(drive.upserts.isEmpty())
        assertEquals(true, settings.syncConflict.first())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun restoreOnEmptyPhoneClearsOpenConflict() = runBlocking {
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val snapshots = FakeWardrobeSnapshotStore(local = WardrobeSyncSnapshot())
        val processor = newTestProcessor(
            settings = settings,
            snapshots = snapshots,
            drive = otherPhoneBackupWithData(),
        )

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))

        assertEquals(1, snapshots.imports.size)
        assertEquals(false, settings.syncConflict.first())
    }

    @Test
    fun keepThisPhoneAfterAnotherWriteReopensConflictAtNewRevisionAndUploadsNothing() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupAtRevision(5L)
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))
        processor.cancelMerge()
        assertEquals(5L, settings.syncConflictRemoteRevision.first())

        // The user declined at revision 5; another phone writes revision 6 before "Keep this phone" is tapped.
        drive.manifest = DriveSyncResult.Success(DriveManifest(revision = 6L, writerDeviceId = "third-phone"))
        processor.keepThisPhone()

        assertTrue(drive.upserts.isEmpty())
        assertEquals(true, settings.syncConflict.first())
        assertEquals(6L, settings.syncConflictRemoteRevision.first())
        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    @Test
    fun keepThisPhoneUploadsWhenTheDeclinedRevisionIsStillCurrent() = runBlocking {
        val settings = FakeSettingsRepository()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupAtRevision(5L)
        val processor = newTestProcessor(
            settings = settings,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))
        processor.cancelMerge()

        processor.keepThisPhone()

        assertEquals(1, drive.upserts.size)
        assertEquals(false, settings.syncConflict.first())
        assertEquals(0L, settings.syncConflictRemoteRevision.first())
        assertEquals("Synced", wardrobe.statusOf("a"))
    }

    @Test
    fun confirmedMergeWithPhotoUploadFailureLeavesGarmentNotSynced() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val drive = otherPhoneBackupWithData()
        drive.upsertResult = DriveSyncResult.Success(
            DriveManifest(
                revision = 6L,
                writerDeviceId = FAKE_DEVICE_ID,
                photoUploadFailures = listOf(PhotoUploadFailure("a")),
            ),
        )
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            drive = drive,
        )
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Foreground))

        processor.confirmMerge()

        assertEquals("NeedsUserAction", wardrobe.statusOf("a"))
        assertEquals(listOf("a" to "photo_upload_failed"), wardrobe.needsUserActions)
    }

    @Test
    fun cancellationWhileClaimingReturnsEveryClaimedRowToQueued() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply {
            addQueued("a")
            addQueued("b")
            cancelClaimOf = "b"
        }
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a", "b")),
        )

        try {
            processor.runOnce(SyncTrigger.Foreground)
            fail("expected the claim to be cancelled")
        } catch (expected: CancellationException) {
            // Cancellation must propagate; the claimed rows are what this test checks.
        }

        assertEquals("Queued", wardrobe.statusOf("a"))
        assertEquals("Queued", wardrobe.statusOf("b"))
    }

    @Test
    fun cancellationAfterClaimWriteStillReturnsRowToQueued() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply {
            addQueued("a")
            cancelAfterClaimOf = "a"
        }
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
        )

        try {
            processor.runOnce(SyncTrigger.Foreground)
            fail("expected the claim to be cancelled")
        } catch (expected: CancellationException) {
            // Cancellation must propagate; the row that was already Running is what this test checks.
        }

        assertEquals("Queued", wardrobe.statusOf("a"))
    }

    private fun otherPhoneBackupWithData(): FakeDriveWardrobeRepository = FakeDriveWardrobeRepository(
        manifest = DriveSyncResult.Success(DriveManifest(revision = 5L, writerDeviceId = "other-phone")),
        snapshot = DriveSyncResult.Success(snapshotWithGarments("remote")),
    )

    /** The other phone's backup at [revision], with the same revision in the snapshot metadata the merge is built from. */
    private fun otherPhoneBackupAtRevision(revision: Long): FakeDriveWardrobeRepository {
        val remote = snapshotWithGarments("remote")
        return FakeDriveWardrobeRepository(
            manifest = DriveSyncResult.Success(DriveManifest(revision = revision, writerDeviceId = "other-phone")),
            snapshot = DriveSyncResult.Success(remote.copy(metadata = remote.metadata.copy(revision = revision))),
        )
    }
}
