package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-REV-COLOR-1 (R3-DEC-052, REQ-FB-12): the one description line on the colour review and its strings. */
class ColorReviewHintTest {
    @Test
    fun colorReviewTapHintStringsExistInEveryLocaleWithTheAgreedWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"color_review_tap_hint\">Robia suggests the colors on the right. Tap one to change it before you accept.</string>"))
        assertTrue(es.contains("<string name=\"color_review_tap_hint\">Robia propone los colores de la derecha. Toca uno para cambiarlo antes de aceptar.</string>"))
        assertTrue(de.contains("<string name=\"color_review_tap_hint\">Robia schlägt die Farben rechts vor. Tippe auf eine, um sie vor dem Akzeptieren zu ändern.</string>"))
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
