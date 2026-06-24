package com.gusanitolabs.robia.media.additionalinfo

import com.gusanitolabs.robia.core.model.DefaultTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalInfoTagMapperTest {
    private val config = AdditionalInfoModelConfig(
        modelVersion = "test",
        modelFile = "test.tflite",
        input = AdditionalInfoInputSpec("image", listOf(1, 224, 224, 3), "mobilenet_v3_preprocess_input"),
        heads = listOf(
            AdditionalInfoHeadSpec(
                name = "category",
                shape = listOf(1, 3),
                labels = listOf("Shorts", "Jackets", "Shoes"),
                tagIds = listOf("category-shorts", "category-jackets", "category-shoes"),
                threshold = 0.55f,
                margin = 0.15f,
                multiSelectThreshold = null,
                nearTieMargin = null,
                multiSeasonLabel = null,
                multiSeasonTagIds = emptySet(),
            ),
            AdditionalInfoHeadSpec(
                name = "season",
                shape = listOf(1, 5),
                labels = listOf("Spring", "Summer", "Fall", "Winter", "Multi Season"),
                tagIds = listOf("season-spring", "season-summer", "season-fall", "season-winter", null),
                threshold = 0.45f,
                margin = null,
                multiSelectThreshold = 0.40f,
                nearTieMargin = 0.08f,
                multiSeasonLabel = "Multi Season",
                multiSeasonTagIds = setOf("season-spring", "season-summer", "season-fall", "season-winter"),
            ),
            AdditionalInfoHeadSpec(
                name = "occasion",
                shape = listOf(1, 2),
                labels = listOf("Active", "Everyday"),
                tagIds = listOf("occasion-active", "occasion-everyday"),
                threshold = 0.45f,
                margin = null,
                multiSelectThreshold = 0.40f,
                nearTieMargin = 0.08f,
                multiSeasonLabel = null,
                multiSeasonTagIds = emptySet(),
            ),
        ),
        noiseBaseline = emptyMap(),
    )

    @Test
    fun categoryRequiresSingleConfidentWinner() {
        val prediction = map(category = floatArrayOf(0.72f, 0.40f, 0.05f))

        assertTrue("category-shorts" in prediction.selectedTagIds)
    }

    @Test
    fun categoryAmbiguitySelectsNoCategory() {
        val prediction = map(category = floatArrayOf(0.61f, 0.54f, 0.05f))

        assertTrue(prediction.selectedTagIds.none { id -> id.startsWith("category-") })
    }

    @Test
    fun multiSeasonExpandsToFourRealSeasonTags() {
        val prediction = map(season = floatArrayOf(0.05f, 0.07f, 0.08f, 0.10f, 0.70f))

        assertEquals(
            setOf("season-spring", "season-summer", "season-fall", "season-winter"),
            prediction.selectedTagIds.filter { id -> id.startsWith("season-") }.toSet(),
        )
    }

    @Test
    fun nearTieSeasonsCanSelectMultipleRealSeasonTags() {
        val prediction = map(season = floatArrayOf(0.50f, 0.47f, 0.05f, 0.02f, 0.01f))

        assertEquals(
            setOf("season-spring", "season-summer"),
            prediction.selectedTagIds.filter { id -> id.startsWith("season-") }.toSet(),
        )
    }

    @Test
    fun nearTieOccasionsCanSelectMultipleTags() {
        val prediction = map(occasion = floatArrayOf(0.49f, 0.43f))

        assertEquals(
            setOf("occasion-active", "occasion-everyday"),
            prediction.selectedTagIds.filter { id -> id.startsWith("occasion-") }.toSet(),
        )
    }

    @Test
    fun missingMappingsAreReportedForRequiredLabels() {
        val configWithMissingOccasionMapping = config.copy(
            heads = config.heads.map { head ->
                if (head.name == "occasion") {
                    head.copy(tagIds = listOf("occasion-active", "occasion-missing"))
                } else {
                    head
                }
            },
        )

        assertEquals(
            listOf("occasion:Everyday"),
            AdditionalInfoTagMapper.unmappedRequiredLabels(
                configWithMissingOccasionMapping,
                DefaultTags.tags.map { tag -> tag.id }.toSet(),
            ),
        )
    }

    @Test
    fun allManifestLabelsMapToDefaultTagsExceptMultiSeason() {
        assertEquals(
            emptyList<String>(),
            AdditionalInfoTagMapper.unmappedRequiredLabels(config, DefaultTags.tags.map { tag -> tag.id }.toSet()),
        )
    }

    private fun map(
        category: FloatArray = floatArrayOf(0.10f, 0.20f, 0.30f),
        season: FloatArray = floatArrayOf(0.43f, 0.42f, 0.05f, 0.03f, 0.02f),
        occasion: FloatArray = floatArrayOf(0.48f, 0.45f),
    ) = AdditionalInfoTagMapper.map(
        scoresByHead = mapOf(
            "category" to category,
            "season" to season,
            "occasion" to occasion,
        ),
        availableTagIds = DefaultTags.tags.map { tag -> tag.id }.toSet(),
        config = config,
    ) ?: error("Expected prediction")
}
