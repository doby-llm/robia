package com.gusanitolabs.robia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ManagePerformanceContractTest {
    @Test
    fun manageTags_usesKeyedTagRowsWithContentType() {
        val manage = source("app/src/main/java/com/gusanitolabs/robia/ui/ManageTagsScreen.kt")

        assertTrue(manage.contains("item(key = \"tag-row:"))
        assertTrue(manage.contains("contentType = \"tag-row\""))
    }

    @Test
    fun manageTags_colorsUseKeyedListRowsWithoutCardOrSearchBox() {
        val manage = source("app/src/main/java/com/gusanitolabs/robia/ui/ManageTagsScreen.kt")

        assertTrue(manage.contains("item(key = \"color-row:"))
        assertFalse(manage.contains("Card("))
        assertFalse(manage.contains("manage_search_tags"))
    }

    @Test
    fun toUiWardrobeItems_isNotComposable() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/BrowseGrid.kt")
        val declaration = "fun List<ClothingItem>.toUiWardrobeItems("
        val declarationIndex = app.indexOf(declaration)
        assertTrue(declarationIndex >= 0)

        val precedingLine = app.substring(0, declarationIndex).trimEnd().lineSequence().last().trim()
        assertFalse(precedingLine == "@Composable")
    }

    private fun uiSource(): String = Path.of("app/src/main/java/com/gusanitolabs/robia/ui").let { dir ->
        val d = if (Files.isDirectory(dir)) dir else Path.of("..").resolve(dir)
        d.toFile().listFiles { file -> file.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }.joinToString("\n") { it.readText() }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
