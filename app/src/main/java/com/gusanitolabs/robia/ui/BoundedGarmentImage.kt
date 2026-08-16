package com.gusanitolabs.robia.ui

import android.net.Uri
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
    val targetBounds = remember(measuredSize, boundedMaxEdgePx, allowOriginal) {
        if (allowOriginal) {
            ImageTargetBounds(0, 0)
        } else {
            val measured = if (measuredSize.width > 0 && measuredSize.height > 0) {
                ImageTargetBounds(measuredSize.width, measuredSize.height)
            } else {
                ImageTargetBounds(
                    widthPx = (boundedMaxEdgePx * 3 / 4).coerceAtLeast(1),
                    heightPx = boundedMaxEdgePx,
                )
            }
            measured.boundedBy(boundedMaxEdgePx)
        }
    }
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
}
