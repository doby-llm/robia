package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

// Replaces the commit-at-end tests (tagReviewCommitCandidates, accept all): only the cards the user decided are written.
class TagReviewAcceptAllTest {
    @Test
    fun onlyDecidedCardsAreWritten() {
        val existing = setOf("casual", "weekend", "evening")
        val start = TagReviewSession()
            .withCard(card("applied", ReviewChange.Add("casual")))
            .withCard(card("rejected", ReviewChange.Add("weekend")))
            .withCard(card("pending", ReviewChange.Add("evening")))

        val (applied, appliedDelta) = start.apply("applied", existing)
        val rejected = applied.reject("rejected")
        val (_, rejectedDelta) = rejected.apply("rejected", existing)

        assertEquals(TagDelta("applied", remove = emptySet(), add = setOf("casual")), appliedDelta)
        assertNull(rejectedDelta)
        assertEquals(listOf("pending"), rejected.pending.map { card -> card.garmentId })
    }

    @Test
    fun aChooseManuallyCardWithoutAChoiceIsNeverWritten() {
        val manual = TagReviewSession().withCard(card("manual", needsChoice = true, offered = setOf("casual")))

        assertFalse(canDecideNow(manual.cards.single(), chosenTagId = null))
        assertEquals(emptyList<TagDelta>(), manual.bulkPlan(existingTagIds = setOf("casual")).deltas)
    }

    private fun card(
        garmentId: String,
        vararg changes: ReviewChange,
        needsChoice: Boolean = false,
        offered: Set<String> = emptySet(),
    ) = ReviewCardState(
        garmentId = garmentId,
        changes = ReviewChanges(changes = changes.toList(), needsChoice = needsChoice),
        liveGroupTagIds = emptySet(),
        offeredTagIds = offered,
    )
}
