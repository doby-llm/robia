package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.localPhoto
import com.gusanitolabs.robia.sync.fakes.processorWithDrive
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadProgressForwardingTest {
    private val api = FakeDriveSnapshotApi()
    private val phone = FakePhotoSources()

    @Test
    fun pipelineReportsPhotosDoneOverTotalFromZeroToComplete() = runBlocking {
        val photos = (1..3).map { index ->
            localPhoto("g$index").also { photo -> phone.sources[photo.localUri] = "photo $index" }
        }
        val reported = mutableListOf<Pair<Int, Int>>()

        val result = phone.pipeline(api).upload(
            accessToken = "token",
            snapshot = WardrobeSyncSnapshot(photos = photos),
            remote = null,
            previous = emptyMap(),
            onPhotoProgress = { done, total -> reported += done to total },
        )

        assertTrue(result is DriveSyncResult.Success)
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), reported)
    }

    @Test
    fun uploadPhotoProgressReachesTheOverlayWhileUploading() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val processor = processorWithDrive(
            drive = GatedUploadDrive(FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("b"))), gate),
            wardrobe = FakeWardrobeRepository().apply { addQueued("a") },
            snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a")),
        )

        val confirm = launch(Dispatchers.Default) { processor.confirmMerge() }
        val during = withTimeout(10_000) {
            processor.state.first { it.restoreProgress?.uploadProgress != null }.restoreProgress
        }
        assertEquals(CloudRestorePhase.Uploading, during?.phase)
        assertEquals(RestoreUploadProgress(completedPhotos = 1, totalPhotos = 2), during?.uploadProgress)

        gate.complete(Unit)
        confirm.join()
        assertNull(processor.state.first().restoreProgress)
    }
}

/** Reports one photo of two, then holds the upload until the test releases it, so the overlay can be observed mid-upload. */
private class GatedUploadDrive(
    private val base: FakeDriveWardrobeRepository,
    private val gate: CompletableDeferred<Unit>,
) : DriveWardrobeRepository by base {
    override suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int) -> Unit)?,
    ): DriveSyncResult<DriveManifest> {
        onPhotoProgress?.invoke(1, 2)
        gate.await()
        return base.upsertSnapshot(snapshot, remote, onPhotoProgress)
    }
}
