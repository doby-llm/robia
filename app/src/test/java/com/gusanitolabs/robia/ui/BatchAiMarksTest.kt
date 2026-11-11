package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.CategoryResult
import com.gusanitolabs.robia.suggestions.OccasionResult
import com.gusanitolabs.robia.suggestions.ScoreMode
import com.gusanitolabs.robia.suggestions.SuggestionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch AI marks (BS1): the marks a draft carries are the ones the single-add editor shows. The suggestion stage
 * needs an Android Uri and the real SuggestionService, so these tests drive the pure stage function with a fake
 * cutout and a fake Ready outcome.
 */
class BatchAiMarksTest {
    private val tags = listOf(
        GarmentTag(id = "shirt", categoryId = "category", name = "Shirt", sortOrder = 0),
        GarmentTag(id = "trousers", categoryId = "category", name = "Trousers", sortOrder = 1),
        GarmentTag(id = "casual", categoryId = "occasion", name = "Casual", sortOrder = 2),
        GarmentTag(id = "work", categoryId = "occasion", name = "Work", sortOrder = 3),
    )

    @Test
    fun a_suggested_draft_owns_the_predicted_tags_it_added_but_not_the_ones_the_user_chose() {
        val draft = BatchDraftItem(orderIndex = 0, originalPhotoUri = "content://raw", selectedTagIds = listOf("work"))

        val result = draft.withSuggestion(
            cutout = fakeCutout(primaryColorId = "red"),
            ready = fakeReady(marked = "shirt", occasions = listOf("casual", "work")),
            availableTags = tags,
            status = BatchDraftStatus.Ready,
            message = null,
        )

        assertEquals(listOf("work", "shirt", "casual"), result.selectedTagIds)
        assertEquals(listOf("shirt", "casual"), result.modelOwnedTagIds)
        assertFalse("work" in result.modelOwnedTagIds)
        assertTrue(isAiSuggested(listOf("shirt"), result.modelOwnedTagIds))
        assertFalse(isAiSuggested(listOf("work", "casual"), result.modelOwnedTagIds))
        assertTrue(result.modelPrimaryColor)
        assertFalse(result.modelSecondaryColor)
    }

    @Test
    fun a_category_the_user_chose_is_not_replaced_and_gets_no_mark() {
        val draft = BatchDraftItem(orderIndex = 0, originalPhotoUri = "content://raw", selectedTagIds = listOf("shirt"))

        val result = draft.withSuggestion(
            cutout = fakeCutout(primaryColorId = null),
            ready = fakeReady(marked = "trousers", occasions = emptyList()),
            availableTags = tags,
            status = BatchDraftStatus.Ready,
            message = null,
        )

        assertEquals(listOf("shirt"), result.selectedTagIds)
        assertEquals(emptyList<String>(), result.modelOwnedTagIds)
    }

    @Test
    fun without_a_ready_suggestion_the_draft_owns_nothing_but_marks_the_cutout_colours() {
        val draft = BatchDraftItem(orderIndex = 0, originalPhotoUri = "content://raw", selectedTagIds = listOf("work"))

        val result = draft.withSuggestion(
            cutout = fakeCutout(primaryColorId = "red", secondaryColorId = "navy"),
            ready = null,
            availableTags = tags,
            status = BatchDraftStatus.Ready,
            message = null,
        )

        assertEquals(listOf("work"), result.selectedTagIds)
        assertEquals(emptyList<String>(), result.modelOwnedTagIds)
        assertTrue(result.modelPrimaryColor)
        assertTrue(result.modelSecondaryColor)
    }

    @Test
    fun carry_back_keeps_a_category_mark_only_while_its_selected_tags_are_unchanged() {
        val previous = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://raw",
            selectedTagIds = listOf("shirt", "casual"),
            modelOwnedTagIds = listOf("shirt", "casual"),
        )

        val categoryChanged = carryBatchAiMarks(
            previous = previous,
            updated = previous.copy(selectedTagIds = listOf("trousers", "casual")),
            availableTags = tags,
        )
        val unchanged = carryBatchAiMarks(previous = previous, updated = previous.copy(name = "Renamed"), availableTags = tags)

        assertEquals(listOf("casual"), categoryChanged.modelOwnedTagIds)
        assertEquals(listOf("shirt", "casual"), unchanged.modelOwnedTagIds)
    }

    @Test
    fun carry_back_keeps_a_colour_mark_only_while_the_colour_is_the_same() {
        val previous = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://raw",
            selectedPrimaryColorId = "red",
            selectedSecondaryColorId = "navy",
            modelPrimaryColor = true,
            modelSecondaryColor = true,
        )

        val primaryChanged = carryBatchAiMarks(
            previous = previous,
            updated = previous.copy(selectedPrimaryColorId = "blue"),
            availableTags = tags,
        )
        val secondaryKept = carryBatchAiMarks(previous = previous, updated = previous, availableTags = tags)

        assertFalse(primaryChanged.modelPrimaryColor)
        assertTrue(primaryChanged.modelSecondaryColor)
        assertTrue(secondaryKept.modelPrimaryColor)
        assertTrue(secondaryKept.modelSecondaryColor)
    }

    @Test
    fun retry_clears_the_marks_of_a_failed_draft_and_of_a_fallback_review() {
        val failed = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://raw",
            status = BatchDraftStatus.Failed,
            modelOwnedTagIds = listOf("shirt"),
            modelPrimaryColor = true,
            modelSecondaryColor = true,
        )
        val fallbackReview = failed.copy(status = BatchDraftStatus.NeedsReview, backgroundFallback = true)

        listOf(failed, fallbackReview).forEach { draft ->
            val retried = draft.retryForBatch()

            assertEquals(BatchDraftStatus.Queued, retried.status)
            assertEquals(emptyList<String>(), retried.modelOwnedTagIds)
            assertFalse(retried.modelPrimaryColor)
            assertFalse(retried.modelSecondaryColor)
        }
    }

    private fun fakeCutout(primaryColorId: String?, secondaryColorId: String? = null) = CutoutStageResult.Ready(
        croppedUri = "content://cropped",
        displayAspectRatio = 1f,
        primaryColorId = primaryColorId,
        secondaryColorId = secondaryColorId,
        usedFallback = false,
        needsOriginalFallbackReview = false,
    )

    private fun fakeReady(marked: String?, occasions: List<String>) = SuggestionOutcome.Ready(
        categoryResult = CategoryResult(
            ranked = emptyList(),
            mode = ScoreMode.COMBINED,
            margin = 0.1f,
            marked = marked,
            reason = "fake",
        ),
        occasionResult = OccasionResult(
            ranked = emptyList(),
            mode = ScoreMode.COMBINED,
            g12 = null,
            g23 = null,
            selected = occasions,
            reason = "fake",
        ),
        imageVector = FloatArray(0),
        imageInputHash = "fake-hash",
        durationMs = 1L,
    )
}
