package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.MainColor

class PaletteColorClassifier(
    private val strategy: ColorClassificationStrategy = ColorClassificationStrategy.CieLabDeltaE76,
) {
    private val metric: ColorDistanceMetric = when (strategy) {
        ColorClassificationStrategy.RgbEuclidean -> RgbEuclideanDistanceMetric
        ColorClassificationStrategy.CieLabDeltaE76 -> CieLabDeltaE76DistanceMetric
    }

    fun nearestColor(
        palette: List<MainColor>,
        sample: RgbColor,
    ): ScoredPaletteColor? = scoredColors(palette, sample).firstOrNull()

    fun distance(first: RgbColor, second: RgbColor): Double = metric.distance(first, second)

    fun distance(first: MainColor, second: MainColor): Double? {
        val firstRgb = RgbColor.fromHexOrNull(first.hex) ?: return null
        val secondRgb = RgbColor.fromHexOrNull(second.hex) ?: return null
        return distance(firstRgb, secondRgb)
    }

    private fun scoredColors(
        palette: List<MainColor>,
        sample: RgbColor,
    ): List<ScoredPaletteColor> {
        // Robia has no dedicated gray swatch yet. For low-chroma samples, restrict the
        // search to neutral wardrobe colors so camera noise does not drift into purple,
        // pink, blue, or green just because one RGB channel is a few points higher.
        val eligiblePalette = if (strategy == DEFAULT_STRATEGY && sample.isLowSaturation) {
            palette.filter(MainColor::isNeutralCandidate).ifEmpty { palette }
        } else {
            palette
        }

        return eligiblePalette.mapNotNull { color ->
            val paletteRgb = RgbColor.fromHexOrNull(color.hex) ?: return@mapNotNull null
            ScoredPaletteColor(color = color, score = metric.distance(sample.withoutAlpha(), paletteRgb))
        }.sortedWith(PALETTE_SCORE_COMPARATOR)
    }

    companion object {
        val DEFAULT_STRATEGY = ColorClassificationStrategy.CieLabDeltaE76
        val Default = PaletteColorClassifier(DEFAULT_STRATEGY)
        val LegacyRgb = PaletteColorClassifier(ColorClassificationStrategy.RgbEuclidean)
    }
}

data class ScoredPaletteColor(
    val color: MainColor,
    val score: Double,
)

private val PALETTE_SCORE_COMPARATOR = compareBy<ScoredPaletteColor> { it.score }
    .thenBy { it.color.sortOrder }
    .thenBy { it.color.id }

private fun MainColor.isNeutralCandidate(): Boolean = id in NEUTRAL_PALETTE_IDS

private val NEUTRAL_PALETTE_IDS = setOf(
    "black",
    "white",
    "gray-charcoal",
    "brown",
)
