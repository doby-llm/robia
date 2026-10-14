package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectFlowTest {
    @Test
    fun emptyPhoneRestoresTheBackupAutomaticallyWithoutMergeDialog() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore()
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("a", "b")))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertEquals(listOf(listOf("a", "b")), snapshots.imports.map { snapshot -> snapshot.garments.map { it.id } })
        assertNull(processor.state.first().pendingMergeSummary)
    }

    @Test
    fun nonEmptyPhoneShowsMergeSummaryAndChangesNothingUntilConfirmed() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a", "b"))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("b", "c", "d")))
        val processor = newTestProcessor(snapshots = snapshots, drive = drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        val summary = withTimeout(5_000) {
            processor.state.first { it.pendingMergeSummary != null }.pendingMergeSummary
        }
        assertEquals(MergeSummary(remoteGarmentCount = 3, localGarmentCount = 2, mergedGarmentCount = 4), summary)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
    }
}
