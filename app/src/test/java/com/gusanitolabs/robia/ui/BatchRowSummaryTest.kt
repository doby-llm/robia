package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRowSummaryTest {
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
    private val dresses = GarmentTag("category-dresses", "category", "Dresses", 0)
    private val everyday = GarmentTag("occasion-everyday", "occasion", "Everyday", 1)
    private val dressedUp = GarmentTag("occasion-dressed-up", "occasion", "Dressed-up", 2)
    private val summer = GarmentTag("season-summer", "season", "Summer", 3)
    private val tags = listOf(dresses, everyday, dressedUp, summer)

    private fun summaryOf(draft: BatchDraftItem): String =
        batchRowSummary(draft, tags, { tag -> tag.name }, collator)

    private fun draft(status: BatchDraftStatus, vararg tagIds: String, errorMessage: String? = null) = BatchDraftItem(
        orderIndex = 0,
        originalPhotoUri = "content://photo",
        status = status,
        selectedTagIds = tagIds.toList(),
        errorMessage = errorMessage,
    )

    @Test
    fun categoryThenAlphabeticalOccasions() {
        val draft = draft(BatchDraftStatus.Ready, everyday.id, summer.id, dressedUp.id, dresses.id)

        assertEquals("Dresses · Dressed-up, Everyday", summaryOf(draft))
    }

    @Test
    fun emptyWhileQueuedOrProcessing() {
        assertEquals("", summaryOf(draft(BatchDraftStatus.Queued, dresses.id, everyday.id)))
        assertEquals("", summaryOf(draft(BatchDraftStatus.Processing, dresses.id, everyday.id)))
    }

    @Test
    fun emptyWhenNothingWasDetected() {
        assertEquals("", summaryOf(draft(BatchDraftStatus.Ready, summer.id)))
    }

    @Test
    fun failedShowsErrorTextInsteadOfTags() {
        val draft = draft(BatchDraftStatus.Failed, dresses.id, errorMessage = "Photo could not be read")

        assertEquals("Photo could not be read", summaryOf(draft))
    }

    @Test
    fun needsReviewNotAcceptedShowsErrorTextButAcceptedShowsTags() {
        val fallback = "Photo cleanup failed. Original photo kept."

        assertEquals(
            fallback,
            summaryOf(draft(BatchDraftStatus.NeedsReview, dresses.id, errorMessage = fallback)),
        )
        val accepted = draft(BatchDraftStatus.NeedsReview, dresses.id, everyday.id, errorMessage = fallback)
            .copy(explicitlyAccepted = true)
        assertEquals("Dresses · Everyday", summaryOf(accepted))
    }

    @Test
    fun attentionLineListsOnlyNonZeroCounts() {
        assertEquals(
            listOf(R.plurals.batch_attention_review to 2, R.plurals.batch_attention_failed to 1),
            batchAttentionLine(needsReview = 2, failed = 1, interrupted = 0),
        )
        assertEquals(emptyList<Pair<Int, Int>>(), batchAttentionLine(0, 0, 0))
    }
}
