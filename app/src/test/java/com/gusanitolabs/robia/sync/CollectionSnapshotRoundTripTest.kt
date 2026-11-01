package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V-C01-6 and REQ-COL-10: a backup that holds a `collection` category, two collection tags and three links survives the
 * restore path and the next export. The restore merges the phone with the backup ([mergeSnapshots]) before it writes; the
 * `withoutTombstonedTaxonomy` filter of the local repository is file-private and drops nothing here (no tombstones).
 */
class CollectionSnapshotRoundTripTest {
    @Test
    fun collectionCategoryTagsAndLinksSurviveImportAndExport() {
        val backup = DriveSnapshotJson.decode(DriveSnapshotJson.encode(collectionBackup()))

        val imported = mergeSnapshots(WardrobeSyncSnapshot(), backup)
        val exported = DriveSnapshotJson.decode(DriveSnapshotJson.encode(imported))

        assertEquals(
            listOf(Triple("collection", "Collections", 60)),
            exported.taxonomies.categories
                .filter { category -> category.id == "collection" }
                .map { category -> Triple(category.id, category.name, category.sortOrder) },
        )
        assertEquals(
            setOf("trips" to "collection", "beach" to "collection"),
            exported.taxonomies.tags.map { tag -> tag.id to tag.categoryId }.toSet(),
        )
        assertEquals(
            setOf("g1" to "beach", "g1" to "trips", "g2" to "trips"),
            exported.garmentTags.map { link -> link.garmentId to link.tagId }.toSet(),
        )
    }

    private fun collectionBackup(): WardrobeSyncSnapshot = WardrobeSyncSnapshot(
        taxonomies = WardrobeTaxonomySnapshot(
            categories = listOf(TagCategorySyncRecord(id = "collection", name = "Collections", sortOrder = 60, isSystem = true, revision = 5L)),
            tags = listOf(
                TagSyncRecord(id = "trips", categoryId = "collection", name = "Trips", sortOrder = 10, isSystem = false, revision = 5L),
                TagSyncRecord(id = "beach", categoryId = "collection", name = "Beach", sortOrder = 20, isSystem = false, revision = 5L),
            ),
        ),
        garments = listOf(
            GarmentSyncRecord(
                id = "g1",
                name = "Linen shirt",
                notes = "",
                fitValue = null,
                isFavorite = false,
                isArchived = false,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L,
                revision = 100L,
            ),
            GarmentSyncRecord(
                id = "g2",
                name = "Denim jacket",
                notes = "",
                fitValue = null,
                isFavorite = false,
                isArchived = false,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L,
                revision = 100L,
            ),
        ),
        garmentTags = listOf(
            GarmentTagMappingRecord(garmentId = "g1", tagId = "trips", revision = 100L),
            GarmentTagMappingRecord(garmentId = "g1", tagId = "beach", revision = 100L),
            GarmentTagMappingRecord(garmentId = "g2", tagId = "trips", revision = 100L),
        ),
    )
}
