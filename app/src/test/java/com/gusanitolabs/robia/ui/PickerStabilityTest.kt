package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerStabilityTest {
    @Test
    fun captionIsDecidedFromTheOptionCountOnTheFirstFrame() {
        // Before the first measurement the scroll range is still 0: a long list must already show its count.
        assertTrue(pickerCaptionVisible(optionCount = 22, maxValue = 0))
        assertFalse(pickerCaptionVisible(optionCount = 3, maxValue = 0))
    }

    @Test
    fun captionStaysOnceTheListScrolls() {
        assertTrue(pickerCaptionVisible(optionCount = 3, maxValue = 120))
        assertTrue(pickerCaptionVisible(optionCount = 22, maxValue = 900))
    }

    @Test
    fun captionHoldsAtEveryScrollPositionWhileOnlyTheFadeFollowsTheScroll() {
        val max = 900
        val positions = (0..max step 30).toList() + max

        val captions = positions.map { pickerCaptionVisible(optionCount = 22, maxValue = max) }
        assertEquals(setOf(true), captions.toSet())
        assertTrue(scrollHintVisible(value = 0, max = max))
        assertFalse(scrollHintVisible(value = max, max = max))
    }

    @Test
    fun suggestedHintRowIsReservedWhileSelectionChanges() {
        val seasonIds = listOf("s-summer", "s-winter")
        val suggested = setOf("s-summer")

        assertTrue(sheetReservesSuggestedHint(suggested, seasonIds))
        assertFalse(sheetShowsSuggestedHint(emptyList(), suggested, seasonIds))
        assertTrue(sheetShowsSuggestedHint(listOf("s-summer"), suggested, seasonIds))
        assertTrue(sheetReservesSuggestedHint(suggested, seasonIds))
    }

    @Test
    fun noSuggestedHintRowWhenNoSuggestionBelongsToTheCategory() {
        val seasonIds = listOf("s-summer", "s-winter")

        assertFalse(sheetReservesSuggestedHint(setOf("cat-shirt"), seasonIds))
        assertFalse(sheetReservesSuggestedHint(emptySet(), seasonIds))
    }
}
