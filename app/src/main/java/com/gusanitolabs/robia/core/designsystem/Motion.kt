package com.gusanitolabs.robia.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** True when the system "remove animations" setting is on (animator duration scale is 0). */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

private const val SPARKLE_START_RADIUS_DP = 24f
private const val SPARKLE_END_RADIUS_DP = 56f
internal const val SPARKLE_DURATION_MILLIS = 900
private const val SPARKLE_GLYPH = "✦"
private const val SPARKLE_FONT_SIZE_SP = 14f

/** Press feedback: scales the modified element down while pressed. No-op when reduced motion is on. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Scales and fades [content] in once [visible] becomes true; appears immediately when reduced motion is on. */
@Composable
fun PopIn(visible: Boolean, delayMillis: Int = 0, content: @Composable () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            scaleIn(
                initialScale = 0.4f,
                animationSpec = keyframes {
                    durationMillis = 300
                    this.delayMillis = delayMillis
                    1.12f at 200
                },
            ) + fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = delayMillis))
        },
        exit = if (reducedMotion) ExitTransition.None else fadeOut(),
    ) {
        content()
    }
}

/** Whether [SparkleBurst] animates. Reduced motion draws nothing. */
internal fun sparkleBurstPlays(reducedMotion: Boolean): Boolean = !reducedMotion

/** The Edit photo burst plays once, when processing ends and its button is shown again. */
internal fun sparkleBurstAfterProcessing(wasProcessing: Boolean, isProcessing: Boolean, buttonVisible: Boolean): Boolean =
    wasProcessing && !isProcessing && buttonVisible

/**
 * One-shot burst of small sparkle glyphs that move outward and fade over 900 ms; draws nothing when reduced motion is on.
 * [onFinished] runs when the burst ends, or at once when reduced motion is on.
 */
@Composable
fun SparkleBurst(modifier: Modifier, count: Int = 7, seed: Int = 0, onFinished: () -> Unit = {}) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(0f) }
    val glyphColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    LaunchedEffect(reducedMotion) {
        if (sparkleBurstPlays(reducedMotion)) {
            progress.animateTo(1f, tween(durationMillis = SPARKLE_DURATION_MILLIS, easing = LinearEasing))
        }
        onFinished()
    }
    if (!sparkleBurstPlays(reducedMotion)) return
    Canvas(modifier) {
        val fraction = progress.value
        val glyph = textMeasurer.measure(SPARKLE_GLYPH, TextStyle(fontSize = SPARKLE_FONT_SIZE_SP.sp))
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfGlyph = Offset(glyph.size.width / 2f, glyph.size.height / 2f)
        sparklePositions(count, seed, fraction).forEach { (dx, dy) ->
            drawText(
                textLayoutResult = glyph,
                color = glyphColor,
                topLeft = center + Offset(dx.dp.toPx(), dy.dp.toPx()) - halfGlyph,
                alpha = 1f - fraction,
            )
        }
    }
}

/** Glyph offsets in dp from the burst centre: fixed angles per index, radius 24 → 56 dp as [progress] goes 0 → 1. */
internal fun sparklePositions(count: Int, seed: Int, progress: Float): List<Pair<Float, Float>> {
    val radius = SPARKLE_START_RADIUS_DP + (SPARKLE_END_RADIUS_DP - SPARKLE_START_RADIUS_DP) * progress
    return List(count) { index ->
        val angle = Math.toRadians((360f / count * index + seed * 13).toDouble())
        Pair((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
    }
}
