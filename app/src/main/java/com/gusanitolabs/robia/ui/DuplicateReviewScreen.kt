package com.gusanitolabs.robia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.RobiaLightColorScheme
import com.gusanitolabs.robia.core.designsystem.extras
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.suggestions.DuplicateGroup
import com.gusanitolabs.robia.suggestions.DuplicateTier

private const val DUPLICATE_PHOTO_EDGE_PX = 256

/**
 * REQ-FB-27 (R3-DEC-062): the review ends when the user resolves the last group. Only a screen that had groups and was
 * emptied after the user acted ends; a screen opened empty, or emptied by a rescan under someone who only looks, does not.
 */
internal fun shouldFinishDuplicateReview(wasNonEmpty: Boolean, nowEmpty: Boolean, userActed: Boolean): Boolean =
    wasNonEmpty && nowEmpty && userActed

/**
 * REQ-DU-03: one card per group. Every garment starts as Keep, so nothing is deleted until it is marked and the delete is
 * confirmed. "Not duplicates" drops the group and keeps its pairs out of later scans, also after a restart (REQ-DU-02).
 * When the user's last action leaves no group, [onAllResolved] fires once with the number of garments deleted here.
 */
@Composable
internal fun DuplicateReviewScreen(
    innerPadding: PaddingValues,
    groups: List<DuplicateGroup>,
    items: List<ClothingItem>,
    onNotDuplicates: (DuplicateGroup) -> Unit,
    onDeleteItems: (List<String>) -> Unit,
    onAllResolved: (removedCount: Int) -> Unit,
) {
    val itemsById = remember(items) { items.associateBy(ClothingItem::id) }
    var markedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removedCount by remember { mutableStateOf(0) }
    var userActed by remember { mutableStateOf(false) }
    var sawGroups by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val listedIds = groups.flatMap(DuplicateGroup::garmentIds).toSet()
    val deleteIds = markedIds.filter { id -> id in listedIds }
    val groupsEmpty = groups.isEmpty()
    val currentOnAllResolved by rememberUpdatedState(onAllResolved)
    // Keyed on emptiness: the prune after a Delete arrives later from Room, so the finish waits for the empty groups.
    LaunchedEffect(groupsEmpty) {
        if (!groupsEmpty) sawGroups = true
        if (!finished && shouldFinishDuplicateReview(wasNonEmpty = sawGroups, nowEmpty = groupsEmpty, userActed = userActed)) {
            finished = true
            currentOnAllResolved(removedCount)
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.duplicate_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(groups, key = { _, group -> group.garmentIds.joinToString(",") }) { index, group ->
                    DuplicateGroupCard(
                        number = index + 1,
                        group = group,
                        itemsById = itemsById,
                        markedIds = markedIds,
                        onToggle = { id -> markedIds = if (id in markedIds) markedIds - id else markedIds + id },
                        onNotDuplicates = {
                            userActed = true
                            onNotDuplicates(group)
                            markedIds = markedIds - group.garmentIds.toSet()
                        },
                    )
                }
            }
            ReviewBottomBar(
                label = pluralStringResource(R.plurals.duplicate_delete_selected, deleteIds.size, deleteIds.size),
                enabled = deleteIds.isNotEmpty(),
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.duplicate_delete_title)) },
            text = { Text(stringResource(R.string.duplicate_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        removedCount += deleteIds.size
                        userActed = true
                        onDeleteItems(deleteIds)
                        markedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(pluralStringResource(R.plurals.duplicate_delete_selected, deleteIds.size, deleteIds.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    number: Int,
    group: DuplicateGroup,
    itemsById: Map<String, ClothingItem>,
    markedIds: Set<String>,
    onToggle: (String) -> Unit,
    onNotDuplicates: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.duplicate_group_title, number, group.garmentIds.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    when (group.tier) {
                        DuplicateTier.SamePhoto -> R.string.duplicate_tier_same_photo
                        DuplicateTier.VerySimilar -> R.string.duplicate_tier_very_similar
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.garmentIds.forEach { id ->
                val item = itemsById[id] ?: return@forEach
                DuplicateGarmentRow(
                    item = item,
                    deleting = id in markedIds,
                    onToggle = { onToggle(id) },
                )
            }
            TextButton(onClick = onNotDuplicates, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.duplicate_not_duplicates))
            }
        }
    }
}

@Composable
private fun DuplicateGarmentRow(item: ClothingItem, deleting: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val collator = remember { browseCollator() }
    val line = duplicateRowLine(
        name = item.name,
        tags = item.tags.map { tag -> UiTag(tag.id, tag.categoryId, tag.localizedTagLabel(context)) },
        colourLabel = item.colorMetrics.primaryDisplayLabel
            ?.takeUnless { label -> label == DisplayColorLabel.Unknown }
            ?.let { label -> stringResource(label.colorStringRes()) },
        collator = collator,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DuplicatePhoto(item = item, modifier = Modifier.size(72.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            line?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !deleting,
                    onClick = { if (deleting) onToggle() },
                    label = { Text(stringResource(R.string.duplicate_keep)) },
                )
                FilterChip(
                    selected = deleting,
                    onClick = { if (!deleting) onToggle() },
                    label = { Text(stringResource(R.string.duplicate_delete)) },
                )
            }
        }
    }
}

/** The colour names the add flow uses (AddEditClothingScreen): the same strings, so a colour reads the same here. */
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

/** A garment photo for the duplicate screens; a garment without a photo shows the Browse placeholder icon. */
@Composable
internal fun DuplicatePhoto(item: ClothingItem, modifier: Modifier = Modifier) {
    val extras = MaterialTheme.extras
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            // Dark gets the light photo well so a black garment stays visible; Light keeps its grey (R3-DEC-045).
            .background(if (extras.isDark) extras.photoWellTop else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf(String::isNotBlank)
        if (photoUri != null) {
            BoundedGarmentImage(
                photoUri = photoUri,
                modifier = Modifier.fillMaxSize(),
                thumbnailMaxEdgePx = DUPLICATE_PHOTO_EDGE_PX,
                sourceRevision = "${item.id}:$photoUri",
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                // The well is light in both themes, so its ink comes from the light scheme (R3-DEC-045).
                tint = RobiaLightColorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
