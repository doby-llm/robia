package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccasionSelectorTest {
    @Test
    fun oneOccasionWhenTheLeadOverSecondIsAboveTextMargin() {
        // g12 = 0.5 - 0.2 = 0.3 > 0.010
        val result = selectOccasions(scored(0.5f, 0.2f, 0.19f), ScoreMode.TEXT, suggestOccasions = true)

        assertEquals(listOf("occasion-1"), result.selected)
        assertEquals("margin_ok", result.reason)
    }

    @Test
    fun twoOccasionsWhenOnlyTheSecondGapIsAboveTextMargin() {
        // g12 = 0.5 - 0.495 = 0.005 (not > 0.010); g23 = 0.495 - 0.3 = 0.195 > 0.010
        val result = selectOccasions(scored(0.5f, 0.495f, 0.3f), ScoreMode.TEXT, suggestOccasions = true)

        assertEquals(listOf("occasion-1", "occasion-2"), result.selected)
    }

    @Test
    fun noOccasionWhenBothGapsAreBelowTextMargin() {
        // g12 = 0.005, g23 = 0.005
        val result = selectOccasions(scored(0.5f, 0.495f, 0.49f), ScoreMode.TEXT, suggestOccasions = true)

        assertEquals(emptyList<String>(), result.selected)
        assertEquals("margin_below:0.005000", result.reason)
    }

    @Test
    fun combinedUsesHalfAndIsStrict() {
        // g12 = 0.5 is not > 0.5; g23 = 0.5 is not > 0.5: nothing selected.
        assertEquals(emptyList<String>(), selectOccasions(scored(1f, 0.5f, 0f), ScoreMode.COMBINED, suggestOccasions = true).selected)
        // g12 = 0.2 is not > 0.5; g23 = 0.8 > 0.5: two occasions.
        assertEquals(listOf("occasion-1", "occasion-2"), selectOccasions(scored(1f, 0.8f, 0f), ScoreMode.COMBINED, suggestOccasions = true).selected)
        // g12 = 1.0 > 0.5: one occasion.
        assertEquals(listOf("occasion-1"), selectOccasions(scored(1f, 0f, -0.6f), ScoreMode.COMBINED, suggestOccasions = true).selected)
    }

    @Test
    fun flagOffSkipsSelectionAndRanking() {
        val result = selectOccasions(scored(1f, 0f, -1f), ScoreMode.TEXT, suggestOccasions = false)

        assertEquals(emptyList<String>(), result.selected)
        assertEquals(emptyList<LabelScore>(), result.ranked)
        assertNull(result.g12)
        assertEquals("flag_off", result.reason)
    }

    @Test
    fun ranksAreSortedBeforeGapsAreTaken() {
        val result = selectOccasions(
            listOf(scored("occasion-b", 0.2f), scored("occasion-a", 0.5f), scored("occasion-c", 0.1f)),
            ScoreMode.TEXT,
            suggestOccasions = true,
        )

        assertEquals(listOf("occasion-a", "occasion-b", "occasion-c"), result.ranked.map { it.tagId })
        assertEquals(0.3f, result.g12!!, 1e-6f)
        assertEquals(0.1f, result.g23!!, 1e-6f)
    }

    private fun scored(first: Float, second: Float, third: Float): List<LabelScore> = listOf(
        scored("occasion-1", first),
        scored("occasion-2", second),
        scored("occasion-3", third),
    )

    private fun scored(tagId: String, score: Float) = LabelScore(tagId, text = score, knn = null, score = score)
}
