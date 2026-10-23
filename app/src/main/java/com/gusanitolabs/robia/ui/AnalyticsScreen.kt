package com.gusanitolabs.robia.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.core.model.WardrobeStatsBar
import com.gusanitolabs.robia.core.model.WardrobeStatsGroup
import com.gusanitolabs.robia.core.model.WardrobeStatsKey
import com.gusanitolabs.robia.core.model.WardrobeStatsSection
import com.gusanitolabs.robia.core.model.barFillFraction
import com.gusanitolabs.robia.core.model.barShare
import com.gusanitolabs.robia.core.model.wardrobeStats
import java.text.Collator
import java.util.Locale

/** Bars grow from the axis once, when the screen is entered (R3-DEC-019). */
private const val BAR_GROW_MILLIS = 350
private const val LABEL_COLUMN_WIDTH_DP = 112
private const val COUNT_COLUMN_WIDTH_DP = 32

/** The Browse filter a bar stands for, or null when Browse has no such filter (a colour "Not set" bar). */
internal fun WardrobeStatsKey.toBrowseFilterState(): BrowseFilterState? = when (this) {
    is WardrobeStatsKey.Tag -> BrowseFilterState(
        selectedTagIds = setOf(tagId),
        selectedTagCategories = mapOf(tagId to categoryId),
    )
    is WardrobeStatsKey.NotSet -> BrowseFilterState(selectedUnsetTagCategoryIds = setOf(categoryId))
    is WardrobeStatsKey.Colour -> BrowseFilterState(selectedPaletteColorIds = setOf(colorId))
    is WardrobeStatsKey.Fit -> BrowseFilterState(
        fit = setOf(if (doesNotFit) FitFilter.DoesNotFit else FitFilter.Fits),
    )
    WardrobeStatsKey.NoColour -> null
}

@Composable
internal fun AnalyticsScreen(
    innerPadding: PaddingValues,
    garments: List<ClothingItem>,
    paletteColors: List<MainColor>,
    tagCategories: List<TagCategory>,
    onBarClick: (BrowseFilterState) -> Unit,
) {
    val context = LocalContext.current
    val collator = remember { Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY } }
    val stats = remember(garments, paletteColors, tagCategories) {
        wardrobeStats(
            items = garments,
            paletteColors = paletteColors,
            tagCategories = tagCategories,
            labelOf = { tag -> tag.localizedTagLabel(context) },
            collator = collator,
        )
    }
    // Set once per visit: the bars grow on entry only. A data change later (or a recomposition) moves them without animating.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = BAR_GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "analytics-bar-grow",
    )
    val reducedMotion = LocalReducedMotion.current
    val colorsById = remember(paletteColors) { paletteColors.associateBy(MainColor::id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = pluralStringResource(R.plurals.analytics_garment_count, stats.activeCount, stats.activeCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (stats.activeCount == 0) {
            item { AnalyticsEmptyState() }
        }
        items(stats.groups, key = { group -> group.section.name + group.customName.orEmpty() }) { group ->
            StatsGroupCard(
                group = group,
                progress = progress,
                reducedMotion = reducedMotion,
                colorsById = colorsById,
                onBarClick = onBarClick,
            )
        }
    }
}

@Composable
private fun AnalyticsEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.analytics_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.analytics_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsGroupCard(
    group: WardrobeStatsGroup,
    progress: Float,
    reducedMotion: Boolean,
    colorsById: Map<String, MainColor>,
    onBarClick: (BrowseFilterState) -> Unit,
) {
    val groupMax = group.bars.maxOf(WardrobeStatsBar::count)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = sectionTitle(group),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            group.bars.forEach { bar ->
                val filter = bar.key.toBrowseFilterState()
                StatsBarRow(
                    label = barLabel(bar),
                    count = bar.count,
                    fraction = barFillFraction(barShare(bar.count, groupMax), progress, reducedMotion),
                    swatch = (bar.key as? WardrobeStatsKey.Colour)?.let { key -> colorsById[key.colorId]?.swatchColor() },
                    onClick = filter?.let { f -> { onBarClick(f) } },
                )
            }
        }
    }
}

@Composable
private fun StatsBarRow(
    label: String,
    count: Int,
    fraction: Float,
    swatch: Color?,
    onClick: (() -> Unit)?,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.width(LABEL_COLUMN_WIDTH_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatch?.let { color ->
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(fill),
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(COUNT_COLUMN_WIDTH_DP.dp),
        )
    }
}

@Composable
private fun sectionTitle(group: WardrobeStatsGroup): String = when (group.section) {
    WardrobeStatsSection.Categories -> stringResource(R.string.analytics_section_categories)
    WardrobeStatsSection.Colours -> stringResource(R.string.analytics_section_colours)
    WardrobeStatsSection.Occasions -> stringResource(R.string.analytics_section_occasions)
    WardrobeStatsSection.Seasons -> stringResource(R.string.analytics_section_seasons)
    WardrobeStatsSection.Fit -> stringResource(R.string.analytics_section_fit)
    WardrobeStatsSection.Locations -> stringResource(R.string.analytics_section_locations)
    WardrobeStatsSection.Custom -> group.customName.orEmpty()
}

@Composable
private fun barLabel(bar: WardrobeStatsBar): String = when (val key = bar.key) {
    is WardrobeStatsKey.Fit -> stringResource(if (key.doesNotFit) R.string.fit_does_not_fit else R.string.fit_good)
    is WardrobeStatsKey.NotSet, WardrobeStatsKey.NoColour -> stringResource(R.string.not_set)
    is WardrobeStatsKey.Tag, is WardrobeStatsKey.Colour -> bar.label
}
