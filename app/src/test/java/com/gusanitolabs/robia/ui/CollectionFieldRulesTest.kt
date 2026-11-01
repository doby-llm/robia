package com.gusanitolabs.robia.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CollectionsBookmark
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionFieldRulesTest {
    private val lisbon = GarmentTag(
        id = "collection-lisbon",
        categoryId = "collection",
        name = "Lisbon trip",
        sortOrder = 0,
    )
    private val workCapsule = GarmentTag(
        id = "collection-work",
        categoryId = "collection",
        name = "Work capsule",
        sortOrder = 1,
    )
    private val casual = GarmentTag(id = "occasion-casual", categoryId = "occasion", name = "Casual", sortOrder = 0)
    private val dresses = GarmentTag(id = "category-dresses", categoryId = "category", name = "Dresses", sortOrder = 0)

    @Test
    fun collectionsAreMultiSelectAndOccasionsAndSeasonsStayMultiSelect() {
        assertTrue(isMultiSelectCategory("collection"))
        assertTrue(isMultiSelectCategory("occasion"))
        assertTrue(isMultiSelectCategory("season"))
        assertFalse(isMultiSelectCategory("category"))
        assertFalse(isMultiSelectCategory("location"))
    }

    @Test
    fun editorRowIsHiddenWithNoCollectionsAndShownWhenOneExistsOrTheGarmentHasOne() {
        assertFalse(showsCollectionRow(availableCollectionCount = 0, garmentCollectionCount = 0))
        assertTrue(showsCollectionRow(availableCollectionCount = 1, garmentCollectionCount = 0))
        assertTrue(showsCollectionRow(availableCollectionCount = 0, garmentCollectionCount = 1))
        assertTrue(showsCollectionRow(availableCollectionCount = 3, garmentCollectionCount = 2))
    }

    @Test
    fun detailRowIsShownOnlyForGarmentsInACollection() {
        assertFalse(showsCollectionDetailRow(garmentCollectionCount = 0))
        assertTrue(showsCollectionDetailRow(garmentCollectionCount = 1))
    }

    @Test
    fun filterCardIsShownOnlyWhenACollectionExists() {
        assertFalse(showsCollectionFilterCard(availableCollectionCount = 0))
        assertTrue(showsCollectionFilterCard(availableCollectionCount = 2))
    }

    @Test
    fun savingAGarmentInTwoCollectionsKeepsBothLinksAndItsOtherTags() {
        val available = listOf(dresses, casual, lisbon, workCapsule)
        val selected = listOf(dresses.id, casual.id, lisbon.id, workCapsule.id)

        val saved = savedGarmentTags(availableTags = available, selectedTagIds = selected)

        assertEquals(setOf(dresses.id, casual.id, lisbon.id, workCapsule.id), saved.map(GarmentTag::id).toSet())
        assertEquals(available.size, saved.size)
    }

    @Test
    fun savingOnlyKeepsSelectedAvailableTags() {
        val saved = savedGarmentTags(
            availableTags = listOf(dresses, lisbon, workCapsule),
            selectedTagIds = listOf(lisbon.id, "collection-deleted"),
        )

        assertEquals(listOf(lisbon), saved)
    }

    @Test
    fun collectionPickerIsTitledCollectionsAndSaveAndWiringStayOnTheSharedRules() {
        val editor = source("app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt")

        assertTrue(editor.contains("\"collection\" -> R.string.metadata_collections"))
        assertTrue(editor.contains("multi = isMultiSelectCategory(categoryId)"))
        assertTrue(editor.contains("showsCollectionRow(availableCollectionCount, selectedIn(\"collection\").size)"))
        assertTrue(editor.contains("savedGarmentTags(availableTags, selectedTagIds)"))
    }

    @Test
    fun detailAndFiltersWireTheCollectionRowAndNotInAnyLabel() {
        val detail = source("app/src/main/java/com/gusanitolabs/robia/ui/ItemDetailScreen.kt")
        val filters = source("app/src/main/java/com/gusanitolabs/robia/ui/AdvancedFiltersScreen.kt")

        assertTrue(detail.contains("showsCollectionDetailRow(item.tags.count { tag -> tag.categoryId == \"collection\" })"))
        assertTrue(filters.contains("collectionTags = availableTags.filter { it.categoryId == \"collection\" }"))
        assertTrue(filters.contains("showsCollectionFilterCard(collectionTags.size)"))
        assertTrue(filters.contains("notSetLabelRes = R.string.filter_collection_none"))
    }

    @Test
    fun collectionChipTitleAndIconAreNotTheRawIdOrTheCheckroomFallback() {
        assertEquals(R.string.filter_collection, categoryTitleRes("collection"))
        assertEquals(Icons.Outlined.CollectionsBookmark, categoryIconFor("collection"))
        assertNotEquals(Icons.Outlined.Checkroom, categoryIconFor("collection"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
