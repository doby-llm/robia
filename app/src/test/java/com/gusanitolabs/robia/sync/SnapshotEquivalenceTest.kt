package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DismissedPair
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotEquivalenceTest {
    @Test
    fun sameContentWithRemoteSourceHashIsEquivalentToLocalWithoutSourceHash() {
        val local = snapshot(photo(contentHash = "content-1", sourceHash = null))
        val remote = snapshot(photo(contentHash = "content-1", sourceHash = "source-1"))

        assertTrue(local.equivalentForUpload(remote))
    }

    @Test
    fun differentContentIsNotEquivalentEvenWhenRemoteHasSourceHash() {
        val local = snapshot(photo(contentHash = "content-2", sourceHash = null))
        val remote = snapshot(photo(contentHash = "content-1", sourceHash = "source-1"))

        assertFalse(local.equivalentForUpload(remote))
    }

    @Test
    fun differentSourceHashesAreNotEquivalent() {
        val local = snapshot(photo(contentHash = "content-1", sourceHash = "source-2"))
        val remote = snapshot(photo(contentHash = "content-2", sourceHash = "source-1"))

        assertFalse(local.equivalentForUpload(remote))
    }

    @Test
    fun matchingSourceHashesAreEquivalentWhenBothSidesHaveThem() {
        val local = snapshot(photo(contentHash = "content-2", sourceHash = "source-1"))
        val remote = snapshot(photo(contentHash = "content-1", sourceHash = "source-1"))

        assertTrue(local.equivalentForUpload(remote))
    }

    @Test
    fun snapshotsDifferingOnlyInDismissedDuplicatesAreNotEquivalent() {
        val local = WardrobeSyncSnapshot(dismissedDuplicates = listOf(DismissedPair("a", "b", dismissedAtEpochMillis = 1L)))
        val remote = WardrobeSyncSnapshot()

        assertFalse(local.equivalentForUpload(remote))
        assertFalse(local.unchangedSince(remote))
        assertFalse(local.remoteAlreadyHolds(remote, hasPendingEmbeddings = false, hasPendingCloudDeletion = false))
    }

    @Test
    fun dismissedDuplicatesInDifferentOrderAreEquivalent() {
        val local = WardrobeSyncSnapshot(
            dismissedDuplicates = listOf(
                DismissedPair("a", "b", dismissedAtEpochMillis = 1L),
                DismissedPair("c", "d", dismissedAtEpochMillis = 2L),
            ),
        )
        val remote = WardrobeSyncSnapshot(
            dismissedDuplicates = listOf(
                DismissedPair("d", "c", dismissedAtEpochMillis = 2L),
                DismissedPair("b", "a", dismissedAtEpochMillis = 1L),
            ),
        )

        assertTrue(local.equivalentForUpload(remote))
        assertTrue(local.unchangedSince(remote))
    }

    @Test
    fun absentAndEmptyDismissedDuplicatesAreEquivalent() {
        val absent = WardrobeSyncSnapshot()
        val empty = WardrobeSyncSnapshot(dismissedDuplicates = emptyList())

        assertTrue(absent.equivalentForUpload(empty))
        assertTrue(empty.equivalentForUpload(absent))
    }

    private fun snapshot(photo: GarmentPhotoRecord): WardrobeSyncSnapshot = WardrobeSyncSnapshot(photos = listOf(photo))

    private fun photo(contentHash: String, sourceHash: String?): GarmentPhotoRecord = GarmentPhotoRecord(
        garmentId = "garment-1",
        localUri = "content://local/1",
        blobPath = "photos/garment-1/0123456789abcdef.webp",
        mimeType = "image/webp",
        contentHash = contentHash,
        sourceHash = sourceHash,
    )
}
