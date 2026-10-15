package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDedupePlannerTest {
    @Test
    fun builtInIdWinsOverCustomDuplicate() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-dresses-1", "category", "Dresses"),
                DedupeTag("category-dresses", "category", "Dresses"),
            ),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-dresses-1", keepId = "category-dresses")), merges)
    }

    @Test
    fun withoutBuiltInTheLowestIdIsKept() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-b", "occasion", "Gala"),
                DedupeTag("custom-a", "occasion", "gala "),
                DedupeTag("custom-c", "occasion", "GALA"),
            ),
        )

        assertEquals(
            listOf(
                TagMerge(dropId = "custom-b", keepId = "custom-a"),
                TagMerge(dropId = "custom-c", keepId = "custom-a"),
            ),
            merges,
        )
    }

    @Test
    fun sameNameInDifferentCategoriesIsNotADuplicate() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-season-x", "season", "Formal"),
                DedupeTag("occasion-formal", "occasion", "Formal"),
            ),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun blankNamesAreNeverMerged() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-blank-1", "category", " "),
                DedupeTag("custom-blank-2", "category", ""),
            ),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun planIsStableForTheSameInput() {
        val tags = listOf(
            DedupeTag("custom-z", "category", "Dresses"),
            DedupeTag("category-dresses", "category", "Dresses"),
            DedupeTag("custom-y", "category", "Dresses"),
        )

        assertEquals(TagDedupePlanner.plan(tags), TagDedupePlanner.plan(tags.reversed()))
    }
}
