package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.InMemoryEmbeddingDao
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.sync.fakes.FAKE_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.SECOND_OTHER_DEVICE_ID
import com.gusanitolabs.robia.sync.fakes.driveBackup
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    @Test
    fun restoreThenForcedImportWithQueuedRowsAfterTheRestoreStaysSilent() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val wardrobe = FakeWardrobeRepository()
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(wardrobe = wardrobe, snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        // A row queued after the restore keeps the trailing run pending: it uploads, and it does not ask the user.
        wardrobe.addQueued("q", revision = 300L)
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, snapshots.imports.size)
    }

    @Test
    fun unsyncedRowsInAnyStateProposeInsteadOfTakingTheNewerBackup() = runBlocking {
        for (status in listOf("NeedsUserAction", "AuthBlocked", "FailedRetryable")) {
            val wardrobe = FakeWardrobeRepository()
            wardrobe.addRow("g", revision = 100L, status = status)
            val local = exportedSnapshot(garmentEntity("g", updatedAt = 100L).copy(notes = "my edit"))
            val backup = driveBackup(exportedSnapshot(garmentEntity("g", updatedAt = 200L)), writer = OTHER_DEVICE_ID, revision = 60L)
            val settings = FakeSettingsRepository()
            settings.setLastKnownRemoteRevision(50L, writerDeviceId = null)
            val snapshots = FakeWardrobeSnapshotStore(local = local)
            val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
            val processor = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)

            processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

            assertNotNull(status, processor.state.first().pendingMergeSummary)
            assertTrue(status, snapshots.imports.isEmpty())
            assertTrue(status, drive.upserts.isEmpty())
        }
    }

    @Test
    fun unsyncedMetadataIsPendingWorkThatReachesTheDecision() = runBlocking {
        // Equal data: the base moves to the backup's revision only when nothing is pending. Pending work uploads instead
        // (remote already holds the data) and leaves the base where it was.
        for (unsynced in listOf(false, true)) {
            val wardrobe = FakeWardrobeRepository().apply { unsyncedMetadata = unsynced }
            val settings = FakeSettingsRepository()
            settings.setLastKnownRemoteRevision(40L, writerDeviceId = null)
            val backup = driveBackup(exportedSnapshot(garmentEntity("a")), writer = OTHER_DEVICE_ID, revision = 50L)
            val processor = newTestProcessor(
                settings = settings,
                wardrobe = wardrobe,
                snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a"))),
                drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup)),
            )

            processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

            assertEquals(unsynced.toString(), if (unsynced) 40L else 50L, settings.lastKnownRemoteRevision.first())
            assertNull(processor.state.first().pendingMergeSummary)
        }
    }

    @Test
    fun aLocalEditDuringTheDownloadStaysPendingAndTheCycleRetries() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val wardrobe = FakeWardrobeRepository()
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a")))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(wardrobe = wardrobe, snapshots = snapshots, drive = drive)
        drive.onSnapshotFetch = {
            // The user adds garment c while the backup downloads: the phone moves on after this cycle's export.
            snapshots.local = exportedSnapshot(garmentEntity("a"), garmentEntity("c", syncStatus = GarmentSyncStatus.Queued))
            wardrobe.addQueued("c", revision = 100L)
        }

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertEquals(1, drive.snapshotFetches)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
        assertEquals("Queued", wardrobe.statusOf("c"))
        assertEquals(SyncRunResult.Retry, processor.state.first().lastCycle?.result)
    }

    @Test
    fun anIdleReturnDoesNotBurnTheFreshInstallFlag() = runBlocking {
        // The phone is fresh and connected when the restore starts, but the connection drops before the restore's cycle runs.
        val settings = FakeSettingsRepository(status = DriveSyncConnectionStatus.Connected, freshInstallRestoreAttempted = false)
        val snapshots = FakeWardrobeSnapshotStore()
        snapshots.onExport = { settings.forceStatus(DriveSyncConnectionStatus.Disabled) }
        val backup = driveBackup(exportedSnapshot(garmentEntity("a")), writer = OTHER_DEVICE_ID, revision = 50L)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))

        newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        assertTrue(snapshots.exports > 0)
        assertEquals(0, drive.snapshotFetches)
        assertEquals(false, settings.settings.first().driveFreshInstallRestoreAttempted)
    }

    @Test
    fun aSettledFreshRestoreBurnsTheFreshInstallFlag() = runBlocking {
        val settings = FakeSettingsRepository(freshInstallRestoreAttempted = false)
        val snapshots = FakeWardrobeSnapshotStore()
        val backup = driveBackup(exportedSnapshot(garmentEntity("a")), writer = OTHER_DEVICE_ID, revision = 50L)

        newTestProcessor(settings = settings, snapshots = snapshots, drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup)))

        assertEquals(1, snapshots.imports.size)
        assertTrue(settings.settings.first().driveFreshInstallRestoreAttempted)
    }
    @Test
    fun restoreThenForcedImportWithAPendingVectorStaysSilent() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val embeddings = EmbeddingRepository(InMemoryEmbeddingDao(), clock = { 1_000L })
        val processor = WardrobeSyncOutboxProcessor(
            settingsRepository = FakeSettingsRepository(),
            wardrobeRepository = FakeWardrobeRepository(),
            snapshotRepository = snapshots,
            driveRepository = drive,
            syncWorkScheduler = FakeSyncWorkScheduler(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
            embeddingRepository = embeddings,
        )

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        // A vector computed after the restore is still pending when the trailing run starts: it uploads, it does not ask.
        embeddings.upsert("a", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-a", 5L)
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, snapshots.imports.size)
    }

    @Test
    fun pendingSummaryClearsOnceTheLocalMergeIsImportedWhateverTheUploadEnds() = runBlocking {
        // SYNC-BANNER (V-SYNC-BANNER-4): the import applied, so the proposal is answered even when the upload then fails.
        val uploadEnds: List<Pair<DriveSyncResult<DriveManifest>, SyncRunResult>> = listOf(
            DriveSyncResult.Blocked(DriveSyncDisabledReason.UserNotConnected, "Drive is not connected.") to SyncRunResult.AuthBlocked,
            DriveSyncResult.Failure(UnknownHostException("www.googleapis.com")) to SyncRunResult.Retry,
        )
        for ((upload, expected) in uploadEnds) {
            val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
            val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
            val snapshots = FakeWardrobeSnapshotStore(local = local)
            val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup), upsertResult = upload)
            val processor = newTestProcessor(snapshots = snapshots, drive = drive)

            processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
            assertNotNull(expected.toString(), processor.state.first().pendingMergeSummary)

            processor.confirmMerge()

            assertEquals(expected.toString(), 1, snapshots.imports.size)
            assertEquals(expected.toString(), expected, processor.state.first().lastCycle?.result)
            assertNull(expected.toString(), processor.state.first().pendingMergeSummary)

            // The upload recovers. A later cycle is not held by a stale proposal, so it finishes Success instead of NeedsUser.
            drive.upsertResult = null
            processor.enqueue(WardrobeSyncOperation.UpsertItem("a"))
            assertEquals(expected.toString(), SyncRunResult.Success, processor.state.first().lastCycle?.result)
        }
    }

    // Round 3. Each scenario runs through the real export and the Drive JSON codec, and each names its finding.

    @Test
    fun finding1_aGuardedRemotePhotoOnTheSamePhotoUriProposesInsteadOfRestoringSilently() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO))
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        // The blob fails to download: the guard is set, so a silent restore would hold the phone's file in attention.
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(guardedBackup(local, revision = 50L)))
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(40L, writerDeviceId = null) }
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNotNull(processor.state.first().pendingMergeSummary)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun finding1_aConfirmedMergeWithAGuardedRemotePhotoKeepsThePhonesFileAndHoldsTheUpload() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO))
        val backup = photoBackup(local, revision = 50L)
        // The blob fails to download: the fetched record has no restored file and carries the guard.
        val fetched = backup.copy(photos = backup.photos.map { it.copy(restoreFailureCategory = "remote_photo_blob_missing") })
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(fetched))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.confirmMerge()

        assertEquals(1, snapshots.imports.size)
        assertEquals(PHONE_PHOTO, snapshots.local.photos.single().localUri)
        assertNull(snapshots.local.photos.single().restoredLocalUri)
        assertTrue(drive.upserts.isEmpty())
        assertEquals(SyncRunResult.Retry, processor.state.first().lastCycle?.result)
    }

    @Test
    fun finding1_aFreshPhoneRestoreOfAGuardedPhotoRetriesWithoutAProposal() = runBlocking {
        // No phone file to protect: the guard holds the garment in attention and the cycle retries, with no dialog.
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(guardedBackup(exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO)), revision = 50L)))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(SyncRunResult.Retry, processor.state.first().lastCycle?.result)
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun finding1_anOrdinaryUploadAfterAGuardedMergeNeverReplacesTheNewerRemotePhoto() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO))
        // The remote photo is newer than the phone's, and its blob fails to download.
        val backup = guardedBackup(local, revision = 50L).let { remote ->
            remote.copy(photos = remote.photos.map { it.copy(revision = local.photos.single().revision + 1) })
        }
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        drive.manifest = DriveSyncResult.Success(DriveManifest.fromSnapshot(backup))
        val wardrobe = FakeWardrobeRepository()
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(40L, writerDeviceId = OTHER_DEVICE_ID) }
        val processor = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)

        // The user confirms the merge: the guarded garment keeps the phone's file, nothing is uploaded, and the base stays.
        processor.confirmMerge()

        assertEquals(SyncRunResult.Retry, processor.state.first().lastCycle?.result)
        assertEquals(40L, settings.lastKnownRemoteRevision.first())
        assertTrue(drive.upserts.isEmpty())

        // The phone edits another garment. The ordinary upload must propose, not replace the remote photo.
        wardrobe.addQueued("b")
        snapshots.local = exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO), garmentEntity("b"))
        processor.enqueue(WardrobeSyncOperation.UpsertItem("b"))

        assertTrue(drive.upserts.isEmpty())
        assertNotNull(processor.state.first().pendingMergeSummary)
    }

    @Test
    fun finding2_aClockCollisionAtTheBaseRevisionFromAnotherWriterProposes() = runBlocking {
        val local = exportedSnapshot(garmentEntity("a"), garmentEntity("c"))
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(50L, writerDeviceId = SECOND_OTHER_DEVICE_ID) }
        val snapshots = FakeWardrobeSnapshotStore(local = local)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNotNull(processor.state.first().pendingMergeSummary)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun finding2_aRestoreRecordsTheBackupWriterSoTheTrailingRunWithAPhotoStaysSilent() = runBlocking {
        val backup = photoBackup(exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO)), revision = 50L)
        // The restore materialises the blob, so the restored record carries a local file.
        val restored = backup.copy(photos = backup.photos.map { it.copy(restoredLocalUri = RESTORED_PHOTO) })
        val settings = FakeSettingsRepository()
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(restored))
        val processor = newTestProcessor(settings = settings, snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        assertEquals(OTHER_DEVICE_ID, settings.lastKnownRemoteWriterDeviceId.first())
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, snapshots.imports.size)
        assertTrue(drive.upserts.isEmpty())
    }

    @Test
    fun finding2_aRestoreWithAPhotoAndAPendingVectorStaysSilentOnTheTrailingRun() = runBlocking {
        val backup = photoBackup(exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO)), revision = 50L)
        val restored = backup.copy(photos = backup.photos.map { it.copy(restoredLocalUri = RESTORED_PHOTO) })
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot())
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(restored))
        val embeddings = EmbeddingRepository(InMemoryEmbeddingDao(), clock = { 1_000L })
        val processor = WardrobeSyncOutboxProcessor(
            settingsRepository = FakeSettingsRepository(),
            wardrobeRepository = FakeWardrobeRepository(),
            snapshotRepository = snapshots,
            driveRepository = drive,
            syncWorkScheduler = FakeSyncWorkScheduler(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined,
            embeddingRepository = embeddings,
        )

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        embeddings.upsert("a", EmbeddingKind.IMAGE, FloatArray(EmbeddingRecipe.DIMENSION), "hash-a", 5L)
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        // The trailing run may upload the vector; it must not ask the user.
        assertNull(processor.state.first().pendingMergeSummary)
        assertEquals(1, snapshots.imports.size)
    }

    @Test
    fun finding4_aLocalEditBetweenTheClaimAndTheExportIsPendingWorkForTheDecision() = runBlocking {
        val wardrobe = FakeWardrobeRepository()
        val settings = FakeSettingsRepository().apply { setLastKnownRemoteRevision(40L, writerDeviceId = null) }
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a")))
        // An unsynced tag edit lands after the cycle has read its pending work and before it exports.
        snapshots.onExport = { wardrobe.unsyncedMetadata = true }
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(settings = settings, wardrobe = wardrobe, snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        // Contained backup with no pending work would fast-forward; the late edit makes it a proposal.
        assertNotNull(processor.state.first().pendingMergeSummary)
        assertTrue(snapshots.imports.isEmpty())
    }

    @Test
    fun finding6_aPhotoSwapDuringTheDownloadWithNoGarmentRevisionChangeReturnsRetry() = runBlocking {
        val backup = driveBackup(exportedSnapshot(garmentEntity("a"), garmentEntity("b")), writer = OTHER_DEVICE_ID, revision = 50L)
        val snapshots = FakeWardrobeSnapshotStore(local = exportedSnapshot(garmentEntity("a", photoUri = PHONE_PHOTO)))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(backup))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)
        drive.onSnapshotFetch = {
            // The garment keeps its revision; only its photo changes. The merge was computed against the old photo.
            snapshots.local = exportedSnapshot(garmentEntity("a", photoUri = "file:///photos/a-new.jpg"))
        }

        processor.confirmMerge()

        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
        assertEquals(SyncRunResult.Retry, processor.state.first().lastCycle?.result)
    }

    /** A backup whose photo blobs fail to download: each photo carries the guard, as the fetch leaves it. */
    private fun guardedBackup(local: WardrobeSyncSnapshot, revision: Long): WardrobeSyncSnapshot =
        photoBackup(local, revision).let { snapshot ->
            snapshot.copy(photos = snapshot.photos.map { it.copy(restoreFailureCategory = "remote_photo_blob_missing") })
        }

    /** A backup of [local] written by [OTHER_DEVICE_ID], with each photo's upload hash, as Drive holds it. */
    private fun photoBackup(local: WardrobeSyncSnapshot, revision: Long): WardrobeSyncSnapshot =
        driveBackup(local, writer = OTHER_DEVICE_ID, revision = revision)
            .let { snapshot -> snapshot.copy(photos = snapshot.photos.map { it.copy(contentHash = "hash-${it.garmentId}") }) }

    private companion object {
        const val PHONE_PHOTO = "file:///photos/a.jpg"
        const val RESTORED_PHOTO = "file:///restored/a.jpg"
    }
}
