package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.newTestProcessor
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    @Test
    fun guardedPhotoRecordFromRemoteJsonReachesTheUploadWhenThisPhoneHasNoCache() = runBlocking {
        val remotePhoto = GarmentPhotoRecord(
            garmentId = "guarded-garment",
            localUri = "",
            blobPath = "photos/guarded-garment/0123456789abcdef.webp",
            mimeType = "image/webp",
            contentHash = "hash-1",
        )
        val wardrobe = FakeWardrobeRepository().apply {
            addSynced("guarded-garment")
            markGuarded("guarded-garment")
            addQueued("edited-garment")
        }
        val drive = FakeDriveWardrobeRepository(
            snapshot = DriveSyncResult.Success(snapshotWithGarments("guarded-garment").copy(photos = listOf(remotePhoto))),
        )
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = guardedLocalSnapshot()),
            drive = drive,
        )

        assertEquals(SyncRunResult.Success, processor.runOnce(SyncTrigger.Foreground))

        // The processor hands the remote photo record to the upload, which copies it for the guarded garment.
        val remoteForUpload = drive.upsertRemotes.single()
        assertEquals(listOf(remotePhoto), remoteForUpload?.photos)
        assertEquals(1, drive.recordsFetches)
        assertEquals("Synced", wardrobe.statusOf("edited-garment"))
    }

    @Test
    fun failedRemoteRecordFetchRetriesWithoutUploading() = runBlocking {
        val wardrobe = FakeWardrobeRepository().apply {
            addSynced("guarded-garment")
            markGuarded("guarded-garment")
            addQueued("edited-garment")
        }
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Failure(IOException("Drive unreachable")))
        val processor = newTestProcessor(
            wardrobe = wardrobe,
            snapshots = FakeWardrobeSnapshotStore(local = guardedLocalSnapshot()),
            drive = drive,
        )

        assertEquals(SyncRunResult.Retry, processor.runOnce(SyncTrigger.Foreground))

        assertTrue(drive.upserts.isEmpty())
        assertEquals("Queued", wardrobe.statusOf("edited-garment"))
    }

    private fun guardedLocalSnapshot() = snapshotWithGarments("guarded-garment", "edited-garment").copy(
        photos = listOf(
            GarmentPhotoRecord(
                garmentId = "guarded-garment",
                localUri = "content://guarded",
                restoreFailureCategory = "remote_photo_transient",
            ),
        ),
    )
}
