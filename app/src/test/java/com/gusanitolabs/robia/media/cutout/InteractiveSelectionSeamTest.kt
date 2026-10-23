package com.gusanitolabs.robia.media.cutout

import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * D1 (R3-DEC-021), JVM part: with a fake segmenter, the tap reaches the segmenter normalised and the first returned mask is the
 * selection, for one mask and for two. Which masks a real tap returns is still answered only on device (G-ADB2).
 */
class InteractiveSelectionSeamTest {
    private val width = 4
    private val height = 3
    private val mask0 = FloatArray(width * height) { it / 12f }
    private val mask1 = FloatArray(width * height) { 1f - it / 12f }

    private fun softMask(values: FloatArray): MPImage {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        values.forEach { buffer.putFloat(it) }
        buffer.rewind()
        return ByteBufferImageBuilder(buffer, width, height, MPImage.IMAGE_FORMAT_VEC32F1).build()
    }

    @Test
    fun keypointReachesSegmenterUnchanged() {
        val seen = mutableListOf<NormalizedKeypoint>()

        selectWholeObject(width, height, 1, 2) { keypoint ->
            seen += keypoint
            listOf(softMask(mask0))
        }

        assertEquals(1, seen.size)
        assertEquals(1f / width, seen.single().x(), 0f)
        assertEquals(2f / height, seen.single().y(), 0f)
    }

    @Test
    fun oneMask_itsValuesAreTheSelection() {
        val selected = selectWholeObject(width, height, 1, 2) { listOf(softMask(mask0)) }

        assertArrayEquals(mask0, selected, 0f)
    }

    @Test
    fun twoMasks_theFirstIsTheSelectionAndTheSecondIsIgnored() {
        val selected = selectWholeObject(width, height, 1, 2) { listOf(softMask(mask0), softMask(mask1)) }

        assertArrayEquals(mask0, selected, 0f)
    }
}
