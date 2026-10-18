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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
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

@Composable
private fun GarmentTag.localizedLabel(): String = localizedTagLabel()
