package com.gusanitolabs.robia.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.designsystem.aiSurface
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.media.GarmentShareColor
import com.gusanitolabs.robia.media.GarmentShareExporter
import com.gusanitolabs.robia.media.GarmentShareItem
import com.gusanitolabs.robia.media.GarmentShareMetadata
import com.gusanitolabs.robia.media.GarmentShareMetadataIcon
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.SuggestionDiagnostics
import com.gusanitolabs.robia.suggestions.SuggestionService
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DETAIL_DISPLAY_MAX_EDGE_PX = 512

@Composable
private fun GarmentCloudStatusRow(item: UiWardrobeItem) {
    val label = item.garmentCloudStatusLabel()
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            if (item.syncStatus == GarmentSyncStatus.Synced && item.lastSyncedAtEpochMillis != null) {
                Text(
                    stringResource(
                        R.string.garment_sync_last_synced_at,
                        formatEpochMillis(item.lastSyncedAtEpochMillis),
                    ),
                )
            } else if (item.syncStatus in PendingUploadStatuses) {
                Text(stringResource(R.string.sync_status_pending_help))
            } else if (item.parkedAttentionKind() != null) {
                Text(stringResource(R.string.sync_status_parked_help))
            }
        },
        leadingContent = {
            if (item.syncStatus == GarmentSyncStatus.Running) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = item.garmentCloudStatusIcon(),
                    contentDescription = null,
                    tint = item.garmentCloudStatusTint(),
                )
            }
        },
    )
}

/** Saved on this phone and waiting for an automatic upload: no user action needed. */
private val PendingUploadStatuses = setOf(
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.FailedRetryable,
)

/** Parked rows that only an explicit Retry releases (RetryAttention, TS§4.5). */
private fun UiWardrobeItem.parkedAttentionKind(): SyncAttentionKind? = when {
    syncStatus != GarmentSyncStatus.NeedsUserAction -> null
    syncFailureMessage == DRIVE_FULL_REASON -> SyncAttentionKind.DriveFull
    syncFailureMessage == PHOTO_UPLOAD_FAILED_REASON -> SyncAttentionKind.PhotoUploadFailed
    else -> null
}

private fun UiWardrobeItem.canRetryRestoredPhotoNow(now: Long = System.currentTimeMillis()): Boolean =
    hasMissingRestoredPhoto &&
        syncStatus != GarmentSyncStatus.Running &&
        photoRestoreState.hasRetryAttemptsRemaining &&
        (photoRestoreState.retryAfterEpochMillis == null || photoRestoreState.retryAfterEpochMillis <= now) &&
        (photoRestoreState.retryDeadlineEpochMillis == null || photoRestoreState.retryDeadlineEpochMillis >= now)

@Composable
private fun UiWardrobeItem.photoRestoreRetryActionText(now: Long = System.currentTimeMillis()): String = when {
    syncStatus == GarmentSyncStatus.Running -> stringResource(R.string.cloud_restore_retry_photo)
    photoRestoreState.retryExhausted -> stringResource(R.string.cloud_restore_retry_photo_exhausted)
    photoRestoreState.retryDeadlineEpochMillis?.let { it < now } == true -> stringResource(R.string.cloud_restore_retry_photo_expired)
    else -> stringResource(R.string.cloud_restore_retry_photo)
}

@Composable
internal fun ItemDetailScreen(
    innerPadding: PaddingValues,
    item: UiWardrobeItem,
    onEditClick: () -> Unit,
    onRetryRestoredPhoto: (String) -> Unit,
    onKeepWithoutPhoto: (String) -> Unit,
    onRetryAttention: (SyncAttentionKind) -> Unit,
    showSyncDetails: Boolean,
    onSyncDetailsClick: () -> Unit,
    canSeeSimilar: Boolean,
    onSeeSimilarClick: () -> Unit,
    showDiagnostics: Boolean,
    diagnosticsGarment: ClothingItem?,
    diagnosticsTags: List<GarmentTag>,
    suggestionService: SuggestionService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_garment_chooser_title)
    val imageShareErrorMessage = stringResource(R.string.image_share_error)
    val pdfShareErrorMessage = stringResource(R.string.pdf_share_error)
    val noShareAppMessage = stringResource(R.string.share_no_app_error)
    val retryPhotoContentDescription = stringResource(R.string.content_cloud_restore_retry_photo)
    val untitled = stringResource(R.string.browse_similar_untitled)
    val pdfShareItem = item.toGarmentShareItem()
    val app = context.applicationContext as RobiaApplication
    val diagnosticsRecomputeDoneMessage = stringResource(R.string.dev_recompute_embedding_done)
    val diagnosticsRecomputeErrorMessage = stringResource(R.string.dev_recompute_embedding_error)
    var suggestionDiagnostics by remember(item.id) { mutableStateOf<SuggestionDiagnostics?>(null) }
    var suggestionDiagnosticsVersion by remember(item.id) { mutableIntStateOf(0) }
    var suggestionDiagnosticsStatus by remember(item.id) { mutableStateOf<String?>(null) }
    val diagnosticsPhotoUri = diagnosticsGarment?.photoUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    LaunchedEffect(showDiagnostics, diagnosticsGarment, diagnosticsTags, suggestionDiagnosticsVersion) {
        val garment = diagnosticsGarment
        suggestionDiagnostics = if (showDiagnostics && garment != null) {
            val rows = app.database.embeddingDao().getForRecipe(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
            suggestionService.diagnose(garment, diagnosticsTags, rows)
        } else {
            null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = cardTitle(item.name, item.tags, untitled),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            DetailMediaCard(
                item = item,
                onShareImageClick = {
                    item.photoUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)?.let { sourceUri ->
                        scope.launch {
                            val shareUri = runCatching {
                                withContext(Dispatchers.IO) {
                                    GarmentShareExporter.createShareImage(context, sourceUri, item.name)
                                }
                            }.getOrNull()
                            if (shareUri != null) {
                                val launched = context.launchShareChooser(
                                    uri = shareUri,
                                    mimeType = "image/png",
                                    chooserTitle = chooserTitle,
                                )
                                if (!launched) {
                                    Toast.makeText(context, noShareAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, imageShareErrorMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onSharePdfClick = {
                    scope.launch {
                        val shareUri = runCatching {
                            withContext(Dispatchers.IO) {
                                GarmentShareExporter.createSharePdf(context, pdfShareItem)
                            }
                        }.getOrNull()
                        if (shareUri != null) {
                            val launched = context.launchShareChooser(
                                uri = shareUri,
                                mimeType = "application/pdf",
                                chooserTitle = chooserTitle,
                            )
                            if (!launched) {
                                Toast.makeText(context, noShareAppMessage, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, pdfShareErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
        if (canSeeSimilar) {
            item {
                FilledTonalButton(
                    onClick = onSeeSimilarClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aiSurface(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Transparent, contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.item_see_similar))
                }
            }
        }
        item { GarmentCloudStatusRow(item) }
        item.parkedAttentionKind()?.let { kind ->
            item {
                SyncAttentionCard(
                    attention = SyncAttention(kind, listOf(item.id)),
                    onAction = { action -> if (action == SyncAttentionAction.Retry) onRetryAttention(kind) },
                    showDetails = showSyncDetails,
                    onDetails = onSyncDetailsClick,
                    actions = listOf(SyncAttentionAction.Retry),
                )
            }
        }
        if (item.hasMissingRestoredPhoto) {
            item {
                SyncAttentionCard(
                    attention = SyncAttention(SyncAttentionKind.PhotoUnrecoverable, listOf(item.id)),
                    onAction = { action ->
                        if (action == SyncAttentionAction.KeepWithoutPhoto) onKeepWithoutPhoto(item.id)
                    },
                    showDetails = showSyncDetails,
                    onDetails = onSyncDetailsClick,
                    actions = listOf(SyncAttentionAction.KeepWithoutPhoto),
                )
            }
            item {
                val now = System.currentTimeMillis()
                val retryEnabled = item.canRetryRestoredPhotoNow(now)
                val retryStateText = item.photoRestoreRetryStateText(now)
                val retryActionText = item.photoRestoreRetryActionText(now)
                Button(
                    onClick = { if (retryEnabled) onRetryRestoredPhoto(item.id) },
                    enabled = retryEnabled,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = retryPhotoContentDescription
                    },
                ) {
                    if (item.syncStatus == GarmentSyncStatus.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(retryActionText)
                }
                Text(
                    text = retryStateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item.notes.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { ColorMetricsCard(item) }
        item { DetailFieldRows(item = item) }
        if (showDiagnostics && diagnosticsGarment != null) {
            item {
                SuggestionDiagnosticsCard(
                    diagnostics = suggestionDiagnostics,
                    hasPhoto = diagnosticsPhotoUri != null,
                    statusMessage = suggestionDiagnosticsStatus,
                    onRecomputeClick = {
                        diagnosticsPhotoUri?.let { photoUri ->
                            scope.launch {
                                val capture = suggestionService.recomputeImage(photoUri)
                                suggestionDiagnosticsStatus = if (capture == null) {
                                    diagnosticsRecomputeErrorMessage
                                } else {
                                    // Replaces the stored IMAGE vector only now that a fresh one exists; a failed run keeps the old one.
                                    app.embeddingRepository.upsert(
                                        diagnosticsGarment.id,
                                        EmbeddingKind.IMAGE,
                                        capture.imageVector,
                                        capture.imageInputHash,
                                        capture.durationMs,
                                    )
                                    suggestionDiagnosticsVersion++
                                    diagnosticsRecomputeDoneMessage
                                }
                            }
                        }
                    },
                    onExportClick = {
                        diagnosticsPhotoUri?.let { photoUri ->
                            scope.launch {
                                val saved = runCatching {
                                    val encoded = ImageInputEncoder.encode(context, photoUri)
                                    withContext(Dispatchers.IO) { saveDiagnosticsInputPng(context, encoded.bytes) }
                                }
                                suggestionDiagnosticsStatus = context.getString(
                                    if (saved.isSuccess) R.string.developer_export_suggestion_input_saved else R.string.developer_export_suggestion_input_error,
                                )
                            }
                        }
                    },
                )
            }
        }
        item {
            Button(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit))
            }
        }
    }
}

@Composable
private fun DetailMediaCard(
    item: UiWardrobeItem,
    onShareImageClick: () -> Unit,
    onSharePdfClick: () -> Unit,
) {
    val hasPhoto = !item.photoUri.isNullOrBlank()
    val shareDescription = stringResource(R.string.content_share_item)
    var shareMenuExpanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            GarmentPhotoPlaceholder(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                thumbnailMaxEdgePx = DETAIL_DISPLAY_MAX_EDGE_PX,
                purpose = ImagePurpose.Detail,
            )
            if (hasPhoto) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = shareDescription },
                        onClick = { shareMenuExpanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = shareMenuExpanded,
                        onDismissRequest = { shareMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_image)) },
                            onClick = {
                                shareMenuExpanded = false
                                onShareImageClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_pdf)) },
                            onClick = {
                                shareMenuExpanded = false
                                onSharePdfClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorMetricsCard(item: UiWardrobeItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.colors_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorSwatch(
                    role = stringResource(R.string.primary_color),
                    color = item.primaryColor,
                    rawValue = item.primaryRawValue,
                    paletteName = item.primaryPaletteColorName,
                    paletteHex = item.primaryPaletteColorHex,
                )
                ColorSwatch(
                    role = stringResource(R.string.secondary_color),
                    color = item.secondaryColor,
                    rawValue = item.secondaryRawValue,
                    paletteName = item.secondaryPaletteColorName,
                    paletteHex = item.secondaryPaletteColorHex,
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    role: String,
    color: DisplayColorLabel,
    rawValue: String?,
    paletteName: String? = null,
    paletteHex: String? = null,
) {
    val colorValue = paletteHex?.takeIf { it.isNotBlank() } ?: rawValue?.takeIf { it.isNotBlank() }
    val hasStoredColor = !colorValue.isNullOrBlank() || !paletteName.isNullOrBlank()
    val isNoColor = !hasStoredColor
    val swatchColor = colorValue?.toComposeColor() ?: color.swatchColor()
    val displayLabel = when {
        isNoColor -> stringResource(R.string.no_color)
        !paletteName.isNullOrBlank() -> paletteName
        color != DisplayColorLabel.Unknown -> color.localizedLabel()
        else -> stringResource(R.string.color_unknown)
    }
    val swatchDescription = if (isNoColor && role == stringResource(R.string.secondary_color)) {
        stringResource(R.string.no_secondary_color)
    } else {
        displayLabel
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (hasStoredColor) swatchColor else MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .semantics { contentDescription = swatchDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (isNoColor) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = role.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        paletteName?.takeIf { it.isNotBlank() && it != displayLabel }?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailFieldRows(item: UiWardrobeItem) {
    val collator = remember { browseCollator() }
    val none = stringResource(R.string.none)
    fun valueIn(categoryId: String): String = fieldValueText(
        selected = item.tags.filter { tag -> tag.categoryId == categoryId },
        collator = collator,
        none = none,
    ) { tag -> tag.label }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            GarmentFieldRow(
                icon = categoryIconFor("category"),
                label = stringResource(R.string.metadata_category),
                value = valueIn("category"),
                aiSuggested = false,
                onClick = null,
            )
            GarmentFieldRow(
                icon = categoryIconFor("occasion"),
                label = stringResource(R.string.metadata_occasions),
                value = valueIn("occasion"),
                aiSuggested = false,
                onClick = null,
            )
            GarmentFieldRow(
                icon = categoryIconFor("season"),
                label = stringResource(R.string.metadata_season),
                value = valueIn("season"),
                aiSuggested = false,
                onClick = null,
            )
            GarmentFieldRow(
                icon = categoryIconFor("location"),
                label = stringResource(R.string.metadata_location),
                value = valueIn("location"),
                aiSuggested = false,
                onClick = null,
            )
            GarmentFieldRow(
                icon = Icons.Rounded.Straighten,
                label = stringResource(R.string.metadata_fit),
                value = item.fitValue?.fitLabel() ?: none,
                aiSuggested = false,
                onClick = null,
            )
        }
    }
}

private fun Context.launchShareChooser(
    uri: Uri,
    mimeType: String,
    chooserTitle: String,
): Boolean {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(contentResolver, chooserTitle, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val activity = findActivity()
    if (activity == null) {
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        (activity ?: this).startActivity(chooserIntent)
        true
    }.getOrDefault(false)
}

@Composable
private fun UiWardrobeItem.toGarmentShareItem(): GarmentShareItem = GarmentShareItem(
    name = name,
    notes = notes,
    imageUri = Uri.parse(photoUri.orEmpty()),
    metadata = listOf(
        GarmentShareMetadata(
            label = stringResource(R.string.metadata_category),
            values = tags.valuesInCategory("category"),
            icon = GarmentShareMetadataIcon.Category,
        ),
        GarmentShareMetadata(
            label = stringResource(R.string.metadata_season),
            values = tags.valuesInCategory("season"),
            icon = GarmentShareMetadataIcon.Season,
        ),
        GarmentShareMetadata(
            label = stringResource(R.string.metadata_occasions),
            values = tags.valuesInCategory("occasion"),
            icon = GarmentShareMetadataIcon.Occasion,
        ),
        GarmentShareMetadata(
            label = stringResource(R.string.metadata_fit),
            values = fitValue?.fitLabel()?.let { listOf(it) }.orEmpty(),
            icon = GarmentShareMetadataIcon.Fit,
        ),
    ),
    colorSectionLabel = stringResource(R.string.colors_section),
    primaryColor = GarmentShareColor(
        role = stringResource(R.string.primary_color),
        name = shareColorName(primaryColor, primaryPaletteColorName, primaryRawValue),
        hex = primaryPaletteColorHex?.takeIf { it.isNotBlank() } ?: primaryRawValue?.normalizedHexOrNull()?.let { "#$it" },
    ),
    secondaryColor = GarmentShareColor(
        role = stringResource(R.string.secondary_color),
        name = shareColorName(secondaryColor, secondaryPaletteColorName, secondaryRawValue),
        hex = secondaryPaletteColorHex?.takeIf { it.isNotBlank() } ?: secondaryRawValue?.normalizedHexOrNull()?.let { "#$it" },
    ),
    noColorLabel = stringResource(R.string.no_color),
)

@Composable
private fun shareColorName(
    color: DisplayColorLabel,
    paletteName: String?,
    rawValue: String?,
): String {
    val hasStoredColor = !paletteName.isNullOrBlank() || !rawValue.isNullOrBlank() || color != DisplayColorLabel.Unknown
    return when {
        !hasStoredColor -> stringResource(R.string.no_color)
        !paletteName.isNullOrBlank() -> paletteName
        color != DisplayColorLabel.Unknown -> color.localizedLabel()
        else -> stringResource(R.string.color_unknown)
    }
}

private fun List<UiTag>.valuesInCategory(categoryId: String): List<String> =
    filter { tag -> tag.categoryId == categoryId }.map(UiTag::label)

private const val DRIVE_FULL_REASON = "drive_full"

private const val PHOTO_UPLOAD_FAILED_REASON = "photo_upload_failed"

@Composable
private fun DisplayColorLabel.localizedLabel(): String = stringResource(
    when (this) {
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
    },
)

@Composable
private fun Int.fitLabel(): String = when (coerceIn(0, 4)) {
    0 -> stringResource(R.string.fit_does_not_fit)
    1 -> stringResource(R.string.fit_snug)
    2 -> stringResource(R.string.fit_good)
    3 -> stringResource(R.string.fit_relaxed)
    else -> stringResource(R.string.fit_oversized)
}

private fun String.toComposeColor(): Color? = normalizedHexOrNull()?.let { normalized ->
    Color(android.graphics.Color.parseColor("#$normalized"))
}
