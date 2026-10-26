package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import java.text.Collator

/**
 * One change a review card shows for a garment, inside one tag group. A locked removal is a tag that was deleted before the
 * review opened: it is shown but can never be left out or written. An unlocked removal or add is a switch. In a category
 * group an unlocked removal is shown only as the `from` half of a [Swap].
 */
sealed interface ReviewChange {
    val key: String

    data class Add(val tagId: String) : ReviewChange {
        override val key: String get() = addKey(tagId)
    }

    data class Remove(val tagId: String, val locked: Boolean) : ReviewChange {
        override val key: String get() = "rem:$tagId"
    }

    /** A category change, one unit: the `from` half is locked when that category was deleted. */
    data class Swap(val fromId: String, val toId: String, val fromLocked: Boolean) : ReviewChange {
        override val key: String get() = "swap:$fromId:$toId"
    }
}

/** The changes of one garment in one group. [needsChoice] is set for a `ChooseManually` proposal: the user picks the tag. */
data class ReviewChanges(val changes: List<ReviewChange>, val needsChoice: Boolean)

/** What the primary button of a card says and does. [showsReject] is true only when Apply is offered. */
sealed interface ReviewButton {
    val showsReject: Boolean

    data class Apply(val count: Int) : ReviewButton {
        override val showsReject: Boolean get() = true
    }

    data class ApplyPartial(val on: Int, val of: Int) : ReviewButton {
        override val showsReject: Boolean get() = true
    }

    data object KeepAsIs : ReviewButton {
        override val showsReject: Boolean get() = false
    }

    data object GotIt : ReviewButton {
        override val showsReject: Boolean get() = false
    }
}

/** The tag ids one garment gets removed and added in one group, as the user decided. */
data class TagDelta(val garmentId: String, val remove: Set<String>, val add: Set<String>) {
    val isEmpty: Boolean get() = remove.isEmpty() && add.isEmpty()
}

/** The tag ids that [applyDelta] or [revertDelta] actually changed. */
data class AppliedDelta(val garmentId: String, val removed: Set<String>, val added: Set<String>)

/** The garment with its new tags, or null when nothing is written; [applied] is what really changed (empty when null). */
data class ApplyResult(val item: ClothingItem?, val applied: AppliedDelta)

/** Category group id, as in the review planner. Only this group has the one-category rule. */
private const val CATEGORY_GROUP = "category"

/**
 * Derives the changes of one proposal inside one tag group. removed = before − proposed, added = proposed − before, both
 * limited to [groupTagIds]. Adds of tags that do not exist now are dropped before the category rules are decided. Order:
 * swaps, removals, additions; each by [label] with [collator], ties by id.
 *
 * Category group: one removed and one added tag is one [ReviewChange.Swap]. In any other shape an unlocked removal is not
 * shown at all, because it could only apply together with its add, and a removal alone would strip the garment's category.
 * Its adds go with it. Locked removals stay as locked chips. A lone add stays a switch only when it can apply, that is when
 * the garment has no category in the group now ([liveGroupTagIds] is empty).
 */
fun reviewChangesFor(
    proposal: ReviewProposal,
    groupTagIds: Set<String>,
    isCategoryGroup: Boolean,
    existingTagIds: Set<String>,
    liveGroupTagIds: Set<String>,
    label: (String) -> String,
    collator: Collator,
): ReviewChanges {
    val removedIds = (proposal.before - proposal.proposed).filterTo(mutableSetOf()) { it in groupTagIds }
    val addedIds = (proposal.proposed - proposal.before).filterTo(mutableSetOf()) { it in groupTagIds && it in existingTagIds }
    val ordered = { ids: Collection<String> -> ids.sortedWith(labelOrder(label, collator)) }
    val changes: List<ReviewChange> = if (isCategoryGroup && removedIds.size == 1 && addedIds.size == 1) {
        val from = removedIds.single()
        listOf(ReviewChange.Swap(from, addedIds.single(), fromLocked = from !in existingTagIds))
    } else if (isCategoryGroup) {
        val lockedIds = removedIds.filterTo(mutableSetOf()) { it !in existingTagIds }
        val hasUnlockedRemoval = removedIds.any { it in existingTagIds }
        val lone = !hasUnlockedRemoval && addedIds.size == 1 && liveGroupTagIds.isEmpty()
        ordered(lockedIds).map { ReviewChange.Remove(it, locked = true) } +
            if (lone) ordered(addedIds).map { ReviewChange.Add(it) } else emptyList<ReviewChange.Add>()
    } else {
        ordered(removedIds).map { ReviewChange.Remove(it, locked = it !in existingTagIds) } +
            ordered(addedIds).map { ReviewChange.Add(it) }
    }
    return ReviewChanges(changes = changes, needsChoice = proposal.kind == ProposalKind.ChooseManually)
}

/**
 * The primary button. Units are each Add, each unlocked Remove, each Swap, and the chosen tag of a card that needs a choice.
 * A unit is on unless its key is in [skipped]. Locked removals are never counted.
 */
fun primaryButtonFor(changes: ReviewChanges, skipped: Set<String>, chosenTagId: String?): ReviewButton {
    val units = unitKeys(changes, chosenTagId)
    val of = units.size
    if (of == 0) return if (changes.needsChoice) ReviewButton.KeepAsIs else ReviewButton.GotIt
    val on = units.count { it !in skipped }
    return when (on) {
        0 -> ReviewButton.KeepAsIs
        of -> ReviewButton.Apply(of)
        else -> ReviewButton.ApplyPartial(on, of)
    }
}

/**
 * True for the quiet "no occasions" note: an occasion group, the garment has an occasion now, and none is left once the
 * active units are applied (locked removals count as removed).
 */
fun noTagsLeft(
    isOccasionGroup: Boolean,
    beforeGroupIds: Set<String>,
    changes: ReviewChanges,
    skipped: Set<String>,
    chosenTagId: String?,
): Boolean {
    if (!isOccasionGroup || beforeGroupIds.isEmpty()) return false
    val after = beforeGroupIds.toMutableSet()
    for (change in changes.changes) {
        when (change) {
            is ReviewChange.Add -> if (change.key !in skipped) after += change.tagId
            is ReviewChange.Remove -> if (change.locked || change.key !in skipped) after -= change.tagId
            is ReviewChange.Swap -> {
                if (change.fromLocked || change.key !in skipped) after -= change.fromId
                if (change.key !in skipped) after += change.toId
            }
        }
    }
    if (changes.needsChoice && chosenTagId != null && addKey(chosenTagId) !in skipped) after += chosenTagId
    return after.isEmpty()
}

/**
 * What the active units write. A swap is its removal plus its addition, and a locked removal is never in a delta, so a
 * card for a deleted occasion has an empty delta.
 */
fun deltaFor(garmentId: String, changes: ReviewChanges, skipped: Set<String>, chosenTagId: String?): TagDelta {
    val remove = mutableSetOf<String>()
    val add = mutableSetOf<String>()
    for (change in changes.changes) {
        when (change) {
            is ReviewChange.Add -> if (change.key !in skipped) add += change.tagId
            is ReviewChange.Remove -> if (!change.locked && change.key !in skipped) remove += change.tagId
            is ReviewChange.Swap -> if (change.key !in skipped) {
                if (!change.fromLocked) remove += change.fromId
                add += change.toId
            }
        }
    }
    if (changes.needsChoice && chosenTagId != null && addKey(chosenTagId) !in skipped) add += chosenTagId
    return TagDelta(garmentId, remove, add)
}

/**
 * Writes [delta] into [live] inside the group [categoryId]. Effective removals are the delta's removals that the garment
 * still has; effective adds are the delta's adds that exist now in this group and are not on the garment. A category group
 * never ends with two categories: its removals apply only when every one is still live, and its add applies only when no
 * category is left after the removals and it is the only add; a removal with an add in the delta applies only together with
 * that add. An archived or missing garment, a delta for another garment, and a delta with nothing effective, give a null item.
 */
fun applyDelta(
    live: ClothingItem?,
    delta: TagDelta,
    existingTags: List<GarmentTag>,
    categoryId: String,
    now: Long,
): ApplyResult {
    val unchanged = ApplyResult(null, AppliedDelta(delta.garmentId, emptySet(), emptySet()))
    if (live == null || live.isArchived || live.id != delta.garmentId) return unchanged
    val liveGroupIds = live.tags.filter { it.categoryId == categoryId }.mapTo(mutableSetOf(), GarmentTag::id)
    val liveIds = live.tags.mapTo(mutableSetOf(), GarmentTag::id)
    val existingById = existingTags.associateBy(GarmentTag::id)
    val remove = delta.remove.filterTo(mutableSetOf()) { it in liveGroupIds }
    val add = delta.add.filterTo(mutableSetOf()) { id -> id !in liveIds && existingById[id]?.categoryId == categoryId }
    if (categoryId == CATEGORY_GROUP) {
        if (remove.size != delta.remove.size) return unchanged
        if (delta.add.isNotEmpty() && (add.size != 1 || (liveGroupIds - remove).isNotEmpty())) return unchanged
    }
    if (remove.isEmpty() && add.isEmpty()) return unchanged
    val tags = live.tags.filterNot { it.id in remove } + existingTags.filter { it.id in add }
    return ApplyResult(
        item = live.copy(tags = tags, updatedAtEpochMillis = now),
        applied = AppliedDelta(delta.garmentId, remove, add),
    )
}

/** Undo and "Review again": [applyDelta] of the inverse of what was applied, with the same guards. */
fun revertDelta(
    live: ClothingItem?,
    applied: AppliedDelta,
    existingTags: List<GarmentTag>,
    categoryId: String,
    now: Long,
): ApplyResult = applyDelta(
    live = live,
    delta = TagDelta(applied.garmentId, remove = applied.added, add = applied.removed),
    existingTags = existingTags,
    categoryId = categoryId,
    now = now,
)

private fun addKey(tagId: String): String = "add:$tagId"

private fun unitKeys(changes: ReviewChanges, chosenTagId: String?): List<String> = buildList {
    for (change in changes.changes) {
        when (change) {
            is ReviewChange.Add -> add(change.key)
            is ReviewChange.Remove -> if (!change.locked) add(change.key)
            is ReviewChange.Swap -> add(change.key)
        }
    }
    if (changes.needsChoice && chosenTagId != null) add(addKey(chosenTagId))
}

/** Label order with [collator] (primary strength), ties by id. */
private fun labelOrder(label: (String) -> String, collator: Collator): Comparator<String> =
    Comparator { a, b -> collator.compare(label(a), label(b)).takeIf { it != 0 } ?: a.compareTo(b) }
