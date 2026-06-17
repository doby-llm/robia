package com.gusanitolabs.robia.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentTag
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditClothingScreen(
    innerPadding: PaddingValues,
    availableTags: List<GarmentTag>,
    existingItem: ClothingItem?,
    onCancel: () -> Unit,
    onSave: (ClothingItem) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var primaryRawColor by rememberSaveable { mutableStateOf("") }
    var secondaryRawColor by rememberSaveable { mutableStateOf("") }
    var removeBackground by rememberSaveable { mutableStateOf(true) }
    var captureStatus by rememberSaveable { mutableStateOf("") }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existingItem?.id) {
        name = existingItem?.name.orEmpty()
        notes = existingItem?.notes.orEmpty()
        photoUri = existingItem?.photoUri
        selectedTagIds = existingItem?.tags?.map(GarmentTag::id).orEmpty()
        primaryRawColor = existingItem?.colorMetrics?.primaryRawValue.orEmpty()
        secondaryRawColor = existingItem?.colorMetrics?.secondaryRawValue.orEmpty()
        captureStatus = ""
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
            captureStatus = "gallery"
        }
    }

    val primaryLabel = remember(primaryRawColor) { ColorLabelResolver.fromRawValue(primaryRawColor) }
    val secondaryLabel = remember(secondaryRawColor) { ColorLabelResolver.fromRawValue(secondaryRawColor) }
    val isEditing = existingItem != null
    val untitledItem = stringResource(R.string.untitled_item)
    val hasUnsavedChanges = name != existingItem?.name.orEmpty() ||
        notes != existingItem?.notes.orEmpty() ||
        photoUri != existingItem?.photoUri ||
        selectedTagIds != existingItem?.tags?.map(GarmentTag::id).orEmpty() ||
        primaryRawColor != existingItem?.colorMetrics?.primaryRawValue.orEmpty() ||
        secondaryRawColor != existingItem?.colorMetrics?.secondaryRawValue.orEmpty()

    fun requestClose() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler { requestClose() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_body)) },
            confirmButton = {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(if (isEditing) R.string.edit_clothing_title else R.string.add_edit_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.add_edit_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PhotoCaptureCard(
                photoUri = photoUri,
                captureStatus = captureStatus,
                removeBackground = removeBackground,
                onRemoveBackgroundChange = { removeBackground = it },
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onCameraClick = { captureStatus = "camera" },
            )
        }

        item {
            CardSection(title = stringResource(R.string.item_details_section)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_name_label)) },
                    placeholder = { Text(stringResource(R.string.item_name_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_notes_label)) },
                    placeholder = { Text(stringResource(R.string.item_notes_placeholder)) },
                    minLines = 3,
                )
            }
        }

        item {
            CardSection(title = stringResource(R.string.colors_section)) {
                ColorInputRow(
                    title = stringResource(R.string.primary_color),
                    rawColor = primaryRawColor,
                    label = primaryLabel,
                    onRawColorChange = { primaryRawColor = it },
                    required = true,
                )
                ColorInputRow(
                    title = stringResource(R.string.secondary_color),
                    rawColor = secondaryRawColor,
                    label = secondaryLabel,
                    onRawColorChange = { secondaryRawColor = it },
                    required = false,
                )
            }
        }

        item {
            CardSection(title = stringResource(R.string.tags_section)) {
                if (availableTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowChipRow {
                        availableTags.forEach { tag ->
                            val selected = tag.id in selectedTagIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedTagIds = if (selected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text(tag.localizedLabel()) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = ::requestClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val selectedTags = availableTags.filter { it.id in selectedTagIds }
                        onSave(
                            ClothingItem(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { untitledItem },
                                notes = notes,
                                photoUri = photoUri,
                                tags = selectedTags,
                                colorMetrics = ClothingColorMetrics(
                                    primaryRawValue = primaryRawColor.ifBlank { null },
                                    primaryDisplayLabel = primaryLabel,
                                    secondaryRawValue = secondaryRawColor.ifBlank { null },
                                    secondaryDisplayLabel = secondaryLabel.takeUnless { secondaryRawColor.isBlank() },
                                ),
                                isFavorite = existingItem?.isFavorite ?: false,
                                isArchived = existingItem?.isArchived ?: false,
                                createdAtEpochMillis = existingItem?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = now,
                            ),
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(2f),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_item))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    innerPadding: PaddingValues,
    item: ClothingItem?,
    onEditClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (item == null) {
            item { EmptyDetailCard(onAddClick = onAddClick) }
        } else {
            item {
                PhotoPreview(photoUri = item.photoUri, modifier = Modifier.fillMaxWidth())
            }
            item {
                CardSection(title = stringResource(R.string.item_detail_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AssistChip(
                            onClick = onEditClick,
                            label = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        )
                    }
                }
            }
            item {
                CardSection(title = stringResource(R.string.colors_section)) {
                    ColorSummaryRow(
                        title = stringResource(R.string.primary_color),
                        rawValue = item.colorMetrics.primaryRawValue,
                        label = item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
                    )
                    ColorSummaryRow(
                        title = stringResource(R.string.secondary_color),
                        rawValue = item.colorMetrics.secondaryRawValue,
                        label = item.colorMetrics.secondaryDisplayLabel ?: DisplayColorLabel.Unknown,
                    )
                }
            }
            item {
                CardSection(title = stringResource(R.string.tags_section)) {
                    if (item.tags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_tags_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowChipRow {
                            item.tags.forEach { tag -> DetailTonalTag(text = tag.localizedLabel()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCaptureCard(
    photoUri: String?,
    captureStatus: String,
    removeBackground: Boolean,
    onRemoveBackgroundChange: (Boolean) -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoPreview(photoUri = photoUri, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onGalleryClick) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gallery))
                }
                Button(onClick = onCameraClick, shape = CircleShape) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.background_removal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.background_removal_status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = removeBackground, onCheckedChange = onRemoveBackgroundChange)
                }
                val statusText = when (captureStatus) {
                    "gallery" -> R.string.gallery_selected_status
                    "camera" -> R.string.camera_hook_status
                    else -> R.string.photo_placeholder_status
                }
                Text(
                    text = stringResource(statusText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoPreview(photoUri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = photoUri ?: stringResource(R.string.photo_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun ColorInputRow(
    title: String,
    rawColor: String,
    label: DisplayColorLabel,
    onRawColorChange: (String) -> Unit,
    required: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ColorSwatch(rawColor = rawColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(R.string.display_color_format, stringResource(label.stringRes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = rawColor,
            onValueChange = onRawColorChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (required) stringResource(R.string.raw_color_required) else stringResource(R.string.raw_color_optional)) },
            placeholder = { Text(stringResource(R.string.raw_color_placeholder)) },
            singleLine = true,
        )
    }
}

@Composable
private fun ColorSummaryRow(
    title: String,
    rawValue: String?,
    label: DisplayColorLabel,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorSwatch(rawColor = rawValue.orEmpty())
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.raw_color_summary, rawValue ?: stringResource(R.string.none), stringResource(label.stringRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorSwatch(rawColor: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(rawColor.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (rawColor.toComposeColor() == null) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EmptyDetailCard(onAddClick: () -> Unit) {
    CardSection(title = stringResource(R.string.item_detail_title)) {
        Text(
            text = stringResource(R.string.item_detail_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = onAddClick,
            label = { Text(stringResource(R.string.add_clothing)) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun DetailTonalTag(text: String) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GarmentTag.localizedLabel(): String = when (id) {
    "category-t-shirt" -> stringResource(R.string.tag_t_shirt)
    "category-pants" -> stringResource(R.string.tag_pants)
    "category-shirt" -> stringResource(R.string.tag_shirt)
    "category-shoes" -> stringResource(R.string.tag_shoes)
    "season-spring" -> stringResource(R.string.tag_spring)
    "season-summer" -> stringResource(R.string.tag_summer)
    "season-autumn" -> stringResource(R.string.tag_autumn)
    "season-winter" -> stringResource(R.string.tag_winter)
    "fit-regular" -> stringResource(R.string.tag_regular)
    "fit-slim" -> stringResource(R.string.tag_slim)
    "fit-oversized" -> stringResource(R.string.tag_oversized)
    "occasion-everyday" -> stringResource(R.string.tag_everyday)
    "occasion-work" -> stringResource(R.string.tag_work)
    "occasion-travel" -> stringResource(R.string.tag_travel)
    "location-main-closet" -> stringResource(R.string.tag_main_closet)
    else -> name
}

private val DisplayColorLabel.stringRes: Int
    get() = when (this) {
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

private fun String.toComposeColor(): Color? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return Color(android.graphics.Color.parseColor("#$normalized"))
}
