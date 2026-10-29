package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.suggestions.labelTextFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagReviewScanTest {
    private val dresses = GarmentTag(id = "category-dresses", categoryId = "category", name = "Dresses", sortOrder = 50)
    private val polos = GarmentTag(id = "category-polos", categoryId = "category", name = "Polos", sortOrder = 0)

    @Test
    fun restoredDefaultWithoutDescriptionIsLabelledByItsBareName() {
        // The built-in description of Dresses ranks next to Jumpsuits; the bare name is what a custom Dresses uses.
        assertEquals(
            EmbeddingRecipe.CLASSIFICATION_PREFIX + "Dresses",
            reviewScanLabelText(dresses, touchedTagIds = setOf("category-dresses")),
        )
    }

    @Test
    fun untouchedDefaultKeepsItsLabelText() {
        assertEquals(labelTextFor(dresses), reviewScanLabelText(dresses, touchedTagIds = emptySet()))
    }

    @Test
    fun addedTagWithDescriptionKeepsItsLabelText() {
        val described = polos.copy(description = "a polo shirt with a collar")

        assertEquals(
            labelTextFor(described),
            reviewScanLabelText(described, touchedTagIds = setOf("category-polos")),
        )
    }

    @Test
    fun scanLogLineNamesTheLabelUsedAndTheProposalCount() {
        val changeSet = TagChangeSet(
            id = "change",
            operation = TagOperation.RestoredDefault,
            categoryId = "category",
            touchedTagIds = setOf("category-dresses"),
            beforeTags = emptyList(),
            afterTags = listOf(dresses),
        )

        assertEquals(
            "RestoredDefault category labels=[${EmbeddingRecipe.CLASSIFICATION_PREFIX}Dresses] proposals=3",
            tagReviewScanLogLine(changeSet, proposalCount = 3),
        )
    }

    @Test
    fun emptyScanNeverAutoCommits() {
        assertFalse(shouldAutoCommit(emptySet(), emptySet(), isScanning = false, committed = false, pickerOpen = false, discardDialogOpen = false))
    }

    @Test
    fun midScanNeverAutoCommits() {
        assertFalse(shouldAutoCommit(setOf("a"), setOf("a"), isScanning = true, committed = false, pickerOpen = false, discardDialogOpen = false))
    }

    @Test
    fun openManualChoicePickerNeverAutoCommits() {
        assertFalse(shouldAutoCommit(setOf("a"), setOf("a"), isScanning = false, committed = false, pickerOpen = true, discardDialogOpen = false))
    }

    @Test
    fun unresolvedCandidateBlocksAutoCommit() {
        assertFalse(shouldAutoCommit(setOf("a", "b"), setOf("a"), isScanning = false, committed = false, pickerOpen = false, discardDialogOpen = false))
    }

    @Test
    fun acceptedAndHiddenCandidatesAutoCommit() {
        assertTrue(shouldAutoCommit(setOf("a", "b"), setOf("a", "b"), isScanning = false, committed = false, pickerOpen = false, discardDialogOpen = false))
    }

    @Test
    fun autoCommitFiresOnlyOnce() {
        assertFalse(shouldAutoCommit(setOf("a"), setOf("a"), isScanning = false, committed = true, pickerOpen = false, discardDialogOpen = false))
    }

    @Test
    fun openDiscardDialogNeverAutoCommits() {
        // The last card accepted while Discard is open must not commit the change the user is discarding.
        assertFalse(shouldAutoCommit(setOf("a"), setOf("a"), isScanning = false, committed = false, pickerOpen = false, discardDialogOpen = true))
    }

    @Test
    fun actionIsDroppedWhileSnapshotIsInFlight() {
        assertFalse(canStartTagReviewAction(offerInFlight = true, promptPending = false))
    }

    @Test
    fun actionIsDroppedWhilePromptIsPending() {
        assertFalse(canStartTagReviewAction(offerInFlight = false, promptPending = true))
    }

    @Test
    fun actionStartsWhenIdle() {
        assertTrue(canStartTagReviewAction(offerInFlight = false, promptPending = false))
    }

    // A deletion needs an active garment that holds the deleted tag; the other operations always pass.

    @Test
    fun deletedCategoryHeldByNoGarmentHasNothingToReview() {
        assertFalse(hasAffectedGarments(deleted(dresses), listOf(garment("a", polos))))
        assertFalse(hasAffectedGarments(deleted(dresses), emptyList()))
    }

    @Test
    fun deletedOccasionHeldOnlyByAnArchivedGarmentHasNothingToReview() {
        assertFalse(hasAffectedGarments(deleted(dresses), listOf(garment("a", dresses, isArchived = true))))
    }

    @Test
    fun deletedTagHeldByAnActiveGarmentIsReviewed() {
        assertTrue(hasAffectedGarments(deleted(dresses), listOf(garment("a", dresses))))
    }

    @Test
    fun deletedTagHeldByAnActiveAndAnArchivedGarmentIsReviewed() {
        assertTrue(
            hasAffectedGarments(
                deleted(dresses),
                listOf(garment("a", dresses, isArchived = true), garment("b", dresses)),
            ),
        )
    }

    @Test
    fun addedRenamedAndRestoredAlwaysPassTheCheck() {
        val changeSet = TagChangeSet(
            id = "change",
            operation = TagOperation.Added,
            categoryId = "category",
            touchedTagIds = setOf("category-dresses"),
            beforeTags = emptyList(),
            afterTags = listOf(dresses),
        )
        for (operation in listOf(TagOperation.Added, TagOperation.Renamed, TagOperation.RestoredDefault)) {
            assertTrue(hasAffectedGarments(changeSet.copy(operation = operation), emptyList()))
            assertTrue(hasAffectedGarments(changeSet.copy(operation = operation), listOf(garment("a", polos))))
        }
    }

    private fun deleted(tag: GarmentTag) = TagChangeSet(
        id = "delete",
        operation = TagOperation.Deleted,
        categoryId = "category",
        touchedTagIds = setOf(tag.id),
        beforeTags = listOf(tag),
        afterTags = emptyList(),
    )

    private fun garment(id: String, vararg tags: GarmentTag, isArchived: Boolean = false) = ClothingItem(
        id = id,
        name = id,
        tags = tags.toList(),
        isArchived = isArchived,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
