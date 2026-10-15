package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** H01 finding A, E and B: what the processor asks WorkManager for when it starts, and when a forced operation is requested. */
class SyncInitRequestTest {
    /** A claim made 60 s ago is inside the 15 min stale window, so only a recovery at the current time frees it. */
    @Test
    fun interruptedClaimFromAKilledProcessProducesAnInitRequest() = runBlocking {
        val wardrobe = FakeWardrobeRepository()
        val scheduler = FakeSyncWorkScheduler()
        wardrobe.addInterruptedClaim("garment-1", startedAtEpochMillis = System.currentTimeMillis() - 60_000L)

        newTestProcessor(wardrobe = wardrobe, scheduler = scheduler)

        assertEquals("Queued", wardrobe.statusOf("garment-1"))
        assertEquals(listOf("init"), scheduler.requestReasons)
    }

    @Test
    fun initRequestIsSentOnlyWhenDriveCanRunIt() = runBlocking {
        for (status in listOf(DriveSyncConnectionStatus.Connected, DriveSyncConnectionStatus.NeedsAttention)) {
            val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
            val scheduler = FakeSyncWorkScheduler()

            newTestProcessor(settings = FakeSettingsRepository(status), wardrobe = wardrobe, scheduler = scheduler)

            assertEquals("status=$status", listOf("init"), scheduler.requestReasons)
        }
    }

    /** H01 finding E: a Drive that is not connected cannot run the request; each cold start would wake WorkManager for nothing. */
    @Test
    fun initRequestIsSkippedWhenDriveIsNotConnected() = runBlocking {
        for (status in listOf(DriveSyncConnectionStatus.Disconnected, DriveSyncConnectionStatus.Disabled, DriveSyncConnectionStatus.NotConfigured)) {
            val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
            val scheduler = FakeSyncWorkScheduler()

            newTestProcessor(settings = FakeSettingsRepository(status), wardrobe = wardrobe, scheduler = scheduler)

            assertEquals("status=$status", 0, scheduler.requestSyncCount)
        }
    }

    /** H01 finding B: an explicit import with nothing pending is still requested, so a failed inline run is retried in the background. */
    @Test
    fun importFullSnapshotWithNothingPendingIsStillRequested() = runBlocking {
        val scheduler = FakeSyncWorkScheduler()
        val processor = newTestProcessor(scheduler = scheduler)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))

        assertEquals(listOf("enqueue:ImportFullSnapshot"), scheduler.requestReasons)
    }
}
