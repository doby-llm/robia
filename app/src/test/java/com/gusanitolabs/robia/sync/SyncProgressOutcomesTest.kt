package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.processorWithDrive
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Every terminal outcome reachable through the processor fakes must leave the restore overlay
 * cleared (null) or finalized (download blocked: Failed; upload failed or blocked: Complete).
 * Not reachable here: the whole-cycle timeout (removed in S04); only per-photo retry timeouts remain,
 * and they run outside the restore overlay.
 */
class SyncProgressOutcomesTest {
    @Test
    fun successClearsTheOverlay() = runBlocking {
        val processor = restoreScenario(FakeDriveWardrobeRepository(snapshot = remoteWith("b")))

        processor.confirmMerge()

        assertNull(processor.state.first().restoreProgress)
    }

    @Test
    fun uploadFailureFinalizesTheOverlayAsComplete() = runBlocking {
        val processor = restoreScenario(
            FakeDriveWardrobeRepository(snapshot = remoteWith("b"), upsertResult = DriveSyncResult.Failure(IOException("offline"))),
        )

        processor.confirmMerge()

        assertEquals(CloudRestorePhase.Complete, processor.state.first().restoreProgress?.phase)
    }

    @Test
    fun uploadBlockedFinalizesTheOverlayAsComplete() = runBlocking {
        val processor = restoreScenario(
            FakeDriveWardrobeRepository(
                snapshot = remoteWith("b"),
                upsertResult = DriveSyncResult.Blocked(DriveSyncDisabledReason.UserNotConnected, "Drive is not connected."),
            ),
        )

        processor.confirmMerge()

        assertEquals(CloudRestorePhase.Complete, processor.state.first().restoreProgress?.phase)
    }

    @Test
    fun downloadBlockedFinalizesTheOverlayAsFailed() = runBlocking {
        val processor = restoreScenario(
            FakeDriveWardrobeRepository(
                snapshot = DriveSyncResult.Blocked(DriveSyncDisabledReason.UserNotConnected, "Drive is not connected."),
            ),
        )

        processor.confirmMerge()

        val progress = processor.state.first().restoreProgress
        assertEquals(CloudRestorePhase.Downloading, progress?.phase)
        assertEquals(CloudRestoreStatus.Failed, progress?.status)
    }

    @Test
    fun conflictLeavesNoOverlay() = runBlocking {
        val settings = FakeSettingsRepository().apply { setSyncConflict(true, remoteRevision = 7L) }
        val wardrobe = FakeWardrobeRepository().apply { addQueued("a") }
        val processor = processorWithDrive(
            drive = FakeDriveWardrobeRepository(snapshot = remoteWith("b")),
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
            settings = settings,
        )

        assertEquals(SyncRunResult.Conflict, processor.runOnce(SyncTrigger.Foreground))

        assertNull(processor.state.first().restoreProgress)
    }

    @Test
    fun cancellationClearsTheOverlay() = runBlocking {
        val processor = restoreScenario(CancellingDownloadDrive(FakeDriveWardrobeRepository(snapshot = remoteWith("b"))))

        try {
            processor.confirmMerge()
        } catch (cancellation: CancellationException) {
            // The cancellation is expected to reach the caller; the overlay must still be cleared.
        }

        assertNull(processor.state.first().restoreProgress)
    }

    private fun remoteWith(id: String) = DriveSyncResult.Success(snapshotWithGarments(id))

    /** Local garment "a" is queued and differs from remote "b", so confirming the merge runs the restore-then-upload path. */
    private fun restoreScenario(drive: DriveWardrobeRepository) = processorWithDrive(
        drive = drive,
        wardrobe = FakeWardrobeRepository().apply { addQueued("a") },
        snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
    )
}

/** Cancels while the remote snapshot is being downloaded, as if the sync job was cancelled mid-restore. */
private class CancellingDownloadDrive(base: FakeDriveWardrobeRepository) : DriveWardrobeRepository by base {
    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> =
        throw CancellationException("cancelled while downloading")
}
