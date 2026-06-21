package com.gusanitolabs.robia.ui

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditClothingScreen(
    innerPadding: PaddingValues,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    existingItem: ClothingItem?,
    onCancel: () -> Unit,
    onSave: (ClothingItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backgroundRemover = remember { PhotoBackgroundRemover() }
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var originalPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoAspectRatio by rememberSaveable { mutableStateOf<Float?>(null) }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var fitValue by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPrimaryColorId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSecondaryColorId by rememberSaveable { mutableStateOf<String?>(null) }
    var captureStatus by rememberSaveable { mutableStateOf("") }
    var colorPickerTarget by rememberSaveable { mutableStateOf<ColorPickerTarget?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var isPhotoProcessing by rememberSaveable { mutableStateOf(false) }
    var photoProcessingStage by rememberSaveable { mutableStateOf<PhotoProcessingStage?>(null) }
    var photoProcessingToken by remember { mutableStateOf(0) }
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current

    val primaryPaletteColor = mainColors.colorForId(selectedPrimaryColorId)
    val secondaryPaletteColor = mainColors.colorForId(selectedSecondaryColorId)
    val primaryRawColor = primaryPaletteColor?.hex.orEmpty()
    val secondaryRawColor = secondaryPaletteColor?.hex.orEmpty()
    val primaryLabel = remember(primaryRawColor) { ColorLabelResolver.fromRawValue(primaryRawColor) }
    val secondaryLabel = remember(secondaryRawColor) { ColorLabelResolver.fromRawValue(secondaryRawColor) }

    fun applyExtractedColors(colors: List<ClothingImageStore.PaletteColorMatch>) {
        val primaryColorId = colors.getOrNull(0)?.color?.id
        if (primaryColorId != null) {
            selectedPrimaryColorId = primaryColorId
        }
        selectedSecondaryColorId = colors
            .drop(1)
            .firstOrNull { match ->
                match.ratio >= SECONDARY_COLOR_MIN_RATIO && match.color.id != primaryColorId
            }
            ?.color
            ?.id
    }

    fun processSelectedPhoto(uriString: String, sourceStatus: String) {
        val token = ++photoProcessingToken
        originalPhotoUri = uriString
        photoUri = uriString
        captureStatus = sourceStatus
        isPhotoProcessing = true
        photoProcessingStage = PhotoProcessingStage.RemovingBackground

        scope.launch {
            try {
                photoAspectRatio = withContext(Dispatchers.IO) {
                    ClothingImageStore.readImageAspectRatio(context, Uri.parse(uriString))
                }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO)
                val result = backgroundRemover.removeBackground(context, Uri.parse(uriString))
                if (token != photoProcessingToken) return@launch
                photoProcessingStage = PhotoProcessingStage.CroppingPicture
                val croppedResult = runCatching {
                    withContext(Dispatchers.IO) {
                        ClothingImageStore.cropTransparentPixels(context, result.outputUri)
                    }
                }
                val croppedUri = croppedResult.getOrDefault(result.originalUri)
                if (token != photoProcessingToken) return@launch
                photoAspectRatio = withContext(Dispatchers.IO) {
                    ClothingImageStore.readImageAspectRatio(context, croppedUri)
                }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO) ?: photoAspectRatio
                val displayUri = croppedUri.toString()
                photoUri = displayUri
                val finalStatus = if (result.usedFallback || croppedResult.isFailure) {
                    PhotoStatus.BackgroundFallback
                } else {
                    PhotoStatus.BackgroundRemoved
                }
                captureStatus = finalStatus
                photoProcessingStage = PhotoProcessingStage.ExtractingColor
                val extracted = if (finalStatus == PhotoStatus.BackgroundRemoved) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            ClothingImageStore.extractPaletteColorMatches(context, croppedUri, mainColors)
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                if (token != photoProcessingToken) return@launch
                if (extracted.isNotEmpty()) {
                    applyExtractedColors(extracted)
                }
            } catch (_: Exception) {
                if (token != photoProcessingToken) return@launch
                photoUri = uriString
                captureStatus = PhotoStatus.BackgroundFallback
            } finally {
                if (token == photoProcessingToken) {
                    isPhotoProcessing = false
                    photoProcessingStage = null
                }
            }
        }
    }

    LaunchedEffect(existingItem?.id, mainColors) {
        name = existingItem?.name.orEmpty()
        notes = existingItem?.notes.orEmpty()
        originalPhotoUri = existingItem?.photoUri
        photoUri = existingItem?.photoUri
        photoAspectRatio = null
        selectedTagIds = existingItem?.tags?.map(GarmentTag::id).orEmpty()
        fitValue = existingItem?.fitValue ?: if (existingItem == null) FIT_VALUE_FITS else null
        selectedPrimaryColorId = existingItem?.colorMetrics?.primaryPaletteColorId
            ?: mainColors.nearestColor(existingItem?.colorMetrics?.primaryPaletteColorHex ?: existingItem?.colorMetrics?.primaryRawValue)?.id
        selectedSecondaryColorId = existingItem?.colorMetrics?.secondaryPaletteColorId
            ?: mainColors.nearestColor(existingItem?.colorMetrics?.secondaryPaletteColorHex ?: existingItem?.colorMetrics?.secondaryRawValue)?.id
        captureStatus = ""
    }

    val isEditing = existingItem != null
    val initialFitValue = existingItem?.fitValue ?: if (existingItem == null) FIT_VALUE_FITS else null
    val untitledItem = stringResource(R.string.untitled_item)
    val hasUnsavedChanges = name != existingItem?.name.orEmpty() ||
        notes != existingItem?.notes.orEmpty() ||
        photoUri != existingItem?.photoUri ||
        selectedTagIds != existingItem?.tags?.map(GarmentTag::id).orEmpty() ||
        fitValue != initialFitValue ||
        selectedPrimaryColorId != existingItem?.colorMetrics?.primaryPaletteColorId ||
        selectedSecondaryColorId != existingItem?.colorMetrics?.secondaryPaletteColorId

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
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancel()
                    },
                ) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    colorPickerTarget?.let { target ->
        ColorPalettePickerDialog(
            title = stringResource(if (target == ColorPickerTarget.Primary) R.string.primary_color else R.string.secondary_color),
            colors = mainColors,
            allowNoColor = target == ColorPickerTarget.Secondary,
            onColorSelected = { color ->
                if (target == ColorPickerTarget.Primary) {
                    selectedPrimaryColorId = color?.id
                } else {
                    selectedSecondaryColorId = color?.id
                }
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null },
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
            if (activityResultRegistryOwner != null) {
                PhotoCaptureCardWithLaunchers(
                    photoUri = photoUri,
                    photoAspectRatio = photoAspectRatio,
                    captureStatus = captureStatus,
                    isPhotoProcessing = isPhotoProcessing,
                    processingStage = photoProcessingStage,
                    onCaptureStatusChange = { captureStatus = it },
                    onPhotoSelected = ::processSelectedPhoto,
                )
            } else {
                PhotoCaptureCard(
                    photoUri = photoUri,
                    photoAspectRatio = photoAspectRatio,
                    captureStatus = PhotoStatus.MediaUnavailable,
                    isPhotoProcessing = false,
                    processingStage = null,
                    onGalleryClick = null,
                    onCameraClick = null,
                )
            }
        }

        item {
            CardSection(title = stringResource(R.string.item_details_section)) {
                Text(
                    text = stringResource(R.string.item_details_refined_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_name_label)) },
                    placeholder = { Text(stringResource(R.string.item_name_placeholder)) },
                    supportingText = {
                        if (name.isBlank()) Text(stringResource(R.string.item_name_required_to_save))
                    },
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
            CardSection(title = stringResource(R.string.extracted_colors)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    PaletteColorCircle(
                        title = stringResource(R.string.primary_color),
                        color = primaryPaletteColor,
                        modifier = Modifier.weight(1f),
                        onClick = { colorPickerTarget = ColorPickerTarget.Primary },
                    )
                    PaletteColorCircle(
                        title = stringResource(R.string.secondary_color),
                        color = secondaryPaletteColor,
                        emptyIcon = Icons.Rounded.Close,
                        modifier = Modifier.weight(1f),
                        onClick = { colorPickerTarget = ColorPickerTarget.Secondary },
                    )
                }
                Text(
                    text = stringResource(R.string.palette_selector_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MetadataCaptureSection(
                availableTags = availableTags,
                selectedTagIds = selectedTagIds,
                fitValue = fitValue,
                onSelectedTagIdsChange = { selectedTagIds = it },
                onFitValueChange = { fitValue = it },
            )
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
                                fitValue = fitValue,
                                colorMetrics = ClothingColorMetrics(
                                    primaryRawValue = primaryPaletteColor?.hex,
                                    primaryDisplayLabel = primaryLabel.takeIf { primaryPaletteColor != null },
                                    primaryPaletteColorId = primaryPaletteColor?.id,
                                    primaryPaletteColorName = primaryPaletteColor?.name,
                                    primaryPaletteColorHex = primaryPaletteColor?.hex,
                                    secondaryRawValue = secondaryPaletteColor?.hex,
                                    secondaryDisplayLabel = secondaryLabel.takeIf { secondaryPaletteColor != null },
                                    secondaryPaletteColorId = secondaryPaletteColor?.id,
                                    secondaryPaletteColorName = secondaryPaletteColor?.name,
                                    secondaryPaletteColorHex = secondaryPaletteColor?.hex,
                                ),
                                isFavorite = existingItem?.isFavorite ?: false,
                                isArchived = existingItem?.isArchived ?: false,
                                createdAtEpochMillis = existingItem?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = now,
                            ),
                        )
                    },
                    enabled = name.isNotBlank() && !isPhotoProcessing,
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

@Composable
private fun MetadataCaptureSection(
    availableTags: List<GarmentTag>,
    selectedTagIds: List<String>,
    fitValue: Int?,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    onFitValueChange: (Int?) -> Unit,
) {
    CardSection(title = stringResource(R.string.metadata_section)) {
        Text(
            text = stringResource(R.string.metadata_section_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (availableTags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MetadataDropdownCard(
                title = stringResource(R.string.metadata_category),
                subtitle = stringResource(R.string.single_select_metadata_hint),
                icon = Icons.Rounded.Checkroom,
                tags = availableTags.forCategory("category"),
                selectedTagIds = selectedTagIds,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            MetadataTagCard(
                title = stringResource(R.string.metadata_season),
                subtitle = stringResource(R.string.multi_select_metadata_hint),
                icon = Icons.Rounded.WbSunny,
                tags = availableTags.forCategory("season"),
                selectedTagIds = selectedTagIds,
                singleSelect = false,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            FitChoiceCard(
                fitValue = fitValue,
                onFitValueChange = onFitValueChange,
            )
            MetadataDropdownCard(
                title = stringResource(R.string.metadata_location),
                subtitle = stringResource(R.string.single_select_metadata_hint),
                icon = Icons.Rounded.Inventory2,
                tags = availableTags.forCategory("location"),
                selectedTagIds = selectedTagIds,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            MetadataTagCard(
                title = stringResource(R.string.metadata_occasions),
                subtitle = stringResource(R.string.multi_select_metadata_hint),
                icon = Icons.Rounded.Event,
                tags = availableTags.forCategory("occasion"),
                selectedTagIds = selectedTagIds,
                singleSelect = false,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
        }
    }
}

@Composable
private fun MetadataDropdownCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tags: List<GarmentTag>,
    selectedTagIds: List<String>,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTag = tags.firstOrNull { tag -> tag.id in selectedTagIds }
    val categoryTagIds = remember(tags) { tags.map(GarmentTag::id).toSet() }
    var expanded by remember { mutableStateOf(false) }

    MetadataShellCard(title = title, subtitle = subtitle, icon = icon, modifier = modifier) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.no_tags_in_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selectedTag?.localizedLabel() ?: stringResource(R.string.not_set),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.not_set)) },
                        onClick = {
                            expanded = false
                            onSelectedTagIdsChange(selectedTagIds.filterNot { selectedId -> selectedId in categoryTagIds })
                        },
                    )
                    tags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag.localizedLabel()) },
                            onClick = {
                                expanded = false
                                onSelectedTagIdsChange(
                                    selectedTagIds.filterNot { selectedId -> selectedId in categoryTagIds } + tag.id,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FitChoiceCard(
    fitValue: Int?,
    onFitValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetadataShellCard(
        title = stringResource(R.string.metadata_fit),
        subtitle = stringResource(R.string.metadata_fit_hint),
        icon = Icons.Rounded.Straighten,
        modifier = modifier,
    ) {
        val fits = fitValue.indicatesFits()
        FlowChipRow {
            FitChoiceChip(
                label = stringResource(R.string.fit_good),
                selected = fits,
                onClick = { onFitValueChange(if (fits) FIT_VALUE_DOES_NOT_FIT else FIT_VALUE_FITS) },
            )
        }
    }
}

@Composable
private fun FitChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun MetadataTagCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tags: List<GarmentTag>,
    selectedTagIds: List<String>,
    singleSelect: Boolean,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetadataShellCard(title = title, subtitle = subtitle, icon = icon, modifier = modifier) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.no_tags_in_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            FlowChipRow {
                tags.forEach { tag ->
                    val selected = tag.id in selectedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelectedTagIdsChange(
                                selectedTagIds.toggleTag(
                                    tag = tag,
                                    selected = selected,
                                    singleSelect = singleSelect,
                                    categoryTagIds = tags.map(GarmentTag::id),
                                ),
                            )
                        },
                        label = { Text(tag.localizedLabel()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataShellCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

private fun List<GarmentTag>.forCategory(categoryId: String): List<GarmentTag> =
    filter { tag -> tag.categoryId == categoryId }.sortedWith(compareBy<GarmentTag> { it.sortOrder }.thenBy { it.name })

private fun List<String>.toggleTag(
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
private fun PhotoCaptureCardWithLaunchers(
    photoUri: String?,
    photoAspectRatio: Float?,
    captureStatus: String,
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
    onCaptureStatusChange: (String) -> Unit,
    onPhotoSelected: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onCaptureStatusChange("")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val storedUri = runCatching {
                withContext(Dispatchers.IO) {
                    ClothingImageStore.copyContentUriToPrivateStorage(context, uri)
                }
            }.getOrElse {
                onCaptureStatusChange(PhotoStatus.MediaUnavailable)
                return@launch
            }
            onPhotoSelected(storedUri.toString(), PhotoStatus.Gallery)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val capturedUri = pendingCameraUri
        if (captured && capturedUri != null) {
            onPhotoSelected(capturedUri, PhotoStatus.Camera)
        } else {
            onCaptureStatusChange("")
        }
        pendingCameraUri = null
    }

    PhotoCaptureCard(
        photoUri = photoUri,
        photoAspectRatio = photoAspectRatio,
        captureStatus = captureStatus,
        isPhotoProcessing = isPhotoProcessing,
        processingStage = processingStage,
        onGalleryClick = {
            runCatching { galleryLauncher.launch(arrayOf("image/*")) }
                .onFailure { onCaptureStatusChange(PhotoStatus.MediaUnavailable) }
        },
        onCameraClick = {
            runCatching {
                val cameraUri = ClothingImageStore.createCaptureUri(context)
                pendingCameraUri = cameraUri.toString()
                cameraLauncher.launch(cameraUri)
            }.onFailure {
                pendingCameraUri = null
                onCaptureStatusChange(PhotoStatus.MediaUnavailable)
            }
        },
    )
}

@Composable
private fun PhotoCaptureCard(
    photoUri: String?,
    photoAspectRatio: Float?,
    captureStatus: String,
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
    onGalleryClick: (() -> Unit)?,
    onCameraClick: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val processingLabel = if (processingStage != null) {
                stringResource(processingStage.labelRes)
            } else {
                null
            }
            PhotoPreview(
                photoUri = photoUri,
                isProcessing = isPhotoProcessing,
                processingLabel = processingLabel,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = photoAspectRatio ?: 4f / 3f,
                onEmptyPhotoClick = onCameraClick,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onGalleryClick?.invoke() }, enabled = onGalleryClick != null) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.content_open_gallery),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gallery))
                }
                Button(
                    onClick = { onCameraClick?.invoke() },
                    enabled = onCameraClick != null,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.content_open_camera),
                    )
                }
            }
            val statusText = when (captureStatus) {
                PhotoStatus.Gallery -> R.string.gallery_selected_status
                PhotoStatus.Camera -> R.string.camera_selected_status
                PhotoStatus.Processed -> R.string.photo_processed_status
                PhotoStatus.BackgroundRemoved -> R.string.background_removal_success_status
                PhotoStatus.BackgroundFallback -> R.string.background_removal_fallback_status
                PhotoStatus.MediaUnavailable -> R.string.media_actions_unavailable_status
                else -> null
            }
            if (statusText != null) {
                Text(
                    text = stringResource(statusText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoPreview(
    photoUri: String?,
    isProcessing: Boolean = false,
    processingLabel: String? = null,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 3f / 4f,
    onEmptyPhotoClick: (() -> Unit)? = null,
) {
    val uri = photoUri?.takeIf { it.isNotBlank() }
    val emptyPhotoContentDescription = stringResource(R.string.content_open_camera)
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (uri == null && onEmptyPhotoClick != null) {
                    Modifier
                        .clickable(onClick = onEmptyPhotoClick)
                        .semantics { contentDescription = emptyPhotoContentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { imageView -> imageView.setImageURI(Uri.parse(uri)) },
                modifier = Modifier.fillMaxSize(),
            )
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        processingLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        } else {
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
                    text = stringResource(R.string.photo_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onEmptyPhotoClick != null) {
                    Text(
                        text = stringResource(R.string.photo_placeholder_tap_camera),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
private fun PaletteColorCircle(
    title: String,
    color: MainColor?,
    emptyIcon: ImageVector = Icons.Rounded.Add,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color?.hex?.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(emptyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = color?.name ?: stringResource(R.string.none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColorPalettePickerDialog(
    title: String,
    colors: List<MainColor>,
    allowNoColor: Boolean,
    onColorSelected: (MainColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (colors.isEmpty() && !allowNoColor) {
                Text(stringResource(R.string.palette_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowNoColor) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onColorSelected(null) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                }
                                Text(stringResource(R.string.no_color), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(colors.size) { index ->
                        val color = colors[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onColorSelected(color) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ColorSwatch(rawColor = color.hex)
                            Column {
                                Text(color.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(color.hex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
    "occasion-everyday" -> stringResource(R.string.tag_everyday)
    "occasion-work" -> stringResource(R.string.tag_work)
    "occasion-travel" -> stringResource(R.string.tag_travel)
    "location-main-closet" -> stringResource(R.string.tag_main_closet)
    else -> name
}

private enum class ColorPickerTarget { Primary, Secondary }

private enum class PhotoProcessingStage(val labelRes: Int) {
    RemovingBackground(R.string.processing_stage_removing_background),
    CroppingPicture(R.string.processing_stage_cropping_picture),
    ExtractingColor(R.string.processing_stage_extracting_color),
}

private const val FIT_VALUE_DOES_NOT_FIT = 0
private const val FIT_VALUE_FITS = 2
private const val SECONDARY_COLOR_MIN_RATIO = 0.20f
private const val PHOTO_PREVIEW_MIN_ASPECT_RATIO = 0.66f
private const val PHOTO_PREVIEW_MAX_ASPECT_RATIO = 1.6f

private object PhotoStatus {
    const val Gallery = "gallery"
    const val Camera = "camera"
    const val Processed = "processed"
    const val BackgroundProcessing = "background_processing"
    const val BackgroundRemoved = "background_removed"
    const val BackgroundDisabled = "background_disabled"
    const val BackgroundFallback = "background_fallback"
    const val MediaUnavailable = "media_unavailable"
}

private fun Int?.indicatesFits(): Boolean = this != FIT_VALUE_DOES_NOT_FIT

private fun List<MainColor>.colorForId(id: String?): MainColor? = firstOrNull { color -> color.id == id }

private fun List<MainColor>.nearestColor(rawHex: String?): MainColor? {
    val rgb = rawHex?.toRgbOrNull() ?: return null
    return minByOrNull { color ->
        val paletteRgb = color.hex.toRgbOrNull() ?: return@minByOrNull Int.MAX_VALUE
        (rgb.red - paletteRgb.red) * (rgb.red - paletteRgb.red) +
            (rgb.green - paletteRgb.green) * (rgb.green - paletteRgb.green) +
            (rgb.blue - paletteRgb.blue) * (rgb.blue - paletteRgb.blue)
    }
}

private data class RgbColor(val red: Int, val green: Int, val blue: Int)

private fun String.toRgbOrNull(): RgbColor? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return RgbColor(
        red = normalized.substring(0, 2).toInt(16),
        green = normalized.substring(2, 4).toInt(16),
        blue = normalized.substring(4, 6).toInt(16),
    )
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
