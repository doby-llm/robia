package com.gusanitolabs.robia.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.media.ClothingImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SWEEP_DURATION_MILLIS = 2200
private const val SWEEP_BAND_FRACTION = 0.28f
private const val SWEEP_ANGLE_DEGREES = 18f
private const val SWEEP_ALPHA = 0.55f
private const val SHIMMER_DURATION_MILLIS = 1800
private const val SHIMMER_ALPHA_WHITE = 0.85f
private const val SHIMMER_ALPHA_PEACH = 0.9f
private const val GLOW_BLUR_DP = 6f
private const val GLOW_ALPHA = 0.45f
private const val FOREGROUND_MAX_EDGE_PX = 1024
private const val SEGMENT_FILL_MILLIS = 400
private const val SEGMENT_HEIGHT_DP = 4f
private const val SEGMENT_SPACING_DP = 6f
private val GlowColor = Color(0xFFFFD2AE)
private val ShimmerPeach = Color(0xFFFFE2C4)

/** Step 1-3 of the processing sequence shown to the user (TS§7). */
internal fun processingStep(stage: PhotoProcessingStage): Int = when (stage) {
    PhotoProcessingStage.RemovingBackground -> 1
    PhotoProcessingStage.CroppingPicture -> 2
    PhotoProcessingStage.ExtractingColor -> 3
    PhotoProcessingStage.PreparingSuggestions -> 3
    PhotoProcessingStage.ApplyingQuickEdit -> 2
}

/** One soft light band crossing the photo every 2200 ms while processing; draws nothing when reduced motion is on. */
@Composable
internal fun SweepOverlay(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) return
    val transition = rememberInfiniteTransition(label = "processingSweep")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "processingSweepProgress",
    )
    Canvas(modifier.clipToBounds()) {
        val bandWidth = size.width * SWEEP_BAND_FRACTION
        val left = -bandWidth + (size.width + bandWidth) * progress
        rotate(degrees = SWEEP_ANGLE_DEGREES) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = SWEEP_ALPHA),
                        Color.White.copy(alpha = 0f),
                    ),
                    startX = left,
                    endX = left + bandWidth,
                ),
                topLeft = Offset(left, -size.height),
                size = Size(bandWidth, size.height * 3f),
            )
        }
    }
}

/**
 * The garment cut-out with a warm shimmer that runs only inside its silhouette (SrcAtop) and a soft
 * halo behind it. Static (no shimmer) when reduced motion is on.
 */
@Composable
internal fun MaskedShimmer(foregroundUri: String, modifier: Modifier = Modifier) {
    val foreground = rememberForegroundBitmap(foregroundUri) ?: return
    val shimmerPhase = if (LocalReducedMotion.current) null else rememberShimmerPhase()
    Box(modifier = modifier) {
        Image(
            bitmap = foreground,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(GlowColor),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = GLOW_ALPHA
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect
                            .createBlurEffect(GLOW_BLUR_DP.dp.toPx(), GLOW_BLUR_DP.dp.toPx(), Shader.TileMode.DECAL)
                            .asComposeRenderEffect()
                    }
                },
        )
        Image(
            bitmap = foreground,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val phase = shimmerPhase?.value ?: return@drawWithContent
                    val x = -size.width + 2f * size.width * phase
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.44f to Color.White.copy(alpha = SHIMMER_ALPHA_WHITE),
                            0.5f to ShimmerPeach.copy(alpha = SHIMMER_ALPHA_PEACH),
                            0.56f to Color.Transparent,
                            1f to Color.Transparent,
                            start = Offset(x, 0f),
                            end = Offset(x + size.width, 0f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}

/** Segmented bar with one segment per step; segments up to [current] are filled (instant with reduced motion). */
@Composable
internal fun SegmentedProgress(current: Int, modifier: Modifier = Modifier, steps: Int = 3) {
    val reducedMotion = LocalReducedMotion.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SEGMENT_SPACING_DP.dp)) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(
                targetValue = if (index < current) 1f else 0f,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = SEGMENT_FILL_MILLIS),
                label = "processingSegmentFill",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT_DP.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** Text for the current processing step; [label] replaces it while set (e.g. background module preparing). */
@Composable
internal fun ProcessingStageLabel(step: Int, modifier: Modifier = Modifier, label: String? = null) {
    val stepText = stringResource(
        when (step) {
            2 -> R.string.processing_step_edges
            3 -> R.string.processing_step_style
            else -> R.string.processing_step_find
        },
    )
    Text(
        text = label ?: stepText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "maskedShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "maskedShimmerPhase",
    )
}

@Composable
private fun rememberForegroundBitmap(foregroundUri: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, context, foregroundUri) {
        value = withContext(Dispatchers.IO) { decodeForegroundBitmap(context, Uri.parse(foregroundUri)) }
    }
    return bitmap
}

private fun decodeForegroundBitmap(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val (width, height) = ClothingImageStore.readImageDimensions(context, uri) ?: return@runCatching null
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = foregroundSampleSize(width, height)
    }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }?.asImageBitmap()
}.getOrNull()

private fun foregroundSampleSize(width: Int, height: Int): Int {
    val longestEdge = maxOf(width, height)
    var sampleSize = 1
    while (longestEdge / sampleSize > FOREGROUND_MAX_EDGE_PX) {
        sampleSize *= 2
    }
    return sampleSize
}
