package com.gusanitolabs.robia.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ReviewChangeColorsTest {
    /** V-REV-TOKENS-1: today's light chip tokens, the mockup's `ADDREM` table. */
    @Test
    fun lightChipColorsKeepEveryMockupHex() {
        assertEquals(LIGHT_HEX.mapValues { Color(it.value) }, RobiaLightExtras.chipRoles())
    }

    @Test
    fun darkChipColorsKeepEveryMockupHex() {
        assertEquals(DARK_HEX.mapValues { Color(it.value) }, RobiaDarkExtras.chipRoles())
    }

    /** V-REV-TOKENS-2: every text or glyph on a chip is readable on its fill, the sign on the card. */
    @Test
    fun lightChipPairsMeetWcagContrast() {
        assertChipContrast(RobiaLightColorScheme.surfaceContainerLowest, RobiaLightExtras)
    }

    @Test
    fun darkChipPairsMeetWcagContrast() {
        assertChipContrast(RobiaDarkColorScheme.surfaceContainerLowest, RobiaDarkExtras)
    }

    @Test
    fun addAndRemoveSignsDifferInHueAndFromOnSurfaceVariant() {
        listOf(
            Triple("light", RobiaLightExtras, RobiaLightColorScheme.onSurfaceVariant),
            Triple("dark", RobiaDarkExtras, RobiaDarkColorScheme.onSurfaceVariant),
        ).forEach { (theme, extras, onSurfaceVariant) ->
            val add = extras.addColors.sign
            val remove = extras.removeColors.sign
            assertTrue("$theme: add and remove signs share a hue", hueDistance(add, remove) >= 60.0)
            assertNotEquals("$theme: add sign equals onSurfaceVariant", onSurfaceVariant, add)
            assertNotEquals("$theme: remove sign equals onSurfaceVariant", onSurfaceVariant, remove)
        }
    }

    private fun assertChipContrast(card: Color, extras: RobiaExtras) {
        listOf("add" to extras.addColors, "remove" to extras.removeColors).forEach { (name, colors) ->
            val pairs = mapOf(
                "$name sign/card" to contrast(colors.sign, card),
                "$name onContainer/container" to contrast(colors.onContainer, colors.container),
                "$name onSign/sign" to contrast(colors.onSign, colors.sign),
            )
            pairs.forEach { (pair, ratio) -> assertTrue("$pair is $ratio, needs 4.5", ratio >= 4.5) }
        }
    }

    private fun RobiaExtras.chipRoles(): Map<String, Color> = mapOf(
        "addSign" to addColors.sign,
        "addOnSign" to addColors.onSign,
        "addContainer" to addColors.container,
        "addOnContainer" to addColors.onContainer,
        "addBorder" to addColors.border,
        "removeSign" to removeColors.sign,
        "removeOnSign" to removeColors.onSign,
        "removeContainer" to removeColors.container,
        "removeOnContainer" to removeColors.onContainer,
        "removeBorder" to removeColors.border,
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

    /** Hue angle in degrees, 0 to 360, of an sRGB colour. */
    private fun hue(colour: Color): Double {
        val r = colour.red.toDouble()
        val g = colour.green.toDouble()
        val b = colour.blue.toDouble()
        val delta = max(r, max(g, b)) - min(r, min(g, b))
        if (delta == 0.0) return 0.0
        val sector = when (max(r, max(g, b))) {
            r -> ((g - b) / delta).mod(6.0)
            g -> (b - r) / delta + 2.0
            else -> (r - g) / delta + 4.0
        }
        return sector * 60.0
    }

    /** Smallest angle between two hues, 0 to 180 degrees. */
    private fun hueDistance(a: Color, b: Color): Double {
        val difference = abs(hue(a) - hue(b))
        return min(difference, 360.0 - difference)
    }

    private companion object {
        val LIGHT_HEX = mapOf(
            "addSign" to 0xFF2E6B47,
            "addOnSign" to 0xFFFFFFFF,
            "addContainer" to 0xFFDCEFE1,
            "addOnContainer" to 0xFF17432A,
            "addBorder" to 0xFF9CC8AA,
            "removeSign" to 0xFFBA1A1A,
            "removeOnSign" to 0xFFFFFFFF,
            "removeContainer" to 0xFFFFDAD6,
            "removeOnContainer" to 0xFF93000A,
            "removeBorder" to 0xFFEAA59C,
        )

        val DARK_HEX = mapOf(
            "addSign" to 0xFF8FD3A5,
            "addOnSign" to 0xFF0B2615,
            "addContainer" to 0xFF1D4430,
            "addOnContainer" to 0xFFC6F2D3,
            "addBorder" to 0xFF4F8F66,
            "removeSign" to 0xFFFFB4AB,
            "removeOnSign" to 0xFF5C0A05,
            "removeContainer" to 0xFF5E1F19,
            "removeOnContainer" to 0xFFFFDAD6,
            "removeBorder" to 0xFFB5554B,
        )
    }
}
