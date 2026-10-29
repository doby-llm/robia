package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-FB3-CUT-NAMES-3 (REQ-FB-30): the method, help and pack strings in EN, ES and DE with the agreed wording. */
class CutoutMethodStringsTest {
    @Test
    fun methodAndPackStringsExistInEveryLocaleWithTheAgreedWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"cutout_method_precise\">Precise</string>"))
        assertTrue(en.contains("<string name=\"cutout_method_precise_hint\">Best for hangers and similar-coloured backgrounds. Slower. Needs the cutout pack.</string>"))
        assertTrue(en.contains("<string name=\"cutout_method_standard_hint\">Fast. Works for most photos. Does not remove hangers.</string>"))
        assertTrue(en.contains("<string name=\"cutout_method_automatic_hint\">Uses Precise when the pack is downloaded, otherwise Standard.</string>"))
        assertTrue(en.contains("<string name=\"cutout_needs_precise\">Needs Precise or Automatic and the cutout pack</string>"))
        assertTrue(en.contains("<string name=\"precise_cutout_title\">Cutout pack</string>"))
        assertTrue(en.contains("<string name=\"precise_cutout_body\">Needed by Precise and Automatic. "))

        assertTrue(es.contains("<string name=\"cutout_method_precise\">Preciso</string>"))
        assertTrue(es.contains("<string name=\"cutout_method_precise_hint\">Ideal con perchas o fondos parecidos. Más lento. Necesita el paquete de recorte.</string>"))
        assertTrue(es.contains("<string name=\"cutout_method_standard_hint\">Rápido. Sirve para la mayoría de fotos. No quita perchas.</string>"))
        assertTrue(es.contains("<string name=\"cutout_method_automatic_hint\">Usa Preciso si el paquete está descargado; si no, Estándar.</string>"))
        assertTrue(es.contains("<string name=\"cutout_needs_precise\">Necesita Preciso o Automático y el paquete de recorte</string>"))
        assertTrue(es.contains("<string name=\"precise_cutout_title\">Paquete de recorte</string>"))
        assertTrue(es.contains("<string name=\"precise_cutout_body\">Lo necesitan Preciso y Automático. "))

        assertTrue(de.contains("<string name=\"cutout_method_precise\">Präzise</string>"))
        assertTrue(de.contains("<string name=\"cutout_method_precise_hint\">Am besten bei Bügeln oder ähnlichem Hintergrund. Langsamer. Braucht das Zuschnitt-Paket.</string>"))
        assertTrue(de.contains("<string name=\"cutout_method_standard_hint\">Schnell. Passt für die meisten Fotos. Entfernt keine Bügel.</string>"))
        assertTrue(de.contains("<string name=\"cutout_method_automatic_hint\">Nutzt Präzise, wenn das Paket geladen ist, sonst Standard.</string>"))
        assertTrue(de.contains("<string name=\"cutout_needs_precise\">Braucht Präzise oder Automatisch und das Zuschnitt-Paket</string>"))
        assertTrue(de.contains("<string name=\"precise_cutout_title\">Zuschnitt-Paket</string>"))
        assertTrue(de.contains("<string name=\"precise_cutout_body\">Benötigt von Präzise und Automatisch. "))
    }

    /** V-FB3-CUT-NAMES-3: the editor's "Cut out again" strings keep their wording (the Redo sheet already says Standard and Precise). */
    @Test
    fun cutOutAgainStringsAreUnchanged() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"redo_cutout\">Cut out again</string>"))
        assertTrue(en.contains("<string name=\"redo_cutout_standard\">Standard</string>"))
        assertTrue(en.contains("<string name=\"redo_cutout_precise\">Precise</string>"))
        assertTrue(es.contains("<string name=\"redo_cutout_precise\">Preciso</string>"))
        assertTrue(de.contains("<string name=\"redo_cutout_precise\">Präzise</string>"))
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
