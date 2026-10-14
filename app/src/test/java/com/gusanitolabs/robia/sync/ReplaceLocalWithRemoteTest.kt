package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
    fun failedReplacementLeavesConflictUnresolvedAndRevisionUnchanged() = runBlocking {
        // The fake changes the store and then throws: the worst case. The processor must not record the remote
        // revision or clear the conflict. Room-level rollback needs a database and is not covered on the JVM.
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a", "b")).apply {
            replaceFailure = IllegalStateException("disk full mid-transaction")
        }
        val remote = snapshotWithGarments("c").copy(metadata = snapshotWithGarments("c").metadata.copy(revision = 7L))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(remote))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.useBackup()

        assertTrue(snapshots.replacements.isEmpty())
        assertTrue(settings.syncConflict.first())
        assertEquals(0L, settings.lastKnownRemoteRevision.first())
    }

    @Test
    fun remoteWithNewerSchemaClearsNothingAndKeepsTheConflict() = runBlocking {
        val local = snapshotWithGarments("a")
        val settings = FakeSettingsRepository().apply { setSyncConflict(true) }
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val newerSchema = snapshotWithGarments("c").let { remote ->
            remote.copy(metadata = remote.metadata.copy(schemaVersion = WARDROBE_SYNC_SCHEMA_VERSION + 1))
        }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(newerSchema))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.useBackup()

        assertTrue(snapshots.replacements.isEmpty())
        assertEquals(local, snapshots.local)
        assertTrue(settings.syncConflict.first())
    }

    @Test
    fun replaceKeepsTheDefaultTaxonomyRows() {
        // Room behaviour needs a database, so this pins the contract in the source: replace clears only items and
        // their links, and the remote taxonomy comes in through importSnapshot's upserts. Default tags are not deleted,
        // so no re-seeding is needed after a replace.
        val source = File("src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt").readText()
        val replace = source.substringAfter("override suspend fun replaceLocalWithRemote(")
            .substringBefore("data class ImportSnapshotResult")

        assertFalse(replace.contains("tagDao."))
        assertTrue(replace.contains("importSnapshot(snapshot)"))
    }
}
