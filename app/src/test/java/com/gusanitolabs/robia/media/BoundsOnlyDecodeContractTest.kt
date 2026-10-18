package com.gusanitolabs.robia.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * BitmapFactory.decodeStream / decodeFile with inJustDecodeBounds = true always return null, so their result must never feed
 * `?: ...`. Sources are walked rather than pinned by path so a new site is covered without editing this test.
 */
class BoundsOnlyDecodeContractTest {
    private val boundsDeclaration = Regex(
        """val\s+(\w+)\s*=\s*BitmapFactory\.Options\(\)\.apply\s*\{\s*inJustDecodeBounds\s*=\s*true\s*\}""",
    )

    private fun resultUsedAsValue(name: String) = Regex(
        """\?\.(?:use|let|run)\s*\{[^{}]*?(?:decodeStream|decodeFile)\([^()]*\b$name\s*\)[^{}]*\}\s*\?:""",
    )

    @Test
    fun boundsOnlyDecodeResultIsNeverUsedAsValue() {
        val files = mainSources()
        val declaredBounds = mutableListOf<String>()
        val misuses = files.flatMap { file ->
            val text = file.readText()
            val names = boundsDeclaration.findAll(text).map { it.groupValues[1] }.toSet()
            declaredBounds += names.map { "${file.name}:$it" }
            names.flatMap { name ->
                resultUsedAsValue(name).findAll(text).map { match ->
                    "${file.name}:${lineOf(text, match.range.first)} uses the result of a bounds-only decode"
                }.toList()
            }
        }

        assertTrue("scanner found no bounds-only declarations under ${files.size} files", declaredBounds.isNotEmpty())
        assertTrue("bounds-only decode result used as a value: $misuses", misuses.isEmpty())
    }

    @Test
    fun detectorMatchesKnownBadSnippetAndIgnoresFixedForm() {
        assertTrue(resultUsedAsValue("bounds").containsMatchIn(KNOWN_BAD))
        assertFalse(resultUsedAsValue("bounds").containsMatchIn(KNOWN_GOOD))
    }

    private fun mainSources(): List<File> {
        val root = listOf(Path.of("app/src/main/java"), Path.of("../app/src/main/java")).first { Files.isDirectory(it) }
        return root.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
    }

    private fun lineOf(text: String, index: Int): Int = text.substring(0, index).count { it == '\n' } + 1

    private companion object {
        val KNOWN_BAD = """
            val decoded = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: error("Unable to open source photo")
        """.trimIndent()

        val KNOWN_GOOD = """
            val input = context.contentResolver.openInputStream(imageUri) ?: error("Unable to open source photo")
            input.use { BitmapFactory.decodeStream(it, null, bounds) }
            val width = bounds.outWidth
        """.trimIndent()
    }
}
