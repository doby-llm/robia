package com.gusanitolabs.robia.suggestions

import java.util.Locale
import kotlin.math.sqrt

/** One label after scoring. `knn` is null when no saved garment carries the label. */
data class LabelScore(val tagId: String, val text: Float, val knn: Float?, val score: Float)

enum class ScoreMode { TEXT, COMBINED }

data class CategoryResult(
    val ranked: List<LabelScore>,
    val mode: ScoreMode,
    val margin: Float,
    val marked: String?,
    val reason: String,
)

/** Cosine similarity; a zero vector scores 0. */
internal fun cosine(a: FloatArray, b: FloatArray): Float {
    val denominator = sqrt(dot(a, a) * dot(b, b))
    return if (denominator == 0f) 0f else dot(a, b) / denominator
}

/**
 * DEC-008 steps 1–3, shared with DEC-009. `examples[tagId]` holds IMAGE vectors of other saved garments carrying
 * that tag. COMBINED only when at least two labels have examples; otherwise the score is the text cosine.
 */
fun scoreLabels(
    image: FloatArray,
    labels: List<Pair<String, FloatArray>>,
    examples: Map<String, List<FloatArray>>,
): Pair<List<LabelScore>, ScoreMode> {
    val text = FloatArray(labels.size) { index -> cosine(image, labels[index].second) }
    val knn = labels.map { (tagId, _) -> examples[tagId]?.takeIf { it.isNotEmpty() }?.let { topKnn(image, it) } }
    val defined = knn.filterNotNull()
    if (defined.size < 2) {
        return rankLabels(labels.mapIndexed { index, (tagId, _) -> LabelScore(tagId, text[index], knn[index], text[index]) }) to ScoreMode.TEXT
    }
    val textZ = zScores(text)
    val knnZ = zScores(defined.toFloatArray())
    var nextKnn = 0
    val scored = labels.mapIndexed { index, (tagId, _) ->
        val score = if (knn[index] == null) textZ[index] else textZ[index] + knnZ[nextKnn++]
        LabelScore(tagId, text[index], knn[index], score)
    }
    return rankLabels(scored) to ScoreMode.COMBINED
}

/**
 * DEC-008 step 4: the top label is marked only when its lead over the second reaches the mode's margin.
 * Fewer than two labels leaves no margin, so nothing is marked.
 */
fun selectCategory(scored: List<LabelScore>, mode: ScoreMode): CategoryResult {
    val ranked = rankLabels(scored)
    if (ranked.size < 2) return CategoryResult(ranked, mode, 0f, null, "no_labels")
    val margin = ranked[0].score - ranked[1].score
    val threshold = if (mode == ScoreMode.TEXT) EmbeddingRecipe.CATEGORY_MARGIN_TEXT else EmbeddingRecipe.CATEGORY_MARGIN_COMBINED
    return if (margin >= threshold) {
        CategoryResult(ranked, mode, margin, ranked[0].tagId, "margin_ok")
    } else {
        CategoryResult(ranked, mode, margin, null, "margin_below:%.6f".format(Locale.ROOT, margin))
    }
}

/** Score descending, then tagId ascending, so equal scores always order the same way. */
internal fun rankLabels(scored: List<LabelScore>): List<LabelScore> =
    scored.sortedWith(compareByDescending<LabelScore> { it.score }.thenBy { it.tagId })

/** Mean of the top `KNN_TOP` cosines between the image and the examples. */
private fun topKnn(image: FloatArray, examples: List<FloatArray>): Float {
    val top = examples.map { cosine(image, it) }.sortedDescending().take(EmbeddingRecipe.KNN_TOP)
    return top.sum() / top.size
}
