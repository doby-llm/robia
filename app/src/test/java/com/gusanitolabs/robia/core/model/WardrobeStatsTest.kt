package com.gusanitolabs.robia.core.model

import com.gusanitolabs.robia.ui.BrowseFilterState
import com.gusanitolabs.robia.ui.FitFilter
import com.gusanitolabs.robia.ui.toBrowseFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class WardrobeStatsTest {
    private val dress = GarmentTag(id = "category-dress", categoryId = "category", name = "Dress", sortOrder = 0)
    private val top = GarmentTag(id = "category-top", categoryId = "category", name = "Top", sortOrder = 1)
    private val everyday = GarmentTag(id = "occasion-everyday", categoryId = "occasion", name = "Everyday", sortOrder = 0)
    private val formal = GarmentTag(id = "occasion-formal", categoryId = "occasion", name = "Formal", sortOrder = 1)
    private val summer = GarmentTag(id = "season-summer", categoryId = "season", name = "Summer", sortOrder = 0)
    private val closet = GarmentTag(id = "location-closet", categoryId = "location", name = "Main closet", sortOrder = 0)
    private val travel = GarmentTag(id = "custom-travel", categoryId = "custom-1", name = "Travel", sortOrder = 0)
    private val red = MainColor(id = "color-red", name = "Red", hex = "#C0392B", sortOrder = 0)
    private val black = MainColor(id = "color-black", name = "Black", hex = "#000000", sortOrder = 1)
    private val customCategory = TagCategory(id = "custom-1", name = "Trips", sortOrder = 9)
    private val collator = Collator.getInstance(Locale.ENGLISH).apply { strength = Collator.PRIMARY }

    private fun stats(items: List<ClothingItem>, categories: List<TagCategory> = emptyList()) = wardrobeStats(
        items = items,
        paletteColors = listOf(red, black),
        tagCategories = categories,
        labelOf = GarmentTag::name,
        collator = collator,
    )

    private fun group(stats: WardrobeStats, section: WardrobeStatsSection) = stats.groups.single { it.section == section }

    private fun garment(
        id: String,
        tags: List<GarmentTag> = emptyList(),
        fitValue: Int? = 2,
        primaryColorId: String? = null,
        isArchived: Boolean = false,
    ) = ClothingItem(
        id = id,
        name = id,
        tags = tags,
        fitValue = fitValue,
        colorMetrics = ClothingColorMetrics(primaryPaletteColorId = primaryColorId),
        isArchived = isArchived,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    @Test
    fun countsTagsPerGarmentAndCountsMultiTagGarmentOncePerTag() {
        val result = stats(
            listOf(
                garment("a", tags = listOf(dress, everyday, formal)),
                garment("b", tags = listOf(dress, everyday)),
                garment("c", tags = listOf(top)),
            ),
        )

        assertEquals(
            listOf(
                WardrobeStatsBar(WardrobeStatsKey.Tag("category-dress", "category"), "Dress", 2),
                WardrobeStatsBar(WardrobeStatsKey.Tag("category-top", "category"), "Top", 1),
            ),
            group(result, WardrobeStatsSection.Categories).bars,
        )
        assertEquals(
            listOf(
                WardrobeStatsBar(WardrobeStatsKey.Tag("occasion-everyday", "occasion"), "Everyday", 2),
                WardrobeStatsBar(WardrobeStatsKey.Tag("occasion-formal", "occasion"), "Formal", 1),
            ),
            group(result, WardrobeStatsSection.Occasions).bars,
        )
    }

    @Test
    fun notSetIsLastEvenWhenItIsTheLargestBar() {
        val result = stats(
            listOf(
                garment("a", tags = listOf(dress)),
                garment("b"),
                garment("c"),
                garment("d"),
            ),
        )

        val categories = group(result, WardrobeStatsSection.Categories).bars
        assertEquals(WardrobeStatsKey.Tag("category-dress", "category"), categories.first().key)
        assertEquals(WardrobeStatsKey.NotSet("category"), categories.last().key)
        assertEquals(3, categories.last().count)
    }

    @Test
    fun tiedBarsSortAlphabeticallyWithCollatorNotCodePoint() {
        val apple = GarmentTag(id = "apple", categoryId = "location", name = "apple", sortOrder = 0)
        val banana = GarmentTag(id = "banana", categoryId = "location", name = "Banana", sortOrder = 1)
        val cherry = GarmentTag(id = "cherry", categoryId = "location", name = "cherry", sortOrder = 2)
        val result = stats(
            listOf(
                garment("a", tags = listOf(cherry)),
                garment("b", tags = listOf(banana)),
                garment("c", tags = listOf(apple)),
            ),
        )

        assertEquals(
            listOf("apple", "Banana", "cherry"),
            group(result, WardrobeStatsSection.Locations).bars.map(WardrobeStatsBar::label),
        )
    }

    @Test
    fun coloursUseStoredPrimaryPaletteColourAndUnknownIdsAreNotSet() {
        val result = stats(
            listOf(
                garment("a", primaryColorId = red.id),
                garment("b", primaryColorId = red.id),
                garment("c", primaryColorId = black.id),
                garment("d"),
                garment("e", primaryColorId = "color-deleted"),
            ),
        )

        assertEquals(
            listOf(
                WardrobeStatsBar(WardrobeStatsKey.Colour(red.id), "Red", 2),
                WardrobeStatsBar(WardrobeStatsKey.Colour(black.id), "Black", 1),
                WardrobeStatsBar(WardrobeStatsKey.NoColour, "", 2),
            ),
            group(result, WardrobeStatsSection.Colours).bars,
        )
    }

    @Test
    fun fitFitsIncludesGarmentsWithoutFitValue() {
        val result = stats(
            listOf(
                garment("a", fitValue = 2),
                garment("b", fitValue = null),
                garment("c", fitValue = 0),
            ),
        )

        assertEquals(
            listOf(
                WardrobeStatsBar(WardrobeStatsKey.Fit(doesNotFit = false), "", 2),
                WardrobeStatsBar(WardrobeStatsKey.Fit(doesNotFit = true), "", 1),
            ),
            group(result, WardrobeStatsSection.Fit).bars,
        )
    }

    @Test
    fun archivedGarmentsAreNotCounted() {
        val result = stats(
            listOf(
                garment("a", tags = listOf(dress), fitValue = 0, primaryColorId = red.id),
                garment("b", tags = listOf(dress), isArchived = true, primaryColorId = red.id),
                garment("c", isArchived = true, fitValue = 0),
            ),
        )

        assertEquals(1, result.activeCount)
        assertEquals(1, group(result, WardrobeStatsSection.Categories).bars.first().count)
        assertEquals(1, group(result, WardrobeStatsSection.Fit).bars.single().count)
        assertEquals(1, group(result, WardrobeStatsSection.Colours).bars.single().count)
    }

    @Test
    fun customCategoriesFollowSystemGroupsAndEmptyGroupsAreLeftOut() {
        val result = stats(
            listOf(garment("a", tags = listOf(travel, summer, closet))),
            categories = listOf(customCategory),
        )

        assertEquals(
            listOf(
                WardrobeStatsSection.Colours,
                WardrobeStatsSection.Seasons,
                WardrobeStatsSection.Fit,
                WardrobeStatsSection.Locations,
                WardrobeStatsSection.Custom,
            ),
            result.groups.map(WardrobeStatsGroup::section),
        )
        assertEquals("Trips", result.groups.last().customName)
        assertEquals(
            listOf(WardrobeStatsKey.Tag("custom-travel", "custom-1")),
            result.groups.last().bars.map(WardrobeStatsBar::key),
        )
        assertTrue(stats(emptyList()).groups.isEmpty())
    }

    @Test
    fun barShareIsRelativeToTheLargestBarOfItsGroup() {
        assertEquals(1f, barShare(count = 14, groupMax = 14), 0f)
        assertEquals(0.5f, barShare(count = 7, groupMax = 14), 0f)
        assertEquals(0f, barShare(count = 0, groupMax = 0), 0f)
    }

    @Test
    fun barFillGrowsMonotonicallyFromZeroToTheShare() {
        val share = 0.64f
        val steps = (0..20).map { step -> barFillFraction(share, step / 20f, reducedMotion = false) }

        assertEquals(0f, steps.first(), 0f)
        assertEquals(share, steps.last(), 0f)
        assertTrue(steps.zipWithNext().all { (before, after) -> before <= after })
    }

    @Test
    fun barFillIsClampedOutsideZeroToOne() {
        assertEquals(0f, barFillFraction(0.5f, progress = -0.3f, reducedMotion = false), 0f)
        assertEquals(0.5f, barFillFraction(0.5f, progress = 1.7f, reducedMotion = false), 0f)
    }

    @Test
    fun barFillJumpsToTheFinalWidthWithReducedMotion() {
        assertEquals(0.5f, barFillFraction(0.5f, progress = 0f, reducedMotion = true), 0f)
        assertEquals(0.5f, barFillFraction(0.5f, progress = 0.4f, reducedMotion = true), 0f)
    }

    @Test
    fun tapMapsEachBarToExactlyItsBrowseFilter() {
        assertEquals(
            BrowseFilterState(
                selectedTagIds = setOf("category-dress"),
                selectedTagCategories = mapOf("category-dress" to "category"),
            ),
            WardrobeStatsKey.Tag("category-dress", "category").toBrowseFilterState(),
        )
        assertEquals(
            BrowseFilterState(selectedUnsetTagCategoryIds = setOf("season")),
            WardrobeStatsKey.NotSet("season").toBrowseFilterState(),
        )
        assertEquals(
            BrowseFilterState(selectedPaletteColorIds = setOf("color-red")),
            WardrobeStatsKey.Colour("color-red").toBrowseFilterState(),
        )
        assertEquals(
            BrowseFilterState(fit = setOf(FitFilter.DoesNotFit)),
            WardrobeStatsKey.Fit(doesNotFit = true).toBrowseFilterState(),
        )
        // Browse has no "no colour" filter, so that bar is not tappable.
        assertNull(WardrobeStatsKey.NoColour.toBrowseFilterState())
    }
}
