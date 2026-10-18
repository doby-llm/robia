package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PruneUnknownFiltersTest {
    private val summer = GarmentTag(id = "season-summer", categoryId = "season", name = "Summer", sortOrder = 0)
    private val dresses = GarmentTag(id = "category-dresses", categoryId = "category", name = "Dresses", sortOrder = 0)
    private val red = MainColor(id = "color-red", name = "Red", hex = "#C0392B", sortOrder = 0)
    private val black = MainColor(id = "color-black", name = "Black", hex = "#000000", sortOrder = 1)

    @Test
    fun unknownTagIdIsPrunedWithItsCategoryEntry() {
        val filters = BrowseFilterState().toggleTag(summer).toggleTag(dresses)

        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = setOf(dresses.id),
            paletteColorIds = setOf(red.id),
        )

        assertEquals(setOf(dresses.id), pruned.selectedTagIds)
        assertEquals(mapOf(dresses.id to dresses.categoryId), pruned.selectedTagCategories)
    }

    @Test
    fun knownIdsAreUntouchedAndTheSameInstanceIsReturned() {
        val filters = BrowseFilterState()
            .toggleTag(summer)
            .toggleTag(dresses)
            .togglePaletteColor(red.id)

        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = setOf(summer.id, dresses.id),
            paletteColorIds = setOf(red.id, black.id),
        )

        assertSame(filters, pruned)
    }

    @Test
    fun unknownColourIdIsPrunedAndKnownOneKept() {
        val filters = BrowseFilterState().togglePaletteColor(red.id).togglePaletteColor(black.id)

        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = emptySet(),
            paletteColorIds = setOf(red.id),
        )

        assertEquals(setOf(red.id), pruned.selectedPaletteColorIds)
    }

    @Test
    fun emptyAvailableListsMeanNotLoadedSoNothingIsPruned() {
        val filters = BrowseFilterState()
            .toggleTag(summer)
            .togglePaletteColor(red.id)

        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = emptySet(),
            paletteColorIds = emptySet(),
        )

        assertSame(filters, pruned)
    }

    @Test
    fun emptyTagListDoesNotStopColourPruning() {
        val filters = BrowseFilterState()
            .toggleTag(summer)
            .togglePaletteColor(red.id)
            .togglePaletteColor(black.id)

        val pruned = pruneUnknownFilters(
            filters = filters,
            availableTagIds = emptySet(),
            paletteColorIds = setOf(black.id),
        )

        assertEquals(setOf(summer.id), pruned.selectedTagIds)
        assertEquals(setOf(black.id), pruned.selectedPaletteColorIds)
    }
}
