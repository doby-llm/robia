package com.gusanitolabs.robia.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
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
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.core.designsystem.pressScale
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.PhotoRestoreState
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.suggestions.BackfillProgress
import com.gusanitolabs.robia.suggestions.SearchMode
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind
import com.gusanitolabs.robia.sync.WardrobeSyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRID_THUMBNAIL_MAX_EDGE_PX = 384

/**
 * Compose skips a card whose item is equal to the one it drew. The list is built once per garment or connection
 * change and never mutated; `tags` stays a List, because no collections library is added for it.
 */
@Immutable
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
    /** False while Drive is not added or disabled: nothing waits for it, so no Waiting badge (R3-DEC-018). */
    val driveEnabled: Boolean = true,
)

internal data class UiTag(
    val id: String,
    val categoryId: String,
    val label: String,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BrowseWardrobeScreen(
    innerPadding: PaddingValues,
    items: List<UiWardrobeItem>,
    isInitialLoading: Boolean,
    hasItemsInWardrobe: Boolean,
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    filters: BrowseFilterState,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onFiltersChange: (BrowseFilterState) -> Unit,
    cloudSync: CloudSyncIndicator,
    selectedItemIds: Set<String>,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onSelectionSet: (Set<String>) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
    attentions: List<SyncAttention>,
    mergeProposalCard: (@Composable () -> Unit)? = null,
    onAttentionAction: (SyncAttention, SyncAttentionAction) -> Unit,
    showSyncDetails: Boolean,
    onSyncDetailsClick: () -> Unit,
    duplicateGroupCount: Int = 0,
    onDuplicateReviewClick: () -> Unit = {},
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMode: SearchMode,
    searchInProgress: Boolean,
    similarToName: String?,
    onClearSimilar: () -> Unit,
    onClearSearch: () -> Unit,
    modelState: ModelState,
    onOpenSettings: () -> Unit,
    browseLayout: BrowseLayout = BrowseLayout.PhotosOnly,
    columns: Int = 2,
    onColumnsChange: (Int) -> Unit = {},
    /** Null hides the Analytics icon in the filter bar. */
    onAnalyticsClick: (() -> Unit)? = null,
) {
    val isSelectionMode = selectedItemIds.isNotEmpty()
    // The sign-in banner is an attention card above the grid; while it shows, cards drop their Waiting and Sign in badges.
    val signInBannerShown = attentions.any { it.kind == SyncAttentionKind.SignInRequired }
    val searchActive = searchQuery.isNotBlank() || similarToName != null
    val gridState = rememberLazyGridState()
    val dragArm = remember { DragSelectArm() }
    val backfill = (LocalContext.current.applicationContext as? RobiaApplication)?.embeddingBackfill
    val backfillProgress by (backfill?.progress ?: remember { MutableStateFlow(BackfillProgress()) }).collectAsState()
    val effectiveColumns = columns.coerceIn(browseColumnRange(browseLayout))
    // Read by the pinch gesture without restarting it: one gesture can change the count several times.
    val currentColumns by rememberUpdatedState(effectiveColumns)
    val currentOnColumnsChange by rememberUpdatedState(onColumnsChange)
    var pinchZoom by remember { mutableFloatStateOf(1f) }
    var pinching by remember { mutableStateOf(false) }
    val pinchFeedback by animateFloatAsState(
        targetValue = if (pinching) pinchZoom.coerceIn(0.94f, 1.06f) else 1f,
        animationSpec = if (LocalReducedMotion.current) snap() else spring(),
        label = "browsePinchFeedback",
    )
    // The first visible item to keep on screen after a pinch changes the column count.
    val scrollAnchor = remember { mutableIntStateOf(-1) }

    // The search field is the grid's first row and the results reorder the whole grid: start each query at the top.
    LaunchedEffect(searchQuery, similarToName) {
        if (searchActive) gridState.scrollToItem(0)
    }
    LaunchedEffect(effectiveColumns) {
        if (scrollAnchor.intValue >= 0) {
            gridState.scrollToItem(scrollAnchor.intValue)
            scrollAnchor.intValue = -1
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(effectiveColumns),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .graphicsLayer {
                scaleX = pinchFeedback
                scaleY = pinchFeedback
            }
            // Two or more fingers only: one finger keeps scrolling, tapping and long-press selection.
            .pointerInput(browseLayout) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Set when the second finger lands: the count the whole gesture is measured from.
                    var startColumns = -1
                    var requested = -1
                    var zoom = 1f
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                if (startColumns < 0) {
                                    startColumns = currentColumns
                                    requested = startColumns
                                }
                                pinching = true
                                zoom *= event.calculateZoom()
                                pinchZoom = pinchResidual(zoom)
                                event.changes.forEach { it.consume() }
                                val target = columnsForPinch(startColumns, zoom, browseColumnRange(browseLayout))
                                if (target != requested) {
                                    requested = target
                                    scrollAnchor.intValue = gridState.firstVisibleItemIndex
                                    currentOnColumnsChange(target)
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        // Runs on lift and on cancellation alike: never leave the feedback scaled.
                        pinching = false
                        pinchZoom = 1f
                    }
                }
            }
            .dragSelect(gridState, dragArm, items, onSelectionSet),
        // innerPadding already carries the bottom bar inset, so the list ends at bar height + 16 dp.
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterBar(
                hasActiveFilters = hasActiveFilters,
                activeFilterCount = activeFilterCount,
                filters = filters,
                availableTags = availableTags,
                paletteColors = mainColors,
                onFiltersChange = onFiltersChange,
                cloudSync = cloudSync,
                onFiltersClick = onFiltersClick,
                onResetFilters = onResetFilters,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchMode = searchMode,
                searchInProgress = searchInProgress,
                resultCount = items.size,
                similarToName = similarToName,
                onClearSimilar = onClearSimilar,
                modelState = modelState,
                onOpenSettings = onOpenSettings,
                onAnalyticsClick = onAnalyticsClick,
            )
        }
        if (backfillProgress.remaining > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SuggestionsProgressBanner(remaining = backfillProgress.remaining)
            }
        }
        if (duplicateGroupCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DuplicatesBanner(groupCount = duplicateGroupCount, onReview = onDuplicateReviewClick)
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
        items(items, key = { it.id }, contentType = { browseCellFor(browseLayout) }) { item ->
            val onClick = {
                if (isSelectionMode) {
                    onSelectionToggle(item.id)
                } else {
                    onItemSelected(item)
                }
            }
            val onLongClick = {
                // The card's long-press still toggles the item; a drag that follows extends from it.
                dragArm.arm(item.id, selectedItemIds)
                onSelectionToggle(item.id)
            }
            val animation = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            when (browseCellFor(browseLayout)) {
                BrowseCell.Tile -> GarmentPhotoTile(
                    item = item,
                    isSelected = item.id in selectedItemIds,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFavoriteClick = { onToggleFavorite(item.id) },
                    modifier = animation,
                    columns = effectiveColumns,
                    signInBannerShown = signInBannerShown,
                )
                BrowseCell.Card -> GarmentGridCard(
                    item = item,
                    isSelected = item.id in selectedItemIds,
                    isSelectionMode = isSelectionMode,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onFavoriteClick = { onToggleFavorite(item.id) },
                    modifier = animation,
                    columns = effectiveColumns,
                    signInBannerShown = signInBannerShown,
                )
            }
        }
    }
}

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
private fun GarmentCardShell(
    item: UiWardrobeItem,
    isSelected: Boolean,
    description: String,
    thumbnailMaxEdgePx: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    signInBannerShown: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val favoriteDescription = stringResource(R.string.content_favorite)
    val favoriteBadge = favoriteBadgeMetrics(columns)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.large,
            )
            .semantics {
                contentDescription = description
                selected = isSelected
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f),
        ) {
            GarmentPhotoPlaceholder(
                item = item,
                modifier = Modifier.matchParentSize(),
                thumbnailMaxEdgePx = thumbnailMaxEdgePx,
                clip = false,
            )
            // The transparent wrapper keeps a 32 dp touch target while the visible heart shrinks with the columns.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(favoriteBadge.outerPaddingDp.dp)
                    .defaultMinSize(minWidth = FAVORITE_HIT_AREA_DP.dp, minHeight = FAVORITE_HIT_AREA_DP.dp)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = favoriteDescription
                        selected = item.isFavorite
                    }
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(favoriteBadge.innerPaddingDp.dp)
                            .size(favoriteBadge.iconSizeDp.dp),
                    )
                }
            }
            GarmentCloudStatusBadge(
                badge = browseCardBadgeFor(
                    status = item.syncStatus,
                    photoNeedsAttention = item.hasMissingRestoredPhoto,
                    driveEnabled = item.driveEnabled,
                    signInBannerShown = signInBannerShown,
                ),
                columns = columns,
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
        footer?.invoke()
    }
}

/** REQ-DU-03: "Possible duplicates found · N groups to review · Review", in the slot of the suggestions banner. */
@Composable
private fun DuplicatesBanner(groupCount: Int, onReview: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.duplicate_banner_groups, groupCount, groupCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text(stringResource(R.string.duplicate_banner_review)) }
        }
    }
}

/** Photos-only tile (default Browse layout): the photo box alone, with title and metadata kept for TalkBack. */
@Composable
private fun GarmentPhotoTile(
    item: UiWardrobeItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    signInBannerShown: Boolean = false,
) {
    val untitled = stringResource(R.string.browse_similar_untitled)
    val collator = remember { browseCollator() }

    GarmentCardShell(
        item = item,
        isSelected = isSelected,
        description = tileContentDescription(item.name, item.tags, untitled, collator),
        thumbnailMaxEdgePx = GRID_THUMBNAIL_MAX_EDGE_PX,
        onClick = onClick,
        onLongClick = onLongClick,
        onFavoriteClick = onFavoriteClick,
        footer = null,
        modifier = modifier,
        columns = columns,
        signInBannerShown = signInBannerShown,
    )
}

@Composable
private fun GarmentGridCard(
    item: UiWardrobeItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    signInBannerShown: Boolean = false,
) {
    val itemDescription = stringResource(R.string.content_open_item_detail)
    val selectDescription = stringResource(R.string.content_select_garment)
    val untitled = stringResource(R.string.browse_similar_untitled)
    val collator = remember { browseCollator() }
    val title = cardTitle(item.name, item.tags, untitled)
    val meta = cardMeta(item.tags, collator)

    GarmentCardShell(
        item = item,
        isSelected = isSelected,
        description = if (isSelectionMode) selectDescription else itemDescription,
        thumbnailMaxEdgePx = GRID_THUMBNAIL_MAX_EDGE_PX,
        onClick = onClick,
        onLongClick = onLongClick,
        onFavoriteClick = onFavoriteClick,
        footer = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    GarmentColorDots(item)
                }
                Text(
                    // A blank line keeps the footer height constant when there are no occasions or seasons.
                    text = meta.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = modifier,
        columns = columns,
        signInBannerShown = signInBannerShown,
    )
}

@Composable
private fun GarmentCloudStatusBadge(
    badge: CardBadge?,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    val reducedMotion = LocalReducedMotion.current
    var shown by remember { mutableStateOf(badge) }
    var shownAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastShown by remember { mutableStateOf(badge) }
    LaunchedEffect(badge) {
        while (true) {
            val now = System.currentTimeMillis()
            val next = visibleBadge(shown, shownAtMs, badge, now)
            if (next != shown) {
                shown = next
                shownAtMs = now
            }
            if (next != null) lastShown = next
            if (shown == badge) break
            delay(shownAtMs + BADGE_HOLD_MILLIS - now)
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reducedMotion) ExitTransition.None else fadeOut(animationSpec = tween(BADGE_FADE_MILLIS)),
    ) {
        lastShown?.let { GarmentCloudBadgePill(it, iconOnly = badgeIconOnly(columns)) }
    }
}

private const val BADGE_FADE_MILLIS = 200

// Smallest touch target for the favourite heart, whatever the column count.
private const val FAVORITE_HIT_AREA_DP = 32

@Composable
private fun GarmentCloudBadgePill(badge: CardBadge, iconOnly: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = if (badge.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = badge.icon,
                // With the label hidden, the icon carries it for TalkBack.
                contentDescription = if (iconOnly) stringResource(badge.labelRes) else null,
                modifier = Modifier.size(14.dp),
            )
            if (!iconOnly) {
                Text(
                    text = stringResource(badge.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Up to two swatches, primary then secondary; unknown colours are skipped. */
@Composable
private fun GarmentColorDots(item: UiWardrobeItem) {
    val swatches = listOf(item.primaryColor, item.secondaryColor)
        .filter { it != DisplayColorLabel.Unknown }
        .take(2)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatches.forEach { color ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color.swatchColor(), CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = .12f), CircleShape),
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
    clip: Boolean = true,
) {
    val swatchColor = item.primaryColor.swatchColor()
    val nearWhite = isNearWhite(item.primaryColor, item.primaryPaletteColorHex ?: item.primaryRawValue)
    Box(
        modifier = modifier
            .then(if (clip) Modifier.clip(MaterialTheme.shapes.large) else Modifier)
            .background(rememberGarmentSwatchBrush(swatchColor, nearWhite)),
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
            item.notes
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
        driveEnabled = syncState.connectionStatus.isDriveEnabled,
    )
}
