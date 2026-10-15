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

    @Test
    fun twoBuiltInsWithTheSameNameAreBothKept() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("category-dresses", "category", "Dresses"),
                DedupeTag("category-shirts", "category", "Dresses"),
            ),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun customDuplicateMergesIntoFirstBuiltInWhenTwoBuiltInsCollide() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("category-shirts", "category", "Dresses"),
                DedupeTag("category-dresses", "category", "Dresses"),
                DedupeTag("custom-dresses", "category", "dresses"),
            ),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-dresses", keepId = "category-dresses")), merges)
    }

    @Test
    fun deletedBuiltInLeavesTheCustomTagAlone() {
        val merges = TagDedupePlanner.plan(
            listOf(DedupeTag("custom-dresses", "category", "Dresses")),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun customVsCustomKeepsTheLowestIdAndNeverDropsABuiltIn() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-b", "category", "Blouse"),
                DedupeTag("custom-a", "category", "Blouse"),
            ),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-b", keepId = "custom-a")), merges)
    }

    @Test
    fun divergentViewsOfTheSameRowsGiveTheSameMerge() {
        // Device A has {Dresses-built-in, custom-x}; device C has {custom-z, custom-x}. The planner sees the union.
        val union = listOf(
            DedupeTag("category-dresses", "category", "Dresses"),
            DedupeTag("custom-x", "category", "Dresses"),
            DedupeTag("custom-z", "category", "Dresses"),
        )

        val merges = TagDedupePlanner.plan(union)

        assertEquals(
            listOf(
                TagMerge(dropId = "custom-x", keepId = "category-dresses"),
                TagMerge(dropId = "custom-z", keepId = "category-dresses"),
            ),
            merges,
        )
        assertEquals(merges, TagDedupePlanner.plan(union.reversed()))
    }

    @Test
    fun tombstonedBuiltInGhostIsNeverMergedInto() {
        // Another device deleted 'Dresses'; this device still has the row at revision 0 (a ghost). Both rows survive.
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("category-dresses", "category", "Dresses", syncRevision = 0L),
                DedupeTag("custom-dresses", "category", "Dresses", syncRevision = 40L),
            ),
            tombstoneRevisionById = mapOf("category-dresses" to 10L),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun restoredBuiltInNewerThanItsTombstoneStillTakesMerges() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("category-dresses", "category", "Dresses", syncRevision = 50L),
                DedupeTag("custom-dresses", "category", "Dresses", syncRevision = 40L),
            ),
            tombstoneRevisionById = mapOf("category-dresses" to 10L),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-dresses", keepId = "category-dresses")), merges)
    }

    @Test
    fun ghostSkipDoesNotStopMergesInOtherGroups() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("category-dresses", "category", "Dresses", syncRevision = 0L),
                DedupeTag("custom-dresses", "category", "Dresses", syncRevision = 40L),
                DedupeTag("custom-a", "occasion", "Gala"),
                DedupeTag("custom-b", "occasion", "Gala"),
            ),
            tombstoneRevisionById = mapOf("category-dresses" to 10L),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-b", keepId = "custom-a")), merges)
    }

    @Test
    fun diacriticsKeepTagsDistinctForAutoMerge() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-mantel", "category", "Mantel"),
                DedupeTag("custom-mantel-accent", "category", "Mäntel"),
            ),
        )

        assertTrue(merges.isEmpty())
    }

    @Test
    fun caseAndSpacingStillMergeForAutoMerge() {
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("custom-a", "category", "Mäntel"),
                DedupeTag("custom-b", "category", "  mäntel  "),
            ),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-b", keepId = "custom-a")), merges)
    }

    @Test
    fun systemRowWithUnknownIdCountsAsBuiltInAndIsNeverDropped() {
        // A row left over from an older version: is_system = 1 but not in DefaultTags. It must not be dropped.
        val merges = TagDedupePlanner.plan(
            listOf(
                DedupeTag("legacy-dresses", "category", "Dresses", isSystem = true),
                DedupeTag("legacy-dresses-2", "category", "Dresses", isSystem = true),
                DedupeTag("custom-dresses", "category", "Dresses"),
            ),
        )

        assertEquals(listOf(TagMerge(dropId = "custom-dresses", keepId = "legacy-dresses")), merges)
    }
}
