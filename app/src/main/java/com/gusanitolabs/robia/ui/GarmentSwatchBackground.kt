package com.gusanitolabs.robia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.designsystem.extras
import com.gusanitolabs.robia.core.model.DisplayColorLabel

// theme-fixed-file: garment colour data and photo backdrop stops

private const val GARMENT_SWATCH_ALPHA = 0.26f

/** Above this luma a swatch would vanish on the white card, so it gets the neutral backdrop instead (R3-02). */
private const val NEAR_WHITE_LUMA = 0.82f

/** Neutral grey backdrop for near-white garments, top to bottom, like the add flow (REQ-FB-05). */
private val NEAR_WHITE_STOPS = listOf(Color(0xFFF3F3F4), Color(0xFFD6D5D6))

internal fun DisplayColorLabel.swatchColor(): Color = when (this) {
    DisplayColorLabel.Black -> Color(0xFF1F1F1F)
    DisplayColorLabel.Blue -> Color(0xFF315F8E)
    DisplayColorLabel.Brown -> Color(0xFF8B6848)
    DisplayColorLabel.Gray -> Color(0xFF8E8E8E)
    DisplayColorLabel.Green -> Color(0xFF5F6F48)
    DisplayColorLabel.Orange -> Color(0xFFC56F33)
    DisplayColorLabel.Pink -> Color(0xFFD4879A)
    DisplayColorLabel.Purple -> Color(0xFF765A91)
    DisplayColorLabel.Red -> Color(0xFF9E3D35)
    DisplayColorLabel.White -> Color(0xFFF8F9FA)
    DisplayColorLabel.Yellow -> Color(0xFFD6B84C)
    DisplayColorLabel.Multicolor -> Color(0xFFA56639)
    DisplayColorLabel.Unknown -> Color(0xFFDADADA)
}

/** The fixed label a raw colour value (hex or name) maps to; a missing or unparsable value is Unknown. */
internal fun garmentSwatchLabel(hex: String?): DisplayColorLabel = ColorLabelResolver.fromRawValue(hex)

/** Rec. 709 luma of a colour's sRGB channels, 0..1. */
internal fun swatchLuma(colour: Color): Float = 0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue

internal fun isNearWhiteSwatch(swatch: Color): Boolean = swatchLuma(swatch) > NEAR_WHITE_LUMA

/** White, or a colour lighter than [NEAR_WHITE_LUMA]. The hex is the colour when it parses, else the label's swatch. */
internal fun isNearWhite(label: DisplayColorLabel, hex: String?): Boolean =
    label == DisplayColorLabel.White || isNearWhiteSwatch(hex?.let(::hexSwatch) ?: label.swatchColor())

/** A `#RRGGBB` value as an opaque colour; null for names, blanks and anything else. */
private fun hexSwatch(hex: String): Color? {
    val digits = hex.trim().removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000L or digits.toLong(16))
}

/**
 * Top stop is the theme surface, bottom stop is the swatch at a fixed alpha.
 * A near-white garment gets the neutral grey stops instead, so it reads against the white card.
 */
internal fun garmentSwatchStops(
    swatch: Color,
    surfaceTop: Color,
    nearWhite: Boolean = isNearWhiteSwatch(swatch),
): List<Color> =
    if (nearWhite) NEAR_WHITE_STOPS else listOf(surfaceTop, swatch.copy(alpha = GARMENT_SWATCH_ALPHA))

internal fun garmentSwatchBrush(
    swatch: Color,
    surfaceTop: Color,
    nearWhite: Boolean = isNearWhiteSwatch(swatch),
): Brush = Brush.verticalGradient(garmentSwatchStops(swatch, surfaceTop, nearWhite))

/**
 * The Browse photo background; remembered so a grid of cards does not rebuild the brush on every frame.
 * [nearWhite] comes from the garment's label and hex where the caller has them; otherwise the swatch decides.
 */
@Composable
internal fun rememberGarmentSwatchBrush(swatch: Color, nearWhite: Boolean = isNearWhiteSwatch(swatch)): Brush {
    val surfaceTop = MaterialTheme.extras.photoWellTop
    return remember(swatch, surfaceTop, nearWhite) { garmentSwatchBrush(swatch, surfaceTop, nearWhite) }
}
