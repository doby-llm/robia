package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.GarmentVectors
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankerTest {
    private val query = floatArrayOf(1f, 0f, 0f, 0f)
    private val names = mapOf("A" to "Vestido", "B" to "Falda", "C" to "Pantalón", "D" to "Bolso", "E" to "Abrigo")

    @Test
    fun imageAndTextAreWeightedZeroPointSevenAndZeroPointThree() {
        val vectors = mapOf(
            // 0.7 * cos(1, 1) + 0.3 * cos(1, 0) = 0.7
            "A" to GarmentVectors("A", image = floatArrayOf(1f, 0f, 0f, 0f), text = floatArrayOf(0f, 1f, 0f, 0f)),
            // 0.7 * 0.5 + 0.3 * 1 = 0.65: the text term lifts B above C
            "B" to GarmentVectors("B", image = floatArrayOf(0.5f, 0.86603f, 0f, 0f), text = floatArrayOf(1f, 0f, 0f, 0f)),
            // no TEXT vector: image cosine only, 0.6
            "C" to GarmentVectors("C", image = floatArrayOf(0.6f, 0.8f, 0f, 0f), text = null),
        )

        assertEquals(listOf("A", "B", "C"), rankByQuery(listOf("A", "B", "C"), query, vectors) { names.getValue(it) })
    }

    @Test
    fun itemsWithoutImageComeLastOrderedByName() {
        val vectors = mapOf(
            "A" to GarmentVectors("A", image = floatArrayOf(0.6f, 0.8f, 0f, 0f), text = null),
            // text only: no IMAGE vector, so it goes after every scored item
            "D" to GarmentVectors("D", image = null, text = floatArrayOf(1f, 0f, 0f, 0f)),
            // "E" has no vectors at all
        )

        // "Abrigo" (E) sorts before "Bolso" (D)
        assertEquals(listOf("A", "E", "D"), rankByQuery(listOf("D", "E", "A"), query, vectors) { names.getValue(it) })
    }

    @Test
    fun similarGarmentsExcludeSelfAndRankByImageCosine() {
        val vectors = mapOf(
            "self" to GarmentVectors("self", image = floatArrayOf(1f, 0f, 0f, 0f), text = null),
            "X" to GarmentVectors("X", image = floatArrayOf(0.9f, 0.43589f, 0f, 0f), text = null),
            "Y" to GarmentVectors("Y", image = floatArrayOf(0.2f, 0.9798f, 0f, 0f), text = null),
            "Z" to GarmentVectors("Z", image = null, text = null),
        )

        assertEquals(listOf("X", "Y", "Z"), rankSimilar("self", listOf("self", "Y", "Z", "X"), vectors))
    }

    @Test
    fun similarGarmentsNeedAnImageForTheSelfGarment() {
        val vectors = mapOf("X" to GarmentVectors("X", image = floatArrayOf(1f, 0f, 0f, 0f), text = null))

        assertEquals(emptyList<String>(), rankSimilar("self", listOf("X"), vectors))
    }

    @Test
    fun textFallbackIgnoresAccentsAndCaseAndNeedsEveryWord() {
        val items = listOf(
            item("1", "Vestido Rojo", "Lino"),
            item("2", "Falda negra", "con volantes"),
            item("3", "Cinturón de piel", ""),
        )

        assertEquals(listOf("1"), textFallback(items, "ROJÓ").map { it.id })
        assertEquals(listOf("1"), textFallback(items, "vestido rojo").map { it.id })
        assertEquals(listOf("3"), textFallback(items, "cinturon").map { it.id })
        assertEquals(listOf("1"), textFallback(items, "lino").map { it.id })
        assertEquals(emptyList<String>(), textFallback(items, "vestido negra").map { it.id })
    }

    private fun item(id: String, name: String, notes: String = "") = ClothingItem(
        id = id,
        name = name,
        notes = notes,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )
}
