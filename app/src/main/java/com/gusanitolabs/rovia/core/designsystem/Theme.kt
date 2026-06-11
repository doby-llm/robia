package com.gusanitolabs.rovia.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RoviaLightColorScheme = lightColorScheme(
    primary = RoviaPrimary,
    onPrimary = RoviaOnPrimary,
    primaryContainer = RoviaPrimaryContainer,
    onPrimaryContainer = RoviaOnPrimaryContainer,
    inversePrimary = RoviaInversePrimary,
    secondary = RoviaSecondary,
    onSecondary = RoviaOnSecondary,
    secondaryContainer = RoviaSecondaryContainer,
    onSecondaryContainer = RoviaOnSecondaryContainer,
    tertiary = RoviaTertiary,
    onTertiary = RoviaOnTertiary,
    tertiaryContainer = RoviaTertiaryContainer,
    onTertiaryContainer = RoviaOnTertiaryContainer,
    background = RoviaBackground,
    onBackground = RoviaOnBackground,
    surface = RoviaSurface,
    onSurface = RoviaOnSurface,
    surfaceVariant = RoviaSurfaceVariant,
    onSurfaceVariant = RoviaOnSurfaceVariant,
    surfaceTint = RoviaSurfaceTint,
    inverseSurface = RoviaInverseSurface,
    inverseOnSurface = RoviaInverseOnSurface,
    error = RoviaError,
    onError = RoviaOnError,
    errorContainer = RoviaErrorContainer,
    onErrorContainer = RoviaOnErrorContainer,
    outline = RoviaOutline,
    outlineVariant = RoviaOutlineVariant,
    scrim = RoviaScrim,
    surfaceBright = RoviaSurfaceBright,
    surfaceDim = RoviaSurfaceDim,
    surfaceContainer = RoviaSurfaceContainer,
    surfaceContainerHigh = RoviaSurfaceContainerHigh,
    surfaceContainerHighest = RoviaSurfaceContainerHighest,
    surfaceContainerLow = RoviaSurfaceContainerLow,
    surfaceContainerLowest = RoviaSurfaceContainerLowest,
)

val RoviaShapes = Shapes(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 12.dp,
    large = 16.dp,
    extraLarge = 24.dp,
)

@Composable
fun RoviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RoviaLightColorScheme,
        typography = RoviaTypography,
        shapes = RoviaShapes,
        content = content,
    )
}
