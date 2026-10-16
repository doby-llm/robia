package com.gusanitolabs.robia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.model.DisplayColorLabel

private const val GARMENT_SWATCH_ALPHA = 0.26f

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

/** Top stop is the theme surface, bottom stop is the swatch at a fixed alpha. */
internal fun garmentSwatchStops(swatch: Color, surfaceTop: Color): List<Color> =
    listOf(surfaceTop, swatch.copy(alpha = GARMENT_SWATCH_ALPHA))

internal fun garmentSwatchBrush(swatch: Color, surfaceTop: Color): Brush =
    Brush.verticalGradient(garmentSwatchStops(swatch, surfaceTop))

/** The Browse photo background; remembered so a grid of cards does not rebuild the brush on every frame. */
@Composable
internal fun rememberGarmentSwatchBrush(swatch: Color): Brush {
    val surfaceTop = MaterialTheme.colorScheme.surfaceContainerLow
    return remember(swatch, surfaceTop) { garmentSwatchBrush(swatch, surfaceTop) }
}
