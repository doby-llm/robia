package com.gusanitolabs.robia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewChange

/**
 * One garment's review card (R3-DEC-052): the description and the occasions it has now, the signed chips in reading order,
 * the quiet lines, and the footer. Garments are shown by their description, never by name. A chooser card shows its locked
 * chips and a "Choose" button until a tag is picked, which then becomes a "+" chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagReviewCard(
    card: ReviewCardState,
    identity: String,
    nowLine: String?,
    chosenTagId: String?,
    noTagsLeft: Boolean,
    labelOf: (String) -> String,
    resolved: Boolean,
    photo: @Composable () -> Unit,
    onToggle: (String) -> Unit,
    onChoose: () -> Unit,
    onApply: () -> Unit,
    onReject: () -> Unit,
) {
    val button = buttonOf(card, chosenTagId)
    val applyLabel = when (button) {
        is ReviewButton.Apply -> pluralStringResource(R.plurals.review_apply_n, button.count, button.count)
        is ReviewButton.ApplyPartial -> stringResource(R.string.review_apply_n_of_m, button.on, button.of)
        ReviewButton.KeepAsIs -> stringResource(R.string.review_keep_as_is)
        ReviewButton.GotIt -> stringResource(R.string.review_got_it)
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    photo()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = identity,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (nowLine != null) {
                        Text(
                            text = nowLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (change in chipsOf(card, chosenTagId)) {
                    ReviewCardChip(
                        change = change,
                        skipped = card.skipped,
                        chosen = chosenTagId != null && change is ReviewChange.Add && change.tagId == chosenTagId,
                        labelOf = labelOf,
                        enabled = !resolved,
                        onToggle = onToggle,
                    )
                }
            }
            if (card.changes.needsChoice && chosenTagId == null) {
                FilledTonalButton(
                    onClick = onChoose,
                    enabled = !resolved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tag_review_choose),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(R.string.tag_review_choose_manually),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (noTagsLeft) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.review_none_left_occ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (allChangesSkipped(card, chosenTagId)) {
                Text(
                    text = stringResource(R.string.review_all_skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (button.showsReject) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !resolved,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.review_reject),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // A chooser card with no tag yet cannot be decided: its Keep as is (or Apply) would do nothing, so it is disabled.
                FilledTonalButton(
                    onClick = onApply,
                    enabled = !resolved && canDecideNow(card, chosenTagId),
                    modifier = Modifier
                        .weight(if (button.showsReject) 1.45f else 1f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        text = applyLabel,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One change of a card: a switch chip, or a framed category swap. */
@Composable
private fun ReviewCardChip(
    change: ReviewChange,
    skipped: Set<String>,
    chosen: Boolean,
    labelOf: (String) -> String,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    val isSkipped = change.key in skipped
    when (change) {
        is ReviewChange.Add -> ReviewChangeChip(
            label = labelOf(change.tagId),
            visual = chipVisual(change, isSkipped),
            stateText = chipText(chipDescription(change, isSkipped, chosen), labelOf(change.tagId)),
            enabled = enabled,
            onToggle = { onToggle(change.key) },
        )
        is ReviewChange.Remove -> ReviewChangeChip(
            label = labelOf(change.tagId),
            visual = chipVisual(change, isSkipped),
            stateText = chipText(chipDescription(change, isSkipped), labelOf(change.tagId)),
            enabled = enabled,
            onToggle = { onToggle(change.key) },
        )
        is ReviewChange.Swap -> ReviewSwapChip(
            fromLabel = labelOf(change.fromId),
            toLabel = labelOf(change.toId),
            removal = chipVisual(ReviewChange.Remove(change.fromId, locked = change.fromLocked), isSkipped),
            addition = chipVisual(ReviewChange.Add(change.toId), isSkipped),
            frame = chipVisual(change, isSkipped),
            stateText = chipText(chipDescription(change, isSkipped), labelOf(change.toId), labelOf(change.fromId)),
            enabled = enabled,
            onToggle = { onToggle(change.key) },
        )
    }
}

/** The spoken text of a chip: its `review_cd_*` string with the tag names. [fromLabel] is the old category of a swap. */
@Composable
private fun chipText(description: ChipDescription, label: String, fromLabel: String = ""): String = when (description) {
    ChipDescription.Add -> stringResource(R.string.review_cd_add, label)
    ChipDescription.AddOff -> stringResource(R.string.review_cd_add_off, label)
    ChipDescription.Remove -> stringResource(R.string.review_cd_remove, label)
    ChipDescription.RemoveOff -> stringResource(R.string.review_cd_remove_off, label)
    ChipDescription.Swap -> stringResource(R.string.review_cd_swap, label, fromLabel)
    ChipDescription.SwapOff -> stringResource(R.string.review_cd_swap_off, label, fromLabel)
    ChipDescription.Replace -> stringResource(R.string.review_cd_replace, label)
    ChipDescription.ReplaceOff -> stringResource(R.string.review_cd_replace_off, label)
    ChipDescription.Removed -> stringResource(R.string.review_cd_removed, label)
}

/** The pinned "N left" header once the scan has ended, with the review progress bar. */
@Composable
internal fun ReviewLeftHeader(
    leftCount: Int,
    progress: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pluralStringResource(R.plurals.review_left, leftCount, leftCount),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
        )
    }
}

/** "Category · Colour" of a garment: its category tag (or "No category") and its colour name; never the garment name. */
@Composable
internal fun tagReviewIdentityLine(item: ClothingItem): String {
    val context = LocalContext.current
    val category = item.tags.firstOrNull { tag -> tag.categoryId == TAG_KIND_CATEGORY }?.localizedTagLabel(context)
    val colour = item.colorMetrics.primaryDisplayLabel
        ?.takeUnless { label -> label == DisplayColorLabel.Unknown }
        ?.let { label -> stringResource(label.colorStringRes()) }
    return identityLine(category, colour, stringResource(R.string.tag_review_none))
}

/** The colour names the add flow uses (AddEditClothingScreen): the same strings, so a colour reads the same on both screens. */
private fun DisplayColorLabel.colorStringRes(): Int = when (this) {
    DisplayColorLabel.Black -> R.string.color_black
    DisplayColorLabel.Blue -> R.string.color_blue
    DisplayColorLabel.Brown -> R.string.color_brown
    DisplayColorLabel.Gray -> R.string.color_gray
    DisplayColorLabel.Green -> R.string.color_green
    DisplayColorLabel.Orange -> R.string.color_orange
    DisplayColorLabel.Pink -> R.string.color_pink
    DisplayColorLabel.Purple -> R.string.color_purple
    DisplayColorLabel.Red -> R.string.color_red
    DisplayColorLabel.White -> R.string.color_white
    DisplayColorLabel.Yellow -> R.string.color_yellow
    DisplayColorLabel.Multicolor -> R.string.color_multicolor
    DisplayColorLabel.Unknown -> R.string.color_unknown
}
