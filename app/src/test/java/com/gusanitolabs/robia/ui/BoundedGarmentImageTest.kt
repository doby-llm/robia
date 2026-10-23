package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.media.ImageTargetBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedGarmentImageTest {
    @Test
    fun noRequestIsIssuedUntilTheCellHasMeasuredBounds() {
        assertNull(boundedImageTargetBounds(0, 0, maxEdgePx = 384))
        assertNull(boundedImageTargetBounds(219, 0, maxEdgePx = 384))
        assertNull(boundedImageTargetBounds(0, 273, maxEdgePx = 384))
    }

    @Test
    fun firstRequestUsesTheMeasuredCellBoundedToTheGridCap() {
        // A 4:5 cell wider than the cap is scaled down; a smaller cell keeps its own size.
        assertEquals(ImageTargetBounds(307, 384), boundedImageTargetBounds(386, 482, maxEdgePx = 384))
        assertEquals(ImageTargetBounds(219, 273), boundedImageTargetBounds(219, 273, maxEdgePx = 384))
    }
}
