package com.gusanitolabs.robia.media.cutout

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
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

    override fun select(bitmap: Bitmap, x: Int, y: Int): FloatArray {
        val keypoint = NormalizedKeypoint.create(x.toFloat() / bitmap.width, y.toFloat() / bitmap.height)
        val result = segmenter.segment(
            BitmapImageBuilder(bitmap).build(),
            InteractiveSegmenterLegacy.RegionOfInterest.create(keypoint),
        )
        val mask = result.confidenceMasks().get()[0]
        check(mask.width == bitmap.width && mask.height == bitmap.height) { "Selection mask size does not match the photo" }
        val floats = ByteBufferExtractor.extract(mask).duplicate().apply { rewind() }
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        return FloatArray(bitmap.width * bitmap.height).also { floats.get(it) }
    }

    override fun close() {
        segmenter.close()
    }

    internal companion object {
        const val MAGIC_TOUCH_ASSET = "models/magic_touch.tflite"
    }
}
