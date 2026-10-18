package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG3-1: brush radius scaling and the soft disc falloff of the Cutout brush. */
class BrushStrokeTest {
    private val size = 40

    @Test
    fun brushRadius_scalesWithDensityAndInverselyWithZoom() {
        assertEquals(24f, brushRadiusPx(24f, density = 2f, zoom = 2f), 1e-4f)
        assertEquals(72f, brushRadiusPx(24f, density = 3f, zoom = 1f), 1e-4f)
        assertEquals(12f, brushRadiusPx(24f, density = 2f, zoom = 4f), 1e-4f)
    }

    @Test
    fun softEdge_isFullAtTheCentre_andZeroBeyondRadiusPlusFeather() {
        val mask = brushMask(listOf(20f to 20f), radiusPx = 6f, featherPx = 4f, w = size, h = size)

        assertEquals(1f, mask[20 * size + 20], 0f)
        val inFeather = mask[20 * size + 28]
        assertTrue("pixel in the feather band should be partial, was $inFeather", inFeather > 0f && inFeather < 1f)
        assertEquals(0f, mask[20 * size + 31], 0f)
    }

    @Test
    fun hardEdge_withZeroFeather_isOneInsideRadiusAndZeroOutside() {
        val mask = brushMask(listOf(20f to 20f), radiusPx = 6f, featherPx = 0f, w = size, h = size)

        assertEquals(1f, mask[20 * size + 20], 0f)
        assertEquals(1f, mask[20 * size + 24], 0f)
        assertEquals(0f, mask[20 * size + 27], 0f)
    }

    @Test
    fun fastDrag_leavesNoGapsBetweenSamples() {
        val mask = brushMask(listOf(5f to 5f, 35f to 5f), radiusPx = 2f, featherPx = 0f, w = size, h = size)

        for (x in 5..35) {
            assertEquals("gap at x=$x", 1f, mask[5 * size + x], 0f)
        }
    }

    @Test
    fun overlappingStamps_doNotExceedOne() {
        val mask = brushMask(listOf(10f to 10f, 12f to 10f, 14f to 10f), radiusPx = 5f, featherPx = 3f, w = size, h = size)

        assertTrue(mask.all { it in 0f..1f })
    }

    @Test
    fun noPoints_giveAnEmptyMask() {
        val mask = brushMask(emptyList(), radiusPx = 5f, featherPx = 3f, w = size, h = size)

        assertTrue(mask.all { it == 0f })
    }
}
