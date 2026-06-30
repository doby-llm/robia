package com.gusanitolabs.robia.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gusanitolabs.robia.media.ClothingImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberProcessingOverlayColors(
    photoUri: String?,
    isProcessing: Boolean,
): ProcessingOverlayColors {
    val context = LocalContext.current
    var sampledContentColor by remember(photoUri) { mutableStateOf<Color?>(null) }

    LaunchedEffect(context, photoUri, isProcessing) {
        sampledContentColor = null
        val uri = photoUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!isProcessing) return@LaunchedEffect

        val luminance = withContext(Dispatchers.IO) {
            runCatching { ClothingImageStore.estimateCentralLuminance(context, Uri.parse(uri)) }.getOrNull()
        }
        sampledContentColor = luminance?.let { if (it < DARK_LUMINANCE_THRESHOLD) Color.White else Color.Black }
    }

    val contentColor = sampledContentColor ?: MaterialTheme.colorScheme.onSurface
    val scrimColor = when (contentColor) {
        Color.White -> Color.Black.copy(alpha = DARK_SCRIM_ALPHA)
        Color.Black -> Color.White.copy(alpha = LIGHT_SCRIM_ALPHA)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = LIGHT_SCRIM_ALPHA)
    }
    return ProcessingOverlayColors(contentColor = contentColor, scrimColor = scrimColor)
}

internal data class ProcessingOverlayColors(
    val contentColor: Color,
    val scrimColor: Color,
)

private const val DARK_LUMINANCE_THRESHOLD = 0.5f
private const val DARK_SCRIM_ALPHA = 0.32f
private const val LIGHT_SCRIM_ALPHA = 0.40f
