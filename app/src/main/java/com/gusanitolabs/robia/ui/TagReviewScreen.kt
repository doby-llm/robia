package com.gusanitolabs.robia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.suggestions.ProposalKind
import com.gusanitolabs.robia.suggestions.ReviewPlanner
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.suggestions.engine.EmbeddingFailure
import java.util.UUID

private const val TAG_REVIEW_THUMBNAIL_MAX_EDGE_PX = 384
private const val TAG_KIND_CATEGORY = "category"
private const val TAG_KIND_OCCASION = "occasion"
private val ReviewedTagKinds = setOf(TAG_KIND_CATEGORY, TAG_KIND_OCCASION)

internal data class TagReviewCandidate(
    val item: ClothingItem,
    val proposal: ReviewProposal,
)

/** The change set plus the garments as they were before the change was saved (deleted tags are already gone from them). */
internal data class TagReviewRequest(
    val changeSet: TagChangeSet,
    val garments: List<ClothingItem>,
)

/** Accept all takes every proposal with a concrete target; a manual-choice proposal joins only when the user accepts it. */
internal fun tagReviewCommitCandidates(
    candidates: List<TagReviewCandidate>,
    acceptedIds: Set<String>,
): List<TagReviewCandidate> = candidates.filter { candidate ->
    candidate.proposal.kind == ProposalKind.Suggested || candidate.item.id in acceptedIds
}

/**
 * Change set for a saved tag (TS§3.10). Null for a new tag of another kind, or when the name did not change:
 * description-only edits and the suggest-occasions switch never affect garments.
 */
internal fun savedTagChangeSet(before: List<GarmentTag>, saved: GarmentTag): TagChangeSet? {
    if (saved.categoryId !in ReviewedTagKinds) return null
    val existing = before.firstOrNull { tag -> tag.id == saved.id }
    val operation = when {
        existing == null -> TagOperation.Added
        existing.name == saved.name -> return null
        else -> TagOperation.Renamed
    }
    val after = before.filterNot { tag -> tag.id == saved.id } + saved
    return tagChangeSet(operation, saved.categoryId, setOf(saved.id), before, after)
}

/** Categories and occasions are both reviewed after a deletion (PLAN-CHANGE 15 supersedes DEC-010). */
internal fun deletedTagChangeSet(before: List<GarmentTag>, deleted: GarmentTag): TagChangeSet? {
    if (deleted.categoryId !in ReviewedTagKinds) return null
    val after = before.filterNot { tag -> tag.id == deleted.id }
    return tagChangeSet(TagOperation.Deleted, deleted.categoryId, setOf(deleted.id), before, after)
}

/** Only the default tags that are added or renamed by the restore are touched (DEC-010). */
internal fun restoredTagChangeSet(
    before: List<GarmentTag>,
    categoryId: String,
    defaults: List<GarmentTag>,
): TagChangeSet? {
    if (categoryId !in ReviewedTagKinds) return null
    val namesById = before.associate { tag -> tag.id to tag.name }
    val touched = defaults
        .filter { tag -> namesById[tag.id] != tag.name }
        .mapTo(mutableSetOf(), GarmentTag::id)
    if (touched.isEmpty()) return null
    val restoredIds = defaults.mapTo(mutableSetOf(), GarmentTag::id)
    val after = before.filterNot { tag -> tag.id in restoredIds } + defaults
    return tagChangeSet(TagOperation.RestoredDefault, categoryId, touched, before, after)
}

private fun tagChangeSet(
    operation: TagOperation,
    categoryId: String,
    touchedTagIds: Set<String>,
    before: List<GarmentTag>,
    after: List<GarmentTag>,
) = TagChangeSet(
    id = UUID.randomUUID().toString(),
    operation = operation,
    categoryId = categoryId,
    touchedTagIds = touchedTagIds,
    beforeTags = before,
    afterTags = after,
)

/** Shown after a tag change is saved. Declining keeps the change; accepting opens the scanning review. */
@Composable
internal fun TagReviewPromptDialog(
    modelReady: Boolean,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_review_prompt_title)) },
        text = { Text(stringResource(if (modelReady) R.string.tag_review_prompt_body else R.string.tag_review_needs_model)) },
        confirmButton = {
            Button(onClick = onReview, enabled = modelReady) {
                Text(stringResource(R.string.tag_review_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

/**
 * Review of a category or occasion change. Garments are scanned incrementally on Dispatchers.Default, so the screen
 * opens at once and proposals appear as they are found. Nothing is written until [onCommit]; leaving the screen
 * cancels the scan and discards the garment proposals, while the tag change itself stays.
 */
@Composable
internal fun TagReviewScreen(
    innerPadding: PaddingValues,
    request: TagReviewRequest,
    prepareScan: suspend (TagChangeSet, List<ClothingItem>) -> ReviewPlanner.Scan,
    onCommit: (List<ClothingItem>) -> Unit,
    onRequestDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val changeSet = request.changeSet
    val labelsById = remember(changeSet.id) {
        (changeSet.beforeTags + changeSet.afterTags).associate { tag -> tag.id to tag.localizedTagLabel(context) }
    }
    val kindTags = remember(changeSet.id) { changeSet.afterTags.filter { tag -> tag.categoryId == changeSet.categoryId } }
    // Before and after: a deleted tag is still the "current" value of a garment, but it can no longer be picked.
    val kindIds = remember(changeSet.id) {
        (changeSet.beforeTags + changeSet.afterTags)
            .filter { tag -> tag.categoryId == changeSet.categoryId }
            .mapTo(mutableSetOf(), GarmentTag::id)
    }
    // Pinned per change set: a later sync emission neither restarts the scan nor drops accepted proposals.
    val garments = remember(changeSet.id) { request.garments.filterNot(ClothingItem::isArchived) }
    val candidates = remember(changeSet.id) { mutableStateListOf<TagReviewCandidate>() }
    val edits = remember(changeSet.id) { mutableStateMapOf<String, Set<String>>() }
    val acceptedIds = remember(changeSet.id) { mutableStateListOf<String>() }
    var scannedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var skippedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var isScanning by remember(changeSet.id) { mutableStateOf(true) }
    var modelUnavailable by remember(changeSet.id) { mutableStateOf(false) }
    var pickerGarmentId by remember(changeSet.id) { mutableStateOf<String?>(null) }

    // Keyed on the change set: a new change cancels the running scan, and only the current change set's results land.
    LaunchedEffect(changeSet.id) {
        candidates.clear()
        scannedCount = 0
        isScanning = true
        modelUnavailable = false
        val scan = try {
            prepareScan(changeSet, garments)
        } catch (unavailable: EmbeddingFailure) {
            modelUnavailable = true
            isScanning = false
            return@LaunchedEffect
        }
        skippedCount = scan.skippedCount
        scan.scanEach(garments) { garment, proposal ->
            if (proposal != null) candidates += TagReviewCandidate(garment, proposal)
            scannedCount += 1
        }
        isScanning = false
    }

    val totalCount = garments.size.coerceAtLeast(1)
    val progress = if (isScanning) scannedCount.toFloat() / totalCount.toFloat() else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "tagReviewProgress",
    )

    BackHandler { onRequestDiscard() }

    pickerGarmentId?.let { garmentId ->
        candidates.firstOrNull { candidate -> candidate.item.id == garmentId }?.let { candidate ->
            val current = edits[garmentId] ?: candidate.proposal.proposed
            TagReviewPickerDialog(
                title = stringResource(
                    R.string.tag_review_picker_title,
                    stringResource(
                        if (changeSet.categoryId == TAG_KIND_OCCASION) R.string.tag_review_occasion_noun else R.string.tag_review_category_noun,
                    ),
                    candidate.item.name,
                ),
                options = kindTags,
                selectedIds = current.filterTo(mutableSetOf()) { id -> id in kindIds },
                multiChoice = changeSet.categoryId == TAG_KIND_OCCASION,
                labelsById = labelsById,
                onDismiss = { pickerGarmentId = null },
                onConfirm = { chosen ->
                    edits[garmentId] = current - kindIds + chosen
                    pickerGarmentId = null
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 128.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TagReviewHeader(
                    changeSet = changeSet,
                    labelsById = labelsById,
                    garmentCount = garments.size,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ReviewProgressHeader(
                    title = stringResource(R.string.tag_review_scan_title),
                    countText = stringResource(R.string.tag_review_scanning_count, scannedCount, garments.size),
                    progress = animatedProgress,
                )
            }

            if (modelUnavailable) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReviewEmptyCard(
                        title = stringResource(R.string.tag_review_needs_model),
                        body = stringResource(R.string.tag_review_needs_model_body),
                    )
                }
            } else if (!isScanning && candidates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReviewEmptyCard(
                        title = stringResource(R.string.tag_review_empty_title),
                        body = stringResource(R.string.tag_review_empty_body),
                    )
                }
            }

            items(
                items = candidates,
                key = { candidate -> candidate.item.id },
            ) { candidate ->
                val garmentId = candidate.item.id
                TagReviewCandidateCard(
                    candidate = candidate,
                    proposedIds = edits[garmentId] ?: candidate.proposal.proposed,
                    kindIds = kindIds,
                    labelsById = labelsById,
                    accepted = garmentId in acceptedIds,
                    onPick = { pickerGarmentId = garmentId },
                    onToggleAccept = {
                        if (garmentId in acceptedIds) acceptedIds.remove(garmentId) else acceptedIds.add(garmentId)
                    },
                )
            }

            if (isScanning || (!modelUnavailable && skippedCount > 0)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReviewHelperRow(
                        isScanning = isScanning,
                        text = if (isScanning) {
                            stringResource(R.string.tag_review_checking_helper)
                        } else {
                            stringResource(R.string.tag_review_skipped, skippedCount)
                        },
                    )
                }
            }
        }

        // Accept all saves every proposal with a concrete target; manual-choice proposals stay out unless accepted by hand.
        val commitCandidates = tagReviewCommitCandidates(candidates.toList(), acceptedIds.toSet())
        ReviewBottomBar(
            label = if (commitCandidates.isNotEmpty()) {
                stringResource(R.string.tag_review_accept_all, commitCandidates.size)
            } else {
                stringResource(R.string.done)
            },
            enabled = !isScanning,
            onClick = {
                val now = System.currentTimeMillis()
                val updatedItems = commitCandidates.map { candidate ->
                    val proposed = edits[candidate.item.id] ?: candidate.proposal.proposed
                    candidate.item.copy(
                        tags = changeSet.afterTags.filter { tag -> tag.id in proposed },
                        updatedAtEpochMillis = now,
                    )
                }
                onCommit(updatedItems)
            },
        )
    }
}

@Composable
private fun TagReviewHeader(
    changeSet: TagChangeSet,
    labelsById: Map<String, String>,
    garmentCount: Int,
) {
    val context = LocalContext.current
    val noun = stringResource(
        if (changeSet.categoryId == TAG_KIND_OCCASION) R.string.tag_review_occasion_noun else R.string.tag_review_category_noun,
    )
    val touchedNames = changeSet.touchedTagIds.mapNotNull(labelsById::get).joinToString()
    val isDeleted = changeSet.operation == TagOperation.Deleted

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isDeleted) {
                stringResource(R.string.tag_review_deleted_header, noun, touchedNames)
            } else {
                stringResource(R.string.tag_review_added_header, noun, touchedNames)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                !isDeleted -> context.resources.getQuantityString(R.plurals.tag_review_added_body, garmentCount, garmentCount, touchedNames)
                changeSet.categoryId == TAG_KIND_OCCASION -> context.resources.getQuantityString(R.plurals.tag_review_deleted_occasion_body, garmentCount, garmentCount)
                else -> context.resources.getQuantityString(R.plurals.tag_review_deleted_body, garmentCount, garmentCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagReviewCandidateCard(
    candidate: TagReviewCandidate,
    proposedIds: Set<String>,
    kindIds: Set<String>,
    labelsById: Map<String, String>,
    accepted: Boolean,
    onPick: () -> Unit,
    onToggleAccept: () -> Unit,
) {
    val isChooseManually = candidate.proposal.kind == ProposalKind.ChooseManually
    val currentLabels = candidate.proposal.before.filter { id -> id in kindIds }.mapNotNull(labelsById::get)
    val proposedLabels = proposedIds.filter { id -> id in kindIds }.mapNotNull(labelsById::get)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            TagReviewPhoto(candidate.item)
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Stacked, never side by side: "From" and "-> To" each take the full card width, so long names
                // ellipsize instead of wrapping letter by letter and resizing the card.
                Text(
                    text = currentLabels.joinToString().ifEmpty { stringResource(R.string.tag_review_none) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    onClick = onPick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (proposedLabels.isEmpty()) {
                                stringResource(if (isChooseManually) R.string.tag_review_choose else R.string.tag_review_none)
                            } else {
                                proposedLabels.joinToString() + " ▾"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isChooseManually) {
                    Text(
                        text = stringResource(R.string.tag_review_choose_manually),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onToggleAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (accepted) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = stringResource(if (accepted) R.string.tag_review_accepted else R.string.accept),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = if (accepted) 6.dp else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagReviewPhoto(item: ClothingItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf(String::isNotBlank)
        if (photoUri != null) {
            BoundedGarmentImage(
                photoUri = photoUri,
                thumbnailMaxEdgePx = TAG_REVIEW_THUMBNAIL_MAX_EDGE_PX,
                purpose = ImagePurpose.Browse,
                sourceRevision = item.id,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** Single choice for categories (the "no category" row clears it); multi choice with "None" for occasions. */
@Composable
private fun TagReviewPickerDialog(
    title: String,
    options: List<GarmentTag>,
    selectedIds: Set<String>,
    multiChoice: Boolean,
    labelsById: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selection by remember(selectedIds) { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TagReviewPickerRow(
                    label = stringResource(if (multiChoice) R.string.tag_review_none_occasion else R.string.tag_review_none),
                    selected = selection.isEmpty(),
                    multiChoice = multiChoice,
                    onClick = {
                        if (multiChoice) selection = emptySet() else onConfirm(emptySet())
                    },
                )
                options.forEach { tag ->
                    val isSelected = tag.id in selection
                    TagReviewPickerRow(
                        label = labelsById[tag.id] ?: tag.name,
                        selected = isSelected,
                        multiChoice = multiChoice,
                        onClick = {
                            if (multiChoice) {
                                selection = if (isSelected) selection - tag.id else selection + tag.id
                            } else {
                                onConfirm(setOf(tag.id))
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (multiChoice) {
                Button(onClick = { onConfirm(selection) }) {
                    Text(stringResource(R.string.done))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TagReviewPickerRow(
    label: String,
    selected: Boolean,
    multiChoice: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiChoice) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
