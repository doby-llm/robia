package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The K01 decision table. Each row names its rule; snapshots come from the real export and codec path. */
class MergeDecisionTest {
    private fun decide(
        local: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot,
        lastKnown: Long = 0L,
        pending: Boolean = false,
        wasEmpty: Boolean = false,
    ): MergeDecision = decideMerge(
        local = local,
        remote = remote,
        lastKnownRemoteRevision = lastKnown,
        deviceId = FAKE_DEVICE_ID,
        pendingLocalWork = pending,
        localWasEmptyAtStart = wasEmpty,
    )

    @Test
    fun rule1_backupWithoutUserDataUploadsThePhone() {
        val local = exportedSnapshot(garmentEntity("a"))
        assertEquals(MergeDecision.UploadLocal, decide(local, driveBackup(exportedSnapshot(), OTHER_DEVICE_ID, 50L), lastKnown = 50L))
    }

    @Test
    fun rule2_backupWrittenByThisPhoneIsAnOrdinaryCycle() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"))
        val own = driveBackup(exportedSnapshot(garmentEntity("a")), FAKE_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, own))
        assertEquals(MergeDecision.UploadLocal, decide(local, own, pending = true))
    }

    @Test
    fun rule2_backupNotNewerThanTheRecordedBaseIsAnOrdinaryCycle() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L))
        assertEquals(MergeDecision.UploadLocal, decide(local, remote, lastKnown = 50L, pending = true))
    }

    @Test
    fun rule2_aNewerBackupFromAnotherWriterIsNotOrdinary() {
        val local = exportedSnapshot(garmentEntity("a"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 60L)
        // Base 50 and the backup is at 60: a real change from another writer, so the rules below decide.
        assertEquals(MergeDecision.FastForward, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun rule3_anEmptyPhoneTakesTheBackupSilently() {
        val remote = driveBackup(exportedSnapshot(garmentEntity("a")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.RestoreSilently, decide(exportedSnapshot(), remote, wasEmpty = true))
    }

    @Test
    fun rule3_anEmptyPhoneWithAKnownBaseStillRestoresInsteadOfStayingEmpty() {
        val remote = driveBackup(exportedSnapshot(garmentEntity("a")), FAKE_DEVICE_ID, 50L)
        assertEquals(MergeDecision.RestoreSilently, decide(exportedSnapshot(), remote, lastKnown = 50L, wasEmpty = true))
    }

    @Test
    fun rule4_sameDataRecordsTheBaseWithoutAProposal() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.RecordBase, decide(local, remote))
        assertEquals(MergeDecision.UploadLocal, decide(local, remote, pending = true))
    }

    @Test
    fun rule5_aSecondDeviceThatNeverSyncedTakesTheBackupWhenItsDataIsContained() {
        val local = exportedSnapshot(garmentEntity("a"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.FastForward, decide(local, remote))
    }

    @Test
    fun rule5_pendingLocalWorkAlwaysProposesEvenWhenContained() {
        val local = exportedSnapshot(garmentEntity("a"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote, pending = true))
    }

    @Test
    fun rule5_aLocalEditNewerThanTheBackupIsNotContained() {
        val local = exportedSnapshot(garmentEntity("a", updatedAt = 200L))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", updatedAt = 100L), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    @Test
    fun rule6_aRealClashProposes() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    @Test
    fun rule6_aRealClashAlsoProposesWithAKnownBaseThatIsOlderThanTheBackup() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 60L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L))
    }
}
