package com.gusanitolabs.robia.ui

import android.net.Uri
import android.os.SystemClock
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.gusanitolabs.robia.BuildConfig
import com.gusanitolabs.robia.media.BoundedThumbnail
import com.gusanitolabs.robia.media.ClothingImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BoundedGarmentImage(
    photoUri: String,
    modifier: Modifier = Modifier,
    thumbnailMaxEdgePx: Int? = null,
) {
    val context = LocalContext.current
    var resolvedUri by remember(photoUri, thumbnailMaxEdgePx) {
        mutableStateOf<String?>(
            if (thumbnailMaxEdgePx?.takeIf { it > 0 } == null) photoUri else null,
        )
    }

    LaunchedEffect(context, photoUri, thumbnailMaxEdgePx) {
        val maxEdgePx = thumbnailMaxEdgePx?.takeIf { it > 0 }
        if (maxEdgePx == null) {
            resolvedUri = photoUri
            return@LaunchedEffect
        }

        // Avoid assigning the canonical full-size URI until the bounded cache lookup fails.
        // Otherwise ImageView may decode the original before the sampled thumbnail is ready.
        resolvedUri = null
        val sourceUri = Uri.parse(photoUri)
        val startedAt = SystemClock.elapsedRealtime()
        val thumbnail = withContext(Dispatchers.IO) {
            runCatching { ClothingImageStore.getOrCreateBoundedThumbnail(context, sourceUri, maxEdgePx) }.getOrNull()
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        if (thumbnail != null) {
            val resolved = thumbnail
            resolvedUri = resolved.uri.toString()
            logThumbnailEvent(thumbnail = resolved, elapsedMs = elapsedMs)
        } else {
            resolvedUri = photoUri
        }
    }

    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { imageView ->
            val nextUri = resolvedUri
            if (nextUri == null) {
                if (imageView.tag != null) {
                    imageView.tag = null
                    imageView.setImageDrawable(null)
                }
            } else if (imageView.tag != nextUri) {
                imageView.tag = nextUri
                imageView.setImageURI(Uri.parse(nextUri))
            }
        },
        modifier = modifier,
    )
}

private fun logThumbnailEvent(
    thumbnail: BoundedThumbnail,
    elapsedMs: Long,
) {
    if (!BuildConfig.DEBUG) return
    android.util.Log.i(
        "RobiaPerformance",
        buildString {
            append("thumbnail_stage")
            append(" elapsedMs=").append(elapsedMs.coerceAtLeast(0L))
            append(" source=").append(thumbnail.source.width).append('x').append(thumbnail.source.height)
            thumbnail.source.byteSize?.let { bytes -> append(" sourceBytes=").append(bytes) }
            append(" thumbnail=")
            append(thumbnail.thumbnail?.width ?: 0).append('x').append(thumbnail.thumbnail?.height ?: 0)
            thumbnail.thumbnail?.byteSize?.let { bytes -> append(" thumbnailBytes=").append(bytes) }
            append(" maxEdgePx=").append(thumbnail.maxEdgePx)
            append(" cacheHit=").append(thumbnail.cacheHit)
        },
    )
}
