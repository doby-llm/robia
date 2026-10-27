package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REV-DONE: the completion view's rules (when it shows, Edit changes, the tally, the thumbnails), over the real session. */
class TagReviewCompleteTest {
    private val existingTagIds = setOf("casual", "weekend", "evening")

    // V-REV-DONE-1: the view appears only when the review is complete, with no write or revert in flight.

    @Test
    fun theCompletionWaitsForEveryWriteToLand() {
        // Kills: showsCompletion without writesInFlight, which shows the view over a write whose tally would still be zero.
        val inFlight = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds).first
        assertFalse(showsCompletion(inFlight, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))

        val landed = inFlight.appliedResult("a", applyResult("a", added = setOf("casual")))
        assertTrue(showsCompletion(landed, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
    }

    @Test
    fun theCompletionIsNotShownWhileScanningOrWhileACardIsPending() {
        // Kills: showsCompletion without the scan guard, or without the Pending guard (a card in the list keeps the list).
        val decided = session(card("a", ReviewChange.Add("casual")), card("b", ReviewChange.Add("weekend")))
            .apply("a", existingTagIds).first
            .appliedResult("a", applyResult("a", added = setOf("casual")))
        assertFalse(showsCompletion(decided, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
        val done = decided.apply("b", existingTagIds).first.appliedResult("b", applyResult("b", added = setOf("weekend")))
        assertFalse(showsCompletion(done, isScanning = true, revertsInFlight = false, edit = ReviewEditState.Off))
        assertTrue(showsCompletion(done, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
    }

    @Test
    fun aRevertInFlightHoldsTheCompletion() {
        // Kills: showsCompletion ignoring revertsInFlight, so a failed revert would find no completion to show its message on.
        val landed = session(card("a", ReviewChange.Add("casual"))).landed("a", added = setOf("casual"))
        assertFalse(showsCompletion(landed, isScanning = false, revertsInFlight = true, edit = ReviewEditState.Off))
        assertTrue(showsCompletion(landed, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
    }

    @Test
    fun aReviewThatFoundNothingShowsNoScreen() {
        // Kills: showsCompletion without the reviewed guard. The only card is a locked removal, which is never offered.
        assertFalse(showsCompletion(TagReviewSession(), isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
        val hiddenOnly = session(card("h", ReviewChange.Remove("evening", locked = true)))
        assertTrue(hiddenOnly.isComplete(scanning = false))
        assertFalse(showsCompletion(hiddenOnly, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
    }

    @Test
    fun aReviewThatOnlyRejectedShowsTheCompletion() {
        // Kills: showsCompletion requiring an applied card (a review that changed nothing would show no completion at all).
        val kept = session(card("a", ReviewChange.Add("casual"))).reject("a")
        assertTrue(showsCompletion(kept, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Off))
    }

    // V-REV-DONE-1: Edit changes keeps the list until a card went back to Pending and was decided again.

    @Test
    fun editChangesKeepsTheListUntilACardIsDecidedAgain() {
        // Kills: showsCompletion ignoring the edit state (the view comes straight back while the user looks at Reviewed).
        val complete = session(card("a", ReviewChange.Add("casual"))).reject("a")
        assertFalse(showsCompletion(complete, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Looking))
        assertFalse(showsCompletion(complete, isScanning = false, revertsInFlight = false, edit = ReviewEditState.Returned))
    }

    @Test
    fun theEditFlagEndsOnlyWhenAReturnedCardIsDecidedAgain() {
        // Kills: a flag that clears at once on Edit (Looking, 0 → Off), one that never clears (Returned, 0 → Returned), and one
        // that drops the return (Looking, 1 → Looking).
        assertEquals(ReviewEditState.Looking, reviewEditAfter(ReviewEditState.Looking, leftCount = 0))
        assertEquals(ReviewEditState.Returned, reviewEditAfter(ReviewEditState.Looking, leftCount = 1))
        assertEquals(ReviewEditState.Returned, reviewEditAfter(ReviewEditState.Returned, leftCount = 2))
        assertEquals(ReviewEditState.Off, reviewEditAfter(ReviewEditState.Returned, leftCount = 0))
        assertEquals(ReviewEditState.Off, reviewEditAfter(ReviewEditState.Off, leftCount = 1))
    }

    @Test
    fun editChangesThenUndoThenDecideAgainFollowsTheWholeFlow() {
        // Kills: a flow where Undo of the last decision leaves the completion up over a card that is Pending again.
        var edit = ReviewEditState.Off
        var review = session(card("a", ReviewChange.Add("casual"))).landed("a", added = setOf("casual"))
        assertTrue(showsCompletion(review, isScanning = false, revertsInFlight = false, edit = edit))

        edit = ReviewEditState.Looking // Edit changes
        assertFalse(showsCompletion(review, isScanning = false, revertsInFlight = false, edit = edit))

        review = review.revert("a").first // Review again or Undo: the card is Pending
        edit = reviewEditAfter(edit, review.leftCount)
        assertEquals(ReviewEditState.Returned, edit)
        assertFalse(showsCompletion(review, isScanning = false, revertsInFlight = false, edit = edit))

        review = review.reject("a") // decided again
        edit = reviewEditAfter(edit, review.leftCount)
        assertEquals(ReviewEditState.Off, edit)
        assertTrue(showsCompletion(review, isScanning = false, revertsInFlight = false, edit = edit))
    }

    // V-REV-DONE-1: the tally counts landed writes only.

    @Test
    fun theTallyCountsLandedWritesOnly() {
        // Kills: a tally from the intended delta (a lands one add of two), from an in-flight write (b), or from a null item (n).
        var review = session(
            card("a", ReviewChange.Add("casual"), ReviewChange.Add("weekend")),
            card("b", ReviewChange.Add("evening")),
            card("n", ReviewChange.Add("casual")),
        )
        review = review.apply("a", existingTagIds).first.appliedResult("a", applyResult("a", added = setOf("casual")))
        review = review.apply("b", existingTagIds).first
        review = review.apply("n", existingTagIds).first.appliedResult("n", ApplyResult(item = null, applied = AppliedDelta("n", emptySet(), emptySet())))

        assertEquals(ReviewTallies(garmentsUpdated = 1, tagsAdded = 1, tagsRemoved = 0, garmentsKept = 0), review.tallies())
    }

    @Test
    fun aRevertedGarmentIsNoLongerCounted() {
        // Kills: a tally that keeps the applied result after its revert (the card is Pending again, so it is not counted).
        val reverted = session(card("a", ReviewChange.Add("casual")))
            .landed("a", added = setOf("casual"))
            .revert("a").first
        assertEquals(ReviewTallies(garmentsUpdated = 0, tagsAdded = 0, tagsRemoved = 0, garmentsKept = 0), reverted.tallies())
    }

    @Test
    fun theKeptCountIsTheRejectedGarments() {
        // Kills: counting Got it or in-flight cards as kept; a rejection is the only kept garment.
        val review = session(card("a", ReviewChange.Add("casual")), card("r", ReviewChange.Add("weekend")))
            .reject("r")
            .landed("a", added = setOf("casual"))
        assertEquals(1, review.tallies().garmentsKept)
    }

    // V-REV-DONE-1: each tally entry shows only above 0, and the title says none when no garment changed.

    @Test
    fun aTallyEntryShowsOnlyWhenItsNumberIsAboveZero() {
        // Kills: an entry shown at 0 (a `>= 0` filter), and a kept entry that takes the place of a removed one.
        assertEquals(
            listOf(CompletionEntry.Added to 3),
            completionEntries(ReviewTallies(garmentsUpdated = 2, tagsAdded = 3, tagsRemoved = 0, garmentsKept = 0)),
        )
        assertEquals(
            listOf(CompletionEntry.Removed to 1, CompletionEntry.Kept to 2),
            completionEntries(ReviewTallies(garmentsUpdated = 1, tagsAdded = 0, tagsRemoved = 1, garmentsKept = 2)),
        )
        assertEquals(emptyList<Pair<CompletionEntry, Int>>(), completionEntries(ReviewTallies(0, 0, 0, 0)))
    }

    @Test
    fun theTitleCountsTheUpdatedGarmentsNotTheKeptOnes() {
        // Kills: a title that counts kept garments (none changed, two kept, must still say "No garments changed").
        assertFalse(completionHasChanges(ReviewTallies(garmentsUpdated = 0, tagsAdded = 0, tagsRemoved = 0, garmentsKept = 2)))
        assertTrue(completionHasChanges(ReviewTallies(garmentsUpdated = 1, tagsAdded = 1, tagsRemoved = 0, garmentsKept = 0)))
    }

    // V-REV-DONE-1: at most five thumbnails, of the garments the landed writes updated.

    @Test
    fun thumbnailsAreTheUpdatedGarmentsCappedAtFiveAndEachOnce() {
        // Kills: no cap or a cap of six (a1..a6 shown), a duplicate kept (a1 twice), a rejected garment shown (r), and a write
        // that changed nothing shown (e).
        val ids = listOf("a1", "a2", "a3", "a4", "a5", "a6")
        val cards = ids.map { id -> card(id, ReviewChange.Add("casual")) } +
            card("r", ReviewChange.Add("casual")) +
            card("e", ReviewChange.Add("casual"))
        var review = session(*cards.toTypedArray())
        for (id in ids) review = review.landed(id, added = setOf("casual"))
        review = review.reject("r")
        review = review.apply("e", existingTagIds).first.appliedResult("e", applyResult("e", added = emptySet()))

        val written = listOf("a1", "a1", "a2", "a3", "a4", "a5", "a6", "r", "e").map { id -> garment(id) }
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), completionThumbnails(review, written).map(ClothingItem::id))
    }

    @Test
    fun noThumbnailsWhenNothingWasUpdated() {
        // Kills: thumbnails taken from the written list without checking the decisions.
        val review = session(card("r", ReviewChange.Add("casual"))).reject("r")
        assertTrue(completionThumbnails(review, listOf(garment("r"))).isEmpty())
    }

    // Fixtures

    private fun session(vararg cards: ReviewCardState): TagReviewSession =
        cards.fold(TagReviewSession()) { session, card -> session.withCard(card) }

    private fun TagReviewSession.landed(
        garmentId: String,
        added: Set<String> = emptySet(),
        removed: Set<String> = emptySet(),
    ): TagReviewSession = apply(garmentId, existingTagIds).first
        .appliedResult(garmentId, ApplyResult(item = garment(garmentId), applied = AppliedDelta(garmentId, removed, added)))

    private fun applyResult(garmentId: String, added: Set<String> = emptySet(), removed: Set<String> = emptySet()) =
        ApplyResult(item = garment(garmentId), applied = AppliedDelta(garmentId, removed, added))

    private fun card(garmentId: String, vararg changes: ReviewChange) = ReviewCardState(
        garmentId = garmentId,
        changes = ReviewChanges(changes = changes.toList(), needsChoice = false),
        liveGroupTagIds = emptySet(),
        offeredTagIds = emptySet(),
    )

    private fun garment(id: String) = ClothingItem(
        id = id,
        name = id,
        tags = emptyList(),
        isArchived = false,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
