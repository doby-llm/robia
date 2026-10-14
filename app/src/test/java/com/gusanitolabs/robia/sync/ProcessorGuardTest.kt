package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessorGuardTest {
    @Test
    fun guardedGarmentDoesNotBlockUploadOfAnEditedGarment() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply {
            addSynced("guarded-garment")
            markGuarded("guarded-garment")
            addQueued("edited-garment")
        }
        val local = snapshotWithGarments("guarded-garment", "edited-garment").copy(
            photos = listOf(
                GarmentPhotoRecord(
                    garmentId = "guarded-garment",
                    localUri = "content://guarded",
                    restoreFailureCategory = "remote_photo_transient",
                ),
            ),
        )
        val drive = FakeDriveWardrobeRepository()
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = local),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        assertEquals(1, drive.upserts.size)
        assertEquals(
            listOf("edited-garment", "guarded-garment"),
            drive.upserts.single().garments.map { garment -> garment.id },
        )
        assertEquals("Synced", wardrobe.statusOf("edited-garment"))
    }
}
