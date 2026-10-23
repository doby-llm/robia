package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** REQ-BR3-05: drag-select range (V-SEL-1) and edge autoscroll (V-SEL-2). Both are pure, so they run on the JVM. */
class DragSelectTest {
    private val ids = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun rangeFollowsTheFingerInBothDirections() {
        val down = DragSelect(anchorId = "b", baseline = emptySet())
        down.moveTo("d")
        assertEquals(setOf("b", "c", "d"), down.selectedIds(ids))

        val up = DragSelect(anchorId = "d", baseline = emptySet())
        up.moveTo("b")
        assertEquals(setOf("b", "c", "d"), up.selectedIds(ids))
    }

    @Test
    fun movingBackShrinksTheRange() {
        val drag = DragSelect(anchorId = "b", baseline = emptySet())
        drag.moveTo("e")
        assertEquals(setOf("b", "c", "d", "e"), drag.selectedIds(ids))
        drag.moveTo("c")
        assertEquals(setOf("b", "c"), drag.selectedIds(ids))
    }

    @Test
    fun itemsSelectedBeforeTheGestureStaySelected() {
        val drag = DragSelect(anchorId = "d", baseline = setOf("a", "f"))
        drag.moveTo("e")
        assertEquals(setOf("a", "f", "d", "e"), drag.selectedIds(ids))
        // The range shrinks back to the anchor; the earlier selection is not dropped.
        drag.moveTo("d")
        assertEquals(setOf("a", "f", "d"), drag.selectedIds(ids))
    }

    @Test
    fun anchorMissingFromTheListKeepsTheBaseline() {
        val drag = DragSelect(anchorId = "zz", baseline = setOf("a"))
        drag.moveTo("c")
        assertEquals(setOf("a"), drag.selectedIds(ids))
    }

    @Test
    fun autoscrollIsZeroOutsideTheEdgeZones() {
        // Viewport 0..1000 px with 64 px edge zones: the middle, and the zone boundaries themselves, do not scroll.
        assertEquals(0.0, step(500f), 0.0)
        assertEquals(0.0, step(64f), 0.0)
        assertEquals(0.0, step(936f), 0.0)
    }

    @Test
    fun autoscrollIsProportionalInsideTheEdgeZones() {
        assertEquals(-12.0, step(32f), 0.001)
        assertEquals(-24.0, step(0f), 0.001)
        assertEquals(12.0, step(968f), 0.001)
        assertEquals(24.0, step(1000f), 0.001)
    }

    @Test
    fun pointerBeyondTheViewportScrollsAtFullSpeed() {
        assertEquals(-24.0, step(-200f), 0.001)
        assertEquals(24.0, step(1200f), 0.001)
    }

    private fun step(pointerY: Float): Double =
        dragAutoscrollStep(pointerY, viewportStart = 0f, viewportEnd = 1000f, edgePx = 64f, maxStepPx = 24f).toDouble()
}
