package com.gusanitolabs.robia.media

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** V-DARK1-5 (R3-DEC-045): the image and PDF exports draw the same file in every theme. */
class ExportsIgnoreThemeTest {
    private val themeReaders = listOf("MaterialTheme", "isSystemInDarkTheme", "LocalRobiaExtras", "colorScheme", "uiMode")

    @Test
    fun exportCodeNeverReadsTheTheme() {
        listOf(
            "app/src/main/java/com/gusanitolabs/robia/media/GarmentShareExporter.kt",
            "app/src/main/java/com/gusanitolabs/robia/media/PdfLayout.kt",
        ).forEach { path ->
            val text = source(path)
            themeReaders.forEach { reader ->
                assertFalse("$path reads $reader", text.contains(reader))
            }
        }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
