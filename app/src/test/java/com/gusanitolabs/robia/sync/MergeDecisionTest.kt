package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DismissedPair
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.SECOND_OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import com.gusanitolabs.robia.sync.fakes.garmentTagEntity
import com.gusanitolabs.robia.sync.fakes.garmentTagRef
import com.gusanitolabs.robia.sync.fakes.tombstoneEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The K01 decision table. Each row names its rule; snapshots come from the real export and codec path. */
class MergeDecisionTest {
    private fun decide(
        local: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot,
        lastKnown: Long = 0L,
        lastKnownWriter: String? = null,
        pending: Boolean = false,
        wasEmpty: Boolean = false,
    ): MergeDecision = decideMerge(
        local = local,
        remote = remote,
        lastKnownRemoteRevision = lastKnown,
        lastKnownRemoteWriter = lastKnownWriter,
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

    // Finding 2: two writers can share a revision under clock skew, so the base's writer is checked as well.

    @Test
    fun lineage_anEqualRevisionFromAnotherWriterThanTheBaseWriterFallsThrough() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        // The base is revision 50 from SECOND_OTHER; this backup is also 50, from OTHER: a clock collision, not the base.
        assertEquals(MergeDecision.Propose, decide(local, remote, lastKnown = 50L, lastKnownWriter = SECOND_OTHER_DEVICE_ID))
    }

    @Test
    fun lineage_anEqualRevisionFromTheBaseWriterIsStillOwnLineage() {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L, lastKnownWriter = OTHER_DEVICE_ID))
    }

    @Test
    fun lineage_aBaseWithAnUnknownWriterKeepsTheRevisionOnlyCheck() {
        // A base recorded before the writer was stored: the exact revision still counts. This is the documented residual.
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), SECOND_OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L, lastKnownWriter = null))
    }

    @Test
    fun lineage_aPhotoBearingRestoreIsOwnLineageAtTheBase() {
        // Writer-only lineage would send this to a proposal. The exact base with its writer keeps the trailing run silent.
        val local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg")), OTHER_DEVICE_ID, 50L)
        assertEquals(MergeDecision.NoOp, decide(local, remote, lastKnown = 50L, lastKnownWriter = OTHER_DEVICE_ID))
        assertEquals(MergeDecision.UploadLocal, decide(local, remote, lastKnown = 50L, lastKnownWriter = OTHER_DEVICE_ID, pending = true))
    }

    // Finding 1: a remote photo whose blob will not restore is never taken silently over the phone's own photo.

    @Test
    fun photos_theDecisionReadsJsonOnlySoAFastForwardIsCheckedAgainstTheFetchedGuard() {
        val local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg")), OTHER_DEVICE_ID, 50L)
            .let { backup -> backup.copy(photos = backup.photos.map { it.copy(contentHash = "hash-a") }) }
        // The merge keeps the newer remote record, which equals the backup, so the JSON decision is a FastForward. A blob that
        // fails to download carries the guard only after the fetch: the restore then proposes (see the scenario test
        // finding1_aGuardedRemotePhotoOnTheSamePhotoUriProposesInsteadOfRestoringSilently).
        assertEquals(MergeDecision.FastForward, decide(local, remote, lastKnown = 40L))
    }

    // Finding 1 (cont.): a row that has not reached Drive makes the phone pending, so a newer backup cannot silently replace it.

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

    // Finding 4 (known deviation, not fixed): a local photo carries no hash, so it never matches a remote photo's hash.

    @Test
    fun photos_aPhotoBearingPhoneWithAnotherPhotoUriProposesEvenWhenTheWardrobeMatches() {
        // Equal revisions tie to the remote record; the local URI differs, so the local record is kept and it carries no hash.
        val local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/local.jpg"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/remote.jpg")), OTHER_DEVICE_ID, 50L)
            .let { backup -> backup.copy(photos = backup.photos.map { it.copy(contentHash = "hash-a") }) }
        assertEquals(MergeDecision.Propose, decide(local, remote))
    }

    @Test
    fun photos_aPhotoBearingPhoneNeverRecordsTheBaseWithoutAHashToCompare() {
        val local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg"))
        val remote = driveBackup(exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a.jpg")), OTHER_DEVICE_ID, 50L)
            .let { backup -> backup.copy(photos = backup.photos.map { it.copy(contentHash = "hash-a") }) }
        assertNotEquals(MergeDecision.RecordBase, decide(local, remote))
    }

    private fun pair(a: String, b: String, dismissedAt: Long) = DismissedPair(a, b, dismissedAtEpochMillis = dismissedAt)

    @Test
    fun mergeKeepsTheUnionOfBothSidesPairs_eachOnceWithTheEarliestDismissal() {
        val garments = arrayOf(garmentEntity("a"), garmentEntity("b"), garmentEntity("c"))
        val local = exportedSnapshot(*garments).copy(dismissedDuplicates = listOf(pair("a", "b", 10L), pair("b", "c", 30L)))
        val remote = exportedSnapshot(*garments).copy(dismissedDuplicates = listOf(pair("c", "b", 20L), pair("a", "c", 5L)))

        // a-b is local only, a-c is remote only, and b-c is on both sides in reverse order with the earlier time (20) kept.
        assertEquals(
            listOf(pair("a", "b", 10L), pair("a", "c", 5L), pair("b", "c", 20L)),
            mergeSnapshots(local, remote).dismissedDuplicates,
        )
    }

    @Test
    fun mergeDropsThePairsOfAGarmentWhoseTombstoneWins() {
        val garments = arrayOf(garmentEntity("a"), garmentEntity("b"), garmentEntity("c"))
        val local = exportedSnapshot(*garments, tombstones = listOf(tombstoneEntity("garment", "a")))
            .copy(dismissedDuplicates = listOf(pair("a", "b", 10L), pair("b", "c", 20L)))

        assertEquals(listOf(pair("b", "c", 20L)), mergeSnapshots(local, exportedSnapshot(*garments)).dismissedDuplicates)
    }

    @Test
    fun mergedSnapshotIsEquivalentToTheRemoteWhenTheirPairsAreTheSameInAnyOrder() {
        val local = exportedSnapshot(garmentEntity("a")).copy(dismissedDuplicates = listOf(pair("b", "a", 10L)))
        val remote = exportedSnapshot(garmentEntity("a")).copy(dismissedDuplicates = listOf(pair("a", "b", 10L)))

        assertTrue(mergeSnapshots(local, remote).equivalentForUpload(remote))
    }

    @Test
    fun rule5_aRemoteHoldingPairsFastForwardsWhenThePhoneHasNone() {
        val garments = arrayOf(garmentEntity("a"), garmentEntity("b"))
        val local = exportedSnapshot(*garments)
        val remote = driveBackup(exportedSnapshot(*garments).copy(dismissedDuplicates = listOf(pair("a", "b", 10L))), OTHER_DEVICE_ID, 60L)

        assertEquals(MergeDecision.FastForward, decide(local, remote, lastKnown = 50L))
    }

    @Test
    fun rule4_aRemoteWithTheSamePairsInAnotherOrderRecordsTheBaseWithoutAPrompt() {
        val garments = arrayOf(garmentEntity("a"), garmentEntity("b"))
        val local = exportedSnapshot(*garments).copy(dismissedDuplicates = listOf(pair("b", "a", 10L)))
        val remote = driveBackup(exportedSnapshot(*garments).copy(dismissedDuplicates = listOf(pair("a", "b", 10L))), OTHER_DEVICE_ID, 60L)

        assertEquals(MergeDecision.RecordBase, decide(local, remote, lastKnown = 50L))
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
