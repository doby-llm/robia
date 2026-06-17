package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTagsTest {
    @Test
    fun defaultTagsReferenceExistingCategories() {
        val categoryIds = DefaultTags.categories.map(TagCategory::id).toSet()
        assertTrue(DefaultTags.tags.all { tag -> tag.categoryId in categoryIds })
    }

    @Test
    fun manageTaxonomyUsesCurrentEditableGroups() {
        val categoryIds = DefaultTags.categories.map(TagCategory::id)
        assertEquals(listOf("category", "season", "fit", "occasion", "location"), categoryIds)
        assertFalse(categoryIds.contains("care"))
    }

    @Test
    fun onlySeasonTagsAreImmutableSystemTags() {
        assertTrue(DefaultTags.tags.filter { tag -> tag.categoryId == "season" }.all { tag -> tag.isSystem })
        assertTrue(DefaultTags.tags.filterNot { tag -> tag.categoryId == "season" }.none { tag -> tag.isSystem })
    }

    @Test
    fun mainColorPaletteContainsMinimumDefaultColors() {
        assertEquals(12, DefaultTags.mainColors.count { color -> color.isDefault })
        assertTrue(DefaultTags.mainColors.all { color -> color.hex.matches(Regex("#[0-9A-Fa-f]{6}")) })
    }
}
