package com.gusanitolabs.robia.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.RobiaTheme
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.TagRepository
import com.gusanitolabs.robia.data.WardrobeRepository
import com.gusanitolabs.robia.media.GarmentShareColor
import com.gusanitolabs.robia.media.GarmentShareExporter
import com.gusanitolabs.robia.media.GarmentShareItem
import com.gusanitolabs.robia.media.GarmentShareMetadata
import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.NoOpWardrobeSyncGateway
import com.gusanitolabs.robia.sync.WardrobeSyncGateway
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import com.gusanitolabs.robia.sync.WardrobeSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val DEVELOPER_UNLOCK_TAP_COUNT = 10
private const val DEVELOPER_UNLOCK_WINDOW_MILLIS = 5_000L

private sealed interface RobiaRoute {
    @get:StringRes
    val titleRes: Int

    data object Browse : RobiaRoute {
        override val titleRes = R.string.browse
    }

    data object ManageTags : RobiaRoute {
        override val titleRes = R.string.manage
    }

    data object ColorReview : RobiaRoute {
        override val titleRes = R.string.color_review_title
    }

    data object AddEditClothing : RobiaRoute {
        override val titleRes = R.string.add_clothing
    }

    data object BatchAddClothing : RobiaRoute {
        override val titleRes = R.string.batch_add_title
    }

    data object BatchEditClothing : RobiaRoute {
        override val titleRes = R.string.batch_edit_title
    }

    data object ItemDetail : RobiaRoute {
        override val titleRes = R.string.item_detail
    }

    data object LanguageSettings : RobiaRoute {
        override val titleRes = R.string.language
    }

    data object AdvancedFilters : RobiaRoute {
        override val titleRes = R.string.advanced_filters_title
    }
}

private data class BottomNavDestination(
    val route: RobiaRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private data class UiWardrobeItem(
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
)

private data class UiTag(
    val id: String,
    val categoryId: String,
    val label: String,
)

private data class BrowseFilterState(
    val selectedTagIds: Set<String> = emptySet(),
    val selectedPaletteColorIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = selectedTagIds.isNotEmpty() || selectedPaletteColorIds.isNotEmpty() || favoritesOnly

    val activeFilterCount: Int
        get() = selectedTagIds.size + selectedPaletteColorIds.size + if (favoritesOnly) 1 else 0

    fun matches(item: UiWardrobeItem, paletteColors: List<MainColor>): Boolean {
        val itemTagIds = item.tags.map(UiTag::id).toSet()
        return (!favoritesOnly || item.isFavorite) &&
            selectedTagIds.all(itemTagIds::contains) &&
            (selectedPaletteColorIds.isEmpty() || item.matchesAnyPaletteColor(selectedPaletteColors(paletteColors)))
    }

    private fun selectedPaletteColors(paletteColors: List<MainColor>): List<MainColor> =
        paletteColors.filter { color -> color.id in selectedPaletteColorIds }
}

private data class CloudSetupGuard(
    val garmentCount: Int,
    val customTagCount: Int,
    val customCategoryCount: Int,
    val customColorCount: Int,
    val taggedGarmentCount: Int,
    val pendingOperationCount: Int,
    val hasConflictingAccountBinding: Boolean,
) {
    val hasUnsafeLocalState: Boolean
        get() = garmentCount > 0 ||
            customTagCount > 0 ||
            customCategoryCount > 0 ||
            customColorCount > 0 ||
            taggedGarmentCount > 0 ||
            pendingOperationCount > 0 ||
            hasConflictingAccountBinding

    val isFirstRunRecommendation: Boolean
        get() = !hasUnsafeLocalState
}

private enum class CloudSetupDialogMode {
    RecommendedFirstRun,
    LateEnableBlocked,
}

private val bottomDestinations = listOf(
    BottomNavDestination(RobiaRoute.Browse, R.string.browse, Icons.Rounded.GridView),
    BottomNavDestination(RobiaRoute.AddEditClothing, R.string.add_clothing, Icons.Rounded.Add),
    BottomNavDestination(RobiaRoute.ManageTags, R.string.manage, Icons.Rounded.Style),
)

@Composable
fun RobiaApp(
    settingsRepository: SettingsRepository,
    wardrobeRepository: WardrobeRepository,
    tagRepository: TagRepository,
    syncGateway: WardrobeSyncGateway = NoOpWardrobeSyncGateway,
) {
    val settings by settingsRepository.settings.collectAsState(initial = RobiaSettings())
    val syncState by syncGateway.state.collectAsState(initial = WardrobeSyncState.notConfigured())
    val clothingItems by wardrobeRepository.observeActiveItems().collectAsState(initial = emptyList())
    val tagCategories by tagRepository.observeCategories().collectAsState(initial = emptyList())
    val availableTags by tagRepository.observeTags().collectAsState(initial = emptyList())
    val mainColors by tagRepository.observeMainColors().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(tagRepository) {
        tagRepository.seedDefaultsIfNeeded()
    }

    LocalizedRobiaContent(settings.languagePreference) {
        RobiaTheme {
        val displaySettings = settings.copy(driveSyncConnectionStatus = syncState.connectionStatus)
        RobiaShell(
            settings = displaySettings,
            syncState = syncState,
            clothingItems = clothingItems,
            tagCategories = tagCategories,
            availableTags = availableTags,
            mainColors = mainColors,
            onLanguageSelected = { language ->
                scope.launch { settingsRepository.setLanguagePreference(language) }
            },
            onDeveloperModeUnlocked = {
                scope.launch { settingsRepository.setDeveloperModeUnlocked(true) }
            },
            onDeveloperModeEnabledChange = { enabled ->
                scope.launch { settingsRepository.setDeveloperModeEnabled(enabled) }
            },
            onSaveItem = { item ->
                scope.launch {
                    wardrobeRepository.upsertItem(item)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertItem(item.id))
                }
            },
            onSaveItems = { items ->
                scope.launch {
                    wardrobeRepository.upsertItems(items)
                    items.forEach { item -> syncGateway.enqueue(WardrobeSyncOperation.UpsertItem(item.id)) }
                }
            },
            onDeleteItems = { itemIds ->
                scope.launch {
                    wardrobeRepository.archiveItems(itemIds, System.currentTimeMillis())
                    itemIds.forEach { itemId -> syncGateway.enqueue(WardrobeSyncOperation.DeleteItemFolder(itemId)) }
                }
            },
            onSaveTag = { tag ->
                scope.launch {
                    tagRepository.upsertTag(tag)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertTags(setOf(tag.id)))
                }
            },
            onSaveMainColor = { color ->
                scope.launch {
                    tagRepository.upsertMainColor(color)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertPalette(listOf(color)))
                }
            },
            onDeleteCustomTag = { tag ->
                scope.launch {
                    tagRepository.deleteCustomTag(tag.id)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertTags(setOf(tag.id)))
                }
            },
            onDeleteMainColor = { color ->
                scope.launch {
                    tagRepository.deleteMainColor(color.id)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertPalette(listOf(color)))
                }
            },
            onRestoreDefaultTags = { category ->
                scope.launch {
                    tagRepository.restoreDefaultTags(category.id)
                    val restoredTagIds = DefaultTags.tags
                        .filter { tag -> tag.categoryId == category.id }
                        .map(GarmentTag::id)
                        .toSet()
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertTags(restoredTagIds))
                }
            },
            onRestoreDefaultMainColors = {
                scope.launch {
                    tagRepository.resetMainColorsToDefaults()
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertPalette(DefaultTags.mainColors))
                }
            },
        )
        }
    }
}

@Composable
private fun LocalizedRobiaContent(
    languagePreference: LanguagePreference,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val localizedConfiguration = remember(languagePreference, baseConfiguration) {
        Configuration(baseConfiguration).apply {
            val languageTag = languagePreference.storageValue
            if (languageTag != null) {
                val locale = Locale.forLanguageTag(languageTag)
                Locale.setDefault(locale)
                setLocales(LocaleList(locale))
            }
        }
    }
    val localizedContext = remember(languagePreference, baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }

    if (activityResultRegistryOwner != null) {
        CompositionLocalProvider(
            LocalConfiguration provides localizedConfiguration,
            LocalContext provides localizedContext,
            LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
        ) {
            content()
        }
    } else {
        CompositionLocalProvider(
            LocalConfiguration provides localizedConfiguration,
            LocalContext provides localizedContext,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobiaShell(
    settings: RobiaSettings,
    syncState: WardrobeSyncState,
    clothingItems: List<ClothingItem>,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDeveloperModeUnlocked: () -> Unit,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onSaveItem: (ClothingItem) -> Unit,
    onSaveItems: (List<ClothingItem>) -> Unit,
    onDeleteItems: (List<String>) -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
    onRestoreDefaultTags: (TagCategory) -> Unit,
    onRestoreDefaultMainColors: () -> Unit,
) {
    val routeStack = remember { mutableStateListOf<RobiaRoute>(RobiaRoute.Browse) }
    val currentRoute = routeStack.last()
    var settingsExpanded by remember { mutableStateOf(false) }
    val settingsTapTimestamps = remember { mutableStateListOf<Long>() }
    val context = LocalContext.current
    val developerModeUnlockedMessage = stringResource(R.string.developer_mode_unlocked)
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var browseFilters by remember { mutableStateOf(BrowseFilterState()) }
    val batchDrafts = remember { mutableStateListOf<BatchDraftItem>() }
    var selectedBatchDraftId by remember { mutableStateOf<String?>(null) }
    var showBatchDiscardDialog by remember { mutableStateOf(false) }
    var selectedBrowseItemIds by remember { mutableStateOf(emptySet<String>()) }
    var showBrowseDeleteDialog by remember { mutableStateOf(false) }
    var showColorReviewDiscardDialog by remember { mutableStateOf(false) }
    var pendingColorReviewChangeSet by remember { mutableStateOf<ColorPaletteChangeSet?>(null) }
    var activeColorReviewChangeSet by remember { mutableStateOf<ColorPaletteChangeSet?>(null) }
    var cloudSetupDialogMode by remember { mutableStateOf<CloudSetupDialogMode?>(null) }
    var hasShownFirstRunCloudPrompt by remember { mutableStateOf(false) }
    val items = clothingItems.toUiWardrobeItems(settings.driveSyncConnectionStatus)
    val filteredItems = remember(items, browseFilters, mainColors) {
        items.filter { item -> browseFilters.matches(item, mainColors) }
    }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val selectedDomainItem = clothingItems.firstOrNull { it.id == selectedItemId }
    val cloudSetupGuard = remember(clothingItems, tagCategories, availableTags, mainColors, syncState) {
        CloudSetupGuard(
            garmentCount = clothingItems.size,
            customTagCount = availableTags.count { tag -> !tag.isSystem },
            customCategoryCount = tagCategories.count { category -> !category.isSystem },
            customColorCount = mainColors.count { color -> !color.isDefault },
            taggedGarmentCount = clothingItems.count { item -> item.tags.isNotEmpty() },
            pendingOperationCount = syncState.pendingOperationCount,
            hasConflictingAccountBinding = syncState.hasConflictingAccountBinding,
        )
    }

    LaunchedEffect(settings.driveSyncConnectionStatus, cloudSetupGuard.isFirstRunRecommendation) {
        if (!hasShownFirstRunCloudPrompt &&
            settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured &&
            cloudSetupGuard.isFirstRunRecommendation
        ) {
            hasShownFirstRunCloudPrompt = true
            cloudSetupDialogMode = CloudSetupDialogMode.RecommendedFirstRun
        }
    }

    LaunchedEffect(items) {
        val activeIds = items.map(UiWardrobeItem::id).toSet()
        selectedBrowseItemIds = selectedBrowseItemIds.intersect(activeIds)
        selectedItemId?.let { itemId ->
            if (itemId !in activeIds) selectedItemId = null
        }
    }

    fun pushRoute(route: RobiaRoute) {
        if (routeStack.lastOrNull() != route) routeStack.add(route)
    }

    fun replaceRoute(route: RobiaRoute) {
        routeStack.clear()
        routeStack.add(route)
    }

    fun popRoute() {
        if (routeStack.size > 1) routeStack.removeAt(routeStack.lastIndex)
    }

    fun closeAddEdit() {
        if (routeStack.size > 1) {
            popRoute()
        } else {
            selectedItemId = null
            replaceRoute(RobiaRoute.Browse)
        }
    }

    fun toggleFavorite(itemId: String) {
        val item = clothingItems.firstOrNull { it.id == itemId } ?: return
        onSaveItem(
            item.copy(
                isFavorite = !item.isFavorite,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun toggleBrowseSelection(itemId: String) {
        selectedBrowseItemIds = if (itemId in selectedBrowseItemIds) {
            selectedBrowseItemIds - itemId
        } else {
            selectedBrowseItemIds + itemId
        }
    }

    fun deleteItemsAndReturnToBrowse(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        onDeleteItems(itemIds.toList())
        selectedBrowseItemIds = emptySet()
        selectedItemId = null
        showBrowseDeleteDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun startBatchAdd(photoUris: List<String>) {
        batchDrafts.clear()
        photoUris.forEachIndexed { index, uri ->
            batchDrafts += BatchDraftItem(orderIndex = index, originalPhotoUri = uri)
        }
        selectedBatchDraftId = null
        replaceRoute(RobiaRoute.BatchAddClothing)
    }

    fun updateBatchDraft(updated: BatchDraftItem) {
        val index = batchDrafts.indexOfFirst { draft -> draft.id == updated.id }
        if (index >= 0) batchDrafts[index] = updated
    }

    fun cancelBatchAdd() {
        batchDrafts.clear()
        selectedBatchDraftId = null
        showBatchDiscardDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun saveMainColorAndOfferReview(color: MainColor) {
        val beforePalette = mainColors
        val operation = if (beforePalette.any { existing -> existing.id == color.id }) {
            ColorPaletteOperation.Edited
        } else {
            ColorPaletteOperation.Added
        }
        val afterPalette = (beforePalette.filterNot { existing -> existing.id == color.id } + color)
            .sortedWith(compareBy<MainColor> { it.sortOrder }.thenBy { it.name }.thenBy { it.id })
        onSaveMainColor(color)
        pendingColorReviewChangeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = afterPalette,
            touchedColorId = color.id,
            operation = operation,
        )
    }

    fun deleteMainColorAndOfferReview(color: MainColor) {
        val beforePalette = mainColors
        val afterPalette = beforePalette.filterNot { existing -> existing.id == color.id }
        onDeleteMainColor(color)
        pendingColorReviewChangeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = afterPalette,
            touchedColorId = color.id,
            operation = ColorPaletteOperation.Deleted,
        )
    }

    fun restoreDefaultMainColorsAndStartReview() {
        val beforePalette = mainColors
        onRestoreDefaultMainColors()
        pendingColorReviewChangeSet = null
        activeColorReviewChangeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = DefaultTags.mainColors,
            touchedColorId = null,
            operation = ColorPaletteOperation.RestoredDefault,
        )
        replaceRoute(RobiaRoute.ColorReview)
    }

    fun requestColorReviewDiscard() {
        showColorReviewDiscardDialog = true
    }

    fun discardColorReviewAndRollbackPalette() {
        val changeSet = activeColorReviewChangeSet ?: return
        val beforeColorIds = changeSet.beforePalette.map(MainColor::id).toSet()
        changeSet.afterPalette
            .filterNot { color -> color.id in beforeColorIds }
            .forEach(onDeleteMainColor)
        changeSet.beforePalette.forEach(onSaveMainColor)
        pendingColorReviewChangeSet = null
        activeColorReviewChangeSet = null
        showColorReviewDiscardDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun handleSettingsClick() {
        settingsExpanded = true
        if (settings.developerModeUnlocked) return

        val now = SystemClock.elapsedRealtime()
        settingsTapTimestamps.removeAll { timestamp -> now - timestamp > DEVELOPER_UNLOCK_WINDOW_MILLIS }
        settingsTapTimestamps.add(now)
        if (settingsTapTimestamps.size >= DEVELOPER_UNLOCK_TAP_COUNT) {
            settingsTapTimestamps.clear()
            onDeveloperModeUnlocked()
            Toast.makeText(context, developerModeUnlockedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCloudSetup() {
        cloudSetupDialogMode = if (cloudSetupGuard.hasUnsafeLocalState) {
            CloudSetupDialogMode.LateEnableBlocked
        } else {
            CloudSetupDialogMode.RecommendedFirstRun
        }
    }

    val restoreProgress = syncState.restoreProgress
    BackHandler(enabled = restoreProgress != null || routeStack.size > 1) {
        if (restoreProgress != null) {
            // Restore is transactional: navigation is locked until the gateway reports a terminal state.
        } else if (currentRoute == RobiaRoute.ColorReview) {
            requestColorReviewDiscard()
        } else {
            popRoute()
        }
    }

    if (showBatchDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDiscardDialog = false },
            title = { Text(stringResource(R.string.batch_discard_title)) },
            text = { Text(stringResource(R.string.batch_discard_body)) },
            confirmButton = {
                TextButton(onClick = ::cancelBatchAdd) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    if (showBrowseDeleteDialog) {
        val selectedCount = selectedBrowseItemIds.size
        AlertDialog(
            onDismissRequest = { showBrowseDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_selected_items_title)) },
            text = { Text(stringResource(R.string.delete_selected_items_body, selectedCount)) },
            confirmButton = {
                TextButton(onClick = { deleteItemsAndReturnToBrowse(selectedBrowseItemIds) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBrowseDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showColorReviewDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showColorReviewDiscardDialog = false },
            title = { Text(stringResource(R.string.color_review_discard_title)) },
            text = { Text(stringResource(R.string.color_review_discard_body)) },
            confirmButton = {
                TextButton(onClick = ::discardColorReviewAndRollbackPalette) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showColorReviewDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }

    pendingColorReviewChangeSet?.let { changeSet ->
        ColorReviewPromptDialog(
            onDismiss = { pendingColorReviewChangeSet = null },
            onReview = {
                activeColorReviewChangeSet = changeSet
                pendingColorReviewChangeSet = null
                replaceRoute(RobiaRoute.ColorReview)
            },
        )
    }

    cloudSetupDialogMode?.let { mode ->
        CloudSetupDialog(
            mode = mode,
            guard = cloudSetupGuard,
            onDismiss = { cloudSetupDialogMode = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (currentRoute == RobiaRoute.Browse && selectedBrowseItemIds.isNotEmpty()) {
                        IconButton(onClick = { selectedBrowseItemIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel),
                            )
                        }
                    } else if (routeStack.size > 1 && currentRoute != RobiaRoute.AddEditClothing) {
                        IconButton(
                            onClick = {
                                if (currentRoute == RobiaRoute.BatchAddClothing && batchDrafts.isNotEmpty()) {
                                    showBatchDiscardDialog = true
                                } else if (currentRoute == RobiaRoute.ColorReview) {
                                    requestColorReviewDiscard()
                                } else {
                                    popRoute()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.content_go_back),
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = if (currentRoute == RobiaRoute.Browse && selectedBrowseItemIds.isNotEmpty()) {
                            stringResource(R.string.browse_selection_count, selectedBrowseItemIds.size)
                        } else {
                            stringResource(currentRoute.titleRes)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (currentRoute == RobiaRoute.Browse && selectedBrowseItemIds.isNotEmpty()) {
                        val deleteDescription = stringResource(R.string.content_delete_selected_items)
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = deleteDescription },
                            onClick = { showBrowseDeleteDialog = true },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (currentRoute == RobiaRoute.ItemDetail && selectedItem != null) {
                        val favoriteDescription = stringResource(R.string.content_favorite)
                        IconButton(
                            modifier = Modifier.semantics {
                                contentDescription = favoriteDescription
                                selected = selectedItem.isFavorite
                            },
                            onClick = { toggleFavorite(selectedItem.id) },
                        ) {
                            Icon(
                                imageVector = if (selectedItem.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    } else {
                        Box {
                            val settingsDescription = stringResource(R.string.content_settings_menu)
                            IconButton(
                                modifier = Modifier.semantics { contentDescription = settingsDescription },
                                onClick = ::handleSettingsClick,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                )
                            }
                            SettingsMenu(
                                expanded = settingsExpanded,
                                currentLanguage = settings.languagePreference,
                                driveSyncConnectionStatus = settings.driveSyncConnectionStatus,
                                canAttemptCloudSetup = !cloudSetupGuard.hasUnsafeLocalState,
                                developerModeUnlocked = settings.developerModeUnlocked,
                                developerModeEnabled = settings.developerModeEnabled,
                                onDeveloperModeEnabledChange = onDeveloperModeEnabledChange,
                                onLanguageSelected = { language ->
                                    onLanguageSelected(language)
                                    settingsExpanded = false
                                },
                                onDismiss = { settingsExpanded = false },
                                onLanguageClick = {
                                    pushRoute(RobiaRoute.LanguageSettings)
                                    settingsExpanded = false
                                },
                                onCloudSetupClick = {
                                    requestCloudSetup()
                                    settingsExpanded = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            if (currentRoute != RobiaRoute.ItemDetail &&
                currentRoute != RobiaRoute.AdvancedFilters &&
                currentRoute != RobiaRoute.BatchAddClothing &&
                currentRoute != RobiaRoute.BatchEditClothing &&
                currentRoute != RobiaRoute.ColorReview
            ) {
                RobiaBottomBar(
                    currentRoute = currentRoute,
                    onRouteSelected = { route ->
                        if (route == RobiaRoute.AddEditClothing) selectedItemId = null
                        replaceRoute(route)
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        RobiaNavHost(
            currentRoute = currentRoute,
            innerPadding = innerPadding,
            items = filteredItems,
            allItems = items,
            domainItems = clothingItems,
            totalItemCount = items.size,
            filters = browseFilters,
            selectedBrowseItemIds = selectedBrowseItemIds,
            selectedItem = selectedItem,
            selectedDomainItem = selectedDomainItem,
            batchDrafts = batchDrafts,
            selectedBatchDraft = batchDrafts.firstOrNull { draft -> draft.id == selectedBatchDraftId },
            tagCategories = tagCategories,
            availableTags = availableTags,
            mainColors = mainColors,
            colorReviewChangeSet = activeColorReviewChangeSet,
            developerModeEnabled = settings.developerModeUnlocked && settings.developerModeEnabled,
            onRouteSelected = { route ->
                if (route == RobiaRoute.AddEditClothing && currentRoute != RobiaRoute.ItemDetail) selectedItemId = null
                pushRoute(route)
            },
            onBack = ::popRoute,
            onItemSelected = { item ->
                selectedItemId = item.id
                pushRoute(RobiaRoute.ItemDetail)
            },
            onToggleFavorite = ::toggleFavorite,
            onBrowseSelectionToggle = ::toggleBrowseSelection,
            onCancelAddEdit = ::closeAddEdit,
            onSaveItem = { item ->
                onSaveItem(item)
                selectedItemId = item.id
                replaceRoute(RobiaRoute.Browse)
                pushRoute(RobiaRoute.ItemDetail)
            },
            onDeleteItem = { item -> deleteItemsAndReturnToBrowse(setOf(item.id)) },
            onBatchPhotosSelected = ::startBatchAdd,
            onDraftUpdated = ::updateBatchDraft,
            onDraftSelected = { draft ->
                selectedBatchDraftId = draft.id
                pushRoute(RobiaRoute.BatchEditClothing)
            },
            onSaveBatch = { batchItems ->
                onSaveItems(batchItems)
                batchDrafts.clear()
                selectedBatchDraftId = null
                replaceRoute(RobiaRoute.Browse)
            },
            onCancelBatch = {
                cancelBatchAdd()
            },
            onSaveBatchDraft = { item ->
                batchDrafts.firstOrNull { draft -> draft.id == item.id }?.let { previous ->
                    updateBatchDraft(item.toBatchDraftFromExisting(previous, mainColors))
                }
                popRoute()
            },
            onFiltersChange = { browseFilters = it },
            onResetFilters = { browseFilters = BrowseFilterState() },
            onSaveTag = onSaveTag,
            onSaveMainColor = ::saveMainColorAndOfferReview,
            onDeleteCustomTag = onDeleteCustomTag,
            onDeleteMainColor = ::deleteMainColorAndOfferReview,
            onRestoreDefaultTags = onRestoreDefaultTags,
            onRestoreDefaultMainColors = ::restoreDefaultMainColorsAndStartReview,
            onApplyColorReviewChanges = onSaveItems,
            onCloseColorReview = {
                activeColorReviewChangeSet = null
                replaceRoute(RobiaRoute.Browse)
            },
            onRequestColorReviewDiscard = ::requestColorReviewDiscard,
        )
    }
    restoreProgress?.let { progress ->
        CloudRestoreProgressOverlay(progress = progress)
    }
    }
}

@Composable
private fun SettingsMenu(
    expanded: Boolean,
    currentLanguage: LanguagePreference,
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    canAttemptCloudSetup: Boolean,
    developerModeUnlocked: Boolean,
    developerModeEnabled: Boolean,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDismiss: () -> Unit,
    onLanguageClick: () -> Unit,
    onCloudSetupClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.language)) },
            leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
            onClick = onLanguageClick,
        )
        LanguagePreference.entries.forEach { language ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(language.labelRes),
                        fontWeight = if (language == currentLanguage) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                onClick = { onLanguageSelected(language) },
            )
        }
        Divider()
        if (developerModeUnlocked) {
            DropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.developer_mode))
                        Text(
                            text = stringResource(R.string.developer_mode_summary),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                trailingIcon = {
                    Switch(
                        checked = developerModeEnabled,
                        onCheckedChange = onDeveloperModeEnabledChange,
                    )
                },
                onClick = { onDeveloperModeEnabledChange(!developerModeEnabled) },
            )
            Divider()
        }
        DropdownMenuItem(
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.data_sync_google_drive))
                    Text(
                        text = stringResource(driveSyncConnectionStatus.statusLabelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(
                            if (canAttemptCloudSetup) {
                                R.string.cloud_setup_recommended_summary
                            } else {
                                R.string.cloud_setup_late_blocked_summary
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingIcon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            onClick = onCloudSetupClick,
            enabled = true,
        )
    }
}

@Composable
private fun CloudSetupDialog(
    mode: CloudSetupDialogMode,
    guard: CloudSetupGuard,
    onDismiss: () -> Unit,
) {
    val titleRes = when (mode) {
        CloudSetupDialogMode.RecommendedFirstRun -> R.string.cloud_setup_prompt_title
        CloudSetupDialogMode.LateEnableBlocked -> R.string.cloud_setup_late_blocked_title
    }
    val bodyRes = when (mode) {
        CloudSetupDialogMode.RecommendedFirstRun -> R.string.cloud_setup_prompt_body
        CloudSetupDialogMode.LateEnableBlocked -> R.string.cloud_setup_late_blocked_body
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(bodyRes))
                if (mode == CloudSetupDialogMode.LateEnableBlocked) {
                    Text(
                        text = stringResource(
                            R.string.cloud_setup_blocked_detail,
                            guard.garmentCount,
                            guard.customTagCount + guard.customCategoryCount,
                            guard.customColorCount,
                            guard.pendingOperationCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (guard.hasConflictingAccountBinding) {
                        Text(
                            text = stringResource(R.string.cloud_setup_account_conflict),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (mode == CloudSetupDialogMode.RecommendedFirstRun) {
                            R.string.cloud_setup_configure_when_available
                        } else {
                            R.string.done
                        },
                    ),
                )
            }
        },
        dismissButton = {
            if (mode == CloudSetupDialogMode.RecommendedFirstRun) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
            }
        },
    )
}

@Composable
private fun CloudRestoreProgressOverlay(progress: CloudRestoreProgress) {
    val statusText = cloudRestoreStatusText(progress)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = statusText }
            .clickable(onClick = {}),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.cloud_restore_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            progress.progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(progress.phase.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cloud_restore_remaining, progress.remainingWork, progress.totalWork),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (progress.status == CloudRestoreStatus.Running) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            progress.message?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun cloudRestoreStatusText(progress: CloudRestoreProgress): String = stringResource(
    when (progress.status) {
        CloudRestoreStatus.Running -> R.string.cloud_restore_status_running
        CloudRestoreStatus.Offline -> R.string.cloud_restore_status_offline
        CloudRestoreStatus.Failed -> R.string.cloud_restore_status_failed
        CloudRestoreStatus.RolledBack -> R.string.cloud_restore_status_rolled_back
    },
)

private val CloudRestorePhase.labelRes: Int
    get() = when (this) {
        CloudRestorePhase.Preparing -> R.string.cloud_restore_phase_preparing
        CloudRestorePhase.Downloading -> R.string.cloud_restore_phase_downloading
        CloudRestorePhase.Validating -> R.string.cloud_restore_phase_validating
        CloudRestorePhase.Applying -> R.string.cloud_restore_phase_applying
        CloudRestorePhase.RollingBack -> R.string.cloud_restore_phase_rolling_back
        CloudRestorePhase.Complete -> R.string.cloud_restore_phase_complete
    }

private val DriveSyncConnectionStatus.statusLabelRes: Int
    get() = when (this) {
        DriveSyncConnectionStatus.Disabled -> R.string.drive_sync_status_disabled
        DriveSyncConnectionStatus.NotConfigured -> R.string.drive_sync_status_not_configured
        DriveSyncConnectionStatus.Disconnected -> R.string.drive_sync_status_disconnected
        DriveSyncConnectionStatus.Connected -> R.string.drive_sync_status_connected
        DriveSyncConnectionStatus.Syncing -> R.string.drive_sync_status_syncing
        DriveSyncConnectionStatus.NeedsAttention -> R.string.drive_sync_status_needs_attention
    }

private val LanguagePreference.labelRes: Int
    get() = when (this) {
        LanguagePreference.System -> R.string.language_system
        LanguagePreference.English -> R.string.language_english
        LanguagePreference.Spanish -> R.string.language_spanish
        LanguagePreference.German -> R.string.language_german
    }

@Composable
private fun RobiaBottomBar(
    currentRoute: RobiaRoute,
    onRouteSelected: (RobiaRoute) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        bottomDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onRouteSelected(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun RobiaNavHost(
    currentRoute: RobiaRoute,
    innerPadding: PaddingValues,
    items: List<UiWardrobeItem>,
    allItems: List<UiWardrobeItem>,
    domainItems: List<ClothingItem>,
    totalItemCount: Int,
    filters: BrowseFilterState,
    selectedBrowseItemIds: Set<String>,
    selectedItem: UiWardrobeItem?,
    selectedDomainItem: ClothingItem?,
    batchDrafts: List<BatchDraftItem>,
    selectedBatchDraft: BatchDraftItem?,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    colorReviewChangeSet: ColorPaletteChangeSet?,
    developerModeEnabled: Boolean,
    onRouteSelected: (RobiaRoute) -> Unit,
    onBack: () -> Unit,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBrowseSelectionToggle: (String) -> Unit,
    onCancelAddEdit: () -> Unit,
    onSaveItem: (ClothingItem) -> Unit,
    onDeleteItem: (ClothingItem) -> Unit,
    onBatchPhotosSelected: (List<String>) -> Unit,
    onDraftUpdated: (BatchDraftItem) -> Unit,
    onDraftSelected: (BatchDraftItem) -> Unit,
    onSaveBatch: (List<ClothingItem>) -> Unit,
    onCancelBatch: () -> Unit,
    onSaveBatchDraft: (ClothingItem) -> Unit,
    onFiltersChange: (BrowseFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
    onRestoreDefaultTags: (TagCategory) -> Unit,
    onRestoreDefaultMainColors: () -> Unit,
    onApplyColorReviewChanges: (List<ClothingItem>) -> Unit,
    onCloseColorReview: () -> Unit,
    onRequestColorReviewDiscard: () -> Unit,
) {
    when (currentRoute) {
        RobiaRoute.Browse -> BrowseWardrobeScreen(
            innerPadding = innerPadding,
            items = items,
            hasItemsInWardrobe = totalItemCount > 0,
            hasActiveFilters = filters.hasActiveFilters,
            activeFilterCount = filters.activeFilterCount,
            selectedItemIds = selectedBrowseItemIds,
            onItemSelected = onItemSelected,
            onToggleFavorite = onToggleFavorite,
            onSelectionToggle = onBrowseSelectionToggle,
            onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) },
            onFiltersClick = { onRouteSelected(RobiaRoute.AdvancedFilters) },
            onResetFilters = onResetFilters,
        )
        RobiaRoute.ManageTags -> ManageTagsScreen(
            innerPadding = innerPadding,
            categories = tagCategories,
            tags = availableTags,
            mainColors = mainColors,
            onSaveTag = onSaveTag,
            onDeleteTag = onDeleteCustomTag,
            onSaveMainColor = onSaveMainColor,
            onDeleteMainColor = onDeleteMainColor,
            onRestoreDefaultTags = onRestoreDefaultTags,
            onRestoreDefaultMainColors = onRestoreDefaultMainColors,
        )
        RobiaRoute.ColorReview -> colorReviewChangeSet?.let { changeSet ->
            ColorReviewScreen(
                innerPadding = innerPadding,
                items = domainItems,
                changeSet = changeSet,
                onApplyChanges = onApplyColorReviewChanges,
                onDone = onCloseColorReview,
                onRequestDiscard = onRequestColorReviewDiscard,
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.AddEditClothing -> AddEditClothingScreen(
            innerPadding = innerPadding,
            availableTags = availableTags,
            mainColors = mainColors,
            existingItem = selectedDomainItem,
            developerModeEnabled = developerModeEnabled,
            onCancel = onCancelAddEdit,
            onSave = onSaveItem,
            onDelete = onDeleteItem,
            onBatchPhotosSelected = if (selectedDomainItem == null) onBatchPhotosSelected else null,
        )
        RobiaRoute.BatchAddClothing -> BatchAddClothingScreen(
            innerPadding = innerPadding,
            drafts = batchDrafts,
            availableTags = availableTags,
            mainColors = mainColors,
            onDraftUpdated = onDraftUpdated,
            onDraftSelected = onDraftSelected,
            onSaveBatch = onSaveBatch,
            onCancelBatch = onCancelBatch,
        )
        RobiaRoute.BatchEditClothing -> selectedBatchDraft?.let { draft ->
            AddEditClothingScreen(
                innerPadding = innerPadding,
                availableTags = availableTags,
                mainColors = mainColors,
                existingItem = draft.toBatchEditItem(availableTags, mainColors),
                developerModeEnabled = developerModeEnabled,
                initialPhotoReviewState = draft.toBatchEditPhotoReviewState(),
                titleRes = R.string.batch_edit_title,
                bodyRes = R.string.batch_edit_body,
                saveButtonTextRes = R.string.batch_return_to_batch,
                onCancel = onBack,
                onSave = onSaveBatchDraft,
            )
        } ?: BatchAddClothingScreen(
            innerPadding = innerPadding,
            drafts = batchDrafts,
            availableTags = availableTags,
            mainColors = mainColors,
            onDraftUpdated = onDraftUpdated,
            onDraftSelected = onDraftSelected,
            onSaveBatch = onSaveBatch,
            onCancelBatch = onCancelBatch,
        )
        RobiaRoute.ItemDetail -> selectedItem?.let { item ->
            ItemDetailScreen(
                innerPadding = innerPadding,
                item = item,
                onEditClick = { onRouteSelected(RobiaRoute.AddEditClothing) },
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.LanguageSettings -> LanguageSettingsScreen(innerPadding)
        RobiaRoute.AdvancedFilters -> AdvancedFiltersScreen(
            innerPadding = innerPadding,
            filters = filters,
            availableTags = availableTags,
            mainColors = mainColors,
            items = allItems,
            allItemCount = totalItemCount,
            onFiltersChange = onFiltersChange,
            onResetFilters = onResetFilters,
            onShowResults = onBack,
        )
    }
}

@Composable
private fun BrowseWardrobeScreen(
    innerPadding: PaddingValues,
    items: List<UiWardrobeItem>,
    hasItemsInWardrobe: Boolean,
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    selectedItemIds: Set<String>,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
) {
    val isSelectionMode = selectedItemIds.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterBar(
                hasActiveFilters = hasActiveFilters,
                activeFilterCount = activeFilterCount,
                onFiltersClick = onFiltersClick,
            )
        }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (hasItemsInWardrobe && hasActiveFilters) {
                    EmptyFiltersCard(onResetFilters = onResetFilters)
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
private fun FilterBar(
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    onFiltersClick: () -> Unit,
) {
    val filterLabel = if (hasActiveFilters) {
        stringResource(R.string.active_filters_count, activeFilterCount)
    } else {
        stringResource(R.string.filters)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilterChip(
            selected = hasActiveFilters,
            onClick = onFiltersClick,
            label = { Text(filterLabel) },
            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        )
        AssistChip(
            onClick = onFiltersClick,
            label = { Text(stringResource(R.string.all_filters)) },
            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        )
        Spacer(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
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
private fun GarmentPhotoPlaceholder(
    item: UiWardrobeItem,
    modifier: Modifier = Modifier,
) {
    val swatchColor = item.primaryColor.swatchColor()
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        swatchColor.copy(alpha = 0.26f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val photoUri = item.photoUri?.takeIf { it.isNotBlank() }
        if (photoUri != null) {
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
        }
    }
}

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
private fun ItemDetailScreen(
    innerPadding: PaddingValues,
    item: UiWardrobeItem,
    onEditClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_garment_chooser_title)
    val imageShareErrorMessage = stringResource(R.string.image_share_error)
    val pdfShareErrorMessage = stringResource(R.string.pdf_share_error)
    val noShareAppMessage = stringResource(R.string.share_no_app_error)
    val pdfShareItem = item.toGarmentShareItem()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
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
        item { DetailMetadataGrid(item = item, onEditClick = onEditClick) }
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
private fun DetailMetadataGrid(
    item: UiWardrobeItem,
    onEditClick: () -> Unit,
) {
    val tags = item.tags
    val metadata = listOf(
        DetailMetadataItem(categoryIconFor("category"), stringResource(R.string.metadata_category), tags.labelsInCategory("category")),
        DetailMetadataItem(categoryIconFor("season"), stringResource(R.string.metadata_season), tags.labelsInCategory("season")),
        DetailMetadataItem(Icons.Rounded.Straighten, stringResource(R.string.metadata_fit), item.fitValue?.fitLabel()),
        DetailMetadataItem(categoryIconFor("location"), stringResource(R.string.metadata_location), tags.labelsInCategory("location")),
        DetailMetadataItem(categoryIconFor("occasion"), stringResource(R.string.metadata_occasions), tags.labelsInCategory("occasion")),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.metadata_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            metadata.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { metadataItem ->
                        DetailMetadataCard(
                            icon = metadataItem.icon,
                            label = metadataItem.label,
                            value = metadataItem.value,
                            onEditClick = onEditClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailMetadataCard(
    icon: ImageVector,
    label: String,
    value: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable(onClick = onEditClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value ?: stringResource(R.string.not_set),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class DetailMetadataItem(
    val icon: ImageVector,
    val label: String,
    val value: String?,
)

private fun List<UiTag>.labelsInCategory(categoryId: String): String? =
    filter { tag -> tag.categoryId == categoryId }
        .joinToString { tag -> tag.label }
        .takeIf { labels -> labels.isNotBlank() }

@Composable
private fun EmptyStateCard(onAddClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.empty_wardrobe_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.empty_wardrobe_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AssistChip(
                onClick = onAddClick,
                label = { Text(stringResource(R.string.add_clothing)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun EmptyFiltersCard(onResetFilters: () -> Unit) {
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

@Composable
private fun PlaceholderScreen(
    innerPadding: PaddingValues,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    icon: ImageVector,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(bodyRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFiltersScreen(
    innerPadding: PaddingValues,
    filters: BrowseFilterState,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    items: List<UiWardrobeItem>,
    allItemCount: Int,
    onFiltersChange: (BrowseFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onShowResults: () -> Unit,
) {
    val resultCount = remember(items, filters, mainColors) {
        items.count { item -> filters.matches(item, mainColors) }
    }
    val categoryTags = availableTags.filter { it.categoryId == "category" }
    val seasonTags = availableTags.filter { it.categoryId == "season" }
    val occasionTags = availableTags.filter { it.categoryId == "occasion" }
    val locationTags = availableTags.filter { it.categoryId == "location" }


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.advanced_filters_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.advanced_filters_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onResetFilters, enabled = filters.hasActiveFilters) {
                    Text(stringResource(R.string.reset_filters))
                }
            }
        }

        item {
            FilterSection(title = stringResource(R.string.filter_favorites)) {
                FilterChip(
                    selected = filters.favoritesOnly,
                    onClick = { onFiltersChange(filters.toggleFavoritesOnly()) },
                    label = { Text(stringResource(R.string.favorites_only)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (filters.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_category), icon = categoryIconFor("category")) {
                FilterTagChips(
                    tags = categoryTags,
                    selectedTagIds = filters.selectedTagIds,
                    emptyText = stringResource(R.string.filters_no_tags),
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag.id)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_season), icon = categoryIconFor("season")) {
                FilterTagChips(
                    tags = seasonTags,
                    selectedTagIds = filters.selectedTagIds,
                    emptyText = stringResource(R.string.filters_no_tags),
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag.id)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_occasion), icon = categoryIconFor("occasion")) {
                FilterTagChips(
                    tags = occasionTags,
                    selectedTagIds = filters.selectedTagIds,
                    emptyText = stringResource(R.string.filters_no_tags),
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag.id)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_location), icon = categoryIconFor("location")) {
                FilterTagChips(
                    tags = locationTags,
                    selectedTagIds = filters.selectedTagIds,
                    emptyText = stringResource(R.string.filters_no_tags),
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag.id)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_color_palette)) {
                ColorPaletteChips(
                    colors = mainColors,
                    selectedColorIds = filters.selectedPaletteColorIds,
                    onColorToggled = { color -> onFiltersChange(filters.togglePaletteColor(color.id)) },
                )
            }
        }
        if (allItemCount > 0 && resultCount == 0 && filters.hasActiveFilters) {
            item { EmptyFiltersCard(onResetFilters = onResetFilters) }
        }
        item {
            Button(
                onClick = onShowResults,
                modifier = Modifier.fillMaxWidth(),
                enabled = allItemCount == 0 || resultCount > 0 || !filters.hasActiveFilters,
            ) {
                Text(stringResource(R.string.show_results_count, resultCount))
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTagChips(
    tags: List<GarmentTag>,
    selectedTagIds: Set<String>,
    emptyText: String,
    onTagToggled: (GarmentTag) -> Unit,
) {
    if (tags.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            val selected = tag.id in selectedTagIds
            FilterChip(
                selected = selected,
                onClick = { onTagToggled(tag) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteChips(
    colors: List<MainColor>,
    selectedColorIds: Set<String>,
    onColorToggled: (MainColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selectedColorIds.isEmpty()) {
            Text(
                text = stringResource(R.string.filters_no_colors_selected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.filters_colors_selected_count, selectedColorIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (colors.isEmpty()) {
            Text(
                text = stringResource(R.string.filters_no_colors),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                colors.forEach { color ->
                    val selected = color.id in selectedColorIds
                    val swatchDescription = stringResource(R.string.content_color_swatch, color.name)
                    FilterChip(
                        selected = selected,
                        onClick = { onColorToggled(color) },
                        label = { Text(color.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(color.swatchColor())
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .semantics { contentDescription = swatchDescription },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun BrowseFilterState.toggleTag(tagId: String): BrowseFilterState = copy(
    selectedTagIds = selectedTagIds.toggle(tagId),
)

private fun BrowseFilterState.togglePaletteColor(colorId: String): BrowseFilterState = copy(
    selectedPaletteColorIds = selectedPaletteColorIds.toggle(colorId),
)

private fun BrowseFilterState.toggleFavoritesOnly(): BrowseFilterState = copy(
    favoritesOnly = !favoritesOnly,
)

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun UiWardrobeItem.matchesAnyPaletteColor(colors: List<MainColor>): Boolean = colors.any { color ->
    primaryPaletteColorId == color.id ||
        secondaryPaletteColorId == color.id ||
        primaryPaletteFallbackMatches(color) ||
        secondaryPaletteFallbackMatches(color)
}

private fun UiWardrobeItem.primaryPaletteFallbackMatches(color: MainColor): Boolean =
    primaryPaletteColorId.isNullOrBlank() &&
        color.matchesStoredPaletteValue(primaryPaletteColorName, primaryPaletteColorHex ?: primaryRawValue)

private fun UiWardrobeItem.secondaryPaletteFallbackMatches(color: MainColor): Boolean =
    secondaryPaletteColorId.isNullOrBlank() &&
        color.matchesStoredPaletteValue(secondaryPaletteColorName, secondaryPaletteColorHex ?: secondaryRawValue)

private fun MainColor.matchesStoredPaletteValue(name: String?, hex: String?): Boolean =
    this.name.equals(name, ignoreCase = true) || normalizedHex?.let { it == hex.normalizedHexOrNull() } == true

private val MainColor.normalizedHex: String?
    get() = hex.normalizedHexOrNull()

private fun MainColor.swatchColor(): Color = hex.toComposeColor() ?: Color(0xFFDADADA)

private fun String?.normalizedHexOrNull(): String? {
    val normalized = this?.trim()?.removePrefix("#") ?: return null
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return normalized.uppercase(Locale.US)
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun UiWardrobeItem.toGarmentShareItem(): GarmentShareItem = GarmentShareItem(
    name = name,
    notes = notes,
    imageUri = Uri.parse(photoUri.orEmpty()),
    metadata = listOf(
        GarmentShareMetadata(stringResource(R.string.metadata_category), tags.valuesInCategory("category")),
        GarmentShareMetadata(stringResource(R.string.metadata_season), tags.valuesInCategory("season")),
        GarmentShareMetadata(stringResource(R.string.metadata_fit), fitValue?.fitLabel()?.let { listOf(it) }.orEmpty()),
        GarmentShareMetadata(stringResource(R.string.metadata_location), tags.valuesInCategory("location")),
        GarmentShareMetadata(stringResource(R.string.metadata_occasions), tags.valuesInCategory("occasion")),
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

private fun String.toComposeColor(): Color? = normalizedHexOrNull()?.let { normalized ->
    Color(android.graphics.Color.parseColor("#$normalized"))
}

@Composable
private fun LanguageSettingsScreen(innerPadding: PaddingValues) {
    val languages = stringArrayResource(R.array.language_choices)

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
                    text = stringResource(R.string.language_settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.language_settings_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                languages.forEachIndexed { index, language ->
                    ListItem(
                        headlineContent = { Text(language) },
                        leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    )
                    if (index != languages.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun List<ClothingItem>.toUiWardrobeItems(
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
): List<UiWardrobeItem> = map { item ->
    UiWardrobeItem(
        id = item.id,
        name = "",
        subtitle = item.notes.ifBlank { stringResource(driveSyncConnectionStatus.itemStatusLabelRes) },
        notes = item.notes,
        photoUri = item.photoUri,
        tags = item.tags.map { tag -> UiTag(tag.id, tag.categoryId, tag.localizedLabel()) },
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
    )
}

private val DriveSyncConnectionStatus.itemStatusLabelRes: Int
    get() = when (this) {
        DriveSyncConnectionStatus.Connected -> R.string.drive_sync_status_connected
        DriveSyncConnectionStatus.Syncing -> R.string.drive_sync_status_syncing
        DriveSyncConnectionStatus.NeedsAttention -> R.string.drive_sync_status_needs_attention
        DriveSyncConnectionStatus.Disabled,
        DriveSyncConnectionStatus.NotConfigured,
        DriveSyncConnectionStatus.Disconnected -> R.string.wardrobe_item_saved_locally
    }

@Composable
private fun GarmentTag.localizedLabel(): String = localizedTagLabel()

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

private fun DisplayColorLabel.swatchColor(): Color = when (this) {
    DisplayColorLabel.Black -> Color(0xFF1F1F1F)
    DisplayColorLabel.Blue -> Color(0xFF315F8E)
    DisplayColorLabel.Brown -> Color(0xFF8B6848)
    DisplayColorLabel.Gray -> Color(0xFF8E8E8E)
    DisplayColorLabel.Green -> Color(0xFF5F6F48)
    DisplayColorLabel.Orange -> Color(0xFFC56F33)
    DisplayColorLabel.Pink -> Color(0xFFD4879A)
    DisplayColorLabel.Purple -> Color(0xFF765A91)
    DisplayColorLabel.Red -> Color(0xFF9E3D35)
    DisplayColorLabel.White -> Color(0xFFF8F9FA)
    DisplayColorLabel.Yellow -> Color(0xFFD6B84C)
    DisplayColorLabel.Multicolor -> Color(0xFFA56639)
    DisplayColorLabel.Unknown -> Color(0xFFDADADA)
}

@Composable
private fun Int.fitLabel(): String = when (coerceIn(0, 4)) {
    0 -> stringResource(R.string.fit_does_not_fit)
    1 -> stringResource(R.string.fit_snug)
    2 -> stringResource(R.string.fit_good)
    3 -> stringResource(R.string.fit_relaxed)
    else -> stringResource(R.string.fit_oversized)
}

@Preview(showBackground = true)
@Composable
private fun RobiaAppPreview() {
    RobiaTheme {
        RobiaShell(
            settings = RobiaSettings(),
            syncState = WardrobeSyncState.notConfigured(),
            clothingItems = emptyList(),
            tagCategories = emptyList(),
            availableTags = emptyList(),
            mainColors = emptyList(),
            onLanguageSelected = {},
            onDeveloperModeUnlocked = {},
            onDeveloperModeEnabledChange = {},
            onSaveItem = {},
            onSaveItems = {},
            onDeleteItems = {},
            onSaveTag = {},
            onSaveMainColor = {},
            onDeleteCustomTag = {},
            onDeleteMainColor = {},
            onRestoreDefaultTags = {},
            onRestoreDefaultMainColors = {},
        )
    }
}
