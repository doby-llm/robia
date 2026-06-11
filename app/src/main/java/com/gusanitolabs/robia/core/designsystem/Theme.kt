package com.gusanitolabs.robia.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RobiaLightColorScheme = lightColorScheme(
    primary = RobiaPrimary,
    onPrimary = RobiaOnPrimary,
    primaryContainer = RobiaPrimaryContainer,
    onPrimaryContainer = RobiaOnPrimaryContainer,
    inversePrimary = RobiaInversePrimary,
    secondary = RobiaSecondary,
    onSecondary = RobiaOnSecondary,
    secondaryContainer = RobiaSecondaryContainer,
    onSecondaryContainer = RobiaOnSecondaryContainer,
    tertiary = RobiaTertiary,
    onTertiary = RobiaOnTertiary,
    tertiaryContainer = RobiaTertiaryContainer,
    onTertiaryContainer = RobiaOnTertiaryContainer,
    background = RobiaBackground,
    onBackground = RobiaOnBackground,
    surface = RobiaSurface,
    onSurface = RobiaOnSurface,
    surfaceVariant = RobiaSurfaceVariant,
    onSurfaceVariant = RobiaOnSurfaceVariant,
    surfaceTint = RobiaSurfaceTint,
    inverseSurface = RobiaInverseSurface,
    inverseOnSurface = RobiaInverseOnSurface,
    error = RobiaError,
    onError = RobiaOnError,
    errorContainer = RobiaErrorContainer,
    onErrorContainer = RobiaOnErrorContainer,
    outline = RobiaOutline,
    outlineVariant = RobiaOutlineVariant,
    scrim = RobiaScrim,
    surfaceBright = RobiaSurfaceBright,
    surfaceDim = RobiaSurfaceDim,
    surfaceContainer = RobiaSurfaceContainer,
    surfaceContainerHigh = RobiaSurfaceContainerHigh,
    surfaceContainerHighest = RobiaSurfaceContainerHighest,
    surfaceContainerLow = RobiaSurfaceContainerLow,
    surfaceContainerLowest = RobiaSurfaceContainerLowest,
)

val RobiaShapes = Shapes(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 12.dp,
    large = 16.dp,
    extraLarge = 24.dp,
)

@Composable
fun RobiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RobiaLightColorScheme,
        typography = RobiaTypography,
        shapes = RobiaShapes,
        content = content,
    )
}
