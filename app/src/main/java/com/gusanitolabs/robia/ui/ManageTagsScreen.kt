package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoModelOutputTags
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import java.util.Locale
import java.util.UUID

private const val DefaultCustomColorHex = "#A8644E"

private data class NeutralColorPreset(
    @StringRes val nameRes: Int,
    val hex: String,
)

private val NeutralColorPresets = listOf(
    NeutralColorPreset(R.string.neutral_color_black, "#1F1F1F"),
    NeutralColorPreset(R.string.neutral_color_charcoal, "#3F3F3F"),
    NeutralColorPreset(R.string.neutral_color_gray, "#777777"),
    NeutralColorPreset(R.string.neutral_color_warm_neutral, "#EFE5D9"),
    NeutralColorPreset(R.string.neutral_color_white, "#F8F9FA"),
)

private data class TagEditorState(
    val categoryId: String,
    val existingTag: GarmentTag? = null,
)

private data class ColorEditorState(
    val existingColor: MainColor? = null,
)

private sealed interface RestoreDefaultTarget {
    data object MainColors : RestoreDefaultTarget
    data class Tags(val category: TagCategory) : RestoreDefaultTarget
}


@Composable
fun ManageTagsScreen(
    innerPadding: PaddingValues,
    categories: List<TagCategory>,
    tags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onSaveTag: (GarmentTag) -> Unit,
    onDeleteTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
    onRestoreDefaultTags: (TagCategory) -> Unit,
    onRestoreDefaultMainColors: () -> Unit,
) {
    var editorState by remember { mutableStateOf<TagEditorState?>(null) }
    var colorEditorState by remember { mutableStateOf<ColorEditorState?>(null) }
    var pendingDeleteColor by remember { mutableStateOf<MainColor?>(null) }
    var pendingDeleteTag by remember { mutableStateOf<GarmentTag?>(null) }
    var pendingModelTagRename by remember { mutableStateOf<GarmentTag?>(null) }
    var pendingRestoreDefault by remember { mutableStateOf<RestoreDefaultTarget?>(null) }
    val visibleCategories = remember(categories) { categories.filterNot { category -> category.id == "care" } }
    val visibleTags = remember(tags) { tags.filterNot { tag -> tag.categoryId == "care" } }
    val tagsByCategory = remember(visibleTags) { visibleTags.groupBy(GarmentTag::categoryId) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.manage_tags_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.manage_tags_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "main-color-palette") {
            MainColorPaletteCard(
                colors = mainColors,
                onAddColor = { colorEditorState = ColorEditorState() },
                onEditColor = { color -> colorEditorState = ColorEditorState(existingColor = color) },
                onDeleteColor = { color -> pendingDeleteColor = color },
                onRestoreDefault = { pendingRestoreDefault = RestoreDefaultTarget.MainColors },
            )
        }

        visibleCategories.forEach { category ->
            item(key = category.id) {
                val categoryTags = tagsByCategory[category.id].orEmpty()
                TagCategoryCard(
                    category = category,
                    tags = categoryTags,
                    onAddTag = { editorState = TagEditorState(categoryId = category.id) },
                    onEditTag = { tag -> editorState = TagEditorState(categoryId = category.id, existingTag = tag) },
                    onDeleteTag = { tag ->
                        if (tag.id in AdditionalInfoModelOutputTags.ids) {
                            pendingDeleteTag = tag
                        } else {
                            onDeleteTag(tag)
                        }
                    },
                    onRestoreDefault = if (category.id in RestorableTagCategoryIds) {
                        { pendingRestoreDefault = RestoreDefaultTarget.Tags(category) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    editorState?.let { state ->
        TagEditorDialog(
            state = state,
            categoryTags = tagsByCategory[state.categoryId].orEmpty(),
            onDismiss = { editorState = null },
            onSave = { tag ->
                val existing = state.existingTag
                if (existing != null && existing.id in AdditionalInfoModelOutputTags.ids && tag.name != existing.name) {
                    pendingModelTagRename = tag
                } else {
                    onSaveTag(tag)
                    editorState = null
                }
            },
        )
    }

    pendingModelTagRename?.let { tag ->
        val originalTag = editorState?.existingTag ?: tag
        AiDetectedTagWarningDialog(
            title = stringResource(R.string.rename_ai_tag_title),
            body = stringResource(R.string.rename_ai_tag_body, originalTag.localizedName()),
            confirmText = stringResource(R.string.rename_anyway),
            destructive = false,
            onDismiss = { pendingModelTagRename = null },
            onConfirm = {
                onSaveTag(tag)
                pendingModelTagRename = null
                editorState = null
            },
        )
    }

    colorEditorState?.let { state ->
        ColorEditorDialog(
            state = state,
            colors = mainColors,
            onDismiss = { colorEditorState = null },
            onSave = { color ->
                onSaveMainColor(color)
                colorEditorState = null
            },
        )
    }

    pendingDeleteColor?.let { color ->
        DeleteColorConfirmationDialog(
            color = color,
            onDismiss = { pendingDeleteColor = null },
            onConfirm = {
                onDeleteMainColor(color)
                pendingDeleteColor = null
            },
        )
    }

    pendingDeleteTag?.let { tag ->
        AiDetectedTagWarningDialog(
            title = stringResource(R.string.delete_ai_tag_title),
            body = stringResource(R.string.delete_ai_tag_body, tag.localizedName()),
            confirmText = stringResource(R.string.delete_anyway),
            destructive = true,
            onDismiss = { pendingDeleteTag = null },
            onConfirm = {
                onDeleteTag(tag)
                pendingDeleteTag = null
            },
        )
    }

    pendingRestoreDefault?.let { target ->
        RestoreDefaultConfirmationDialog(
            target = target,
            onDismiss = { pendingRestoreDefault = null },
            onConfirm = {
                when (target) {
                    RestoreDefaultTarget.MainColors -> onRestoreDefaultMainColors()
                    is RestoreDefaultTarget.Tags -> onRestoreDefaultTags(target.category)
                }
                pendingRestoreDefault = null
            },
        )
    }
}

@Composable
private fun TagCategoryCard(
    category: TagCategory,
    tags: List<GarmentTag>,
    onAddTag: () -> Unit,
    onEditTag: (GarmentTag) -> Unit,
    onDeleteTag: (GarmentTag) -> Unit,
    onRestoreDefault: (() -> Unit)?,
) {
    val addDescription = stringResource(R.string.content_add_tag)
    val allowsNewTags = category.id != "season"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.localizedName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.tag_count, tags.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allowsNewTags) {
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = addDescription },
                        onClick = onAddTag,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                }
                onRestoreDefault?.let { restoreDefault ->
                    TextButton(onClick = restoreDefault) {
                        Text(stringResource(R.string.restore_default_button))
                    }
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tags_in_category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        key(tag.id) {
                            TagListRow(
                                tag = tag,
                                canEdit = category.id != "season" || tag.id in AdditionalInfoModelOutputTags.ids,
                                onEdit = { onEditTag(tag) },
                                onDelete = { onDeleteTag(tag) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagListRow(
    tag: GarmentTag,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editDescription = stringResource(R.string.content_edit_tag)
    val deleteDescription = stringResource(R.string.content_delete_tag)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tag.dotColor()),
            )
            Text(
                text = tag.localizedName(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (canEdit) {
                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = editDescription },
                    onClick = onEdit,
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = deleteDescription },
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MainColorPaletteCard(
    colors: List<MainColor>,
    onAddColor: () -> Unit,
    onEditColor: (MainColor) -> Unit,
    onDeleteColor: (MainColor) -> Unit,
    onRestoreDefault: () -> Unit,
) {
    val addDescription = stringResource(R.string.content_add_color)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.main_color_palette_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.color_count, colors.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    modifier = Modifier.semantics { contentDescription = addDescription },
                    onClick = onAddColor,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
                TextButton(onClick = onRestoreDefault) {
                    Text(stringResource(R.string.restore_default_button))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { color ->
                    key(color.id) {
                        ColorListRow(
                            color = color,
                            canEdit = true,
                            canDelete = colors.size > 1,
                            onEdit = { onEditColor(color) },
                            onDelete = { onDeleteColor(color) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorListRow(
    color: MainColor,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editDescription = stringResource(R.string.content_edit_color)
    val deleteDescription = stringResource(R.string.content_delete_color)
    val swatchDescription = stringResource(R.string.content_color_swatch, color.name)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color.hex.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .semantics { contentDescription = swatchDescription },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = color.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = color.hex,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = editDescription },
                enabled = canEdit,
                onClick = onEdit,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = if (canEdit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (canDelete) {
                IconButton(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = deleteDescription },
                    onClick = onDelete,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagEditorDialog(
    state: TagEditorState,
    categoryTags: List<GarmentTag>,
    onDismiss: () -> Unit,
    onSave: (GarmentTag) -> Unit,
) {
    var name by remember(state) { mutableStateOf(state.existingTag?.name.orEmpty()) }
    val trimmedName = name.trim()
    val editingTagId = state.existingTag?.id
    val hasDuplicateName = trimmedName.isNotEmpty() && categoryTags.any { tag ->
        tag.id != editingTagId && tag.name.toNormalizedName() == trimmedName.toNormalizedName()
    }
    val canSave = trimmedName.isNotEmpty() && !hasDuplicateName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (state.existingTag == null) R.string.add_tag_title else R.string.edit_tag_title,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = hasDuplicateName,
                label = { Text(stringResource(R.string.tag_name_label)) },
                supportingText = {
                    if (hasDuplicateName) {
                        Text(stringResource(R.string.tag_name_duplicate))
                    }
                },
            )
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val existing = state.existingTag
                    onSave(
                        GarmentTag(
                            id = existing?.id ?: customId(prefix = state.categoryId, name = trimmedName),
                            categoryId = state.categoryId,
                            name = trimmedName,
                            sortOrder = existing?.sortOrder ?: categoryTags.nextTagSortOrder(),
                            isSystem = existing?.isSystem ?: false,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
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
private fun AiDetectedTagWarningDialog(
    title: String,
    body: String,
    confirmText: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (destructive) {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = confirmText,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Button(onClick = onConfirm) {
                    Text(confirmText)
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
private fun ColorEditorDialog(
    state: ColorEditorState,
    colors: List<MainColor>,
    onDismiss: () -> Unit,
    onSave: (MainColor) -> Unit,
) {
    val editingColorId = state.existingColor?.id
    val siblingColors = remember(colors, editingColorId) { colors.filterNot { color -> color.id == editingColorId } }
    val unavailableHexes = remember(siblingColors) { siblingColors.mapNotNull { color -> color.hex.toNormalizedHex() }.toSet() }
    val initialHex = state.existingColor?.hex?.toNormalizedHex() ?: DefaultCustomColorHex
    val initialColor = initialHex.toComposeColor() ?: Color(android.graphics.Color.parseColor(DefaultCustomColorHex))
    var name by remember(state) { mutableStateOf(state.existingColor?.name.orEmpty()) }
    var selectedHex by remember(state) { mutableStateOf(initialHex) }
    var userHasPickedColor by remember(state) { mutableStateOf(false) }
    val trimmedName = name.trim()
    val selectedColorHex = selectedHex.toNormalizedHex()
    val hasDuplicateName = trimmedName.isNotEmpty() && siblingColors.any { color ->
        color.name.toNormalizedName() == trimmedName.toNormalizedName()
    }
    val hasDuplicateHex = selectedColorHex != null && selectedColorHex in unavailableHexes
    val canSave = trimmedName.isNotEmpty() && selectedColorHex != null && !hasDuplicateName && !hasDuplicateHex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (state.existingColor == null) R.string.add_color_title else R.string.edit_color_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = hasDuplicateName,
                    label = { Text(stringResource(R.string.color_name_label)) },
                    supportingText = {
                        if (hasDuplicateName) {
                            Text(stringResource(R.string.color_name_duplicate))
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.color_palette_picker_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                key(editingColorId, initialHex) {
                    val controller = rememberColorPickerController()
                    LaunchedEffect(editingColorId, initialHex) {
                        selectedHex = initialHex
                        userHasPickedColor = false
                        controller.selectByColor(initialColor, fromUser = false)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HsvColorPicker(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            controller = controller,
                            initialColor = initialColor,
                            onColorChanged = { envelope ->
                                if (envelope.fromUser) {
                                    userHasPickedColor = true
                                }
                                val changedHex = envelope.hexCode.toNormalizedRgbHex()
                                if (changedHex != null && (userHasPickedColor || changedHex == initialHex)) {
                                    selectedHex = changedHex
                                }
                            },
                        )
                        NeutralColorPresetRow(
                            selectedHex = selectedColorHex,
                            onSelect = { preset ->
                                val presetColor = preset.hex.toComposeColor() ?: return@NeutralColorPresetRow
                                userHasPickedColor = true
                                selectedHex = preset.hex
                                controller.selectByColor(presetColor, fromUser = true)
                            },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val swatchDescription = stringResource(R.string.content_selected_color_swatch, selectedColorHex.orEmpty())
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(selectedColorHex?.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .semantics { contentDescription = swatchDescription },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.selected_color_hex_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedColorHex.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (hasDuplicateHex) {
                    Text(
                        text = stringResource(R.string.color_hex_duplicate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val existing = state.existingColor
                    onSave(
                        MainColor(
                            id = existing?.id ?: customId(prefix = "color", name = trimmedName),
                            name = trimmedName,
                            hex = selectedColorHex.orEmpty(),
                            sortOrder = existing?.sortOrder ?: colors.nextColorSortOrder(),
                            isDefault = existing?.isDefault ?: false,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
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
private fun NeutralColorPresetRow(
    selectedHex: String?,
    onSelect: (NeutralColorPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.neutral_color_presets_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeutralColorPresets.forEach { preset ->
                NeutralColorPresetSwatch(
                    preset = preset,
                    selected = preset.hex == selectedHex,
                    onSelect = { onSelect(preset) },
                )
            }
        }
    }
}

@Composable
private fun NeutralColorPresetSwatch(
    preset: NeutralColorPreset,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val presetName = stringResource(preset.nameRes)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    // Low-saturation HSV choices are ambiguous by gesture alone; exact neutral presets make
    // black, gray, white, and the shared warm neutral selectable without guessing a hue.
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(preset.hex.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onSelect)
            .semantics {
                contentDescription = stringResource(R.string.content_select_neutral_color_swatch, presetName)
            },
    )
}

@Composable
private fun RestoreDefaultConfirmationDialog(
    target: RestoreDefaultTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (target) {
        RestoreDefaultTarget.MainColors -> stringResource(R.string.restore_default_colors_title)
        is RestoreDefaultTarget.Tags -> stringResource(R.string.restore_default_tags_title, target.category.localizedName())
    }
    val body = when (target) {
        RestoreDefaultTarget.MainColors -> stringResource(R.string.restore_default_colors_body)
        is RestoreDefaultTarget.Tags -> stringResource(R.string.restore_default_tags_body, target.category.localizedName())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.restore_default_confirm))
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
private fun DeleteColorConfirmationDialog(
    color: MainColor,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_color_title)) },
        text = { Text(stringResource(R.string.delete_color_body, color.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
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
private fun TagCategory.localizedName(): String = when (id) {
    "category" -> stringResource(R.string.category_category)
    "season" -> stringResource(R.string.category_season)
    "occasion" -> stringResource(R.string.category_occasion)
    "location" -> stringResource(R.string.category_location)
    else -> name
}

@Composable
private fun GarmentTag.localizedName(): String = localizedTagLabel()

@Composable
private fun GarmentTag.dotColor(): Color = when (categoryId) {
    "category" -> MaterialTheme.colorScheme.primaryContainer
    "season" -> MaterialTheme.colorScheme.secondaryContainer
    "occasion" -> MaterialTheme.colorScheme.outline
    "location" -> MaterialTheme.colorScheme.inversePrimary
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun List<GarmentTag>.nextTagSortOrder(): Int =
    (maxOfOrNull(GarmentTag::sortOrder) ?: 0) + 10

private fun List<MainColor>.nextColorSortOrder(): Int =
    (maxOfOrNull(MainColor::sortOrder) ?: 0) + 10

private val RestorableTagCategoryIds = setOf("category", "season", "occasion")

private fun customId(prefix: String, name: String): String {
    val slug = name
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifEmpty { prefix }
    return "$prefix-$slug-${UUID.randomUUID()}"
}

private fun String.toNormalizedName(): String =
    trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

private fun String.toNormalizedHex(): String? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return "#${normalized.uppercase(Locale.ROOT)}"
}

private fun String.toNormalizedRgbHex(): String? {
    val normalized = trim().removePrefix("#")
    val rgbHex = when (normalized.length) {
        6 -> normalized
        8 -> normalized.takeLast(6)
        else -> return null
    }
    if (rgbHex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return "#${rgbHex.uppercase(Locale.ROOT)}"
}

private fun String.toComposeColor(): Color? = toNormalizedHex()?.let { normalized ->
    Color(android.graphics.Color.parseColor(normalized))
}
