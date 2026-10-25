package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DarkThemeEditorScanTest {
    @Test
    fun editorAnalyticsAndExportFilesMarkEveryColourLiteral() {
        val violations = ThemeSourceScan.scan(
            listOf(
                "app/src/main/java/com/gusanitolabs/robia/ui/PhotoEditorScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/AnalyticsScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ExportScreen.kt",
            ),
        )

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun editorFixedLiteralsEachGiveAReason() {
        val reasons = source("PhotoEditorScreen.kt").lines()
            .filter { "theme-fixed:" in it }
            .map { it.substringAfter("theme-fixed:") }

        listOf("checkerboard", "backdrop", "swatch", "chip").forEach { word ->
            assertTrue("no theme-fixed reason names the $word", reasons.any { it.contains(word, ignoreCase = true) })
        }
    }

    @Test
    fun renderPreviewTakesNoThemeInput() {
        val signature = source("ExportScreen.kt").lines().single { "fun renderPreview(" in it }

        assertFalse(listOf("Theme", "dark", "Dark", "colorScheme", "extras").any { it in signature })
    }

    private fun source(fileName: String): String {
        val relativePath = "app/src/main/java/com/gusanitolabs/robia/ui/$fileName"
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
