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
        assertEquals(listOf("category", "season", "occasion", "location"), categoryIds)
        assertFalse(categoryIds.contains("care"))
        assertFalse(categoryIds.contains("fit"))
    }

    @Test
    fun onlySeasonTagsAreImmutableSystemTags() {
        assertTrue(DefaultTags.tags.filter { tag -> tag.categoryId == "season" }.all { tag -> tag.isSystem })
        assertTrue(DefaultTags.tags.filterNot { tag -> tag.categoryId == "season" }.none { tag -> tag.isSystem })
    }

    @Test
    fun modelAlignedDefaultTagsAreAvailable() {
        val tagIds = DefaultTags.tags.map(GarmentTag::id).toSet()

        assertTrue(
            tagIds.containsAll(
                listOf(
                    "category-shorts",
                    "category-jackets",
                    "category-jumpsuits",
                    "category-blouses",
                    "category-dresses",
                    "category-skirts",
                    "category-blazers",
                    "category-cardigans",
                    "category-bags",
                    "category-tops",
                    "category-knitwear",
                    "category-trousers",
                    "category-sweaters",
                    "category-shoes",
                    "category-shirts",
                    "category-vests",
                    "category-jewelry",
                    "category-accessories",
                    "category-coats",
                ),
            ),
        )
        assertTrue(tagIds.containsAll(listOf("season-spring", "season-summer", "season-fall", "season-winter")))
        assertFalse(tagIds.contains("season-autumn"))
        assertTrue(tagIds.containsAll(listOf("occasion-active", "occasion-statement", "occasion-dressed-up", "occasion-formal", "occasion-everyday", "occasion-business")))
        assertFalse(tagIds.any { id -> id.contains("multi", ignoreCase = true) })
    }

    @Test
    fun mainColorPaletteContainsMinimumDefaultColors() {
        assertEquals(11, DefaultTags.mainColors.count { color -> color.isDefault })
        assertTrue(DefaultTags.mainColors.all { color -> color.hex.matches(Regex("#[0-9A-Fa-f]{6}")) })
    }

    @Test
    fun mainColorPaletteHasSingleBeigeDefault() {
        val beigeDefaults = DefaultTags.mainColors.filter { color ->
            color.name.contains("Beige", ignoreCase = true)
        }

        assertEquals(listOf("gray-charcoal"), beigeDefaults.map { color -> color.id })
    }
}
