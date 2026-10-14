package com.gusanitolabs.robia.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryScorerTest {
    private val image = floatArrayOf(1f, 0f, 0f, 0f)

    // Cosines against `image` are the first components: a = 1.0, b = 0.0, c = 0.0.
    private val labels = listOf(
        "category-a" to floatArrayOf(1f, 0f, 0f, 0f),
        "category-b" to floatArrayOf(0f, 1f, 0f, 0f),
        "category-c" to floatArrayOf(0f, 0f, 1f, 0f),
    )

    @Test
    fun knnUsesTopThreeExamplesAndTextModeWhenOnlyOneLabelHasExamples() {
        // Example cosines for a: 0.9, 0.8, 0.7, 0.1. Top-3 mean = (0.9 + 0.8 + 0.7) / 3 = 0.8.
        val examples = mapOf(
            "category-a" to listOf(
                floatArrayOf(0.9f, 0.43589f, 0f, 0f),
                floatArrayOf(0.8f, 0.6f, 0f, 0f),
                floatArrayOf(0.7f, 0.71414f, 0f, 0f),
                floatArrayOf(0.1f, 0.99499f, 0f, 0f),
            ),
        )

        val (scored, mode) = scoreLabels(image, labels, examples)

        assertEquals(ScoreMode.TEXT, mode)
        assertEquals(0.8f, scored.first { it.tagId == "category-a" }.knn!!, 1e-4f)
        // TEXT: score is the text cosine; equal scores (b, c) sort by tagId.
        assertEquals(listOf("category-a", "category-b", "category-c"), scored.map { it.tagId })
        assertEquals(1f, scored[0].score, 1e-6f)
        assertEquals(0f, scored[1].score, 1e-6f)
    }

    @Test
    fun combinedModeAddsZScoredKnnWhenTwoLabelsHaveExamples() {
        // Text cosines 1, 0, 0: mean 1/3, population std sqrt(2/9), so z = sqrt(2), -1/sqrt(2), -1/sqrt(2).
        // kNN: a = 0.8 (example at 0.8), b = 0 (orthogonal example); z over {0.8, 0} = +1, -1. c has no examples.
        // Scores: a = 1.41421 + 1 = 2.41421, c = -0.70711, b = -0.70711 - 1 = -1.70711.
        val examples = mapOf(
            "category-a" to listOf(floatArrayOf(0.8f, 0.6f, 0f, 0f)),
            "category-b" to listOf(floatArrayOf(0f, 1f, 0f, 0f)),
        )

        val (scored, mode) = scoreLabels(image, labels, examples)

        assertEquals(ScoreMode.COMBINED, mode)
        assertEquals(listOf("category-a", "category-c", "category-b"), scored.map { it.tagId })
        assertEquals(2.41421f, scored[0].score, 1e-4f)
        assertEquals(-0.70711f, scored[1].score, 1e-4f)
        assertEquals(-1.70711f, scored[2].score, 1e-4f)
    }

    @Test
    fun noLabelsGiveNoScores() {
        val (scored, mode) = scoreLabels(image, emptyList(), emptyMap())

        assertEquals(ScoreMode.TEXT, mode)
        assertEquals(emptyList<LabelScore>(), scored)
    }

    @Test
    fun textMarginNeedsZeroPointZeroOneAndCombinedNeedsHalf() {
        assertEquals("category-a", selectCategory(listOf(scored("category-a", 0.511f), scored("category-b", 0.5f)), ScoreMode.TEXT).marked)
        assertNull(selectCategory(listOf(scored("category-a", 0.509f), scored("category-b", 0.5f)), ScoreMode.TEXT).marked)
        // COMBINED uses 0.50: exactly 0.50 passes, 0.49 fails, and a 0.011 lead is not enough.
        assertEquals("category-a", selectCategory(listOf(scored("category-a", 1f), scored("category-b", 0.5f)), ScoreMode.COMBINED).marked)
        assertNull(selectCategory(listOf(scored("category-a", 0.99f), scored("category-b", 0.5f)), ScoreMode.COMBINED).marked)
        assertNull(selectCategory(listOf(scored("category-a", 0.511f), scored("category-b", 0.5f)), ScoreMode.COMBINED).marked)
    }

    @Test
    fun equalScoresSortByTagIdAndLeaveNothingMarked() {
        val result = selectCategory(listOf(scored("category-b", 0.5f), scored("category-a", 0.5f)), ScoreMode.TEXT)

        assertEquals(listOf("category-a", "category-b"), result.ranked.map { it.tagId })
        assertEquals(0f, result.margin, 0f)
        assertNull(result.marked)
    }

    @Test
    fun aSingleLabelHasNoMargin() {
        val result = selectCategory(listOf(scored("category-a", 1f)), ScoreMode.TEXT)

        assertNull(result.marked)
        assertEquals("no_labels", result.reason)
    }

    private fun scored(tagId: String, score: Float) = LabelScore(tagId, text = score, knn = null, score = score)
}
