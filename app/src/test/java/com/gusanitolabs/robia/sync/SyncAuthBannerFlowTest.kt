package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.processorWithDrive
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncAuthBannerFlowTest {
    private val signInRequired = SyncAttention(SyncAttentionKind.SignInRequired)

    @Test
    fun aCycleThatEndsEarlyClearsTheSignInBannerOfTheCycleBefore() = runBlocking<Unit> {
        val wardrobe = FakeWardrobeRepository().apply { addQueued("garment-1") }
        val settings = FakeSettingsRepository()
        val drive = object : DriveWardrobeRepository by FakeDriveWardrobeRepository() {
            override suspend fun upsertSnapshot(
                snapshot: WardrobeSyncSnapshot,
                remote: WardrobeSyncSnapshot?,
                onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int, reusedPhotos: Int) -> Unit)?,
            ): DriveSyncResult<DriveManifest> = DriveSyncResult.Blocked(
                DriveSyncDisabledReason.UserNotConnected,
                "Google Drive authorization is required before sync can continue.",
            )
        }
        val processor = processorWithDrive(
            drive = drive,
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("garment-1")),
            settings = settings,
        )

        assertEquals(SyncRunResult.AuthBlocked, processor.runOnce(SyncTrigger.Background))
        withTimeout(5_000) { processor.state.first { state -> signInRequired in state.attention } }

        // A cycle that ends before any Drive call must not keep the banner of the blocked cycle.
        settings.forceStatus(DriveSyncConnectionStatus.NeedsAttention)
        assertEquals(SyncRunResult.NeedsUser, processor.runOnce(SyncTrigger.Background))
        withTimeout(5_000) { processor.state.first { state -> signInRequired !in state.attention } }
    }
}
