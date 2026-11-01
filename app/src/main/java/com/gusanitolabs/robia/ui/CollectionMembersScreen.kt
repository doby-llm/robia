package com.gusanitolabs.robia.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.designsystem.extras
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.COLLECTION_CATEGORY_ID
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.data.GarmentVectors
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.QueryEmbedding
import com.gusanitolabs.robia.suggestions.SearchController
import com.gusanitolabs.robia.suggestions.SearchMode
import com.gusanitolabs.robia.suggestions.SearchResult
import com.gusanitolabs.robia.suggestions.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Edge zones and frame step of the paint-drag autoscroll; the same values as Browse's drag selection. */
private val PAINT_EDGE_ZONE = 64.dp
private val PAINT_MAX_STEP = 24.dp
private const val PAINT_FRAME_MS = 16L

/** The sync indicator is idle here: the members screen shows no cloud status (the list cards still show their own). */
private val IdleCloudSync = CloudSyncIndicator(headline = CloudSyncHeadline.Synced, pendingCount = 0, attention = false)

/** Survives rotation with the draft; process death drops it, which is fine because nothing was written. */
private val membersSessionSaver = listSaver<CollectionMembersSession, String>(
    save = { session -> session.toSaveable() },
    restore = { saved -> CollectionMembersSession.fromSaveable(saved) },
)

/** The long-pressed garment and the draft before that press: the anchor of one paint-drag. */
private class MembersDragArm {
    var anchorId: String? = null
    var before: Set<String> = emptySet()

    fun arm(id: String, draftBefore: Set<String>) {
        anchorId = id
        before = draftBefore
    }

    fun disarm() {
        anchorId = null
        before = emptySet()
    }
}

/**
 * Members of one collection (R4-D10..D16, mockup frames 3-6). A Browse-like picker with its own filters, search and
 * drag arm: Browse selection and filters are never read or changed here. Nothing is written except by [onSave].
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionMembersScreen(
    innerPadding: PaddingValues,
    collection: GarmentTag,
    items: List<UiWardrobeItem>,
    domainItems: List<ClothingItem>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    vectors: Map<String, GarmentVectors>,
    modelState: ModelState,
    isInitialLoading: Boolean,
    browseLayout: BrowseLayout,
    columns: Int,
    signInBannerShown: Boolean,
    onOpenSettings: () -> Unit,
    onSave: (add: Set<String>, remove: Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as RobiaApplication
    val haptic = LocalHapticFeedback.current
    val filterTags = remember(availableTags) { availableTags.filterNot { tag -> tag.categoryId == COLLECTION_CATEGORY_ID } }
    val effectiveColumns = columns.coerceIn(browseColumnRange(browseLayout))

    // The draft is rememberSaveable so a rotation keeps it. It is read once the wardrobe has loaded (see below).
    var session by rememberSaveable(collection.id, stateSaver = membersSessionSaver) {
        mutableStateOf(CollectionMembersSession.open(collection.id, emptySet()))
    }
    var loaded by rememberSaveable(collection.id) { mutableStateOf(false) }
    var filters by remember { mutableStateOf(BrowseFilterState()) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val paintArm = remember { MembersDragArm() }
    val gridState = rememberLazyGridState()

    // The first non-empty load opens the draft at the saved membership. Later loads only prune garments that vanished,
    // so an empty list before the first load cannot wipe the draft.
    LaunchedEffect(collection.id, items) {
        if (!loaded) {
            if (items.isEmpty()) return@LaunchedEffect
            session = CollectionMembersSession.open(collection.id, memberIdsIn(items, collection.id))
            loaded = true
        } else {
            session = session.pruned(items.mapTo(HashSet(), UiWardrobeItem::id))
        }
    }

    // Own search, like Browse's: its own scope (cancelled when this screen leaves) and its own cache.
    val searchScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    DisposableEffect(searchScope) {
        onDispose { searchScope.cancel() }
    }
    val searchController = remember(app, searchScope) {
        SearchController(
            embedQuery = { text -> app.embeddingEngineHolder.embedText(EmbeddingRecipe.QUERY_PREFIX + text) },
            scope = searchScope,
        )
    }
    val engineAvailable = modelState is ModelState.Ready
    val trimmedQuery = query.trim()
    val searchActive = query.isNotBlank()
    var queryEmbedding by remember { mutableStateOf<QueryEmbedding?>(null) }
    var searchInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(trimmedQuery, engineAvailable) {
        queryEmbedding = null
        if (trimmedQuery.isEmpty()) return@LaunchedEffect
        searchInProgress = true
        try {
            queryEmbedding = QueryEmbedding(trimmedQuery, searchController.queryVector(trimmedQuery, engineAvailable))
        } finally {
            searchInProgress = false
        }
    }
    LaunchedEffect(searchActive, engineAvailable) {
        if (searchActive && engineAvailable) app.embeddingEngineHolder.preload()
    }

    val filteredItems = remember(items, filters, mainColors) {
        items.filter { item -> filters.matches(item, mainColors) }
    }
    val searchResult = remember(trimmedQuery, filteredItems, domainItems, vectors, queryEmbedding) {
        // Until the query embedding exists (or gave up), the plain list stays visible.
        val embedded = queryEmbedding?.takeIf { it.query == trimmedQuery }
        if (embedded == null) {
            SearchResult(emptyList(), SearchMode.None)
        } else {
            val filteredIds = filteredItems.mapTo(HashSet(), UiWardrobeItem::id)
            searchController.rank(trimmedQuery, domainItems.filter { item -> item.id in filteredIds }, vectors, embedded.vector)
        }
    }
    val orderedItems = remember(filteredItems, searchResult, searchActive) {
        if (searchActive && searchResult.mode != SearchMode.None) {
            val byId = filteredItems.associateBy(UiWardrobeItem::id)
            searchResult.orderedIds.mapNotNull(byId::get)
        } else {
            filteredItems
        }
    }
    // Judged on the DRAFT, so a tap in "Not in" removes the garment from the view at once.
    val visibleItems = remember(orderedItems, session) {
        val shownIds = session.visible(orderedItems.map(UiWardrobeItem::id)).toSet()
        orderedItems.filter { item -> item.id in shownIds }
    }
    val visibleIds = remember(visibleItems) { visibleItems.map(UiWardrobeItem::id) }
    val addedIds = remember(session) { session.added }
    val removedIds = remember(session) { session.removed }

    val subtitle = if (session.hasChanges) {
        stringResource(R.string.collection_members_count_changed, session.saved.size, session.draft.size)
    } else {
        stringResource(R.string.collection_members_count, session.draft.size)
    }

    fun requestLeave() {
        if (session.leaveDecision() == LeaveDecision.Leave) onClose() else showLeaveDialog = true
    }

    fun save() {
        if (saving) return
        saving = true
        onSave(session.added, session.removed)
        onClose()
    }

    fun toggleMember(id: String) {
        session = session.toggle(id)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // System back and predictive back: the advanced filters close first, then the dialog, then the leave decision.
    BackHandler {
        when {
            showAdvanced -> showAdvanced = false
            showLeaveDialog -> showLeaveDialog = false
            else -> requestLeave()
        }
    }

    if (showAdvanced) {
        AdvancedFiltersScreen(
            innerPadding = innerPadding,
            filters = filters,
            availableTags = filterTags,
            mainColors = mainColors,
            items = items,
            allItemCount = items.size,
            onFiltersChange = { next -> filters = next },
            onResetFilters = { filters = BrowseFilterState() },
            onShowResults = { showAdvanced = false },
        )
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { requestLeave() }) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.content_go_back),
                            )
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                // With no garments there is nothing to save: the empty state replaces the bar.
                if (items.isNotEmpty()) {
                    MembersBottomBar(
                        session = session,
                        saving = saving,
                        onUndoAll = { session = session.undoAll() },
                        onSave = { save() },
                    )
                }
            },
        ) { contentPadding ->
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(effectiveColumns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .membersDragPaint(gridState, paintArm, visibleIds) { draft ->
                        if (draft != session.draft) {
                            session = session.withDraft(draft)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterBar(
                        hasActiveFilters = filters.hasActiveFilters,
                        activeFilterCount = filters.activeFilterCount,
                        filters = filters,
                        availableTags = filterTags,
                        paletteColors = mainColors,
                        onFiltersChange = { next -> filters = next },
                        cloudSync = IdleCloudSync,
                        onFiltersClick = { showAdvanced = true },
                        onResetFilters = { filters = BrowseFilterState() },
                        searchQuery = query,
                        onSearchQueryChange = { next -> query = next },
                        searchMode = searchResult.mode,
                        searchInProgress = searchInProgress,
                        resultCount = visibleItems.size,
                        similarToName = null,
                        onClearSimilar = {},
                        modelState = modelState,
                        onOpenSettings = onOpenSettings,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MembersViewSwitch(
                        view = session.view,
                        onViewChange = { next -> session = session.withView(next) },
                    )
                }
                if (!isInitialLoading && items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MembersEmptyWardrobe()
                    }
                } else if (items.isNotEmpty() && visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (filters.hasActiveFilters || searchActive) {
                            EmptyFiltersCard(onResetFilters = {
                                filters = BrowseFilterState()
                                query = ""
                            })
                        } else {
                            MembersMessage(text = stringResource(R.string.collection_members_empty_view))
                        }
                    }
                }
                items(visibleItems, key = { item -> item.id }, contentType = { browseCellFor(browseLayout) }) { item ->
                    val onLongClick = {
                        // The long-press toggles the garment; a drag that follows paints the range to the same state.
                        paintArm.arm(item.id, session.draft)
                        toggleMember(item.id)
                    }
                    Box(modifier = Modifier.animateItem()) {
                        when (browseCellFor(browseLayout)) {
                            BrowseCell.Tile -> GarmentPhotoTile(
                                item = item,
                                isSelected = item.id in session.draft,
                                onClick = { toggleMember(item.id) },
                                onLongClick = onLongClick,
                                // Browse's favourite heart is inert here: nothing is written except by Save.
                                onFavoriteClick = {},
                                columns = effectiveColumns,
                                signInBannerShown = signInBannerShown,
                            )
                            BrowseCell.Card -> GarmentGridCard(
                                item = item,
                                isSelected = item.id in session.draft,
                                isSelectionMode = true,
                                onClick = { toggleMember(item.id) },
                                onLongClick = onLongClick,
                                onFavoriteClick = {},
                                columns = effectiveColumns,
                                signInBannerShown = signInBannerShown,
                            )
                        }
                        MemberChangeBadge(added = item.id in addedIds, removed = item.id in removedIds)
                    }
                }
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.collection_members_leave_title, collection.name)) },
            text = {
                Text(stringResource(R.string.collection_members_leave_body, session.added.size, session.removed.size))
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { save() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.collection_members_save)) }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.collection_members_discard)) }
                    TextButton(
                        onClick = { showLeaveDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.collection_members_keep_editing)) }
                }
            },
        )
    }
}

private fun memberIdsIn(items: List<UiWardrobeItem>, collectionId: String): Set<String> =
    items.filter { item -> item.tags.any { tag -> tag.id == collectionId } }.mapTo(HashSet(), UiWardrobeItem::id)

/** The "+ added" and "removed" labels on a changed garment, bottom-start over the photo. Text carries the meaning, not colour alone. */
@Composable
private fun MemberChangeBadge(added: Boolean, removed: Boolean) {
    if (!added && !removed) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .padding(8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (added) {
            ChangeLabel(
                text = "+ " + stringResource(R.string.collection_members_added_badge),
                containerColor = MaterialTheme.extras.addColors.container,
                contentColor = MaterialTheme.extras.addColors.onContainer,
            )
        } else {
            ChangeLabel(
                text = stringResource(R.string.collection_members_removed_badge),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                strikeThrough = true,
            )
        }
    }
}

@Composable
private fun ChangeLabel(
    text: String,
    containerColor: Color,
    contentColor: Color,
    strikeThrough: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersViewSwitch(view: MembersView, onViewChange: (MembersView) -> Unit) {
    val views = MembersView.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        views.forEachIndexed { index, option ->
            SegmentedButton(
                selected = view == option,
                onClick = { onViewChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = views.size),
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
}

@StringRes
private fun MembersView.labelRes(): Int = when (this) {
    MembersView.All -> R.string.collection_members_view_all
    MembersView.InCollection -> R.string.collection_members_view_in
    MembersView.NotIn -> R.string.collection_members_view_out
}

@Composable
private fun MembersBottomBar(
    session: CollectionMembersSession,
    saving: Boolean,
    onUndoAll: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (session.hasChanges) {
                ChangeLabel(
                    text = "+${session.added.size}",
                    containerColor = MaterialTheme.extras.addColors.container,
                    contentColor = MaterialTheme.extras.addColors.onContainer,
                )
                ChangeLabel(
                    text = "−${session.removed.size}",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onUndoAll, enabled = !saving) {
                    Text(stringResource(R.string.collection_members_undo_all))
                }
                Button(onClick = onSave, enabled = !saving) {
                    Text(stringResource(R.string.collection_members_save))
                }
            } else {
                Text(
                    text = stringResource(R.string.collection_members_hint),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSave, enabled = false) {
                    Text(stringResource(R.string.collection_members_save))
                }
            }
        }
    }
}

@Composable
private fun MembersEmptyWardrobe() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_wardrobe_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_wardrobe_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MembersMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Paint-drag on the members grid, mirroring Browse's `dragSelect` gesture (same pinch and autoscroll rules). The anchor's
 * new state decides what the whole range becomes; see [DragPaint]. Only a long-press that armed the drag starts it.
 */
@Composable
private fun Modifier.membersDragPaint(
    gridState: LazyGridState,
    arm: MembersDragArm,
    orderedIds: List<String>,
    onDraftChange: (Set<String>) -> Unit,
): Modifier {
    val currentIds by rememberUpdatedState(orderedIds)
    val currentOnDraftChange by rememberUpdatedState(onDraftChange)
    val scope = rememberCoroutineScope()
    return pointerInput(gridState, arm) {
        val edgePx = PAINT_EDGE_ZONE.toPx()
        val maxStepPx = PAINT_MAX_STEP.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var paint: DragPaint? = null
            var pinched = false
            var pointer = Offset.Zero
            var autoscroll: Job? = null
            fun track(active: DragPaint) {
                val ids = currentIds
                gridState.membersItemIdAt(pointer, ids.toHashSet())?.let(active::moveTo)
                currentOnDraftChange(active.draft(ids))
            }
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        // Pinch takes over; the draft painted so far stays.
                        pinched = true
                        paint = null
                        autoscroll?.cancel()
                    } else if (!pinched) {
                        val change = event.changes.firstOrNull { it.pressed }
                        val anchorId = arm.anchorId
                        if (change != null && paint == null && anchorId != null) {
                            val started = DragPaint(anchorId, arm.before)
                            paint = started
                            autoscroll = scope.launch {
                                while (true) {
                                    delay(PAINT_FRAME_MS)
                                    val layout = gridState.layoutInfo
                                    val step = dragAutoscrollStep(
                                        pointerY = pointer.y,
                                        viewportStart = layout.viewportStartOffset.toFloat(),
                                        viewportEnd = layout.viewportEndOffset.toFloat(),
                                        edgePx = edgePx,
                                        maxStepPx = maxStepPx,
                                    )
                                    if (step != 0f) {
                                        gridState.scrollBy(step)
                                        track(started)
                                    }
                                }
                            }
                        }
                        val active = paint
                        if (change != null && active != null) {
                            pointer = change.position
                            change.consume()
                            track(active)
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                autoscroll?.cancel()
                arm.disarm()
            }
        }
    }
}

/** The visible card under [pointer] whose id is in [ids]; gaps and banners hit nothing. */
private fun LazyGridState.membersItemIdAt(pointer: Offset, ids: Set<String>): String? {
    for (info in layoutInfo.visibleItemsInfo) {
        val id = info.key as? String ?: continue
        if (id !in ids) continue
        val left = info.offset.x.toFloat()
        val top = info.offset.y.toFloat()
        if (pointer.x in left..left + info.size.width && pointer.y in top..top + info.size.height) return id
    }
    return null
}
