package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    @After
    fun stopPendingEmbeddings() {
        scope.cancel()
    }

    @Test
    fun blankQueryKeepsTheOriginalOrderAndDoesNotEmbed() {
        val embedder = FakeQueryEmbedder()
        val controller = controller(embedder)

        val result = runBlocking { controller.search("   ", items, vectors, engineAvailable = true) }

        assertEquals(listOf("1", "2", "3"), result.orderedIds)
        assertEquals(SearchMode.None, result.mode)
        assertEquals(emptyList<String>(), embedder.queries)
    }

    @Test
    fun nonBlankQueryWaitsThe300MsDebounceBeforeRanking() {
        assertEquals(300L, SEARCH_DEBOUNCE_MILLIS)
        val embedder = FakeQueryEmbedder()
        val controller = controller(embedder, debounceMillis = SEARCH_DEBOUNCE_MILLIS)

        val elapsedMillis = runBlocking {
            val startedAt = System.nanoTime()
            controller.queryVector("vestido", engineAvailable = true)
            (System.nanoTime() - startedAt) / 1_000_000L
        }

        assertTrue("debounce was $elapsedMillis ms", elapsedMillis >= SEARCH_DEBOUNCE_MILLIS)
    }

    @Test
    fun aQueryReplacedBeforeTheDebounceEndsNeverReachesTheEmbedder() {
        val embedder = FakeQueryEmbedder()
        val controller = controller(embedder, debounceMillis = SEARCH_DEBOUNCE_MILLIS)

        val queries = runBlocking {
            val stale = launch { controller.queryVector("ve", engineAvailable = true) }
            delay(50)
            stale.cancelAndJoin()
            controller.queryVector("vestido", engineAvailable = true)
            embedder.queries.toList()
        }

        assertEquals(listOf("vestido"), queries)
    }

    @Test
    fun semanticOrderUsesTheQueryVectorWhenTheEngineIsReady() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(0.6f, 0.8f, 0f, 0f))
        val controller = controller(embedder)

        val result = runBlocking { controller.search("falda", items, vectors, engineAvailable = true) }

        assertEquals(listOf("2", "1", "3"), result.orderedIds)
        assertEquals(SearchMode.Semantic, result.mode)
        assertEquals(listOf("falda"), embedder.queries)
    }

    @Test
    fun wordSearchIsUsedWhenTheEngineIsNotReady() {
        val embedder = FakeQueryEmbedder()
        val controller = controller(embedder)

        val result = runBlocking { controller.search("VESTIDO", items, vectors, engineAvailable = false) }

        assertEquals(listOf("1", "3"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
        assertEquals(emptyList<String>(), embedder.queries)
    }

    @Test
    fun wordSearchIsUsedWhenTheQueryEmbeddingFails() {
        val embedder = FakeQueryEmbedder(failure = IllegalStateException("model unloaded"))
        val controller = controller(embedder)

        val result = runBlocking { controller.search("falda", items, vectors, engineAvailable = true) }

        assertEquals(listOf("2"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
    }

    @Test
    fun hangingQueryEmbeddingGivesUpAfterTheTimeoutAndUsesWordSearch() {
        val controller = SearchController(
            embedQuery = { awaitCancellation() },
            scope = scope,
            debounceMillis = 0L,
            timeoutMillis = 100L,
        )

        val (elapsedMillis, result) = runBlocking {
            val startedAt = System.nanoTime()
            val result = controller.search("vestido", items, vectors, engineAvailable = true)
            (System.nanoTime() - startedAt) / 1_000_000L to result
        }

        assertTrue("gave up after $elapsedMillis ms", elapsedMillis < 2_000L)
        assertEquals(listOf("1", "3"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
    }

    @Test
    fun aLateEmbeddingFillsTheCacheSoTheRestartedSearchDoesNotEmbedAgain() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(1f, 0f, 0f, 0f), delayMillis = 150L)
        val controller = controller(embedder, timeoutMillis = 30L)

        val (firstVector, secondVector) = runBlocking {
            val first = controller.queryVector("vestido", engineAvailable = true)
            delay(200)
            val second = controller.queryVector("vestido", engineAvailable = true)
            first to second
        }

        assertNull(firstVector)
        assertEquals(listOf(1f, 0f, 0f, 0f), secondVector?.toList())
        assertEquals(listOf("vestido"), embedder.queries)
    }

    @Test
    fun vectorWritesRankWithTheLatestVectorsWithoutEmbeddingAgain() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(0f, 1f, 0f, 0f))
        val controller = controller(embedder)
        val backfilled = vectors + ("3" to GarmentVectors("3", image = floatArrayOf(0f, 1f, 0f, 0f), text = null))

        val (before, after) = runBlocking {
            val queryVector = controller.queryVector("falda", engineAvailable = true)
            val before = controller.rank("falda", items, vectors, queryVector)
            val after = controller.rank("falda", items, backfilled, controller.queryVector("falda", engineAvailable = true))
            before to after
        }

        assertEquals(listOf("2", "1", "3"), before.orderedIds)
        // Garment 3 gained an IMAGE vector identical to the query: it ties with 2 and moves ahead of 1.
        assertEquals(setOf("2", "3"), after.orderedIds.take(2).toSet())
        assertEquals("1", after.orderedIds.last())
        assertEquals(SearchMode.Semantic, after.mode)
        assertEquals(listOf("falda"), embedder.queries)
    }

    @Test
    fun wordSearchIsUsedWhenNoFilteredGarmentHasAnImageVector() {
        val embedder = FakeQueryEmbedder()
        val controller = controller(embedder)

        val result = runBlocking { controller.search("vestido", items.take(1), emptyMap(), engineAvailable = true) }

        assertEquals(listOf("1"), result.orderedIds)
        assertEquals(SearchMode.Text, result.mode)
    }

    @Test
    fun garmentsWithoutImageVectorComeLastInSemanticOrder() {
        val embedder = FakeQueryEmbedder(vector = floatArrayOf(1f, 0f, 0f, 0f))
        val controller = controller(embedder)

        val result = runBlocking { controller.search("vestido", items, vectors, engineAvailable = true) }

        assertEquals(listOf("1", "2", "3"), result.orderedIds)
        assertEquals(SearchMode.Semantic, result.mode)
    }

    @Test
    fun similarGarmentsExcludeTheGarmentItself() {
        assertEquals(listOf("2"), rankSimilar("1", listOf("1", "2"), vectors))
    }

    @Test
    fun aNewlySavedGarmentSortsFirstByCreationTime() {
        val createdAt = mapOf("1" to 10L, "2" to 30L, "3" to 20L)

        assertEquals(listOf("2", "3", "1"), sortNewestFirst(listOf("1", "2", "3")) { id -> createdAt.getValue(id) })
    }

    private fun controller(
        embedder: FakeQueryEmbedder,
        debounceMillis: Long = 0L,
        timeoutMillis: Long = SEARCH_EMBED_TIMEOUT_MILLIS,
    ) = SearchController(
        embedQuery = { query -> embedder.embed(query) },
        scope = scope,
        debounceMillis = debounceMillis,
        timeoutMillis = timeoutMillis,
    )

    /** What the Browse screen does: embed the trimmed query, then rank with the vectors at hand. */
    private suspend fun SearchController.search(
        query: String,
        items: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        engineAvailable: Boolean,
    ): SearchResult {
        val trimmed = query.trim()
        val queryVector = if (trimmed.isEmpty()) null else queryVector(trimmed, engineAvailable)
        return rank(query, items, vectors, queryVector)
    }

    private class FakeQueryEmbedder(
        private val vector: FloatArray = floatArrayOf(1f, 0f, 0f, 0f),
        private val failure: Exception? = null,
        private val delayMillis: Long = 0L,
    ) {
        val queries = mutableListOf<String>()

        suspend fun embed(query: String): FloatArray? {
            queries += query
            if (delayMillis > 0L) delay(delayMillis)
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
