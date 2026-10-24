package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkThemeSharedScanTest {
    @Test
    fun sharedUiFilesMarkEveryColourLiteral() {
        val violations = ThemeSourceScan.scan(
            listOf(
                "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/GarmentSwatchBackground.kt",
            ),
        )

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun literalWithoutMarkerFails() {
        val text = "val a = 1\nval red = Color(0xFFFF0000)\n"

        assertEquals(listOf(2 to "val red = Color(0xFFFF0000)"), ThemeSourceScan.unmarkedLiterals(text))
    }

    @Test
    fun markerWithEmptyReasonFails() {
        val text = "val red = Color(0xFFFF0000) // theme-fixed:   \n"

        assertEquals(1, ThemeSourceScan.unmarkedLiterals(text).size)
    }

    @Test
    fun markerWithReasonPasses() {
        val text = "val red = Color(0xFFFF0000) // theme-fixed: garment colour data\n"

        assertEquals(emptyList<Pair<Int, String>>(), ThemeSourceScan.unmarkedLiterals(text))
    }

    @Test
    fun fileMarkerPassesEveryLiteralInTheFile() {
        val text = "// theme-fixed-file: garment colour data\nval red = Color(0xFFFF0000)\nval w = Color.White\n"

        assertEquals(emptyList<Pair<Int, String>>(), ThemeSourceScan.unmarkedLiterals(text))
    }

    @Test
    fun transparentAndNamesThatOnlyContainWhiteOrBlackAreNotLiterals() {
        val text = "val t = Color.Transparent\nval label = DisplayColorLabel.Black\nval c = MainColor.White\n"

        assertEquals(emptyList<Pair<Int, String>>(), ThemeSourceScan.unmarkedLiterals(text))
    }

    @Test
    fun fileWithoutLiteralsPasses() {
        assertEquals(emptyList<Pair<Int, String>>(), ThemeSourceScan.unmarkedLiterals("val x = MaterialTheme.colorScheme.surface\n"))
    }
}
