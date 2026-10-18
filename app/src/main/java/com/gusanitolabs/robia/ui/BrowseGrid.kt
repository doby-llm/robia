package com.gusanitolabs.robia.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.PhotoRestoreState
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.media.ImagePipeline
import com.gusanitolabs.robia.media.ImagePriority
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.media.ImageRequest
import com.gusanitolabs.robia.media.ImageTargetBounds
import com.gusanitolabs.robia.suggestions.BackfillProgress
import com.gusanitolabs.robia.suggestions.SearchMode
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.WardrobeSyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRID_THUMBNAIL_MAX_EDGE_PX = 384

internal data class UiWardrobeItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val notes: String,
    val photoUri: String?,
    val tags: List<UiTag>,
    val fitValue: Int?,
    val primaryColor: DisplayColorLabel,
    val primaryRawValue: String?,
    val primaryPaletteColorId: String?,
    val primaryPaletteColorName: String?,
    val primaryPaletteColorHex: String?,
    val secondaryColor: DisplayColorLabel,
    val secondaryRawValue: String?,
    val secondaryPaletteColorId: String?,
    val secondaryPaletteColorName: String?,
    val secondaryPaletteColorHex: String?,
    val isFavorite: Boolean,
    val syncStatus: GarmentSyncStatus,
    val syncDirtyAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
    val syncFailureMessage: String?,
    val photoRestoreState: PhotoRestoreState,
    /** Offline: the pending garment waits for the connection, shown instead of "Pending upload". */
    val waitingForConnection: Boolean = false,
)

internal data class UiTag(
    val id: String,
    val categoryId: String,
    val label: String,
)

@Composable
internal fun BrowseWardrobeScreen(
    innerPadding: PaddingValues,
    items: List<UiWardrobeItem>,
    isInitialLoading: Boolean,
    hasItemsInWardrobe: Boolean,
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    cloudSync: CloudSyncIndicator,
    selectedItemIds: Set<String>,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
    attentions: List<SyncAttention>,
    mergeProposalCard: (@Composable () -> Unit)? = null,
    onAttentionAction: (SyncAttention, SyncAttentionAction) -> Unit,
    showSyncDetails: Boolean,
    onSyncDetailsClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMode: SearchMode,
    searchInProgress: Boolean,
    similarToName: String?,
    onClearSimilar: () -> Unit,
    onClearSearch: () -> Unit,
    modelState: ModelState,
    onOpenSettings: () -> Unit,
) {
    val isSelectionMode = selectedItemIds.isNotEmpty()
    val searchActive = searchQuery.isNotBlank() || similarToName != null
    val gridState = rememberLazyGridState()
    val backfill = (LocalContext.current.applicationContext as? RobiaApplication)?.embeddingBackfill
    val backfillProgress by (backfill?.progress ?: remember { MutableStateFlow(BackfillProgress()) }).collectAsState()

    // The search field is the grid's first row and the results reorder the whole grid: start each query at the top.
    LaunchedEffect(searchQuery, similarToName) {
        if (searchActive) gridState.scrollToItem(0)
    }
    BrowseImagePrefetch(items = items, gridState = gridState)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterBar(
                hasActiveFilters = hasActiveFilters,
                activeFilterCount = activeFilterCount,
                cloudSync = cloudSync,
                onFiltersClick = onFiltersClick,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchMode = searchMode,
                searchInProgress = searchInProgress,
                resultCount = items.size,
                similarToName = similarToName,
                onClearSimilar = onClearSimilar,
                modelState = modelState,
                onOpenSettings = onOpenSettings,
            )
        }
        if (backfillProgress.remaining > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SuggestionsProgressBanner(remaining = backfillProgress.remaining)
            }
        }
        mergeProposalCard?.let { card ->
            item(span = { GridItemSpan(maxLineSpan) }) { card() }
        }
        if (items.isNotEmpty()) {
            attentions.forEach { attention ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SyncAttentionCard(
                        attention = attention,
                        onAction = { action -> onAttentionAction(attention, action) },
                        showDetails = showSyncDetails,
                        onDetails = onSyncDetailsClick,
                    )
                }
            }
        }
        if (isInitialLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseWardrobeLoadingCard()
            }
        } else if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (hasItemsInWardrobe && (hasActiveFilters || searchActive)) {
                    EmptyFiltersCard(onResetFilters = {
                        onResetFilters()
                        onClearSearch()
                    })
                } else {
                    EmptyStateCard(onAddClick = onAddClick)
                }
            }
        }
        items(items, key = { it.id }) { item ->
            GarmentGridCard(
                item = item,
                isSelected = item.id in selectedItemIds,
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (isSelectionMode) {
                        onSelectionToggle(item.id)
                    } else {
                        onItemSelected(item)
                    }
                },
                onLongClick = { onSelectionToggle(item.id) },
                onFavoriteClick = { onToggleFavorite(item.id) },
            )
        }
    }
}

@Composable
private fun BrowseImagePrefetch(
    items: List<UiWardrobeItem>,
    gridState: LazyGridState,
) {
    val context = LocalContext.current
    val pipeline = remember(context) { ImagePipeline.shared(context) }
    LaunchedEffect(pipeline, items, gridState) {
        try {
            var previousFirstIndex = 0
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo
                    .map { it.index }
                    .let { visible ->
                        visible.minOrNull().orDefault(0) to visible.maxOrNull().orDefault(-1)
                    }
            }.collectLatest { (firstIndex, lastIndex) ->
                if (lastIndex < 0) return@collectLatest
                if (firstIndex != previousFirstIndex) {
                    pipeline.cancelPrefetch()
                }
                val movingForward = firstIndex >= previousFirstIndex
                previousFirstIndex = firstIndex
                val gridIndices = if (movingForward) {
                    (lastIndex + 1)..(lastIndex + GRID_PREFETCH_ITEM_COUNT)
                } else {
                    (firstIndex - GRID_PREFETCH_ITEM_COUNT until firstIndex).reversed()
                }
                gridIndices
                    .map { it - 1 } // index zero is the full-width filter row
                    .filter { it in items.indices }
                    .take(GRID_PREFETCH_ITEM_COUNT)
                    .mapNotNull { items.getOrNull(it) }
                    .mapNotNull { item ->
                        val uri = item.photoUri?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                        ImageRequest(
                            sourceUri = Uri.parse(uri),
                            purpose = ImagePurpose.Browse,
                            targetBounds = ImageTargetBounds(
                                GRID_THUMBNAIL_MAX_EDGE_PX * 3 / 4,
                                GRID_THUMBNAIL_MAX_EDGE_PX,
                            ),
                            sourceRevision = "${item.id}:$uri",
                            priority = ImagePriority.Prefetch,
                            maxEdgePx = GRID_THUMBNAIL_MAX_EDGE_PX,
                        )
                    }
                    .forEach(pipeline::prefetch)
            }
        } finally {
            pipeline.cancelPrefetch()
        }
    }
}

private fun Int?.orDefault(default: Int): Int = this ?: default

private const val GRID_PREFETCH_ITEM_COUNT = 6

@Composable
private fun BrowseWardrobeLoadingCard() {
    val loadingDescription = stringResource(R.string.content_browse_wardrobe_loading)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .semantics { contentDescription = loadingDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.browse_wardrobe_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GarmentGridCard(
    item: UiWardrobeItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val itemDescription = stringResource(R.string.content_open_item_detail)
    val selectDescription = stringResource(R.string.content_select_garment)
    val favoriteDescription = stringResource(R.string.content_favorite)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .semantics {
                contentDescription = if (isSelectionMode) selectDescription else itemDescription
                selected = isSelected
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            GarmentPhotoPlaceholder(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                thumbnailMaxEdgePx = GRID_THUMBNAIL_MAX_EDGE_PX,
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .semantics {
                        contentDescription = favoriteDescription
                        selected = item.isFavorite
                    }
                    .clickable(onClick = onFavoriteClick),
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(6.dp),
                )
            }
            GarmentCloudStatusBadge(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.tags.take(2).forEach { tag -> TonalTag(text = tag.label) }
            }
        }
    }
}

@Composable
private fun GarmentCloudStatusBadge(
    item: UiWardrobeItem,
    modifier: Modifier = Modifier,
) {
    val label = item.garmentCloudStatusLabel()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = item.garmentCloudStatusTint(),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        if (item.syncStatus == GarmentSyncStatus.Running) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(7.dp)
                    .size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = item.garmentCloudStatusIcon(),
                contentDescription = null,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}

internal fun UiWardrobeItem.garmentCloudStatusIcon(): ImageVector = when (syncStatus) {
    GarmentSyncStatus.LocalOnly,
    GarmentSyncStatus.AuthBlocked -> Icons.Rounded.CloudOff
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.Running -> Icons.Rounded.CloudUpload
    GarmentSyncStatus.Synced -> Icons.Rounded.CloudDone
    GarmentSyncStatus.FailedRetryable -> Icons.Rounded.CloudUpload
    GarmentSyncStatus.NeedsUserAction -> Icons.Rounded.Error
}

@Composable
internal fun UiWardrobeItem.garmentCloudStatusTint(): Color = when (syncStatus) {
    GarmentSyncStatus.Synced -> MaterialTheme.colorScheme.primary
    GarmentSyncStatus.NeedsUserAction,
    GarmentSyncStatus.AuthBlocked -> MaterialTheme.colorScheme.error
    GarmentSyncStatus.FailedRetryable,
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.Running -> MaterialTheme.colorScheme.tertiary
    GarmentSyncStatus.LocalOnly -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun GarmentPhotoPlaceholder(
    item: UiWardrobeItem,
    modifier: Modifier = Modifier,
    thumbnailMaxEdgePx: Int? = GRID_THUMBNAIL_MAX_EDGE_PX,
    purpose: ImagePurpose = ImagePurpose.Browse,
) {
    val swatchColor = item.primaryColor.swatchColor()
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(rememberGarmentSwatchBrush(swatchColor)),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf { it.isNotBlank() }
        if (photoUri != null) {
            BoundedGarmentImage(
                photoUri = photoUri,
                thumbnailMaxEdgePx = thumbnailMaxEdgePx,
                purpose = purpose,
                sourceRevision = "${item.id}:$photoUri",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Style,
                        contentDescription = null,
                        tint = swatchColor,
                        modifier = Modifier.size(56.dp),
                    )
                }
                if (item.hasMissingRestoredPhoto) {
                    Text(
                        text = stringResource(R.string.cloud_restore_photo_missing_message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal val UiWardrobeItem.hasMissingRestoredPhoto: Boolean
    get() = photoRestoreState.needsAttention

@Composable
private fun TonalTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun EmptyFiltersCard(onResetFilters: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = stringResource(R.string.empty_filters_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.empty_filters_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onResetFilters) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reset_filters))
            }
        }
    }
}

internal fun List<ClothingItem>.toUiWardrobeItems(
    syncState: WardrobeSyncState,
    online: Boolean,
    context: Context,
): List<UiWardrobeItem> = map { item ->
    val cloud = item.syncStatus.resolveForConnection(syncState.connectionStatus, online)
    UiWardrobeItem(
        id = item.id,
        name = item.name,
        subtitle = if (item.photoRestoreState.needsAttention) {
            context.getString(R.string.cloud_restore_photo_missing_message)
        } else {
            item.notes.ifBlank { context.getString(cloud.labelRes) }
        },
        notes = item.notes,
        photoUri = item.photoUri,
        tags = item.tags.map { tag -> UiTag(tag.id, tag.categoryId, tag.localizedTagLabel(context)) },
        fitValue = item.fitValue,
        primaryColor = item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
        primaryRawValue = item.colorMetrics.primaryRawValue,
        primaryPaletteColorId = item.colorMetrics.primaryPaletteColorId,
        primaryPaletteColorName = item.colorMetrics.primaryPaletteColorName,
        primaryPaletteColorHex = item.colorMetrics.primaryPaletteColorHex,
        secondaryColor = item.colorMetrics.secondaryDisplayLabel ?: DisplayColorLabel.Unknown,
        secondaryRawValue = item.colorMetrics.secondaryRawValue,
        secondaryPaletteColorId = item.colorMetrics.secondaryPaletteColorId,
        secondaryPaletteColorName = item.colorMetrics.secondaryPaletteColorName,
        secondaryPaletteColorHex = item.colorMetrics.secondaryPaletteColorHex,
        isFavorite = item.isFavorite,
        syncStatus = cloud.status,
        syncDirtyAtEpochMillis = item.syncDirtyAtEpochMillis,
        lastSyncedAtEpochMillis = item.lastSyncedAtEpochMillis,
        syncFailureMessage = item.syncFailureMessage,
        photoRestoreState = item.photoRestoreState,
        waitingForConnection = cloud.waitingForConnection,
    )
}
