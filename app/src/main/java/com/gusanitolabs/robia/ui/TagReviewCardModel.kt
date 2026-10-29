package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.suggestions.deltaFor
import com.gusanitolabs.robia.suggestions.primaryButtonFor
import com.gusanitolabs.robia.suggestions.reviewChangesFor
import com.gusanitolabs.robia.suggestions.tagIdsExcept
import java.text.Collator

/** The three chip families of a review card. */
internal enum class ChipKind { Add, Remove, Swap }

/**
 * How one chip looks (state table, R3-DEC-052). [filledSign] is the switch's on state: a filled sign means the change will
 * happen, a hollow sign means the user left it out. For a [ChipKind.Swap] this describes the frame (solid or dashed, and whether
 * the frame is a switch); its halves are looked up as the removal and the addition.
 */
internal data class ChipVisual(
    val kind: ChipKind,
    val filledSign: Boolean,
    val struck: Boolean,
    val dashedOutline: Boolean,
    val switch: Boolean,
)

/** The visual state of one change. A locked removal is always filled and struck, and is never a switch. */
internal fun chipVisual(change: ReviewChange, skipped: Boolean): ChipVisual = when (change) {
    is ReviewChange.Add -> ChipVisual(ChipKind.Add, filledSign = !skipped, struck = false, dashedOutline = skipped, switch = true)
    is ReviewChange.Remove -> if (change.locked) {
        ChipVisual(ChipKind.Remove, filledSign = true, struck = true, dashedOutline = false, switch = false)
    } else {
        ChipVisual(ChipKind.Remove, filledSign = !skipped, struck = !skipped, dashedOutline = false, switch = true)
    }
    is ReviewChange.Swap -> ChipVisual(
        ChipKind.Swap,
        filledSign = !skipped,
        struck = false,
        dashedOutline = skipped,
        switch = !change.fromLocked,
    )
}

/** The TalkBack text of a chip's switch. Each value names its `review_cd_*` string; the composable supplies the arguments. */
internal enum class ChipDescription(val resName: String) {
    Add("review_cd_add"),
    AddOff("review_cd_add_off"),
    Remove("review_cd_remove"),
    RemoveOff("review_cd_remove_off"),
    Swap("review_cd_swap"),
    SwapOff("review_cd_swap_off"),
    Replace("review_cd_replace"),
    ReplaceOff("review_cd_replace_off"),
    Removed("review_cd_removed"),
}

/**
 * The description of a chip. [chosen] marks the tag a chooser picked: it reads "Set" rather than "Add". A swap with a locked
 * "−" half has only its "+" half as a switch, so it reads like a chosen tag.
 */
internal fun chipDescription(change: ReviewChange, skipped: Boolean, chosen: Boolean = false): ChipDescription = when (change) {
    is ReviewChange.Add -> when {
        chosen && skipped -> ChipDescription.ReplaceOff
        chosen -> ChipDescription.Replace
        skipped -> ChipDescription.AddOff
        else -> ChipDescription.Add
    }
    is ReviewChange.Remove -> when {
        change.locked -> ChipDescription.Removed
        skipped -> ChipDescription.RemoveOff
        else -> ChipDescription.Remove
    }
    is ReviewChange.Swap -> when {
        change.fromLocked && skipped -> ChipDescription.ReplaceOff
        change.fromLocked -> ChipDescription.Replace
        skipped -> ChipDescription.SwapOff
        else -> ChipDescription.Swap
    }
}

/** "Category · Colour". The garment name is never shown; with no known colour there is no separator. */
internal fun identityLine(categoryLabel: String?, colourLabel: String?, noCategoryLabel: String): String {
    val category = categoryLabel?.takeIf(String::isNotBlank) ?: noCategoryLabel
    val colour = colourLabel?.takeIf(String::isNotBlank) ?: return category
    return "$category · $colour"
}

/** The occasions the garment has before the change, or [noneLabel] when it has none. */
internal fun nowLine(groupLabelsBefore: List<String>, noneLabel: String): String =
    groupLabelsBefore.filter(String::isNotBlank).joinToString().ifEmpty { noneLabel }

/** What the pinned header shows: the scan header while the scan runs, else "N left" with the progress decided / found. */
internal data class ReviewHeaderState(val showsScan: Boolean, val leftCount: Int, val progress: Float)

internal fun reviewHeaderState(
    isScanning: Boolean,
    isPreparing: Boolean,
    leftCount: Int,
    decided: Int,
    found: Int,
): ReviewHeaderState = ReviewHeaderState(
    showsScan = isScanning || isPreparing,
    leftCount = leftCount,
    progress = if (found <= 0) 0f else (decided.toFloat() / found).coerceIn(0f, 1f),
)

/**
 * The card of one garment. [liveGroupTagIds] are the garment's own tags of the group (never the proposal's `before`), and
 * [offeredTagIds] are the group's tags that exist now and are not on the garment: the only tags a chooser offers.
 */
internal fun reviewCardStateFor(
    proposal: ReviewProposal,
    liveGroupTagIds: Set<String>,
    groupTagIds: Set<String>,
    isCategoryGroup: Boolean,
    existingTagIds: Set<String>,
    label: (String) -> String,
    collator: Collator,
): ReviewCardState = ReviewCardState(
    garmentId = proposal.garmentId,
    changes = reviewChangesFor(proposal, groupTagIds, isCategoryGroup, existingTagIds, liveGroupTagIds, label, collator),
    liveGroupTagIds = liveGroupTagIds,
    offeredTagIds = (groupTagIds intersect existingTagIds) - liveGroupTagIds,
)

/**
 * The garment as the database holds it after the tag change is saved. A deletion takes the deleted tags off every garment, so
 * the card and the commit read this view, never the snapshot taken before the write. Any other change leaves the garment as it is.
 */
internal fun liveViewOf(garment: ClothingItem, changeSet: TagChangeSet): ClothingItem =
    if (changeSet.operation == TagOperation.Deleted) {
        garment.copy(tags = garment.tags.filterNot { tag -> tag.id in changeSet.touchedTagIds })
    } else {
        garment
    }

/** A garment whose card is not pending has nothing to toggle (only locked changes): it is closed at scan time, with no write. */
internal fun isHiddenOnScan(session: TagReviewSession, garmentId: String): Boolean =
    session.pending.none { card -> card.garmentId == garmentId }

/** The chosen tag, only while it still exists: a deleted tag is no choice and shows no chip. */
internal fun chosenTagOf(card: ReviewCardState, existingTagIds: Set<String>): String? =
    card.chosenTagId?.takeIf { tagId -> tagId in existingTagIds }

/** The chips in reading order: the changes as REV-MODEL orders them, then the chosen tag as a "+" chip. */
internal fun chipsOf(card: ReviewCardState, chosenTagId: String?): List<ReviewChange> =
    card.changes.changes + listOfNotNull(chosenTagId?.let { tagId -> ReviewChange.Add(tagId) })

/**
 * Whether a long press on a chip asks for another tag (R3-DEC-060): an Add (the chosen tag of a card that needs a choice is one),
 * and the "+" half of a swap, also when its "−" half is locked. Never a Remove (a "−" or a locked chip), and never on a card that
 * is not Pending.
 */
internal fun replaceableChip(card: ReviewCardState, change: ReviewChange): Boolean = card.decision is ReviewDecision.Pending &&
    change !is ReviewChange.Remove

/**
 * The tags the picker offers for the chip [key]: the card's offered tags minus the tags of its other chips (the chosen tag
 * included), so a tag already on the card is not offered twice. The pressed chip's own tag stays in: it is the original proposal.
 * [key] null is the chooser of a card that needs a choice, opened by its "Choose" button or by its chosen chip.
 */
internal fun replacementOptions(card: ReviewCardState, chosenTagId: String?, key: String?): Set<String> {
    val otherTags = card.changes.tagIdsExcept(key) + listOfNotNull(chosenTagId.takeIf { key != null })
    return card.offeredTagIds - otherTags
}

/** The button of a card, with the chosen tag already validated. */
internal fun buttonOf(card: ReviewCardState, chosenTagId: String?): ReviewButton =
    primaryButtonFor(card.changes, card.skipped, chosenTagId)

/** "All changes skipped": the button is Keep as is and there was at least one change or choice to leave out. */
internal fun allChangesSkipped(card: ReviewCardState, chosenTagId: String?): Boolean =
    buttonOf(card, chosenTagId) == ReviewButton.KeepAsIs &&
        (chosenTagId != null || card.changes.changes.any { change -> change !is ReviewChange.Remove || !change.locked })

/**
 * What one accepted card writes. An applied card writes the delta it was decided with. A pending card (Accept all) writes its
 * chips as they are, but only once a required choice is made. A rejected card (Reject, or Keep as is) writes nothing.
 */
internal fun commitDeltaFor(card: ReviewCardState?, existingTagIds: Set<String>): TagDelta? {
    if (card == null) return null
    return when (val decision = card.decision) {
        is ReviewDecision.Applied -> decision.intended
        ReviewDecision.Pending -> {
            val chosen = chosenTagOf(card, existingTagIds)
            if (card.changes.needsChoice && chosen == null) {
                null
            } else {
                deltaFor(card.garmentId, card.changes, card.skipped, chosen)
            }
        }
        is ReviewDecision.Rejected -> null
    }
}
