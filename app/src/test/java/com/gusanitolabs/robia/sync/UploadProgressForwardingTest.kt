package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.fakes.FakeDriveSnapshotApi
import com.gusanitolabs.robia.sync.fakes.FakePhotoSources
import com.gusanitolabs.robia.sync.fakes.localPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
