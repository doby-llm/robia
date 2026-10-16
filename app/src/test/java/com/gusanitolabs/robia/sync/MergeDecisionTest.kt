package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import com.gusanitolabs.robia.sync.fakes.garmentTagEntity
import com.gusanitolabs.robia.sync.fakes.garmentTagRef
import com.gusanitolabs.robia.sync.fakes.tombstoneEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // Finding 3: own lineage is this device's writer, or exactly the recorded base. Clocks skew, so no other revision counts.

    @Test
    fun lineage_aLowerRevisionFromAnotherWriterIsNotOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 40L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun lineage_aLegacyBackupWithoutWriterBelowTheBaseIsNotOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = null, revision = 40L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun lineage_aNewerLegacyBackupWithoutWriterIsNotOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = null, revision = 60L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun lineage_aLegacyBackupWithoutWriterAtExactlyTheBaseIsOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = null, revision = 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun lineage_theOwnWriterIsOwnWhateverItsRevision() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), FAKE_DEVICE_ID, 40L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun lineage_anotherWriterAtExactlyTheBaseIsOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L))
    }

    // Finding 1: a row that has not reached Drive makes the phone pending, so a newer backup cannot silently replace it.

    @Test
    fun pending_aNeedsUserActionEditIsNotTakenOverByANewerBackup() {
        val local = exportedSnapshot(garmentEntity("g", updatedAt = 100L, syncStatus = GarmentSyncStatus.NeedsUserAction).copy(notes = "my edit"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("g", updatedAt = 200L)), OTHER_DEVICE_ID, 60L)
        // Without pending work the newer remote garment wins the merge and equals the backup: a silent fast-forward loses the edit.
        assertEquals(MergeDecision.FastForward, decide(local, remote, lastKnown = 50L, pending = false))
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L, pending = true))
    }

    @Test
    fun pending_aLocalDeletionIsNotUndoneByTheBackup() {
        // The tombstone of garment a is unsynced; b is the only garment left on the phone.
        val local = exportedSnapshot(garmentEntity("b"), tombstones = listOf(tombstoneEntity("garment", "a")))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 40L, pending = true))
    }

    @Test
    fun pending_aLocalDeletionWhoseGarmentStillExistsRemotelyProposes() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"), tombstones = listOf(tombstoneEntity("garment", "a")))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 40L, pending = true))
    }

    @Test
    fun pending_aLocalTagRemovalIsNotUndoneByTheBackupsTag() {
        // Removing a tag re-queues the garment, so the pending flag is set. The union of tags would otherwise re-add the tag.
        val tag = garmentTagEntity("t1")
        val local = exportedSnapshot(garmentEntity("g", updatedAt = 200L, syncStatus = GarmentSyncStatus.Queued), tags = listOf(tag))
        val remote = driveBackup(
            exportedSnapshot(garmentEntity("g", updatedAt = 100L), tagRefs = listOf(garmentTagRef("g", "t1")), tags = listOf(tag)),
            OTHER_DEVICE_ID,
            50L,
        )
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 40L, pending = true))
    }

    @Test
    fun pending_aLocalPhotoRemovalProposes() {
        val local = exportedSnapshot(garmentEntity("g", updatedAt = 200L, syncStatus = GarmentSyncStatus.Queued))
        val remote = driveBackup(exportedSnapshot(garmentEntity("g", photoUri = "file:///photos/g.jpg", updatedAt = 100L)), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 40L, pending = true))
    }

    // Finding 8: the same data in another wardrobe or schema is not "already held".

    @Test
    fun wardrobe_aDifferentWardrobeIdProposesEvenWhenTheDataMatches() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("b"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
            .let { it.copy(metadata = it.metadata.copy(wardrobeId = "another-wardrobe")) }
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    @Test
    fun wardrobe_aDifferentSchemaProposesEvenWhenTheBackupIsContained() {
        val local = exportedSnapshot(garmentEntity("a"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
            .let { it.copy(metadata = it.metadata.copy(schemaVersion = local.metadata.schemaVersion - 1)) }
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    // Finding 7: an archived default tag is a customisation, so the backup holding it is user data.

    @Test
    fun userData_anArchivedDefaultTagIsUserDataAndAnUnchangedDefaultIsNot() {
        val archived = driveBackup(exportedSnapshot().copy(taxonomies = archivedDefaultTag()), OTHER_DEVICE_ID, 50L)
        assertTrue(archived.hasUserData())
        assertFalse(exportedSnapshot().hasUserData())
        assertFalse(driveBackup(exportedSnapshot(), OTHER_DEVICE_ID, 50L).hasUserData())
    }

    @Test
    fun userData_aBackupWhoseOnlyDataIsAnArchivedDefaultProposesRatherThanBeingUploadedOver() {
        val local = exportedSnapshot(garmentEntity("a"))
        val remote = driveBackup(exportedSnapshot().copy(taxonomies = archivedDefaultTag()), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    // Finding 4 (known deviation, not fixed): a local photo carries no hash, so an identical photo-bearing wardrobe proposes.

    @Test
    fun photos_aPhotoBearingPhoneProposesEvenWhenTheWardrobeMatches() {
        val local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg"))
        // Drive records carry the blob's content hash; the local export does not.
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg")), OTHER_DEVICE_ID, 50L)
            .let { backup -> backup.copy(photos = backup.photos.map { it.copy(contentHash = "hash-a") }) }
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    private fun archivedDefaultTag(): WardrobeTaxonomySnapshot {
        val default = DefaultTags.tags.first { it.id == "occasion-business" }
        return WardrobeTaxonomySnapshot(
            tags = listOf(
                TagSyncRecord(
                    id = default.id,
                    categoryId = default.categoryId,
                    name = default.name,
                    sortOrder = default.sortOrder,
                    isSystem = default.isSystem,
                    isArchived = true,
                ),
            ),
        )
    }
}
