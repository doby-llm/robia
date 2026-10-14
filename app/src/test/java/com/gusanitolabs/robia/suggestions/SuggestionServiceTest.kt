package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.engine.EncodedImage
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import com.gusanitolabs.robia.ui.reconcilePredictedTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionServiceTest {
    private val dresses = tag(id = "dresses", categoryId = "category")
    private val skirts = tag(id = "skirts", categoryId = "category")
    private val accessories = tag(id = "accessories", categoryId = "category", suggestOccasions = false)
    private val everyday = tag(id = "everyday", categoryId = "occasion")
    private val formal = tag(id = "formal", categoryId = "occasion")
    private val tags = listOf(dresses, skirts, accessories, everyday, formal)

    @Test
    fun unavailableWhenModelIsNotReadyAndNothingIsEncoded() = runBlocking {
        val embedder = FakeEmbedder(image = floatArrayOf(1f, 0f))
        var encodeCalls = 0
        val service = service(embedder, modelReady = false)

        val outcome = service.suggestForEncodedImage(tags, selectedCategoryId = null, excludeGarmentId = null) {
            encodeCalls++
            EncodedImage(byteArrayOf(1), 1, 1)
        }

        assertEquals(SuggestionOutcome.Unavailable, outcome)
        assertEquals(0, encodeCalls)
        assertEquals(0, embedder.imageCalls)
        assertTrue(embedder.textCalls.isEmpty())
    }

    @Test
    fun categoryIsMarkedOnlyWhenTheLeadReachesTheTextMargin() = runBlocking {
        val clear = FakeEmbedder(
            image = floatArrayOf(1f, 0f),
            textVectors = mapOf("Dresses" to floatArrayOf(1f, 0f), "Skirts" to floatArrayOf(0f, 1f)),
        )
        val clearOutcome = ready(clear, selectedCategoryId = null)
        assertEquals("dresses", clearOutcome.categoryResult.marked)
        assertEquals("margin_ok", clearOutcome.categoryResult.reason)

        // Lead of 0.0001 is below the 0.010 text margin: nothing is marked.
        val close = FakeEmbedder(
            image = floatArrayOf(1f, 0f),
            textVectors = mapOf("Dresses" to floatArrayOf(1f, 0f), "Skirts" to floatArrayOf(0.9999f, 0.01414f)),
        )
        val closeOutcome = ready(close, selectedCategoryId = null)
        assertEquals(null, closeOutcome.categoryResult.marked)
        assertTrue(closeOutcome.categoryResult.reason.startsWith("margin_below"))
        assertTrue(closeOutcome.predictedTagIds.isEmpty())
    }

    @Test
    fun occasionIsSkippedForACategoryThatTurnsOffSuggestions() = runBlocking {
        val embedder = FakeEmbedder(
            image = floatArrayOf(1f, 0f),
            textVectors = mapOf(
                "Accessories" to floatArrayOf(1f, 0f),
                "Everyday" to floatArrayOf(1f, 0f),
                "Formal" to floatArrayOf(0f, 1f),
            ),
        )

        val outcome = ready(embedder, selectedCategoryId = accessories.id)

        val occasion = outcome.occasionResult
        assertEquals("flag_off", occasion?.reason)
        assertTrue(occasion?.selected.orEmpty().isEmpty())
        assertFalse(embedder.textCalls.any { text -> text.endsWith("Everyday") || text.endsWith("Formal") })
    }

    @Test
    fun occasionIsScoredForACategoryThatAllowsSuggestions() = runBlocking {
        val embedder = FakeEmbedder(
            image = floatArrayOf(1f, 0f),
            textVectors = mapOf(
                "Dresses" to floatArrayOf(1f, 0f),
                "Everyday" to floatArrayOf(1f, 0f),
                "Formal" to floatArrayOf(0f, 1f),
            ),
        )

        val outcome = ready(embedder, selectedCategoryId = dresses.id)

        assertEquals("margin_ok", outcome.occasionResult?.reason)
        assertEquals(listOf("everyday"), outcome.occasionResult?.selected)
        assertEquals(setOf("everyday"), outcome.predictedTagIds - setOf("dresses"))
    }

    @Test
    fun userChosenCategorySurvivesPredictedTags() {
        val result = reconcilePredictedTags(
            currentTagIds = listOf(skirts.id),
            currentModelOwnedTagIds = emptyList(),
            predictedTagIds = setOf(dresses.id),
            availableTags = tags,
            userOverriddenCategories = listOf("category"),
        )

        assertEquals(listOf(skirts.id), result.selectedTagIds)
        assertEquals(emptyList<String>(), result.modelOwnedTagIds)
    }

    @Test
    fun lowMarginPhotoKeepsTheCategoryAlreadyOnTheGarment() {
        val result = reconcilePredictedTags(
            currentTagIds = listOf(skirts.id),
            currentModelOwnedTagIds = emptyList(),
            predictedTagIds = emptySet(),
            availableTags = tags,
            userOverriddenCategories = emptyList(),
        )

        assertEquals(listOf(skirts.id), result.selectedTagIds)
    }

    private suspend fun ready(embedder: FakeEmbedder, selectedCategoryId: String?): SuggestionOutcome.Ready {
        val outcome = service(embedder, modelReady = true).suggestForEncodedImage(
            tags = tags,
            selectedCategoryId = selectedCategoryId,
            excludeGarmentId = null,
        ) { EncodedImage(byteArrayOf(1, 2, 3), 1, 1) }
        return outcome as SuggestionOutcome.Ready
    }

    private fun service(embedder: TextImageEmbedder, modelReady: Boolean) = SuggestionService(
        embedder = embedder,
        isModelReady = { modelReady },
        encodeImage = { error("Not used by the scoring tests") },
        savedExamples = { emptyList() },
        preloadEngine = {},
        clockMillis = { 0L },
    )

    private fun tag(id: String, categoryId: String, suggestOccasions: Boolean = true) = GarmentTag(
        id = id,
        categoryId = categoryId,
        name = id.replaceFirstChar { it.uppercase() },
        sortOrder = 0,
        suggestOccasions = suggestOccasions,
    )

    /** Returns the vector registered under the label name; unknown labels get a zero vector. */
    private class FakeEmbedder(
        private val image: FloatArray,
        private val textVectors: Map<String, FloatArray> = emptyMap(),
    ) : TextImageEmbedder {
        var imageCalls = 0
        val textCalls = mutableListOf<String>()

        override suspend fun embedImage(png: ByteArray): FloatArray {
            imageCalls++
            return image
        }

        override suspend fun embedText(text: String): FloatArray {
            textCalls += text
            return textVectors[text.removePrefix(EmbeddingRecipe.CLASSIFICATION_PREFIX)] ?: floatArrayOf(0f, 0f)
        }
    }
}
