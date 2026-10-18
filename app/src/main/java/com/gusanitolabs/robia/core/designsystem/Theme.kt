package com.gusanitolabs.robia.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal fun isReducedMotion(scale: Float): Boolean = scale == 0f

@Composable
fun RobiaTheme(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = RobiaLightColorScheme,
            typography = RobiaTypography,
            shapes = RobiaShapes,
            content = content,
        )
    }
}
