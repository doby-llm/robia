package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.MainColor
import kotlin.math.roundToInt

object GarmentColorAnalyzer {
    fun dominantPaletteMatches(
        samples: Sequence<RgbColor>,
        palette: List<MainColor>,
        classifier: PaletteColorClassifier = PaletteColorClassifier.Default,
    ): List<PaletteColorMatch> {
        if (palette.isEmpty()) return emptyList()

        val counts = mutableMapOf<MainColor, Double>()
        var reliablePixelWeight = 0.0

        samples.forEach { sample ->
            val weight = sample.foregroundWeight() ?: return@forEach
            val nearest = classifier.nearestColor(palette, sample)?.color ?: return@forEach
            counts[nearest] = (counts[nearest] ?: 0.0) + weight
            reliablePixelWeight += weight
        }

        if (reliablePixelWeight < MIN_RELIABLE_PIXEL_WEIGHT) return emptyList()

        // Rank by weighted garment pixels rather than raw RGB averages. This preserves
        // two-color garments while letting low-alpha mask fringes contribute only weakly.
        val rankedMatches = counts.entries
            .map { (color, count) ->
                PaletteColorMatch(
                    color = color,
                    pixelCount = count.roundToInt(),
                    ratio = (count / reliablePixelWeight).toFloat(),
                )
            }
            .sortedWith(PALETTE_MATCH_COMPARATOR)

        val primary = rankedMatches.firstOrNull { match -> match.ratio >= MIN_PRIMARY_RATIO } ?: return emptyList()
        // Secondary colors must be both large enough and perceptually distinct from the
        // primary swatch; otherwise shadows/highlights and near-duplicate custom colors
        // should collapse to a single detected garment color.
        val secondary = rankedMatches
            .asSequence()
            .drop(1)
            .firstOrNull { candidate ->
                candidate.ratio >= MIN_SECONDARY_RATIO &&
                    primary.isDistinctFrom(candidate, classifier)
            }

        return listOfNotNull(primary, secondary)
    }
}

data class PaletteColorMatch(
    val color: MainColor,
    val pixelCount: Int,
    val ratio: Float,
)

private fun RgbColor.foregroundWeight(): Double? = when {
    alpha < MIN_FOREGROUND_ALPHA -> null
    alpha < FULL_FOREGROUND_ALPHA -> SEMI_OPAQUE_FOREGROUND_WEIGHT
    else -> FULL_FOREGROUND_WEIGHT
}

private fun PaletteColorMatch.isDistinctFrom(
    other: PaletteColorMatch,
    classifier: PaletteColorClassifier,
): Boolean {
    if (color.id == other.color.id) return false
    val distance = classifier.distance(color, other.color) ?: return true
    return distance >= MIN_SECONDARY_DELTA_E
}

private val PALETTE_MATCH_COMPARATOR = compareByDescending<PaletteColorMatch> { it.pixelCount }
    .thenByDescending { it.ratio }
    .thenBy { it.color.sortOrder }
    .thenBy { it.color.id }

private const val MIN_FOREGROUND_ALPHA = 192
private const val FULL_FOREGROUND_ALPHA = 240
private const val SEMI_OPAQUE_FOREGROUND_WEIGHT = 0.35
private const val FULL_FOREGROUND_WEIGHT = 1.0
private const val MIN_RELIABLE_PIXEL_WEIGHT = 12.0
private const val MIN_PRIMARY_RATIO = 0.28f
private const val MIN_SECONDARY_RATIO = 0.18f
private const val MIN_SECONDARY_DELTA_E = 16.0
