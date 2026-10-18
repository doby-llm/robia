package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentTag
import org.junit.Assert.assertEquals
import org.junit.Test

class TagChipSlotsTest {
    private fun tags(count: Int): List<GarmentTag> = (0 until count).map { index ->
        GarmentTag(id = "season-$index", categoryId = "season", name = "Tag%02d".format(index), sortOrder = index)
    }

    @Test
    fun emptySectionShowsOnlyNotSet() {
        assertEquals(listOf(TagChipSlot.NotSet), tagChipSlots(tags = emptyList(), expanded = false, label = GarmentTag::name))
    }

    @Test
    fun exactlyEightTagsShowNoMoreChip() {
        val slots = tagChipSlots(tags = tags(8), expanded = false, label = GarmentTag::name)

        assertEquals(tags(8).map { TagChipSlot.Tag(it) } + TagChipSlot.NotSet, slots)
    }

    @Test
    fun nineTagsCollapsedShowEightThenMoreThenNotSet() {
        val slots = tagChipSlots(tags = tags(9), expanded = false, label = GarmentTag::name)

        assertEquals(tags(9).take(8).map { TagChipSlot.Tag(it) } + TagChipSlot.More(1) + TagChipSlot.NotSet, slots)
    }

    @Test
    fun nineTagsExpandedShowAllThenNotSetWithoutMore() {
        val slots = tagChipSlots(tags = tags(9), expanded = true, label = GarmentTag::name)

        assertEquals(tags(9).map { TagChipSlot.Tag(it) } + TagChipSlot.NotSet, slots)
    }

    @Test
    fun eightTagsExpandedShowNoMoreChip() {
        val slots = tagChipSlots(tags = tags(8), expanded = true, label = GarmentTag::name)

        assertEquals(tags(8).map { TagChipSlot.Tag(it) } + TagChipSlot.NotSet, slots)
    }

    @Test
    fun visibleTagsAreTheFirstEightInLabelOrderNotListOrder() {
        val reversed = tags(9).reversed()

        val slots = tagChipSlots(tags = reversed, expanded = false, label = GarmentTag::name)

        assertEquals(tags(9).take(8).map { TagChipSlot.Tag(it) } + TagChipSlot.More(1) + TagChipSlot.NotSet, slots)
    }
}
