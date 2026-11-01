package com.gusanitolabs.robia.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class AnalyticsCollectionsContractTest {
    @Test
    fun collectionsSectionTitleIsWiredToItsString() {
        val branch = source("AnalyticsScreen.kt").lines().single { "WardrobeStatsSection.Collections ->" in it }

        assertTrue(branch.contains("R.string.analytics_section_collections"))
    }

    @Test
    fun collectionsSectionTitleHasEnglishSpanishAndGermanText() {
        val expected = mapOf(
            "values" to "Collections",
            "values-es" to "Colecciones",
            "values-de" to "Sammlungen",
        )

        expected.forEach { (folder, text) ->
            val entry = "<string name=\"analytics_section_collections\">$text</string>"
            assertTrue("$folder lacks $entry", resource("$folder/strings.xml").contains(entry))
        }
    }

    private fun source(fileName: String): String {
        val relativePath = "app/src/main/java/com/gusanitolabs/robia/ui/$fileName"
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }

    private fun resource(relativeToRes: String): String {
        val relativePath = "app/src/main/res/$relativeToRes"
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
