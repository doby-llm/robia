package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * K01: the merge proposal appears only on a real clash. Every fixture is built by the phone's export and the Drive
 * JSON codec (see RealPathFixtures.kt). Rows: the scenario table in artifacts/inv-feedback5-J.md.
 */
class MergeProposalScenarioTest {
    @Test
    fun freshPhoneAndEmptyRemoteStaysSilentAndWritesNothing() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(driveBackup(exportedSnapshot(), writer = null, revision = 0L)))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun restoreThenForcedImportNeverProposesAndTheTrailingRunWritesNothing() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        // The second forced run (connect + fresh-install restore) sees the restored phone.
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertEquals(1, snapshots.imports.size)
        assertNull(processor.state.first().pendingMergeSummary)
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun phoneWithDataAndEmptyRemoteUploadsWithoutAProposal() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a")))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(driveBackup(exportedSnapshot(), writer = null, revision = 0L)))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, drive.upserts.size)
        assertEquals(listOf("a"), drive.upserts.single().garments.map { it.id })
    }

    @Test
    fun sameLineageBackupWrittenByThisPhoneNeverProposes() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"))
        val backup = driveBackup(local, writer = FAKE_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun realClashStillProposesAndChangesNothingUntilConfirmed() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertEquals(
            MergeSummary(remoteGarmentCount = 2, localGarmentCount = 2, mergedGarmentCount = 3),
            processor.state.first().pendingMergeSummary,
        )
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun secondDeviceNeverSyncedFastForwardsWhenItsDataIsContainedInTheBackup() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b"), garmentEntity("c")), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a")))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(listOf(listOf("a", "b", "c")), snapshots.imports.map { snapshot -> snapshot.garments.map { it.id } })
    }

    @Test
    fun partialRestoreWithAGuardedPhotoDoesNotProposeOnTheTrailingRun() = runBlocking {
        val guarded = garmentEntity("b").copy(photoRestoreGuarded = true)
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), guarded), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, snapshots.imports.size)
    }

    @Test
    fun restoreOfflineLeavesTheFreshInstallFlagForALaterAttempt() = runBlocking {
        val settings = FakeSettingsRepository(status = DriveSyncConnectionStatus.Connected, freshInstallRestoreAttempted = false)
        val drive = FakeDriveWardrobeRepository(
            snapshot = DriveSyncResult.Failure(UnknownHostException("www.googleapis.com")),
        )
        newTestProcessor(settings = settings, snapshots = FakeWardrobeSnapshotStore(), drive = drive)

        // The restore runs when the processor observes a connected, idle state; it must have been attempted.
        assertTrue(drive.snapshotFetches > 0)
        assertEquals(false, settings.settings.first().driveFreshInstallRestoreAttempted)
    }
}
