package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchControllerTest {
    private val items = listOf(
        item("1", "Vestido rojo", "verano"),
        item("2", "Falda negra"),
        item("3", "Vestido azul"),
    )
    private val vectors = mapOf(
        "1" to GarmentVectors("1", image = floatArrayOf(1f, 0f, 0f, 0f), text = null),
        "2" to GarmentVectors("2", image = floatArrayOf(0f, 1f, 0f, 0f), text = null),
        "3" to GarmentVectors("3", image = null, text = null),
    )

    @Test
    fun blankQueryKeepsTheOriginalOrderAndDoesNotEmbed() {
        val embedder = FakeQueryEmbedder()
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("   ", items, vectors, engineAvailable = true) }

        assertEquals(listOf("1", "2", "3"), result.orderedIds)
        assertEquals(SearchMode.None, result.mode)
        assertEquals(emptyList<String>(), embedder.queries)
    }

    @Test
    fun nonBlankQueryWaitsThe300MsDebounceBeforeRanking() {
        assertEquals(300L, SEARCH_DEBOUNCE_MILLIS)
        val embedder = FakeQueryEmbedder()
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) })

        val elapsedMillis = runBlocking {
            val startedAt = System.nanoTime()
            controller.search("vestido", items, vectors, engineAvailable = true)
            (System.nanoTime() - startedAt) / 1_000_000L
        }

        assertTrue("debounce was $elapsedMillis ms", elapsedMillis >= SEARCH_DEBOUNCE_MILLIS)
    }

    @Test
    fun aQueryReplacedBeforeTheDebounceEndsNeverReachesTheEmbedder() {
        val embedder = FakeQueryEmbedder()
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) })

        val queries = runBlocking {
            val stale = launch { controller.search("ve", items, vectors, engineAvailable = true) }
            delay(50)
            stale.cancelAndJoin()
            controller.search("vestido", items, vectors, engineAvailable = true)
            embedder.queries.toList()
        }

        assertEquals(listOf("vestido"), queries)
    }

    @Test
    fun semanticOrderUsesTheQueryVectorWhenTheEngineIsReady() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(0.6f, 0.8f, 0f, 0f))
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("falda", items, vectors, engineAvailable = true) }

        assertEquals(listOf("2", "1", "3"), result.orderedIds)
        assertEquals(SearchMode.Semantic, result.mode)
        assertEquals(listOf("falda"), embedder.queries)
    }

    @Test
    fun wordSearchIsUsedWhenTheEngineIsNotReady() {
        val embedder = FakeQueryEmbedder()
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("VESTIDO", items, vectors, engineAvailable = false) }

        assertEquals(listOf("1", "3"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
        assertEquals(emptyList<String>(), embedder.queries)
    }

    @Test
    fun wordSearchIsUsedWhenTheQueryEmbeddingFails() {
        val embedder = FakeQueryEmbedder(failure = IllegalStateException("model unloaded"))
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("falda", items, vectors, engineAvailable = true) }

        assertEquals(listOf("2"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
    }

    @Test
    fun wordSearchIsUsedWhenNoFilteredGarmentHasAnImageVector() {
        val embedder = FakeQueryEmbedder()
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("vestido", items.take(1), emptyMap(), engineAvailable = true) }

        assertEquals(listOf("1"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
        assertEquals(emptyList<String>(), embedder.queries)
    }

    @Test
    fun garmentsWithoutImageVectorComeLastInSemanticOrder() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(1f, 0f, 0f, 0f))
        val controller = SearchController(embedQuery = { query -> embedder.embed(query) }, debounceMillis = 0L)

        val result = runBlocking { controller.search("vestido", items, vectors, engineAvailable = true) }

        assertEquals(listOf("1", "2", "3"), result.orderedIds)
        assertEquals(SearchMode.Semantic, result.mode)
    }

    @Test
    fun similarGarmentsExcludeTheGarmentItself() {
        assertEquals(listOf("2"), rankSimilar("1", listOf("1", "2"), vectors))
    }

    private class FakeQueryEmbedder(
        private val vector: FloatArray = floatArrayOf(1f, 0f, 0f, 0f),
        private val failure: Exception? = null,
    ) {
        val queries = mutableListOf<String>()

        suspend fun embed(query: String): FloatArray? {
            queries += query
            failure?.let { throw it }
            return vector
        }
    }

    private fun item(id: String, name: String, notes: String = "") = ClothingItem(
        id = id,
        name = name,
        notes = notes,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
