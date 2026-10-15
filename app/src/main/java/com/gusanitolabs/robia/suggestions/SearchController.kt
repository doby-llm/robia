package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

const val SEARCH_DEBOUNCE_MILLIS = 300L

/** The native query embedding cannot be cancelled, so the search stops waiting for it after this long. */
const val SEARCH_EMBED_TIMEOUT_MILLIS = 5_000L

/** How the Browse search ordered its result: by vector similarity, by words, or not at all (blank query). */
enum class SearchMode { Semantic, Text, None }

data class SearchResult(val orderedIds: List<String>, val mode: SearchMode)

/** The embedding of [query] (trimmed), or a null [vector] when it could not be computed. */
class QueryEmbedding(val query: String, val vector: FloatArray?)

/** Browse order right after a new garment is saved (PLAN-CHANGE 14, F01): newest first. Equal timestamps keep their order. */
fun sortNewestFirst(ids: List<String>, createdAtOf: (String) -> Long): List<String> = ids.sortedByDescending(createdAtOf)

/**
 * Browse search (TS§3.11, DEC-011), in two steps so that vector writes never re-embed the query:
 * [queryVector] embeds the query text (debounced, cached, bounded by a timeout), and [rank] orders the garments that
 * already pass the active filters with the vectors that exist at that moment. When the query embedding is missing, or no
 * filtered garment has an IMAGE vector, [rank] falls back to the word search over name and notes.
 *
 * The caller cancels [queryVector] on every query change; that cancellation is the debounce.
 */
class SearchController(
    private val embedQuery: suspend (String) -> FloatArray?,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
    private val timeoutMillis: Long = SEARCH_EMBED_TIMEOUT_MILLIS,
) {
    private var cachedQuery: String? = null
    private var cachedVector: FloatArray? = null
    private var inFlight: Pair<String, Deferred<FloatArray?>>? = null

    /**
     * Embedding of [query] (trimmed, non-blank), or null when the engine is not available, the embedding fails, or it
     * takes longer than [timeoutMillis]. The embedding runs on [scope] and is not cancelled with the caller, because the
     * native call cannot be stopped: a late result still fills the cache, and a restarted search reuses it.
     */
    suspend fun queryVector(query: String, engineAvailable: Boolean): FloatArray? {
        if (!engineAvailable) return null
        delay(debounceMillis)
        if (query == cachedQuery) return cachedVector
        val pending = inFlight?.takeIf { (text, _) -> text == query }?.second
            ?: scope.async { embed(query) }.also { inFlight = query to it }
        return withTimeoutOrNull(timeoutMillis) { pending.await() }
    }

    /** Orders [items] by similarity to [queryVector] when it exists, otherwise by words. Reads vectors only. */
    fun rank(
        query: String,
        items: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        queryVector: FloatArray?,
    ): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResult(items.map(ClothingItem::id), SearchMode.None)
        if (queryVector == null || items.none { vectors[it.id]?.image != null }) {
            return SearchResult(textFallback(items, trimmed).map(ClothingItem::id), SearchMode.Text)
        }
        val names = items.associate { it.id to it.name }
        val ranked = rankByQuery(items.map(ClothingItem::id), queryVector, vectors) { id -> names.getValue(id) }
        return SearchResult(ranked, SearchMode.Semantic)
    }

    private suspend fun embed(query: String): FloatArray? {
        val vector = try {
            embedQuery(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Search is optional: a failed embedding leaves the word search in charge.
            null
        }
        if (vector != null) {
            cachedQuery = query
            cachedVector = vector
        }
        return vector
    }
}
