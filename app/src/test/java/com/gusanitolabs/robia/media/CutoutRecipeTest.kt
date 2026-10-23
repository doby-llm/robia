package com.gusanitolabs.robia.media

import com.gusanitolabs.robia.core.model.CUTOUT_RECIPE_VERSION
import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.core.model.CutoutRecipe
import com.gusanitolabs.robia.media.cutout.CropRect
import com.gusanitolabs.robia.media.cutout.alphaCropRect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CutoutRecipeTest {
    private val precise = CutoutRecipe(
        engine = "precise",
        removeHanger = true,
        hangerThreshold = 0.25f,
        edges = CutoutEdges.Sharp,
        workW = 1024,
        workH = 768,
        rotationDegrees = 90,
        sampleSize = 2,
        cropLeft = 16,
        cropTop = 26,
        cropRight = 1000,
        cropBottom = 700,
    )

    @Test
    fun jsonRoundTripKeepsEveryField() {
        assertEquals(precise, CutoutRecipe.fromJson(precise.toJson()))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val withFutureKeys = JSONObject(precise.toJson()).put("futureField", "later").put("nested", JSONObject().put("x", 1))

        assertEquals(precise, CutoutRecipe.fromJson(withFutureKeys.toString()))
    }

    @Test
    fun missingOrUnreadableRecipeIsNull() {
        assertNull(CutoutRecipe.fromJson(null))
        assertNull(CutoutRecipe.fromJson(""))
        assertNull(CutoutRecipe.fromJson("{}"))
        assertNull(CutoutRecipe.fromJson("not json"))
        assertNull(CutoutRecipe.fromJson(JSONObject(precise.toJson()).put("edges", "Unknown").toString()))
    }

    @Test
    fun recipeFromLaterVersionIsNull() {
        val later = JSONObject(precise.toJson()).put("version", CUTOUT_RECIPE_VERSION + 1)

        assertNull(CutoutRecipe.fromJson(later.toString()))
    }

    @Test
    fun preciseCropRectIsTheRectCutToAlpha() {
        // Foreground is alpha above 24 in x 20..60, y 30..70 of a 100x100 frame; the 4 % pad is 4 px.
        val width = 100
        val height = 100
        val alpha = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val inside = x in 20..60 && y in 30..70
            alpha[y * width + x] = (if (inside) 200 else 0).toByte()
        }
        alpha[0] = 24 // at the threshold, so not foreground and not part of the crop

        assertEquals(CropRect(left = 16, top = 26, right = 64, bottom = 74), alphaCropRect(alpha, width, height))
    }

    @Test
    fun mlKitRecipeHasTheFullFrameAndPowerOfTwoSample() {
        val sampleSize = segmentationSampleSize(4032, 3024)

        val recipe = mlKitRecipe(
            edges = CutoutEdges.Soft,
            width = 4032 / sampleSize,
            height = 3024 / sampleSize,
            rotationDegrees = 90,
            sampleSize = sampleSize,
        )

        assertEquals(2, sampleSize)
        assertEquals("mlkit", recipe.engine)
        assertEquals(listOf(0, 0, 2016, 1512), listOf(recipe.cropLeft, recipe.cropTop, recipe.cropRight, recipe.cropBottom))
        assertEquals(listOf(2016, 1512), listOf(recipe.workW, recipe.workH))
        assertEquals(2, recipe.sampleSize)
    }

    @Test
    fun segmentationSampleSizeKeepsTheLongEdgeAtMost2048() {
        assertEquals(1, segmentationSampleSize(1024, 768))
        assertEquals(1, segmentationSampleSize(2048, 1536))
        assertEquals(2, segmentationSampleSize(4032, 3024))
        assertEquals(4, segmentationSampleSize(8000, 6000))
    }
}
