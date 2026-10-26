package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.applyDelta
import com.gusanitolabs.robia.suggestions.revertDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagReviewSessionTest {
    // V-REV-SESSION-1: toggles, choices and the guards G1 (locked switch) and G2 (chooser)

    @Test
    fun toggleFlipsAnUnlockedChipAndTheButtonFollows() {
        val session = sessionOf(card("g1", add("occasion-formal"), add("occasion-evening")))
        assertEquals(ReviewButton.Apply(2), session.buttonFor("g1"))
        val off = session.toggle("g1", add("occasion-formal").key)
        assertEquals(ReviewButton.ApplyPartial(1, 2), off.buttonFor("g1"))
        assertEquals(ReviewButton.Apply(2), off.toggle("g1", add("occasion-formal").key).buttonFor("g1"))
    }

    @Test
    fun aLockedRemovalNeverToggles() {
        val locked = ReviewChange.Remove("occasion-gone", locked = true)
        val session = sessionOf(card("g1", locked, add("occasion-formal")))
        assertEquals(session, session.toggle("g1", locked.key))
        assertEquals(ReviewButton.Apply(1), session.buttonFor("g1"))
    }

    @Test
    fun aSwapWithALockedHalfTogglesOnlyTheReplacement() {
        val swap = ReviewChange.Swap(fromId = "category-old", toId = "category-dresses", fromLocked = true)
        val session = sessionOf(card("g1", swap))
        assertEquals(TagDelta("g1", emptySet(), setOf("category-dresses")), session.apply("g1", EXISTING).second)
        val off = session.toggle("g1", swap.key)
        assertEquals(ReviewButton.KeepAsIs, off.buttonFor("g1"))
        assertNull(off.apply("g1", EXISTING).second)
    }

    @Test
    fun chooseMakesAPlusUnitAndApplyWritesOneAdd() {
        val session = sessionOf(card("g1", needsChoice = true, offered = setOf("category-skirts", "category-dresses")))
        assertEquals(ReviewButton.KeepAsIs, session.buttonFor("g1"))
        val chosen = session.choose("g1", "category-skirts")
        assertEquals("category-skirts", chosen.stateOf("g1").chosenTagId)
        assertEquals(ReviewButton.Apply(1), chosen.buttonFor("g1"))
        assertEquals(TagDelta("g1", emptySet(), setOf("category-skirts")), chosen.apply("g1", EXISTING).second)
    }

    @Test
    fun chooseOnlyAcceptsAnOfferedTagAndOnlyOnAChoiceCard() {
        val choice = sessionOf(card("g1", needsChoice = true, offered = setOf("category-skirts")))
        assertEquals(choice, choice.choose("g1", "category-knitwear"))
        val plain = sessionOf(card("g2", add("occasion-formal")))
        assertEquals(plain, plain.choose("g2", "occasion-formal"))
    }

    @Test
    fun addingTheSameCardTwiceKeepsOne() {
        val same = card("g1", add("occasion-formal"))
        assertEquals(1, sessionOf(same, same).cards.size)
    }

    @Test
    fun theChooserIsOfferedOnlyWhileTheGarmentHasNoCategory() {
        val taken = sessionOf(
            card("g1", needsChoice = true, live = setOf("category-dresses"), offered = setOf("category-skirts")),
        )
        assertTrue(taken.pending.isEmpty())
        assertNull(taken.choose("g1", "category-skirts").stateOf("g1").chosenTagId)
        val free = sessionOf(card("g2", needsChoice = true, offered = setOf("category-skirts")))
        assertEquals(1, free.pending.size)
        assertEquals("category-skirts", free.choose("g2", "category-skirts").stateOf("g2").chosenTagId)
    }

    @Test
    fun aSwapWithALockedFromIsOnlyTheLockedRemovalWhileTheGarmentHasACategory() {
        val swap = ReviewChange.Swap(fromId = "category-old", toId = "category-dresses", fromLocked = true)
        val session = sessionOf(card("g1", swap, live = setOf("category-skirts")))
        assertEquals(listOf(ReviewChange.Remove("category-old", locked = true)), session.stateOf("g1").changes.changes)
        assertEquals(ReviewButton.GotIt, session.buttonFor("g1"))
        assertTrue(session.pending.isEmpty())
    }

    @Test
    fun aSwapWithALockedFromIsASwitchWhileTheGarmentHasNoCategory() {
        val swap = ReviewChange.Swap(fromId = "category-old", toId = "category-dresses", fromLocked = true)
        val session = sessionOf(card("g1", swap))
        assertEquals(ReviewButton.Apply(1), session.buttonFor("g1"))
        assertEquals(1, session.pending.size)
    }

    // V-REV-SESSION-2: decisions

    @Test
    fun applyWithUnitsOnIsAppliedInFlightAndReturnsTheDelta() {
        val session = sessionOf(card("g1", add("occasion-formal"), remove("occasion-everyday")))
        val (inFlight, delta) = session.apply("g1", EXISTING)
        val expected = TagDelta("g1", remove = setOf("occasion-everyday"), add = setOf("occasion-formal"))
        assertEquals(expected, delta)
        assertEquals(ReviewDecision.Applied(expected, null), inFlight.stateOf("g1").decision)
        assertTrue(inFlight.pending.isEmpty())
    }

    @Test
    fun keepAsIsAndRejectGiveARejectionAndNoDelta() {
        val keep = sessionOf(card("g1", add("occasion-formal"))).toggle("g1", add("occasion-formal").key)
        val (kept, keptDelta) = keep.apply("g1", EXISTING)
        assertNull(keptDelta)
        assertEquals(ReviewDecision.Rejected(), kept.stateOf("g1").decision)
        val rejected = sessionOf(card("g2", add("occasion-formal"))).reject("g2")
        assertEquals(ReviewDecision.Rejected(), rejected.stateOf("g2").decision)
    }

    @Test
    fun rejectIsRefusedWhenTheButtonDoesNotOfferReject() {
        val onlyLocked = sessionOf(card("g1", ReviewChange.Remove("occasion-gone", locked = true)))
        assertEquals(onlyLocked, onlyLocked.reject("g1"))
        val needsChoice = sessionOf(card("g2", needsChoice = true, offered = setOf("category-skirts")))
        assertEquals(needsChoice, needsChoice.reject("g2"))
    }

    @Test
    fun gotItGivesAnAppliedEmptyDeltaAndNoWrite() {
        val session = sessionOf(card("g1", ReviewChange.Remove("occasion-gone", locked = true)))
        assertEquals(ReviewButton.GotIt, session.buttonFor("g1"))
        val (after, delta) = session.apply("g1", EXISTING)
        assertNull(delta)
        val empty = TagDelta("g1", emptySet(), emptySet())
        assertEquals(ReviewDecision.Applied(empty, AppliedDelta("g1", emptySet(), emptySet())), after.stateOf("g1").decision)
    }

    @Test
    fun aDecidedCardIsNotPendingAndCannotBeDecidedAgain() {
        val (applied, _) = sessionOf(card("g1", add("occasion-formal"))).apply("g1", EXISTING)
        assertTrue(applied.pending.isEmpty())
        assertEquals(0, applied.leftCount)
        val (again, delta) = applied.apply("g1", EXISTING)
        assertNull(delta)
        assertEquals(applied, again)
        assertEquals(applied, applied.reject("g1"))
        assertEquals(applied, applied.toggle("g1", add("occasion-formal").key))
        assertEquals(applied, applied.choose("g1", null))
    }

    @Test
    fun appliedResultFillsTheInFlightResultOnceAndIsIgnoredAfter() {
        val (inFlight, delta) = sessionOf(card("g1", add("occasion-formal"))).apply("g1", EXISTING)
        val applied = AppliedDelta("g1", emptySet(), setOf("occasion-formal"))
        val done = inFlight.appliedResult("g1", ApplyResult(garment("g1"), applied))
        assertEquals(ReviewDecision.Applied(checkNotNull(delta), applied), done.stateOf("g1").decision)
        val again = done.appliedResult("g1", ApplyResult(garment("g1"), AppliedDelta("g1", emptySet(), emptySet())))
        assertEquals(done, again)
    }

    @Test
    fun appliedResultWithoutAnItemIsNothingAppliedAndCountedNowhere() {
        val (inFlight, _) = sessionOf(card("g1", add("occasion-formal"))).apply("g1", EXISTING)
        val none = inFlight.appliedResult("g1", ApplyResult(null, AppliedDelta("g1", emptySet(), emptySet())))
        assertEquals(ReviewDecision.Rejected(nothingApplied = true), none.stateOf("g1").decision)
        assertEquals(0, none.appliedGarmentCount)
        assertEquals(ReviewTallies(0, 0, 0, 0), none.tallies())
    }

    @Test
    fun applyFailedReturnsTheCardToPendingWithItsChipsKept() {
        val session = sessionOf(card("g1", add("occasion-formal"), add("occasion-evening")))
            .toggle("g1", add("occasion-evening").key)
        val (inFlight, _) = session.apply("g1", EXISTING)
        val failed = inFlight.applyFailed("g1")
        assertEquals(session.stateOf("g1"), failed.stateOf("g1"))
        assertEquals(1, failed.pending.size)
        val result = AppliedDelta("g1", emptySet(), setOf("occasion-formal"))
        val done = inFlight.appliedResult("g1", ApplyResult(garment("g1"), result))
        assertEquals(done, done.applyFailed("g1"))
    }

    // V-REV-SESSION-3: revert, order and counts

    @Test
    fun revertOfAnAppliedCardReturnsTheDeltaAndTheCardToItsScanPlace() {
        val session = sessionOf(card("g1", add("occasion-formal")), card("g2", add("occasion-evening")))
        val (inFlight, _) = session.apply("g1", EXISTING)
        val done = inFlight.appliedResult("g1", ApplyResult(garment("g1"), AppliedDelta("g1", emptySet(), setOf("occasion-formal"))))
        val (reverted, toReverse) = done.revert("g1")
        assertEquals(AppliedDelta("g1", emptySet(), setOf("occasion-formal")), toReverse)
        assertEquals(ReviewDecision.Pending, reverted.stateOf("g1").decision)
        assertEquals(listOf("g1", "g2"), reverted.pending.map { it.garmentId })
    }

    @Test
    fun revertOfARejectedCardIsPendingWithNoDelta() {
        val rejected = sessionOf(card("g1", add("occasion-formal"))).reject("g1")
        val (reverted, toReverse) = rejected.revert("g1")
        assertNull(toReverse)
        assertEquals(ReviewDecision.Pending, reverted.stateOf("g1").decision)
        assertEquals(1, reverted.leftCount)
    }

    @Test
    fun revertIsRefusedWhileTheWriteIsInFlightAndForAPendingCard() {
        val pendingOnly = sessionOf(card("g1", add("occasion-formal")))
        val (samePending, pendingDelta) = pendingOnly.revert("g1")
        assertNull(pendingDelta)
        assertEquals(pendingOnly, samePending)
        val (inFlight, _) = pendingOnly.apply("g1", EXISTING)
        val (same, delta) = inFlight.revert("g1")
        assertNull(delta)
        assertEquals(inFlight, same)
    }

    @Test
    fun revertOfAnEmptyDeltaGivesNoDelta() {
        val (gotIt, _) = sessionOf(card("g1", ReviewChange.Remove("occasion-gone", locked = true))).apply("g1", EXISTING)
        val (reverted, toReverse) = gotIt.revert("g1")
        assertNull(toReverse)
        assertEquals(ReviewDecision.Pending, reverted.stateOf("g1").decision)
    }

    @Test
    fun applyRevertThenApplyAgainWritesTheSameDeltaOnce() {
        val dresses = tag("category-dresses", "category")
        val skirts = tag("category-skirts", "category")
        val existingTags = listOf(dresses, skirts)
        val live = garment("g1", skirts)
        val swap = ReviewChange.Swap(fromId = "category-skirts", toId = "category-dresses", fromLocked = false)
        val session = sessionOf(card("g1", swap, live = setOf("category-skirts")))

        val (inFlight, first) = session.apply("g1", EXISTING)
        val firstDelta = checkNotNull(first)
        val firstWrite = applyDelta(live, firstDelta, existingTags, "category", now = 2L)
        val done = inFlight.appliedResult("g1", firstWrite)
        val (reverted, toReverse) = done.revert("g1")
        val undo = revertDelta(firstWrite.item, checkNotNull(toReverse), existingTags, "category", now = 3L)
        assertEquals(setOf("category-skirts"), undo.item!!.tags.map { it.id }.toSet())

        val (again, second) = reverted.apply("g1", EXISTING)
        assertEquals(firstDelta, checkNotNull(second))
        assertEquals(ReviewDecision.Applied(firstDelta, null), again.stateOf("g1").decision)
    }

    @Test
    fun applyFailedThenApplyAgainGivesTheSameDelta() {
        val session = sessionOf(card("g1", add("occasion-formal"), remove("occasion-everyday")))
        val (first, firstDelta) = session.apply("g1", EXISTING)
        val (second, secondDelta) = first.applyFailed("g1").apply("g1", EXISTING)
        assertEquals(firstDelta, secondDelta)
        assertEquals(session, first.applyFailed("g1"))
        assertEquals(second.stateOf("g1").decision, ReviewDecision.Applied(checkNotNull(firstDelta), null))
    }

    @Test
    fun reviewedIsMostRecentFirstAndRevertReturnsACardToItsScanPosition() {
        val session = sessionOf(card("a", add("occasion-formal")), card("b", add("occasion-evening")), card("c", add("occasion-casual")))
        val decided = session.reject("a").reject("b")
        assertEquals(listOf("b", "a"), decided.reviewed.map { it.garmentId })
        val reverted = decided.revert("b").first
        assertEquals(listOf("a"), reverted.reviewed.map { it.garmentId })
        assertEquals(listOf("b", "c"), reverted.pending.map { it.garmentId })
    }

    @Test
    fun leftAndAppliedCountsFollowEveryStepAndHiddenCardsCountNothing() {
        val session = sessionOf(
            card("a", add("occasion-formal")),
            card("b", add("occasion-evening")),
            card("c", ReviewChange.Remove("occasion-gone", locked = true)),
        )
        assertEquals(2, session.leftCount)
        assertEquals(0, session.appliedGarmentCount)

        val (inFlight, _) = session.apply("a", EXISTING)
        assertEquals(1, inFlight.leftCount)
        assertEquals(1, inFlight.appliedGarmentCount)

        val done = inFlight.appliedResult("a", ApplyResult(garment("a"), AppliedDelta("a", emptySet(), setOf("occasion-formal"))))
        assertEquals(1, done.appliedGarmentCount)

        val rejected = done.reject("b")
        assertEquals(0, rejected.leftCount)
        assertEquals(1, rejected.appliedGarmentCount)

        val reverted = rejected.revert("a").first
        assertEquals(1, reverted.leftCount)
        assertEquals(0, reverted.appliedGarmentCount)

        val hiddenDecided = session.apply("c", EXISTING).first
        assertEquals(2, hiddenDecided.leftCount)
        assertEquals(0, hiddenDecided.appliedGarmentCount)
    }

    @Test
    fun toggleAfterRevertKeepsTheUserChoices() {
        val session = sessionOf(card("g1", add("occasion-formal"), add("occasion-evening")))
            .toggle("g1", add("occasion-evening").key)
        val (inFlight, _) = session.apply("g1", EXISTING)
        val done = inFlight.appliedResult("g1", ApplyResult(garment("g1"), AppliedDelta("g1", emptySet(), setOf("occasion-formal"))))
        val reverted = done.revert("g1").first
        assertEquals(setOf(add("occasion-evening").key), reverted.stateOf("g1").skipped)
        assertEquals(ReviewButton.ApplyPartial(1, 2), reverted.buttonFor("g1"))
    }

    @Test
    fun rejectRevertApplyGivesAnAppliedCardWithTheSameDelta() {
        val session = sessionOf(card("g1", add("occasion-formal")))
        val (applied, delta) = session.reject("g1").revert("g1").first.apply("g1", EXISTING)
        assertEquals(TagDelta("g1", emptySet(), setOf("occasion-formal")), delta)
        assertEquals(ReviewDecision.Applied(checkNotNull(delta), null), applied.stateOf("g1").decision)
    }

    // V-REV-SESSION-4: bulk plan, completion and tallies

    @Test
    fun bulkPlanCoversApplyAndKeepAsIsAndLeavesOutAnUnchosenChoiceAndHiddenCards() {
        val session = sessionOf(
            card("a", add("occasion-formal"), remove("occasion-everyday")),
            card("b", add("occasion-evening")),
            card("c", needsChoice = true, offered = setOf("category-skirts")),
            card("d", ReviewChange.Remove("occasion-gone", locked = true)),
        ).toggle("b", add("occasion-evening").key)
        val plan = session.bulkPlan(EXISTING)
        assertEquals(listOf("a", "b"), plan.garmentIds)
        assertEquals(listOf(TagDelta("a", setOf("occasion-everyday"), setOf("occasion-formal"))), plan.deltas)
        assertEquals(1, plan.garments)
        assertEquals(1, plan.tagsAdded)
        assertEquals(1, plan.tagsRemoved)
    }

    @Test
    fun bulkPlanCountsTagsRemovedWithoutLockedRemovalsAndCountsSwapHalves() {
        val swap = ReviewChange.Swap(fromId = "category-skirts", toId = "category-dresses", fromLocked = false)
        val session = sessionOf(
            card("a", swap, live = setOf("category-skirts")),
            card("b", ReviewChange.Remove("category-gone", locked = true), add("occasion-formal")),
        )
        val plan = session.bulkPlan(EXISTING)
        assertEquals(1, plan.tagsRemoved)
        assertEquals(2, plan.tagsAdded)
        assertEquals(2, plan.garments)
    }

    @Test
    fun bulkPlanIncludesAChosenTagThatStillExistsAndLeavesOutADeletedOne() {
        val session = sessionOf(card("c", needsChoice = true, offered = setOf("category-skirts")))
            .choose("c", "category-skirts")
        assertEquals(listOf("c"), session.bulkPlan(EXISTING).garmentIds)
        assertTrue(session.bulkPlan(setOf("occasion-formal")).garmentIds.isEmpty())
    }

    @Test
    fun applyBulkDecidesExactlyThePlannedCardsAndLeavesTheOthersPending() {
        val session = sessionOf(
            card("a", add("occasion-formal")),
            card("b", add("occasion-evening")),
            card("c", needsChoice = true, offered = setOf("category-skirts")),
        ).toggle("b", add("occasion-evening").key)
        val (after, deltas) = session.applyBulk(EXISTING)
        val formal = TagDelta("a", emptySet(), setOf("occasion-formal"))
        assertEquals(listOf(formal), deltas)
        assertEquals(ReviewDecision.Applied(formal, null), after.stateOf("a").decision)
        assertEquals(ReviewDecision.Rejected(), after.stateOf("b").decision)
        assertEquals(ReviewDecision.Pending, after.stateOf("c").decision)
        assertEquals(listOf("c"), after.pending.map { it.garmentId })
    }

    @Test
    fun aSecondBulkPlanIsEmptyAfterTheFirst() {
        val session = sessionOf(card("a", add("occasion-formal")), card("b", add("occasion-evening")))
        val (after, _) = session.applyBulk(EXISTING)
        val second = after.bulkPlan(EXISTING)
        assertTrue(second.garmentIds.isEmpty())
        assertTrue(second.deltas.isEmpty())
        assertEquals(0, second.garments)
        val (again, deltas) = after.applyBulk(EXISTING)
        assertTrue(deltas.isEmpty())
        assertEquals(after, again)
    }

    @Test
    fun aChosenTagDeletedBeforeApplyIsClearedAndTheCardStaysPending() {
        val chosen = sessionOf(card("c", needsChoice = true, offered = setOf("category-skirts")))
            .choose("c", "category-skirts")
        val deleted = setOf("occasion-formal")
        val (after, delta) = chosen.apply("c", deleted)
        assertNull(delta)
        assertNull(after.stateOf("c").chosenTagId)
        assertEquals(ReviewDecision.Pending, after.stateOf("c").decision)
        assertEquals(ReviewButton.KeepAsIs, after.buttonFor("c"))
    }

    @Test
    fun isCompleteNeedsTheScanEndedCardsAndNoPendingCard() {
        assertFalse(TagReviewSession().isComplete(scanning = false))
        val one = sessionOf(card("a", add("occasion-formal")))
        assertFalse(one.isComplete(scanning = true))
        assertFalse(one.isComplete(scanning = false))
        assertTrue(one.apply("a", EXISTING).first.isComplete(scanning = false))
        val hidden = sessionOf(card("d", ReviewChange.Remove("occasion-gone", locked = true)))
        assertTrue(hidden.isComplete(scanning = false))
    }

    @Test
    fun leaveKindIsDiscardOnlyWhenNothingWasApplied() {
        val session = TagReviewSession()
        assertEquals(LeaveKind.Discard, session.leaveKind(0))
        assertEquals(LeaveKind.Leave, session.leaveKind(1))
        assertEquals(LeaveKind.Leave, session.leaveKind(5))
    }

    @Test
    fun talliesCountUpdatedAndKeptGarmentsAndLeaveNothingAppliedOut() {
        val session = sessionOf(
            card("a", add("occasion-formal")),
            card("b", add("occasion-evening")),
            card("c", add("occasion-casual")),
            card("d", ReviewChange.Remove("occasion-gone", locked = true)),
        )
        val (inFlight, _) = session.apply("a", EXISTING)
        val done = inFlight.appliedResult("a", ApplyResult(garment("a"), AppliedDelta("a", emptySet(), setOf("occasion-formal"))))
        val kept = done.reject("b")
        val (cInFlight, _) = kept.apply("c", EXISTING)
        val withNothing = cInFlight.appliedResult("c", ApplyResult(null, AppliedDelta("c", emptySet(), emptySet())))
        assertEquals(ReviewDecision.Rejected(nothingApplied = true), withNothing.stateOf("c").decision)
        // a: written (1 garment, 1 tag added); b: kept; c: nothing applied, counted nowhere; d: hidden, never decided
        assertEquals(ReviewTallies(garmentsUpdated = 1, tagsAdded = 1, tagsRemoved = 0, garmentsKept = 1), withNothing.tallies())
    }

    // Helpers

    private fun add(tagId: String): ReviewChange = ReviewChange.Add(tagId)

    private fun remove(tagId: String): ReviewChange = ReviewChange.Remove(tagId, locked = false)

    private fun card(
        garmentId: String,
        vararg changes: ReviewChange,
        needsChoice: Boolean = false,
        live: Set<String> = emptySet(),
        offered: Set<String> = emptySet(),
    ): ReviewCardState = ReviewCardState(
        garmentId = garmentId,
        changes = ReviewChanges(changes = changes.toList(), needsChoice = needsChoice),
        liveGroupTagIds = live,
        offeredTagIds = offered,
    )

    private fun sessionOf(vararg cards: ReviewCardState): TagReviewSession =
        cards.fold(TagReviewSession()) { session, card -> session.withCard(card) }

    private fun TagReviewSession.stateOf(garmentId: String): ReviewCardState = cards.first { it.garmentId == garmentId }

    private fun tag(id: String, categoryId: String): GarmentTag =
        GarmentTag(id = id, categoryId = categoryId, name = id, sortOrder = 0)

    private fun garment(id: String, vararg tags: GarmentTag): ClothingItem = ClothingItem(
        id = id,
        name = "Shirt",
        tags = tags.toList(),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private companion object {
        val EXISTING = setOf(
            "occasion-formal",
            "occasion-evening",
            "occasion-everyday",
            "occasion-casual",
            "category-dresses",
            "category-skirts",
            "category-knitwear",
        )
    }
}
