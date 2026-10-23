package com.gusanitolabs.robia.media.cutout

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.gusanitolabs.robia.BuildConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenterlegacy.InteractiveSegmenterLegacy
import java.nio.ByteOrder

/** Selects the whole object under one point (S2 prompts). Implementations are created once per photo and closed after it. */
internal interface InteractiveSelector : AutoCloseable {
    /** Soft selection in 0..1, same size as [bitmap], for the object under pixel ([x], [y]). */
    fun select(bitmap: Bitmap, x: Int, y: Int): FloatArray
}

/** MediaPipe Interactive Segmenter with the bundled `magic_touch.tflite` (Apache-2.0, see [CutoutPack]). */
internal class MediaPipeInteractiveSelector(context: Context) : InteractiveSelector {
    private val segmenter: InteractiveSegmenterLegacy = InteractiveSegmenterLegacy.createFromOptions(
        context,
        InteractiveSegmenterLegacy.InteractiveSegmenterLegacyOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MAGIC_TOUCH_ASSET).build())
            .setOutputConfidenceMasks(true)
            .build(),
    )

    /**
     * The caller keeps ownership of [bitmap]. BitmapImageBuilder wraps the caller's bitmap; MPImage.close() recycles it,
     * so it must not be closed here. Only the confidence masks, which MediaPipe allocates, are closed.
     */
    override fun select(bitmap: Bitmap, x: Int, y: Int): FloatArray {
        val input = BitmapImageBuilder(bitmap).build()
        return selectWholeObject(bitmap.width, bitmap.height, x, y) { keypoint ->
            val masks = segmenter.segment(input, InteractiveSegmenterLegacy.RegionOfInterest.create(keypoint)).confidenceMasks().get()
            // D1 (R3-DEC-021): how many masks one tap returns, on the device; debug builds only.
            if (BuildConfig.DEBUG) Log.d(CUTOUT_LOG_TAG, "s2_masks=${masks.size} x=$x y=$y")
            masks
        }
    }

    override fun close() {
        segmenter.close()
    }

    internal companion object {
        const val MAGIC_TOUCH_ASSET = "models/magic_touch.tflite"
    }
}

/**
 * The part of [MediaPipeInteractiveSelector.select] after the segmenter call, so a JVM test can drive it with a fake.
 * [segment] gets the tap normalised to 0..1 and returns the confidence masks in the order the segmenter gave them; the
 * first one is the selection. Every mask is closed here, after its values are copied out.
 */
internal fun selectWholeObject(width: Int, height: Int, x: Int, y: Int, segment: (NormalizedKeypoint) -> List<MPImage>): FloatArray {
    val keypoint = NormalizedKeypoint.create(x.toFloat() / width, y.toFloat() / height)
    val masks = segment(keypoint)
    try {
        val mask = masks[0]
        check(mask.width == width && mask.height == height) { "Selection mask size does not match the photo" }
        val floats = ByteBufferExtractor.extract(mask).duplicate().apply { rewind() }
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        return FloatArray(width * height).also { floats.get(it) }
    } finally {
        // Copied out above; the native mask memory is released here.
        masks.forEach { it.close() }
    }
}
