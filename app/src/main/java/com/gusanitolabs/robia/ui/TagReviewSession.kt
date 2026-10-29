package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.deltaFor
import com.gusanitolabs.robia.suggestions.primaryButtonFor
import com.gusanitolabs.robia.suggestions.replaceTag
import com.gusanitolabs.robia.suggestions.tagIdsExcept

/**
 * What the user decided for one card. [Applied.applied] is null while the write is in flight. [Rejected.nothingApplied] is a
 * write that changed nothing (the garment changed since the review opened): it is counted nowhere.
 */
internal sealed interface ReviewDecision {
    data object Pending : ReviewDecision

    data class Applied(val intended: TagDelta, val applied: AppliedDelta?) : ReviewDecision

    data class Rejected(val nothingApplied: Boolean = false) : ReviewDecision
}

/**
 * One review card. [liveGroupTagIds] is the live garment's tags of the group at build time (never the proposal's before).
 * [offeredTagIds] is the group's tags that exist now and are not on the garment: the only tags a chooser offers.
 */
internal data class ReviewCardState(
    val garmentId: String,
    val changes: ReviewChanges,
    val liveGroupTagIds: Set<String>,
    val offeredTagIds: Set<String>,
    val skipped: Set<String> = emptySet(),
    val chosenTagId: String? = null,
    val decision: ReviewDecision = ReviewDecision.Pending,
)

/** What a bulk apply would write: [deltas] are the Apply cards in scan order; [garmentIds] also lists the kept cards. */
internal data class BulkPlan(
    val garmentIds: List<String>,
    val deltas: List<TagDelta>,
    val tagsAdded: Int,
    val tagsRemoved: Int,
    val garments: Int,
)

internal data class ReviewTallies(
    val garmentsUpdated: Int,
    val tagsAdded: Int,
    val tagsRemoved: Int,
    val garmentsKept: Int,
)

internal enum class LeaveKind { Discard, Leave }

/**
 * The decision state of one tag review: a card per candidate garment in scan order, and the order of the decisions.
 * Every transition returns a new session; a call that does not apply returns the session unchanged (and a null delta).
 *
 * A card with no unlocked change and no choice (a dropped change, a two-category garment, a locked removal with no winner)
 * is hidden: it is kept in [cards] so that its state is known, but no query counts it. A card whose garment has a category
 * now never offers a chooser, and a switch from a locked category is offered only while the garment has no category.
 */
internal data class TagReviewSession(
    val cards: List<ReviewCardState> = emptyList(),
    val decisionOrder: List<String> = emptyList(),
) {
    /** Adds a card (cards start Pending); a card already there is kept as it is. */
    fun withCard(card: ReviewCardState): TagReviewSession {
        if (cards.any { it.garmentId == card.garmentId }) return this
        return copy(cards = cards + normalized(card))
    }

    /** Flips one unit: an Add, an unlocked Remove, a Swap or the chosen tag. A locked removal never flips. */
    fun toggle(garmentId: String, key: String): TagReviewSession = updateCard(garmentId) { card ->
        if (card.decision !is ReviewDecision.Pending || key !in unitKeys(card)) {
            card
        } else {
            card.copy(skipped = if (key in card.skipped) card.skipped - key else card.skipped + key)
        }
    }

    /** Picks the tag of a `needsChoice` card (null clears it). Only a tag the chooser offers is accepted. */
    fun choose(garmentId: String, tagId: String?): TagReviewSession = updateCard(garmentId) { card ->
        val offered = tagId == null || tagId in card.offeredTagIds
        if (card.decision !is ReviewDecision.Pending || !card.changes.needsChoice || !offered) {
            card
        } else {
            card.copy(chosenTagId = tagId)
        }
    }

    /**
     * Replaces the tag of one chip of a Pending card: the long press of R3-DEC-060. Refused, and the card unchanged, unless
     * [newTagId] is offered by the card and no other chip of the card has it (the chosen tag included). A decided card, a
     * Remove, a locked chip and an unknown key are refused as well. The old key leaves [ReviewCardState.skipped]: the replaced
     * chip is on. The chosen tag of a card that needs a choice goes through [choose], not here.
     */
    fun replaceChip(garmentId: String, key: String, newTagId: String): TagReviewSession = updateCard(garmentId) { card ->
        val usedElsewhere = newTagId in card.changes.tagIdsExcept(key) || newTagId == card.chosenTagId
        val replaced = if (card.decision is ReviewDecision.Pending && newTagId in card.offeredTagIds && !usedElsewhere) {
            card.changes.replaceTag(key, newTagId)
        } else {
            null
        }
        if (replaced == null) {
            card
        } else {
            normalized(card.copy(changes = replaced, skipped = card.skipped - key))
        }
    }

    /**
     * Decides a Pending card by its button: `Apply*` gives the delta to write, `KeepAsIs` is a rejection, `GotIt` is an empty
     * applied decision with no write. A chosen tag that is not in [existingTagIds] is cleared and the card stays Pending.
     */
    fun apply(garmentId: String, existingTagIds: Set<String>): Pair<TagReviewSession, TagDelta?> {
        val card = cards.firstOrNull { it.garmentId == garmentId } ?: return this to null
        if (card.decision !is ReviewDecision.Pending) return this to null
        val chosen = card.chosenTagId
        if (chosen != null && chosen !in existingTagIds) {
            return updateCard(garmentId) { it.copy(chosenTagId = null) } to null
        }
        val delta = deltaFor(garmentId, card.changes, card.skipped, chosen)
        return when (primaryButtonFor(card.changes, card.skipped, chosen)) {
            ReviewButton.KeepAsIs -> updateCard(garmentId) { it.copy(decision = ReviewDecision.Rejected()) } to null
            ReviewButton.GotIt -> updateCard(garmentId) {
                it.copy(decision = ReviewDecision.Applied(delta, AppliedDelta(garmentId, emptySet(), emptySet())))
            } to null
            is ReviewButton.Apply, is ReviewButton.ApplyPartial -> updateCard(garmentId) {
                it.copy(decision = ReviewDecision.Applied(delta, null))
            } to delta
        }
    }

    /** Rejects a Pending card, only when its button offers Reject. */
    fun reject(garmentId: String): TagReviewSession = updateCard(garmentId) { card ->
        val offersReject = primaryButtonFor(card.changes, card.skipped, card.chosenTagId).showsReject
        if (card.decision !is ReviewDecision.Pending || !offersReject) {
            card
        } else {
            card.copy(decision = ReviewDecision.Rejected())
        }
    }

    /** The write of an applied card came back: an item means the applied delta, no item means nothing was applied. */
    fun appliedResult(garmentId: String, result: ApplyResult): TagReviewSession = updateCard(garmentId) { card ->
        val decision = card.decision
        if (decision !is ReviewDecision.Applied || decision.applied != null) {
            card
        } else if (result.item == null) {
            card.copy(decision = ReviewDecision.Rejected(nothingApplied = true))
        } else {
            card.copy(decision = ReviewDecision.Applied(decision.intended, result.applied))
        }
    }

    /** The write of an applied card failed: the card is Pending again with its chips kept. */
    fun applyFailed(garmentId: String): TagReviewSession = updateCard(garmentId) { card ->
        val decision = card.decision
        if (decision is ReviewDecision.Applied && decision.applied == null) {
            card.copy(decision = ReviewDecision.Pending)
        } else {
            card
        }
    }

    /**
     * Undo and "Review again": a card that has a written result or was rejected is Pending again with its chips kept, and the
     * delta to reverse is returned (null for a rejection and for an empty delta). Refused while a write is in flight.
     */
    fun revert(garmentId: String): Pair<TagReviewSession, AppliedDelta?> {
        val card = cards.firstOrNull { it.garmentId == garmentId } ?: return this to null
        return when (val decision = card.decision) {
            ReviewDecision.Pending -> this to null
            is ReviewDecision.Applied -> {
                val applied = decision.applied
                if (applied == null) {
                    this to null
                } else {
                    updateCard(garmentId) { it.copy(decision = ReviewDecision.Pending) } to applied.takeUnless { it.isEmptyDelta() }
                }
            }
            is ReviewDecision.Rejected -> updateCard(garmentId) { it.copy(decision = ReviewDecision.Pending) } to null
        }
    }

    /** The primary button of a card, or null when there is no such card. */
    fun buttonFor(garmentId: String): ReviewButton? =
        cards.firstOrNull { it.garmentId == garmentId }?.let { primaryButtonFor(it.changes, it.skipped, it.chosenTagId) }

    /** Cards still Pending and shown, in scan order. */
    val pending: List<ReviewCardState>
        get() = cards.filter { isShown(it) && it.decision is ReviewDecision.Pending }

    val leftCount: Int get() = pending.size

    /** Shown cards decided, most recent decision first. */
    val reviewed: List<ReviewCardState>
        get() {
            val byId = cards.associateBy(ReviewCardState::garmentId)
            return decisionOrder.reversed()
                .mapNotNull { byId[it] }
                .filter { isShown(it) && it.decision !is ReviewDecision.Pending }
        }

    /** Applied cards whose intended delta is not empty; a write in flight counts. */
    val appliedGarmentCount: Int
        get() = cards.count { card ->
            val decision = card.decision
            decision is ReviewDecision.Applied && !decision.intended.isEmpty
        }

    /**
     * The bulk plan over the shown Pending cards that can be decided now: a `needsChoice` card without a chosen tag that still
     * exists is left out. Every other card is planned: the Apply cards write, the KeepAsIs cards are kept.
     */
    fun bulkPlan(existingTagIds: Set<String>): BulkPlan {
        val planned = plannedCards(existingTagIds)
        val deltas = planned
            .filter { (_, button) -> button is ReviewButton.Apply || button is ReviewButton.ApplyPartial }
            .map { (card, _) -> deltaFor(card.garmentId, card.changes, card.skipped, card.chosenTagId) }
        return BulkPlan(
            garmentIds = planned.map { (card, _) -> card.garmentId },
            deltas = deltas,
            tagsAdded = deltas.sumOf { it.add.size },
            tagsRemoved = deltas.sumOf { it.remove.size },
            garments = deltas.size,
        )
    }

    /** Decides exactly the planned cards (see [bulkPlan]) and returns the deltas to write. */
    fun applyBulk(existingTagIds: Set<String>): Pair<TagReviewSession, List<TagDelta>> {
        var session = this
        val deltas = mutableListOf<TagDelta>()
        for ((card, _) in plannedCards(existingTagIds)) {
            val (next, delta) = session.apply(card.garmentId, existingTagIds)
            session = next
            if (delta != null) deltas.add(delta)
        }
        return session to deltas
    }

    /** Totals of the decisions that were written or kept; a nothing-applied card is counted nowhere. */
    fun tallies(): ReviewTallies {
        val updated = cards.mapNotNull { (it.decision as? ReviewDecision.Applied)?.applied }.filterNot { it.isEmptyDelta() }
        return ReviewTallies(
            garmentsUpdated = updated.size,
            tagsAdded = updated.sumOf { it.added.size },
            tagsRemoved = updated.sumOf { it.removed.size },
            garmentsKept = cards.count { val decision = it.decision; decision is ReviewDecision.Rejected && !decision.nothingApplied },
        )
    }

    /** The review ends once the scan has ended, there is at least one card and none is Pending. */
    fun isComplete(scanning: Boolean): Boolean = !scanning && cards.isNotEmpty() && pending.isEmpty()

    /** Leaving with nothing applied discards the review; with something applied it is left for later. */
    fun leaveKind(appliedCount: Int): LeaveKind = if (appliedCount == 0) LeaveKind.Discard else LeaveKind.Leave

    private fun plannedCards(existingTagIds: Set<String>): List<Pair<ReviewCardState, ReviewButton>> = pending
        .filter { card ->
            val chosen = card.chosenTagId
            !card.changes.needsChoice || (chosen != null && chosen in existingTagIds)
        }
        .map { card -> card to primaryButtonFor(card.changes, card.skipped, card.chosenTagId) }

    /** Applies [transform] to the card and keeps [decisionOrder] in step with the decisions. */
    private fun updateCard(garmentId: String, transform: (ReviewCardState) -> ReviewCardState): TagReviewSession {
        val index = cards.indexOfFirst { it.garmentId == garmentId }
        if (index < 0) return this
        val before = cards[index]
        val after = transform(before)
        val order = when {
            after.decision is ReviewDecision.Pending -> decisionOrder - garmentId
            before.decision is ReviewDecision.Pending -> decisionOrder + garmentId
            else -> decisionOrder
        }
        return copy(cards = cards.toMutableList().apply { set(index, after) }, decisionOrder = order)
    }

    private fun unitKeys(card: ReviewCardState): Set<String> {
        val keys = card.changes.changes.filterNot { it is ReviewChange.Remove && it.locked }.mapTo(mutableSetOf()) { it.key }
        val chosen = card.chosenTagId
        if (card.changes.needsChoice && chosen != null) keys += ReviewChange.Add(chosen).key
        return keys
    }
}

/**
 * Guard G1: a switch from a locked category is offered only while the garment has no category now; otherwise it is only the
 * locked removal. Guard G2: a chooser is offered only while the garment has no category now.
 */
private fun normalized(card: ReviewCardState): ReviewCardState {
    val live = card.liveGroupTagIds
    val changes = card.changes.changes.map { change ->
        if (change is ReviewChange.Swap && change.fromLocked && live.isNotEmpty()) {
            ReviewChange.Remove(change.fromId, locked = true)
        } else {
            change
        }
    }
    val needsChoice = card.changes.needsChoice && live.isEmpty()
    return card.copy(
        changes = ReviewChanges(changes = changes, needsChoice = needsChoice),
        chosenTagId = card.chosenTagId.takeIf { needsChoice },
    )
}

/** A card is shown when it has an unlocked change or needs a choice (see [TagReviewSession]). */
private fun isShown(card: ReviewCardState): Boolean =
    card.changes.needsChoice || card.changes.changes.any { it !is ReviewChange.Remove || !it.locked }

private fun AppliedDelta.isEmptyDelta(): Boolean = removed.isEmpty() && added.isEmpty()
