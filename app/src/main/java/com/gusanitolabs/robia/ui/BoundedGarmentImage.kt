package com.gusanitolabs.robia.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.gusanitolabs.robia.media.ImagePipeline
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.media.ImageRequest
import com.gusanitolabs.robia.media.ImageTargetBounds

private const val DETAIL_DISPLAY_MAX_EDGE_PX = 512

/**
 * Shared display binding for garment surfaces. Display callers pass explicit bounds;
 * export still owns original-resolution sharing by opting into allowOriginal.
 */
@Composable
internal fun BoundedGarmentImage(
    photoUri: String,
    modifier: Modifier = Modifier,
    thumbnailMaxEdgePx: Int? = null,
    purpose: ImagePurpose = ImagePurpose.Browse,
    sourceRevision: String? = null,
) {
    if (photoUri.isBlank()) return

    val context = LocalContext.current
    val pipeline = remember(context) { ImagePipeline.shared(context) }
    val allowOriginal = thumbnailMaxEdgePx == null
    val boundedMaxEdgePx = thumbnailMaxEdgePx?.takeIf { it > 0 } ?: DETAIL_DISPLAY_MAX_EDGE_PX
    var measuredSize by remember(photoUri, purpose) { mutableStateOf(IntSize.Zero) }
    val targetBounds = if (allowOriginal) {
        ImageTargetBounds(0, 0)
    } else {
        boundedImageTargetBounds(measuredSize.width, measuredSize.height, boundedMaxEdgePx)
    }
    // Until the cell is measured the swatch behind the card is the placeholder; no guessed-size request is issued.
    if (targetBounds != null) {
        val request = remember(photoUri, purpose, sourceRevision, targetBounds, boundedMaxEdgePx, allowOriginal) {
            ImageRequest(
                sourceUri = Uri.parse(photoUri),
                purpose = purpose,
                targetBounds = targetBounds,
                sourceRevision = sourceRevision,
                maxEdgePx = boundedMaxEdgePx.takeIf { !allowOriginal },
                allowOriginal = allowOriginal,
            )
        }

        pipeline.Display(
            request = request,
            modifier = modifier.onSizeChanged { measuredSize = it },
        )
    } else {
        Box(modifier = modifier.onSizeChanged { measuredSize = it })
    }
}

/** Measured cell size bounded to [maxEdgePx]; null until the cell has been measured. */
internal fun boundedImageTargetBounds(
    measuredWidthPx: Int,
    measuredHeightPx: Int,
    maxEdgePx: Int,
): ImageTargetBounds? {
    if (measuredWidthPx <= 0 || measuredHeightPx <= 0) return null
    return ImageTargetBounds(measuredWidthPx, measuredHeightPx).boundedBy(maxEdgePx)
}
