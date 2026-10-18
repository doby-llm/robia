package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentTag
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentFieldRowsTest {
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
    private val none = "None"

    private fun occasion(id: String, name: String) =
        GarmentTag(id = id, categoryId = "occasion", name = name, sortOrder = 0)

    @Test
    fun valueTextJoinsSelectedLabelsAlphabetically() {
        val selected = listOf(occasion("o-work", "Work"), occasion("o-beach", "Beach"), occasion("o-everyday", "Everyday"))

        assertEquals("Beach, Everyday, Work", fieldValueText(selected, collator, none) { tag -> tag.name })
    }

    @Test
    fun valueTextUsesCollatorSoAccentedLabelsSortWithTheirBaseLetter() {
        assertEquals("Ñu, Ob", fieldValueText(listOf("Ob", "Ñu"), collator, none) { label -> label })
    }

    @Test
    fun emptySelectionShowsNone() {
        assertEquals(none, fieldValueText(emptyList<GarmentTag>(), collator, none) { tag -> tag.name })
    }

    @Test
    fun aiSuggestedNeedsSelectedValuesAndAllOfThemModelOwned() {
        assertTrue(isAiSuggested(listOf("a"), listOf("a", "b")))
        assertTrue(isAiSuggested(listOf("a", "b"), listOf("b", "a")))
        assertFalse(isAiSuggested(listOf("a", "c"), listOf("a", "b")))
        assertFalse(isAiSuggested(emptyList(), listOf("a")))
        assertFalse(isAiSuggested(listOf("a"), emptyList()))
    }

    @Test
    fun sheetHintOnlyForSuggestedTagsOfTheSheetsOwnCategory() {
        val seasonIds = listOf("s-summer", "s-winter")

        // Category was suggested; the Season sheet has nothing suggested in its own category.
        assertFalse(sheetShowsSuggestedHint(listOf("cat-shirt", "s-summer"), setOf("cat-shirt"), seasonIds))
        assertTrue(sheetShowsSuggestedHint(listOf("s-summer"), setOf("s-summer"), seasonIds))
        assertFalse(sheetShowsSuggestedHint(listOf("s-summer"), emptySet(), seasonIds))
    }

    @Test
    fun colorAiMarkerWhileEitherColourIsModelSuggested() {
        assertTrue(isColorAiSuggested(primaryGeneration = 3L, secondaryGeneration = null))
        assertTrue(isColorAiSuggested(primaryGeneration = null, secondaryGeneration = 3L))
        assertTrue(isColorAiSuggested(primaryGeneration = 3L, secondaryGeneration = 3L))
        assertFalse(isColorAiSuggested(primaryGeneration = null, secondaryGeneration = null))
    }

    @Test
    fun colorValueShowsRolesOnlyWhenTheySuggestOneIsMissing() {
        assertEquals("Navy · Ivory", colorFieldValueText(primary = "Navy", secondary = "Ivory", none = none))
        assertEquals("Navy", colorFieldValueText(primary = "Navy", secondary = null, none = none))
        assertEquals("None · Ivory", colorFieldValueText(primary = null, secondary = "Ivory", none = none))
        assertEquals(none, colorFieldValueText(primary = null, secondary = null, none = none))
    }

    @Test
    fun multiSelectToggleAddsAndRemovesOnlyThatTag() {
        val tags = listOf("o-work", "o-beach")
        val beach = occasion("o-beach", "Beach")

        assertEquals(listOf("o-work", "o-beach"), listOf("o-work").toggleTag(beach, selected = false, singleSelect = false, categoryTagIds = tags))
        assertEquals(listOf("o-work"), listOf("o-work", "o-beach").toggleTag(beach, selected = true, singleSelect = false, categoryTagIds = tags))
    }

    @Test
    fun singleSelectReplacesOnlyTagsOfTheSameCategory() {
        val attic = GarmentTag(id = "loc-attic", categoryId = "location", name = "Attic", sortOrder = 0)
        val locationIds = listOf("loc-wardrobe", "loc-attic")

        assertEquals(
            listOf("o-work", "loc-attic"),
            listOf("loc-wardrobe", "o-work").toggleTag(attic, selected = false, singleSelect = true, categoryTagIds = locationIds),
        )
    }
}
