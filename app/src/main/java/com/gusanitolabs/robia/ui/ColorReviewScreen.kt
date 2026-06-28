package com.gusanitolabs.robia.ui

import android.net.Uri
import android.widget.ImageView
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val COLOR_REVIEW_SECONDARY_MIN_RATIO = 0.05

internal data class ColorPaletteChangeSet(
    val beforePalette: List<MainColor>,
    val afterPalette: List<MainColor>,
    val touchedColorId: String?,
    val operation: ColorPaletteOperation,
    val id: String = UUID.randomUUID().toString(),
)

internal enum class ColorPaletteOperation { Added, Edited, Deleted }

@Composable
internal fun ColorReviewPromptDialog(
    onDismiss: () -> Unit,
    onReview: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_review_prompt_title)) },
        text = { Text(stringResource(R.string.color_review_prompt_body)) },
        confirmButton = {
            Button(onClick = onReview) {
                Text(stringResource(R.string.color_review_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
    onApplyChanges: (List<ClothingItem>) -> Unit,
    onDone: () -> Unit,
    onRequestDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val candidates = remember(changeSet.id) { mutableStateListOf<ColorReviewCandidate>() }
    var processedCount by remember(changeSet.id) { mutableIntStateOf(0) }
    var isScanning by remember(changeSet.id) { mutableStateOf(true) }
    var pickerTarget by remember(changeSet.id) { mutableStateOf<ColorReviewPickerTarget?>(null) }

    LaunchedEffect(changeSet.id, items) {
        candidates.clear()
        processedCount = 0
        isScanning = true
        items.forEach { item ->
            val imageUri = item.photoUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            if (imageUri != null && changeSet.afterPalette.isNotEmpty()) {
                val matches = runCatching {
                    withContext(Dispatchers.IO) {
                        ClothingImageStore.extractPaletteColorDiagnostics(context, imageUri, changeSet.afterPalette).matches
                    }
                }.getOrDefault(emptyList())
                val proposedMetrics = matches.toColorMetrics()
                if (item.isAffectedBy(changeSet, proposedMetrics)) {
                    candidates += ColorReviewCandidate(
                        item = item,
                        proposedMetrics = proposedMetrics,
                        editablePrimaryColorId = proposedMetrics.primaryPaletteColorId,
                        editableSecondaryColorId = proposedMetrics.secondaryPaletteColorId,
                    )
                }
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

    val unresolvedCount = candidates.count { candidate -> candidate.decision == null }
    val acceptedItems = candidates.filter { candidate -> candidate.decision == ColorReviewDecision.Accepted }
        .map { candidate -> candidate.toUpdatedItem(changeSet.afterPalette) }
    val canSave = !isScanning && unresolvedCount == 0
    val totalCount = items.size.coerceAtLeast(1)
    val progress = processedCount.toFloat() / totalCount.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = if (isScanning) progress else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "colorReviewProgress",
    )

    BackHandler { onRequestDiscard() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.color_review_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.color_review_scanning_count, processedCount, items.size)
                    } else {
                        stringResource(R.string.color_review_suggestions_count, candidates.size)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!isScanning && candidates.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ColorReviewEmptyCard(onDone = onDone)
            }
        }

        items(
            items = candidates.filter { candidate -> candidate.decision == null },
            key = { candidate -> candidate.item.id },
        ) { candidate ->
            ColorReviewCandidateCard(
                candidate = candidate,
                palette = changeSet.afterPalette,
                onPrimaryClick = {
                    pickerTarget = ColorReviewPickerTarget(candidate.item.id, ColorReviewColorRole.Primary)
                },
                onSecondaryClick = {
                    pickerTarget = ColorReviewPickerTarget(candidate.item.id, ColorReviewColorRole.Secondary)
                },
                onAccept = {
                    val index = candidates.indexOfFirst { it.item.id == candidate.item.id }
                    if (index >= 0) candidates[index] = candidates[index].copy(decision = ColorReviewDecision.Accepted)
                },
                onReject = {
                    val index = candidates.indexOfFirst { it.item.id == candidate.item.id }
                    if (index >= 0) candidates[index] = candidates[index].copy(decision = ColorReviewDecision.Rejected)
                },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isScanning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.color_review_checking_helper),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (unresolvedCount > 0) {
                    Text(
                        text = stringResource(R.string.color_review_complete_all_helper, unresolvedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (candidates.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.color_review_ready_helper, acceptedItems.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        onApplyChanges(acceptedItems)
                        onDone()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (candidates.isEmpty() && !isScanning) {
                            stringResource(R.string.done)
                        } else {
                            stringResource(R.string.color_review_save_updates)
                        },
                    )
                }
                TextButton(onClick = onRequestDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun ColorReviewEmptyCard(onDone: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.color_review_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.color_review_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDone) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun ColorReviewCandidateCard(
    candidate: ColorReviewCandidate,
    palette: List<MainColor>,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val primaryColor = palette.colorForId(candidate.editablePrimaryColorId)
    val secondaryColor = palette.colorForId(candidate.editableSecondaryColorId)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorReviewPhoto(candidate.item)
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = candidate.item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.color_review_suggested_colors),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewColorChip(
                        role = stringResource(R.string.primary_color),
                        color = primaryColor,
                        onClick = onPrimaryClick,
                        modifier = Modifier.weight(1f),
                    )
                    ReviewColorChip(
                        role = stringResource(R.string.secondary_color),
                        color = secondaryColor,
                        onClick = onSecondaryClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.reject),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.accept),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorReviewPhoto(item: ClothingItem) {
    val swatchColor = item.colorMetrics.primaryPaletteColorHex.toComposeColor() ?: Color(0xFFDADADA)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        swatchColor.copy(alpha = 0.26f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf { it.isNotBlank() }
        if (photoUri != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { imageView ->
                    if (imageView.tag != photoUri) {
                        imageView.tag = photoUri
                        imageView.setImageURI(Uri.parse(photoUri))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Style, contentDescription = null, tint = swatchColor, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun ReviewColorChip(
    role: String,
    color: MainColor?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = color?.name ?: stringResource(R.string.no_color)
    val chipDescription = stringResource(R.string.color_review_chip_description, role, label)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = chipDescription }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color?.hex.toComposeColor() ?: MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (color == null) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowNoColor) {
                    ColorPickerRow(color = null, label = stringResource(R.string.no_secondary_color), onClick = { onColorSelected(null) })
                }
                colors.forEach { color ->
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
    val decision: ColorReviewDecision? = null,
)

private enum class ColorReviewDecision { Accepted, Rejected }
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

private fun ClothingItem.isAffectedBy(
    changeSet: ColorPaletteChangeSet,
    proposedMetrics: ClothingColorMetrics,
): Boolean {
    val current = colorMetrics
    val touched = changeSet.touchedColorId
    val referencesTouchedColor = touched != null &&
        (current.primaryPaletteColorId == touched || current.secondaryPaletteColorId == touched)
    return referencesTouchedColor || current.colorReviewSignature() != proposedMetrics.colorReviewSignature()
}

private fun ClothingColorMetrics.colorReviewSignature(): List<String?> = listOf(
    primaryPaletteColorId,
    primaryPaletteColorName,
    primaryPaletteColorHex,
    secondaryPaletteColorId,
    secondaryPaletteColorName,
    secondaryPaletteColorHex,
)

private fun List<MainColor>.colorForId(id: String?): MainColor? = firstOrNull { color -> color.id == id }

private fun String?.toComposeColor(): Color? {
    val normalized = this?.trim()?.removePrefix("#") ?: return null
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return Color(android.graphics.Color.parseColor("#${normalized.uppercase()}"))
}
