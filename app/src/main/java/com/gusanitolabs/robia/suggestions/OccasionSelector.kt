package com.gusanitolabs.robia.suggestions

import java.util.Locale

data class OccasionResult(
    val ranked: List<LabelScore>,
    val mode: ScoreMode,
    val g12: Float?,
    val g23: Float?,
    val selected: List<String>,
    val reason: String,
)

/**
 * DEC-009: occasions are scored with `scoreLabels` (examples = saved garments carrying each occasion). Selection is
 * strict: `g12 > T` selects the first occasion, else `g23 > T` selects the first two, else none. When the garment's
 * category has `suggestOccasions = false` nothing is selected and nothing is ranked.
 */
fun selectOccasions(scored: List<LabelScore>, mode: ScoreMode, suggestOccasions: Boolean): OccasionResult {
    if (!suggestOccasions) return OccasionResult(emptyList(), mode, null, null, emptyList(), "flag_off")
    val ranked = rankLabels(scored)
    val threshold = if (mode == ScoreMode.TEXT) EmbeddingRecipe.OCCASION_MARGIN_TEXT else EmbeddingRecipe.OCCASION_MARGIN_COMBINED
    val g12 = if (ranked.size >= 2) ranked[0].score - ranked[1].score else null
    val g23 = if (ranked.size >= 3) ranked[1].score - ranked[2].score else null
    val selected = when {
        g12 != null && g12 > threshold -> listOf(ranked[0].tagId)
        g23 != null && g23 > threshold -> listOf(ranked[0].tagId, ranked[1].tagId)
        else -> emptyList()
    }
    val reason = if (selected.isNotEmpty()) "margin_ok" else "margin_below:%.6f".format(Locale.ROOT, g23 ?: 0f)
    return OccasionResult(ranked, mode, g12, g23, selected, reason)
}
