package com.gusanitolabs.robia.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class RobiaColorSchemeTest {
    /** V-DARK1-2: today's light tokens. A light change is a failure here, not a visual surprise. */
    @Test
    fun lightSchemeKeepsEveryTodaysHex() {
        assertEquals(LIGHT_HEX.mapValues { Color(it.value) }, RobiaLightColorScheme.roles())
    }

    @Test
    fun darkSchemeSetsEveryRoleFromTheTable() {
        assertEquals(DARK_HEX.mapValues { Color(it.value) }, RobiaDarkColorScheme.roles())
    }

    @Test
    fun darkSchemeDiffersFromLightOnBackgroundSurfaceAndOnSurface() {
        assertNotEquals(RobiaLightColorScheme.background, RobiaDarkColorScheme.background)
        assertNotEquals(RobiaLightColorScheme.surface, RobiaDarkColorScheme.surface)
        assertNotEquals(RobiaLightColorScheme.onSurface, RobiaDarkColorScheme.onSurface)
    }

    @Test
    fun darkSchemeMeetsWcagContrastForTextPairs() {
        val dark = RobiaDarkColorScheme
        val textPairs = listOf(
            "onSurface/surface" to (dark.onSurface to dark.surface),
            "onSurfaceVariant/surface" to (dark.onSurfaceVariant to dark.surface),
            "onPrimary/primary" to (dark.onPrimary to dark.primary),
            "onPrimaryContainer/primaryContainer" to (dark.onPrimaryContainer to dark.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (dark.onSecondaryContainer to dark.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to (dark.onTertiaryContainer to dark.tertiaryContainer),
            "onError/error" to (dark.onError to dark.error),
            "onErrorContainer/errorContainer" to (dark.onErrorContainer to dark.errorContainer),
            "inverseOnSurface/inverseSurface" to (dark.inverseOnSurface to dark.inverseSurface),
        )
        textPairs.forEach { (name, pair) ->
            val ratio = contrast(pair.first, pair.second)
            assertTrue("$name is $ratio, needs 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun sparkIconTokenIsTheLightTintAndAFixedDarkValue() {
        assertEquals(RobiaOnTertiaryContainer, RobiaLightExtras.sparkOnPrimary)
        assertEquals(Color(0xFF6D390F), RobiaDarkExtras.sparkOnPrimary)
    }

    @Test
    fun sparkIconReadsOnThePrimaryButtonInBothSchemes() {
        val light = contrast(RobiaLightExtras.sparkOnPrimary, RobiaLightColorScheme.primary)
        val dark = contrast(RobiaDarkExtras.sparkOnPrimary, RobiaDarkColorScheme.primary)

        assertTrue("spark over light primary is $light, needs 3", light >= 3.0)
        assertTrue("spark over dark primary is $dark, needs 3", dark >= 3.0)
    }

    @Test
    fun darkOutlineMeetsWcagContrastForEdges() {
        val ratio = contrast(RobiaDarkColorScheme.outline, RobiaDarkColorScheme.surface)

        assertTrue("outline/surface is $ratio, needs 3", ratio >= 3.0)
    }

    @Test
    fun resolveDarkThemeFollowsTheChoiceAndTheSystemOnlyForFollowSystem() {
        assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = false))
        assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = false))
        assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.System, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.System, systemDark = false))
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
        val LIGHT_HEX = mapOf(
            "primary" to 0xFF1D100D,
            "onPrimary" to 0xFFFFFFFF,
            "primaryContainer" to 0xFF332421,
            "onPrimaryContainer" to 0xFFA08A85,
            "inversePrimary" to 0xFFDAC1BC,
            "secondary" to 0xFF6B5C4D,
            "onSecondary" to 0xFFFFFFFF,
            "secondaryContainer" to 0xFFF1DCC9,
            "onSecondaryContainer" to 0xFF6F6051,
            "tertiary" to 0xFF240D00,
            "onTertiary" to 0xFFFFFFFF,
            "tertiaryContainer" to 0xFF431D00,
            "onTertiaryContainer" to 0xFFC37F50,
            "background" to 0xFFF9F9F9,
            "onBackground" to 0xFF1A1C1C,
            "surface" to 0xFFF9F9F9,
            "onSurface" to 0xFF1A1C1C,
            "surfaceVariant" to 0xFFE2E2E2,
            "onSurfaceVariant" to 0xFF4F4442,
            "surfaceTint" to 0xFF6D5A56,
            "inverseSurface" to 0xFF2F3131,
            "inverseOnSurface" to 0xFFF0F1F1,
            "error" to 0xFFBA1A1A,
            "onError" to 0xFFFFFFFF,
            "errorContainer" to 0xFFFFDAD6,
            "onErrorContainer" to 0xFF93000A,
            "outline" to 0xFF817472,
            "outlineVariant" to 0xFFD2C3C0,
            "scrim" to 0xFF000000,
            "surfaceBright" to 0xFFF9F9F9,
            "surfaceDim" to 0xFFDADADA,
            "surfaceContainer" to 0xFFEFE5D9,
            "surfaceContainerHigh" to 0xFFE8E8E8,
            "surfaceContainerHighest" to 0xFFE2E2E2,
            "surfaceContainerLow" to 0xFFF3F3F4,
            "surfaceContainerLowest" to 0xFFFFFFFF,
        )

        /** The dark table of briefs/TASK-DARK1.md, role by role. */
        val DARK_HEX = mapOf(
            "background" to 0xFF171312,
            "surface" to 0xFF171312,
            "surfaceDim" to 0xFF120E0D,
            "surfaceBright" to 0xFF3A3330,
            "surfaceContainerHighest" to 0xFF3A3330,
            "surfaceVariant" to 0xFF3A3330,
            "surfaceContainerLowest" to 0xFF211C1A,
            "surfaceContainerLow" to 0xFF1D1817,
            "surfaceContainer" to 0xFF2B2421,
            "surfaceContainerHigh" to 0xFF2E2826,
            "onBackground" to 0xFFEDE0DC,
            "onSurface" to 0xFFEDE0DC,
            "onSurfaceVariant" to 0xFFCDBFBB,
            "outline" to 0xFF9C8D8A,
            "outlineVariant" to 0xFF544845,
            "inverseSurface" to 0xFFEDE0DC,
            "inverseOnSurface" to 0xFF2A1C19,
            "surfaceTint" to 0xFFDAC1BC,
            "scrim" to 0xFF000000,
            "primary" to 0xFFEAD7D1,
            "onPrimary" to 0xFF2A1C19,
            "primaryContainer" to 0xFF54433F,
            "onPrimaryContainer" to 0xFFF7DDD8,
            "inversePrimary" to 0xFF6D5A56,
            "secondary" to 0xFFD7C3B0,
            "onSecondary" to 0xFF3A2E22,
            "secondaryContainer" to 0xFF4A3C31,
            "onSecondaryContainer" to 0xFFF4DFCC,
            "tertiary" to 0xFFFFB786,
            "onTertiary" to 0xFF4E2600,
            "tertiaryContainer" to 0xFF6D390F,
            "onTertiaryContainer" to 0xFFFFDCC7,
            "error" to 0xFFFFB4AB,
            "onError" to 0xFF690005,
            "errorContainer" to 0xFF93000A,
            "onErrorContainer" to 0xFFFFDAD6,
        )
    }
}
