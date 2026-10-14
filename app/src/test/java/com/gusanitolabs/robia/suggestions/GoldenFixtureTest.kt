package com.gusanitolabs.robia.suggestions

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Kotlin scores must match the Python-computed fixture (TS§3.6, V-E04e): same ranks, marks, selections, scores ±1e-4. */
class GoldenFixtureTest {
    private val fixture: JSONObject by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream("/suggestions/golden-scores.json")) { "golden fixture missing" }
            .bufferedReader().use { it.readText() }
        JSONObject(text)
    }

    private val labels: List<Pair<String, FloatArray>> by lazy {
        fixture.getJSONArray("labels").objects().map { it.getString("tagId") to floatArrayFromBase64(it.getString("b64")) }
    }

    private val garments: List<Garment> by lazy {
        fixture.getJSONArray("garments").objects().map {
            Garment(it.getString("id"), it.getString("category"), floatArrayFromBase64(it.getString("image_b64")))
        }
    }

    private val categoryLabels get() = labels.filter { it.first.startsWith("category-") }
    private val occasionLabels get() = labels.filter { it.first.startsWith("occasion-") }

    private val expected: JSONObject get() = fixture.getJSONObject("expected")

    @Test
    fun fixtureMatchesTheRecipeAndHasSixGarmentsAndTwentyFiveLabels() {
        assertEquals(EmbeddingRecipe.MODEL_ID, fixture.getString("modelId"))
        assertEquals(EmbeddingRecipe.RECIPE_ID, fixture.getString("recipeId"))
        assertEquals(6, garments.size)
        assertEquals(25, labels.size)
        assertEquals(19, categoryLabels.size)
        assertEquals(6, occasionLabels.size)
    }

    @Test
    fun textModeCategoriesMatchPython() {
        val array = expected.getJSONObject("text").getJSONArray("category")

        for (garment in garments) {
            val (scored, mode) = scoreLabels(garment.image, categoryLabels, emptyMap())
            assertCategory(garment.id, expectedFor(array, garment.id), selectCategory(scored, mode))
        }
    }

    @Test
    fun combinedModeCategoriesMatchPython() {
        val array = expected.getJSONObject("combined").getJSONArray("category")

        for (garment in garments) {
            val (scored, mode) = scoreLabels(garment.image, categoryLabels, examplesExcluding(garment))
            assertCategory(garment.id, expectedFor(array, garment.id), selectCategory(scored, mode))
        }
    }

    @Test
    fun textModeOccasionsMatchPython() {
        val array = expected.getJSONObject("text").getJSONArray("occasion")
        assertOccasions(array)
    }

    @Test
    fun combinedModeOccasionsMatchPython() {
        // The fixture has no occasion examples, so COMBINED never applies to occasions.
        val array = expected.getJSONObject("combined").getJSONArray("occasion")
        assertOccasions(array)
    }

    private fun assertOccasions(array: JSONArray) {
        for (garment in garments) {
            val (scored, mode) = scoreLabels(garment.image, occasionLabels, emptyMap())
            val suggestOccasions = garment.category !in EmbeddingRecipe.SUGGEST_OCCASIONS_DEFAULT_OFF
            val actual = selectOccasions(scored, mode, suggestOccasions)
            val want = expectedFor(array, garment.id)

            assertEquals("mode for ${garment.id}", want.getString("mode"), actual.mode.name)
            assertRanked(garment.id, want.getJSONArray("ranked"), actual.ranked)
            assertEquals("selection for ${garment.id}", stringList(want.getJSONArray("selected")), actual.selected)
            assertOptionalScore("g12 for ${garment.id}", want, "g12", actual.g12)
            assertOptionalScore("g23 for ${garment.id}", want, "g23", actual.g23)
        }
    }

    private fun assertCategory(garmentId: String, want: JSONObject, actual: CategoryResult) {
        assertEquals("mode for $garmentId", want.getString("mode"), actual.mode.name)
        assertRanked(garmentId, want.getJSONArray("ranked"), actual.ranked)
        assertEquals("mark for $garmentId", if (want.isNull("marked")) null else want.getString("marked"), actual.marked)
        assertTrue("margin for $garmentId", abs(actual.margin - want.getDouble("margin")) <= TOLERANCE)
    }

    private fun assertRanked(garmentId: String, wantRanked: JSONArray, actual: List<LabelScore>) {
        val wantIds = List(wantRanked.length()) { wantRanked.getJSONArray(it).getString(0) }
        assertEquals("rank order for $garmentId", wantIds, actual.map(LabelScore::tagId))
        actual.forEachIndexed { index, label ->
            val wantScore = wantRanked.getJSONArray(index).getDouble(1)
            assertTrue(
                "score for $garmentId/${label.tagId}: ${label.score} vs $wantScore",
                abs(label.score - wantScore) <= TOLERANCE,
            )
        }
    }

    private fun assertOptionalScore(message: String, want: JSONObject, key: String, actual: Float?) {
        if (want.isNull(key)) {
            assertNull(message, actual)
        } else {
            assertTrue(message, actual != null && abs(actual - want.getDouble(key)) <= TOLERANCE)
        }
    }

    /** Other fixture garments are each other's examples, grouped by category; the garment itself is excluded. */
    private fun examplesExcluding(garment: Garment): Map<String, List<FloatArray>> =
        garments.filter { it.id != garment.id }.groupBy({ it.category }, { it.image })

    private data class Garment(val id: String, val category: String, val image: FloatArray)

    private companion object {
        const val TOLERANCE = 1e-4

        fun expectedFor(array: JSONArray, garmentId: String): JSONObject =
            array.objects().single { it.getString("garment") == garmentId }

        fun stringList(array: JSONArray): List<String> = List(array.length()) { array.getString(it) }

        fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    }
}
