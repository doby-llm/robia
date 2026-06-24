package com.gusanitolabs.robia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import java.util.Locale
import java.util.UUID

private const val SeasonCategoryId = "season"
private const val DefaultCustomColorHex = "#A8644E"

private data class TagEditorState(
    val categoryId: String,
    val existingTag: GarmentTag? = null,
)

private data class ColorEditorState(
    val existingColor: MainColor? = null,
)


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
) {
    var editorState by remember { mutableStateOf<TagEditorState?>(null) }
    var colorEditorState by remember { mutableStateOf<ColorEditorState?>(null) }
    var pendingDeleteColor by remember { mutableStateOf<MainColor?>(null) }
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
                    onDeleteTag = onDeleteTag,
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
                onSaveTag(tag)
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
}

@Composable
private fun TagCategoryCard(
    category: TagCategory,
    tags: List<GarmentTag>,
    onAddTag: () -> Unit,
    onEditTag: (GarmentTag) -> Unit,
    onDeleteTag: (GarmentTag) -> Unit,
) {
    val addDescription = stringResource(R.string.content_add_tag)
    val isImmutable = category.id == SeasonCategoryId

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
                if (!isImmutable) {
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = addDescription },
                        onClick = onAddTag,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
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
                                canEdit = !isImmutable,
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
                label = { Text(stringResource(R.string.tag_name_label)) },
            )
        },
        confirmButton = {
            Button(
                enabled = trimmedName.isNotEmpty(),
                onClick = {
                    val existing = state.existingTag
                    onSave(
                        GarmentTag(
                            id = existing?.id ?: customId(prefix = state.categoryId, name = trimmedName),
                            categoryId = state.categoryId,
                            name = trimmedName,
                            sortOrder = existing?.sortOrder ?: categoryTags.nextTagSortOrder(),
                            isSystem = false,
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
private fun GarmentTag.localizedName(): String = when (id) {
    "category-shorts" -> stringResource(R.string.tag_shorts)
    "category-jackets" -> stringResource(R.string.tag_jackets)
    "category-jumpsuits" -> stringResource(R.string.tag_jumpsuits)
    "category-blouses" -> stringResource(R.string.tag_blouses)
    "category-dresses" -> stringResource(R.string.tag_dresses)
    "category-skirts" -> stringResource(R.string.tag_skirts)
    "category-blazers" -> stringResource(R.string.tag_blazers)
    "category-cardigans" -> stringResource(R.string.tag_cardigans)
    "category-bags" -> stringResource(R.string.tag_bags)
    "category-tops" -> stringResource(R.string.tag_tops)
    "category-knitwear" -> stringResource(R.string.tag_knitwear)
    "category-trousers" -> stringResource(R.string.tag_trousers)
    "category-sweaters" -> stringResource(R.string.tag_sweaters)
    "category-shoes" -> stringResource(R.string.tag_shoes)
    "category-shirts" -> stringResource(R.string.tag_shirts)
    "category-vests" -> stringResource(R.string.tag_vests)
    "category-jewelry" -> stringResource(R.string.tag_jewelry)
    "category-accessories" -> stringResource(R.string.tag_accessories)
    "category-coats" -> stringResource(R.string.tag_coats)
    "season-spring" -> stringResource(R.string.tag_spring)
    "season-summer" -> stringResource(R.string.tag_summer)
    "season-fall" -> stringResource(R.string.tag_fall)
    "season-winter" -> stringResource(R.string.tag_winter)
    "occasion-active" -> stringResource(R.string.tag_active)
    "occasion-statement" -> stringResource(R.string.tag_statement)
    "occasion-dressed-up" -> stringResource(R.string.tag_dressed_up)
    "occasion-formal" -> stringResource(R.string.tag_formal)
    "occasion-everyday" -> stringResource(R.string.tag_everyday)
    "occasion-business" -> stringResource(R.string.tag_business)
    "location-main-closet" -> stringResource(R.string.tag_main_closet)
    else -> name
}

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
