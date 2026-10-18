package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** A kept blob is reused only while Drive holds the size the record was written with; otherwise the same key is uploaded again. */
class PlanPhotoUploadsSizeTest {
    private val blobPath = "photos/g1/0123456789abcdef.webp"
    private val record = LastUploadedPhoto("g1", "hash", blobPath, null, 10L, "image/webp")

    @Test
    fun aBlobOfTheRecordedSizeIsReused() {
        val index = DriveFileIndex(listOf(DriveFileMetadata(id = "id-1", name = blobPath, size = 10L)))

        assertEquals(listOf(PhotoUploadAction.Reuse("g1", record)), plan(index))
    }

    @Test
    fun aBlobWhoseListedSizeDiffersIsUploadedUnderTheSameKey() {
        val index = DriveFileIndex(listOf(DriveFileMetadata(id = "id-1", name = blobPath, size = 4L)))

        assertEquals(listOf(PhotoUploadAction.Upload("g1", "hash")), plan(index))
    }

    private fun plan(index: DriveFileIndex) = planPhotoUploads(
        local = listOf(LocalPhotoState.Readable("g1", "hash")),
        previous = mapOf("g1" to record),
        index = index,
    )
}
