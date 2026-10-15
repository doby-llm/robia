package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

const val SEARCH_DEBOUNCE_MILLIS = 300L

/** How the Browse search ordered its result: by vector similarity, by words, or not at all (blank query). */
enum class SearchMode { Semantic, Text, None }

data class SearchResult(val orderedIds: List<String>, val mode: SearchMode)

/** Browse order right after a new garment is saved (PLAN-CHANGE 14, F01): newest first. Equal timestamps keep their order. */
fun sortNewestFirst(ids: List<String>, createdAtOf: (String) -> Long): List<String> = ids.sortedByDescending(createdAtOf)

/**
 * Browse search (TS§3.11, DEC-011). A non-blank query waits [debounceMillis], then ranks the garments that already
 * pass the active filters. It only reads vectors. When the engine is not available, the query embedding fails, or no
 * filtered garment has an IMAGE vector, it falls back to the word search over name and notes.
 *
 * The caller runs each search in a coroutine that it cancels on every input change; that cancellation is the debounce.
 */
class SearchController(
    private val embedQuery: suspend (String) -> FloatArray?,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) {
    private var cachedQuery: String? = null
    private var cachedVector: FloatArray? = null

    suspend fun search(
        query: String,
        items: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        engineAvailable: Boolean,
    ): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResult(items.map(ClothingItem::id), SearchMode.None)
        delay(debounceMillis)
        val queryVector = if (engineAvailable && items.any { vectors[it.id]?.image != null }) {
            queryVector(trimmed)
        } else {
            null
        }
        if (queryVector == null) {
            return SearchResult(textFallback(items, trimmed).map(ClothingItem::id), SearchMode.Text)
        }
        val names = items.associate { it.id to it.name }
        val ranked = rankByQuery(items.map(ClothingItem::id), queryVector, vectors) { id -> names.getValue(id) }
        return SearchResult(ranked, SearchMode.Semantic)
    }

    // Typing a longer query re-runs the search with the same text, so the last successful embedding is kept.
    private suspend fun queryVector(query: String): FloatArray? {
        if (query == cachedQuery) return cachedVector
        val vector = try {
            embedQuery(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Search is optional: a failed embedding leaves the word search in charge.
            null
        }
        if (vector == null) return null
        cachedQuery = query
        cachedVector = vector
        return vector
    }
}
