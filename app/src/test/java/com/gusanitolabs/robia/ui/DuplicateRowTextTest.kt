package com.gusanitolabs.robia.ui

import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateRowTextTest {
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }
    private val dresses = UiTag("category-dresses", "category", "Dresses")
    private val summer = UiTag("season-summer", "season", "Summer")
    private val winter = UiTag("season-winter", "season", "Winter")
    private val work = UiTag("occasion-work", "occasion", "Work")
    private val beach = UiTag("occasion-beach", "occasion", "Beach")
    private val colour = UiTag("color-red", "color", "Red")

    @Test
    fun nameWinsOverCategoryColourAndMeta() {
        assertEquals(
            "Mine",
            duplicateRowLine("  Mine  ", listOf(dresses, summer, work), "Black", collator),
        )
    }

    @Test
    fun whitespaceNameCountsAsBlank() {
        assertEquals("Dresses · Black", duplicateRowLine("   ", listOf(dresses), "Black", collator))
        assertNull(duplicateRowLine("   ", emptyList(), null, collator))
    }

    @Test
    fun categoryAndColourJoinWithSeparator() {
        assertEquals("Dresses · Black", duplicateRowLine("", listOf(dresses), "Black", collator))
    }

    @Test
    fun categoryAloneShowsAlone() {
        assertEquals("Dresses", duplicateRowLine("", listOf(dresses, summer), null, collator))
        assertEquals("Dresses", duplicateRowLine("", listOf(dresses), "  ", collator))
    }

    @Test
    fun colourAloneShowsAlone() {
        assertEquals("Black", duplicateRowLine("", listOf(summer, work), "Black", collator))
    }

    @Test
    fun withoutCategoryOrColourShowsOccasionsThenSeasons() {
        val tags = listOf(winter, work, summer, beach, colour)
        assertEquals("Beach · Work · Summer · Winter", duplicateRowLine("", tags, null, collator))
    }

    @Test
    fun nothingAtAllIsNull() {
        assertNull(duplicateRowLine("", emptyList(), null, collator))
        assertNull(duplicateRowLine("", listOf(colour), "", collator))
    }
}
