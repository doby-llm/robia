package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.DisplayColorLabel
import kotlin.math.max
import kotlin.math.min

object ColorLabelResolver {
    fun fromRawValue(rawValue: String?): DisplayColorLabel {
        val normalized = rawValue?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return DisplayColorLabel.Unknown

        namedLabels[normalized]?.let { return it }
        parseHexColor(normalized)?.let { rgb -> return fromRgb(rgb.red, rgb.green, rgb.blue) }

        return DisplayColorLabel.Unknown
    }

    fun fromRgb(red: Int, green: Int, blue: Int): DisplayColorLabel {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val chroma = maxChannel - minChannel

        if (maxChannel < 40) return DisplayColorLabel.Black
        if (minChannel > 220 && chroma < 24) return DisplayColorLabel.White
        if (chroma < 28) return DisplayColorLabel.Gray

        val hue = rgbToHueDegrees(r, g, b)
        return when {
            hue < 15 || hue >= 350 -> DisplayColorLabel.Red
            hue < 45 -> DisplayColorLabel.Orange
            hue < 70 -> DisplayColorLabel.Yellow
            hue < 165 -> DisplayColorLabel.Green
            hue < 255 -> DisplayColorLabel.Blue
            hue < 290 -> DisplayColorLabel.Purple
            hue < 350 -> DisplayColorLabel.Pink
            else -> DisplayColorLabel.Unknown
        }
    }

    private fun rgbToHueDegrees(red: Int, green: Int, blue: Int): Float {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min
        if (delta == 0f) return 0f

        val hue = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return if (hue < 0) hue + 360f else hue
    }

    private fun parseHexColor(value: String): Rgb? {
        val hex = value.removePrefix("#")
        if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return Rgb(
            red = hex.substring(0, 2).toInt(16),
            green = hex.substring(2, 4).toInt(16),
            blue = hex.substring(4, 6).toInt(16),
        )
    }

    private data class Rgb(val red: Int, val green: Int, val blue: Int)

    private val namedLabels = mapOf(
        "black" to DisplayColorLabel.Black,
        "blue" to DisplayColorLabel.Blue,
        "beige" to DisplayColorLabel.Gray,
        "brown" to DisplayColorLabel.Brown,
        "cream" to DisplayColorLabel.Gray,
        "gray" to DisplayColorLabel.Gray,
        "grey" to DisplayColorLabel.Gray,
        "green" to DisplayColorLabel.Green,
        "orange" to DisplayColorLabel.Orange,
        "pink" to DisplayColorLabel.Pink,
        "purple" to DisplayColorLabel.Purple,
        "red" to DisplayColorLabel.Red,
        "white" to DisplayColorLabel.White,
        "yellow" to DisplayColorLabel.Yellow,
        "multicolor" to DisplayColorLabel.Multicolor,
        "multi-color" to DisplayColorLabel.Multicolor,
    )
}
