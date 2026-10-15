package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTagSeedsTest {
    @Test
    fun deletedBuiltInIsNotSeededAgain() {
        val defaults = listOf(
            DefaultTags.tags.first { it.id == "category-dresses" },
            DefaultTags.tags.first { it.id == "category-shirts" },
        )

        val seeded = DefaultTagSeeds.withoutDeleted(defaults, deletedTagIds = setOf("category-dresses"))

        assertEquals(listOf("category-shirts"), seeded.map(GarmentTag::id))
    }

    @Test
    fun noTombstonesSeedsEverything() {
        assertEquals(DefaultTags.tags, DefaultTagSeeds.withoutDeleted(DefaultTags.tags, deletedTagIds = emptySet()))
    }
}
