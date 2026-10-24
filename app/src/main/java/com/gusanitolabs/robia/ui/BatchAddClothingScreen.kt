package com.gusanitolabs.robia.ui

import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorClassifier
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.color.RgbColor
import com.gusanitolabs.robia.core.model.CanvasOffset
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.CutoutRecipe
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.media.OriginalPhotoStore
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import com.gusanitolabs.robia.media.SegmentationModuleGates
import com.gusanitolabs.robia.media.TransparentCrop
import com.gusanitolabs.robia.media.cutout.atEngineCanvas
import com.gusanitolabs.robia.suggestions.SuggestionCapture
import com.gusanitolabs.robia.suggestions.SuggestionOutcome
import com.gusanitolabs.robia.suggestions.SuggestionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

private const val BATCH_THUMBNAIL_MAX_EDGE_PX = 384

internal data class BatchDraftItem(
    val id: String = UUID.randomUUID().toString(),
    val orderIndex: Int,
    val originalPhotoUri: String,
    val photoUri: String = originalPhotoUri,
    val photoAspectRatio: Float? = null,
    val status: BatchDraftStatus = BatchDraftStatus.Queued,
    val explicitlyAccepted: Boolean = false,
    val errorMessage: String? = null,
    val name: String = "",
    val notes: String = "",
    val selectedTagIds: List<String> = emptyList(),
    val fitValue: Int? = FIT_VALUE_FITS,
    val selectedPrimaryColorId: String? = null,
    val selectedSecondaryColorId: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val suggestionCapture: SuggestionCapture? = null,
    val backgroundFallback: Boolean = false,
    /** The kept original of the cutout, and its recipe (REQ-OR-01/02); null when the photo was not cut out. */
    val originalUri: String? = null,
    val originalRecipe: CutoutRecipe? = null,
    /** The engine's subject file of the cutout, kept until the garment is saved; null when no subject was made. */
    val subjectUri: String? = null,
)

internal enum class BatchDraftStatus(@StringRes val labelRes: Int) {
    Queued(R.string.batch_status_queued),
    Processing(R.string.batch_status_processing),
    NeedsReview(R.string.batch_status_needs_review),
    Ready(R.string.batch_status_ready),
    Failed(R.string.batch_status_failed),
    Interrupted(R.string.batch_status_interrupted),
}

/**
 * The batch list. The cancel request and its discard dialog belong to the app shell, which also draws the top-bar
 * close button: [onRequestCancel] opens the dialog, [onCancelBatch] confirms the discard.
 */
@Composable
internal fun BatchAddClothingScreen(
    innerPadding: PaddingValues,
    drafts: List<BatchDraftItem>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    eta: BatchEta?,
    showDiscardDialog: Boolean,
    onRequestCancel: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDraftSelected: (BatchDraftItem) -> Unit,
    onRetryDraft: (BatchDraftItem) -> Unit,
    onDiscardDraft: (BatchDraftItem) -> Unit,
    onSaveBatch: (Set<String>) -> Unit,
    onCancelBatch: () -> Unit,
    saveStatusMessage: String? = null,
) {
    BackHandler { onRequestCancel() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardDialog,
            title = { Text(stringResource(R.string.batch_discard_title)) },
            text = { Text(stringResource(R.string.batch_discard_body)) },
            confirmButton = {
                TextButton(onClick = onCancelBatch) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscardDialog) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    val processedCount = drafts.count { it.status.isTerminal }
    val needsReviewCount = drafts.count {
        it.status == BatchDraftStatus.NeedsReview && !it.explicitlyAccepted
    }
    val failedCount = drafts.count { it.status == BatchDraftStatus.Failed }
    val interruptedCount = drafts.count { it.status == BatchDraftStatus.Interrupted }
    val acceptedCount = drafts.count(BatchDraftItem::isAcceptedForSave)
    val progress = if (drafts.isEmpty()) 0f else processedCount.toFloat() / drafts.size.toFloat()
    val canSave = drafts.canSaveBatch()
    val saveContentDescription = stringResource(R.string.batch_save_content_description, acceptedCount)
    val saveStateDescription = stringResource(
        if (canSave) R.string.batch_save_enabled_state else R.string.batch_save_disabled_state,
    )
    val attentionLine = batchAttentionLine(needsReviewCount, failedCount, interruptedCount)
    val context = LocalContext.current
    val localize: (GarmentTag) -> String = { tag -> tag.localizedTagLabel(context) }
    val collator = remember { browseCollator() }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned: the progress and ETA stay visible while the rows scroll.
            BatchProgressHeader(
                totalCount = drafts.size,
                processedCount = processedCount,
                progress = progress,
                eta = eta,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = with(density) { bottomBarHeightPx.toDp() } + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (drafts.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.batch_helper_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(drafts, key = BatchDraftItem::id) { draft ->
                    BatchDraftRow(
                        draft = draft,
                        position = draft.orderIndex + 1,
                        totalCount = drafts.size,
                        summary = batchRowSummary(draft, availableTags, localize, collator),
                        onRetry = { onRetryDraft(draft) },
                        onDiscard = { onDiscardDraft(draft) },
                        onClick = {
                            if (draft.status.isSelectable) {
                                onDraftSelected(draft)
                            }
                        },
                    )
                }
            }
        }
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (attentionLine.isNotEmpty()) {
                    Text(
                        text = attentionLine
                            .map { (pluralRes, count) -> pluralStringResource(pluralRes, count, count) }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                saveStatusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = { onSaveBatch(drafts.filter(BatchDraftItem::isAcceptedForSave).map { it.id }.toSet()) },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = saveContentDescription
                            stateDescription = saveStateDescription
                        },
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.batch_save_items, acceptedCount))
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
    eta: BatchEta?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.fillMaxWidth(),
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
                text = stringResource(R.string.batch_processed_count, processedCount, totalCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatchProgressBar(progress = progress, modifier = Modifier.weight(1f))
                eta?.let { BatchEtaText(it) }
            }
        }
    }
}

@Composable
private fun BatchProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "batchProgress",
    )
    Box(
        modifier = modifier
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
private fun BatchEtaText(eta: BatchEta) {
    val text = when (eta) {
        BatchEta.AlmostDone -> stringResource(R.string.batch_eta_almost_done)
        is BatchEta.Seconds -> stringResource(R.string.batch_eta_seconds, eta.s)
        is BatchEta.Minutes -> stringResource(R.string.batch_eta_minutes, eta.m)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun BatchDraftRow(
    draft: BatchDraftItem,
    position: Int,
    totalCount: Int,
    summary: String,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val status = if (draft.status == BatchDraftStatus.NeedsReview && draft.explicitlyAccepted) {
        stringResource(R.string.batch_status_original_accepted)
    } else {
        stringResource(draft.status.labelRes)
    }
    val rowDescription = stringResource(R.string.batch_tile_content_description, position, totalCount, status)
    val summaryIsError = draft.status == BatchDraftStatus.Failed && draft.errorMessage != null
    val backgroundPreparing by SegmentationModuleGates.get(LocalContext.current).preparing.collectAsState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = rowDescription
                stateDescription = status
            }
            .alpha(if (draft.status == BatchDraftStatus.Queued) 0.62f else 1f)
            .clickable(
                enabled = draft.status.isSelectable,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatchPhotoPreview(
                photoUri = draft.photoUri,
                sourceRevision = draft.id,
                isProcessing = draft.status == BatchDraftStatus.Processing,
                modifier = Modifier.size(width = 72.dp, height = 88.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.batch_row_position, position, totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    BatchStatusBadge(
                        status = draft.status,
                        isAccepted = draft.isAcceptedForSave(),
                        backgroundPreparing = backgroundPreparing,
                    )
                }
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summaryIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (draft.isRetryableForBatch()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.batch_retry)) }
                        TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_changes)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchPhotoPreview(
    photoUri: String,
    sourceRevision: String,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
) {
    Box(
        modifier = modifier
            .clip(shape)
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
            BoundedGarmentImage(
                photoUri = photoUri,
                thumbnailMaxEdgePx = BATCH_THUMBNAIL_MAX_EDGE_PX,
                purpose = ImagePurpose.BatchPreview,
                sourceRevision = sourceRevision,
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
            SweepOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun BatchStatusBadge(
    status: BatchDraftStatus,
    isAccepted: Boolean,
    backgroundPreparing: Boolean,
    modifier: Modifier = Modifier,
) {
    val visualStatus = if (isAccepted) BatchDraftStatus.Ready else status
    val containerColor = when (visualStatus) {
        BatchDraftStatus.Ready -> MaterialTheme.colorScheme.primaryContainer
        BatchDraftStatus.Failed, BatchDraftStatus.Interrupted -> MaterialTheme.colorScheme.errorContainer
        BatchDraftStatus.NeedsReview -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val contentColor = when (visualStatus) {
        BatchDraftStatus.Ready -> MaterialTheme.colorScheme.onPrimaryContainer
        BatchDraftStatus.Failed, BatchDraftStatus.Interrupted -> MaterialTheme.colorScheme.onErrorContainer
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
            when (visualStatus) {
                BatchDraftStatus.Ready -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                BatchDraftStatus.Failed, BatchDraftStatus.Interrupted -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                BatchDraftStatus.NeedsReview -> Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp))
                else -> Unit
            }
            Text(
                text = if (backgroundPreparing && visualStatus == BatchDraftStatus.Processing) {
                    stringResource(R.string.background_removal_preparing)
                } else {
                    stringResource(visualStatus.labelRes)
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** Output of the cutout stage (background removal, crop, aspect, palette), before any suggestion. */
internal sealed interface CutoutStageResult {
    data class Ready(
        val croppedUri: String,
        val displayAspectRatio: Float?,
        val primaryColorId: String?,
        val secondaryColorId: String?,
        val usedFallback: Boolean,
        val needsOriginalFallbackReview: Boolean,
        val originalUri: String? = null,
        val originalRecipe: CutoutRecipe? = null,
        val subjectUri: String? = null,
    ) : CutoutStageResult

    data class Failed(val message: String?) : CutoutStageResult
}

internal suspend fun cutoutStage(
    draft: BatchDraftItem,
    context: android.content.Context,
    backgroundRemover: PhotoBackgroundRemover,
    mainColors: List<MainColor>,
): CutoutStageResult {
    // The kept original is written before the crop; a stage that ends without a Ready result deletes it (see the catches).
    var keptOriginal: Uri? = null
    // The subject is the crop's input. It is kept until the garment is saved (released on save, like the raw copy), so it
    // is deleted here only when the stage ends without a Ready result.
    var subjectFile: Uri? = null
    return try {
        val sourceUri = Uri.parse(draft.originalPhotoUri)
        val sourceAspectRatio = withContext(Dispatchers.IO) {
            ClothingImageStore.readImageAspectRatio(context, sourceUri)
        }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO)
        val backgroundResult = backgroundRemover.removeBackground(context, sourceUri)
        keptOriginal = withContext(NonCancellable + Dispatchers.IO) { OriginalPhotoStore.saveAndRecycle(context, backgroundResult.inputFrame) }
        subjectFile = backgroundResult.outputUri.takeUnless { backgroundResult.usedFallback }
        // Not cancellable: the crop writes its file once. A cancel after this point is seen by the inner catch, which deletes it.
        val crop = fallbackUnlessCancelled(TransparentCrop(backgroundResult.outputUri, CanvasOffset(0, 0))) {
            withContext(NonCancellable + Dispatchers.IO) { ClothingImageStore.cropTransparentPixels(context, backgroundResult.outputUri) }
        }
        val croppedUri = crop.uri
        try {
            val displayAspectRatio = withContext(Dispatchers.IO) {
                ClothingImageStore.readImageAspectRatio(context, croppedUri)
            }?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO) ?: sourceAspectRatio
            val matches = fallbackUnlessCancelled(emptyList<PaletteColorMatch>()) {
                withContext(Dispatchers.IO) { ClothingImageStore.extractPaletteColorDiagnostics(context, croppedUri, mainColors).matches }
            }
            val primaryColorId = matches.getOrNull(0)?.color?.id
            val secondaryColorId = matches.drop(1)
                .firstOrNull { match -> match.ratio >= SECONDARY_COLOR_MIN_RATIO && match.color.id != primaryColorId }
                ?.color
                ?.id
            // The raw copy and the subject are released once the garment is saved; a crop fallback is the raw copy or the
            // subject itself, which the name check keeps.
            ClothingImageStore.registerPhotoFilesForRelease(
                context,
                croppedUri.toString(),
                listOfNotNull(sourceUri.toString(), subjectFile?.toString()),
            )
            CutoutStageResult.Ready(
                croppedUri = croppedUri.toString(),
                displayAspectRatio = displayAspectRatio,
                primaryColorId = primaryColorId,
                secondaryColorId = secondaryColorId,
                usedFallback = backgroundResult.usedFallback,
                needsOriginalFallbackReview = backgroundResult.usedFallback || croppedUri == sourceUri,
                originalUri = keptOriginal?.toString(),
                // The recipe maps the stored cutout back to the kept frame: the engine's crop, then the crop just made (REQ-OR-05).
                originalRecipe = backgroundResult.recipe?.takeIf { keptOriginal != null }?.atEngineCanvas(crop.offset),
                subjectUri = subjectFile?.toString(),
            )
        } catch (cancellation: CancellationException) {
            // A cancelled stage hands no result to the batch, so the cropped output it made is deleted here. A crop fallback
            // is the raw copy the draft still references, so it stays.
            if (croppedUri != sourceUri) {
                withContext(NonCancellable + Dispatchers.IO) {
                    ClothingImageStore.deleteBatchDraftFiles(context, listOf(croppedUri.toString()))
                }
            }
            throw cancellation
        }
    } catch (throwable: CancellationException) {
        deleteStageFiles(context, keptOriginal, subjectFile)
        throw throwable
    } catch (throwable: Exception) {
        deleteStageFiles(context, keptOriginal, subjectFile)
        CutoutStageResult.Failed(throwable.message?.takeIf { it.isNotBlank() })
    }
}

/** Deletes the files a stage made when it ends without handing them to the batch: the kept original and the subject. */
private suspend fun deleteStageFiles(context: android.content.Context, keptOriginal: Uri?, subjectFile: Uri?) {
    val uris = listOfNotNull(keptOriginal, subjectFile).map(Uri::toString)
    if (uris.isEmpty()) return
    withContext(NonCancellable + Dispatchers.IO) {
        ClothingImageStore.deleteBatchDraftFiles(context, uris)
    }
}

/**
 * Runs an optional cutout step (crop, palette) and falls back on failure. Unlike runCatching, a cancellation
 * always propagates, so a cancelled batch never publishes a fallback draft.
 */
internal inline fun <T> fallbackUnlessCancelled(fallback: T, block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    fallback
}

internal suspend fun suggestStage(
    draft: BatchDraftItem,
    cutout: CutoutStageResult.Ready,
    suggestionService: SuggestionService,
    availableTags: List<GarmentTag>,
    context: android.content.Context,
): BatchDraftItem = try {
    val suggestion = suggestionService.suggestForPhoto(
        photoUri = Uri.parse(cutout.croppedUri),
        tags = availableTags,
        selectedCategoryId = availableTags.firstOrNull { tag -> tag.categoryId == "category" && tag.id in draft.selectedTagIds }?.id,
    )
    val resolvedStatus = if (cutout.needsOriginalFallbackReview) BatchDraftStatus.NeedsReview else BatchDraftStatus.Ready
    val resolvedMessage = if (cutout.needsOriginalFallbackReview) {
        context.getString(R.string.batch_original_fallback_message)
    } else {
        null
    }
    val ready = suggestion as? SuggestionOutcome.Ready
    draft.copy(
        photoUri = cutout.croppedUri,
        photoAspectRatio = cutout.displayAspectRatio,
        originalUri = cutout.originalUri,
        originalRecipe = cutout.originalRecipe,
        subjectUri = cutout.subjectUri,
        status = resolvedStatus,
        selectedPrimaryColorId = cutout.primaryColorId,
        selectedSecondaryColorId = cutout.secondaryColorId,
        selectedTagIds = if (ready == null) {
            draft.selectedTagIds
        } else {
            mergePredictedTags(draft.selectedTagIds, ready.predictedTagIds, availableTags)
        },
        suggestionCapture = ready?.let { outcome ->
            SuggestionCapture(cutout.croppedUri, outcome.imageVector, outcome.imageInputHash, outcome.durationMs)
        },
        errorMessage = resolvedMessage,
        backgroundFallback = cutout.usedFallback,
    )
} catch (throwable: CancellationException) {
    throw throwable
} catch (throwable: Exception) {
    // The failed draft does not reference the cutout, so the subject it kept for a save is deleted here.
    cutout.subjectUri?.let { subject ->
        withContext(NonCancellable + Dispatchers.IO) { ClothingImageStore.deleteBatchDraftFiles(context, listOf(subject)) }
    }
    draft.copy(
        status = BatchDraftStatus.Failed,
        errorMessage = throwable.message?.takeIf { it.isNotBlank() },
    )
}

private val BatchDraftStatus.isSelectable: Boolean
    get() = this == BatchDraftStatus.Ready || this == BatchDraftStatus.NeedsReview

private val BatchDraftStatus.isRetryable: Boolean
    get() = this == BatchDraftStatus.Failed || this == BatchDraftStatus.Interrupted

private val BatchDraftStatus.isTerminal: Boolean
    get() = this != BatchDraftStatus.Queued && this != BatchDraftStatus.Processing

internal fun BatchDraftItem.isAcceptedForSave(): Boolean =
    status == BatchDraftStatus.Ready ||
        (status == BatchDraftStatus.NeedsReview && explicitlyAccepted)

/**
 * The image files this draft owns: its raw copy, the photo it shows now (the same file until a cutout replaces it), the
 * kept original of that cutout and its subject file. A discarded or cancelled draft deletes all of them.
 */
internal fun BatchDraftItem.tempFileUris(): List<String> = listOfNotNull(originalPhotoUri, photoUri, originalUri, subjectUri).distinct()

internal fun BatchDraftItem.isProcessingActive(): Boolean =
    status == BatchDraftStatus.Queued || status == BatchDraftStatus.Processing

internal fun BatchDraftItem.interruptedForBatch(message: String): BatchDraftItem? =
    if (isProcessingActive()) {
        copy(
            status = BatchDraftStatus.Interrupted,
            errorMessage = message,
        )
    } else {
        null
    }

internal fun List<BatchDraftItem>.canSaveBatch(): Boolean =
    none { it.isProcessingActive() } && any { it.isAcceptedForSave() }

internal fun List<BatchDraftItem>.acceptedForSave(requestedDraftIds: Set<String>): List<BatchDraftItem> =
    filter { draft -> draft.id in requestedDraftIds && draft.isAcceptedForSave() }

internal fun BatchDraftItem.isRetryableForBatch(): Boolean =
    status.isRetryable || (status == BatchDraftStatus.NeedsReview && backgroundFallback)

internal fun BatchDraftItem.retryForBatch(): BatchDraftItem = if (isRetryableForBatch()) {
    copy(
        status = BatchDraftStatus.Queued,
        explicitlyAccepted = false,
        errorMessage = null,
        backgroundFallback = false,
    )
} else {
    this
}

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
        originalUri = originalUri,
        originalRecipe = originalRecipe,
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
    if (status != BatchDraftStatus.NeedsReview) return null
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
    originalUri = originalUri,
    originalRecipe = originalRecipe,
    selectedTagIds = tags.map(GarmentTag::id),
    fitValue = fitValue,
    selectedPrimaryColorId = colorMetrics.primaryPaletteColorId,
    selectedSecondaryColorId = colorMetrics.secondaryPaletteColorId,
    status = previous.status,
    explicitlyAccepted = previous.explicitlyAccepted,
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
    acceptOriginalPhoto: Boolean = false,
): BatchDraftItem = previous.copy(
    name = name,
    notes = notes,
    photoUri = photoUri ?: previous.photoUri,
    originalUri = originalUri,
    originalRecipe = originalRecipe,
    selectedTagIds = tags.map(GarmentTag::id),
    fitValue = fitValue,
    selectedPrimaryColorId = colorMetrics.primaryPaletteColorId ?: mainColors.nearestColor(colorMetrics.primaryPaletteColorHex ?: colorMetrics.primaryRawValue)?.id,
    selectedSecondaryColorId = colorMetrics.secondaryPaletteColorId ?: mainColors.nearestColor(colorMetrics.secondaryPaletteColorHex ?: colorMetrics.secondaryRawValue)?.id,
    status = previous.status,
    explicitlyAccepted = previous.explicitlyAccepted ||
        (previous.status == BatchDraftStatus.NeedsReview && acceptOriginalPhoto),
    errorMessage = null,
)

private const val FIT_VALUE_FITS = 2
private const val SECONDARY_COLOR_MIN_RATIO = 0.20f
private const val PHOTO_PREVIEW_MIN_ASPECT_RATIO = 0.66f
private const val PHOTO_PREVIEW_MAX_ASPECT_RATIO = 1.6f
private val MODEL_PREDICTED_CATEGORIES = setOf("category", "occasion")
