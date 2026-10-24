package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitComposerTest {
    private fun tag(id: String, group: String) = GarmentTag(id = id, categoryId = group, name = id, sortOrder = 0)

    private fun item(id: String, category: String, vararg tags: GarmentTag, colour: String? = null) = ClothingItem(
        id = id,
        name = id,
        tags = listOf(tag(category, "category")) + tags,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        colorMetrics = ClothingColorMetrics(primaryPaletteColorId = colour),
    )

    private fun vectors(vararg entries: Pair<String, Pair<FloatArray?, FloatArray?>>): Map<String, GarmentVectors> =
        entries.associate { (id, pair) -> id to GarmentVectors(garmentId = id, image = pair.first, text = pair.second) }

    private val summer = tag("season-summer", "season")
    private val everyday = tag("occasion-everyday", "occasion")
    private val fall = tag("season-fall", "season")

    @Test
    fun dressSeedExcludesTopAndBottom() {
        val items = listOf(
            item("dress", "category-dresses", colour = "red"),
            item("top", "category-tops"),
            item("bottom", "category-trousers"),
            item("shoes", "category-shoes"),
        )
        val vectors = vectors(
            "dress" to (floatArrayOf(1f, 0f, 0f) to null),
            "top" to (floatArrayOf(0f, 1f, 0f) to null),
            "bottom" to (floatArrayOf(0f, 0f, 1f) to null),
            "shoes" to (floatArrayOf(1f, 1f, 0f) to null),
        )

        val outfits = OutfitComposer.compose("dress", null, null, null, items, vectors)

        assertTrue(outfits.isNotEmpty())
        outfits.forEach { outfit ->
            assertEquals("dress", outfit.garmentIds[OutfitSlot.DRESS])
            assertFalse(OutfitSlot.TOP in outfit.garmentIds)
            assertFalse(OutfitSlot.BOTTOM in outfit.garmentIds)
        }
    }

    @Test
    fun outerwearIsProposedOnlyForFallOrWinter() {
        val items = listOf(
            item("top", "category-tops", summer, everyday),
            item("bottom", "category-trousers", summer, everyday),
            item("jacket", "category-jackets", summer, everyday),
            item("fallTop", "category-tops", fall),
            item("fallBottom", "category-trousers", fall),
            item("fallJacket", "category-jackets", fall),
        )
        val vectors = vectors(
            "top" to (floatArrayOf(1f, 0f, 0f) to null),
            "bottom" to (floatArrayOf(0f, 1f, 0f) to null),
            "jacket" to (floatArrayOf(0f, 0f, 1f) to null),
            "fallTop" to (floatArrayOf(1f, 0f, 0f) to null),
            "fallBottom" to (floatArrayOf(0f, 1f, 0f) to null),
            "fallJacket" to (floatArrayOf(0f, 0f, 1f) to null),
        )

        val summerOutfits = OutfitComposer.compose(null, null, "season-summer", null, items, vectors)
        val fallOutfits = OutfitComposer.compose(null, null, "season-fall", null, items, vectors)

        assertTrue(summerOutfits.isNotEmpty())
        summerOutfits.forEach { outfit -> assertFalse(OutfitSlot.OUTER in outfit.garmentIds) }
        assertTrue(fallOutfits.any { outfit -> OutfitSlot.OUTER in outfit.garmentIds })
    }

    @Test
    fun seasonAndOccasionFiltersKeepOnlyMatchingPieces() {
        val winterTop = tag("season-winter", "season")
        val items = listOf(
            item("top1", "category-tops", summer, everyday),
            item("top2", "category-tops", winterTop, everyday),
            item("bottom1", "category-trousers", summer, everyday),
            item("shoes", "category-shoes", summer, everyday),
        )
        val vectors = vectors(
            "top1" to (floatArrayOf(1f, 0f, 0f) to null),
            "top2" to (floatArrayOf(1f, 0.1f, 0f) to null),
            "bottom1" to (floatArrayOf(0f, 1f, 0f) to null),
            "shoes" to (floatArrayOf(0f, 0f, 1f) to null),
        )

        val outfits = OutfitComposer.compose(null, null, "season-summer", "occasion-everyday", items, vectors)

        assertTrue(outfits.isNotEmpty())
        outfits.forEach { outfit -> assertFalse("top2" in outfit.garmentIds.values) }
        assertTrue(outfits.first().sharedTagIds.containsAll(listOf("season-summer", "occasion-everyday")))
    }

    @Test
    fun outfitsDifferInAtLeastOneSlotAndAreDeterministic() {
        val items = listOf(
            item("top1", "category-tops"),
            item("top2", "category-tops"),
            item("top3", "category-tops"),
            item("bottom1", "category-trousers"),
            item("bottom2", "category-trousers"),
        )
        val vectors = vectors(
            "top1" to (floatArrayOf(1f, 0f, 0f) to null),
            "top2" to (floatArrayOf(0.9f, 0.1f, 0f) to null),
            "top3" to (floatArrayOf(0.8f, 0.2f, 0f) to null),
            "bottom1" to (floatArrayOf(0f, 1f, 0f) to null),
            "bottom2" to (floatArrayOf(0f, 0.9f, 0.1f) to null),
        )

        val first = OutfitComposer.compose(null, null, null, null, items, vectors)
        val second = OutfitComposer.compose(null, null, null, null, items, vectors)

        assertEquals(3, first.size)
        assertEquals(first, second)
        assertEquals(first.size, first.map(Outfit::garmentIds).toSet().size)
    }

    @Test
    fun rankingUsesMeanImageCosineToChosenPieces() {
        val items = listOf(
            item("top", "category-tops"),
            item("closeBottom", "category-trousers"),
            item("farBottom", "category-trousers"),
        )
        val vectors = vectors(
            "top" to (floatArrayOf(1f, 0f, 0f) to null),
            "closeBottom" to (floatArrayOf(1f, 0.1f, 0f) to null),
            "farBottom" to (floatArrayOf(0f, 1f, 0f) to null),
        )

        val outfit = OutfitComposer.compose("top", null, null, null, items, vectors, n = 1).single()

        assertEquals("closeBottom", outfit.garmentIds[OutfitSlot.BOTTOM])
    }

    @Test
    fun promptTextCosineBreaksImageTies() {
        val items = listOf(
            item("top", "category-tops"),
            item("bottomA", "category-trousers"),
            item("bottomB", "category-trousers"),
        )
        val vectors = vectors(
            "top" to (floatArrayOf(1f, 0f, 0f) to null),
            "bottomA" to (floatArrayOf(0f, 1f, 0f) to floatArrayOf(1f, 0f, 0f)),
            "bottomB" to (floatArrayOf(0f, 1f, 0f) to floatArrayOf(0f, 1f, 0f)),
        )

        val outfit = OutfitComposer.compose("top", floatArrayOf(0f, 1f, 0f), null, null, items, vectors, n = 1).single()

        assertEquals("bottomB", outfit.garmentIds[OutfitSlot.BOTTOM])
    }

    @Test
    fun garmentsWithoutImageVectorAreNeverProposed() {
        val items = listOf(
            item("top", "category-tops"),
            item("noImageBottom", "category-trousers"),
            item("bottom", "category-trousers"),
        )
        val vectors = vectors(
            "top" to (floatArrayOf(1f, 0f, 0f) to null),
            "noImageBottom" to (null to floatArrayOf(1f, 0f, 0f)),
            "bottom" to (floatArrayOf(0f, 1f, 0f) to null),
        )

        val outfits = OutfitComposer.compose(null, null, null, null, items, vectors)

        assertTrue(outfits.isNotEmpty())
        outfits.forEach { outfit -> assertFalse("noImageBottom" in outfit.garmentIds.values) }
    }

    @Test
    fun unknownSeedGivesNoOutfits() {
        val items = listOf(item("top", "category-tops"), item("bottom", "category-trousers"))
        val vectors = vectors(
            "top" to (floatArrayOf(1f, 0f, 0f) to null),
            "bottom" to (floatArrayOf(0f, 1f, 0f) to null),
        )

        assertTrue(OutfitComposer.compose("missing", null, null, null, items, vectors).isEmpty())
    }

    @Test
    fun ratingLineHoldsIdsAndRatingOnly() {
        val outfit = Outfit(
            garmentIds = mapOf(OutfitSlot.TOP to "top", OutfitSlot.BOTTOM to "bottom"),
            sharedTagIds = listOf("season-summer"),
            similarColours = false,
        )

        val json = JSONObject(outfitRatingLine(1_000L, outfit, "top", "season-summer", null, thumbsUp = true))

        assertEquals(1_000L, json.getLong("at"))
        assertEquals("top", json.getString("seed"))
        assertEquals("season-summer", json.getString("season"))
        assertTrue(json.isNull("occasion"))
        assertEquals("top", json.getJSONObject("items").getString("top"))
        assertEquals("bottom", json.getJSONObject("items").getString("bottom"))
        assertEquals("up", json.getString("rating"))
        assertFalse(json.has("prompt"))
    }
}
