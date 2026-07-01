package com.gusanitolabs.robia.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayedImageTransformTest {
    @Test
    fun wideImageFitCenterMapsLetterboxedTapToNormalizedPoint() {
        val transform = DisplayedImageTransform(
            imageWidth = 400,
            imageHeight = 200,
            containerWidth = 300f,
            containerHeight = 300f,
        )

        assertEquals(0f, transform.left, 0.001f)
        assertEquals(75f, transform.top, 0.001f)
        assertEquals(300f, transform.contentWidth, 0.001f)
        assertEquals(150f, transform.contentHeight, 0.001f)
        assertNull(transform.toNormalizedPoint(150f, 40f))

        val point = transform.toNormalizedPoint(150f, 150f)
        requireNotNull(point)
        assertEquals(0.5f, point.x, 0.001f)
        assertEquals(0.5f, point.y, 0.001f)
    }

    @Test
    fun tallImageFitCenterMapsSidePaddingToNull() {
        val transform = DisplayedImageTransform(
            imageWidth = 200,
            imageHeight = 400,
            containerWidth = 300f,
            containerHeight = 300f,
        )

        assertEquals(75f, transform.left, 0.001f)
        assertEquals(0f, transform.top, 0.001f)
        assertEquals(150f, transform.contentWidth, 0.001f)
        assertEquals(300f, transform.contentHeight, 0.001f)
        assertNull(transform.toNormalizedPoint(40f, 150f))

        val point = transform.toNormalizedPoint(150f, 225f)
        requireNotNull(point)
        assertEquals(0.5f, point.x, 0.001f)
        assertEquals(0.75f, point.y, 0.001f)
    }
}
