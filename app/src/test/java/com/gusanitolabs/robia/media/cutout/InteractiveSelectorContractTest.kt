package com.gusanitolabs.robia.media.cutout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * BitmapImageBuilder wraps the caller's bitmap without copying it, and MPImage.close() recycles that bitmap. An MPImage built
 * from a caller's bitmap must therefore never be closed (or `use`d) in InteractiveSelector.kt, where the wrap happens.
 */
class InteractiveSelectorContractTest {
    private val builderDeclaration = Regex("""val\s+(\w+)\s*=\s*BitmapImageBuilder\([^()]*\)\.build\(\)""")

    private fun closedOrUsed(name: String) = Regex("""\b$name\s*\.\s*(?:close\s*\(\s*\)|use\s*\{)""")

    private fun closedBuiltImages(text: String): List<String> {
        val names = builderDeclaration.findAll(text).map { it.groupValues[1] }.toList()
        return names.flatMap { name ->
            closedOrUsed(name).findAll(text).map { match ->
                "$name closed at line ${lineOf(text, match.range.first)}"
            }.toList()
        }
    }

    @Test
    fun builtImageIsNeverClosedInSelector() {
        val text = selectorSource().readText()
        assertTrue("no BitmapImageBuilder(...).build() declaration in InteractiveSelector.kt", builderDeclaration.containsMatchIn(text))
        val misuses = closedBuiltImages(text)
        assertTrue("MPImage built from the caller's bitmap is closed, which recycles the bitmap: $misuses", misuses.isEmpty())
    }

    @Test
    fun detectorMatchesKnownBadSnippetAndIgnoresFixedForm() {
        assertTrue(builderDeclaration.containsMatchIn(KNOWN_BAD))
        assertTrue(closedBuiltImages(KNOWN_BAD).isNotEmpty())
        assertTrue(builderDeclaration.containsMatchIn(KNOWN_GOOD))
        assertFalse(closedBuiltImages(KNOWN_GOOD).isNotEmpty())
    }

    private fun selectorSource(): File {
        val relative = "app/src/main/java/com/gusanitolabs/robia/media/cutout/InteractiveSelector.kt"
        return listOf(Path.of(relative), Path.of("../$relative")).first { Files.isRegularFile(it) }.toFile()
    }

    private fun lineOf(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

    private companion object {
        val KNOWN_BAD = """
            val input = BitmapImageBuilder(bitmap).build()
            try {
                segmenter.segment(input, roi)
            } finally {
                input.close()
            }
        """.trimIndent()

        val KNOWN_GOOD = """
            val input = BitmapImageBuilder(bitmap).build()
            val result = segmenter.segment(input, roi)
        """.trimIndent()
    }
}
