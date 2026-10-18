package com.gusanitolabs.robia.ui

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VISIBLE_TAG_CHIPS = 8
private const val COLOR_GRID_COLUMNS = 6

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedFiltersScreen(
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onShowResults) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.filters_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = onResetFilters, enabled = filters.hasActiveFilters) {
                        Text(stringResource(R.string.reset_filters_short))
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
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Button(
                    onClick = onShowResults,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allItemCount == 0 || resultCount > 0 || !filters.hasActiveFilters,
                ) {
                    Text(pluralStringResource(R.plurals.filters_show_count, resultCount, resultCount))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                FilterCard(
                    title = stringResource(R.string.filter_favorites),
                    icon = if (filters.favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    count = if (filters.favoritesOnly) 1 else 0,
                ) {
                    FavoritesSwitchRow(
                        checked = filters.favoritesOnly,
                        onCheckedChange = { onFiltersChange(filters.toggleFavoritesOnly()) },
                    )
                }
            }
            item {
                TagFilterCard(
                    categoryId = "category",
                    title = stringResource(R.string.filter_category),
                    tags = categoryTags,
                    filters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            item {
                TagFilterCard(
                    categoryId = "season",
                    title = stringResource(R.string.filter_season),
                    tags = seasonTags,
                    filters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            item {
                TagFilterCard(
                    categoryId = "occasion",
                    title = stringResource(R.string.filter_occasion),
                    tags = occasionTags,
                    filters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            item {
                TagFilterCard(
                    categoryId = "location",
                    title = stringResource(R.string.filter_location),
                    tags = locationTags,
                    filters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            item {
                FilterCard(
                    title = stringResource(R.string.filter_color_palette),
                    icon = null,
                    count = filters.selectedPaletteColorIds.size,
                ) {
                    ColorPaletteGrid(
                        colors = mainColors,
                        selectedColorIds = filters.selectedPaletteColorIds,
                        onColorToggled = { color -> onFiltersChange(filters.togglePaletteColor(color.id)) },
                    )
                }
            }
            if (allItemCount > 0 && resultCount == 0 && filters.hasActiveFilters) {
                item { EmptyFiltersCard(onResetFilters = onResetFilters) }
            }
        }
    }
}

@Composable
private fun FilterCard(
    title: String,
    icon: ImageVector?,
    count: Int,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(14.dp)) {
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
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun TagFilterCard(
    categoryId: String,
    title: String,
    tags: List<GarmentTag>,
    filters: BrowseFilterState,
    onFiltersChange: (BrowseFilterState) -> Unit,
) {
    FilterCard(
        title = title,
        icon = categoryIconFor(categoryId),
        count = filters.activeFilterCountIn(categoryId),
    ) {
        FilterTagChips(
            tags = tags,
            categoryId = categoryId,
            selectedTagIds = filters.selectedTagIds,
            selectedUnsetTagCategoryIds = filters.selectedUnsetTagCategoryIds,
            onTagToggled = { tag -> onFiltersChange(filters.toggleTag(tag)) },
            onUnsetToggled = { onFiltersChange(filters.toggleUnsetCategory(categoryId)) },
        )
    }
}

@Composable
private fun FavoritesSwitchRow(checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.filters_favourites_only),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.filters_favourites_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
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
    var expanded by rememberSaveable(categoryId) { mutableStateOf(false) }
    val visibleTags = if (expanded) sortedTags else sortedTags.take(VISIBLE_TAG_CHIPS)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visibleTags.forEach { tag ->
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
        if (!expanded && sortedTags.size > VISIBLE_TAG_CHIPS) {
            FilterChip(
                selected = false,
                onClick = { expanded = true },
                label = { Text(stringResource(R.string.filters_more, sortedTags.size - VISIBLE_TAG_CHIPS)) },
            )
        }
        val notSetSelected = categoryId in selectedUnsetTagCategoryIds
        FilterChip(
            selected = notSetSelected,
            onClick = onUnsetToggled,
            label = { Text(stringResource(R.string.filters_not_set)) },
            leadingIcon = if (notSetSelected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ColorPaletteGrid(
    colors: List<MainColor>,
    selectedColorIds: Set<String>,
    onColorToggled: (MainColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            colors.sortedAlphabetically(label = MainColor::name).chunked(COLOR_GRID_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { color ->
                        ColorSwatchCell(
                            color = color,
                            selected = color.id in selectedColorIds,
                            onClick = { onColorToggled(color) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(COLOR_GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchCell(
    color: MainColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.swatchColor())
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Text(
            text = color.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GarmentTag.localizedLabel(): String = localizedTagLabel()
