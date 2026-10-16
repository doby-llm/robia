package com.gusanitolabs.robia.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.ImagePurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val COLOR_REVIEW_SECONDARY_MIN_RATIO = 0.05
private const val COLOR_REVIEW_THUMBNAIL_MAX_EDGE_PX = 384

internal data class ColorPaletteChangeSet(
    val beforePalette: List<MainColor>,
    val afterPalette: List<MainColor>,
    val touchedColorId: String?,
    val operation: ColorPaletteOperation,
    val requiresCommitOnSave: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
)

internal enum class ColorPaletteOperation { Added, Edited, Deleted, RestoredDefault }

/**
 * Shown after a colour change is saved. Not now keeps the change; tapping outside or back cancels it (the caller undoes
 * it, and garments are left alone); accepting opens the review.
 */
@Composable
internal fun ColorReviewPromptDialog(
    onNotNow: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.color_review_prompt_title)) },
        text = { Text(stringResource(R.string.color_review_prompt_body)) },
        confirmButton = {
            Button(onClick = onReview) {
                Text(stringResource(R.string.color_review_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

@Composable
internal fun ColorReviewScreen(
    innerPadding: PaddingValues,
    items: List<ClothingItem>,
    changeSet: ColorPaletteChangeSet,
    discardDialogOpen: Boolean,
    onCommit: (ColorPaletteChangeSet, List<ClothingItem>) -> Unit,
    onRequestDiscard: () -> Unit,
) {
    val context = LocalContext.current
    // Pinned at review start so a later sync emission neither restarts the scan nor drops accepted items.
    val reviewItems = remember(changeSet.id) {
        if (changeSet.requiresCommitOnSave) items.filterNot(ClothingItem::isArchived) else items.filter(ClothingItem::isEligibleForColorReview)
    }
    // Accepted ids stay in [candidates] until the commit; discarded cards are never saved. Hidden ids are the resolved cards whose exit has finished.
    val candidates = remember(changeSet.id) { mutableStateListOf<ColorReviewCandidate>() }
    val acceptedIds = remember(changeSet.id) { mutableStateListOf<String>() }
    val discardedIds = remember(changeSet.id) { mutableStateListOf<String>() }
    val hiddenIds = remember(changeSet.id) { mutableStateListOf<String>() }
    var processedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var isScanning by remember(changeSet.id) { mutableStateOf(true) }
    var pickerTarget by remember(changeSet.id) { mutableStateOf<ColorReviewPickerTarget?>(null) }
    var committed by remember(changeSet.id) { mutableStateOf(false) }

    LaunchedEffect(changeSet.id) {
        candidates.clear()
        acceptedIds.clear()
        discardedIds.clear()
        hiddenIds.clear()
        processedCount = 0
        isScanning = true
        val paletteIds = changeSet.afterPalette.map(MainColor::id).toSet()
        val firstPaletteId = changeSet.afterPalette.firstOrNull()?.id
        reviewItems.forEach { item ->
            val needsManualRemap = item.needsManualPaletteRemap(changeSet)
            val imageUri = item.photoUri?.takeIf(String::isNotBlank)?.let(Uri::parse)
            val proposedMetrics = if (imageUri != null && changeSet.afterPalette.isNotEmpty()) {
                val matches = runCatching {
                    withContext(Dispatchers.IO) {
                        ClothingImageStore.extractPaletteColorDiagnostics(context, imageUri, changeSet.afterPalette).matches
                    }
                }.getOrDefault(emptyList())
                matches.toColorMetrics()
            } else {
                item.colorMetrics
            }
            val current = item.colorMetrics
            val pair = colorReviewPair(
                proposedPrimaryId = proposedMetrics.primaryPaletteColorId,
                currentPrimaryId = current.primaryPaletteColorId,
                proposedSecondaryId = proposedMetrics.secondaryPaletteColorId,
                currentSecondaryId = current.secondaryPaletteColorId,
                paletteIds = paletteIds,
                fallbackPrimaryId = firstPaletteId,
            )
            if (needsManualRemap || colorReviewNeedsProposal(
                    currentPrimaryId = current.primaryPaletteColorId,
                    currentSecondaryId = current.secondaryPaletteColorId,
                    targetPrimaryId = pair.primaryId,
                    targetSecondaryId = pair.secondaryId,
                    paletteIds = paletteIds,
                )
            ) {
                candidates += ColorReviewCandidate(
                    item = item,
                    proposedMetrics = proposedMetrics,
                    editablePrimaryColorId = pair.primaryId,
                    editableSecondaryColorId = pair.secondaryId,
                    hasConcreteTarget = colorReviewHasConcreteTarget(current, proposedMetrics, paletteIds),
                )
            }
            processedCount += 1
        }
        isScanning = false
    }

    pickerTarget?.let { target ->
        ColorReviewPalettePickerDialog(
            title = stringResource(
                if (target.role == ColorReviewColorRole.Primary) R.string.primary_color else R.string.secondary_color,
            ),
            colors = changeSet.afterPalette,
            allowNoColor = target.role == ColorReviewColorRole.Secondary,
            onDismiss = { pickerTarget = null },
            onColorSelected = { selected ->
                val index = candidates.indexOfFirst { candidate -> candidate.item.id == target.itemId }
                if (index >= 0) {
                    val candidate = candidates[index]
                    candidates[index] = when (target.role) {
                        ColorReviewColorRole.Primary -> candidate.copy(editablePrimaryColorId = selected?.id)
                        ColorReviewColorRole.Secondary -> candidate.copy(editableSecondaryColorId = selected?.id)
                    }
                }
                pickerTarget = null
            },
        )
    }

    val closedIds = acceptedIds.toSet() + discardedIds
    val unresolvedCount = candidates.count { candidate -> candidate.item.id !in closedIds }
    val acceptableCount = candidates.count { candidate -> candidate.item.id !in closedIds && candidate.hasConcreteTarget }
    val canFinish = !isScanning && unresolvedCount == 0
    fun commitItems(): List<ClothingItem> = candidates
        .filter { candidate ->
            colorReviewIncludedInCommit(
                accepted = candidate.item.id in acceptedIds,
                discarded = candidate.item.id in discardedIds,
                hasConcreteTarget = candidate.hasConcreteTarget,
            )
        }
        .map { candidate -> candidate.toUpdatedItem(changeSet.afterPalette) }
    val autoCommit = shouldAutoCommit(
        candidateIds = candidates.mapTo(mutableSetOf()) { candidate -> candidate.item.id },
        closedIds = closedIds + hiddenIds,
        isScanning = isScanning,
        committed = committed,
        pickerOpen = pickerTarget != null,
        discardDialogOpen = discardDialogOpen,
    )
    LaunchedEffect(autoCommit) {
        if (autoCommit) {
            committed = true
            onCommit(changeSet, commitItems())
        }
    }
    val totalCount = reviewItems.size.coerceAtLeast(1)
    val progress = processedCount.toFloat() / totalCount.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = if (isScanning) progress else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "colorReviewProgress",
    )

    BackHandler { onRequestDiscard() }

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
                ReviewProgressHeader(
                    title = stringResource(R.string.color_review_batch_progress),
                    countText = stringResource(R.string.color_review_scanning_count, processedCount, reviewItems.size),
                    progress = animatedProgress,
                )
            }

            if (!isScanning && candidates.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val hasEligibleItems = reviewItems.isNotEmpty()
                    ReviewEmptyCard(
                        title = stringResource(
                            if (hasEligibleItems) R.string.color_review_empty_title else R.string.color_review_no_eligible_title,
                        ),
                        body = stringResource(
                            if (hasEligibleItems) R.string.color_review_empty_body else R.string.color_review_no_eligible_body,
                        ),
                    )
                }
            }

            items(
                items = candidates.filterNot { candidate -> candidate.item.id in hiddenIds },
                key = { candidate -> candidate.item.id },
            ) { candidate ->
                val accepted = candidate.item.id in acceptedIds
                val discarded = candidate.item.id in discardedIds
                ReviewAcceptedCard(
                    resolved = accepted || discarded,
                    onGone = { hiddenIds.add(candidate.item.id) },
                ) {
                    ColorReviewCandidateCard(
                        candidate = candidate,
                        palette = changeSet.afterPalette,
                        accepted = accepted,
                        discarded = discarded,
                        onPrimaryClick = {
                            if (!accepted && !discarded) pickerTarget = ColorReviewPickerTarget(candidate.item.id, ColorReviewColorRole.Primary)
                        },
                        onSecondaryClick = {
                            if (!accepted && !discarded) pickerTarget = ColorReviewPickerTarget(candidate.item.id, ColorReviewColorRole.Secondary)
                        },
                        onAccept = {
                            if (!accepted && !discarded) acceptedIds.add(candidate.item.id)
                        },
                        onDiscard = {
                            if (!accepted && !discarded) discardedIds.add(candidate.item.id)
                        },
                    )
                }
            }

            if (isScanning || unresolvedCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReviewHelperRow(
                        isScanning = isScanning,
                        text = if (isScanning) {
                            stringResource(R.string.color_review_checking_helper)
                        } else {
                            stringResource(R.string.color_review_complete_all_helper, unresolvedCount)
                        },
                    )
                }
            }
        }

        // Accept all counts open cards with a concrete target; manual-choice proposals stay out and remain visible.
        ReviewBottomBar(
            label = when {
                acceptableCount > 0 -> stringResource(R.string.color_review_accept_all, acceptableCount)
                acceptedIds.isNotEmpty() -> stringResource(R.string.color_review_save_accepted_mappings, acceptedIds.size)
                else -> stringResource(R.string.done)
            },
            enabled = if (acceptableCount > 0) !isScanning else canFinish,
            onClick = {
                // Same once-only flag as the auto-commit: a Done tapped in the frame the auto-commit fires must not commit twice.
                if (!committed) {
                    committed = true
                    onCommit(changeSet, commitItems())
                }
            },
        )
    }
}

@Composable
private fun ColorReviewCandidateCard(
    candidate: ColorReviewCandidate,
    palette: List<MainColor>,
    accepted: Boolean,
    discarded: Boolean,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
) {
    val primaryColor = palette.colorForId(candidate.editablePrimaryColorId)
    val secondaryColor = palette.colorForId(candidate.editableSecondaryColorId)
    val resolved = accepted || discarded

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            ColorReviewPhoto(candidate.item)
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ColorMappingStrip(
                    currentMetrics = candidate.item.colorMetrics,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    onPrimaryClick = onPrimaryClick,
                    onSecondaryClick = onSecondaryClick,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAccept,
                        enabled = !resolved,
                        contentPadding = ReviewCardActionPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(
                            stringResource(if (accepted) R.string.tag_review_accepted else R.string.accept),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = onDiscard,
                        enabled = !resolved,
                        contentPadding = ReviewCardActionPadding,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(
                            stringResource(R.string.review_card_discard),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorReviewPhoto(item: ClothingItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf { it.isNotBlank() }
        if (photoUri != null) {
            BoundedGarmentImage(
                photoUri = photoUri,
                thumbnailMaxEdgePx = COLOR_REVIEW_THUMBNAIL_MAX_EDGE_PX,
                purpose = ImagePurpose.ColorReview,
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

@Composable
private fun ColorMappingStrip(
    currentMetrics: ClothingColorMetrics,
    primaryColor: MainColor?,
    secondaryColor: MainColor?,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlappingColorDots(
                primaryHex = currentMetrics.primaryPaletteColorHex ?: currentMetrics.primaryRawValue,
                secondaryHex = currentMetrics.secondaryPaletteColorHex ?: currentMetrics.secondaryRawValue,
            )
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            OverlappingColorDots(
                primaryHex = primaryColor?.hex,
                secondaryHex = secondaryColor?.hex,
                primaryDescription = stringResource(
                    R.string.color_review_chip_description,
                    stringResource(R.string.primary_color),
                    primaryColor?.name ?: stringResource(R.string.no_color),
                ),
                secondaryDescription = stringResource(
                    R.string.color_review_chip_description,
                    stringResource(R.string.secondary_color),
                    secondaryColor?.name ?: stringResource(R.string.no_color),
                ),
                onPrimaryClick = onPrimaryClick,
                onSecondaryClick = onSecondaryClick,
            )
        }
    }
}

@Composable
private fun OverlappingColorDots(
    primaryHex: String?,
    secondaryHex: String?,
    primaryDescription: String? = null,
    secondaryDescription: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.width(48.dp)) {
        ReviewColorDot(
            hex = primaryHex,
            contentDescription = primaryDescription,
            onClick = onPrimaryClick,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        ReviewColorDot(
            hex = secondaryHex,
            contentDescription = secondaryDescription,
            onClick = onSecondaryClick,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ReviewColorDot(
    hex: String?,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val color = hex.toComposeColor()
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(
        modifier = clickableModifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}


@Composable
private fun ColorReviewPalettePickerDialog(
    title: String,
    colors: List<MainColor>,
    allowNoColor: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (MainColor?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Scrolls like the tag picker: the last colour must stay reachable on a small screen.
            ReviewPickerList(optionCount = colors.size, verticalSpacing = 8.dp) {
                if (allowNoColor) {
                    ColorPickerRow(color = null, label = stringResource(R.string.no_secondary_color), onClick = { onColorSelected(null) })
                }
                colors.sortedAlphabetically(label = MainColor::name).forEach { color ->
                    ColorPickerRow(color = color, label = color.name, onClick = { onColorSelected(color) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ColorPickerRow(
    color: MainColor?,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color?.hex.toComposeColor() ?: MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class ColorReviewCandidate(
    val item: ClothingItem,
    val proposedMetrics: ClothingColorMetrics,
    val editablePrimaryColorId: String?,
    val editableSecondaryColorId: String?,
    // False when a referenced colour has no analyzer target: needs a manual choice and is not part of Accept all.
    val hasConcreteTarget: Boolean,
)

private enum class ColorReviewColorRole { Primary, Secondary }

private data class ColorReviewPickerTarget(
    val itemId: String,
    val role: ColorReviewColorRole,
)

private fun ColorReviewCandidate.toUpdatedItem(palette: List<MainColor>): ClothingItem {
    val primary = palette.colorForId(editablePrimaryColorId)
    val secondary = palette.colorForId(editableSecondaryColorId)
    return item.copy(
        colorMetrics = ClothingColorMetrics(
            primaryRawValue = primary?.hex,
            primaryDisplayLabel = primary?.hex?.let(ColorLabelResolver::fromRawValue),
            primaryPaletteColorId = primary?.id,
            primaryPaletteColorName = primary?.name,
            primaryPaletteColorHex = primary?.hex,
            secondaryRawValue = secondary?.hex,
            secondaryDisplayLabel = secondary?.hex?.let(ColorLabelResolver::fromRawValue),
            secondaryPaletteColorId = secondary?.id,
            secondaryPaletteColorName = secondary?.name,
            secondaryPaletteColorHex = secondary?.hex,
        ),
        updatedAtEpochMillis = System.currentTimeMillis(),
    )
}

/**
 * Discard = undo for a saved palette change. Added colours are removed, edited and deleted ones are written back as they
 * were. A restore-defaults review saves nothing before its commit, so there is nothing to undo. Garments are not touched:
 * a deleted colour is only taken off garments by the commit or the prompt's decline, never by a discard.
 */
internal fun colorReviewUndoPlan(changeSet: ColorPaletteChangeSet): ReviewUndoPlan {
    val touchedId = changeSet.touchedColorId
    if (changeSet.requiresCommitOnSave || touchedId == null) return ReviewUndoPlan()
    val before = changeSet.beforePalette.firstOrNull { color -> color.id == touchedId }
    val after = changeSet.afterPalette.firstOrNull { color -> color.id == touchedId }
    return when (changeSet.operation) {
        ColorPaletteOperation.Added -> ReviewUndoPlan(removeColors = listOfNotNull(after))
        ColorPaletteOperation.Edited,
        ColorPaletteOperation.Deleted -> ReviewUndoPlan(restoreColors = listOfNotNull(before))
        ColorPaletteOperation.RestoredDefault -> ReviewUndoPlan()
    }
}

/** A discarded card is never saved. Otherwise a card is saved when it was accepted or has a concrete target (Accept all). */
internal fun colorReviewIncludedInCommit(accepted: Boolean, discarded: Boolean, hasConcreteTarget: Boolean): Boolean =
    !discarded && (accepted || hasConcreteTarget)

/**
 * Garments outside [excludeIds] that still reference a removed palette colour, with those slots cleared. The palette rows go
 * away with the commit, so a garment the commit leaves out (a discarded card, or a garment with no change) would keep a
 * dangling id unless it is cleared here too.
 */
internal fun garmentsWithRemovedColors(
    items: List<ClothingItem>,
    removedIds: Collection<String>,
    excludeIds: Set<String>,
): List<ClothingItem> {
    if (removedIds.isEmpty()) return emptyList()
    return items
        .filter { item -> item.id !in excludeIds && removedIds.any { colorId -> item.colorMetrics.referencesColorId(colorId) } }
        .map { item -> removedIds.fold(item) { garment, colorId -> garment.withoutPaletteColor(colorId) } }
}

private fun ClothingColorMetrics.referencesColorId(colorId: String): Boolean =
    primaryPaletteColorId == colorId || secondaryPaletteColorId == colorId

// Removes a deleted palette colour from a garment that carried it; the other slot is kept.
internal fun ClothingItem.withoutPaletteColor(colorId: String): ClothingItem {
    val metrics = colorMetrics
    var cleared = metrics
    if (metrics.primaryPaletteColorId == colorId) {
        cleared = cleared.copy(
            primaryRawValue = null,
            primaryDisplayLabel = null,
            primaryPaletteColorId = null,
            primaryPaletteColorName = null,
            primaryPaletteColorHex = null,
        )
    }
    if (metrics.secondaryPaletteColorId == colorId) {
        cleared = cleared.copy(
            secondaryRawValue = null,
            secondaryDisplayLabel = null,
            secondaryPaletteColorId = null,
            secondaryPaletteColorName = null,
            secondaryPaletteColorHex = null,
        )
    }
    return copy(colorMetrics = cleared, updatedAtEpochMillis = System.currentTimeMillis())
}

internal fun List<PaletteColorMatch>.toColorMetrics(): ClothingColorMetrics {
    val primary = getOrNull(0)?.color
    val secondary = drop(1)
        .firstOrNull { match -> match.ratio >= COLOR_REVIEW_SECONDARY_MIN_RATIO && match.color.id != primary?.id }
        ?.color
    return ClothingColorMetrics(
        primaryRawValue = primary?.hex,
        primaryDisplayLabel = primary?.hex?.let(ColorLabelResolver::fromRawValue),
        primaryPaletteColorId = primary?.id,
        primaryPaletteColorName = primary?.name,
        primaryPaletteColorHex = primary?.hex,
        secondaryRawValue = secondary?.hex,
        secondaryDisplayLabel = secondary?.hex?.let(ColorLabelResolver::fromRawValue),
        secondaryPaletteColorId = secondary?.id,
        secondaryPaletteColorName = secondary?.name,
        secondaryPaletteColorHex = secondary?.hex,
    )
}

private fun ClothingItem.needsManualPaletteRemap(changeSet: ColorPaletteChangeSet): Boolean {
    if (!changeSet.requiresCommitOnSave) return false
    val afterPaletteById = changeSet.afterPalette.associateBy(MainColor::id)
    val current = colorMetrics
    return current.primaryPaletteColorId.requiresPaletteUpdate(
        paletteById = afterPaletteById,
        snapshotName = current.primaryPaletteColorName,
        snapshotHex = current.primaryPaletteColorHex,
        touchedColorId = changeSet.touchedColorId,
    ) || current.secondaryPaletteColorId.requiresPaletteUpdate(
        paletteById = afterPaletteById,
        snapshotName = current.secondaryPaletteColorName,
        snapshotHex = current.secondaryPaletteColorHex,
        touchedColorId = changeSet.touchedColorId,
    )
}

private fun String?.requiresPaletteUpdate(
    paletteById: Map<String, MainColor>,
    snapshotName: String?,
    snapshotHex: String?,
    touchedColorId: String?,
): Boolean {
    val id = this ?: return false
    val paletteColor = paletteById[id] ?: return true
    return id == touchedColorId || paletteColor.name != snapshotName || paletteColor.hex != snapshotHex
}

// Resolves the palette colour a garment should carry: the analyzer's proposal first, then its current colour, when either is still in the palette.
internal fun colorReviewConcreteTargetId(
    proposedId: String?,
    currentId: String?,
    paletteIds: Set<String>,
): String? = listOf(proposedId, currentId).firstOrNull { id -> id != null && id in paletteIds }

// A garment with no concrete colour keeps no colour, unless it had one; then the first palette colour is only a placeholder for a manual choice.
internal fun colorReviewTargetId(
    proposedId: String?,
    currentId: String?,
    paletteIds: Set<String>,
    fallbackId: String?,
): String? = colorReviewConcreteTargetId(proposedId, currentId, paletteIds)
    ?: fallbackId.takeIf { currentId != null }

internal data class ColorReviewPair(val primaryId: String?, val secondaryId: String?)

// Primary and secondary are never equal. The secondary is the analyzer's proposal, then the current one, but only while it differs from the resolved primary; otherwise it is dropped.
internal fun colorReviewPair(
    proposedPrimaryId: String?,
    currentPrimaryId: String?,
    proposedSecondaryId: String?,
    currentSecondaryId: String?,
    paletteIds: Set<String>,
    fallbackPrimaryId: String?,
): ColorReviewPair {
    val primaryId = colorReviewTargetId(
        proposedId = proposedPrimaryId,
        currentId = currentPrimaryId,
        paletteIds = paletteIds,
        fallbackId = fallbackPrimaryId,
    )
    val secondaryId = listOf(proposedSecondaryId, currentSecondaryId).firstOrNull { id ->
        id != null && id in paletteIds && id != primaryId
    }
    return ColorReviewPair(primaryId = primaryId, secondaryId = secondaryId)
}

// A garment is listed only when its resolved colour ids differ from the stored ones, or a stored id is no longer in the palette.
// Unchanged garments and weak or absent secondaries that resolve to the same ids produce no row.
internal fun colorReviewNeedsProposal(
    currentPrimaryId: String?,
    currentSecondaryId: String?,
    targetPrimaryId: String?,
    targetSecondaryId: String?,
    paletteIds: Set<String>,
): Boolean {
    val referencedIds = listOfNotNull(currentPrimaryId, currentSecondaryId)
    if (referencedIds.any { id -> id !in paletteIds }) return true
    return currentPrimaryId != targetPrimaryId || currentSecondaryId != targetSecondaryId
}

// Accept all takes only proposals where the primary the garment currently has resolves to a palette colour.
// A secondary never blocks: it either resolves to a palette colour or is dropped, and a dropped secondary is a valid outcome.
internal fun colorReviewHasConcreteTarget(
    current: ClothingColorMetrics,
    proposed: ClothingColorMetrics,
    paletteIds: Set<String>,
): Boolean {
    val primaryResolved = colorReviewConcreteTargetId(
        proposedId = proposed.primaryPaletteColorId,
        currentId = current.primaryPaletteColorId,
        paletteIds = paletteIds,
    ) != null
    return current.primaryPaletteColorId == null || primaryResolved
}

internal fun ClothingItem.isEligibleForColorReview(): Boolean = !isArchived && !photoUri.isNullOrBlank()

private fun List<MainColor>.colorForId(id: String?): MainColor? = firstOrNull { color -> color.id == id }

private fun String?.toComposeColor(): Color? {
    val normalized = this?.trim()?.removePrefix("#") ?: return null
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return Color(android.graphics.Color.parseColor("#${normalized.uppercase()}"))
}
