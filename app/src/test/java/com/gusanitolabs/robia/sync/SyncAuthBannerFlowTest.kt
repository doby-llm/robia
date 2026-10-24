package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.processorWithDrive
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAuthBannerFlowTest {
    private val signInRequired = SyncAttention(SyncAttentionKind.SignInRequired)

    @Test
    fun aNewCycleStartsWithoutTheSignInBannerOfTheLastCycle() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
        val signInAtUpload = mutableListOf<Boolean>()
        lateinit var processor: WardrobeSyncOutboxProcessor
        val drive = object : DriveWardrobeRepository by FakeDriveWardrobeRepository() {
            override suspend fun upsertSnapshot(
                snapshot: WardrobeSyncSnapshot,
                remote: WardrobeSyncSnapshot?,
                onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int) -> Unit)?,
            ): DriveSyncResult<DriveManifest> {
                signInAtUpload += signInRequired in processor.state.first().attention
                return DriveSyncResult.Blocked(
                    DriveSyncDisabledReason.UserNotConnected,
                    "Google Drive authorization is required before sync can continue.",
                )
            }
        }
        processor = processorWithDrive(
            drive = drive,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1", "garment-2")),
        )

        assertEquals(SyncRunResult.AuthBlocked, processor.runOnce(SyncTrigger.Background))
        assertTrue(signInRequired in processor.state.first().attention)

        // A new garment gives the next cycle work to do; the banner from the blocked cycle must not carry over into it.
        wardrobe.addQueued("garment-2")
        assertEquals(SyncRunResult.AuthBlocked, processor.runOnce(SyncTrigger.Background))

        assertEquals(listOf(false, false), signInAtUpload)
    }
}
