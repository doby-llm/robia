package com.gusanitolabs.robia.core.model

import java.text.Collator

/** Groups of the Analytics screen, in display order: the system groups, then user-created tag categories. */
enum class WardrobeStatsSection { Categories, Colours, Occasions, Seasons, Fit, Locations, Custom }

/** What one bar stands for. Browse is filtered by exactly this entry when the bar is tapped. */
sealed interface WardrobeStatsKey {
    data class Tag(val tagId: String, val categoryId: String) : WardrobeStatsKey
    /** Garments with no tag of [categoryId]. */
    data class NotSet(val categoryId: String) : WardrobeStatsKey
    data class Colour(val colorId: String) : WardrobeStatsKey
    /** Garments with no primary palette colour. */
    data object NoColour : WardrobeStatsKey
    /** [doesNotFit] false is "Fits", which includes garments without a fit value. */
    data class Fit(val doesNotFit: Boolean) : WardrobeStatsKey
}

/** [label] is the tag label or colour name; empty for fit and "Not set" bars, which the screen words. */
data class WardrobeStatsBar(val key: WardrobeStatsKey, val label: String, val count: Int)

data class WardrobeStatsGroup(
    val section: WardrobeStatsSection,
    /** The user's category name, for [WardrobeStatsSection.Custom] only. */
    val customName: String? = null,
    val bars: List<WardrobeStatsBar>,
)

data class WardrobeStats(
    /** Active (non-archived) garments; the screen header shows this total. */
    val activeCount: Int,
    /** Groups without a bar are left out. */
    val groups: List<WardrobeStatsGroup>,
)

// Same values as the editor and the Browse fit filter.
private const val FIT_VALUE_DOES_NOT_FIT = 0

/**
 * Counts active garments per group. A garment with several tags of one category counts once per tag. Bars are
 * sorted by count, descending, then by [collator] on the label; "Not set" is always last. Archived garments are
 * not counted. Colours use the stored primary palette colour; a colour that is not in [paletteColors] counts as
 * "Not set".
 */
fun wardrobeStats(
    items: List<ClothingItem>,
    paletteColors: List<MainColor>,
    tagCategories: List<TagCategory>,
    labelOf: (GarmentTag) -> String,
    collator: Collator,
): WardrobeStats {
    val garments = items.filterNot(ClothingItem::isArchived)
    val paletteById = paletteColors.associateBy(MainColor::id)
    val systemGroups = listOf(
        tagGroup(WardrobeStatsSection.Categories, garments, "category", labelOf, collator),
        colourGroup(garments, paletteById, collator),
        tagGroup(WardrobeStatsSection.Occasions, garments, "occasion", labelOf, collator),
        tagGroup(WardrobeStatsSection.Seasons, garments, "season", labelOf, collator),
        fitGroup(garments),
        tagGroup(WardrobeStatsSection.Locations, garments, "location", labelOf, collator),
    )
    val customGroups = tagCategories
        .filterNot(TagCategory::isSystem)
        .sortedBy(TagCategory::sortOrder)
        .map { category ->
            tagGroup(WardrobeStatsSection.Custom, garments, category.id, labelOf, collator, category.name)
        }
    return WardrobeStats(
        activeCount = garments.size,
        groups = (systemGroups + customGroups).filter { group -> group.bars.isNotEmpty() },
    )
}

/** Bar length as a share of the largest bar in its group, 0..1. */
fun barShare(count: Int, groupMax: Int): Float = if (groupMax > 0) count.toFloat() / groupMax else 0f

/**
 * Width of one bar during the grow-on-open animation: [share] times [progress] (clamped to 0..1). Monotonic in
 * [progress] and equal to [share] at 1. With reduced motion the full [share] at once.
 */
fun barFillFraction(share: Float, progress: Float, reducedMotion: Boolean): Float =
    if (reducedMotion) share else share * progress.coerceIn(0f, 1f)

private fun tagGroup(
    section: WardrobeStatsSection,
    garments: List<ClothingItem>,
    categoryId: String,
    labelOf: (GarmentTag) -> String,
    collator: Collator,
    customName: String? = null,
): WardrobeStatsGroup {
    val taggedPerGarment = garments.map { garment ->
        garment.tags.filter { tag -> tag.categoryId == categoryId }.distinctBy(GarmentTag::id)
    }
    val tagsById = taggedPerGarment.flatten().associateBy(GarmentTag::id)
    val counts = taggedPerGarment.flatten().groupingBy(GarmentTag::id).eachCount()
    val bars = counts.map { (tagId, count) ->
        WardrobeStatsBar(WardrobeStatsKey.Tag(tagId, categoryId), labelOf(tagsById.getValue(tagId)), count)
    }
    val notSet = taggedPerGarment.count(List<GarmentTag>::isEmpty)
    return WardrobeStatsGroup(section, customName, ranked(bars, collator, notSetBar(categoryId, notSet)))
}

private fun colourGroup(
    garments: List<ClothingItem>,
    paletteById: Map<String, MainColor>,
    collator: Collator,
): WardrobeStatsGroup {
    val colourIds = garments.map { garment ->
        garment.colorMetrics.primaryPaletteColorId?.takeIf(paletteById::containsKey)
    }
    val counts = colourIds.filterNotNull().groupingBy { it }.eachCount()
    val bars = counts.map { (colorId, count) ->
        WardrobeStatsBar(WardrobeStatsKey.Colour(colorId), paletteById.getValue(colorId).name, count)
    }
    val notSet = colourIds.count { it == null }
    val notSetBar = notSet.takeIf { it > 0 }?.let { WardrobeStatsBar(WardrobeStatsKey.NoColour, "", it) }
    return WardrobeStatsGroup(WardrobeStatsSection.Colours, bars = ranked(bars, collator, notSetBar))
}

private fun fitGroup(garments: List<ClothingItem>): WardrobeStatsGroup {
    val doesNotFit = garments.count { garment -> garment.fitValue == FIT_VALUE_DOES_NOT_FIT }
    val fits = garments.size - doesNotFit
    // Fits first, so a tie keeps that order (the sort below is stable).
    val bars = listOf(
        WardrobeStatsBar(WardrobeStatsKey.Fit(doesNotFit = false), "", fits),
        WardrobeStatsBar(WardrobeStatsKey.Fit(doesNotFit = true), "", doesNotFit),
    ).filter { bar -> bar.count > 0 }
    return WardrobeStatsGroup(WardrobeStatsSection.Fit, bars = bars.sortedByDescending(WardrobeStatsBar::count))
}

private fun notSetBar(categoryId: String, count: Int): WardrobeStatsBar? =
    count.takeIf { it > 0 }?.let { WardrobeStatsBar(WardrobeStatsKey.NotSet(categoryId), "", it) }

private fun ranked(
    bars: List<WardrobeStatsBar>,
    collator: Collator,
    notSet: WardrobeStatsBar?,
): List<WardrobeStatsBar> = bars
    .sortedWith(
        compareByDescending<WardrobeStatsBar> { bar -> bar.count }
            .thenComparator { a, b -> collator.compare(a.label, b.label) },
    ) + listOfNotNull(notSet)
