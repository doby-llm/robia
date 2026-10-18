package com.gusanitolabs.robia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import java.text.Collator

/** Value of a field row: labels sorted with [collator], joined with ", ", or [none] when nothing is selected. */
internal fun <T> fieldValueText(
    selected: List<T>,
    collator: Collator,
    none: String,
    label: (T) -> String,
): String = selected.map(label).sortedWith(collator).joinToString(", ").ifEmpty { none }

/** A field shows the AI marker when it has values and every one of them was suggested by the model. */
internal fun isAiSuggested(selectedIds: Collection<String>, modelOwnedIds: Collection<String>): Boolean {
    val owned = modelOwnedIds.toSet()
    return selectedIds.isNotEmpty() && selectedIds.all { id -> id in owned }
}

/** The Colours row shows the AI marker while either colour still holds a model suggestion. */
internal fun isColorAiSuggested(primaryGeneration: Long?, secondaryGeneration: Long?): Boolean =
    primaryGeneration != null || secondaryGeneration != null

/** Colours value: both names with roles, a lone primary as its name, a lone secondary as "None · name", or [none]. */
internal fun colorFieldValueText(primary: String?, secondary: String?, none: String): String =
    when {
        primary != null && secondary != null -> "$primary · $secondary"
        primary != null -> primary
        secondary != null -> "$none · $secondary"
        else -> none
    }

/** The sheet hint shows only when a selected tag is suggested and belongs to this sheet's own category. */
internal fun sheetShowsSuggestedHint(
    selectedIds: Collection<String>,
    suggestedIds: Set<String>,
    categoryTagIds: Collection<String>,
): Boolean = selectedIds.any { id -> id in suggestedIds && id in categoryTagIds }

/** Same selection rule the metadata chips always used: multi toggles the tag, single replaces the category's tags. */
internal fun List<String>.toggleTag(
    tag: GarmentTag,
    selected: Boolean,
    singleSelect: Boolean,
    categoryTagIds: List<String>,
): List<String> =
    if (selected) {
        this - tag.id
    } else if (singleSelect) {
        filterNot { selectedId -> selectedId in categoryTagIds } + tag.id
    } else {
        this + tag.id
    }

/** One row of the garment form and detail: lead icon tile, field name, value, then a chevron, [trailing] or nothing. */
@Composable
fun GarmentFieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    aiSuggested: Boolean,
    onClick: (() -> Unit)?,
    swatches: List<Color> = emptyList(),
    trailing: (@Composable () -> Unit)? = null,
) {
    val suggestedDescription = stringResource(R.string.field_suggested_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(swatch),
                )
            }
            if (aiSuggested) {
                Text(
                    text = "✦",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { contentDescription = suggestedDescription },
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Bottom sheet listing one category's tags alphabetically. Single-select adds a "None" row first; the sheet applies
 * each tap straight away, the same as the chips did. Tapping the selected radio keeps it selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    title: String,
    tags: List<GarmentTag>,
    selectedIds: List<String>,
    multi: Boolean,
    suggestedIds: Set<String>,
    onSelectedIdsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val none = stringResource(R.string.none)
    val categoryTagIds = remember(tags) { tags.map(GarmentTag::id) }
    val sortedTags = remember(tags) { tags.sortedAlphabetically(label = { tag -> tag.localizedTagLabel(context) }) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (sortedTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tags_in_category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (sheetShowsSuggestedHint(selectedIds, suggestedIds, categoryTagIds)) {
                    Text(
                        text = stringResource(R.string.field_suggested_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ReviewPickerList(optionCount = sortedTags.size + (if (multi) 0 else 1)) {
                    if (!multi) {
                        TagPickerRow(
                            label = none,
                            selected = selectedIds.none { id -> id in categoryTagIds },
                            multi = false,
                            suggested = false,
                            onClick = {
                                onSelectedIdsChange(selectedIds.filterNot { id -> id in categoryTagIds })
                            },
                        )
                    }
                    sortedTags.forEach { tag ->
                        val selected = tag.id in selectedIds
                        TagPickerRow(
                            label = tag.localizedTagLabel(context),
                            selected = selected,
                            multi = multi,
                            suggested = tag.id in suggestedIds,
                            onClick = {
                                if (multi || !selected) {
                                    onSelectedIdsChange(
                                        selectedIds.toggleTag(
                                            tag = tag,
                                            selected = selected,
                                            singleSelect = !multi,
                                            categoryTagIds = categoryTagIds,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPickerRow(
    label: String,
    selected: Boolean,
    multi: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (multi) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (suggested) {
            Text(
                text = "✦",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
