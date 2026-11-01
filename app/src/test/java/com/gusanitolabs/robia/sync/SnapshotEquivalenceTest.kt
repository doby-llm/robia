package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DismissedPair
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.sync.fakes.exportedSnapshot
import com.gusanitolabs.robia.sync.fakes.garmentEntity
import com.gusanitolabs.robia.sync.fakes.garmentTagEntity
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

    // V-C01-4 (R4-D03): an upgrade seeds the empty `collection` category; it is not a user difference until a collection exists.

    @Test
    fun anEmptyDefaultCollectionRowIsIgnoredInBothDirections() {
        val withRow = exportedSnapshot(garmentEntity("a"), categories = listOf(collectionCategory()))
        val withoutRow = exportedSnapshot(garmentEntity("a"))

        assertTrue(withRow.equivalentForUpload(withoutRow))
        assertTrue(withoutRow.equivalentForUpload(withRow))
    }

    @Test
    fun aCollectionRowWithATagIsComparedAndEqualOnlyWhenBothSidesHoldIt() {
        val withTag = exportedSnapshot(garmentEntity("a"), categories = listOf(collectionCategory()), tags = listOf(collectionTag()))
        val withoutRow = exportedSnapshot(garmentEntity("a"))
        val sameTag = exportedSnapshot(garmentEntity("a"), categories = listOf(collectionCategory()), tags = listOf(collectionTag()))

        assertFalse(withTag.equivalentForUpload(withoutRow))
        assertFalse(withoutRow.equivalentForUpload(withTag))
        assertTrue(withTag.equivalentForUpload(sameTag))
    }

    @Test
    fun aRenamedOrReorderedDefaultCollectionRowIsCompared() {
        val plain = exportedSnapshot(garmentEntity("a"))
        val renamed = exportedSnapshot(garmentEntity("a"), categories = listOf(collectionCategory(name = "Trips")))
        val moved = exportedSnapshot(garmentEntity("a"), categories = listOf(collectionCategory(sortOrder = 70)))

        assertFalse(renamed.equivalentForUpload(plain))
        assertFalse(plain.equivalentForUpload(renamed))
        assertFalse(moved.equivalentForUpload(plain))
        assertFalse(plain.equivalentForUpload(moved))
    }

    @Test
    fun otherEmptyCategoriesAreStillCompared() {
        val plain = exportedSnapshot(garmentEntity("a"))
        val custom = exportedSnapshot(
            garmentEntity("a"),
            categories = listOf(TagCategoryEntity(id = "style", name = "Style", sortOrder = 60, isSystem = false)),
        )
        val archivedDefault = WardrobeSyncSnapshot(
            taxonomies = WardrobeTaxonomySnapshot(
                categories = listOf(TagCategorySyncRecord(id = "collection", name = "Collections", sortOrder = 60, isSystem = true, isArchived = true)),
            ),
        )

        assertFalse(custom.equivalentForUpload(plain))
        assertFalse(plain.equivalentForUpload(custom))
        assertFalse(archivedDefault.equivalentForUpload(WardrobeSyncSnapshot()))
        assertFalse(WardrobeSyncSnapshot().equivalentForUpload(archivedDefault))
    }

    private fun collectionCategory(name: String = "Collections", sortOrder: Int = 60): TagCategoryEntity =
        TagCategoryEntity(id = "collection", name = name, sortOrder = sortOrder, isSystem = true)

    private fun collectionTag(): GarmentTagEntity = garmentTagEntity("trips", categoryId = "collection")

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
