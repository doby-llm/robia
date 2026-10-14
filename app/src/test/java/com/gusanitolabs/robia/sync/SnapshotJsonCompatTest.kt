package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSnapshotMetadata
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.toBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotJsonCompatTest {
    @Test
    fun roundTripPreservesEmbeddingsTagFieldsAndWriterDevice() {
        val embedding = EmbeddingSyncRecord(
            kind = "image",
            modelId = EmbeddingRecipe.MODEL_ID,
            recipeId = EmbeddingRecipe.RECIPE_ID,
            dim = EmbeddingRecipe.DIMENSION,
            inputHash = "0123456789abcdef",
            computedAtEpochMillis = 1_700_000_000_123L,
            b64 = FloatArray(EmbeddingRecipe.DIMENSION) { index -> index * 0.5f }.toBase64(),
        )
        val snapshot = WardrobeSyncSnapshot(
            metadata = WardrobeSnapshotMetadata(revision = 9L, writerDeviceId = "device-uuid"),
            taxonomies = WardrobeTaxonomySnapshot(
                categories = listOf(TagCategorySyncRecord(id = "category", name = "Category", sortOrder = 10, isSystem = true)),
                tags = listOf(
                    TagSyncRecord(
                        id = "category-accessories",
                        categoryId = "category",
                        name = "Accessories",
                        sortOrder = 180,
                        isSystem = false,
                        revision = 4L,
                        updatedAtEpochMillis = 4L,
                        description = "a belt, a scarf or a watch",
                        suggestOccasions = false,
                    ),
                ),
            ),
            garments = listOf(
                GarmentSyncRecord(
                    id = "garment-1",
                    name = "Belt",
                    notes = "",
                    fitValue = null,
                    isFavorite = false,
                    isArchived = false,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    revision = 2L,
                    embeddings = listOf(embedding),
                ),
            ),
        )

        val decoded = DriveSnapshotJson.decode(DriveSnapshotJson.encode(snapshot))

        assertEquals("device-uuid", decoded.metadata.writerDeviceId)
        assertEquals(listOf(embedding), decoded.garments.single().embeddings)
        val tag = decoded.taxonomies.tags.single()
        assertEquals("a belt, a scarf or a watch", tag.description)
        assertFalse(tag.suggestOccasions)
    }

    @Test
    fun encoderOmitsNewFieldsWhenTheyHaveNoValue() {
        val json = DriveSnapshotJson.encode(
            WardrobeSyncSnapshot(
                taxonomies = WardrobeTaxonomySnapshot(
                    tags = listOf(TagSyncRecord(id = "category-shirts", categoryId = "category", name = "Shirts", sortOrder = 150, isSystem = false)),
                ),
                garments = listOf(
                    GarmentSyncRecord(
                        id = "garment-1",
                        name = "Shirt",
                        notes = "",
                        fitValue = null,
                        isFavorite = false,
                        isArchived = false,
                        createdAtEpochMillis = 1L,
                        updatedAtEpochMillis = 1L,
                    ),
                ),
            ),
        )

        assertFalse(json.contains("\"description\""))
        assertFalse(json.contains("\"embeddings\""))
        assertFalse(json.contains("\"writerDeviceId\""))
        assertTrue(json.contains("\"suggestOccasions\":true"))
    }

    /**
     * Shape of a snapshot written before DEC-007: no `description`, `suggestOccasions`, `embeddings`
     * or `writerDeviceId`. Mirrors what DriveSnapshotJson.encode produced on the base commit.
     */
    @Test
    fun snapshotWithoutNewFieldsDecodesWithDefaults() {
        val legacy = """
            {
              "metadata": {"schemaVersion": 2, "appPackage": "com.gusanitolabs.robia", "generatedAtEpochMillis": 1700000000000, "wardrobeId": "wardrobe-1", "revision": 5},
              "taxonomies": {
                "categories": [{"id": "category", "name": "Category", "sortOrder": 10, "isSystem": true, "isArchived": false, "revision": 0, "updatedAtEpochMillis": 0}],
                "tags": [
                  {"id": "category-accessories", "categoryId": "category", "name": "Accessories", "sortOrder": 180, "isSystem": false, "isArchived": false, "revision": 3, "updatedAtEpochMillis": 3},
                  {"id": "category-jewelry", "categoryId": "category", "name": "Jewelry", "sortOrder": 170, "isSystem": false, "isArchived": false, "revision": 3, "updatedAtEpochMillis": 3},
                  {"id": "category-shirts", "categoryId": "category", "name": "Shirts", "sortOrder": 150, "isSystem": false, "isArchived": false, "revision": 0, "updatedAtEpochMillis": 0}
                ],
                "mainColors": []
              },
              "garments": [
                {"id": "garment-1", "name": "Shirt", "notes": "", "isFavorite": false, "isArchived": false, "createdAtEpochMillis": 1, "updatedAtEpochMillis": 2, "revision": 2}
              ],
              "garmentTags": [{"garmentId": "garment-1", "tagId": "category-shirts", "revision": 2, "updatedAtEpochMillis": 2}],
              "garmentColors": [],
              "photos": [],
              "tombstones": []
            }
        """.trimIndent()

        val decoded = DriveSnapshotJson.decode(legacy)

        assertNull(decoded.metadata.writerDeviceId)
        assertEquals("wardrobe-1", decoded.metadata.wardrobeId)
        assertEquals(emptyList<EmbeddingSyncRecord>(), decoded.garments.single().embeddings)
        val tagsById = decoded.taxonomies.tags.associateBy(TagSyncRecord::id)
        assertFalse(tagsById.getValue("category-accessories").suggestOccasions)
        assertFalse(tagsById.getValue("category-jewelry").suggestOccasions)
        assertTrue(tagsById.getValue("category-shirts").suggestOccasions)
        assertNull(tagsById.getValue("category-shirts").description)
    }
}
