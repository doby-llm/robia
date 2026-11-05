package com.gusanitolabs.robia.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.model.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ColorPaletteTest {
    /** V-TH1-1: the Tobacco & Bordeaux tokens are the artifact table, light and dark, including the #FFD9DE fix. */
    @Test
    fun tobaccoLightAndDarkMatchTheTokenTable() {
        assertEquals(LIGHT_HEX.mapValues { Color(it.value) }, TobaccoLightColorScheme.roles())
        assertEquals(DARK_HEX.mapValues { Color(it.value) }, TobaccoDarkColorScheme.roles())
        assertEquals(LIGHT_EXTRA_HEX.mapValues { Color(it.value) }, TobaccoLightExtras.extraRoles())
        assertEquals(DARK_EXTRA_HEX.mapValues { Color(it.value) }, TobaccoDarkExtras.extraRoles())
    }

    /** V-TH1-2: the twelve text pairs of the artifact meet WCAG AA in both modes. */
    @Test
    fun tobaccoTextPairsMeetWcag() {
        listOf("light" to TobaccoLightColorScheme, "dark" to TobaccoDarkColorScheme).forEach { (mode, scheme) ->
            val textPairs = listOf(
                "onSurface/surface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
                "onSurfaceVariant/surfaceContainer" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
                "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary/secondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary/tertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiaryContainer/tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onError/error" to (scheme.onError to scheme.error),
                "onErrorContainer/errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "inverseOnSurface/inverseSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
            )
            textPairs.forEach { (name, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$mode $name is $ratio, needs 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun tobaccoSparkAndOutlineMeetTheirThresholds() {
        val sparkLight = contrast(TobaccoLightExtras.sparkOnPrimary, TobaccoLightColorScheme.primary)
        val sparkDark = contrast(TobaccoDarkExtras.sparkOnPrimary, TobaccoDarkColorScheme.primary)
        val outlineLight = contrast(TobaccoLightColorScheme.outline, TobaccoLightColorScheme.surface)
        val outlineDark = contrast(TobaccoDarkColorScheme.outline, TobaccoDarkColorScheme.surface)

        assertTrue("spark over light primary is $sparkLight, needs 3", sparkLight >= 3.0)
        assertTrue("spark over dark primary is $sparkDark, needs 3", sparkDark >= 3.0)
        assertTrue("light outline/surface is $outlineLight, needs 3", outlineLight >= 3.0)
        assertTrue("dark outline/surface is $outlineDark, needs 3", outlineDark >= 3.0)
    }

    /** V-TH1-3: absent, empty or unknown stored values read as Tobacco & Bordeaux; each storage value round-trips. */
    @Test
    fun paletteFromStorage() {
        listOf(null, "", "x").forEach { assertEquals(ColorPalette.TobaccoBordeaux, ColorPalette.fromStorage(it)) }
        assertEquals("earth_steel", ColorPalette.EarthSteel.storageValue)
        assertEquals("tobacco_bordeaux", ColorPalette.TobaccoBordeaux.storageValue)
        ColorPalette.entries.forEach { assertEquals(it, ColorPalette.fromStorage(it.storageValue)) }
    }

    /** V-TH1-4: palette and mode pick the four schemes; Earth & Steel light stays the existing scheme. */
    @Test
    fun robiaColorSchemeSelectsPaletteAndMode() {
        assertSame(RobiaLightColorScheme, robiaColorScheme(ColorPalette.EarthSteel, dark = false))
        assertSame(RobiaDarkColorScheme, robiaColorScheme(ColorPalette.EarthSteel, dark = true))
        assertSame(TobaccoLightColorScheme, robiaColorScheme(ColorPalette.TobaccoBordeaux, dark = false))
        assertSame(TobaccoDarkColorScheme, robiaColorScheme(ColorPalette.TobaccoBordeaux, dark = true))
        assertSame(RobiaLightExtras, robiaExtras(ColorPalette.EarthSteel, dark = false))
        assertSame(RobiaDarkExtras, robiaExtras(ColorPalette.EarthSteel, dark = true))
        assertSame(TobaccoLightExtras, robiaExtras(ColorPalette.TobaccoBordeaux, dark = false))
        assertSame(TobaccoDarkExtras, robiaExtras(ColorPalette.TobaccoBordeaux, dark = true))
    }

    /** V-TH1-5: the photo-well ink is the palette's light scheme in both modes; Tobacco chips and wells equal Earth & Steel. */
    @Test
    fun wellSchemeIsTheLightSchemeOfThePalette() {
        listOf(false, true).forEach { dark ->
            assertSame(RobiaLightColorScheme, robiaExtras(ColorPalette.EarthSteel, dark).wellScheme)
            assertSame(TobaccoLightColorScheme, robiaExtras(ColorPalette.TobaccoBordeaux, dark).wellScheme)
        }
    }

    @Test
    fun tobaccoChipsAndPhotoWellsEqualEarthSteel() {
        assertEquals(RobiaLightExtras.sharedRoles(), TobaccoLightExtras.sharedRoles())
        assertEquals(RobiaDarkExtras.sharedRoles(), TobaccoDarkExtras.sharedRoles())
    }

    /** V-TH1-6: no screen file imports the Earth & Steel light scheme; photo wells read the palette's `wellScheme`. */
    @Test
    fun noUiFileImportsTheEarthSteelLightSchemeDirectly() {
        val uiDir = listOf(
            Path.of("app/src/main/java/com/gusanitolabs/robia/ui"),
            Path.of("../app/src/main/java/com/gusanitolabs/robia/ui"),
        ).first { Files.isDirectory(it) }.toFile()
        val importLine = "import com.gusanitolabs.robia.core.designsystem.RobiaLightColorScheme"

        val offenders = uiDir.walk()
            .filter { it.extension == "kt" && importLine in it.readText() }
            .map { it.name }
            .toList()

        assertEquals(emptyList<String>(), offenders)
    }

    private fun ColorScheme.roles(): Map<String, Color> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
    )

    private fun RobiaExtras.extraRoles(): Map<String, Color> = mapOf(
        "addBorder" to addColors.border,
        "addContainer" to addColors.container,
        "addSign" to addColors.sign,
        "onAddContainer" to addColors.onContainer,
        "onAddSign" to addColors.onSign,
        "onRemoveContainer" to removeColors.onContainer,
        "onRemoveSign" to removeColors.onSign,
        "removeBorder" to removeColors.border,
        "removeContainer" to removeColors.container,
        "removeSign" to removeColors.sign,
        "sparkOnPrimary" to sparkOnPrimary,
        "photoWellTop" to photoWellTop,
        "photoWellBottom" to photoWellBottom,
    )

    /** The extras that Tobacco & Bordeaux shares with Earth & Steel: chips and photo wells, not the spark. */
    private fun RobiaExtras.sharedRoles(): Map<String, Color> = extraRoles().filterKeys { it != "sparkOnPrimary" }

    /** WCAG 2.x contrast ratio of two sRGB colours. */
    private fun contrast(a: Color, b: Color): Double {
        val lighter = max(luminance(a), luminance(b))
        val darker = min(luminance(a), luminance(b))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(colour: Color): Double {
        fun linear(channel: Double): Double = if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(colour.red.toDouble()) + 0.7152 * linear(colour.green.toDouble()) + 0.0722 * linear(colour.blue.toDouble())
    }

    private companion object {
        // Source: docs/specs/robia-round5/artifacts/tobacco-bordeaux-airy.md (light onTertiaryContainer #FFD9DE, R5-DEC-D03).
        val LIGHT_HEX = mapOf(
            "primary" to 0xFF5A3B2D,
            "onPrimary" to 0xFFFBF7F1,
            "primaryContainer" to 0xFF76584A,
            "onPrimaryContainer" to 0xFFF7EDE6,
            "inversePrimary" to 0xFFEABDAA,
            "secondary" to 0xFF73594D,
            "onSecondary" to 0xFFFFFFFF,
            "secondaryContainer" to 0xFFF3E4DA,
            "onSecondaryContainer" to 0xFF2A1B15,
            "tertiary" to 0xFF4E1B25,
            "onTertiary" to 0xFFFFFFFF,
            "tertiaryContainer" to 0xFF6A303A,
            "onTertiaryContainer" to 0xFFFFD9DE,
            "background" to 0xFFFCFAF8,
            "onBackground" to 0xFF221814,
            "surface" to 0xFFFCFAF8,
            "onSurface" to 0xFF221814,
            "surfaceVariant" to 0xFFE3D9D0,
            "onSurfaceVariant" to 0xFF5C4A43,
            "surfaceTint" to 0xFF795647,
            "inverseSurface" to 0xFF352F2D,
            "inverseOnSurface" to 0xFFF9EEEB,
            "error" to 0xFFBA1A1A,
            "onError" to 0xFFFFFFFF,
            "errorContainer" to 0xFFFFDAD5,
            "onErrorContainer" to 0xFF930009,
            "outline" to 0xFF82746F,
            "outlineVariant" to 0xFFD4C3BD,
            "scrim" to 0xFF000000,
            "surfaceBright" to 0xFFFCFAF8,
            "surfaceDim" to 0xFFE2D8D4,
            "surfaceContainer" to 0xFFF3ECE6,
            "surfaceContainerHigh" to 0xFFEBE3DC,
            "surfaceContainerHighest" to 0xFFE3D9D0,
            "surfaceContainerLow" to 0xFFF8F3EF,
            "surfaceContainerLowest" to 0xFFFFFFFF,
        )

        val DARK_HEX = mapOf(
            "primary" to 0xFFF2D6C8,
            "onPrimary" to 0xFF46291C,
            "primaryContainer" to 0xFF5A3B2D,
            "onPrimaryContainer" to 0xFFD1A693,
            "inversePrimary" to 0xFF795647,
            "secondary" to 0xFFE1BFB1,
            "onSecondary" to 0xFF412B22,
            "secondaryContainer" to 0xFF6B4C4A,
            "onSecondaryContainer" to 0xFFF5EBDD,
            "tertiary" to 0xFFFFB2BC,
            "onTertiary" to 0xFF531F29,
            "tertiaryContainer" to 0xFF6A303A,
            "onTertiaryContainer" to 0xFFE79AA4,
            "background" to 0xFF211715,
            "onBackground" to 0xFFEBE0DD,
            "surface" to 0xFF211715,
            "onSurface" to 0xFFEBE0DD,
            "surfaceVariant" to 0xFF504440,
            "onSurfaceVariant" to 0xFFD4C3BD,
            "surfaceTint" to 0xFFEABDAA,
            "inverseSurface" to 0xFFEBE0DD,
            "inverseOnSurface" to 0xFF352F2D,
            "error" to 0xFFFFB4AB,
            "onError" to 0xFF690004,
            "errorContainer" to 0xFF930009,
            "onErrorContainer" to 0xFFFFDAD5,
            "outline" to 0xFF9C8E88,
            "outlineVariant" to 0xFF504440,
            "scrim" to 0xFF000000,
            "surfaceBright" to 0xFF3E3836,
            "surfaceDim" to 0xFF171211,
            "surfaceContainer" to 0xFF3D2C27,
            "surfaceContainerHigh" to 0xFF2E2927,
            "surfaceContainerHighest" to 0xFF393431,
            "surfaceContainerLow" to 0xFF1F1B19,
            "surfaceContainerLowest" to 0xFF110D0C,
        )

        val LIGHT_EXTRA_HEX = mapOf(
            "addBorder" to 0xFF9CC8AA,
            "addContainer" to 0xFFDCEFE1,
            "addSign" to 0xFF2E6B47,
            "onAddContainer" to 0xFF17432A,
            "onAddSign" to 0xFFFFFFFF,
            "onRemoveContainer" to 0xFF93000A,
            "onRemoveSign" to 0xFFFFFFFF,
            "removeBorder" to 0xFFEAA59C,
            "removeContainer" to 0xFFFFDAD6,
            "removeSign" to 0xFFBA1A1A,
            "sparkOnPrimary" to 0xFFFFD9DE,
            "photoWellTop" to 0xFFF3F3F4,
            "photoWellBottom" to 0xFFD1CFCF,
        )

        val DARK_EXTRA_HEX = mapOf(
            "addBorder" to 0xFF4F8F66,
            "addContainer" to 0xFF1D4430,
            "addSign" to 0xFF8FD3A5,
            "onAddContainer" to 0xFFC6F2D3,
            "onAddSign" to 0xFF0B2615,
            "onRemoveContainer" to 0xFFFFDAD6,
            "onRemoveSign" to 0xFF5C0A05,
            "removeBorder" to 0xFFB5554B,
            "removeContainer" to 0xFF5E1F19,
            "removeSign" to 0xFFFFB4AB,
            "sparkOnPrimary" to 0xFF6A303A,
            "photoWellTop" to 0xFFF3F3F4,
            "photoWellBottom" to 0xFFD1CFCF,
        )
    }
}
