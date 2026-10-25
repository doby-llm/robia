package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-ADD-UX-1 and V-ADD-UX-2 (R3-DEC-049): the photo scroll distance, the batch hint and its strings. */
class AddPhotoScrollTest {
    @Test
    fun photoScrollDeltaIsZeroUntilThePhotoIsMeasured() {
        assertEquals(0, photoScrollDelta(itemTopPx = null, thresholdPx = 8))
    }

    @Test
    fun photoScrollDeltaScrollsTheTopEdgeToTheTopOfTheList() {
        assertEquals(120, photoScrollDelta(itemTopPx = 120, thresholdPx = 8))
        assertEquals(9, photoScrollDelta(itemTopPx = 9, thresholdPx = 8))
    }

    @Test
    fun photoScrollDeltaLeavesAPhotoAtOrWithinTheThresholdAlone() {
        assertEquals(0, photoScrollDelta(itemTopPx = 0, thresholdPx = 8))
        assertEquals(0, photoScrollDelta(itemTopPx = 8, thresholdPx = 8))
    }

    @Test
    fun photoScrollDeltaLeavesAUserWhoScrolledFurtherDownAlone() {
        assertEquals(0, photoScrollDelta(itemTopPx = -400, thresholdPx = 8))
    }

    @Test
    fun photoScrollDeltaIsNeverNegative() {
        listOf(-1000, -1, 0, 7, 8, 9, 1000).forEach { top ->
            assertTrue("top=$top", photoScrollDelta(itemTopPx = top, thresholdPx = 8) >= 0)
        }
    }

    @Test
    fun batchHintShowsOnlyForANewGarmentWithoutAPhoto() {
        assertTrue(batchHintVisible(isEditing = false, hasPhoto = false))
        assertFalse(batchHintVisible(isEditing = false, hasPhoto = true))
        assertFalse(batchHintVisible(isEditing = true, hasPhoto = false))
        assertFalse(batchHintVisible(isEditing = true, hasPhoto = true))
    }

    @Test
    fun batchHintStringsExistInEveryLocaleWithTheAgreedWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"gallery_multi\">Gallery · pick several</string>"))
        assertTrue(es.contains("<string name=\"gallery_multi\">Galería · elige varias</string>"))
        assertTrue(de.contains("<string name=\"gallery_multi\">Galerie · mehrere</string>"))
        assertTrue(en.contains("<string name=\"add_batch_hint\">Tip: pick several photos at once to add them as a batch.</string>"))
        assertTrue(es.contains("<string name=\"add_batch_hint\">Consejo: elige varias fotos a la vez para añadirlas como lote.</string>"))
        assertTrue(de.contains("<string name=\"add_batch_hint\">Tipp: Wähle mehrere Fotos auf einmal, um sie als Stapel hinzuzufügen.</string>"))
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
