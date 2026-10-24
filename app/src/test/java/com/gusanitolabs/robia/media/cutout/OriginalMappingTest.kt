package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CanvasOffset
import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.core.model.CutoutRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-OR3-1: the cutout pixel → original pixel mapping (REQ-OR-05), and the recipe's JSON with the stored-canvas offset. */
class OriginalMappingTest {
    /** A 10×10 work frame, cut to a 6×4 canvas that starts at (2, 3). */
    private fun recipe(rotationDegrees: Int = 0, offset: CanvasOffset? = CanvasOffset(2, 3)) = CutoutRecipe(
        engine = "precise",
        removeHanger = true,
        hangerThreshold = 0.5f,
        edges = CutoutEdges.Soft,
        workW = 10,
        workH = 10,
        rotationDegrees = rotationDegrees,
        sampleSize = 1,
        cropLeft = 2,
        cropTop = 3,
        cropRight = 8,
        cropBottom = 7,
        storedCanvasOffset = offset,
    )

    @Test
    fun sameSizeFrames_mapCornersAndCentreExactly_withOrWithoutRotation() {
        for (rotation in listOf(0, 90)) {
            val mapping = OriginalMapping(recipe(rotationDegrees = rotation), originalW = 10, originalH = 10)

            assertEquals(3 * 10 + 2, mapping.originalIndex(0, 0))
            assertEquals(3 * 10 + 7, mapping.originalIndex(5, 0))
            assertEquals(6 * 10 + 2, mapping.originalIndex(0, 3))
            assertEquals(6 * 10 + 7, mapping.originalIndex(5, 3))
            assertEquals(4 * 10 + 4, mapping.originalIndex(2, 1))
        }
    }

    @Test
    fun pixelsOutsideTheWorkFrame_mapOutsideTheOriginal() {
        val mapping = OriginalMapping(recipe(), originalW = 10, originalH = 10)
        // Canvas x = 8 is work x = 10, one past the frame; the same for y.
        assertEquals(-1, mapping.originalIndex(8, 0))
        assertEquals(-1, mapping.originalIndex(0, 7))
    }

    @Test
    fun noOffset_isRejectedBeforeAnyMapping() {
        val failure = runCatching { OriginalMapping(recipe(offset = null), 10, 10) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun differentOriginalSize_scalesTheWorkFrameToTheOriginal() {
        // The work frame is 10×10 and the original 20×20: each work pixel covers two original pixels, and the centre is taken.
        val mapping = OriginalMapping(recipe().copy(storedCanvasOffset = CanvasOffset(0, 0)), originalW = 20, originalH = 20)
        assertEquals(1 * 20 + 1, mapping.originalIndex(0, 0))
        assertEquals(19 * 20 + 19, mapping.originalIndex(9, 9))
    }

    @Test
    fun originalLayer_takesTheOriginalColour_andIsOpaqueOnlyInsideTheOriginal() {
        val original = IntArray(10 * 10) { i -> (0x10 shl 24) or (i + 1) }
        val layer = originalLayer(original, 10, 10, canvasW = 6, canvasH = 4, recipe = recipe())

        assertEquals((0xFF shl 24) or (3 * 10 + 2 + 1), layer[0])
        assertEquals((0xFF shl 24) or (6 * 10 + 7 + 1), layer[3 * 6 + 5])
        // Offset (5, 5): canvas x = 0 is inside the frame, canvas x = 5 is work x = 10, one past it.
        val shifted = originalLayer(original, 10, 10, canvasW = 6, canvasH = 4, recipe = recipe(offset = CanvasOffset(5, 5)))
        assertEquals((0xFF shl 24) or (5 * 10 + 5 + 1), shifted[0])
        assertEquals(0, shifted[5])
    }

    @Test
    fun engineCanvas_addsTheEngineCropToTheTrim_andTrimmedBy_shiftsTheOffset() {
        val crop = recipe().copy(storedCanvasOffset = null)

        val imported = crop.atEngineCanvas(CanvasOffset(1, 4))
        assertEquals(CanvasOffset(3, 7), imported.storedCanvasOffset)
        assertEquals(CanvasOffset(5, 9), imported.trimmedBy(CanvasOffset(2, 2)).storedCanvasOffset)
        assertNull(crop.trimmedBy(CanvasOffset(2, 2)).storedCanvasOffset)
    }

    @Test
    fun recipeJson_roundTripsTheOffset_andReadsOldRecipesAsNoOffset() {
        val withOffset = recipe(offset = CanvasOffset(4, 5))
        assertEquals(CanvasOffset(4, 5), CutoutRecipe.fromJson(withOffset.toJson())?.storedCanvasOffset)

        val withoutOffset = recipe(offset = null)
        assertTrue(!withoutOffset.toJson().contains("storedCanvasOffset"))
        assertNull(CutoutRecipe.fromJson(withoutOffset.toJson())?.storedCanvasOffset)
    }

    /** V-OR3-3 (REQ-OR-02 parity): the crop of a stored recipe, re-run on the stored frame, gives the stored size and alpha box. */
    @Test
    fun reRunningTheStoredCrop_onTheStoredFrame_givesTheStoredCanvas_andItsAlphaBox() {
        val w = 40
        val h = 30
        val alpha = ByteArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 10..25 && y in 8..20) 255.toByte() else 0
        }
        val rect = alphaCropRect(alpha, w, h)
        val stored = recipe().copy(
            workW = w,
            workH = h,
            cropLeft = rect.left,
            cropTop = rect.top,
            cropRight = rect.right,
            cropBottom = rect.bottom,
            storedCanvasOffset = CanvasOffset(rect.left, rect.top),
        )

        val again = alphaCropRect(alpha, stored.workW, stored.workH)
        assertEquals(CropRect(stored.cropLeft, stored.cropTop, stored.cropRight, stored.cropBottom), again)
        assertEquals(rect.width, again.width)
        assertEquals(rect.height, again.height)

        // The alpha box found in the stored canvas, moved by the stored offset, is the alpha box in the original frame.
        val offset = checkNotNull(stored.storedCanvasOffset)
        val canvasBox = alphaBox(alpha, w, rect.left, rect.top, rect.width, rect.height)
        val originalBox = alphaBox(alpha, w, 0, 0, w, h)
        assertEquals(originalBox.first, canvasBox.first + offset.left)
        assertEquals(originalBox.second, canvasBox.second + offset.top)
    }

    /** The top-left of the alpha box (alpha above 24) of a [cw]×[ch] window at ([left], [top]) of a [w]-wide frame. */
    private fun alphaBox(alpha: ByteArray, w: Int, left: Int, top: Int, cw: Int, ch: Int): Pair<Int, Int> {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        for (y in top until top + ch) {
            for (x in left until left + cw) {
                if ((alpha[y * w + x].toInt() and 0xFF) > 24) {
                    minX = minOf(minX, x - left)
                    minY = minOf(minY, y - top)
                }
            }
        }
        return minX to minY
    }
}
