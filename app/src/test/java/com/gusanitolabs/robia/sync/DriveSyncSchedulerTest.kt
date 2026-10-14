package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveSyncSchedulerTest {
    @Test
    fun savingAGarmentRequestsTheUniqueSyncWorkOnce() = runBlocking {
        val scheduler = FakeSyncWorkScheduler()
        val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1")),
            drive = FakeDriveWardrobeRepository(),
            scheduler = scheduler,
        )

        processor.enqueue(WardrobeSyncOperation.UpsertGarments(touchedGarmentIds = setOf("garment-1")))

        assertEquals(1, scheduler.requestSyncCount)
    }

    @Test
    fun disconnectedDriveCancelsScheduledWork() = runBlocking {
        val scheduler = FakeSyncWorkScheduler()
        newTestProcessor(
            settings = FakeSettingsRepository(DriveSyncConnectionStatus.Disconnected),
            scheduler = scheduler,
        )

        withTimeout(5_000) {
            while (scheduler.cancelAllCount == 0) delay(10)
        }
        assertEquals(0, scheduler.ensurePeriodicCount)
    }
}
