package com.gusanitolabs.robia.media.additionalinfo

import com.gusanitolabs.robia.core.model.DefaultTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalInfoModelContractTest {
    @Test
    fun manifestDeclaresExpectedModelContract() {
        val manifest = readManifest()

        assertTrue(manifest.contains("\"shape\": [1, 224, 224, 3]"))
        assertTrue(manifest.contains("\"name\": \"category\""))
        assertTrue(manifest.contains("\"shape\": [1, 19]"))
        assertTrue(manifest.contains("\"name\": \"occasion\""))
        assertTrue(manifest.contains("\"shape\": [1, 6]"))
        assertTrue(manifest.contains("\"name\": \"season\""))
        assertTrue(manifest.contains("\"shape\": [1, 5]"))
        assertTrue(manifest.contains("\"normalization\""))
        assertTrue(manifest.contains("\"raw_rgb_0_255_embedded_mobilenet_v3_preprocess_input\""))
        assertTrue(manifest.contains("\"formula\": \"rgb\""))
        assertTrue(manifest.contains("\"resizeStrategy\": \"square_pad_preserve_aspect_then_resize_224\""))
        assertTrue(manifest.contains("do not apply external [-1,1] normalization"))
    }

    @Test
    fun manifestLabelsMatchTrainingOrder() {
        val manifest = readManifest()

        assertEquals(
            listOf("Shorts", "Jackets", "Jumpsuits", "Blouses", "Dresses", "Skirts", "Blazers", "Cardigans", "Bags", "Tops", "Knitwear", "Trousers", "Sweaters", "Shoes", "Shirts", "Vests", "Jewelry", "Accessories", "Coats"),
            labelsFor(manifest, "category"),
        )
        assertEquals(
            listOf("Active", "Statement", "Dressed-up", "Formal", "Everyday", "Business"),
            labelsFor(manifest, "occasion"),
        )
        assertEquals(
            listOf("Spring", "Summer", "Fall", "Winter", "Multi Season"),
            labelsFor(manifest, "season"),
        )
    }

    @Test
    fun deterministicNoiseBaselineIsPinnedForDriftReview() {
        val manifest = readManifest()

        assertTrue(manifest.contains("raw RGB float32 [0,255]"))
        assertTrue(manifest.contains("\"argmax\": 16"))
        assertTrue(manifest.contains("\"maxScore\": 0.73013633"))
        assertTrue(manifest.contains("\"argmax\": 2"))
        assertTrue(manifest.contains("\"maxScore\": 0.9040952"))
        assertTrue(manifest.contains("\"argmax\": 4"))
        assertTrue(manifest.contains("\"maxScore\": 0.48142487"))
    }

    @Test
    fun localImageEvidenceDocumentsRawContractResult() {
        val manifest = readManifest()

        assertTrue(manifest.contains("\"image\": \"image_nn.png\""))
        assertTrue(manifest.contains("\"label\": \"Shirts\""))
        assertTrue(manifest.contains("\"label\": \"Tops\""))
        assertFalse(manifest.contains("Coats 0.989"))
    }

    @Test
    fun modelAssetPathUsesManifestModelFileInsideAdditionalInfoDirectory() {
        assertEquals(
            "additional_info/replacement-model_01.tflite",
            AdditionalInfoModelAssets.modelAssetPath("replacement-model_01.tflite"),
        )
    }

    @Test
    fun unsafeManifestModelFileFragmentsAreRejected() {
        listOf(
            "../mobilenet_v3_large.tflite",
            "nested/mobilenet_v3_large.tflite",
            "nested\\mobilenet_v3_large.tflite",
            ".hidden.tflite",
            "mobilenet_v3_large.pb",
        ).forEach { modelFile ->
            assertFalse(
                "Expected unsafe modelFile to be rejected: $modelFile",
                AdditionalInfoModelAssets.isSafeModelFile(modelFile),
            )
        }
    }

    @Test
    fun multiSeasonIsNeverExposedAsDefaultManageTag() {
        assertFalse(DefaultTags.tags.any { tag -> tag.name == "Multi Season" || tag.id.contains("multi", ignoreCase = true) })
    }

    @Test
    fun everyNonMultiSeasonManifestLabelMapsToDefaultTag() {
        val manifest = readManifest()
        val defaultTagIds = DefaultTags.tags.map { tag -> tag.id }.toSet()
        val missing = listOf("category", "occasion", "season").flatMap { head ->
            labelsFor(manifest, head).zip(tagIdsFor(manifest, head)).mapNotNull { (label, tagId) ->
                when {
                    label == "Multi Season" -> null
                    tagId == null || tagId !in defaultTagIds -> "$head:$label"
                    else -> null
                }
            }
        }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun modelOutputTagIdsMatchManifestIncludingMultiSeasonExpansion() {
        val manifest = readManifest()
        val config = AdditionalInfoModelManifest.parse(manifest)
        val expectedTagIds = listOf("category", "occasion", "season")
            .flatMap { head -> tagIdsFor(manifest, head).filterNotNull() }
            .toSet() + setOf("season-spring", "season-summer", "season-fall", "season-winter")

        assertEquals(expectedTagIds, config.modelOutputTagIds())
        assertEquals(expectedTagIds, AdditionalInfoModelOutputTags.ids)
        assertFalse(AdditionalInfoModelOutputTags.ids.contains("location-main-closet"))
    }

    private fun readManifest(): String = java.io.File("src/main/assets/additional_info/mobilenet_v3_large.json").readText()

    private fun labelsFor(manifest: String, headName: String): List<String> {
        val headStart = manifest.indexOf("\"name\": \"$headName\"")
        check(headStart >= 0) { "Missing head $headName" }
        val labelsStart = manifest.indexOf("\"labels\": [", headStart)
        val labelsEnd = manifest.indexOf(']', labelsStart)
        return manifest.substring(labelsStart, labelsEnd)
            .substringAfter('[')
            .split(',')
            .map { value -> value.trim().trim('"') }
            .filter(String::isNotBlank)
    }

    private fun tagIdsFor(manifest: String, headName: String): List<String?> {
        val headStart = manifest.indexOf("\"name\": \"$headName\"")
        check(headStart >= 0) { "Missing head $headName" }
        val tagIdsStart = manifest.indexOf("\"tagIds\": [", headStart)
        val tagIdsEnd = manifest.indexOf(']', tagIdsStart)
        return manifest.substring(tagIdsStart, tagIdsEnd)
            .substringAfter('[')
            .split(',')
            .map { value -> value.trim().trim('"') }
            .filter(String::isNotBlank)
            .map { value -> value.takeUnless { it == "null" } }
    }
}
