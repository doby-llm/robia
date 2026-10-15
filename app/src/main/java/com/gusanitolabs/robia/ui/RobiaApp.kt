package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.BuildConfig

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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.designsystem.RobiaTheme
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.PhotoRestoreState
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.TagRepository
import com.gusanitolabs.robia.data.WardrobeRepository
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.media.GarmentShareColor
import com.gusanitolabs.robia.media.GarmentShareExporter
import com.gusanitolabs.robia.media.GarmentShareItem
import com.gusanitolabs.robia.media.GarmentShareMetadata
import com.gusanitolabs.robia.media.GarmentShareMetadataIcon
import com.gusanitolabs.robia.media.ImagePipeline
import com.gusanitolabs.robia.media.ImagePriority
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.media.ImageRequest
import com.gusanitolabs.robia.media.ImageTargetBounds
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import com.gusanitolabs.robia.suggestions.BackfillProgress
import com.gusanitolabs.robia.suggestions.DevBenchmark
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.SavedExample
import com.gusanitolabs.robia.suggestions.SuggestionCapture
import com.gusanitolabs.robia.suggestions.SuggestionService
import com.gusanitolabs.robia.suggestions.engine.EmbeddingFailure
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelDownloadWorker
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.MergeSummary
import com.gusanitolabs.robia.sync.NoOpWardrobeSyncGateway
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind
import com.gusanitolabs.robia.sync.WardrobeSyncGateway
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import com.gusanitolabs.robia.sync.WardrobeSyncState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val DEVELOPER_UNLOCK_TAP_COUNT = 10
private const val DEVELOPER_UNLOCK_WINDOW_MILLIS = 5_000L
private const val GRID_THUMBNAIL_MAX_EDGE_PX = 384
private const val DETAIL_DISPLAY_MAX_EDGE_PX = 512

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

    data object Settings : RobiaRoute {
        override val titleRes = R.string.settings
    }

    data object DeveloperSyncLog : RobiaRoute {
        override val titleRes = R.string.restore_sync_log_title
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
    val syncStatus: GarmentSyncStatus,
    val syncDirtyAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
    val syncFailureMessage: String?,
    val photoRestoreState: PhotoRestoreState,
)

private data class UiTag(
    val id: String,
    val categoryId: String,
    val label: String,
)

private data class BrowseFilterState(
    val selectedTagIds: Set<String> = emptySet(),
    val selectedUnsetTagCategoryIds: Set<String> = emptySet(),
    val selectedPaletteColorIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = selectedTagIds.isNotEmpty() ||
            selectedUnsetTagCategoryIds.isNotEmpty() ||
            selectedPaletteColorIds.isNotEmpty() ||
            favoritesOnly

    val activeFilterCount: Int
        get() = selectedTagIds.size +
            selectedUnsetTagCategoryIds.size +
            selectedPaletteColorIds.size +
            if (favoritesOnly) 1 else 0

    fun matches(item: UiWardrobeItem, paletteColors: List<MainColor>): Boolean {
        val assignedTags = item.tags.filter { tag -> tag.id.isNotBlank() && tag.label.isNotBlank() }
        val itemTagIds = assignedTags.map(UiTag::id).toSet()
        val itemTagCategoryIds = assignedTags.map(UiTag::categoryId).toSet()
        return (!favoritesOnly || item.isFavorite) &&
            selectedTagIds.all(itemTagIds::contains) &&
            selectedUnsetTagCategoryIds.none(itemTagCategoryIds::contains) &&
            (selectedPaletteColorIds.isEmpty() || item.matchesAnyPaletteColor(selectedPaletteColors(paletteColors)))
    }

    private fun selectedPaletteColors(paletteColors: List<MainColor>): List<MainColor> =
        paletteColors.filter { color -> color.id in selectedPaletteColorIds }
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
    onRequestCloudSetup: () -> Unit = {},
    performanceFixtureUris: List<String> = emptyList(),
    performanceBatch: Boolean = false,
) {
    val persistedSettings by settingsRepository.settings.collectAsState(initial = null)
    val settingsLoaded = persistedSettings != null
    val settings = persistedSettings ?: RobiaSettings(cloudSetupPromptInteracted = true)
    val syncState by syncGateway.state.collectAsState(initial = WardrobeSyncState.notConfigured())
    val restoreSyncLogText by syncGateway.restoreSyncLogText.collectAsState(initial = "")
    val displaySyncState = syncState.reconcileWithSettings(settings.driveSyncConnectionStatus)
    val loadedClothingItems by wardrobeRepository.observeActiveItems().collectAsState(initial = null)
    val performanceFixtureMode = BuildConfig.DEBUG && performanceFixtureUris.isNotEmpty()
    val clothingItems = if (performanceFixtureMode) {
        performanceFixtureUris.mapIndexed { index, uri ->
            ClothingItem(
                id = "performance-fixture-$index",
                name = "Synthetic fixture ${index + 1}",
                photoUri = uri,
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            )
        }
    } else {
        loadedClothingItems.orEmpty()
    }
    val wardrobeItemsLoading = !performanceFixtureMode && loadedClothingItems == null
    val pendingGarmentSyncCount by wardrobeRepository.observePendingGarmentSyncCount().collectAsState(initial = 0)
    val tagCategories by tagRepository.observeCategories().collectAsState(initial = emptyList())
    val availableTags by tagRepository.observeTags().collectAsState(initial = emptyList())
    val mainColors by tagRepository.observeMainColors().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as RobiaApplication
    val embeddingRepository = remember(app) { EmbeddingRepository(app.database.embeddingDao()) }
    val suggestionService = remember(app, wardrobeRepository, embeddingRepository) {
        SuggestionService(
            embedder = app.embeddingEngineHolder,
            isModelReady = { app.modelStore.state.value is ModelState.Ready },
            encodeImage = { uri -> ImageInputEncoder.encode(app, uri) },
            savedExamples = {
                val vectors = embeddingRepository.observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID).first()
                wardrobeRepository.observeActiveItems().first().mapNotNull { item ->
                    vectors[item.id]?.image?.let { image -> SavedExample(item.id, item.tags.map(GarmentTag::id), image) }
                }
            },
            preloadEngine = { app.embeddingEngineHolder.preload() },
        )
    }
    val modelState by app.modelStore.state.collectAsState()
    val modelPromptShown by settingsRepository.modelPromptShown.collectAsState(initial = null)
    LaunchedEffect(tagRepository) {
        tagRepository.seedDefaultsIfNeeded()
    }

    LocalizedRobiaContent(settings.languagePreference) {
        RobiaTheme {
        val displaySettings = settings.copy(driveSyncConnectionStatus = displaySyncState.connectionStatus)
        RobiaShell(
            suggestionService = suggestionService,
            settings = displaySettings,
            settingsLoaded = settingsLoaded,
            syncState = displaySyncState,
            restoreSyncLogText = restoreSyncLogText,
            pendingGarmentSyncCount = pendingGarmentSyncCount,
            clothingItems = clothingItems,
            wardrobeItemsLoading = wardrobeItemsLoading,
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
            onCloudSetupPromptInteracted = {
                scope.launch { settingsRepository.markCloudSetupPromptInteracted() }
            },
            onRequestCloudSetup = onRequestCloudSetup,
            onConfirmMerge = { scope.launch { syncGateway.confirmMerge() } },
            onCancelMerge = { scope.launch { syncGateway.cancelMerge() } },
            onUseBackup = { scope.launch { syncGateway.useBackup() } },
            onKeepThisPhone = { scope.launch { syncGateway.keepThisPhone() } },
            onRetryAttention = { kind ->
                scope.launch { syncGateway.enqueue(WardrobeSyncOperation.RetryAttention(kind)) }
            },
            onKeepWithoutPhoto = { garmentId ->
                scope.launch { syncGateway.keepWithoutPhoto(garmentId) }
            },
            modelState = modelState,
            modelPromptShown = modelPromptShown,
            onModelPromptShown = {
                scope.launch { settingsRepository.setModelPromptShown(true) }
            },
            onRequestCloudManualSync = {
                scope.launch {
                    // Explicit Backup again is the only path that re-enables sync after deletion.
                    settingsRepository.setDriveBackupDeletionState(com.gusanitolabs.robia.core.model.DriveBackupDeletionState.None)
                    settingsRepository.setDriveSyncConnectionStatus(DriveSyncConnectionStatus.Connected)
                    syncGateway.enqueue(WardrobeSyncOperation.ExportFullSnapshot())
                }
            },
            onRequestCloudRestoreRetry = {
                scope.launch { syncGateway.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L)) }
            },
            onRequestCloudBackupDeletion = {
                scope.launch { syncGateway.enqueue(WardrobeSyncOperation.DeleteCloudBackup()) }
            },
            onRetryRestoredPhoto = { garmentId ->
                scope.launch { syncGateway.enqueue(WardrobeSyncOperation.RetryRestoredPhoto(garmentId)) }
            },
            onClearRestoreSyncLog = {
                scope.launch { syncGateway.clearRestoreSyncLog() }
            },
            onSaveItem = { item, capture ->
                scope.launch {
                    wardrobeRepository.upsertItem(item)
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertItem(item.id))
                    app.appScope.launch { storeSuggestionVectors(app, embeddingRepository, item, capture) }
                }
            },
            onSaveItems = { items, captures ->
                scope.launch {
                    wardrobeRepository.upsertItems(items)
                    items.forEach { item -> syncGateway.enqueue(WardrobeSyncOperation.UpsertItem(item.id)) }
                    app.appScope.launch {
                        items.forEach { item -> storeSuggestionVectors(app, embeddingRepository, item, captures[item.id]) }
                    }
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
            onCommitMainColorChange = { changeSet, updatedItems ->
                scope.launch {
                    tagRepository.applyMainColorChange(
                        upsertColors = changeSet.colorsToUpsertForCommit(),
                        deleteColorIds = changeSet.colorIdsToDeleteForCommit(),
                        updatedItems = updatedItems,
                    )
                    syncGateway.enqueue(WardrobeSyncOperation.UpsertPalette(changeSet.afterPalette))
                    updatedItems.forEach { item -> syncGateway.enqueue(WardrobeSyncOperation.UpsertItem(item.id)) }
                }
            },
            initialPerformanceBatchFixtureUris = if (performanceFixtureMode && performanceBatch) performanceFixtureUris else emptyList(),
        )
        }
    }
}

private fun WardrobeSyncState.reconcileWithSettings(
    settingsConnectionStatus: DriveSyncConnectionStatus,
): WardrobeSyncState = if (
    connectionStatus == DriveSyncConnectionStatus.NotConfigured &&
    settingsConnectionStatus != DriveSyncConnectionStatus.NotConfigured
) {
    copy(connectionStatus = settingsConnectionStatus)
} else {
    this
}

private val WardrobeSyncState.hasVisibleSyncActivity: Boolean
    get() = connectionStatus == DriveSyncConnectionStatus.Syncing ||
        restoreProgress?.status == CloudRestoreStatus.Running

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

private fun ColorPaletteChangeSet.colorsToUpsertForCommit(): List<MainColor> = when (operation) {
    ColorPaletteOperation.Added,
    ColorPaletteOperation.Edited -> afterPalette.filter { color -> color.id == touchedColorId }
    ColorPaletteOperation.Deleted -> emptyList()
    ColorPaletteOperation.RestoredDefault -> afterPalette
}

private fun ColorPaletteChangeSet.colorIdsToDeleteForCommit(): List<String> = when (operation) {
    ColorPaletteOperation.Deleted -> listOfNotNull(touchedColorId)
    ColorPaletteOperation.RestoredDefault -> {
        val restoredIds = afterPalette.map(MainColor::id).toSet()
        beforePalette.map(MainColor::id).filterNot(restoredIds::contains)
    }
    ColorPaletteOperation.Added,
    ColorPaletteOperation.Edited -> emptyList()
}

private fun List<ClothingItem>.referencesPaletteColor(colorId: String): Boolean = any { item ->
    val metrics = item.colorMetrics
    !item.isArchived && (metrics.primaryPaletteColorId == colorId || metrics.secondaryPaletteColorId == colorId)
}

// Routes that render browse-style wardrobe items; the others skip building them.
// TagReview joins this set in TASK-E08.
private val WardrobeUiModelRoutes = setOf(
    RobiaRoute.Browse,
    RobiaRoute.ItemDetail,
    RobiaRoute.AdvancedFilters,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobiaShell(
    settings: RobiaSettings,
    settingsLoaded: Boolean,
    syncState: WardrobeSyncState,
    restoreSyncLogText: String,
    pendingGarmentSyncCount: Int,
    clothingItems: List<ClothingItem>,
    wardrobeItemsLoading: Boolean,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDeveloperModeUnlocked: () -> Unit,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onCloudSetupPromptInteracted: () -> Unit,
    onRequestCloudSetup: () -> Unit,
    onConfirmMerge: () -> Unit,
    onCancelMerge: () -> Unit,
    onUseBackup: () -> Unit = {},
    onKeepThisPhone: () -> Unit = {},
    onRetryAttention: (SyncAttentionKind) -> Unit = {},
    onKeepWithoutPhoto: (String) -> Unit = {},
    modelState: ModelState = ModelState.NotDownloaded,
    modelPromptShown: Boolean? = null,
    onModelPromptShown: () -> Unit = {},
    onRequestCloudManualSync: () -> Unit = {},
    onRequestCloudRestoreRetry: () -> Unit = {},
    onRequestCloudBackupDeletion: () -> Unit = {},
    onRetryRestoredPhoto: (String) -> Unit = {},
    onClearRestoreSyncLog: () -> Unit = {},
    suggestionService: SuggestionService,
    onSaveItem: (ClothingItem, SuggestionCapture?) -> Unit,
    onSaveItems: (List<ClothingItem>, Map<String, SuggestionCapture>) -> Unit,
    onDeleteItems: (List<String>) -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
    onRestoreDefaultTags: (TagCategory) -> Unit,
    onRestoreDefaultMainColors: () -> Unit,
    onCommitMainColorChange: (ColorPaletteChangeSet, List<ClothingItem>) -> Unit,
    initialPerformanceBatchFixtureUris: List<String> = emptyList(),
) {
    val routeStack = remember(initialPerformanceBatchFixtureUris) {
        mutableStateListOf(
            if (initialPerformanceBatchFixtureUris.isEmpty()) RobiaRoute.Browse else RobiaRoute.BatchAddClothing,
        )
    }
    val currentRoute = routeStack.last()
    val settingsTapTimestamps = remember { mutableStateListOf<Long>() }
    val context = LocalContext.current
    val developerModeUnlockedMessage = stringResource(R.string.developer_mode_unlocked)
    val cloudSetupConfiguredMessage = stringResource(R.string.cloud_setup_configured_status)
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var browseFilters by remember { mutableStateOf(BrowseFilterState()) }
    val batchDrafts = remember(initialPerformanceBatchFixtureUris) {
        mutableStateListOf<BatchDraftItem>().apply {
            initialPerformanceBatchFixtureUris.forEachIndexed { index, uri ->
                add(BatchDraftItem(orderIndex = index, originalPhotoUri = uri))
            }
        }
    }
    val batchProcessingScope = rememberCoroutineScope()
    val batchProcessingCoordinator = remember(batchProcessingScope) {
        BatchProcessingCoordinator(batchProcessingScope)
    }
    val batchBackgroundRemover = remember { PhotoBackgroundRemover() }
    var selectedBatchDraftId by remember { mutableStateOf<String?>(null) }
    var batchSaveStatusMessage by remember { mutableStateOf<String?>(null) }
    var showBatchDiscardDialog by remember { mutableStateOf(false) }
    var selectedBrowseItemIds by remember { mutableStateOf(emptySet<String>()) }
    var showBrowseDeleteDialog by remember { mutableStateOf(false) }
    var showColorReviewDiscardDialog by remember { mutableStateOf(false) }
    var pendingColorReviewChangeSet by remember { mutableStateOf<ColorPaletteChangeSet?>(null) }
    var activeColorReviewChangeSet by remember { mutableStateOf<ColorPaletteChangeSet?>(null) }
    var showCloudSetupPrompt by remember { mutableStateOf(false) }
    var showBackupDeletionConfirmation by remember { mutableStateOf(false) }
    var showUseBackupConfirmation by remember { mutableStateOf(false) }
    var conflictDialogDismissed by remember(syncState.conflict) { mutableStateOf(false) }
    // Merge confirmation is a dialog; every other attention kind is shown as a card in Browse, highest priority first.
    val browseAttentions = syncState.attention
        .filter { attention -> attention.kind != SyncAttentionKind.MergeConfirmation }
        .take(MAX_BROWSE_ATTENTION_CARDS)
    val needsWardrobeUiModels = currentRoute in WardrobeUiModelRoutes
    val items = remember(context, clothingItems, syncState, needsWardrobeUiModels) {
        if (needsWardrobeUiModels) clothingItems.toUiWardrobeItems(syncState, context) else emptyList()
    }
    val filteredItems = remember(items, browseFilters, mainColors, needsWardrobeUiModels) {
        if (needsWardrobeUiModels) {
            items.filter { item -> browseFilters.matches(item, mainColors) }
        } else {
            emptyList()
        }
    }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val selectedDomainItem = clothingItems.firstOrNull { it.id == selectedItemId }
    // Only an untouched phone is recommended to set up Drive at first run; connecting later goes through the merge summary.
    val isFirstRunCloudSetup = remember(clothingItems, tagCategories, availableTags, mainColors, syncState) {
        clothingItems.isEmpty() &&
            availableTags.none(DefaultTags::isCustomOrModifiedDefault) &&
            tagCategories.none(DefaultTags::isCustomOrModifiedDefault) &&
            mainColors.none(DefaultTags::isCustomOrModifiedDefault) &&
            syncState.pendingOperationCount == 0 &&
            !syncState.hasConflictingAccountBinding
    }

    LaunchedEffect(
        settingsLoaded,
        settings.driveSyncConnectionStatus,
        settings.cloudSetupPromptInteracted,
        isFirstRunCloudSetup,
    ) {
        if (settingsLoaded &&
            !settings.cloudSetupPromptInteracted &&
            settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured &&
            isFirstRunCloudSetup
        ) {
            onCloudSetupPromptInteracted()
            showCloudSetupPrompt = true
        }
    }

    var showModelPrompt by remember { mutableStateOf(false) }
    val cloudSetupPromptPending = settingsLoaded &&
        !settings.cloudSetupPromptInteracted &&
        settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured &&
        isFirstRunCloudSetup
    LaunchedEffect(modelPromptShown, modelState, showCloudSetupPrompt, cloudSetupPromptPending) {
        // A null preference means not loaded yet, so the prompt waits; the cloud-setup prompt goes first.
        if (!showCloudSetupPrompt &&
            !cloudSetupPromptPending &&
            shouldShowModelPrompt(promptShown = modelPromptShown ?: true, state = modelState)
        ) {
            showModelPrompt = true
        }
    }

    LaunchedEffect(items, needsWardrobeUiModels) {
        // Items are empty off the wardrobe routes; pruning then would drop the selection.
        if (!needsWardrobeUiModels) return@LaunchedEffect
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
            null,
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

    fun updateBatchDraft(updated: BatchDraftItem) {
        val index = batchDrafts.indexOfFirst { draft -> draft.id == updated.id }
        if (index >= 0) batchDrafts[index] = updated
    }

    fun processQueuedBatchDrafts() {
        batchProcessingCoordinator.processQueued(
            drafts = { batchDrafts.toList() },
            processDraft = { draft ->
                processBatchDraft(
                    draft = draft,
                    context = context,
                    backgroundRemover = batchBackgroundRemover,
                    mainColors = mainColors,
                    availableTags = availableTags,
                    suggestionService = suggestionService,
                    onDraftUpdated = ::updateBatchDraft,
                )
            },
            onInterrupted = { draft ->
                val current = batchDrafts.firstOrNull { it.id == draft.id }
                current
                    ?.interruptedForBatch(context.getString(R.string.batch_interrupted_message))
                    ?.let(::updateBatchDraft)
            },
        )
    }

    fun startBatchAdd(photoUris: List<String>) {
        batchProcessingCoordinator.cancel()
        batchDrafts.clear()
        batchSaveStatusMessage = null
        photoUris.forEachIndexed { index, uri ->
            batchDrafts += BatchDraftItem(orderIndex = index, originalPhotoUri = uri)
        }
        selectedBatchDraftId = null
        replaceRoute(RobiaRoute.BatchAddClothing)
        processQueuedBatchDrafts()
    }

    fun retryBatchDraft(draft: BatchDraftItem) {
        updateBatchDraft(draft.retryForBatch())
        batchSaveStatusMessage = null
        processQueuedBatchDrafts()
    }

    fun discardBatchDraft(draft: BatchDraftItem) {
        batchDrafts.removeAll { it.id == draft.id }
        if (selectedBatchDraftId == draft.id) selectedBatchDraftId = null
    }

    fun cancelBatchAdd() {
        batchProcessingCoordinator.cancel()
        batchDrafts.clear()
        batchSaveStatusMessage = null
        selectedBatchDraftId = null
        showBatchDiscardDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun saveMainColorAndOfferReview(color: MainColor) {
        val beforePalette = mainColors
        val existingColor = beforePalette.firstOrNull { existing -> existing.id == color.id }
        val operation = if (existingColor != null) ColorPaletteOperation.Edited else ColorPaletteOperation.Added
        val afterPalette = (beforePalette.filterNot { existing -> existing.id == color.id } + color)
            .sortedWith(compareBy<MainColor> { it.sortOrder }.thenBy { it.name }.thenBy { it.id })
        val changeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = afterPalette,
            touchedColorId = color.id,
            operation = operation,
            requiresCommitOnSave = existingColor?.let { previous ->
                (previous.name != color.name || previous.hex != color.hex) && clothingItems.referencesPaletteColor(color.id)
            } ?: false,
        )
        if (changeSet.requiresCommitOnSave) {
            pendingColorReviewChangeSet = null
            activeColorReviewChangeSet = changeSet
            replaceRoute(RobiaRoute.ColorReview)
        } else {
            onSaveMainColor(color)
            pendingColorReviewChangeSet = changeSet
        }
    }

    fun deleteMainColorAndOfferReview(color: MainColor) {
        val beforePalette = mainColors
        val afterPalette = beforePalette.filterNot { existing -> existing.id == color.id }
        val changeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = afterPalette,
            touchedColorId = color.id,
            operation = ColorPaletteOperation.Deleted,
            requiresCommitOnSave = clothingItems.referencesPaletteColor(color.id),
        )
        if (changeSet.requiresCommitOnSave) {
            pendingColorReviewChangeSet = null
            activeColorReviewChangeSet = changeSet
            replaceRoute(RobiaRoute.ColorReview)
        } else {
            onDeleteMainColor(color)
            pendingColorReviewChangeSet = changeSet
        }
    }

    fun restoreDefaultMainColorsAndStartReview() {
        val beforePalette = mainColors
        pendingColorReviewChangeSet = null
        activeColorReviewChangeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = DefaultTags.mainColors,
            touchedColorId = null,
            operation = ColorPaletteOperation.RestoredDefault,
            requiresCommitOnSave = true,
        )
        replaceRoute(RobiaRoute.ColorReview)
    }

    fun commitColorReview(changeSet: ColorPaletteChangeSet, updatedItems: List<ClothingItem>) {
        if (changeSet.requiresCommitOnSave) {
            onCommitMainColorChange(changeSet, updatedItems)
        } else if (updatedItems.isNotEmpty()) {
            onSaveItems(updatedItems, emptyMap())
        }
        activeColorReviewChangeSet = null
        pendingColorReviewChangeSet = null
        showColorReviewDiscardDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun requestColorReviewDiscard() {
        showColorReviewDiscardDialog = true
    }

    fun discardColorReviewAndRollbackPalette() {
        val changeSet = activeColorReviewChangeSet ?: return
        if (!changeSet.requiresCommitOnSave) {
            val beforeColorIds = changeSet.beforePalette.map(MainColor::id).toSet()
            changeSet.afterPalette
                .filterNot { color -> color.id in beforeColorIds }
                .forEach(onDeleteMainColor)
            changeSet.beforePalette.forEach(onSaveMainColor)
        }
        pendingColorReviewChangeSet = null
        activeColorReviewChangeSet = null
        showColorReviewDiscardDialog = false
        replaceRoute(RobiaRoute.Browse)
    }

    fun handleSettingsClick() {
        if (currentRoute != RobiaRoute.Settings) pushRoute(RobiaRoute.Settings)
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
        if (settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected ||
            settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NeedsAttention
        ) {
            onRequestCloudManualSync()
            Toast.makeText(context, context.getString(R.string.cloud_sync_started), Toast.LENGTH_SHORT).show()
        } else if (settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.Disabled) {
            onRequestCloudManualSync()
            Toast.makeText(context, context.getString(R.string.cloud_sync_started), Toast.LENGTH_SHORT).show()
        } else if (settings.driveSyncConnectionStatus != DriveSyncConnectionStatus.NotConfigured) {
            Toast.makeText(context, cloudSetupConfiguredMessage, Toast.LENGTH_SHORT).show()
        } else {
            onRequestCloudSetup()
        }
    }

    fun openFirstGuardedGarment() {
        val guarded = items.firstOrNull { item -> item.hasMissingRestoredPhoto } ?: return
        selectedItemId = guarded.id
        pushRoute(RobiaRoute.ItemDetail)
    }

    fun openSyncStorage() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SYNC_STORAGE_URL)))
        }
    }

    fun handleAttentionAction(attention: SyncAttention, action: SyncAttentionAction) {
        when (action) {
            SyncAttentionAction.SignIn -> onRequestCloudSetup()
            SyncAttentionAction.Retry -> onRetryAttention(attention.kind)
            SyncAttentionAction.OpenStorage -> openSyncStorage()
            // Photo actions act on one garment, so the card opens the first affected garment's detail.
            SyncAttentionAction.RetryPhoto,
            SyncAttentionAction.KeepWithoutPhoto -> openFirstGuardedGarment()
            SyncAttentionAction.UseBackup -> showUseBackupConfirmation = true
            SyncAttentionAction.KeepThisPhone -> onKeepThisPhone()
            SyncAttentionAction.ConfirmMerge -> onConfirmMerge()
            SyncAttentionAction.CancelMerge -> onCancelMerge()
        }
    }

    fun dismissCloudSetupPrompt() {
        onCloudSetupPromptInteracted()
        showCloudSetupPrompt = false
    }

    fun confirmCloudSetupPrompt() {
        onCloudSetupPromptInteracted()
        showCloudSetupPrompt = false
        if (isFirstRunCloudSetup) {
            onRequestCloudSetup()
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

    if (showBackupDeletionConfirmation) {
        AlertDialog(
            onDismissRequest = { showBackupDeletionConfirmation = false },
            title = { Text(stringResource(R.string.drive_backup_delete_title)) },
            text = { Text(stringResource(R.string.drive_backup_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackupDeletionConfirmation = false
                    onRequestCloudBackupDeletion()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDeletionConfirmation = false }) { Text(stringResource(R.string.cancel)) }
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

    if (showCloudSetupPrompt) {
        CloudSetupDialog(
            onDismiss = ::dismissCloudSetupPrompt,
            onConfirmSetup = ::confirmCloudSetupPrompt,
        )
    }

    if (syncState.conflict && !conflictDialogDismissed && !showUseBackupConfirmation) {
        BackupConflictDialog(
            onUseBackup = { showUseBackupConfirmation = true },
            onKeepThisPhone = {
                conflictDialogDismissed = true
                onKeepThisPhone()
            },
            onNotNow = { conflictDialogDismissed = true },
        )
    }

    if (showUseBackupConfirmation) {
        UseBackupConfirmationDialog(
            onConfirm = {
                showUseBackupConfirmation = false
                conflictDialogDismissed = true
                onUseBackup()
            },
            onCancel = { showUseBackupConfirmation = false },
        )
    }

    syncState.pendingMergeSummary?.let { summary ->
        MergeConfirmationDialog(
            summary = summary,
            onConfirm = onConfirmMerge,
            onCancel = onCancelMerge,
        )
    }

    if (showModelPrompt) {
        ModelPromptDialog(
            onLater = {
                showModelPrompt = false
                onModelPromptShown()
            },
            onDownloadNow = {
                showModelPrompt = false
                onModelPromptShown()
                startModelDownload(context)
            },
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
                currentRoute != RobiaRoute.Settings &&
                currentRoute != RobiaRoute.DeveloperSyncLog &&
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
            suggestionService = suggestionService,
            currentRoute = currentRoute,
            innerPadding = innerPadding,
            items = filteredItems,
            allItems = items,
            domainItems = clothingItems,
            wardrobeItemsLoading = wardrobeItemsLoading,
            totalItemCount = items.size,
            filters = browseFilters,
            selectedBrowseItemIds = selectedBrowseItemIds,
            selectedItem = selectedItem,
            selectedDomainItem = selectedDomainItem,
            batchDrafts = batchDrafts,
            selectedBatchDraft = batchDrafts.firstOrNull { draft -> draft.id == selectedBatchDraftId },
            batchSaveStatusMessage = batchSaveStatusMessage,
            tagCategories = tagCategories,
            availableTags = availableTags,
            mainColors = mainColors,
            colorReviewChangeSet = activeColorReviewChangeSet,
            developerModeEnabled = settings.developerModeUnlocked && settings.developerModeEnabled,
            restoreSyncLogText = restoreSyncLogText,
            syncState = syncState,
            currentLanguage = settings.languagePreference,
            driveSyncConnectionStatus = settings.driveSyncConnectionStatus,
            pendingGarmentSyncCount = pendingGarmentSyncCount,
            cloudSetupSummaryRes = settings.driveSyncConnectionStatus.cloudSetupSummaryRes(
                canAttemptCloudSetup = isFirstRunCloudSetup,
                pendingGarmentSyncCount = pendingGarmentSyncCount,
            ),
            developerModeUnlocked = settings.developerModeUnlocked,
            driveBackupDeletionState = settings.driveBackupDeletionState,
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
            onSaveItem = { item, capture ->
                onSaveItem(item, capture)
                selectedItemId = item.id
                replaceRoute(RobiaRoute.Browse)
                pushRoute(RobiaRoute.ItemDetail)
            },
            onDeleteItem = { item -> deleteItemsAndReturnToBrowse(setOf(item.id)) },
            onBatchPhotosSelected = ::startBatchAdd,
            onDraftSelected = { draft ->
                selectedBatchDraftId = draft.id
                pushRoute(RobiaRoute.BatchEditClothing)
            },
            onRetryBatchDraft = ::retryBatchDraft,
            onDiscardBatchDraft = ::discardBatchDraft,
            onSaveBatch = { requestedDraftIds ->
                // Revalidate the UI's accepted-ID snapshot against the latest state immediately
                // before creating payloads. A late failure, cancellation, or review change must
                // not slip into this save operation.
                val acceptedDrafts = batchDrafts.toList().acceptedForSave(requestedDraftIds)
                val batchItems = acceptedDrafts.map { draft ->
                    draft.toClothingItem(availableTags, mainColors)
                }
                val batchCaptures = acceptedDrafts.mapNotNull { draft -> draft.suggestionCapture?.let { draft.id to it } }.toMap()
                if (batchItems.isNotEmpty()) onSaveItems(batchItems, batchCaptures)
                val savedIds = acceptedDrafts.map(BatchDraftItem::id).toSet()
                batchDrafts.removeAll { it.id in savedIds }
                batchSaveStatusMessage = context.getString(
                    R.string.batch_save_result,
                    savedIds.size,
                    batchDrafts.size,
                )
                selectedBatchDraftId = null
                if (batchDrafts.isEmpty()) replaceRoute(RobiaRoute.Browse)
            },
            onCancelBatch = {
                cancelBatchAdd()
            },
            onSaveBatchDraft = { item, acceptOriginalPhoto, capture ->
                batchDrafts.firstOrNull { draft -> draft.id == item.id }?.let { previous ->
                    updateBatchDraft(
                        item.toBatchDraftFromExisting(
                            previous = previous,
                            mainColors = mainColors,
                            acceptOriginalPhoto = acceptOriginalPhoto,
                        ).copy(suggestionCapture = capture ?: previous.suggestionCapture),
                    )
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
            onCommitColorReview = ::commitColorReview,
            onCloseColorReview = {
                activeColorReviewChangeSet = null
                replaceRoute(RobiaRoute.Browse)
            },
            onRequestColorReviewDiscard = ::requestColorReviewDiscard,
            onClearRestoreSyncLog = onClearRestoreSyncLog,
            onLanguageSelected = onLanguageSelected,
            onDeveloperModeEnabledChange = onDeveloperModeEnabledChange,
            onCloudSetupClick = ::requestCloudSetup,
            onDeleteBackupClick = { showBackupDeletionConfirmation = true },
            onRestoreSyncLogClick = { pushRoute(RobiaRoute.DeveloperSyncLog) },
            onRetryRestoredPhoto = onRetryRestoredPhoto,
            browseAttentions = browseAttentions,
            onSyncAttentionAction = ::handleAttentionAction,
            onKeepWithoutPhoto = onKeepWithoutPhoto,
            onRetryAttention = onRetryAttention,
        )
    }
    restoreProgress?.let { progress ->
        CloudRestoreProgressOverlay(
            progress = progress,
            developerModeUnlocked = settings.developerModeUnlocked,
            developerModeEnabled = settings.developerModeEnabled,
            onRetry = onRequestCloudRestoreRetry,
        )
    }
    }
}

@Composable
private fun SettingsScreen(
    innerPadding: PaddingValues,
    currentLanguage: LanguagePreference,
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    pendingGarmentSyncCount: Int,
    @StringRes cloudSetupSummaryRes: Int,
    developerModeUnlocked: Boolean,
    developerModeEnabled: Boolean,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onLanguageClick: () -> Unit,
    onCloudSetupClick: () -> Unit,
    driveBackupDeletionState: com.gusanitolabs.robia.core.model.DriveBackupDeletionState,
    onDeleteBackupClick: () -> Unit,
    onRestoreSyncLogClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val modelState by app.modelStore.state.collectAsState()
    val modelWorkQueued by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
            .map { infos -> infos.any { info -> info.state == WorkInfo.State.ENQUEUED } }
    }.collectAsState(initial = false)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var benchmarkText by remember { mutableStateOf<String?>(null) }
    var benchmarkRunning by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SmartSuggestionsSettingsSection(
                state = settingsModelState(modelState, modelWorkQueued),
                onDownload = { startModelDownload(context) },
                onUseMobileData = {
                    scope.launch {
                        app.settingsRepository.setModelMobileDataApproved(true)
                        startModelDownload(context, allowMobileData = true, restart = true)
                    }
                },
                onCancel = { cancelModelDownload(context) },
                onDelete = {
                    scope.launch {
                        cancelModelDownload(context)
                        app.embeddingEngineHolder.unload()
                        app.modelStore.delete()
                    }
                },
                onRetry = {
                    scope.launch {
                        val approved = app.settingsRepository.modelMobileDataApproved.first()
                        startModelDownload(context, allowMobileData = approved, restart = true)
                    }
                },
            )
        }
        item {
            SettingsSection(title = stringResource(R.string.language)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(stringResource(currentLanguage.labelRes)) },
                    leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onLanguageClick),
                )
                LanguagePreference.entries.forEach { language ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(language.labelRes),
                                fontWeight = if (language == currentLanguage) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingContent = if (language == currentLanguage) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onLanguageSelected(language) },
                    )
                }
            }
        }
        if (developerModeUnlocked) {
            item {
                SettingsSection(title = stringResource(R.string.developer_mode)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.developer_mode)) },
                        supportingContent = { Text(stringResource(R.string.developer_mode_summary)) },
                        leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = developerModeEnabled,
                                onCheckedChange = onDeveloperModeEnabledChange,
                            )
                        },
                        modifier = Modifier.clickable { onDeveloperModeEnabledChange(!developerModeEnabled) },
                    )
                    if (developerModeEnabled) {
                        Divider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.restore_sync_log_title)) },
                            supportingContent = { Text(stringResource(R.string.restore_sync_log_summary)) },
                            leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onRestoreSyncLogClick),
                        )
                        Divider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.dev_model_benchmark)) },
                            supportingContent = {
                                val result = benchmarkText
                                if (benchmarkRunning) {
                                    Text(stringResource(R.string.dev_benchmark_running))
                                } else if (result != null) {
                                    Text(
                                        text = result,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    TextButton(
                                        enabled = !benchmarkRunning,
                                        onClick = {
                                            benchmarkRunning = true
                                            scope.launch {
                                                benchmarkText = try {
                                                    DevBenchmark(
                                                        context = context,
                                                        holder = app.embeddingEngineHolder,
                                                        syncLog = app.restoreSyncLogRepository,
                                                    ).measure()
                                                } catch (failure: Exception) {
                                                    context.getString(
                                                        R.string.dev_benchmark_failed,
                                                        failure.message ?: failure.javaClass.simpleName,
                                                    )
                                                } finally {
                                                    benchmarkRunning = false
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.dev_run))
                                    }
                                    TextButton(
                                        enabled = benchmarkText != null && !benchmarkRunning,
                                        onClick = { clipboard.setText(AnnotatedString(benchmarkText.orEmpty())) },
                                    ) {
                                        Text(stringResource(R.string.dev_copy))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.data_sync)) {
                if (driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected ||
                    driveSyncConnectionStatus == DriveSyncConnectionStatus.NeedsAttention
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.drive_backup_delete_menu)) },
                        leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onDeleteBackupClick),
                    )
                    Divider()
                }
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (driveBackupDeletionState == com.gusanitolabs.robia.core.model.DriveBackupDeletionState.Complete ||
                                    driveBackupDeletionState == com.gusanitolabs.robia.core.model.DriveBackupDeletionState.NeedsAttention
                                ) {
                                    R.string.drive_backup_again
                                } else {
                                    R.string.data_sync_google_drive
                                },
                            ),
                        )
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(driveSyncConnectionStatus.statusLabelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = cloudQueueSummaryText(driveSyncConnectionStatus, pendingGarmentSyncCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(cloudSetupSummaryRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (driveBackupDeletionState != com.gusanitolabs.robia.core.model.DriveBackupDeletionState.None) {
                                Text(
                                    text = stringResource(driveBackupDeletionState.statusRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(driveSyncConnectionStatus.settingsIcon, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onCloudSetupClick),
                )
            }
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            content()
        }
    }
}

@StringRes
private fun DriveSyncConnectionStatus.cloudSetupSummaryRes(
    canAttemptCloudSetup: Boolean,
    pendingGarmentSyncCount: Int,
): Int = when (this) {
    DriveSyncConnectionStatus.NotConfigured -> if (canAttemptCloudSetup) {
        R.string.cloud_setup_recommended_summary
    } else {
        R.string.cloud_setup_late_blocked_summary
    }
    DriveSyncConnectionStatus.Disabled -> R.string.cloud_setup_late_blocked_summary
    DriveSyncConnectionStatus.Disconnected -> R.string.garment_sync_auth_blocked
    DriveSyncConnectionStatus.Connected -> if (pendingGarmentSyncCount > 0) {
        R.string.garment_sync_queued
    } else {
        R.string.cloud_setup_configured_summary
    }
    DriveSyncConnectionStatus.Syncing -> R.string.garment_sync_syncing
    DriveSyncConnectionStatus.NeedsAttention -> R.string.cloud_setup_configured_summary
}

private val DriveSyncConnectionStatus.settingsIcon: ImageVector
    get() = when (this) {
        DriveSyncConnectionStatus.Connected -> Icons.Rounded.CloudDone
        DriveSyncConnectionStatus.Syncing -> Icons.Rounded.CloudUpload
        DriveSyncConnectionStatus.NeedsAttention -> Icons.Rounded.Error
        DriveSyncConnectionStatus.Disabled,
        DriveSyncConnectionStatus.NotConfigured,
        DriveSyncConnectionStatus.Disconnected -> Icons.Rounded.CloudOff
    }

@Composable
private fun cloudQueueSummaryText(
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    pendingGarmentSyncCount: Int,
): String = when {
    pendingGarmentSyncCount <= 0 && driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured ->
        stringResource(R.string.garment_sync_cloud_setup_required)
    pendingGarmentSyncCount <= 0 && driveSyncConnectionStatus == DriveSyncConnectionStatus.Disabled ->
        stringResource(R.string.garment_sync_saved_device)
    pendingGarmentSyncCount <= 0 -> stringResource(R.string.garment_sync_all_synced)
    driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured ||
        driveSyncConnectionStatus == DriveSyncConnectionStatus.Disabled ->
        stringResource(R.string.garment_sync_local_changes_saved, pendingGarmentSyncCount)
    pendingGarmentSyncCount == 1 -> stringResource(R.string.garment_sync_pending_count_one)
    else -> stringResource(R.string.garment_sync_pending_count_many, pendingGarmentSyncCount)
}

@Composable
private fun CloudSetupDialog(
    onDismiss: () -> Unit,
    onConfirmSetup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_setup_prompt_title)) },
        text = { Text(stringResource(R.string.cloud_setup_prompt_body)) },
        confirmButton = {
            TextButton(onClick = onConfirmSetup) { Text(stringResource(R.string.cloud_setup_set_up)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) }
        },
    )
}

@Composable
private fun BackupConflictDialog(
    onUseBackup: () -> Unit,
    onKeepThisPhone: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.sync_attention_conflict_title)) },
        text = { Text(stringResource(R.string.sync_attention_conflict_body)) },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onUseBackup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_conflict_use_backup))
                }
                OutlinedButton(onClick = onKeepThisPhone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_conflict_keep_phone))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) }
        },
    )
}

@Composable
private fun UseBackupConfirmationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_use_backup_confirm_title)) },
        text = { Text(stringResource(R.string.sync_use_backup_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sync_use_backup_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun MergeConfirmationDialog(
    summary: MergeSummary,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_merge_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.sync_merge_body,
                        summary.remoteGarmentCount,
                        summary.localGarmentCount,
                        summary.mergedGarmentCount,
                    ),
                )
                Text(
                    text = stringResource(R.string.sync_merge_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sync_merge_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CloudRestoreProgressOverlay(
    progress: CloudRestoreProgress,
    developerModeUnlocked: Boolean,
    developerModeEnabled: Boolean,
    onRetry: () -> Unit,
) {
    val statusText = cloudRestoreStatusText(progress)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val effectiveDeveloperMode = developerModeUnlocked && developerModeEnabled
    val diagnostics = progress.diagnostics.takeIf { effectiveDeveloperMode }
    val diagnosticsText = diagnostics?.toCopyText().orEmpty()
    val diagnosticsCopiedMessage = stringResource(R.string.cloud_restore_diagnostics_copied)
    var etaState by remember(progress.diagnostics?.correlationId, progress.status) {
        mutableStateOf(RestoreEtaState())
    }
    LaunchedEffect(
        progress.diagnostics?.correlationId,
        progress.status,
        progress.byteProgress?.completedBytes,
        progress.byteProgress?.totalBytes,
    ) {
        etaState = etaState.updated(progress, SystemClock.elapsedRealtime())
    }
    val eta = etaState.estimate(progress)
    var showDiagnostics by remember(diagnostics?.correlationId, progress.status) {
        mutableStateOf(progress.status != CloudRestoreStatus.Running)
    }
    val stageFraction = progress.stageBarFraction()?.coerceIn(0f, 1f)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = statusText }
            .clickable(onClick = {}),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_restore_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (stageFraction != null) {
                                // Keyed per stage: a new stage starts at its own value, never animating down from 100%.
                                key(progress.phase) {
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = stageFraction,
                                        animationSpec = tween(durationMillis = 450),
                                        label = "cloudRestoreProgress",
                                    )
                                    CloudRestoreProgressBar(progress = animatedProgress)
                                }
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(progress.phase.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        when (val stepLine = progress.stepLine()) {
                            RestoreStepLine.Hidden -> Unit
                            is RestoreStepLine.Steps -> Text(
                                text = stringResource(R.string.cloud_restore_remaining, stepLine.remaining, stepLine.total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            is RestoreStepLine.PhotosUploaded -> Text(
                                text = stringResource(
                                    R.string.cloud_restore_upload_photos_progress,
                                    stepLine.completed,
                                    stepLine.total,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            RestoreStepLine.Finishing -> Text(
                                text = stringResource(R.string.cloud_restore_finishing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        progress.itemProgress?.let { itemProgress ->
                            Text(
                                text = stringResource(
                                    R.string.cloud_restore_photo_item_progress,
                                    itemProgress.completedItems,
                                    itemProgress.totalItems,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        progress.byteProgress?.let { byteProgress ->
                            val byteProgressText = byteProgress.toDisplayText()
                            val byteText = byteProgressText.totalMegabytes?.let { total ->
                                stringResource(
                                    R.string.cloud_restore_byte_progress_known,
                                    byteProgressText.completedMegabytes,
                                    total,
                                )
                            } ?: stringResource(
                                R.string.cloud_restore_byte_progress_unknown,
                                byteProgressText.completedMegabytes,
                            )
                            Text(
                                text = byteText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        // Always composed, so the line keeps its height when the estimate appears or disappears.
                        Text(
                            text = eta?.let { estimate ->
                                when (estimate) {
                                    RestoreEta.LessThanMinute -> stringResource(R.string.cloud_restore_eta_less_than_minute)
                                    is RestoreEta.AboutMinutes -> stringResource(
                                        R.string.cloud_restore_eta_about_minutes,
                                        estimate.minutes,
                                    )
                                }
                            } ?: " ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress.status == CloudRestoreStatus.Running) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        progress.message?.takeIf(String::isNotBlank)?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (progress.status != CloudRestoreStatus.Running) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.cloud_restore_retry))
                            }
                        }
                    }
                }
                if (diagnostics != null) {
                    CloudRestoreDiagnosticsPanel(
                        diagnosticsText = diagnosticsText,
                        expanded = showDiagnostics,
                        onToggleExpanded = { showDiagnostics = !showDiagnostics },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(diagnosticsText))
                            Toast.makeText(context, diagnosticsCopiedMessage, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudRestoreDiagnosticsPanel(
    diagnosticsText: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnosticsContentDescription = stringResource(R.string.cloud_restore_diagnostics_content_description)
    val copyContentDescription = stringResource(R.string.cloud_restore_diagnostics_copy_content_description)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cloud_restore_diagnostics_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onToggleExpanded) {
                    Text(
                        stringResource(
                            if (expanded) {
                                R.string.cloud_restore_diagnostics_hide
                            } else {
                                R.string.cloud_restore_diagnostics_show
                            },
                        ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.cloud_restore_diagnostics_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                SelectionContainer {
                    Text(
                        text = diagnosticsText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = diagnosticsContentDescription },
                    )
                }
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.semantics {
                    contentDescription = copyContentDescription
                },
            ) {
                Text(stringResource(R.string.cloud_restore_diagnostics_copy))
            }
        }
    }
}

@Composable
private fun CloudRestoreProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun cloudRestoreStatusText(progress: CloudRestoreProgress): String = stringResource(
    when (progress.status) {
        CloudRestoreStatus.Running -> R.string.cloud_restore_status_running
        CloudRestoreStatus.Interrupted -> R.string.cloud_restore_status_interrupted
        CloudRestoreStatus.Offline -> R.string.cloud_restore_status_offline
        CloudRestoreStatus.Failed -> R.string.cloud_restore_status_failed
        CloudRestoreStatus.RolledBack -> R.string.cloud_restore_status_rolled_back
        CloudRestoreStatus.CompletedWithAttention -> R.string.cloud_restore_status_completed_with_attention
    },
)

private val CloudRestorePhase.labelRes: Int
    get() = when (this) {
        CloudRestorePhase.Preparing -> R.string.cloud_restore_phase_preparing
        CloudRestorePhase.Downloading -> R.string.cloud_restore_phase_downloading
        CloudRestorePhase.Validating -> R.string.cloud_restore_phase_validating
        CloudRestorePhase.Applying -> R.string.cloud_restore_phase_applying
        CloudRestorePhase.Uploading -> R.string.cloud_restore_phase_uploading
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

private val com.gusanitolabs.robia.core.model.DriveBackupDeletionState.statusRes: Int
    get() = when (this) {
        com.gusanitolabs.robia.core.model.DriveBackupDeletionState.None -> R.string.drive_backup_deletion_status_none
        com.gusanitolabs.robia.core.model.DriveBackupDeletionState.Deleting -> R.string.drive_backup_deletion_status_deleting
        com.gusanitolabs.robia.core.model.DriveBackupDeletionState.Complete -> R.string.drive_backup_deletion_status_complete
        com.gusanitolabs.robia.core.model.DriveBackupDeletionState.NeedsAttention -> R.string.drive_backup_deletion_status_attention
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
    suggestionService: SuggestionService,
    currentRoute: RobiaRoute,
    innerPadding: PaddingValues,
    items: List<UiWardrobeItem>,
    allItems: List<UiWardrobeItem>,
    domainItems: List<ClothingItem>,
    wardrobeItemsLoading: Boolean,
    totalItemCount: Int,
    filters: BrowseFilterState,
    selectedBrowseItemIds: Set<String>,
    selectedItem: UiWardrobeItem?,
    selectedDomainItem: ClothingItem?,
    batchDrafts: List<BatchDraftItem>,
    selectedBatchDraft: BatchDraftItem?,
    batchSaveStatusMessage: String?,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    colorReviewChangeSet: ColorPaletteChangeSet?,
    developerModeEnabled: Boolean,
    restoreSyncLogText: String,
    syncState: WardrobeSyncState,
    currentLanguage: LanguagePreference,
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    pendingGarmentSyncCount: Int,
    @StringRes cloudSetupSummaryRes: Int,
    developerModeUnlocked: Boolean,
    driveBackupDeletionState: com.gusanitolabs.robia.core.model.DriveBackupDeletionState,
    onRouteSelected: (RobiaRoute) -> Unit,
    onBack: () -> Unit,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBrowseSelectionToggle: (String) -> Unit,
    onCancelAddEdit: () -> Unit,
    onSaveItem: (ClothingItem, SuggestionCapture?) -> Unit,
    onDeleteItem: (ClothingItem) -> Unit,
    onBatchPhotosSelected: (List<String>) -> Unit,
    onDraftSelected: (BatchDraftItem) -> Unit,
    onRetryBatchDraft: (BatchDraftItem) -> Unit,
    onDiscardBatchDraft: (BatchDraftItem) -> Unit,
    onSaveBatch: (Set<String>) -> Unit,
    onCancelBatch: () -> Unit,
    onSaveBatchDraft: (ClothingItem, Boolean, SuggestionCapture?) -> Unit,
    onFiltersChange: (BrowseFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
    onRestoreDefaultTags: (TagCategory) -> Unit,
    onRestoreDefaultMainColors: () -> Unit,
    onCommitColorReview: (ColorPaletteChangeSet, List<ClothingItem>) -> Unit,
    onCloseColorReview: () -> Unit,
    onRequestColorReviewDiscard: () -> Unit,
    onClearRestoreSyncLog: () -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onCloudSetupClick: () -> Unit,
    onDeleteBackupClick: () -> Unit,
    onRestoreSyncLogClick: () -> Unit,
    onRetryRestoredPhoto: (String) -> Unit,
    browseAttentions: List<SyncAttention>,
    onSyncAttentionAction: (SyncAttention, SyncAttentionAction) -> Unit,
    onKeepWithoutPhoto: (String) -> Unit,
    onRetryAttention: (SyncAttentionKind) -> Unit,
) {
    when (currentRoute) {
        RobiaRoute.Browse -> BrowseWardrobeScreen(
            innerPadding = innerPadding,
            items = items,
            isInitialLoading = wardrobeItemsLoading,
            hasItemsInWardrobe = totalItemCount > 0,
            hasActiveFilters = filters.hasActiveFilters,
            activeFilterCount = filters.activeFilterCount,
            showSyncActivity = syncState.hasVisibleSyncActivity,
            selectedItemIds = selectedBrowseItemIds,
            onItemSelected = onItemSelected,
            onToggleFavorite = onToggleFavorite,
            onSelectionToggle = onBrowseSelectionToggle,
            onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) },
            onFiltersClick = { onRouteSelected(RobiaRoute.AdvancedFilters) },
            onResetFilters = onResetFilters,
            attentions = browseAttentions,
            onAttentionAction = onSyncAttentionAction,
            showSyncDetails = developerModeEnabled,
            onSyncDetailsClick = onRestoreSyncLogClick,
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
                onCommit = onCommitColorReview,
                onRequestDiscard = onRequestColorReviewDiscard,
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.AddEditClothing -> AddEditClothingScreen(
            innerPadding = innerPadding,
            availableTags = availableTags,
            mainColors = mainColors,
            existingItem = selectedDomainItem,
            developerModeEnabled = developerModeEnabled,
            suggestionService = suggestionService,
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
            onDraftSelected = onDraftSelected,
            onRetryDraft = onRetryBatchDraft,
            onDiscardDraft = onDiscardBatchDraft,
            onSaveBatch = onSaveBatch,
            onCancelBatch = onCancelBatch,
            saveStatusMessage = batchSaveStatusMessage,
        )
        RobiaRoute.BatchEditClothing -> selectedBatchDraft?.let { draft ->
            AddEditClothingScreen(
                innerPadding = innerPadding,
                availableTags = availableTags,
                mainColors = mainColors,
                existingItem = draft.toBatchEditItem(availableTags, mainColors),
                developerModeEnabled = developerModeEnabled,
                suggestionService = suggestionService,
                initialPhotoReviewState = draft.toBatchEditPhotoReviewState(),
                titleRes = R.string.batch_edit_title,
                bodyRes = R.string.batch_edit_body,
                saveButtonTextRes = if (draft.status == BatchDraftStatus.NeedsReview) {
                    R.string.batch_keep_original_accept
                } else {
                    R.string.batch_return_to_batch
                },
                onCancel = onBack,
                onSave = { item, capture ->
                    onSaveBatchDraft(item, draft.status == BatchDraftStatus.NeedsReview, capture)
                },
            )
        } ?: BatchAddClothingScreen(
            innerPadding = innerPadding,
            drafts = batchDrafts,
            availableTags = availableTags,
            mainColors = mainColors,
            onDraftSelected = onDraftSelected,
            onRetryDraft = onRetryBatchDraft,
            onDiscardDraft = onDiscardBatchDraft,
            onSaveBatch = onSaveBatch,
            onCancelBatch = onCancelBatch,
            saveStatusMessage = batchSaveStatusMessage,
        )
        RobiaRoute.ItemDetail -> selectedItem?.let { item ->
            ItemDetailScreen(
                innerPadding = innerPadding,
                item = item,
                onEditClick = { onRouteSelected(RobiaRoute.AddEditClothing) },
                onRetryRestoredPhoto = onRetryRestoredPhoto,
                onKeepWithoutPhoto = onKeepWithoutPhoto,
                onRetryAttention = onRetryAttention,
                showSyncDetails = developerModeEnabled,
                onSyncDetailsClick = onRestoreSyncLogClick,
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.LanguageSettings -> LanguageSettingsScreen(innerPadding)
        RobiaRoute.Settings -> SettingsScreen(
            innerPadding = innerPadding,
            currentLanguage = currentLanguage,
            driveSyncConnectionStatus = driveSyncConnectionStatus,
            pendingGarmentSyncCount = pendingGarmentSyncCount,
            cloudSetupSummaryRes = cloudSetupSummaryRes,
            developerModeUnlocked = developerModeUnlocked,
            developerModeEnabled = developerModeEnabled,
            onDeveloperModeEnabledChange = onDeveloperModeEnabledChange,
            onLanguageSelected = onLanguageSelected,
            onLanguageClick = { onRouteSelected(RobiaRoute.LanguageSettings) },
            onCloudSetupClick = onCloudSetupClick,
            driveBackupDeletionState = driveBackupDeletionState,
            onDeleteBackupClick = onDeleteBackupClick,
            onRestoreSyncLogClick = onRestoreSyncLogClick,
        )
        RobiaRoute.DeveloperSyncLog -> if (developerModeUnlocked && developerModeEnabled) {
            DeveloperRestoreSyncLogScreen(
                innerPadding = innerPadding,
                logText = restoreSyncLogText,
                onClear = onClearRestoreSyncLog,
            )
        } else {
            SettingsScreen(
                innerPadding = innerPadding,
                currentLanguage = currentLanguage,
                driveSyncConnectionStatus = driveSyncConnectionStatus,
                pendingGarmentSyncCount = pendingGarmentSyncCount,
                cloudSetupSummaryRes = cloudSetupSummaryRes,
                developerModeUnlocked = developerModeUnlocked,
                developerModeEnabled = developerModeEnabled,
                onDeveloperModeEnabledChange = onDeveloperModeEnabledChange,
                onLanguageSelected = onLanguageSelected,
                onLanguageClick = { onRouteSelected(RobiaRoute.LanguageSettings) },
                onCloudSetupClick = onCloudSetupClick,
                driveBackupDeletionState = driveBackupDeletionState,
                onDeleteBackupClick = onDeleteBackupClick,
                onRestoreSyncLogClick = onRestoreSyncLogClick,
            )
        }
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
    isInitialLoading: Boolean,
    hasItemsInWardrobe: Boolean,
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    showSyncActivity: Boolean,
    selectedItemIds: Set<String>,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectionToggle: (String) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
    attentions: List<SyncAttention>,
    onAttentionAction: (SyncAttention, SyncAttentionAction) -> Unit,
    showSyncDetails: Boolean,
    onSyncDetailsClick: () -> Unit,
) {
    val isSelectionMode = selectedItemIds.isNotEmpty()
    val gridState = rememberLazyGridState()
    val backfill = (LocalContext.current.applicationContext as? RobiaApplication)?.embeddingBackfill
    val backfillProgress by (backfill?.progress ?: remember { MutableStateFlow(BackfillProgress()) }).collectAsState()

    BrowseImagePrefetch(items = items, gridState = gridState)

    LazyVerticalGrid(
        state = gridState,
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
                showSyncActivity = showSyncActivity,
                onFiltersClick = onFiltersClick,
            )
        }
        if (backfillProgress.remaining > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SuggestionsProgressBanner(remaining = backfillProgress.remaining)
            }
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
private fun FilterBar(
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    showSyncActivity: Boolean,
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
        Spacer(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showSyncActivity) {
            val syncDescription = stringResource(R.string.content_cloud_sync_active)
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .semantics { contentDescription = syncDescription },
                strokeWidth = 2.dp,
            )
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

@Composable
private fun UiWardrobeItem.garmentCloudStatusLabel(): String = when (syncStatus) {
    GarmentSyncStatus.Running,
    GarmentSyncStatus.FailedRetryable,
    GarmentSyncStatus.NeedsUserAction -> if (hasMissingRestoredPhoto) {
        photoRestoreRetryStateText()
    } else when (syncStatus) {
        GarmentSyncStatus.Running -> stringResource(R.string.sync_status_uploading)
        else -> stringResource(R.string.sync_status_pending)
    }
    GarmentSyncStatus.LocalOnly -> stringResource(R.string.garment_sync_saved_device)
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued -> stringResource(R.string.sync_status_pending)
    GarmentSyncStatus.Synced -> stringResource(R.string.garment_sync_synced)
    GarmentSyncStatus.AuthBlocked -> stringResource(R.string.garment_sync_auth_blocked)
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

private fun formatEpochMillis(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private fun UiWardrobeItem.garmentCloudStatusIcon(): ImageVector = when (syncStatus) {
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
private fun UiWardrobeItem.garmentCloudStatusTint(): Color = when (syncStatus) {
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
private fun GarmentPhotoPlaceholder(
    item: UiWardrobeItem,
    modifier: Modifier = Modifier,
    thumbnailMaxEdgePx: Int? = GRID_THUMBNAIL_MAX_EDGE_PX,
    purpose: ImagePurpose = ImagePurpose.Browse,
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

private val UiWardrobeItem.hasMissingRestoredPhoto: Boolean
    get() = photoRestoreState.needsAttention

private fun UiWardrobeItem.canRetryRestoredPhotoNow(now: Long = System.currentTimeMillis()): Boolean =
    hasMissingRestoredPhoto &&
        syncStatus != GarmentSyncStatus.Running &&
        photoRestoreState.hasRetryAttemptsRemaining &&
        (photoRestoreState.retryAfterEpochMillis == null || photoRestoreState.retryAfterEpochMillis <= now) &&
        (photoRestoreState.retryDeadlineEpochMillis == null || photoRestoreState.retryDeadlineEpochMillis >= now)

@Composable
private fun UiWardrobeItem.photoRestoreRetryStateText(now: Long = System.currentTimeMillis()): String = when {
    syncStatus == GarmentSyncStatus.Running -> stringResource(R.string.cloud_restore_photo_retry_running)
    photoRestoreState.retryExhausted -> stringResource(R.string.cloud_restore_photo_retry_exhausted)
    photoRestoreState.retryDeadlineEpochMillis?.let { it < now } == true -> stringResource(R.string.cloud_restore_photo_retry_expired)
    photoRestoreState.retryAfterEpochMillis?.let { it > now } == true -> stringResource(
        R.string.cloud_restore_photo_retry_backoff_until,
        formatEpochMillis(photoRestoreState.retryAfterEpochMillis),
    )
    else -> stringResource(R.string.cloud_restore_photo_retry_ready)
}

@Composable
private fun UiWardrobeItem.photoRestoreRetryActionText(now: Long = System.currentTimeMillis()): String = when {
    syncStatus == GarmentSyncStatus.Running -> stringResource(R.string.cloud_restore_retry_photo)
    photoRestoreState.retryExhausted -> stringResource(R.string.cloud_restore_retry_photo_exhausted)
    photoRestoreState.retryDeadlineEpochMillis?.let { it < now } == true -> stringResource(R.string.cloud_restore_retry_photo_expired)
    else -> stringResource(R.string.cloud_restore_retry_photo)
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
    onRetryRestoredPhoto: (String) -> Unit,
    onKeepWithoutPhoto: (String) -> Unit,
    onRetryAttention: (SyncAttentionKind) -> Unit,
    showSyncDetails: Boolean,
    onSyncDetailsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_garment_chooser_title)
    val imageShareErrorMessage = stringResource(R.string.image_share_error)
    val pdfShareErrorMessage = stringResource(R.string.pdf_share_error)
    val noShareAppMessage = stringResource(R.string.share_no_app_error)
    val retryPhotoContentDescription = stringResource(R.string.content_cloud_restore_retry_photo)
    val pdfShareItem = item.toGarmentShareItem()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = item.name,
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
                    categoryId = "category",
                    selectedTagIds = filters.selectedTagIds,
                    selectedUnsetTagCategoryIds = filters.selectedUnsetTagCategoryIds,
                    onTagToggled = { tag -> onFiltersChange(filters.toggleSingleValueTag(tag, categoryTags)) },
                    onUnsetToggled = { onFiltersChange(filters.toggleUnsetCategory("category", categoryTags)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_season), icon = categoryIconFor("season")) {
                FilterTagChips(
                    tags = seasonTags,
                    categoryId = "season",
                    selectedTagIds = filters.selectedTagIds,
                    selectedUnsetTagCategoryIds = filters.selectedUnsetTagCategoryIds,
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag)) },
                    onUnsetToggled = { onFiltersChange(filters.toggleUnsetCategory("season", seasonTags)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_occasion), icon = categoryIconFor("occasion")) {
                FilterTagChips(
                    tags = occasionTags,
                    categoryId = "occasion",
                    selectedTagIds = filters.selectedTagIds,
                    selectedUnsetTagCategoryIds = filters.selectedUnsetTagCategoryIds,
                    onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag)) },
                    onUnsetToggled = { onFiltersChange(filters.toggleUnsetCategory("occasion", occasionTags)) },
                )
            }
        }
        item {
            FilterSection(title = stringResource(R.string.filter_location), icon = categoryIconFor("location")) {
                FilterTagChips(
                    tags = locationTags,
                    categoryId = "location",
                    selectedTagIds = filters.selectedTagIds,
                    selectedUnsetTagCategoryIds = filters.selectedUnsetTagCategoryIds,
                    onTagToggled = { tag -> onFiltersChange(filters.toggleSingleValueTag(tag, locationTags)) },
                    onUnsetToggled = { onFiltersChange(filters.toggleUnsetCategory("location", locationTags)) },
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
    categoryId: String,
    selectedTagIds: Set<String>,
    selectedUnsetTagCategoryIds: Set<String>,
    onTagToggled: (GarmentTag) -> Unit,
    onUnsetToggled: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val notSetSelected = categoryId in selectedUnsetTagCategoryIds
        FilterChip(
            selected = notSetSelected,
            onClick = onUnsetToggled,
            label = { Text(stringResource(R.string.not_set)) },
            leadingIcon = if (notSetSelected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
        )
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

private fun BrowseFilterState.toggleTag(tag: GarmentTag): BrowseFilterState = copy(
    selectedTagIds = selectedTagIds.toggle(tag.id),
    selectedUnsetTagCategoryIds = selectedUnsetTagCategoryIds - tag.categoryId,
)

private fun BrowseFilterState.toggleSingleValueTag(
    tag: GarmentTag,
    categoryTags: List<GarmentTag>,
): BrowseFilterState {
    val categoryTagIds = categoryTags.map(GarmentTag::id).toSet()
    return copy(
        selectedTagIds = if (tag.id in selectedTagIds) {
            selectedTagIds - tag.id
        } else {
            (selectedTagIds - categoryTagIds) + tag.id
        },
        selectedUnsetTagCategoryIds = selectedUnsetTagCategoryIds - tag.categoryId,
    )
}

private fun BrowseFilterState.toggleUnsetCategory(
    categoryId: String,
    categoryTags: List<GarmentTag>,
): BrowseFilterState {
    val categoryTagIds = categoryTags.map(GarmentTag::id).toSet()
    return copy(
        selectedTagIds = selectedTagIds - categoryTagIds,
        selectedUnsetTagCategoryIds = selectedUnsetTagCategoryIds.toggle(categoryId),
    )
}

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

private fun Context.launchTextShareChooser(
    text: String,
    chooserTitle: String,
): Boolean {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooserIntent = Intent.createChooser(shareIntent, chooserTitle)
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

private fun String.toComposeColor(): Color? = normalizedHexOrNull()?.let { normalized ->
    Color(android.graphics.Color.parseColor("#$normalized"))
}

@Composable
private fun DeveloperRestoreSyncLogScreen(
    innerPadding: PaddingValues,
    logText: String,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.restore_sync_log_copied)
    val noShareAppMessage = stringResource(R.string.share_no_app_error)
    val chooserTitle = stringResource(R.string.restore_sync_log_export)
    val effectiveLogText = logText.takeIf { it.isNotBlank() } ?: stringResource(R.string.restore_sync_log_empty)

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
                    text = stringResource(R.string.restore_sync_log_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.restore_sync_log_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(effectiveLogText))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                    enabled = logText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.restore_sync_log_copy))
                }
                OutlinedButton(
                    onClick = {
                        val launched = context.launchTextShareChooser(effectiveLogText, chooserTitle)
                        if (!launched) Toast.makeText(context, noShareAppMessage, Toast.LENGTH_SHORT).show()
                    },
                    enabled = logText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.restore_sync_log_export))
                }
                TextButton(onClick = onClear, enabled = logText.isNotBlank()) {
                    Text(stringResource(R.string.restore_sync_log_clear))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                SelectionContainer {
                    Text(
                        text = effectiveLogText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
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

private fun List<ClothingItem>.toUiWardrobeItems(
    syncState: WardrobeSyncState,
    context: Context,
): List<UiWardrobeItem> = map { item ->
    val effectiveSyncStatus = item.syncStatus.resolveForConnection(syncState.connectionStatus)
    UiWardrobeItem(
        id = item.id,
        name = item.name,
        subtitle = if (item.photoRestoreState.needsAttention) {
            context.getString(R.string.cloud_restore_photo_missing_message)
        } else {
            item.notes.ifBlank { context.getString(effectiveSyncStatus.itemStatusLabelRes) }
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
        syncStatus = effectiveSyncStatus,
        syncDirtyAtEpochMillis = item.syncDirtyAtEpochMillis,
        lastSyncedAtEpochMillis = item.lastSyncedAtEpochMillis,
        syncFailureMessage = item.syncFailureMessage,
        photoRestoreState = item.photoRestoreState,
    )
}

private fun GarmentSyncStatus.resolveForConnection(
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
): GarmentSyncStatus = when {
    this == GarmentSyncStatus.Synced -> GarmentSyncStatus.Synced
    this == GarmentSyncStatus.FailedRetryable -> GarmentSyncStatus.FailedRetryable
    this == GarmentSyncStatus.NeedsUserAction -> GarmentSyncStatus.NeedsUserAction
    this == GarmentSyncStatus.Running -> GarmentSyncStatus.Running
    driveSyncConnectionStatus == DriveSyncConnectionStatus.Disconnected -> GarmentSyncStatus.AuthBlocked
    driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured ||
        driveSyncConnectionStatus == DriveSyncConnectionStatus.Disabled -> when (this) {
            GarmentSyncStatus.Dirty,
            GarmentSyncStatus.Queued,
            GarmentSyncStatus.AuthBlocked -> this
            else -> GarmentSyncStatus.LocalOnly
        }
    else -> this
}

private val GarmentSyncStatus.itemStatusLabelRes: Int
    get() = when (this) {
        GarmentSyncStatus.LocalOnly -> R.string.garment_sync_saved_device
        GarmentSyncStatus.Dirty,
        GarmentSyncStatus.Queued,
        GarmentSyncStatus.FailedRetryable,
        GarmentSyncStatus.NeedsUserAction -> R.string.sync_status_pending
        GarmentSyncStatus.Running -> R.string.sync_status_uploading
        GarmentSyncStatus.Synced -> R.string.garment_sync_synced
        GarmentSyncStatus.AuthBlocked -> R.string.garment_sync_auth_blocked
    }

private const val SYNC_STORAGE_URL = "https://one.google.com/storage"
private const val MAX_BROWSE_ATTENTION_CARDS = 2
private const val DRIVE_FULL_REASON = "drive_full"
private const val PHOTO_UPLOAD_FAILED_REASON = "photo_upload_failed"

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

private object PreviewTextImageEmbedder : TextImageEmbedder {
    override suspend fun embedImage(png: ByteArray): FloatArray = error("Preview has no embedder")
    override suspend fun embedText(text: String): FloatArray = error("Preview has no embedder")
}

@Preview(showBackground = true)
@Composable
private fun RobiaAppPreview() {
    RobiaTheme {
        RobiaShell(
            suggestionService = SuggestionService(
                embedder = PreviewTextImageEmbedder,
                isModelReady = { false },
                encodeImage = { error("Preview has no image input") },
                savedExamples = { emptyList() },
                preloadEngine = {},
            ),
            settings = RobiaSettings(),
            settingsLoaded = true,
            syncState = WardrobeSyncState.notConfigured(),
            restoreSyncLogText = "",
            pendingGarmentSyncCount = 0,
            clothingItems = emptyList(),
            wardrobeItemsLoading = false,
            tagCategories = emptyList(),
            availableTags = emptyList(),
            mainColors = emptyList(),
            onLanguageSelected = {},
            onDeveloperModeUnlocked = {},
            onDeveloperModeEnabledChange = {},
            onCloudSetupPromptInteracted = {},
            onRequestCloudSetup = {},
            onConfirmMerge = {},
            onCancelMerge = {},
            onSaveItem = { _, _ -> },
            onSaveItems = { _, _ -> },
            onDeleteItems = {},
            onSaveTag = {},
            onSaveMainColor = {},
            onDeleteCustomTag = {},
            onDeleteMainColor = {},
            onRestoreDefaultTags = {},
            onRestoreDefaultMainColors = {},
            onCommitMainColorChange = { _, _ -> },
        )
    }
}

/**
 * Stores the capture-time IMAGE vector when it belongs to the saved photo, and keeps the TEXT vector in step with
 * name and notes (TS§3.7). Writes only `garment_embeddings`, never the garment row.
 */
private suspend fun storeSuggestionVectors(
    app: RobiaApplication,
    embeddingRepository: EmbeddingRepository,
    item: ClothingItem,
    capture: SuggestionCapture?,
) {
    if (capture != null && capture.photoUri == item.photoUri) {
        embeddingRepository.upsert(item.id, EmbeddingKind.IMAGE, capture.imageVector, capture.imageInputHash, capture.durationMs)
    }
    val textInput = garmentTextInput(item.name, item.notes)
    if (textInput == null) {
        embeddingRepository.delete(item.id, EmbeddingKind.TEXT)
        return
    }
    val inputHash = sha256Hex16(textInput.toByteArray(Charsets.UTF_8))
    if (app.database.embeddingDao().get(item.id, EmbeddingKind.TEXT)?.inputHash == inputHash) return
    val startedAt = System.nanoTime()
    // Without a ready model the TEXT vector waits for the backfill, so the save itself never fails.
    val vector = try {
        app.embeddingEngineHolder.embedText(textInput)
    } catch (unavailable: EmbeddingFailure) {
        return
    }
    embeddingRepository.upsert(item.id, EmbeddingKind.TEXT, vector, inputHash, (System.nanoTime() - startedAt) / 1_000_000L)
}
