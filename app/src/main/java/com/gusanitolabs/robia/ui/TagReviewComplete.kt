package com.gusanitolabs.robia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.extras
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel

/** Most thumbnails the completion shows: the first garments the landed writes updated, in write order. */
internal const val REVIEW_COMPLETE_MAX_THUMBNAILS = 5

/**
 * Edit changes: [Looking] keeps the list up while the user looks at "Reviewed". [Returned]: a card went back to Pending, so the
 * list stays up until that card is decided again.
 */
internal enum class ReviewEditState { Off, Looking, Returned }

/** The edit state after a change of the pending count: a card back in the list is a return, and deciding it again ends the edit. */
internal fun reviewEditAfter(state: ReviewEditState, leftCount: Int): ReviewEditState = when {
    state == ReviewEditState.Off -> ReviewEditState.Off
    leftCount > 0 -> ReviewEditState.Returned
    state == ReviewEditState.Returned -> ReviewEditState.Off
    else -> ReviewEditState.Looking
}

/**
 * The completion view replaces the list and the bottom bar when the review is complete: no write or revert in flight, no card
 * Pending (both are [shouldFinishTagReview]'s guards), at least one card decided and shown (a review that found nothing shows no
 * screen), and not while the user is editing. The picker and the discard or leave dialogs do not hide it: they open over it.
 */
internal fun showsCompletion(
    session: TagReviewSession,
    isScanning: Boolean,
    revertsInFlight: Boolean,
    edit: ReviewEditState,
): Boolean = edit == ReviewEditState.Off && session.reviewed.isNotEmpty() &&
    shouldFinishTagReview(
        session,
        isScanning = isScanning,
        pickerOpen = false,
        discardDialogOpen = false,
        finished = false,
        revertsInFlight = revertsInFlight,
    )

/** The tally entries of the completion view. [Kept] is garments left as they were, with no sign. */
internal enum class CompletionEntry { Added, Removed, Kept }

/** The tally entries that show, in reading order: an entry shows only when its number is above 0. */
internal fun completionEntries(tallies: ReviewTallies): List<Pair<CompletionEntry, Int>> = listOf(
    CompletionEntry.Added to tallies.tagsAdded,
    CompletionEntry.Removed to tallies.tagsRemoved,
    CompletionEntry.Kept to tallies.garmentsKept,
).filter { (_, count) -> count > 0 }

/** The title counts the garments updated; with none, the title says so instead. */
internal fun completionHasChanges(tallies: ReviewTallies): Boolean = tallies.garmentsUpdated > 0

/**
 * The thumbnails of the garments the landed writes updated: a write that changed nothing is not one, and a reverted garment is no
 * longer in [written]. At most [REVIEW_COMPLETE_MAX_THUMBNAILS], each garment once.
 */
internal fun completionThumbnails(session: TagReviewSession, written: List<ClothingItem>): List<ClothingItem> {
    val updatedIds = session.cards.mapNotNullTo(mutableSetOf()) { card ->
        val applied = (card.decision as? ReviewDecision.Applied)?.applied
        card.garmentId.takeIf { applied != null && (applied.added.isNotEmpty() || applied.removed.isNotEmpty()) }
    }
    return written
        .filter { item -> item.id in updatedIds }
        .distinctBy(ClothingItem::id)
        .take(REVIEW_COMPLETE_MAX_THUMBNAILS)
}

/**
 * The static completion of a tag review: a check, the title, the tally in the add and remove signs, the thumbnails and
 * "Edit changes". No sparkle and no timer; Done is the bottom bar of the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagReviewCompletion(
    tallies: ReviewTallies,
    thumbnails: List<ClothingItem>,
    onEditChanges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = if (completionHasChanges(tallies)) {
                pluralStringResource(R.plurals.review_complete_tags, tallies.garmentsUpdated, tallies.garmentsUpdated)
            } else {
                stringResource(R.string.review_complete_none)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            completionEntries(tallies).forEach { (entry, count) -> CompletionTallyEntry(entry, count) }
        }
        if (thumbnails.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                thumbnails.forEach { item ->
                    Box(modifier = Modifier.size(width = 36.dp, height = 42.dp)) {
                        TagReviewPhoto(
                            item = item,
                            swatch = item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
                        )
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = onEditChanges,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.review_complete_edit))
        }
    }
}

/** One tally entry: a filled sign in the add or remove colours, then the words; the kept entry has no sign. */
@Composable
private fun CompletionTallyEntry(entry: CompletionEntry, count: Int) {
    val text = when (entry) {
        CompletionEntry.Added -> pluralStringResource(R.plurals.review_complete_added, count, count)
        CompletionEntry.Removed -> pluralStringResource(R.plurals.review_complete_removed, count, count)
        CompletionEntry.Kept -> pluralStringResource(R.plurals.review_complete_kept, count, count)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entry != CompletionEntry.Kept) {
            val palette = if (entry == CompletionEntry.Added) MaterialTheme.extras.addColors else MaterialTheme.extras.removeColors
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .background(palette.sign, CircleShape),
            ) {
                Icon(
                    imageVector = if (entry == CompletionEntry.Added) Icons.Rounded.Add else Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = palette.onSign,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
