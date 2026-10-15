package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class TagReviewPolishTest {
    private val formal = "occasion-formal"

    @Test
    fun deletionCountsOnlyTheGarmentsThatCarriedTheDeletedTag() {
        val garments = (1..35).map { index ->
            garment("g$index", if (index <= 3) formal else "occasion-everyday")
        }

        assertEquals(3, affectedGarmentCount(changeSet(TagOperation.Deleted, "occasion", formal), garments, proposalCount = 0))
    }

    @Test
    fun addedAndRenamedCountTheProposalsFoundSoFarNotTheWardrobeSize() {
        val garments = (1..35).map { index -> garment("g$index", "occasion-everyday") }

        assertEquals(2, affectedGarmentCount(changeSet(TagOperation.Added, "occasion", formal), garments, proposalCount = 2))
        assertEquals(0, affectedGarmentCount(changeSet(TagOperation.Renamed, "occasion", formal), garments, proposalCount = 0))
    }

    @Test
    fun pickerLabelUsesTheLocalizedLabelThenTheStoredNameThenTheId() {
        assertEquals("Abrigos", tagPickerLabel("category-coats", "Coats", mapOf("category-coats" to "Abrigos")))
        assertEquals("Coats", tagPickerLabel("category-coats", "Coats", mapOf("category-coats" to "  ")))
        assertEquals("category-coats", tagPickerLabel("category-coats", " ", emptyMap()))
    }

    @Test
    fun pickerTitleIsTheKindOnlyWhenTheGarmentHasNoName() {
        assertEquals("Category", tagReviewPickerTitle("category", "", "category for “”"))
        assertEquals("Occasion", tagReviewPickerTitle("occasion", " ", "occasion for “ ”"))
        assertEquals("category for “Blazer”", tagReviewPickerTitle("category", "Blazer", "category for “Blazer”"))
    }

    @Test
    fun alphabeticalOrderIgnoresCaseAndAccentsAndKeepsPinnedRowsFirst() {
        val labels = listOf("Zapatos", "árbol", "Abrigo", "bolsa", "No category")

        val sorted = labels.sortedAlphabetically(label = { label -> label }, pinned = { label -> label == "No category" })

        assertEquals(listOf("No category", "Abrigo", "árbol", "bolsa", "Zapatos"), sorted)
    }

    private fun changeSet(operation: TagOperation, categoryId: String, vararg touched: String) = TagChangeSet(
        id = "change-${touched.joinToString()}",
        operation = operation,
        categoryId = categoryId,
        touchedTagIds = touched.toSet(),
        beforeTags = emptyList(),
        afterTags = emptyList(),
    )

    private fun garment(id: String, vararg tagIds: String) = ClothingItem(
        id = id,
        name = id,
        tags = tagIds.map { tagId -> GarmentTag(id = tagId, categoryId = tagId.substringBefore('-'), name = tagId, sortOrder = 0) },
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
