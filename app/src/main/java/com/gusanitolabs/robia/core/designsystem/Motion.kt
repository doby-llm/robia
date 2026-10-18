package com.gusanitolabs.robia.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
private const val SPARKLE_DURATION_MILLIS = 900
private const val SPARKLE_GLYPH = "✦"
private const val SPARKLE_FONT_SIZE_SP = 14f

private const val SHIMMER_ANGLE_DEGREES = 100.0
private const val SHIMMER_SPAN_DP = 240f
private const val SHIMMER_ACTIVE_PERIOD_MILLIS = 1400
private const val SHIMMER_IDLE_PASS_MILLIS = 2600
private const val SHIMMER_IDLE_PERIOD_MILLIS = 5600
private val ShimmerBase = Color(0xFF332421)
private val ShimmerMid = Color(0xFF5A3A2C)
private val ShimmerBand = Color(0x59FFDCBE)

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

/** Warm gradient that sweeps across the surface; idle sweeps once then rests, active sweeps continuously. */
@Composable
fun rememberAiShimmerBrush(active: Boolean): Brush {
    val phase = if (LocalReducedMotion.current) 0f else rememberAiShimmerPhase(active)
    val spanPx = with(LocalDensity.current) { SHIMMER_SPAN_DP.dp.toPx() }
    return remember(phase, spanPx) { aiShimmerBrush(phase, spanPx) }
}

/** Clips to [shape] and paints the AI shimmer behind the content. */
fun Modifier.aiSurface(active: Boolean = false, shape: Shape = CircleShape): Modifier = composed {
    clip(shape).background(rememberAiShimmerBrush(active))
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

/** One-shot burst of small sparkle glyphs that move outward and fade over 900 ms; draws nothing when reduced motion is on. */
@Composable
fun SparkleBurst(modifier: Modifier, count: Int = 7, seed: Int = 0) {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(0f) }
    val glyphColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            progress.animateTo(1f, tween(durationMillis = SPARKLE_DURATION_MILLIS, easing = LinearEasing))
        }
    }
    if (reducedMotion) return
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

@Composable
private fun rememberAiShimmerPhase(active: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "aiShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = if (active) {
                tween(durationMillis = SHIMMER_ACTIVE_PERIOD_MILLIS, easing = LinearEasing)
            } else {
                keyframes {
                    durationMillis = SHIMMER_IDLE_PERIOD_MILLIS
                    1f at SHIMMER_IDLE_PASS_MILLIS
                }
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "aiShimmerPhase",
    )
    return phase
}

private fun aiShimmerBrush(phase: Float, spanPx: Float): Brush {
    val radians = Math.toRadians(SHIMMER_ANGLE_DEGREES)
    val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    val start = direction * (phase * spanPx)
    return Brush.linearGradient(
        0f to ShimmerBase,
        0.45f to ShimmerMid,
        0.5f to ShimmerBand,
        0.55f to ShimmerMid,
        1f to ShimmerBase,
        start = start,
        end = start + direction * spanPx,
        tileMode = TileMode.Repeated,
    )
}
