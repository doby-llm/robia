package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.PhotoRestoreState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class BrowseFilterStateTest {
    private val defaultLocale = Locale.getDefault()

    private val summer = GarmentTag(id = "season-summer", categoryId = "season", name = "Summer", sortOrder = 0)
    private val winter = GarmentTag(id = "season-winter", categoryId = "season", name = "Winter", sortOrder = 1)
    private val spring = GarmentTag(id = "season-spring", categoryId = "season", name = "Spring", sortOrder = 2)
    private val dresses = GarmentTag(id = "category-dresses", categoryId = "category", name = "Dresses", sortOrder = 0)
    private val skirts = GarmentTag(id = "category-skirts", categoryId = "category", name = "Skirts", sortOrder = 1)
    private val red = MainColor(id = "color-red", name = "Red", hex = "#C0392B", sortOrder = 0)
    private val black = MainColor(id = "color-black", name = "Black", hex = "#000000", sortOrder = 1)
    private val colors = listOf(red, black)

    @Before
    fun useSpanishCollation() {
        Locale.setDefault(Locale("es", "ES"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun noSelectionMatchesEveryItem() {
        assertTrue(BrowseFilterState().matches(item(), colors))
        assertTrue(BrowseFilterState().matches(item(listOf(dresses, summer)), colors))
    }

    @Test
    fun twoSeasonsMatchAnItemWithEitherOfThem() {
        val filters = BrowseFilterState().selectTags(summer, winter)

        assertTrue(filters.matches(item(listOf(summer)), colors))
        assertFalse(filters.matches(item(listOf(spring)), colors))
    }

    @Test
    fun categoryAndSeasonMustBothMatch() {
        val filters = BrowseFilterState().selectTags(dresses, summer)

        assertFalse(filters.matches(item(listOf(dresses, winter)), colors))
        assertTrue(filters.matches(item(listOf(dresses, summer)), colors))
    }

    @Test
    fun twoCategoriesMatchAnItemWithEitherOfThem() {
        val filters = BrowseFilterState().selectTags(dresses, skirts)

        assertTrue(filters.matches(item(listOf(skirts)), colors))
    }

    @Test
    fun notSetMatchesAnItemWithoutThatCategory() {
        val filters = BrowseFilterState().toggleUnsetCategory("category")

        assertTrue(filters.matches(item(listOf(summer)), colors))
        assertFalse(filters.matches(item(listOf(dresses)), colors))
    }

    @Test
    fun notSetOrSelectedTagOfTheSameCategoryMatchesEither() {
        val filters = BrowseFilterState().toggleUnsetCategory("category").toggleTag(dresses)

        assertTrue(filters.matches(item(listOf(dresses)), colors))
        assertFalse(filters.matches(item(listOf(skirts)), colors))
        assertTrue(filters.matches(item(listOf(summer)), colors))
    }

    @Test
    fun coloursMatchAnyOfTheSelectedColours() {
        val filters = BrowseFilterState().togglePaletteColor(red.id).togglePaletteColor(black.id)

        assertTrue(filters.matches(item(primaryPaletteColorId = black.id), colors))
    }

    @Test
    fun favouritesAreAndedWithTheOtherFilters() {
        val filters = BrowseFilterState().toggleFavoritesOnly().toggleTag(dresses)

        assertFalse(filters.matches(item(listOf(dresses)), colors))
        assertTrue(filters.matches(item(listOf(dresses), favorite = true), colors))
    }

    @Test
    fun activeCountSumsEveryEntryAndSectionCountsOnlyItsOwn() {
        val filters = BrowseFilterState()
            .selectTags(summer, winter, dresses)
            .toggleUnsetCategory("location")
            .togglePaletteColor(red.id)
            .toggleFavoritesOnly()

        assertEquals(6, filters.activeFilterCount)
        assertEquals(2, filters.activeFilterCountIn("season"))
        assertEquals(1, filters.activeFilterCountIn("category"))
        assertEquals(1, filters.activeFilterCountIn("location"))
        assertEquals(0, filters.activeFilterCountIn("occasion"))
        assertTrue(filters.hasActiveFilters)
    }

    @Test
    fun emptySelectionHasNoActiveFilters() {
        assertFalse(BrowseFilterState().hasActiveFilters)
        assertEquals(0, BrowseFilterState().activeFilterCount)
    }

    @Test
    fun togglingAnEntryTwiceLeavesNoCategoryBehind() {
        val filters = BrowseFilterState().toggleTag(summer).toggleTag(summer)

        assertEquals(BrowseFilterState(), filters)
    }

    @Test
    fun removeTagDropsExactlyThatEntry() {
        val filters = BrowseFilterState().selectTags(summer, winter).removeTag(summer.id)

        assertEquals(setOf(winter.id), filters.selectedTagIds)
        assertEquals(mapOf(winter.id to winter.categoryId), filters.selectedTagCategories)
    }

    @Test
    fun chipRemovalDropsOnlyItsOwnNotSetEntry() {
        val filters = BrowseFilterState()
            .toggleUnsetCategory("category")
            .toggleUnsetCategory("season")
            .toggleFavoritesOnly()
            .togglePaletteColor(red.id)
            .toggleUnsetCategory("category")

        assertEquals(setOf("season"), filters.selectedUnsetTagCategoryIds)
        assertTrue(filters.favoritesOnly)
        assertEquals(setOf(red.id), filters.selectedPaletteColorIds)
    }

    @Test
    fun chipRemovalDropsOnlyItsOwnColourEntry() {
        val filters = BrowseFilterState()
            .togglePaletteColor(red.id)
            .togglePaletteColor(black.id)
            .togglePaletteColor(red.id)

        assertEquals(setOf(black.id), filters.selectedPaletteColorIds)
        assertEquals(1, filters.activeFilterCount)
    }

    @Test
    fun chipRemovalOfFavouritesKeepsEveryOtherEntry() {
        val filters = BrowseFilterState()
            .selectTags(dresses, summer)
            .togglePaletteColor(red.id)
            .toggleFavoritesOnly()
            .toggleFavoritesOnly()

        assertFalse(filters.favoritesOnly)
        assertEquals(setOf(dresses.id, summer.id), filters.selectedTagIds)
        assertEquals(setOf(red.id), filters.selectedPaletteColorIds)
    }

    @Test
    fun tagSelectionAndNotSetDoNotClearEachOther() {
        val withNotSet = BrowseFilterState().toggleUnsetCategory("category").toggleTag(dresses)

        assertEquals(setOf("category"), withNotSet.selectedUnsetTagCategoryIds)
        assertEquals(setOf(dresses.id), withNotSet.selectedTagIds)

        val notSetAfterTagRemoved = withNotSet.removeTag(dresses.id)

        assertEquals(setOf("category"), notSetAfterTagRemoved.selectedUnsetTagCategoryIds)
        assertTrue(notSetAfterTagRemoved.selectedTagIds.isEmpty())
    }

    @Test
    fun sortingUsesTheLocaleCollatorSoNtildeComesAfterN() {
        val labels = listOf("Ñu", "Nz", "Mesa", "Árbol")

        assertEquals(listOf("Árbol", "Mesa", "Nz", "Ñu"), labels.sortedAlphabetically(label = { label -> label }))
    }

    @Test
    fun fitsMatchesEveryGarmentExceptDoesNotFitIncludingUnsetFit() {
        val filters = BrowseFilterState().toggleFit(FitFilter.Fits)

        assertTrue(filters.matches(item(fitValue = null), colors))
        assertTrue(filters.matches(item(fitValue = 1), colors))
        assertTrue(filters.matches(item(fitValue = 2), colors))
        assertTrue(filters.matches(item(fitValue = 4), colors))
        assertFalse(filters.matches(item(fitValue = 0), colors))
    }

    @Test
    fun doesNotFitMatchesOnlyTheDoesNotFitValue() {
        val filters = BrowseFilterState().toggleFit(FitFilter.DoesNotFit)

        assertTrue(filters.matches(item(fitValue = 0), colors))
        assertFalse(filters.matches(item(fitValue = null), colors))
        assertFalse(filters.matches(item(fitValue = 2), colors))
    }

    @Test
    fun bothFitOptionsMatchEveryGarment() {
        val filters = BrowseFilterState().toggleFit(FitFilter.Fits).toggleFit(FitFilter.DoesNotFit)

        assertTrue(filters.matches(item(fitValue = null), colors))
        assertTrue(filters.matches(item(fitValue = 0), colors))
        assertTrue(filters.matches(item(fitValue = 3), colors))
    }

    @Test
    fun fitIsAndedWithOtherSections() {
        val filters = BrowseFilterState().selectTags(dresses).toggleFit(FitFilter.DoesNotFit)

        assertTrue(filters.matches(item(listOf(dresses), fitValue = 0), colors))
        assertFalse(filters.matches(item(listOf(dresses), fitValue = 2), colors))
        assertFalse(filters.matches(item(listOf(summer), fitValue = 0), colors))
    }

    @Test
    fun fitCountsEachOptionAndMakesTheFilterActive() {
        val oneOption = BrowseFilterState().toggleFit(FitFilter.Fits)
        val bothOptions = oneOption.toggleFit(FitFilter.DoesNotFit)

        assertTrue(oneOption.hasActiveFilters)
        assertEquals(1, oneOption.activeFilterCount)
        assertEquals(2, bothOptions.activeFilterCount)
    }

    @Test
    fun togglingFitTwiceLeavesNoFitBehind() {
        assertEquals(BrowseFilterState(), BrowseFilterState().toggleFit(FitFilter.Fits).toggleFit(FitFilter.Fits))
    }

    @Test
    fun fitChipRemovalDropsOnlyItsOwnOption() {
        val filters = BrowseFilterState()
            .toggleFit(FitFilter.Fits)
            .toggleFit(FitFilter.DoesNotFit)
            .toggleFavoritesOnly()
            .toggleFit(FitFilter.Fits)

        assertEquals(setOf(FitFilter.DoesNotFit), filters.fit)
        assertTrue(filters.favoritesOnly)
    }

    @Test
    fun resetStateHasNoFitSelectionAndNoActiveFilters() {
        assertTrue(BrowseFilterState().fit.isEmpty())
        assertFalse(BrowseFilterState().hasActiveFilters)
    }

    private fun BrowseFilterState.selectTags(vararg tags: GarmentTag): BrowseFilterState =
        tags.fold(this) { filters, tag -> filters.toggleTag(tag) }

    private fun item(
        tags: List<GarmentTag> = emptyList(),
        favorite: Boolean = false,
        primaryPaletteColorId: String? = null,
        fitValue: Int? = null,
    ) = UiWardrobeItem(
        id = "garment",
        name = "Garment",
        subtitle = "",
        notes = "",
        photoUri = null,
        tags = tags.map { tag -> UiTag(id = tag.id, categoryId = tag.categoryId, label = tag.name) },
        fitValue = fitValue,
        primaryColor = DisplayColorLabel.Unknown,
        primaryRawValue = null,
        primaryPaletteColorId = primaryPaletteColorId,
        primaryPaletteColorName = null,
        primaryPaletteColorHex = null,
        secondaryColor = DisplayColorLabel.Unknown,
        secondaryRawValue = null,
        secondaryPaletteColorId = null,
        secondaryPaletteColorName = null,
        secondaryPaletteColorHex = null,
        isFavorite = favorite,
        syncStatus = GarmentSyncStatus.LocalOnly,
        syncDirtyAtEpochMillis = null,
        lastSyncedAtEpochMillis = null,
        syncFailureMessage = null,
        photoRestoreState = PhotoRestoreState(),
    )
}
