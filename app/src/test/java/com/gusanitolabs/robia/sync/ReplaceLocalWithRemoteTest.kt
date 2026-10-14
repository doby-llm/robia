package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** "Use backup" (PLAN-CHANGE 5): the phone is only replaced after a remote snapshot has been fetched and decoded. */
class ReplaceLocalWithRemoteTest {
    @Test
    fun remoteThatFailsToDecodeClearsNothing() = runBlocking {
        val local = snapshotWithGarments("a", "b")
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Failure(IOException("backup JSON is malformed")))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.useBackup()

        assertTrue(snapshots.replacements.isEmpty())
        assertEquals(local, snapshots.local)
        assertTrue(settings.syncConflict.first())
    }

    @Test
    fun emptyRemoteNeverWipesThePhone() = runBlocking {
        val local = snapshotWithGarments("a")
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(WardrobeSyncSnapshot()))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.useBackup()

        assertTrue(snapshots.replacements.isEmpty())
        assertEquals(local, snapshots.local)
    }

    @Test
    fun failedReplacementKeepsLocalDataAndTheConflict() = runBlocking {
        // The fake throws before replacing, as a rolled-back transaction would. The Room-level rollback itself
        // needs a database and is not covered on the JVM.
        val local = snapshotWithGarments("a", "b")
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val snapshots = FakeWardrobeSnapshotStore(local = local).apply {
            replaceFailure = IllegalStateException("disk full mid-transaction")
        }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("c")))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.useBackup()

        assertEquals(local, snapshots.local)
        assertTrue(settings.syncConflict.first())
    }
}
