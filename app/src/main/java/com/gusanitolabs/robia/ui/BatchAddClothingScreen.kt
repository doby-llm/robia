package com.gusanitolabs.robia.ui

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorClassifier
import com.gusanitolabs.robia.core.color.RgbColor
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import com.gusanitolabs.robia.media.additionalinfo.TfliteAdditionalInfoDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class BatchDraftItem(
    val id: String = UUID.randomUUID().toString(),
    val orderIndex: Int,
    val originalPhotoUri: String,
    val photoUri: String = originalPhotoUri,
    val photoAspectRatio: Float? = null,
    val status: BatchDraftStatus = BatchDraftStatus.Queued,
    val errorMessage: String? = null,
    val name: String = "",
    val notes: String = "",
    val selectedTagIds: List<String> = emptyList(),
    val fitValue: Int? = FIT_VALUE_FITS,
    val selectedPrimaryColorId: String? = null,
    val selectedSecondaryColorId: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

internal enum class BatchDraftStatus(@StringRes val labelRes: Int) {
    Queued(R.string.batch_status_queued),
    Processing(R.string.batch_status_processing),
    NeedsReview(R.string.batch_status_needs_review),
    Ready(R.string.batch_status_ready),
    Failed(R.string.batch_status_failed),
}

@Composable
internal fun BatchAddClothingScreen(
    innerPadding: PaddingValues,
    drafts: List<BatchDraftItem>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onDraftUpdated: (BatchDraftItem) -> Unit,
    onDraftSelected: (BatchDraftItem) -> Unit,
    onSaveBatch: (List<ClothingItem>) -> Unit,
    onCancelBatch: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundRemover = remember { PhotoBackgroundRemover() }
    val additionalInfoDetector = remember { TfliteAdditionalInfoDetector() }
    val latestDrafts by rememberUpdatedState(drafts)
    val latestMainColors by rememberUpdatedState(mainColors)
    val latestAvailableTags by rememberUpdatedState(availableTags)
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    fun requestCancel() {
        if (drafts.isNotEmpty()) {
            showDiscardDialog = true
        } else {
            onCancelBatch()
        }
    }

    BackHandler { requestCancel() }

    LaunchedEffect(drafts.map(BatchDraftItem::id)) {
        while (true) {
            val next = latestDrafts.firstOrNull { it.status == BatchDraftStatus.Queued } ?: break
            processBatchDraft(
                draft = next,
                context = context,
                backgroundRemover = backgroundRemover,
                additionalInfoDetector = additionalInfoDetector,
                mainColors = latestMainColors,
                availableTags = latestAvailableTags,
                onDraftUpdated = onDraftUpdated,
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.batch_discard_title)) },
            text = { Text(stringResource(R.string.batch_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancelBatch()
                    },
                ) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    val processedCount = drafts.count { it.status != BatchDraftStatus.Queued && it.status != BatchDraftStatus.Processing }
    val processingCount = drafts.count { it.status == BatchDraftStatus.Queued || it.status == BatchDraftStatus.Processing }
    val needsReviewCount = drafts.count { it.status == BatchDraftStatus.NeedsReview }
    val failedCount = drafts.count { it.status == BatchDraftStatus.Failed }
    val progress = if (drafts.isEmpty()) 0f else processedCount.toFloat() / drafts.size.toFloat()
    val canSave = drafts.isNotEmpty() && drafts.all { it.status == BatchDraftStatus.Ready }
    val saveHelper = when {
        processingCount > 0 -> stringResource(R.string.batch_helper_processing, processingCount)
        needsReviewCount > 0 -> stringResource(R.string.batch_helper_needs_review, needsReviewCount)
        failedCount > 0 -> stringResource(R.string.batch_helper_failed, failedCount)
        drafts.isEmpty() -> stringResource(R.string.batch_helper_empty)
        else -> stringResource(R.string.batch_helper_ready, drafts.size)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 144.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BatchProgressHeader(
                    totalCount = drafts.size,
                    processedCount = processedCount,
                    progress = progress,
                    helper = saveHelper,
                )
            }
            items(drafts, key = BatchDraftItem::id) { draft ->
                BatchDraftTile(
                    draft = draft,
                    position = draft.orderIndex + 1,
                    totalCount = drafts.size,
                    onClick = {
                        if (draft.status.isSelectable) {
                            onDraftSelected(draft)
                        }
                    },
                )
            }
        }
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = saveHelper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onSaveBatch(drafts.map { it.toClothingItem(availableTags, mainColors) }) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.batch_save_items, drafts.size))
                }
            }
        }
    }
}

@Composable
private fun BatchProgressHeader(
    totalCount: Int,
    processedCount: Int,
    progress: Float,
    helper: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.batch_add_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.batch_processing_count, processedCount, totalCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BatchProgressBar(progress = progress)
            Text(
                text = helper,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatchProgressBar(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "batchProgress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun BatchDraftTile(
    draft: BatchDraftItem,
    position: Int,
    totalCount: Int,
    onClick: () -> Unit,
) {
    val status = stringResource(draft.status.labelRes)
    val tileDescription = stringResource(R.string.batch_tile_content_description, position, totalCount, status)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = tileDescription
                stateDescription = status
            }
            .alpha(if (draft.status == BatchDraftStatus.Queued) 0.62f else 1f)
            .clickable(
                enabled = draft.status.isSelectable,
                onClick = onClick,
            ),
    ) {
        Box {
            BatchPhotoPreview(
                photoUri = draft.photoUri,
                isProcessing = draft.status == BatchDraftStatus.Processing,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
            BatchStatusBadge(
                status = draft.status,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = draft.errorMessage ?: status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BatchPhotoPreview(
    photoUri: String,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    val processingOverlayColors = rememberProcessingOverlayColors(photoUri, isProcessing)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUri.isNotBlank()) {
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
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(56.dp),
            )
        }
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(processingOverlayColors.scrimColor),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = processingOverlayColors.contentColor)
            }
        }
    }
}

@Composable
private fun BatchStatusBadge(
    status: BatchDraftStatus,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (status) {
        BatchDraftStatus.Ready -> MaterialTheme.colorScheme.primaryContainer
        BatchDraftStatus.Failed -> MaterialTheme.colorScheme.errorContainer
        BatchDraftStatus.NeedsReview -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val contentColor = when (status) {
        BatchDraftStatus.Ready -> MaterialTheme.colorScheme.onPrimaryContainer
        BatchDraftStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        BatchDraftStatus.NeedsReview -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                BatchDraftStatus.Ready -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                BatchDraftStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                BatchDraftStatus.NeedsReview -> Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp))
                else -> Unit
            }
            Text(
                text = stringResource(status.labelRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private suspend fun processBatchDraft(
    draft: BatchDraftItem,
    context: android.content.Context,
    backgroundRemover: PhotoBackgroundRemover,
    additionalInfoDetector: TfliteAdditionalInfoDetector,
    mainColors: List<MainColor>,
    availableTags: List<GarmentTag>,
    onDraftUpdated: (BatchDraftItem) -> Unit,
) {
    onDraftUpdated(draft.copy(status = BatchDraftStatus.Processing, errorMessage = null))
    try {
        val sourceUri = Uri.parse(draft.originalPhotoUri)
        val sourceAspectRatio = withContext(Dispatchers.IO) {
            ClothingImageStore.readImageAspectRatio(context, sourceUri)
        }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO)
        val backgroundResult = backgroundRemover.removeBackground(context, sourceUri)
        val croppedUri = runCatching {
            withContext(Dispatchers.IO) { ClothingImageStore.cropTransparentPixels(context, backgroundResult.outputUri) }
        }.getOrDefault(backgroundResult.outputUri)
        val displayAspectRatio = withContext(Dispatchers.IO) {
            ClothingImageStore.readImageAspectRatio(context, croppedUri)
        }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO) ?: sourceAspectRatio
        val matches = runCatching {
            withContext(Dispatchers.IO) { ClothingImageStore.extractPaletteColorDiagnostics(context, croppedUri, mainColors).matches }
        }.getOrDefault(emptyList())
        val primaryColorId = matches.getOrNull(0)?.color?.id
        val secondaryColorId = matches.drop(1)
            .firstOrNull { match -> match.ratio >= SECONDARY_COLOR_MIN_RATIO && match.color.id != primaryColorId }
            ?.color
            ?.id
        val detectionResult = runCatching {
            withContext(Dispatchers.IO) { additionalInfoDetector.detect(context, croppedUri, availableTags) }
        }.getOrNull()
        val detectedTagIds = detectionResult?.prediction?.selectedTagIds.orEmpty()
        val mergedTags = mergePredictedTags(draft.selectedTagIds, detectedTagIds, availableTags)
        val needsOriginalFallbackReview = backgroundResult.usedFallback || croppedUri == sourceUri
        val resolvedStatus = if (needsOriginalFallbackReview) BatchDraftStatus.NeedsReview else BatchDraftStatus.Ready
        val resolvedMessage = if (needsOriginalFallbackReview) {
            context.getString(R.string.batch_original_fallback_message)
        } else {
            null
        }
        onDraftUpdated(
            draft.copy(
                photoUri = croppedUri.toString(),
                photoAspectRatio = displayAspectRatio,
                status = resolvedStatus,
                selectedPrimaryColorId = primaryColorId,
                selectedSecondaryColorId = secondaryColorId,
                selectedTagIds = mergedTags,
                errorMessage = resolvedMessage,
            ),
        )
    } catch (throwable: CancellationException) {
        throw throwable
    } catch (throwable: Exception) {
        onDraftUpdated(
            draft.copy(
                status = BatchDraftStatus.Failed,
                errorMessage = throwable.message?.takeIf { it.isNotBlank() },
            ),
        )
    }
}

private val BatchDraftStatus.isSelectable: Boolean
    get() = this == BatchDraftStatus.Ready || this == BatchDraftStatus.NeedsReview || this == BatchDraftStatus.Failed

internal fun BatchDraftItem.toClothingItem(
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
): ClothingItem {
    val now = System.currentTimeMillis()
    val primaryPaletteColor = mainColors.colorForId(selectedPrimaryColorId)
    val secondaryPaletteColor = mainColors.colorForId(selectedSecondaryColorId)
    val primaryRawColor = primaryPaletteColor?.hex.orEmpty()
    val secondaryRawColor = secondaryPaletteColor?.hex.orEmpty()
    return ClothingItem(
        id = id,
        name = name,
        notes = notes,
        photoUri = photoUri,
        tags = availableTags.filter { tag -> tag.id in selectedTagIds },
        fitValue = fitValue,
        colorMetrics = ClothingColorMetrics(
            primaryRawValue = primaryPaletteColor?.hex,
            primaryDisplayLabel = ColorLabelResolver.fromRawValue(primaryRawColor).takeIf { primaryPaletteColor != null },
            primaryPaletteColorId = primaryPaletteColor?.id,
            primaryPaletteColorName = primaryPaletteColor?.name,
            primaryPaletteColorHex = primaryPaletteColor?.hex,
            secondaryRawValue = secondaryPaletteColor?.hex,
            secondaryDisplayLabel = ColorLabelResolver.fromRawValue(secondaryRawColor).takeIf { secondaryPaletteColor != null },
            secondaryPaletteColorId = secondaryPaletteColor?.id,
            secondaryPaletteColorName = secondaryPaletteColor?.name,
            secondaryPaletteColorHex = secondaryPaletteColor?.hex,
        ),
        isFavorite = false,
        isArchived = false,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = now,
    )
}

private fun BatchDraftItem.toEditingClothingItem(
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
): ClothingItem = toClothingItem(availableTags, mainColors).copy(
    updatedAtEpochMillis = createdAtEpochMillis,
)

internal fun BatchDraftItem.toBatchEditItem(
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
): ClothingItem = toEditingClothingItem(availableTags, mainColors)

internal fun BatchDraftItem.toBatchEditPhotoReviewState(): AddEditPhotoReviewState? {
    if (originalPhotoUri.isBlank()) return null
    val needsRetryReview = status == BatchDraftStatus.NeedsReview || status == BatchDraftStatus.Failed
    if (!needsRetryReview) return null
    return AddEditPhotoReviewState(
        captureStatus = PhotoStatus.BackgroundFallback,
        retrySourceUri = originalPhotoUri,
        retrySourceStatus = PhotoStatus.Gallery,
    )
}

internal fun ClothingItem.toBatchDraftItem(
    previous: BatchDraftItem,
): BatchDraftItem = previous.copy(
    name = name,
    notes = notes,
    photoUri = photoUri ?: previous.photoUri,
    selectedTagIds = tags.map(GarmentTag::id),
    fitValue = fitValue,
    selectedPrimaryColorId = colorMetrics.primaryPaletteColorId,
    selectedSecondaryColorId = colorMetrics.secondaryPaletteColorId,
    status = BatchDraftStatus.Ready,
    errorMessage = null,
)

private fun mergePredictedTags(
    currentTagIds: List<String>,
    predictedTagIds: Set<String>,
    availableTags: List<GarmentTag>,
): List<String> {
    if (predictedTagIds.isEmpty()) return currentTagIds
    val tagsById = availableTags.associateBy(GarmentTag::id)
    val hasCurrentCategory = currentTagIds.any { tagId -> tagsById[tagId]?.categoryId == "category" }
    val inferredTagIds = predictedTagIds.filter { tagId ->
        val categoryId = tagsById[tagId]?.categoryId ?: return@filter false
        categoryId in MODEL_PREDICTED_CATEGORIES && (categoryId != "category" || !hasCurrentCategory)
    }
    return (currentTagIds + inferredTagIds).distinct()
}

private fun List<MainColor>.colorForId(id: String?): MainColor? = firstOrNull { color -> color.id == id }

private fun List<MainColor>.nearestColor(rawHex: String?): MainColor? {
    val rgb = RgbColor.fromHexOrNull(rawHex) ?: return null
    return PaletteColorClassifier.Default.nearestColor(this, rgb)?.color
}

internal fun ClothingItem.toBatchDraftFromExisting(
    previous: BatchDraftItem,
    mainColors: List<MainColor>,
): BatchDraftItem = previous.copy(
    name = name,
    notes = notes,
    photoUri = photoUri ?: previous.photoUri,
    selectedTagIds = tags.map(GarmentTag::id),
    fitValue = fitValue,
    selectedPrimaryColorId = colorMetrics.primaryPaletteColorId ?: mainColors.nearestColor(colorMetrics.primaryPaletteColorHex ?: colorMetrics.primaryRawValue)?.id,
    selectedSecondaryColorId = colorMetrics.secondaryPaletteColorId ?: mainColors.nearestColor(colorMetrics.secondaryPaletteColorHex ?: colorMetrics.secondaryRawValue)?.id,
    status = BatchDraftStatus.Ready,
    errorMessage = null,
)

private const val FIT_VALUE_FITS = 2
private const val SECONDARY_COLOR_MIN_RATIO = 0.20f
private const val PHOTO_PREVIEW_MIN_ASPECT_RATIO = 0.66f
private const val PHOTO_PREVIEW_MAX_ASPECT_RATIO = 1.6f
private val MODEL_PREDICTED_CATEGORIES = setOf("category", "season", "occasion")
