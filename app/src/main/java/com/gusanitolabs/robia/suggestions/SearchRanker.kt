package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import java.text.Normalizer

private val DIACRITICS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

/**
 * DEC-011: `0.7·cos(query, IMAGE) + 0.3·cos(query, TEXT)`; the TEXT term is left out when the garment has no TEXT
 * vector. Every item with an IMAGE vector is ranked, with no threshold. Items without IMAGE come last, by name.
 */
fun rankByQuery(
    itemIds: List<String>,
    query: FloatArray,
    vectors: Map<String, GarmentVectors>,
    nameOf: (String) -> String,
): List<String> {
    val scored = itemIds.mapNotNull { id ->
        val garment = vectors[id] ?: return@mapNotNull null
        val image = garment.image ?: return@mapNotNull null
        id to queryScore(query, image, garment.text)
    }
    val unscored = itemIds.filter { vectors[it]?.image == null }
    return scored
        .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { nameOf(it.first) })
        .map { it.first } + unscored.sortedBy { nameOf(it) }
}

/**
 * TS§3.11 similar garments: IMAGE cosine to `selfId`, most similar first. The garment itself is excluded; garments
 * without IMAGE follow in input order. Returns nothing when `selfId` has no IMAGE vector.
 */
fun rankSimilar(selfId: String, itemIds: List<String>, vectors: Map<String, GarmentVectors>): List<String> {
    val self = vectors[selfId]?.image ?: return emptyList()
    val candidates = itemIds.filter { it != selfId }
    val scored = candidates.mapNotNull { id -> vectors[id]?.image?.let { id to cosine(self, it) } }
    val unscored = candidates.filter { vectors[it]?.image == null }
    return scored.sortedByDescending { it.second }.map { it.first } + unscored
}

/**
 * Fallback when no query vector is available (TS§3.11). Accents and case are ignored; every query word must appear
 * in the name or notes. Keeps the input order.
 */
fun textFallback(items: List<ClothingItem>, query: String): List<ClothingItem> {
    val words = normalizeForSearch(query).split(WHITESPACE).filter(String::isNotEmpty)
    return items.filter { item ->
        val haystack = normalizeForSearch("${item.name} ${item.notes}")
        words.all { word -> word in haystack }
    }
}

private fun queryScore(query: FloatArray, image: FloatArray, text: FloatArray?): Float {
    val imageScore = cosine(query, image)
    if (text == null) return imageScore
    return EmbeddingRecipe.SEARCH_IMAGE_WEIGHT * imageScore + EmbeddingRecipe.SEARCH_TEXT_WEIGHT * cosine(query, text)
}

private fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
