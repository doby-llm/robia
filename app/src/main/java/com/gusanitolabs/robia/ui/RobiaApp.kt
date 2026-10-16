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
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.designsystem.RobiaTheme
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
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
import com.gusanitolabs.robia.data.local.ClothingItemWithTags
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.toDomain
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
import com.gusanitolabs.robia.suggestions.QueryEmbedding
import com.gusanitolabs.robia.suggestions.SavedExample
import com.gusanitolabs.robia.suggestions.SearchController
import com.gusanitolabs.robia.suggestions.SearchMode
import com.gusanitolabs.robia.suggestions.SearchResult
import com.gusanitolabs.robia.suggestions.ReviewPlanner
import com.gusanitolabs.robia.suggestions.SuggestionCapture
import com.gusanitolabs.robia.suggestions.SuggestionDiagnostics
import com.gusanitolabs.robia.suggestions.SuggestionService
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.engine.EmbeddingFailure
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelDownloadWorker
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.SyncCycleSummary
import com.gusanitolabs.robia.sync.MergeSummary
import com.gusanitolabs.robia.sync.NoOpWardrobeSyncGateway
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind
import com.gusanitolabs.robia.sync.SyncRunResult
import com.gusanitolabs.robia.sync.WardrobeSyncGateway
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import com.gusanitolabs.robia.sync.WardrobeSyncState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    data object TagReview : RobiaRoute {
        override val titleRes = R.string.tag_review_title
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
    /** Offline: the pending garment waits for the connection, shown instead of "Pending upload". */
    val waitingForConnection: Boolean = false,
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

private const val TAG_REVIEW_OFFER_LOG_TAG = "TagReviewOffer"

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
    val tagCategories by tagRepository.observeCategories().collectAsState(initial = emptyList())
    val availableTags by tagRepository.observeTags().collectAsState(initial = emptyList())
    val mainColors by tagRepository.observeMainColors().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as RobiaApplication
    // The tag and colour writes of this session. The next review snapshot or undo joins the latest one's database part first.
    val reviewWrites = remember(app, syncGateway) {
        ReviewWrites(app.appScope) { operation -> syncGateway.enqueue(operation) }
    }
    fun launchReviewWrite(write: suspend CoroutineScope.() -> List<WardrobeSyncOperation>) = reviewWrites.launch(write)
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
        val mergedTagIds = tagRepository.seedDefaultsIfNeeded()
        // A launch-time dedupe removes local rows; enqueue them so the tombstones reach Drive without waiting for the next snapshot.
        if (mergedTagIds.isNotEmpty()) syncGateway.enqueue(WardrobeSyncOperation.UpsertTags(mergedTagIds))
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
                launchReviewWrite {
                    wardrobeRepository.upsertItems(items)
                    app.appScope.launch {
                        items.forEach { item -> storeSuggestionVectors(app, embeddingRepository, item, captures[item.id]) }
                    }
                    items.map { item -> WardrobeSyncOperation.UpsertItem(item.id) }
                }
            },
            onDeleteItems = { itemIds ->
                scope.launch {
                    wardrobeRepository.archiveItems(itemIds, System.currentTimeMillis())
                    itemIds.forEach { itemId -> syncGateway.enqueue(WardrobeSyncOperation.DeleteItemFolder(itemId)) }
                }
            },
            onSaveTag = { tag ->
                launchReviewWrite {
                    tagRepository.upsertTag(tag)
                    listOf(WardrobeSyncOperation.UpsertTags(setOf(tag.id)))
                }
            },
            onSaveMainColor = { color ->
                launchReviewWrite {
                    tagRepository.upsertMainColor(color)
                    listOf(WardrobeSyncOperation.UpsertPalette(listOf(color)))
                }
            },
            onDeleteCustomTag = { tag ->
                launchReviewWrite {
                    tagRepository.deleteCustomTag(tag.id)
                    listOf(WardrobeSyncOperation.UpsertTags(setOf(tag.id)))
                }
            },
            onDeleteMainColor = { color ->
                launchReviewWrite {
                    tagRepository.deleteMainColor(color.id)
                    listOf(WardrobeSyncOperation.UpsertPalette(listOf(color)))
                }
            },
            onRestoreDefaultTags = { category ->
                launchReviewWrite {
                    val mergedTagIds = tagRepository.restoreDefaultTags(category.id)
                    val restoredTagIds = DefaultTags.tags
                        .filter { tag -> tag.categoryId == category.id }
                        .map(GarmentTag::id)
                        .toSet()
                    listOf(WardrobeSyncOperation.UpsertTags(restoredTagIds + mergedTagIds))
                }
            },
            readGarmentsForReview = {
                // Timing for the snapshot delay: the join waits for the previous review write's database part only.
                val requestedAt = System.nanoTime()
                afterPreviousWrite(reviewWrites.latest) {
                    val joinedAt = System.nanoTime()
                    app.database.wardrobeDao().getAllItemsForSync().map(ClothingItemWithTags::toDomain).also { garments ->
                        Log.d(
                            TAG_REVIEW_OFFER_LOG_TAG,
                            "snapshot joinMs=${(joinedAt - requestedAt) / 1_000_000} readMs=${(System.nanoTime() - joinedAt) / 1_000_000} garments=${garments.size}",
                        )
                    }
                }
            },
            onUndoReview = { buildPlan ->
                // One coroutine, in order: the write it follows, then the plan, then tombstones, so a restored tag is not deleted again by the next sync.
                reviewWrites.launchUndo(buildPlan) { plan ->
                    plan.tombstonesToClear.forEach { (entityType, entityId) ->
                        app.database.syncTombstoneDao().deleteTombstone(entityType, entityId)
                    }
                    plan.restoreTags.forEach { tag -> tagRepository.upsertTag(tag) }
                    plan.removeTags.forEach { tag -> tagRepository.deleteCustomTag(tag.id) }
                    plan.restoreColors.forEach { color -> tagRepository.upsertMainColor(color) }
                    plan.removeColors.forEach { color -> tagRepository.deleteMainColor(color.id) }
                    if (plan.restoreGarments.isNotEmpty()) wardrobeRepository.upsertItems(plan.restoreGarments)
                    buildList<WardrobeSyncOperation> {
                        plan.restoreTags.forEach { tag -> add(WardrobeSyncOperation.UpsertTags(setOf(tag.id))) }
                        plan.removeTags.forEach { tag -> add(WardrobeSyncOperation.UpsertTags(setOf(tag.id))) }
                        plan.restoreColors.forEach { color -> add(WardrobeSyncOperation.UpsertPalette(listOf(color))) }
                        plan.removeColors.forEach { color -> add(WardrobeSyncOperation.UpsertPalette(listOf(color))) }
                        plan.restoreGarments.forEach { item -> add(WardrobeSyncOperation.UpsertItem(item.id)) }
                    }
                }
            },
            onCommitMainColorChange = { changeSet, updatedItems ->
                launchReviewWrite {
                    tagRepository.applyMainColorChange(
                        upsertColors = changeSet.colorsToUpsertForCommit(),
                        deleteColorIds = changeSet.colorIdsToDeleteForCommit(),
                        updatedItems = updatedItems,
                    )
                    buildList<WardrobeSyncOperation> {
                        add(WardrobeSyncOperation.UpsertPalette(changeSet.afterPalette))
                        updatedItems.forEach { item -> add(WardrobeSyncOperation.UpsertItem(item.id)) }
                    }
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

/**
 * One headline for every cloud-sync surface: the Browse icon, the Settings lines and the developer card all read from here,
 * so they cannot contradict each other. [Syncing] means the processor is running or a restore is in progress.
 */
internal enum class CloudSyncHeadline { Synced, Syncing, Pending, Waiting, Attention, Error, Disabled, NotConfigured }

internal data class CloudSyncIndicator(
    val headline: CloudSyncHeadline,
    /** Garments plus metadata still waiting; the same count as [WardrobeSyncState.pendingOperationCount]. */
    val pendingCount: Int,
    /** The user must act (sign in, conflict, merge, attention items) or the last cycle needs sign-in. */
    val attention: Boolean,
    /** Developer mode only: the class and message of the last failed cycle. */
    val detail: String? = null,
    /** Developer mode only: the last failed cycle was a connection failure, shown as waiting rather than as an error. */
    val offline: Boolean = false,
)

private val OFFLINE_FAILURE_CLASSES = setOf(
    "UnknownHostException",
    "SocketTimeoutException",
    "ConnectException",
    "SSLHandshakeException",
    "NoRouteToHostException",
)

/**
 * Whether a failed cycle is a connection failure (no route, no DNS, timeout, TLS handshake) rather than a real error.
 * Matches the simple class name; without a class, the text before the first colon of [errorMessage] is checked.
 */
internal fun isOfflineFailure(errorClass: String?, errorMessage: String?): Boolean {
    if (errorClass != null) return errorClass.substringAfterLast('.') in OFFLINE_FAILURE_CLASSES
    val messageClass = errorMessage?.substringBefore(':')?.trim()?.substringAfterLast('.') ?: return false
    return messageClass in OFFLINE_FAILURE_CLASSES
}

/** The processor or a restore is running right now: the raw signal behind [CloudSyncHeadline.Syncing], without any display hold. */
internal val WardrobeSyncState.isRunningNow: Boolean
    get() = connectionStatus == DriveSyncConnectionStatus.Syncing ||
        restoreProgress?.status == CloudRestoreStatus.Running

/**
 * [online] is false without an internet network. Offline, a run cannot finish, so it never reads as Syncing: pending
 * changes wait for the connection. A transient Retry waits the same way.
 */
internal fun cloudSyncIndicator(
    state: WardrobeSyncState,
    developerMode: Boolean,
    online: Boolean = true,
): CloudSyncIndicator {
    val pending = state.pendingOperationCount.coerceAtLeast(0)
    val needsUserAction = state.connectionStatus == DriveSyncConnectionStatus.NeedsAttention ||
        state.attention.isNotEmpty() ||
        state.conflict ||
        state.pendingMergeSummary != null
    val waiting = pending > 0 && (!online || state.lastCycle?.result == SyncRunResult.Retry)
    val headline = when {
        state.connectionStatus == DriveSyncConnectionStatus.NotConfigured -> CloudSyncHeadline.NotConfigured
        state.connectionStatus == DriveSyncConnectionStatus.Disabled -> CloudSyncHeadline.Disabled
        state.isRunningNow && online -> CloudSyncHeadline.Syncing
        state.connectionStatus == DriveSyncConnectionStatus.Disconnected ||
            state.lastCycle?.result == SyncRunResult.AuthBlocked -> CloudSyncHeadline.Error
        needsUserAction -> CloudSyncHeadline.Attention
        waiting -> CloudSyncHeadline.Waiting
        pending > 0 -> CloudSyncHeadline.Pending
        else -> CloudSyncHeadline.Synced
    }
    val failedCycle = state.lastCycle?.takeIf { developerMode && (it.errorClass != null || it.errorMessage != null) }
    val detail = failedCycle?.let { cycle -> listOfNotNull(cycle.errorClass, cycle.errorMessage).joinToString(": ") }
    return CloudSyncIndicator(
        headline = headline,
        pendingCount = pending,
        attention = headline == CloudSyncHeadline.Attention || headline == CloudSyncHeadline.Error,
        detail = detail,
        offline = failedCycle != null && isOfflineFailure(failedCycle.errorClass, failedCycle.errorMessage),
    )
}

/** Developer card wording for a cycle result. Exhaustive on purpose: a new [SyncRunResult] must get a readable name, never a class name. */
@StringRes
internal fun SyncRunResult?.readableNameRes(): Int = when (this) {
    null -> R.string.dev_cloud_cancelled
    SyncRunResult.Success -> R.string.dev_cloud_result_success
    SyncRunResult.Retry -> R.string.dev_cloud_result_retry
    SyncRunResult.AuthBlocked -> R.string.dev_cloud_result_auth_blocked
    SyncRunResult.Conflict -> R.string.dev_cloud_result_conflict
    SyncRunResult.NeedsUser -> R.string.dev_cloud_result_needs_user
}

/** Browse shows the cloud icon while syncing, while waiting for a connection, or when the user has to act; synced and idle states show nothing. */
internal val CloudSyncHeadline.showsBrowseIndicator: Boolean
    get() = this == CloudSyncHeadline.Syncing ||
        this == CloudSyncHeadline.Waiting ||
        this == CloudSyncHeadline.Attention ||
        this == CloudSyncHeadline.Error

/** One change of the processing flag, [atMs] on the elapsed-realtime clock. */
internal data class SyncActivitySample(val atMs: Long, val processing: Boolean)

internal const val SYNC_INDICATOR_HOLD_MS = 1_500L

/**
 * Whether the Syncing indicator shows at [nowMs]: at once while processing, and for [holdMs] after the last processing run
 * ends, so back-to-back cycles do not blink. [samples] are processing-flag changes in time order; an idle stretch that
 * never followed a run does not show anything.
 */
internal fun syncIndicatorShowing(
    samples: List<SyncActivitySample>,
    nowMs: Long,
    holdMs: Long = SYNC_INDICATOR_HOLD_MS,
): Boolean {
    var processing = false
    var sawRun = false
    var idleSinceMs: Long? = null
    for (sample in samples) {
        if (sample.atMs > nowMs) break
        processing = sample.processing
        if (processing) {
            sawRun = true
            idleSinceMs = null
        } else if (sawRun && idleSinceMs == null) {
            idleSinceMs = sample.atMs
        }
    }
    return processing || (idleSinceMs != null && nowMs - idleSinceMs < holdMs)
}

/** The processing flag as the UI holds it. Keeps the last two changes: that is all [syncIndicatorShowing] needs. */
@Composable
private fun rememberSyncIndicatorShowing(processing: Boolean): Boolean {
    val changes = remember { mutableStateListOf<SyncActivitySample>() }
    var nowMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(processing) {
        changes.add(SyncActivitySample(SystemClock.elapsedRealtime(), processing))
        while (changes.size > 2) changes.removeAt(0)
        nowMs = SystemClock.elapsedRealtime()
        if (!processing) {
            // A new run cancels this effect, so the hold only ends after a full idle stretch.
            delay(SYNC_INDICATOR_HOLD_MS)
            nowMs = SystemClock.elapsedRealtime()
        }
    }
    return processing || syncIndicatorShowing(changes.toList(), nowMs)
}

/** The indicator every cloud surface reads (Browse, Settings, setup summary): [cloudSyncIndicator] with Syncing held. */
@Composable
private fun rememberCloudSyncIndicator(
    state: WardrobeSyncState,
    developerMode: Boolean,
    online: Boolean,
): CloudSyncIndicator {
    val indicator = cloudSyncIndicator(state = state, developerMode = developerMode, online = online)
    val showing = rememberSyncIndicatorShowing(processing = indicator.headline == CloudSyncHeadline.Syncing)
    return if (online && showing && indicator.headline != CloudSyncHeadline.Syncing) {
        indicator.copy(headline = CloudSyncHeadline.Syncing, attention = false)
    } else {
        indicator
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

private fun ClothingColorMetrics.referencesPaletteColorId(colorId: String): Boolean =
    primaryPaletteColorId == colorId || secondaryPaletteColorId == colorId

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
    onCommitMainColorChange: (ColorPaletteChangeSet, List<ClothingItem>) -> Unit,
    readGarmentsForReview: suspend () -> List<ClothingItem>,
    onUndoReview: (buildPlan: suspend () -> ReviewUndoPlan) -> Unit = {},
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
    val app = context.applicationContext as RobiaApplication
    val online by app.online.collectAsState()
    val cloudAuthorizationInFlight by app.cloudAuthorizationInFlight.collectAsState()
    val developerModeUnlockedMessage = stringResource(R.string.developer_mode_unlocked)
    val cloudSetupConfiguredMessage = stringResource(R.string.cloud_setup_configured_status)
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var browseFilters by remember { mutableStateOf(BrowseFilterState()) }
    var browseQuery by rememberSaveable { mutableStateOf("") }
    var similarToGarmentId by rememberSaveable { mutableStateOf<String?>(null) }
    // Set by saving a new garment: Browse shows the newest first until the user leaves Browse.
    var browseNewestFirst by rememberSaveable { mutableStateOf(false) }
    // Hoisted so Manage keeps its open tab when a tag or colour review returns to it.
    var manageTagsTabId by rememberSaveable { mutableStateOf<String?>(null) }
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
    var pendingTagReview by remember { mutableStateOf<TagReviewRequest?>(null) }
    // True from the start of a tag action's snapshot until its write; a second action in that window is dropped.
    var tagReviewOfferInFlight by remember { mutableStateOf(false) }
    val reviewScope = rememberCoroutineScope()
    var activeTagReview by remember { mutableStateOf<TagReviewRequest?>(null) }
    var showTagReviewDiscardDialog by remember { mutableStateOf(false) }
    // One first-run prompt at a time; the order lives in nextFirstRunPrompt. Answered cloud/model prompts stay answered in memory until settings catch up.
    var activeFirstRunPrompt by remember { mutableStateOf<FirstRunPrompt?>(null) }
    var answeredFirstRunPrompts by remember { mutableStateOf(emptySet<FirstRunPrompt>()) }
    var showKeepPhoneConfirmation by remember { mutableStateOf(false) }
    // A kept merge proposal (Not now) lives on as a Browse card; a new proposal shows the dialog again.
    var mergeDialogDismissed by remember(syncState.pendingMergeSummary) { mutableStateOf(false) }
    var mergeCombinePhase by remember { mutableStateOf(MergeCombinePhase.Idle) }
    var mergeCycleAtTap by remember { mutableStateOf<SyncCycleSummary?>(null) }
    var showBackupDeletionConfirmation by remember { mutableStateOf(false) }
    var showUseBackupConfirmation by remember { mutableStateOf(false) }
    var conflictDialogDismissed by remember(syncState.conflict) { mutableStateOf(false) }
    // Merge confirmation is a dialog; every other attention kind is shown as a card in Browse, highest priority first.
    val browseAttentions = syncState.attention
        .filter { attention -> attention.kind != SyncAttentionKind.MergeConfirmation }
        .take(MAX_BROWSE_ATTENTION_CARDS)
    val needsWardrobeUiModels = currentRoute in WardrobeUiModelRoutes
    val items = remember(context, clothingItems, syncState, online, needsWardrobeUiModels) {
        if (needsWardrobeUiModels) clothingItems.toUiWardrobeItems(syncState, online, context) else emptyList()
    }
    val filteredItems = remember(items, browseFilters, mainColors, needsWardrobeUiModels) {
        if (needsWardrobeUiModels) {
            items.filter { item -> browseFilters.matches(item, mainColors) }
        } else {
            emptyList()
        }
    }
    val vectors by app.embeddingRepository
        .observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        .collectAsState(initial = emptyMap())
    val engineAvailable = modelState is ModelState.Ready
    // Own supervised scope: a query embedding outlives the search that started it, and its failure must not cancel the screen.
    val searchScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val searchController = remember(app, searchScope) {
        SearchController(
            embedQuery = { query -> app.embeddingEngineHolder.embedText(EmbeddingRecipe.QUERY_PREFIX + query) },
            scope = searchScope,
        )
    }
    val searchActive = browseQuery.isNotBlank()
    val searchItems = remember(clothingItems, filteredItems) {
        val filteredIds = filteredItems.mapTo(HashSet(), UiWardrobeItem::id)
        clothingItems.filter { item -> item.id in filteredIds }
    }
    val trimmedQuery = browseQuery.trim()
    var queryEmbedding by remember { mutableStateOf<QueryEmbedding?>(null) }
    var searchInProgress by remember { mutableStateOf(false) }
    // Keyed on the query text and the engine only: vector writes from the backfill re-rank below, they never restart this.
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
    val embedded = queryEmbedding?.takeIf { it.query == trimmedQuery }
    val searchResult = remember(trimmedQuery, searchItems, vectors, embedded) {
        // Until the embedding of this query is known (or gave up), the plain list stays visible.
        if (embedded == null) {
            SearchResult(emptyList(), SearchMode.None)
        } else {
            searchController.rank(trimmedQuery, searchItems, vectors, embedded.vector)
        }
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute != RobiaRoute.Browse) browseNewestFirst = false
    }
    LaunchedEffect(searchActive, engineAvailable) {
        if (searchActive && engineAvailable) app.embeddingEngineHolder.preload()
    }
    // Similar garments need the garment's own IMAGE vector; without one the Browse list is the plain search.
    val similarSource = similarToGarmentId
        ?.let { id -> items.firstOrNull { item -> item.id == id } }
        ?.takeIf { item -> vectors[item.id]?.image != null }
    val searchMode = if (searchActive && similarSource == null) searchResult.mode else SearchMode.None
    val createdAtById = remember(clothingItems) { clothingItems.associate { item -> item.id to item.createdAtEpochMillis } }
    val displayedItems = remember(filteredItems, searchResult, searchActive, similarSource?.id, vectors, browseNewestFirst, createdAtById) {
        val filteredById = filteredItems.associateBy(UiWardrobeItem::id)
        when {
            similarSource != null -> rankSimilar(similarSource.id, filteredItems.map(UiWardrobeItem::id), vectors)
                .mapNotNull(filteredById::get)
            // Before the first query result exists the plain list stays visible, not an empty "no results" grid.
            searchActive && searchResult.mode != SearchMode.None -> searchResult.orderedIds.mapNotNull(filteredById::get)
            browseNewestFirst -> sortNewestFirst(filteredItems.map(UiWardrobeItem::id)) { id -> createdAtById[id] ?: 0L }
                .mapNotNull(filteredById::get)
            else -> filteredItems
        }
    }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val selectedDomainItem = clothingItems.firstOrNull { it.id == selectedItemId }
    val selectedHasImageVector = selectedItem != null && vectors[selectedItem.id]?.image != null
    val cloudIndicator = rememberCloudSyncIndicator(
        state = syncState,
        developerMode = settings.developerModeUnlocked && settings.developerModeEnabled,
        online = online,
    )
    // Only an untouched phone is recommended to set up Drive at first run; connecting later goes through the merge summary.
    val isFirstRunCloudSetup = remember(clothingItems, tagCategories, availableTags, mainColors, syncState) {
        clothingItems.isEmpty() &&
            availableTags.none(DefaultTags::isCustomOrModifiedDefault) &&
            tagCategories.none(DefaultTags::isCustomOrModifiedDefault) &&
            mainColors.none(DefaultTags::isCustomOrModifiedDefault) &&
            syncState.pendingOperationCount == 0 &&
            !syncState.hasConflictingAccountBinding
    }

    val cloudSetupPromptPending = settingsLoaded &&
        !settings.cloudSetupPromptInteracted &&
        settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured &&
        isFirstRunCloudSetup
    val cloudImportArmed by app.cloudImportArmed.collectAsState()
    val mergeConfirmationPending = syncState.pendingMergeSummary != null && !mergeDialogDismissed
    val backupConflictPending = syncState.conflict && !conflictDialogDismissed
    // The model prompt waits for Browse at rest; a Drive download or import that is starting also holds it (see firstRunGateOpen).
    val firstRunBusy = syncState.isRunningNow || cloudAuthorizationInFlight || cloudImportArmed
    var lastBusyTransitionMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var calmCheckMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(firstRunBusy) {
        lastBusyTransitionMs = SystemClock.elapsedRealtime()
        if (!firstRunBusy) {
            delay(FIRST_RUN_QUIET_MS)
            calmCheckMs = SystemClock.elapsedRealtime()
        }
    }
    val modelPromptGateOpen = firstRunGateOpen(
        ModelPromptGateInputs(
            onBrowse = currentRoute == RobiaRoute.Browse,
            syncRunning = syncState.isRunningNow,
            cloudImportArmed = cloudImportArmed,
            cloudAuthorizationInFlight = cloudAuthorizationInFlight,
            browseDialogOpen = showBrowseDeleteDialog ||
                showBackupDeletionConfirmation ||
                pendingColorReviewChangeSet != null ||
                pendingTagReview != null ||
                tagReviewOfferInFlight,
            mergeCombining = mergeCombinePhase == MergeCombinePhase.Combining,
            mergeOrConflictPending = mergeConfirmationPending || backupConflictPending,
            emptyWardrobeRestorePending = clothingItems.isEmpty() &&
                settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected &&
                !settings.driveFreshInstallRestoreAttempted,
            quietEnough = !firstRunBusy && quietEnoughSince(lastBusyTransitionMs, calmCheckMs),
        ),
    )
    val firstRunInputs = FirstRunPromptInputs(
        settingsLoaded = settingsLoaded,
        cloudAuthorizationInFlight = cloudAuthorizationInFlight,
        anotherDialogOpen = activeFirstRunPrompt != null || showUseBackupConfirmation || showKeepPhoneConfirmation,
        cloudSetupPending = cloudSetupPromptPending && FirstRunPrompt.CloudSetup !in answeredFirstRunPrompts,
        mergeConfirmationPending = mergeConfirmationPending,
        backupConflictPending = backupConflictPending,
        modelDownloadPending = FirstRunPrompt.ModelDownload !in answeredFirstRunPrompts &&
            shouldShowModelPrompt(promptShown = modelPromptShown ?: true, state = modelState) &&
            modelPromptGateOpen,
    )
    val queuedFirstRunPrompt = nextFirstRunPrompt(firstRunInputs)
    LaunchedEffect(queuedFirstRunPrompt) {
        if (queuedFirstRunPrompt != null) activeFirstRunPrompt = queuedFirstRunPrompt
    }
    LaunchedEffect(activeFirstRunPrompt, firstRunInputs) {
        // A prompt whose answer arrived elsewhere (for example a finished sync) closes instead of blocking the queue.
        val shown = activeFirstRunPrompt ?: return@LaunchedEffect
        if (!firstRunInputs.isPending(shown)) activeFirstRunPrompt = null
    }

    val lastCycle = syncState.lastCycle
    val mergeProposalPending = syncState.pendingMergeSummary != null
    LaunchedEffect(lastCycle, mergeProposalPending) {
        mergeCombinePhase = nextMergeCombinePhase(mergeCombinePhase, mergeCycleAtTap, lastCycle, mergeProposalPending)
    }
    // Safety net: no cycle ever finished after the tap (offline, work not scheduled) must not leave 'Combining' forever.
    LaunchedEffect(mergeCombinePhase) {
        if (mergeCombinePhase == MergeCombinePhase.Combining) {
            kotlinx.coroutines.delay(MERGE_COMBINE_TIMEOUT_MS)
            if (mergeCombinePhase == MergeCombinePhase.Combining) mergeCombinePhase = MergeCombinePhase.Failed
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

    // Garments that still reference a removed palette colour (a delete, or a restore-defaults), minus the ones a review already writes.
    fun clothingItemsWithoutRemovedColors(
        changeSet: ColorPaletteChangeSet,
        excludeIds: Set<String>,
    ): List<ClothingItem> = garmentsWithRemovedColors(
        items = clothingItems,
        removedIds = changeSet.colorIdsToDeleteForCommit(),
        excludeIds = excludeIds,
    )

    // Declining a review keeps the palette change; a deleted colour is still removed from the garments that carried it.
    fun declineColorReviewProposal(changeSet: ColorPaletteChangeSet) {
        pendingColorReviewChangeSet = null
        val itemsToSave = clothingItemsWithoutRemovedColors(changeSet, excludeIds = emptySet())
        if (itemsToSave.isNotEmpty()) onSaveItems(itemsToSave, emptyMap())
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
        )
        // Save-first: the colour change stands even if the user declines the review. No photographed garment means nothing to review.
        onSaveMainColor(color)
        if (hasReviewableGarments(clothingItems, ReviewKind.Colour)) pendingColorReviewChangeSet = changeSet
    }

    fun deleteMainColorAndOfferReview(color: MainColor) {
        val beforePalette = mainColors
        val afterPalette = beforePalette.filterNot { existing -> existing.id == color.id }
        val changeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = afterPalette,
            touchedColorId = color.id,
            operation = ColorPaletteOperation.Deleted,
        )
        onDeleteMainColor(color)
        if (hasReviewableGarments(clothingItems, ReviewKind.Colour)) {
            pendingColorReviewChangeSet = changeSet
        } else {
            // Nothing to review, but the garments that carried the deleted colour still lose it.
            declineColorReviewProposal(changeSet)
        }
    }

    // Save-first: the change is persisted before the snapshot is offered for review. The snapshot holds every garment,
    // archived ones too, and is read before the write, because a deleted tag is removed from the garments right away.
    fun offerTagReviewAfterWrite(changeSet: TagChangeSet?, write: () -> Unit) {
        if (!canStartTagReviewAction(tagReviewOfferInFlight, promptPending = pendingTagReview != null)) return
        tagReviewOfferInFlight = true
        reviewScope.launch {
            try {
                pendingTagReview = offerTagReviewRequest(changeSet, readGarments = readGarmentsForReview, write = write)
            } finally {
                tagReviewOfferInFlight = false
            }
        }
    }

    fun saveTagAndOfferReview(tag: GarmentTag) {
        offerTagReviewAfterWrite(savedTagChangeSet(availableTags, tag)) { onSaveTag(tag) }
    }

    fun deleteTagAndOfferReview(tag: GarmentTag) {
        offerTagReviewAfterWrite(deletedTagChangeSet(availableTags, tag)) { onDeleteCustomTag(tag) }
    }

    fun restoreDefaultTagsAndOfferReview(category: TagCategory) {
        val defaults = DefaultTags.tags.filter { tag -> tag.categoryId == category.id }
        offerTagReviewAfterWrite(restoredTagChangeSet(availableTags, category.id, defaults)) { onRestoreDefaultTags(category) }
    }

    fun commitTagReview(updatedItems: List<ClothingItem>) {
        if (updatedItems.isNotEmpty()) onSaveItems(updatedItems, emptyMap())
        activeTagReview = null
        showTagReviewDiscardDialog = false
        replaceRoute(RobiaRoute.ManageTags)
    }

    fun requestTagReviewDiscard() {
        showTagReviewDiscardDialog = true
    }

    // Read when the undo runs, after the write it follows, so the plan sees the garments that write left behind. Archived
    // garments are included: a tag they still reference must not be removed, and a deleted tag goes back onto them too.
    // A plain read, not readGarmentsForReview: the undo is itself the latest review write, so a read that joins the latest
    // write would join the undo from inside it and never return. ReviewWrites.launchUndo has already waited for the write it follows.
    suspend fun currentGarmentsForUndo(): List<ClothingItem> =
        app.database.wardrobeDao().getAllItemsForSync().map(ClothingItemWithTags::toDomain)

    // Discard = undo: the saved tag change is reversed through the normal paths. The garment proposals go with it.
    fun discardTagReview() {
        val request = activeTagReview
        if (request != null) {
            onUndoReview {
                tagReviewUndoPlan(request, currentGarmentsForUndo(), System.currentTimeMillis())
            }
        }
        activeTagReview = null
        showTagReviewDiscardDialog = false
        replaceRoute(RobiaRoute.ManageTags)
    }

    fun commitColorReview(changeSet: ColorPaletteChangeSet, updatedItems: List<ClothingItem>) {
        if (changeSet.requiresCommitOnSave) {
            // The restore removes palette rows: a garment the commit leaves out (a discarded card) is cleared of the removed colours too.
            val updatedIds = updatedItems.map(ClothingItem::id).toSet()
            onCommitMainColorChange(changeSet, updatedItems + clothingItemsWithoutRemovedColors(changeSet, excludeIds = updatedIds))
        } else {
            val acceptedIds = updatedItems.map(ClothingItem::id).toSet()
            val itemsToSave = updatedItems + clothingItemsWithoutRemovedColors(changeSet, excludeIds = acceptedIds)
            if (itemsToSave.isNotEmpty()) onSaveItems(itemsToSave, emptyMap())
        }
        activeColorReviewChangeSet = null
        pendingColorReviewChangeSet = null
        showColorReviewDiscardDialog = false
        replaceRoute(RobiaRoute.ManageTags)
    }

    // Restore defaults is confirmed in Manage. With no garment to review, the palette restore still applies and no screen opens.
    fun restoreDefaultMainColorsAndStartReview() {
        val beforePalette = mainColors
        pendingColorReviewChangeSet = null
        val changeSet = ColorPaletteChangeSet(
            beforePalette = beforePalette,
            afterPalette = DefaultTags.mainColors,
            touchedColorId = null,
            operation = ColorPaletteOperation.RestoredDefault,
            requiresCommitOnSave = true,
        )
        if (hasReviewableGarments(clothingItems, ReviewKind.ColourRestore)) {
            activeColorReviewChangeSet = changeSet
            replaceRoute(RobiaRoute.ColorReview)
        } else {
            commitColorReview(changeSet, emptyList())
        }
    }

    fun requestColorReviewDiscard() {
        showColorReviewDiscardDialog = true
    }

    // Tapping outside the tag prompt or going back cancels the saved change: the same undo as Discard, without a review.
    // Once only: a repeat tap, back after an outside tap, or a dismiss after Review finds the request already taken.
    fun cancelTagReviewPrompt(request: TagReviewRequest) {
        if (!isStillPending(pendingTagReview, request)) return
        pendingTagReview = null
        onUndoReview {
            tagReviewUndoPlan(request, currentGarmentsForUndo(), System.currentTimeMillis())
        }
    }

    // Tapping outside the colour prompt or going back cancels the saved change. Garments are untouched, so no decline.
    fun cancelColorReviewPrompt(changeSet: ColorPaletteChangeSet) {
        if (!isStillPending(pendingColorReviewChangeSet, changeSet)) return
        pendingColorReviewChangeSet = null
        onUndoReview { colorReviewUndoPlan(changeSet) }
    }

    // Discard = undo: the saved palette change is reversed. Garments are not written here, so no decline is called.
    fun discardColorReview() {
        val changeSet = activeColorReviewChangeSet
        if (changeSet != null) onUndoReview { colorReviewUndoPlan(changeSet) }
        activeColorReviewChangeSet = null
        showColorReviewDiscardDialog = false
        replaceRoute(RobiaRoute.ManageTags)
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

    fun closeFirstRunPrompt(prompt: FirstRunPrompt) {
        answeredFirstRunPrompts = answeredFirstRunPrompts + prompt
        activeFirstRunPrompt = null
    }

    fun dismissCloudSetupPrompt() {
        onCloudSetupPromptInteracted()
        closeFirstRunPrompt(FirstRunPrompt.CloudSetup)
    }

    fun confirmCloudSetupPrompt() {
        onCloudSetupPromptInteracted()
        closeFirstRunPrompt(FirstRunPrompt.CloudSetup)
        if (isFirstRunCloudSetup) {
            onRequestCloudSetup()
        }
    }

    fun laterModelPrompt() {
        onModelPromptShown()
        closeFirstRunPrompt(FirstRunPrompt.ModelDownload)
    }

    fun downloadModelFromPrompt() {
        onModelPromptShown()
        closeFirstRunPrompt(FirstRunPrompt.ModelDownload)
        startModelDownload(context)
    }

    // Combine closes the dialog at once; the Browse card shows the progress and the result.
    fun startMergeCombine() {
        mergeCycleAtTap = syncState.lastCycle
        mergeCombinePhase = MergeCombinePhase.Combining
        onConfirmMerge()
    }

    fun combineFromMergeDialog() {
        mergeDialogDismissed = true
        activeFirstRunPrompt = null
        startMergeCombine()
    }

    // Not now, outside tap and back keep the proposal as a Browse card. cancelMerge is NOT called: it would persist a conflict.
    fun keepMergeProposalForLater() {
        mergeDialogDismissed = true
        activeFirstRunPrompt = null
    }

    // Replacing is a destructive choice: the existing replace confirmation decides; cancelling it brings the proposal back.
    fun replaceFromMergeProposal() {
        activeFirstRunPrompt = null
        showUseBackupConfirmation = true
    }

    fun useBackupFromConflict() {
        activeFirstRunPrompt = null
        showUseBackupConfirmation = true
    }

    fun keepThisPhoneFromConflict() {
        activeFirstRunPrompt = null
        showKeepPhoneConfirmation = true
    }

    fun notNowFromConflict() {
        conflictDialogDismissed = true
        activeFirstRunPrompt = null
    }

    fun confirmUseBackup() {
        showUseBackupConfirmation = false
        conflictDialogDismissed = true
        mergeDialogDismissed = true
        onUseBackup()
    }

    fun confirmKeepThisPhone() {
        showKeepPhoneConfirmation = false
        conflictDialogDismissed = true
        onKeepThisPhone()
    }

    val restoreProgress = syncState.restoreProgress
    BackHandler(enabled = restoreProgress != null || routeStack.size > 1) {
        if (restoreProgress != null) {
            // Restore is transactional: navigation is locked until the gateway reports a terminal state.
        } else if (currentRoute == RobiaRoute.ColorReview) {
            requestColorReviewDiscard()
        } else if (currentRoute == RobiaRoute.TagReview) {
            requestTagReviewDiscard()
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
        ReviewDiscardDialog(
            title = stringResource(R.string.color_review_discard_title),
            body = stringResource(R.string.color_review_discard_body),
            onConfirm = ::discardColorReview,
            onDismiss = { showColorReviewDiscardDialog = false },
        )
    }

    if (showTagReviewDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showTagReviewDiscardDialog = false },
            title = { Text(stringResource(R.string.tag_review_discard_title)) },
            text = { Text(stringResource(R.string.tag_review_discard_body)) },
            confirmButton = {
                TextButton(onClick = ::discardTagReview) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagReviewDiscardDialog = false }) {
                    Text(stringResource(R.string.review_keep_reviewing))
                }
            },
        )
    }

    pendingTagReview?.let { request ->
        TagReviewPromptDialog(
            modelReady = app.modelStore.state.value is ModelState.Ready,
            onNotNow = { pendingTagReview = null },
            onCancel = { cancelTagReviewPrompt(request) },
            onReview = {
                // Cleared first: a dismiss callback after Review finds the request taken and cannot undo the change.
                pendingTagReview = null
                activeTagReview = request
                replaceRoute(RobiaRoute.TagReview)
            },
        )
    }

    pendingColorReviewChangeSet?.let { changeSet ->
        ColorReviewPromptDialog(
            onNotNow = { declineColorReviewProposal(changeSet) },
            onCancel = { cancelColorReviewPrompt(changeSet) },
            onReview = {
                // Cleared first, as for the tag prompt: a dismiss after Review cannot undo the change.
                pendingColorReviewChangeSet = null
                activeColorReviewChangeSet = changeSet
                replaceRoute(RobiaRoute.ColorReview)
            },
        )
    }

    when (activeFirstRunPrompt) {
        FirstRunPrompt.CloudSetup -> CloudSetupDialog(
            onDismiss = ::dismissCloudSetupPrompt,
            onConfirmSetup = ::confirmCloudSetupPrompt,
        )
        FirstRunPrompt.MergeConfirmation -> syncState.pendingMergeSummary?.let { summary ->
            MergeConfirmationDialog(
                summary = summary,
                onCombine = ::combineFromMergeDialog,
                onReplace = ::replaceFromMergeProposal,
                onNotNow = ::keepMergeProposalForLater,
            )
        }
        FirstRunPrompt.BackupConflict -> BackupConflictDialog(
            onUseBackup = ::useBackupFromConflict,
            onKeepThisPhone = ::keepThisPhoneFromConflict,
            onNotNow = ::notNowFromConflict,
        )
        FirstRunPrompt.ModelDownload -> ModelPromptDialog(
            onLater = ::laterModelPrompt,
            onDownloadNow = ::downloadModelFromPrompt,
        )
        null -> Unit
    }

    if (showUseBackupConfirmation) {
        UseBackupConfirmationDialog(
            onConfirm = ::confirmUseBackup,
            onCancel = { showUseBackupConfirmation = false },
        )
    }

    if (showKeepPhoneConfirmation) {
        KeepPhoneConfirmationDialog(
            onConfirm = ::confirmKeepThisPhone,
            onCancel = { showKeepPhoneConfirmation = false },
        )
    }

    val mergeProposalCardShown = (syncState.pendingMergeSummary != null && mergeDialogDismissed) ||
        mergeCombinePhase != MergeCombinePhase.Idle
    val mergeProposalCard: (@Composable () -> Unit)? = if (mergeProposalCardShown) {
        {
            MergeProposalCard(
                summary = syncState.pendingMergeSummary,
                phase = mergeCombinePhase,
                onCombine = ::startMergeCombine,
                onReplace = ::replaceFromMergeProposal,
                onDone = { mergeCombinePhase = MergeCombinePhase.Idle },
            )
        }
    } else {
        null
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
                                } else if (currentRoute == RobiaRoute.TagReview) {
                                    requestTagReviewDiscard()
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
                currentRoute != RobiaRoute.ColorReview &&
                currentRoute != RobiaRoute.TagReview
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
            items = displayedItems,
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
            cloudSetupSummaryRes = cloudIndicator.setupSummaryRes(canAttemptCloudSetup = isFirstRunCloudSetup),
            cloudSync = cloudIndicator,
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
                val isNewGarment = clothingItems.none { existing -> existing.id == item.id }
                onSaveItem(item, capture)
                if (isNewGarment) {
                    // The new garment is not in the list yet, so ItemDetail cannot show it: Browse shows it first instead.
                    browseQuery = ""
                    similarToGarmentId = null
                    browseFilters = BrowseFilterState()
                    browseNewestFirst = true
                    selectedItemId = null
                    replaceRoute(RobiaRoute.Browse)
                } else {
                    selectedItemId = item.id
                    replaceRoute(RobiaRoute.Browse)
                    pushRoute(RobiaRoute.ItemDetail)
                }
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
            onSaveTag = ::saveTagAndOfferReview,
            onSaveMainColor = ::saveMainColorAndOfferReview,
            onDeleteCustomTag = ::deleteTagAndOfferReview,
            onDeleteMainColor = ::deleteMainColorAndOfferReview,
            onRestoreDefaultTags = ::restoreDefaultTagsAndOfferReview,
            onRestoreDefaultMainColors = ::restoreDefaultMainColorsAndStartReview,
            tagReviewRequest = activeTagReview,
            prepareTagReviewScan = { changeSet, garments -> prepareTagReviewScan(app, changeSet, garments) },
            manageTagsTabId = manageTagsTabId,
            onManageTagsTabIdChange = { manageTagsTabId = it },
            onCommitTagReview = ::commitTagReview,
            onRequestTagReviewDiscard = ::requestTagReviewDiscard,
            showTagReviewDiscardDialog = showTagReviewDiscardDialog,
            onCommitColorReview = ::commitColorReview,
            onCloseColorReview = {
                activeColorReviewChangeSet = null
                replaceRoute(RobiaRoute.ManageTags)
            },
            onRequestColorReviewDiscard = ::requestColorReviewDiscard,
            showColorReviewDiscardDialog = showColorReviewDiscardDialog,
            onClearRestoreSyncLog = onClearRestoreSyncLog,
            onLanguageSelected = onLanguageSelected,
            onDeveloperModeEnabledChange = onDeveloperModeEnabledChange,
            onCloudSetupClick = ::requestCloudSetup,
            onDeleteBackupClick = { showBackupDeletionConfirmation = true },
            onRestoreSyncLogClick = { pushRoute(RobiaRoute.DeveloperSyncLog) },
            onRetryRestoredPhoto = onRetryRestoredPhoto,
            browseAttentions = browseAttentions,
            mergeProposalCard = mergeProposalCard,
            onSyncAttentionAction = ::handleAttentionAction,
            onKeepWithoutPhoto = onKeepWithoutPhoto,
            onRetryAttention = onRetryAttention,
            searchQuery = browseQuery,
            onSearchQueryChange = { browseQuery = it },
            searchMode = searchMode,
            searchInProgress = searchInProgress,
            similarToName = similarSource?.name,
            onClearSimilar = { similarToGarmentId = null },
            onClearSearch = {
                browseQuery = ""
                similarToGarmentId = null
            },
            modelState = modelState,
            onOpenSettings = { pushRoute(RobiaRoute.Settings) },
            canSeeSimilar = selectedHasImageVector,
            onSeeSimilarClick = {
                similarToGarmentId = selectedItemId
                browseQuery = ""
                replaceRoute(RobiaRoute.Browse)
            },
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
    syncState: WardrobeSyncState,
    @StringRes cloudSetupSummaryRes: Int,
    cloudIndicator: CloudSyncIndicator,
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
                                text = stringResource(cloudIndicator.headline.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            cloudIndicator.queueSummaryText()?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                    leadingContent = { Icon(cloudIndicator.headline.icon, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onCloudSetupClick),
                )
            }
        }
        if (developerModeEnabled) {
            item {
                DeveloperCloudSyncCard(syncState = syncState, indicator = cloudIndicator)
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
private fun CloudSyncIndicator.setupSummaryRes(canAttemptCloudSetup: Boolean): Int = when (headline) {
    CloudSyncHeadline.NotConfigured -> if (canAttemptCloudSetup) {
        R.string.cloud_setup_recommended_summary
    } else {
        R.string.cloud_setup_late_blocked_summary
    }
    CloudSyncHeadline.Disabled -> R.string.cloud_setup_late_blocked_summary
    CloudSyncHeadline.Error -> R.string.garment_sync_auth_blocked
    CloudSyncHeadline.Syncing -> R.string.garment_sync_syncing
    CloudSyncHeadline.Pending -> R.string.garment_sync_queued
    CloudSyncHeadline.Waiting -> R.string.cloud_sync_headline_waiting
    CloudSyncHeadline.Synced,
    CloudSyncHeadline.Attention -> R.string.cloud_setup_configured_summary
}

private val CloudSyncHeadline.labelRes: Int
    get() = when (this) {
        CloudSyncHeadline.Synced -> R.string.cloud_sync_headline_synced
        CloudSyncHeadline.Syncing -> R.string.drive_sync_status_syncing
        CloudSyncHeadline.Pending -> R.string.cloud_sync_headline_pending
        CloudSyncHeadline.Waiting -> R.string.cloud_sync_headline_waiting
        CloudSyncHeadline.Attention -> R.string.drive_sync_status_needs_attention
        CloudSyncHeadline.Error -> R.string.drive_sync_status_disconnected
        CloudSyncHeadline.Disabled -> R.string.drive_sync_status_disabled
        CloudSyncHeadline.NotConfigured -> R.string.drive_sync_status_not_configured
    }

private val CloudSyncHeadline.icon: ImageVector
    get() = when (this) {
        CloudSyncHeadline.Synced -> Icons.Rounded.CloudDone
        CloudSyncHeadline.Syncing,
        CloudSyncHeadline.Pending -> Icons.Rounded.CloudUpload
        CloudSyncHeadline.Waiting -> Icons.Rounded.CloudQueue
        CloudSyncHeadline.Attention -> Icons.Rounded.Error
        CloudSyncHeadline.Error,
        CloudSyncHeadline.Disabled,
        CloudSyncHeadline.NotConfigured -> Icons.Rounded.CloudOff
    }

/** Null hides the line: a syncing or attention state with nothing pending says nothing here rather than "synced". */
@Composable
private fun CloudSyncIndicator.queueSummaryText(): String? = when {
    headline == CloudSyncHeadline.Synced -> stringResource(R.string.garment_sync_all_synced)
    pendingCount <= 0 -> null
    headline == CloudSyncHeadline.NotConfigured || headline == CloudSyncHeadline.Disabled ->
        stringResource(R.string.garment_sync_local_changes_saved, pendingCount)
    pendingCount == 1 -> stringResource(R.string.garment_sync_pending_count_one)
    else -> stringResource(R.string.garment_sync_pending_count_many, pendingCount)
}

private const val CLOUD_STATUS_POPUP_MS = 4_000L

/**
 * Browse indicator: a cloud icon that pulses while syncing, tinted when the user has to act. Same source as Settings.
 * A tap opens a small popup that explains the state; it closes by itself, on an outside tap or on its action.
 */
@Composable
private fun CloudSyncStatusIcon(indicator: CloudSyncIndicator, onOpenDataSync: () -> Unit) {
    val description = stringResource(indicator.headline.labelRes)
    val tint = if (indicator.attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val explanation = cloudStatusExplanation(indicator.headline, indicator.pendingCount)
    var explanationOpen by remember { mutableStateOf(false) }
    LaunchedEffect(explanationOpen) {
        if (explanationOpen) {
            delay(CLOUD_STATUS_POPUP_MS)
            explanationOpen = false
        }
    }
    Box {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .semantics { contentDescription = description }
                .then(
                    if (explanation != null) {
                        Modifier.clickable(role = Role.Button) { explanationOpen = !explanationOpen }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (indicator.headline == CloudSyncHeadline.Syncing) {
                PulsingCloudIcon(tint = tint)
                Text(
                    text = stringResource(R.string.drive_sync_status_syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            } else {
                Icon(
                    imageVector = indicator.headline.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (explanation != null && explanationOpen) {
            CloudStatusPopup(
                explanation = explanation,
                onOpenDataSync = {
                    explanationOpen = false
                    onOpenDataSync()
                },
                onDismiss = { explanationOpen = false },
            )
        }
    }
}

/** Non-modal: the window is not focusable, so it does not take focus or block the screen behind it. */
@Composable
private fun CloudStatusPopup(
    explanation: CloudStatusExplanation,
    onOpenDataSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val message = explanation.quantity?.let { count ->
        context.resources.getQuantityString(explanation.messageRes, count, count)
    } ?: stringResource(explanation.messageRes)
    val offsetY = with(LocalDensity.current) { 48.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                if (explanation.opensDataSync) {
                    TextButton(onClick = onOpenDataSync) {
                        Text(text = stringResource(R.string.cloud_status_open_data_sync))
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingCloudIcon(tint: Color) {
    val pulse by rememberInfiniteTransition(label = "cloud-sync-pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloud-sync-pulse-alpha",
    )
    Icon(
        imageVector = Icons.Rounded.CloudUpload,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp).alpha(pulse),
    )
}

/** Developer mode only: the last cycle, what was pending and whether the processor is running, read from the same state. */
@Composable
private fun DeveloperCloudSyncCard(syncState: WardrobeSyncState, indicator: CloudSyncIndicator) {
    val cycle = syncState.lastCycle
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val processing = syncState.isRunningNow
    SettingsSection(title = stringResource(R.string.dev_cloud_title)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.dev_cloud_status, stringResource(indicator.headline.labelRes)),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(R.string.dev_cloud_pending, indicator.pendingCount),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(
                    R.string.dev_cloud_processing,
                    stringResource(if (processing) R.string.dev_cloud_yes else R.string.dev_cloud_no),
                ),
                style = MaterialTheme.typography.labelSmall,
            )
            if (cycle == null) {
                Text(
                    text = stringResource(R.string.dev_cloud_no_cycle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.dev_cloud_started, timeFormat.format(Date(cycle.startedAtEpochMillis))),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_ended, timeFormat.format(Date(cycle.endedAtEpochMillis))),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_result, stringResource(cycle.result.readableNameRes())),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_pending_at_start, cycle.pendingAtStart),
                    style = MaterialTheme.typography.labelSmall,
                )
                indicator.detail?.let { detail ->
                    if (indicator.offline) {
                        // A connection failure is not an error: neutral wording, the raw class stays as secondary text.
                        Text(
                            text = stringResource(R.string.cloud_sync_headline_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dev_cloud_error, detail),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
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

/**
 * Dialog with its choices stacked full width in the button area, so long EN/ES/DE labels never share a row.
 * Cancel, Not now, outside tap and back close it without changing data unless an action is chosen.
 */
@Composable
private fun StackedActionDialog(
    title: String,
    onDismiss: () -> Unit,
    text: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text,
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                content = actions,
            )
        },
    )
}

@Composable
private fun DestructiveButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(label) }
}

@Composable
private fun DestructiveOutlinedButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { Text(label) }
}

@Composable
private fun BackupConflictDialog(
    onUseBackup: () -> Unit,
    onKeepThisPhone: () -> Unit,
    onNotNow: () -> Unit,
) {
    StackedActionDialog(
        title = stringResource(R.string.sync_attention_conflict_title),
        onDismiss = onNotNow,
        text = { Text(stringResource(R.string.sync_attention_conflict_body)) },
        actions = {
            DestructiveButton(stringResource(R.string.sync_conflict_use_backup), onUseBackup)
            DestructiveButton(stringResource(R.string.sync_conflict_keep_phone), onKeepThisPhone)
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

@Composable
private fun UseBackupConfirmationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    StackedActionDialog(
        title = stringResource(R.string.sync_use_backup_confirm_title),
        onDismiss = onCancel,
        text = { Text(stringResource(R.string.sync_use_backup_confirm_body)) },
        actions = {
            DestructiveButton(stringResource(R.string.sync_use_backup_confirm_action), onConfirm)
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun KeepPhoneConfirmationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    StackedActionDialog(
        title = stringResource(R.string.sync_keep_phone_confirm_title),
        onDismiss = onCancel,
        text = { Text(stringResource(R.string.sync_keep_phone_confirm_body)) },
        actions = {
            DestructiveButton(stringResource(R.string.sync_keep_phone_confirm_action), onConfirm)
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MergeConfirmationDialog(
    summary: MergeSummary,
    onCombine: () -> Unit,
    onReplace: () -> Unit,
    onNotNow: () -> Unit,
) {
    StackedActionDialog(
        title = stringResource(R.string.sync_merge_title),
        onDismiss = onNotNow,
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.sync_merge_plain_body,
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
        actions = {
            Button(onClick = onCombine, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_merge_confirm))
            }
            DestructiveOutlinedButton(stringResource(R.string.sync_merge_replace), onReplace)
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

/** Browse card for a merge proposal that was kept, or one being combined. Its buttons never write without a tap. */
@Composable
private fun MergeProposalCard(
    summary: MergeSummary?,
    phase: MergeCombinePhase,
    onCombine: () -> Unit,
    onReplace: () -> Unit,
    onDone: () -> Unit,
) {
    val combining = phase == MergeCombinePhase.Combining
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sync_merge_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            summary?.let { merge ->
                Text(
                    text = stringResource(
                        R.string.sync_merge_plain_body,
                        merge.remoteGarmentCount,
                        merge.localGarmentCount,
                        merge.mergedGarmentCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val status = when (phase) {
                MergeCombinePhase.Idle -> null
                MergeCombinePhase.Combining -> R.string.sync_merge_combining
                MergeCombinePhase.Done -> R.string.sync_merge_combined
                MergeCombinePhase.Failed -> R.string.sync_merge_combine_failed
            }
            status?.let { statusRes ->
                Text(
                    text = stringResource(statusRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (phase == MergeCombinePhase.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (phase == MergeCombinePhase.Done) {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_merge_result_ok))
                    }
                } else {
                    Button(onClick = onCombine, enabled = !combining, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_merge_confirm))
                    }
                    DestructiveOutlinedButton(
                        label = stringResource(R.string.sync_merge_replace),
                        onClick = onReplace,
                        enabled = !combining,
                    )
                }
            }
        }
    }
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
    @StringRes cloudSetupSummaryRes: Int,
    cloudSync: CloudSyncIndicator,
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
    showColorReviewDiscardDialog: Boolean,
    tagReviewRequest: TagReviewRequest?,
    prepareTagReviewScan: suspend (TagChangeSet, List<ClothingItem>) -> ReviewPlanner.Scan,
    manageTagsTabId: String?,
    onManageTagsTabIdChange: (String?) -> Unit,
    onCommitTagReview: (List<ClothingItem>) -> Unit,
    onRequestTagReviewDiscard: () -> Unit,
    showTagReviewDiscardDialog: Boolean,
    onClearRestoreSyncLog: () -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onCloudSetupClick: () -> Unit,
    onDeleteBackupClick: () -> Unit,
    onRestoreSyncLogClick: () -> Unit,
    onRetryRestoredPhoto: (String) -> Unit,
    browseAttentions: List<SyncAttention>,
    mergeProposalCard: (@Composable () -> Unit)? = null,
    onSyncAttentionAction: (SyncAttention, SyncAttentionAction) -> Unit,
    onKeepWithoutPhoto: (String) -> Unit,
    onRetryAttention: (SyncAttentionKind) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMode: SearchMode,
    searchInProgress: Boolean,
    similarToName: String?,
    onClearSimilar: () -> Unit,
    onClearSearch: () -> Unit,
    modelState: ModelState,
    onOpenSettings: () -> Unit,
    canSeeSimilar: Boolean,
    onSeeSimilarClick: () -> Unit,
) {
    when (currentRoute) {
        RobiaRoute.Browse -> BrowseWardrobeScreen(
            innerPadding = innerPadding,
            items = items,
            isInitialLoading = wardrobeItemsLoading,
            hasItemsInWardrobe = totalItemCount > 0,
            hasActiveFilters = filters.hasActiveFilters,
            activeFilterCount = filters.activeFilterCount,
            cloudSync = cloudSync,
            selectedItemIds = selectedBrowseItemIds,
            onItemSelected = onItemSelected,
            onToggleFavorite = onToggleFavorite,
            onSelectionToggle = onBrowseSelectionToggle,
            onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) },
            onFiltersClick = { onRouteSelected(RobiaRoute.AdvancedFilters) },
            onResetFilters = onResetFilters,
            attentions = browseAttentions,
            mergeProposalCard = mergeProposalCard,
            onAttentionAction = onSyncAttentionAction,
            showSyncDetails = developerModeEnabled,
            onSyncDetailsClick = onRestoreSyncLogClick,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            searchMode = searchMode,
            searchInProgress = searchInProgress,
            similarToName = similarToName,
            onClearSimilar = onClearSimilar,
            onClearSearch = onClearSearch,
            modelState = modelState,
            onOpenSettings = onOpenSettings,
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
            selectedTabId = manageTagsTabId,
            onSelectedTabIdChange = onManageTagsTabIdChange,
        )
        RobiaRoute.ColorReview -> colorReviewChangeSet?.let { changeSet ->
            ColorReviewScreen(
                innerPadding = innerPadding,
                items = domainItems,
                changeSet = changeSet,
                onCommit = onCommitColorReview,
                discardDialogOpen = showColorReviewDiscardDialog,
                onRequestDiscard = onRequestColorReviewDiscard,
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.TagReview -> tagReviewRequest?.let { request ->
            TagReviewScreen(
                innerPadding = innerPadding,
                request = request,
                prepareScan = prepareTagReviewScan,
                discardDialogOpen = showTagReviewDiscardDialog,
                onCommit = onCommitTagReview,
                onRequestDiscard = onRequestTagReviewDiscard,
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
                canSeeSimilar = canSeeSimilar,
                onSeeSimilarClick = onSeeSimilarClick,
                showDiagnostics = showSuggestionDiagnostics(developerModeUnlocked, developerModeEnabled),
                diagnosticsGarment = domainItems.firstOrNull { garment -> garment.id == item.id },
                diagnosticsTags = availableTags,
                suggestionService = suggestionService,
            )
        } ?: EmptyStateCard(onAddClick = { onRouteSelected(RobiaRoute.AddEditClothing) })
        RobiaRoute.LanguageSettings -> LanguageSettingsScreen(innerPadding)
        RobiaRoute.Settings -> SettingsScreen(
            innerPadding = innerPadding,
            currentLanguage = currentLanguage,
            driveSyncConnectionStatus = driveSyncConnectionStatus,
            syncState = syncState,
            cloudSetupSummaryRes = cloudSetupSummaryRes,
            cloudIndicator = cloudSync,
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
                syncState = syncState,
                cloudSetupSummaryRes = cloudSetupSummaryRes,
                cloudIndicator = cloudSync,
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
private fun FilterBar(
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    cloudSync: CloudSyncIndicator,
    onFiltersClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMode: SearchMode,
    searchInProgress: Boolean,
    resultCount: Int,
    similarToName: String?,
    onClearSimilar: () -> Unit,
    modelState: ModelState,
    onOpenSettings: () -> Unit,
) {
    val filterLabel = if (hasActiveFilters) {
        stringResource(R.string.active_filters_count, activeFilterCount)
    } else {
        stringResource(R.string.filters)
    }
    val searchVisible = browseSearchVisible(modelState)
    val context = LocalContext.current
    var showSmartPrompt by rememberSaveable { mutableStateOf(false) }

    // A query typed before the model went missing must not keep filtering while no search field is shown.
    LaunchedEffect(searchVisible) {
        if (!searchVisible) onSearchQueryChange("")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (similarToName != null) {
            SimilarGarmentsChip(name = similarToName, onClear = onClearSimilar)
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            AnimatedVisibility(
                visible = searchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    BrowseSearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (!searchVisible) {
                    IconButton(onClick = { showSmartPrompt = true }) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.model_prompt_title),
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (cloudSync.headline.showsBrowseIndicator) {
                    CloudSyncStatusIcon(indicator = cloudSync, onOpenDataSync = onOpenSettings)
                }
            }
            if (searchInProgress) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.browse_search_in_progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (similarToName != null) {
                Text(
                    text = stringResource(R.string.browse_similar_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else when (searchMode) {
                SearchMode.Semantic -> Text(
                    text = stringResource(R.string.browse_sorted_by_similarity, resultCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchMode.Text -> Text(
                    text = stringResource(R.string.browse_search_text_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchMode.None -> Unit
            }
        }
    }

    if (showSmartPrompt) {
        ModelPromptDialog(
            onLater = { showSmartPrompt = false },
            onDownloadNow = {
                showSmartPrompt = false
                startModelDownload(context)
            },
            onOpenSettings = {
                showSmartPrompt = false
                onOpenSettings()
            },
        )
    }
}

@Composable
private fun BrowseSearchField(query: String, onQueryChange: (String) -> Unit) {
    val hint = stringResource(R.string.browse_search_hint)
    val clearDescription = stringResource(R.string.browse_search_clear)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(percent = 50),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = clearDescription)
                }
            }
        },
    )
}

@Composable
private fun SimilarGarmentsChip(name: String, onClear: () -> Unit) {
    val clearDescription = stringResource(R.string.browse_search_clear)
    val untitledName = stringResource(R.string.browse_similar_untitled)

    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(stringResource(R.string.browse_similar_to, name.ifBlank { untitledName })) },
        leadingIcon = { Icon(Icons.Outlined.Checkroom, contentDescription = null) },
        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = clearDescription) },
    )
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
private fun UiWardrobeItem.garmentCloudStatusLabel(): String = when {
    waitingForConnection -> stringResource(R.string.cloud_sync_headline_waiting)
    else -> when (syncStatus) {
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
        if (canSeeSimilar) {
            item {
                FilledTonalButton(onClick = onSeeSimilarClick, modifier = Modifier.fillMaxWidth()) {
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
        item { DetailMetadataGrid(item = item, onEditClick = onEditClick) }
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
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
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
    val context = LocalContext.current
    val sortedTags = tags.sortedAlphabetically(label = { tag -> tag.localizedTagLabel(context) })
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
        sortedTags.forEach { tag ->
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
                colors.sortedAlphabetically(label = MainColor::name).forEach { color ->
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

/** A garment's cloud line: its status as the connection allows it, and whether it only waits for a connection. */
internal data class GarmentCloudState(
    val status: GarmentSyncStatus,
    val waitingForConnection: Boolean,
)

/** Statuses that wait for an upload: offline they read "Waiting for connection" rather than a stuck upload. */
internal val WaitingForConnectionStatuses = setOf(
    GarmentSyncStatus.Dirty,
    GarmentSyncStatus.Queued,
    GarmentSyncStatus.FailedRetryable,
)

internal fun GarmentSyncStatus.resolveForConnection(
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    online: Boolean,
): GarmentCloudState {
    val status = when {
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
    // Without a configured or enabled Drive there is no connection to wait for: the garment stays saved on this phone.
    val driveEnabled = driveSyncConnectionStatus != DriveSyncConnectionStatus.NotConfigured &&
        driveSyncConnectionStatus != DriveSyncConnectionStatus.Disabled
    return GarmentCloudState(
        status = status,
        waitingForConnection = !online && driveEnabled && status in WaitingForConnectionStatuses,
    )
}

private val GarmentCloudState.labelRes: Int
    get() = if (waitingForConnection) R.string.cloud_sync_headline_waiting else status.itemStatusLabelRes

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
            readGarmentsForReview = { emptyList() },
            onCommitMainColorChange = { _, _ -> },
        )
    }
}

/**
 * Planner state for a tag review scan (DEC-010, superseded by PLAN-CHANGE 15). Label vectors come from the same recipe as
 * the garment vectors. Throws [EmbeddingFailure] while the model is not ready; the screen shows that state.
 */
private suspend fun prepareTagReviewScan(
    app: RobiaApplication,
    changeSet: TagChangeSet,
    garments: List<ClothingItem>,
): ReviewPlanner.Scan {
    val vectors = EmbeddingRepository(app.database.embeddingDao())
        .observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        .first()
    val labelVectors = changeSet.afterTags
        .filter { tag -> tag.categoryId == changeSet.categoryId }
        .associate { tag -> tag.id to app.embeddingEngineHolder.embedText(reviewScanLabelText(tag, changeSet.touchedTagIds)) }
    return ReviewPlanner.scan(changeSet, garments, vectors, labelVectors, changeSet.afterTags)
}

/**
 * Builds an undo plan only after [previousWrite] has finished, so the plan reads what that write left behind. `join` waits
 * and does not rethrow a failed write, so a failed save never blocks the undo. Cancelling the caller during the wait still
 * propagates, which is why this is not wrapped in runCatching.
 */
internal suspend fun <T> afterPreviousWrite(previousWrite: Job?, buildPlan: suspend () -> T): T {
    previousWrite?.join()
    return buildPlan()
}

/**
 * The tag and colour writes of one screen session. [launch] runs a write's database part on [scope], a SupervisorJob: a failed
 * save must not cancel the undo. The sync operations it returns are enqueued afterwards, detached: a sync cycle takes seconds
 * (Drive calls), and a snapshot that joined it would hold the review prompt back. [latest] is the job the next snapshot or undo
 * joins, database part only.
 */
internal class ReviewWrites(
    private val scope: CoroutineScope,
    private val enqueue: suspend (WardrobeSyncOperation) -> Unit,
) {
    var latest: Job? = null
        private set

    fun launch(write: suspend CoroutineScope.() -> List<WardrobeSyncOperation>) {
        // Lazy start: [latest] is set before the write can run, so nothing reads a stale value while it is being launched.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val operations = write()
            scope.launch { operations.forEach { operation -> enqueue(operation) } }
        }
        latest = job
        job.start()
    }

    /**
     * Discard = undo as one write: it waits for the write it follows, builds its plan, then applies it. The plan must not join
     * [latest] (see RobiaShell's currentGarmentsForUndo): [latest] is this undo, so a join from inside would never return.
     */
    fun launchUndo(
        buildPlan: suspend () -> ReviewUndoPlan,
        applyPlan: suspend (ReviewUndoPlan) -> List<WardrobeSyncOperation>,
    ) {
        val previousWrite = latest
        launch { applyPlan(afterPreviousWrite(previousWrite, buildPlan)) }
    }
}

/** A prompt's cancel or confirm acts only while its request is still the pending one; a stale callback finds it taken. */
internal fun <T : Any> isStillPending(pending: T?, request: T): Boolean = pending === request

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
