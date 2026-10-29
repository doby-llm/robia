package com.gusanitolabs.robia.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's screen ↔ photo mapping. The preview is ContentScale.Fit inside the same box that takes the taps, then scaled about
 * the box centre and panned. [photoToScreen] writes that forward transform out on its own, so the round trip checks the inverse
 * against it rather than against itself.
 */
class PhotoEditorMappingTest {
    private val tolerance = 1e-3

    private fun photoToScreen(photo: Offset, container: IntSize, zoom: Float, pan: Offset, photoWidth: Int, photoHeight: Int): Offset {
        val fit = fitRect(container, photoWidth, photoHeight)
        val perPixel = fit.width / photoWidth
        val fitted = Offset(fit.left + photo.x * perPixel, fit.top + photo.y * perPixel)
        val center = Offset(container.width / 2f, container.height / 2f)
        return center + (fitted - center) * zoom + pan
    }

    private fun assertPointNear(expected: Offset, actual: Offset) {
        assertEquals("x of $expected", expected.x.toDouble(), actual.x.toDouble(), tolerance)
        assertEquals("y of $expected", expected.y.toDouble(), actual.y.toDouble(), tolerance)
    }

    private fun assertRoundTrip(photo: Offset, container: IntSize, zoom: Float, pan: Offset, photoWidth: Int, photoHeight: Int) {
        val screen = photoToScreen(photo, container, zoom, pan, photoWidth, photoHeight)
        val back = screenToPhoto(screen, container, zoom, pan, photoWidth, photoHeight)
        assertEquals("x of $photo", photo.x.toDouble(), back.x.toDouble(), tolerance)
        assertEquals("y of $photo", photo.y.toDouble(), back.y.toDouble(), tolerance)
    }

    private fun assertRoundTripGrid(container: IntSize, zoom: Float, pan: Offset, photoWidth: Int, photoHeight: Int) {
        val w = photoWidth.toFloat()
        val h = photoHeight.toFloat()
        for (photo in listOf(Offset(0f, 0f), Offset(w, h), Offset(w / 2f, h / 2f), Offset(w * 0.25f, h * 0.8f), Offset(w * 0.9f, h * 0.1f))) {
            assertRoundTrip(photo, container, zoom, pan, photoWidth, photoHeight)
        }
    }

    @Test
    fun portraitPhotoInLandscapeBoxAtZoomOneHasItsEdgesAtTheFit() {
        val container = IntSize(800, 400)
        // Height limits the fit: 1000 x 2000 becomes 200 x 400 at x = 300.
        assertPointNear(Offset(0f, 0f), screenToPhoto(Offset(300f, 0f), container, 1f, Offset.Zero, 1000, 2000))
        assertPointNear(Offset(1000f, 2000f), screenToPhoto(Offset(500f, 400f), container, 1f, Offset.Zero, 1000, 2000))
        assertRoundTripGrid(container, 1f, Offset.Zero, 1000, 2000)
    }

    @Test
    fun landscapePhotoInPortraitBoxAtZoomOneHasItsEdgesAtTheFit() {
        val container = IntSize(400, 800)
        // Width limits the fit: 2000 x 1000 becomes 400 x 200 at y = 300.
        assertPointNear(Offset(0f, 0f), screenToPhoto(Offset(0f, 300f), container, 1f, Offset.Zero, 2000, 1000))
        assertPointNear(Offset(2000f, 1000f), screenToPhoto(Offset(400f, 500f), container, 1f, Offset.Zero, 2000, 1000))
        assertRoundTripGrid(container, 1f, Offset.Zero, 2000, 1000)
    }

    @Test
    fun zoomedAndPannedPortraitPhotoRoundTrips() {
        val container = IntSize(800, 400)
        assertRoundTripGrid(container, 2.5f, Offset(60f, -40f), 1000, 2000)
    }

    @Test
    fun zoomedAndPannedLandscapePhotoRoundTrips() {
        val container = IntSize(400, 800)
        assertRoundTripGrid(container, 3.5f, Offset(-120f, 90f), 2000, 1000)
    }

    @Test
    fun zoomScalesAboutTheBoxCentre() {
        val container = IntSize(800, 400)
        // The centre of the box stays on the same photo point at any zoom, so a pinch does not jump the image.
        val beforeZoom = screenToPhoto(Offset(400f, 200f), container, 1f, Offset.Zero, 1000, 2000)
        val afterZoom = screenToPhoto(Offset(400f, 200f), container, 3f, Offset.Zero, 1000, 2000)
        assertEquals(beforeZoom.x.toDouble(), afterZoom.x.toDouble(), tolerance)
        assertEquals(beforeZoom.y.toDouble(), afterZoom.y.toDouble(), tolerance)
    }

    @Test
    fun clampPanKeepsThePreviewCoveringTheBox() {
        val container = IntSize(800, 400)
        val zoom = 3f
        val clamped = clampPan(Offset(1000f, -1000f), container, zoom)
        assertEquals(800f, clamped.x, 1e-3f)
        assertEquals(-400f, clamped.y, 1e-3f)

        // Scaled box edges: left and top must not come inside the box, right and bottom must not fall short of it.
        val left = container.width / 2f + (0f - container.width / 2f) * zoom + clamped.x
        val right = container.width / 2f + (container.width - container.width / 2f) * zoom + clamped.x
        val top = container.height / 2f + (0f - container.height / 2f) * zoom + clamped.y
        val bottom = container.height / 2f + (container.height - container.height / 2f) * zoom + clamped.y
        assertTrue(left <= 0f && top <= 0f)
        assertTrue(right >= container.width && bottom >= container.height)
    }

    @Test
    fun clampPanLeavesAPanInsideTheLimitAndAtZoomOneForcesNoPan() {
        val container = IntSize(800, 400)
        assertEquals(Offset(60f, -40f), clampPan(Offset(60f, -40f), container, 2.5f))
        assertEquals(Offset.Zero, clampPan(Offset(50f, 50f), container, 1f))
    }
}
