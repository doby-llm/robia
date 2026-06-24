package com.gusanitolabs.robia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.LocaleList
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import com.gusanitolabs.robia.core.model.DisplayColorLabel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private sealed interface RobiaRoute {
    @get:StringRes
    val titleRes: Int

    data object Browse : RobiaRoute {
        override val titleRes = R.string.browse
    }

    data object ManageTags : RobiaRoute {
        override val titleRes = R.string.manage
    }

    data object AddEditClothing : RobiaRoute {
        override val titleRes = R.string.add_clothing
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
) {
    val settings by settingsRepository.settings.collectAsState(initial = RobiaSettings())
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
        RobiaShell(
            settings = settings,
            clothingItems = clothingItems,
            tagCategories = tagCategories,
            availableTags = availableTags,
            mainColors = mainColors,
            onLanguageSelected = { language ->
                scope.launch { settingsRepository.setLanguagePreference(language) }
            },
            onSaveItem = { item ->
                scope.launch { wardrobeRepository.upsertItem(item) }
            },
            onSaveTag = { tag ->
                scope.launch { tagRepository.upsertTag(tag) }
            },
            onSaveMainColor = { color ->
                scope.launch { tagRepository.upsertMainColor(color) }
            },
            onDeleteCustomTag = { tag ->
                scope.launch { tagRepository.deleteCustomTag(tag.id) }
            },
            onDeleteMainColor = { color ->
                scope.launch { tagRepository.deleteMainColor(color.id) }
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
    clothingItems: List<ClothingItem>,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onSaveItem: (ClothingItem) -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
) {
    val routeStack = remember { mutableStateListOf<RobiaRoute>(RobiaRoute.Browse) }
    val currentRoute = routeStack.last()
    var settingsExpanded by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    var browseFilters by remember { mutableStateOf(BrowseFilterState()) }
    val items = remember(clothingItems) { clothingItems.toUiWardrobeItems() }
    val filteredItems = remember(items, browseFilters, mainColors) {
        items.filter { item -> browseFilters.matches(item, mainColors) }
    }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val selectedDomainItem = clothingItems.firstOrNull { it.id == selectedItemId }

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

    BackHandler(enabled = routeStack.size > 1) { popRoute() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (routeStack.size > 1 && currentRoute != RobiaRoute.AddEditClothing) {
                        IconButton(onClick = ::popRoute) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.content_go_back),
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = stringResource(currentRoute.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (currentRoute == RobiaRoute.ItemDetail && selectedItem != null) {
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
                                onClick = { settingsExpanded = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                )
                            }
                            SettingsMenu(
                                expanded = settingsExpanded,
                                currentLanguage = settings.languagePreference,
                                onLanguageSelected = { language ->
                                    onLanguageSelected(language)
                                    settingsExpanded = false
                                },
                                onDismiss = { settingsExpanded = false },
                                onLanguageClick = {
                                    pushRoute(RobiaRoute.LanguageSettings)
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
            if (currentRoute != RobiaRoute.ItemDetail && currentRoute != RobiaRoute.AdvancedFilters) {
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
            totalItemCount = items.size,
            filters = browseFilters,
            selectedItem = selectedItem,
            selectedDomainItem = selectedDomainItem,
            tagCategories = tagCategories,
            availableTags = availableTags,
            mainColors = mainColors,
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
            onCancelAddEdit = ::closeAddEdit,
            onSaveItem = { item ->
                onSaveItem(item)
                selectedItemId = item.id
                replaceRoute(RobiaRoute.Browse)
                pushRoute(RobiaRoute.ItemDetail)
            },
            onFiltersChange = { browseFilters = it },
            onResetFilters = { browseFilters = BrowseFilterState() },
            onSaveTag = onSaveTag,
            onSaveMainColor = onSaveMainColor,
            onDeleteCustomTag = onDeleteCustomTag,
            onDeleteMainColor = onDeleteMainColor,
        )
    }
}

@Composable
private fun SettingsMenu(
    expanded: Boolean,
    currentLanguage: LanguagePreference,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onDismiss: () -> Unit,
    onLanguageClick: () -> Unit,
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
        DropdownMenuItem(
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.data_sync))
                    Text(
                        text = stringResource(R.string.data_sync_coming_soon),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            leadingIcon = { Icon(Icons.Rounded.CloudOff, contentDescription = null) },
            onClick = { },
            enabled = false,
        )
    }
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
    totalItemCount: Int,
    filters: BrowseFilterState,
    selectedItem: UiWardrobeItem?,
    selectedDomainItem: ClothingItem?,
    tagCategories: List<TagCategory>,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    onRouteSelected: (RobiaRoute) -> Unit,
    onBack: () -> Unit,
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCancelAddEdit: () -> Unit,
    onSaveItem: (ClothingItem) -> Unit,
    onFiltersChange: (BrowseFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onSaveTag: (GarmentTag) -> Unit,
    onSaveMainColor: (MainColor) -> Unit,
    onDeleteCustomTag: (GarmentTag) -> Unit,
    onDeleteMainColor: (MainColor) -> Unit,
) {
    when (currentRoute) {
        RobiaRoute.Browse -> BrowseWardrobeScreen(
            innerPadding = innerPadding,
            items = items,
            hasItemsInWardrobe = totalItemCount > 0,
            hasActiveFilters = filters.hasActiveFilters,
            activeFilterCount = filters.activeFilterCount,
            onItemSelected = onItemSelected,
            onToggleFavorite = onToggleFavorite,
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
        )
        RobiaRoute.AddEditClothing -> AddEditClothingScreen(
            innerPadding = innerPadding,
            availableTags = availableTags,
            mainColors = mainColors,
            existingItem = selectedDomainItem,
            onCancel = onCancelAddEdit,
            onSave = onSaveItem,
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
    onItemSelected: (UiWardrobeItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddClick: () -> Unit,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
) {
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
                onClick = { onItemSelected(item) },
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
private fun GarmentGridCard(
    item: UiWardrobeItem,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val itemDescription = stringResource(R.string.content_open_item_detail)
    val favoriteDescription = stringResource(R.string.content_favorite)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = itemDescription }
            .clickable(onClick = onClick),
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
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
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
                                context.launchShareChooser(
                                    uri = shareUri,
                                    mimeType = "image/png",
                                    chooserTitle = chooserTitle,
                                )
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
                            context.launchShareChooser(
                                uri = shareUri,
                                mimeType = "application/pdf",
                                chooserTitle = chooserTitle,
                            )
                        } else {
                            Toast.makeText(context, pdfShareErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.notes.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, chooserTitle))
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
private fun List<ClothingItem>.toUiWardrobeItems(): List<UiWardrobeItem> = map { item ->
    UiWardrobeItem(
        id = item.id,
        name = item.name,
        subtitle = item.notes.ifBlank { stringResource(R.string.wardrobe_item_saved_locally) },
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

@Composable
private fun GarmentTag.localizedLabel(): String = when (id) {
    "category-shorts" -> stringResource(R.string.tag_shorts)
    "category-jackets" -> stringResource(R.string.tag_jackets)
    "category-jumpsuits" -> stringResource(R.string.tag_jumpsuits)
    "category-blouses" -> stringResource(R.string.tag_blouses)
    "category-dresses" -> stringResource(R.string.tag_dresses)
    "category-skirts" -> stringResource(R.string.tag_skirts)
    "category-blazers" -> stringResource(R.string.tag_blazers)
    "category-cardigans" -> stringResource(R.string.tag_cardigans)
    "category-bags" -> stringResource(R.string.tag_bags)
    "category-tops" -> stringResource(R.string.tag_tops)
    "category-knitwear" -> stringResource(R.string.tag_knitwear)
    "category-trousers" -> stringResource(R.string.tag_trousers)
    "category-sweaters" -> stringResource(R.string.tag_sweaters)
    "category-shoes" -> stringResource(R.string.tag_shoes)
    "category-shirts" -> stringResource(R.string.tag_shirts)
    "category-vests" -> stringResource(R.string.tag_vests)
    "category-jewelry" -> stringResource(R.string.tag_jewelry)
    "category-accessories" -> stringResource(R.string.tag_accessories)
    "category-coats" -> stringResource(R.string.tag_coats)
    "season-spring" -> stringResource(R.string.tag_spring)
    "season-summer" -> stringResource(R.string.tag_summer)
    "season-fall" -> stringResource(R.string.tag_fall)
    "season-winter" -> stringResource(R.string.tag_winter)
    "occasion-active" -> stringResource(R.string.tag_active)
    "occasion-statement" -> stringResource(R.string.tag_statement)
    "occasion-dressed-up" -> stringResource(R.string.tag_dressed_up)
    "occasion-formal" -> stringResource(R.string.tag_formal)
    "occasion-everyday" -> stringResource(R.string.tag_everyday)
    "occasion-business" -> stringResource(R.string.tag_business)
    "location-main-closet" -> stringResource(R.string.tag_main_closet)
    else -> name
}

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
            clothingItems = emptyList(),
            tagCategories = emptyList(),
            availableTags = emptyList(),
            mainColors = emptyList(),
            onLanguageSelected = {},
            onSaveItem = {},
            onSaveTag = {},
            onSaveMainColor = {},
            onDeleteCustomTag = {},
            onDeleteMainColor = {},
        )
    }
}
