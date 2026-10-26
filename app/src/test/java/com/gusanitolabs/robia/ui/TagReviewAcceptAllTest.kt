package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.suggestions.ProposalKind
import com.gusanitolabs.robia.suggestions.ReviewProposal
import org.junit.Assert.assertEquals
import org.junit.Test

class TagReviewAcceptAllTest {
    @Test
    fun acceptAllTakesEveryConcreteProposalAndLeavesManualChoicesOut() {
        val suggested = candidate("gA", ProposalKind.Suggested)
        val manual = candidate("gB", ProposalKind.ChooseManually)

        assertEquals(listOf(suggested), tagReviewCommitCandidates(listOf(suggested, manual), emptySet()))
    }

    @Test
    fun manualChoiceJoinsOnlyWhenTheUserAcceptsItByHand() {
        val manual = candidate("gB", ProposalKind.ChooseManually)

        assertEquals(listOf(manual), tagReviewCommitCandidates(listOf(manual), setOf("gB")))
    }

    private fun candidate(id: String, kind: ProposalKind): TagReviewCandidate {
        val item = ClothingItem(id = id, name = id, tags = emptyList(), createdAtEpochMillis = 0, updatedAtEpochMillis = 0)
        return TagReviewCandidate(item = item, proposal = ReviewProposal(id, emptySet(), emptySet(), kind), live = item)
    }
}
