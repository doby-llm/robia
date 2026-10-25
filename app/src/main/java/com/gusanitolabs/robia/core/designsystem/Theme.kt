package com.gusanitolabs.robia.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.core.model.ThemeMode

internal val RobiaLightColorScheme = lightColorScheme(
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

internal val RobiaDarkColorScheme = darkColorScheme(
    primary = RobiaDarkPrimary,
    onPrimary = RobiaDarkOnPrimary,
    primaryContainer = RobiaDarkPrimaryContainer,
    onPrimaryContainer = RobiaDarkOnPrimaryContainer,
    inversePrimary = RobiaDarkInversePrimary,
    secondary = RobiaDarkSecondary,
    onSecondary = RobiaDarkOnSecondary,
    secondaryContainer = RobiaDarkSecondaryContainer,
    onSecondaryContainer = RobiaDarkOnSecondaryContainer,
    tertiary = RobiaDarkTertiary,
    onTertiary = RobiaDarkOnTertiary,
    tertiaryContainer = RobiaDarkTertiaryContainer,
    onTertiaryContainer = RobiaDarkOnTertiaryContainer,
    background = RobiaDarkBackground,
    onBackground = RobiaDarkOnBackground,
    surface = RobiaDarkSurface,
    onSurface = RobiaDarkOnSurface,
    surfaceVariant = RobiaDarkSurfaceVariant,
    onSurfaceVariant = RobiaDarkOnSurfaceVariant,
    surfaceTint = RobiaDarkSurfaceTint,
    inverseSurface = RobiaDarkInverseSurface,
    inverseOnSurface = RobiaDarkInverseOnSurface,
    error = RobiaDarkError,
    onError = RobiaDarkOnError,
    errorContainer = RobiaDarkErrorContainer,
    onErrorContainer = RobiaDarkOnErrorContainer,
    outline = RobiaDarkOutline,
    outlineVariant = RobiaDarkOutlineVariant,
    scrim = RobiaDarkScrim,
    surfaceBright = RobiaDarkSurfaceBright,
    surfaceDim = RobiaDarkSurfaceDim,
    surfaceContainer = RobiaDarkSurfaceContainer,
    surfaceContainerHigh = RobiaDarkSurfaceContainerHigh,
    surfaceContainerHighest = RobiaDarkSurfaceContainerHighest,
    surfaceContainerLow = RobiaDarkSurfaceContainerLow,
    surfaceContainerLowest = RobiaDarkSurfaceContainerLowest,
)

/** Robia tokens that Material's colour scheme has no role for. Read with `MaterialTheme.extras`. */
data class RobiaExtras(
    val isDark: Boolean,
    /** Top and bottom of the photo well behind a garment cutout; the same in both themes (R3-DEC-045). */
    val photoWellTop: Color,
    val photoWellBottom: Color,
)

internal val RobiaLightExtras = RobiaExtras(isDark = false, photoWellTop = RobiaPhotoWellTop, photoWellBottom = RobiaPhotoWellBottom)
internal val RobiaDarkExtras = RobiaExtras(isDark = true, photoWellTop = RobiaPhotoWellTop, photoWellBottom = RobiaPhotoWellBottom)

val LocalRobiaExtras = staticCompositionLocalOf { RobiaLightExtras }

val MaterialTheme.extras: RobiaExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalRobiaExtras.current

/** Light unless the choice is Dark, or Follow system on a system in dark mode (REQ-FB-14). */
fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemDark
}

val RobiaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal fun isReducedMotion(scale: Float): Boolean = scale == 0f

@Composable
fun RobiaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalRobiaExtras provides if (darkTheme) RobiaDarkExtras else RobiaLightExtras,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RobiaDarkColorScheme else RobiaLightColorScheme,
            typography = RobiaTypography,
            shapes = RobiaShapes,
            content = content,
        )
    }
}
