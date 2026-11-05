package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-TH2-2 (REQ-TH-7): the palette row writes through the repository, and its three strings exist once in each locale. */
class PaletteSettingsContractTest {
    private val paletteKeys = listOf("settings_color_palette", "palette_earth_steel", "palette_tobacco_bordeaux")

    @Test
    fun settingsRowWritesThroughTheRepositoryAndUsesTheStringKey() {
        val settings = resource("app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt")

        assertTrue(settings.contains("setColorPalette("))
        assertTrue(settings.contains("settings_color_palette"))
    }

    @Test
    fun paletteKeysExistOnceInEveryLocaleAndTheLocalesHaveEqualKeySets() {
        val english = stringNames(resource("app/src/main/res/values/strings.xml"))
        val spanish = stringNames(resource("app/src/main/res/values-es/strings.xml"))
        val german = stringNames(resource("app/src/main/res/values-de/strings.xml"))

        listOf(english, spanish, german).forEach { names ->
            assertEquals(names.size, names.distinct().size)
            paletteKeys.forEach { key -> assertEquals(1, names.count { it == key }) }
        }
        assertEquals(english.toSet(), spanish.toSet())
        assertEquals(english.toSet(), german.toSet())
    }

    private fun stringNames(xml: String): List<String> =
        Regex("<string name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
