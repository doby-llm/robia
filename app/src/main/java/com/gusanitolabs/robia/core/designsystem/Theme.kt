package com.gusanitolabs.robia.core.designsystem

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import com.gusanitolabs.robia.core.model.ColorPalette
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

/** One signed review chip family: the sign, its glyph, the chip fill and text, and the chip outline (REQ-FB-21). */
class RobiaChangeColors(
    val sign: Color,
    val onSign: Color,
    val container: Color,
    val onContainer: Color,
    val border: Color,
)

/** Robia tokens that Material's colour scheme has no role for. Read with `MaterialTheme.extras`. */
data class RobiaExtras(
    val isDark: Boolean,
    /** Top and bottom of the photo well behind a garment cutout; the same in both themes (R3-DEC-045). */
    val photoWellTop: Color,
    val photoWellBottom: Color,
    /** Green "+" chips of the review: added tags (REQ-FB-21). */
    val addColors: RobiaChangeColors,
    /** Red "−" chips of the review: removed tags (REQ-FB-21). */
    val removeColors: RobiaChangeColors,
    /** The sparkle icon on a filled primary button, such as the review prompts (ui3 F21, R3-DEC-057). */
    val sparkOnPrimary: Color,
    /** Light scheme of the active palette; ink on the always-light photo well, in both modes (round 5). */
    val wellScheme: ColorScheme,
)

internal val RobiaLightExtras = RobiaExtras(
    isDark = false,
    photoWellTop = RobiaPhotoWellTop,
    photoWellBottom = RobiaPhotoWellBottom,
    addColors = RobiaChangeColors(
        sign = RobiaAddSign,
        onSign = RobiaOnAddSign,
        container = RobiaAddContainer,
        onContainer = RobiaOnAddContainer,
        border = RobiaAddBorder,
    ),
    removeColors = RobiaChangeColors(
        sign = RobiaRemoveSign,
        onSign = RobiaOnRemoveSign,
        container = RobiaRemoveContainer,
        onContainer = RobiaOnRemoveContainer,
        border = RobiaRemoveBorder,
    ),
    sparkOnPrimary = RobiaOnTertiaryContainer,
    wellScheme = RobiaLightColorScheme,
)
internal val RobiaDarkExtras = RobiaExtras(
    isDark = true,
    photoWellTop = RobiaPhotoWellTop,
    photoWellBottom = RobiaPhotoWellBottom,
    addColors = RobiaChangeColors(
        sign = RobiaDarkAddSign,
        onSign = RobiaDarkOnAddSign,
        container = RobiaDarkAddContainer,
        onContainer = RobiaDarkOnAddContainer,
        border = RobiaDarkAddBorder,
    ),
    removeColors = RobiaChangeColors(
        sign = RobiaDarkRemoveSign,
        onSign = RobiaDarkOnRemoveSign,
        container = RobiaDarkRemoveContainer,
        onContainer = RobiaDarkOnRemoveContainer,
        border = RobiaDarkRemoveBorder,
    ),
    sparkOnPrimary = RobiaDarkSparkOnPrimary,
    wellScheme = RobiaLightColorScheme,
)

val LocalRobiaExtras = staticCompositionLocalOf { RobiaLightExtras }

val MaterialTheme.extras: RobiaExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalRobiaExtras.current

/** The colour scheme of [palette] in the requested mode (REQ-TH-2). */
fun robiaColorScheme(palette: ColorPalette, dark: Boolean): ColorScheme = when (palette) {
    ColorPalette.EarthSteel -> if (dark) RobiaDarkColorScheme else RobiaLightColorScheme
    ColorPalette.TobaccoBordeaux -> if (dark) TobaccoDarkColorScheme else TobaccoLightColorScheme
}

/** The [RobiaExtras] of [palette] in the requested mode (REQ-TH-2). */
fun robiaExtras(palette: ColorPalette, dark: Boolean): RobiaExtras = when (palette) {
    ColorPalette.EarthSteel -> if (dark) RobiaDarkExtras else RobiaLightExtras
    ColorPalette.TobaccoBordeaux -> if (dark) TobaccoDarkExtras else TobaccoLightExtras
}

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
fun RobiaTheme(
    darkTheme: Boolean = false,
    palette: ColorPalette = ColorPalette.TobaccoBordeaux,
    content: @Composable () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalRobiaExtras provides robiaExtras(palette, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = robiaColorScheme(palette, darkTheme),
            typography = RobiaTypography,
            shapes = RobiaShapes,
            content = content,
        )
    }
}
