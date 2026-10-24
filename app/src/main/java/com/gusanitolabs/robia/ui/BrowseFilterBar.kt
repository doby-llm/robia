package com.gusanitolabs.robia.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.suggestions.SearchMode
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Options of the Advanced filters "Fit" section. Both can be selected; together they match every garment. */
internal enum class FitFilter {
    Fits, DoesNotFit;

    /** "Fits" includes garments without a fit value, as the editor's fit switch does. */
    fun matches(fitValue: Int?): Boolean = when (this) {
        Fits -> fitValue != FIT_VALUE_DOES_NOT_FIT
        DoesNotFit -> fitValue == FIT_VALUE_DOES_NOT_FIT
    }
}

internal fun FitFilter.labelRes(): Int = when (this) {
    FitFilter.Fits -> R.string.fit_good
    FitFilter.DoesNotFit -> R.string.fit_does_not_fit
}

private const val FIT_VALUE_DOES_NOT_FIT = 0

internal data class BrowseFilterState(
    val selectedTagIds: Set<String> = emptySet(),
    /** tagId → categoryId for each entry of [selectedTagIds], kept by the toggle helpers. */
    val selectedTagCategories: Map<String, String> = emptyMap(),
    val selectedUnsetTagCategoryIds: Set<String> = emptySet(),
    val selectedPaletteColorIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val fit: Set<FitFilter> = emptySet(),
) {
    val hasActiveFilters: Boolean
        get() = selectedTagIds.isNotEmpty() ||
            selectedUnsetTagCategoryIds.isNotEmpty() ||
            selectedPaletteColorIds.isNotEmpty() ||
            favoritesOnly ||
            fit.isNotEmpty()

    val activeFilterCount: Int
        get() = selectedTagIds.size +
            selectedUnsetTagCategoryIds.size +
            selectedPaletteColorIds.size +
            fit.size +
            if (favoritesOnly) 1 else 0

    /** Entries in one category section: its selected tags plus its "Not set" option. */
    fun activeFilterCountIn(categoryId: String): Int =
        selectedTagIds.count { tagId -> selectedTagCategories[tagId] == categoryId } +
            if (categoryId in selectedUnsetTagCategoryIds) 1 else 0

    /**
     * Within a category the selections are OR (an item passes if it carries any of them, or has no tag of the
     * category when "Not set" is selected); across categories they are AND; colours are OR; favourites are AND.
     * A selected tag with no known category stands alone, so it still has to match. Fit options are OR within the
     * section, and the section is AND with the rest.
     */
    fun matches(item: UiWardrobeItem, paletteColors: List<MainColor>): Boolean {
        val assignedTags = item.tags.filter { tag -> tag.id.isNotBlank() && tag.label.isNotBlank() }
        val itemTagIds = assignedTags.map(UiTag::id).toSet()
        val itemTagCategoryIds = assignedTags.map(UiTag::categoryId).toSet()
        val selectedIdsByCategory = selectedTagIds.groupBy { tagId -> selectedTagCategories[tagId] ?: tagId }
        return (!favoritesOnly || item.isFavorite) &&
            (selectedIdsByCategory.keys + selectedUnsetTagCategoryIds).all { categoryId ->
                selectedIdsByCategory[categoryId].orEmpty().any(itemTagIds::contains) ||
                    (categoryId in selectedUnsetTagCategoryIds && categoryId !in itemTagCategoryIds)
            } &&
            (selectedPaletteColorIds.isEmpty() || item.matchesAnyPaletteColor(selectedPaletteColors(paletteColors))) &&
            (fit.isEmpty() || fit.any { option -> option.matches(item.fitValue) })
    }

    private fun selectedPaletteColors(paletteColors: List<MainColor>): List<MainColor> =
        paletteColors.filter { color -> color.id in selectedPaletteColorIds }
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

@Composable
internal fun FilterBar(
    hasActiveFilters: Boolean,
    activeFilterCount: Int,
    filters: BrowseFilterState,
    availableTags: List<GarmentTag>,
    paletteColors: List<MainColor>,
    onFiltersChange: (BrowseFilterState) -> Unit,
    cloudSync: CloudSyncIndicator,
    onFiltersClick: () -> Unit,
    onResetFilters: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchMode: SearchMode,
    searchInProgress: Boolean,
    resultCount: Int,
    similarToName: String?,
    onClearSimilar: () -> Unit,
    modelState: ModelState,
    onOpenSettings: () -> Unit,
    /** Null hides the Analytics icon. */
    onAnalyticsClick: (() -> Unit)? = null,
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

    // A tag or colour deleted elsewhere must not keep filtering (and showing its raw id) here.
    LaunchedEffect(availableTags, paletteColors) {
        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = availableTags.mapTo(mutableSetOf(), GarmentTag::id),
            paletteColorIds = paletteColors.mapTo(mutableSetOf(), MainColor::id),
        )
        if (pruned != filters) onFiltersChange(pruned)
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
                onAnalyticsClick?.let { onClick ->
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = stringResource(R.string.analytics_open),
                        )
                    }
                }
                if (hasActiveFilters) {
                    TextButton(onClick = onResetFilters) {
                        Text(stringResource(R.string.reset_filters_short))
                    }
                }
                if (!searchVisible) {
                    IconButton(
                        onClick = { showSmartPrompt = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    ) {
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
            if (filters.hasActiveFilters) {
                ActiveFilterChipsRow(
                    filters = filters,
                    availableTags = availableTags,
                    paletteColors = paletteColors,
                    onFiltersChange = onFiltersChange,
                )
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

/**
 * One chip per active filter, in the order: tags, "Not set" options, colours, fit, favourites. Each ✕ removes only
 * its entry.
 */
@Composable
internal fun ActiveFilterChipsRow(
    filters: BrowseFilterState,
    availableTags: List<GarmentTag>,
    paletteColors: List<MainColor>,
    onFiltersChange: (BrowseFilterState) -> Unit,
) {
    if (!filters.hasActiveFilters) return
    val context = LocalContext.current
    val tagsById = availableTags.associateBy(GarmentTag::id)
    val colorsById = paletteColors.associateBy(MainColor::id)
    val tagIds = filters.selectedTagIds.toList().sortedAlphabetically(
        label = { tagId -> tagsById[tagId]?.localizedTagLabel(context) ?: tagId },
    )
    val colorIds = filters.selectedPaletteColorIds.toList().sortedAlphabetically(
        label = { colorId -> colorsById[colorId]?.name ?: colorId },
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tagIds) { tagId ->
            ActiveFilterChip(
                label = tagsById[tagId]?.localizedTagLabel(context) ?: tagId,
                onRemove = { onFiltersChange(filters.removeTag(tagId)) },
            )
        }
        items(filters.selectedUnsetTagCategoryIds.toList()) { categoryId ->
            val categoryTitle = categoryTitleRes(categoryId)?.let { stringResource(it) } ?: categoryId
            ActiveFilterChip(
                label = stringResource(R.string.filters_category_not_set, categoryTitle, stringResource(R.string.filters_not_set)),
                onRemove = { onFiltersChange(filters.toggleUnsetCategory(categoryId)) },
            )
        }
        items(colorIds) { colorId ->
            ActiveFilterChip(
                label = colorsById[colorId]?.name ?: colorId,
                swatch = colorsById[colorId]?.swatchColor(),
                onRemove = { onFiltersChange(filters.togglePaletteColor(colorId)) },
            )
        }
        items(FitFilter.entries.filter { fit -> fit in filters.fit }) { fit ->
            ActiveFilterChip(
                label = stringResource(fit.labelRes()),
                onRemove = { onFiltersChange(filters.toggleFit(fit)) },
            )
        }
        if (filters.favoritesOnly) {
            item {
                ActiveFilterChip(
                    label = stringResource(R.string.filters_favourites_only),
                    onRemove = { onFiltersChange(filters.toggleFavoritesOnly()) },
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit, swatch: Color? = null) {
    val removeDescription = stringResource(R.string.filter_remove)

    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        leadingIcon = if (swatch != null) {
            { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(swatch)) }
        } else {
            null
        },
        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = removeDescription) },
    )
}

private fun categoryTitleRes(categoryId: String): Int? = when (categoryId) {
    "category" -> R.string.filter_category
    "season" -> R.string.filter_season
    "occasion" -> R.string.filter_occasion
    "location" -> R.string.filter_location
    else -> null
}

internal fun BrowseFilterState.toggleTag(tag: GarmentTag): BrowseFilterState {
    val selected = tag.id in selectedTagIds
    return copy(
        selectedTagIds = selectedTagIds.toggle(tag.id),
        selectedTagCategories = if (selected) {
            selectedTagCategories - tag.id
        } else {
            selectedTagCategories + (tag.id to tag.categoryId)
        },
    )
}

/** Removes exactly one tag entry, as the active-filter chip's ✕ does. */
internal fun BrowseFilterState.removeTag(tagId: String): BrowseFilterState = copy(
    selectedTagIds = selectedTagIds - tagId,
    selectedTagCategories = selectedTagCategories - tagId,
)

/**
 * Drops selected tags (with their category entry) and colours that no longer exist. An empty id set means the list
 * has not loaded yet, not that everything was deleted, so that kind of selection is left alone.
 * Returns [filters] itself when nothing has to go.
 */
internal fun pruneUnknownFilters(
    filters: BrowseFilterState,
    availableTagIds: Set<String>,
    paletteColorIds: Set<String>,
): BrowseFilterState {
    val unknownTagIds = if (availableTagIds.isEmpty()) {
        emptySet()
    } else {
        filters.selectedTagIds.filterNot(availableTagIds::contains).toSet()
    }
    val unknownColorIds = if (paletteColorIds.isEmpty()) {
        emptySet()
    } else {
        filters.selectedPaletteColorIds.filterNot(paletteColorIds::contains).toSet()
    }
    if (unknownTagIds.isEmpty() && unknownColorIds.isEmpty()) return filters
    return filters.copy(
        selectedTagIds = filters.selectedTagIds - unknownTagIds,
        selectedTagCategories = filters.selectedTagCategories - unknownTagIds,
        selectedPaletteColorIds = filters.selectedPaletteColorIds - unknownColorIds,
    )
}

internal fun BrowseFilterState.toggleUnsetCategory(categoryId: String): BrowseFilterState = copy(
    selectedUnsetTagCategoryIds = selectedUnsetTagCategoryIds.toggle(categoryId),
)

internal fun BrowseFilterState.togglePaletteColor(colorId: String): BrowseFilterState = copy(
    selectedPaletteColorIds = selectedPaletteColorIds.toggle(colorId),
)

internal fun BrowseFilterState.toggleFavoritesOnly(): BrowseFilterState = copy(
    favoritesOnly = !favoritesOnly,
)

internal fun BrowseFilterState.toggleFit(fit: FitFilter): BrowseFilterState = copy(
    fit = this.fit.toggle(fit),
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
