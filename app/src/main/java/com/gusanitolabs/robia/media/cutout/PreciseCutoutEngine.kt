package com.gusanitolabs.robia.media.cutout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import com.gusanitolabs.robia.media.BackgroundRemovalResult
import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import com.gusanitolabs.robia.media.ClothingImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val WORK_LONG_EDGE_PX = 1024
private const val SALIENCY_SIZE = 320
private const val DETECTOR_SIZE = 640
private const val SAM_EMBEDDING_CHANNELS = 256
private const val SAM_EMBEDDING_SIDE = 64
private const val SAM_MASK_SIDE = 256
private const val SAM_MASKS = 4
private const val DFINE_CLASSES = 366
private const val HANGER_CLASS = 101
private const val TOP_HANGER_CANDIDATES = 5
private const val GARMENT_OVERLAP_MIN = 0.6f
private const val CROP_ALPHA_THRESHOLD = 24
private const val CROP_PAD_FRACTION = 0.04f
private const val NANOS_PER_MILLI = 1_000_000L
private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
private val CHANNEL_SHIFTS = intArrayOf(16, 8, 0)

/**
 * Precise cutout (REQ-SG-05): saliency → garment selection → hanger detection → hanger removal → hanger-first re-selection
 * → island filter and edges. Stage order, thresholds and the work size (long edge 1024) follow `pipeline_v11.py`
 * with BAND=1 FILL=median. Throws on any failure; [PhotoBackgroundRemover] turns that into the ML Kit fallback.
 * Sessions and the selector live for one photo only.
 */
internal class PreciseCutoutEngine(
    private val packDir: File,
    private val selectorFactory: (Context) -> InteractiveSelector = ::MediaPipeInteractiveSelector,
) : BackgroundRemovalEngine {
    override suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult = withContext(Dispatchers.Default) {
        val timings = LinkedHashMap<String, Long>()
        val work = timings.timed("decode") { decodeWorkBitmap(context, sourceUri) }
        try {
            val argb = IntArray(work.width * work.height).also { work.getPixels(it, 0, work.width, 0, 0, work.width, work.height) }
            val callerContext = coroutineContext
            val alpha = OnnxSessions(packDir).use { models ->
                selectorFactory(context).use { selector ->
                    CutoutRun(models, selector, work, argb, timings, checkpoint = { callerContext.ensureActive() }).alpha()
                }
            }
            val composed = Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(IntArray(argb.size) { i -> ((alpha[i].toInt() and 0xFF) shl 24) or (argb[i] and 0xFFFFFF) }, 0, work.width, 0, 0, work.width, work.height)
            }
            val cropped = cropToAlpha(composed, alpha)
            val outputUri = try {
                ClothingImageStore.writeProcessedBitmap(context, cropped)
            } finally {
                cropped.recycle()
                if (cropped !== composed) composed.recycle()
            }
            BackgroundRemovalResult(
                originalUri = sourceUri,
                outputUri = outputUri,
                status = BackgroundRemovalStatus.REMOVED,
                timings = timings.toMap(),
            )
        } finally {
            work.recycle()
        }
    }

    private inline fun <T> MutableMap<String, Long>.timed(key: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            this[key] = (System.nanoTime() - started) / NANOS_PER_MILLI
        }
    }

    /** Crop to alpha above 24 with a 4 % pad of the long edge (the pipeline's output rule). */
    private fun cropToAlpha(source: Bitmap, alpha: ByteArray): Bitmap {
        val w = source.width
        val h = source.height
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((alpha[y * w + x].toInt() and 0xFF) > CROP_ALPHA_THRESHOLD) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        check(right >= 0) { "No foreground left after the precise cutout" }
        val pad = (CROP_PAD_FRACTION * max(w, h)).toInt()
        val x0 = max(left - pad, 0)
        val y0 = max(top - pad, 0)
        val x1 = min(right + pad, w)
        val y1 = min(bottom + pad, h)
        return Bitmap.createBitmap(source, x0, y0, x1 - x0, y1 - y0)
    }
}

/** One photo through the pipeline. [work] and [argb] are the same 1024-long-edge pixels. */
private class CutoutRun(
    private val models: OnnxSessions,
    private val selector: InteractiveSelector,
    private val work: Bitmap,
    private val argb: IntArray,
    private val timings: MutableMap<String, Long>,
    /** Throws when the caller was cancelled (the 30 s budget); stages are CPU-bound and cannot be interrupted otherwise. */
    private val checkpoint: () -> Unit,
) {
    private val w = work.width
    private val h = work.height

    /** Per-pixel alpha 0..255 of the work photo. */
    fun alpha(): ByteArray {
        val coarse = stage("s1") { saliency(argb) }
        val selection = stage("s2") { garmentSelection(coarse, work) }
        val hard = MaskOps.thresholded(selection)
        val garment = MaskOps.bbox(hard, w, h) ?: error("Garment selection is empty")
        val garmentArea = MaskOps.count(hard)

        var result = selection
        val hanger = stage("s3") { detectHanger(garment) }
        if (hanger != null) {
            val kill = stage("s4") { hangerKill(hanger, selection, garment, garmentArea) }
            if (kill != null) {
                result = stage("s4b") { hangerFirst(selection, kill, hanger, garment.height) }
            }
        }
        return stage("s5") {
            MaskOps.refineAlpha(MaskOps.luminance(argb), MaskOps.dropIslands(result, w, h), w, h)
        }
    }

    /** S1: u2netp saliency, min-max normalised and resampled to the work size. */
    private fun saliency(pixels: IntArray): FloatArray {
        val planes = CHANNEL_SHIFTS.map { MaskOps.channel(pixels, it) }
        val resized = planes.map { MaskOps.resizeBilinear(it, w, h, SALIENCY_SIZE, SALIENCY_SIZE) }
        val denominator = max(resized.maxOf { plane -> plane.max() }, 1e-6f)
        val input = FloatArray(3 * SALIENCY_SIZE * SALIENCY_SIZE)
        for (c in 0 until 3) {
            for (i in resized[c].indices) {
                input[c * resized[c].size + i] = (resized[c][i] / denominator - IMAGENET_MEAN[c]) / IMAGENET_STD[c]
            }
        }
        val output = models.run(
            models.saliency,
            mapOf("input.1" to FloatTensor(input, longArrayOf(1, 3, SALIENCY_SIZE.toLong(), SALIENCY_SIZE.toLong()))),
        )[0]
        val low = output.min()
        val high = output.max()
        val normalised = FloatArray(output.size) { (output[it] - low) / max(high - low, 1e-6f) }
        return MaskOps.resizeBilinear(normalised, SALIENCY_SIZE, SALIENCY_SIZE, w, h)
    }

    /** S2: magic_touch at three automatic points; a selection counts when ≥ 60 % of it lies in the coarse mask. */
    private fun garmentSelection(coarse: FloatArray, bitmap: Bitmap): FloatArray {
        val hard = MaskOps.thresholded(coarse)
        val selection = FloatArray(w * h)
        for (point in MaskOps.thirdPoints(hard, w, h)) {
            val mask = selector.select(bitmap, point.x, point.y)
            val maskHard = MaskOps.thresholded(mask)
            if (MaskOps.count(maskHard) > 0 && MaskOps.overlap(maskHard, hard) >= GARMENT_OVERLAP_MIN) {
                for (i in selection.indices) selection[i] = max(selection[i], mask[i])
            }
        }
        return if (selection.any { it > 0f }) selection else coarse.copyOf()
    }

    /** S3: D-FINE Hanger class; the first pass on the whole photo, then the top band of the garment if nothing is plausible. */
    private fun detectHanger(garment: MaskBox): MaskBox? {
        val first = detectHangers(argb, w, h).filter { HangerRemoval.isPlausible(it.second, garment) }
        val found = if (first.isNotEmpty()) {
            first
        } else {
            val region = HangerRemoval.hangerSearchRegion(garment, w, h)
            val regionW = region[2] - region[0]
            val regionH = region[3] - region[1]
            detectHangers(cropPixels(argb, w, region, regionW, regionH), regionW, regionH)
                .map { (score, box) ->
                    score to MaskBox(box.x0 + region[0], box.y0 + region[1], box.x1 + region[0], box.y1 + region[1])
                }
                .filter { HangerRemoval.isPlausible(it.second, garment) }
        }
        return found.firstOrNull()?.second?.let { HangerRemoval.clip(it, w, h) }
    }

    /** Detections above the threshold among the top candidates, best first, boxes in the coordinates of [pixels]. */
    private fun detectHangers(pixels: IntArray, pw: Int, ph: Int): List<Pair<Float, MaskBox>> {
        val input = resampleTensor(pixels, pw, ph, DETECTOR_SIZE)
        val outputs = models.run(models.hangerDetector, mapOf("pixel_values" to input))
        val logits = outputs[0]
        val boxes = outputs[1]
        val queries = logits.size / DFINE_CLASSES
        val scores = FloatArray(queries) { q -> sigmoid(logits[q * DFINE_CLASSES + HANGER_CLASS]) }
        val result = ArrayList<Pair<Float, MaskBox>>()
        for (q in (0 until queries).sortedByDescending { scores[it] }.take(TOP_HANGER_CANDIDATES)) {
            val score = scores[q]
            if (score < HangerRemoval.SCORE_THRESHOLD) break
            val cx = boxes[q * 4]
            val cy = boxes[q * 4 + 1]
            val bw = boxes[q * 4 + 2]
            val bh = boxes[q * 4 + 3]
            result += score to MaskBox(
                ((cx - bw / 2) * pw).toInt(),
                ((cy - bh / 2) * ph).toInt(),
                ((cx + bw / 2) * pw).toInt(),
                ((cy + bh / 2) * ph).toInt(),
            )
        }
        return result
    }

    /** S4: MobileSAM box-only hanger mask and garment mask, then the kill rule (null when it would exceed 15 %). */
    private fun hangerKill(hanger: MaskBox, selection: FloatArray, garment: MaskBox, garmentArea: Int): BooleanArray? {
        val embedding = samEmbedding()
        val hangerProb = samDecode(embedding, hanger, emptyList(), emptyList())
        val core = MaskOps.corePoints(selection, w, h, 3)
        val negative = MaskPoint((hanger.x0 + hanger.x1) / 2, hanger.y0 + max(2, hanger.height / 6))
        val garmentProb = samDecode(embedding, garment, core, listOf(negative))
        return HangerRemoval.killMask(hangerProb, garmentProb, hanger, w, h, garmentArea)
    }

    /** S4b: fill the hanger, run S1+S2 again on the filled photo, and keep the previous result below the band. */
    private fun hangerFirst(selection: FloatArray, kill: BooleanArray, hanger: MaskBox, garmentHeight: Int): FloatArray {
        val support = HangerRemoval.killSupport(kill, w, h)
        val filled = HangerRemoval.fill(argb, w, h, support)
        val filledBitmap = Bitmap.createBitmap(filled, w, h, Bitmap.Config.ARGB_8888)
        try {
            val reselected = garmentSelection(saliency(filled), filledBitmap)
            return HangerRemoval.applyBand(
                reselected = reselected,
                previous = selection,
                support = support,
                bandBottom = HangerRemoval.bandBottom(hanger, garmentHeight, h),
                w = w,
            )
        } finally {
            filledBitmap.recycle()
        }
    }

    /** MobileSAM image embedding of the work photo (long edge 1024, so no resize is needed). */
    private fun samEmbedding(): FloatArray {
        val data = FloatArray(w * h * 3)
        for (i in 0 until w * h) {
            for (c in 0 until 3) data[i * 3 + c] = ((argb[i] shr CHANNEL_SHIFTS[c]) and 0xFF).toFloat()
        }
        return models.run(
            models.samEncoder,
            mapOf("input_image" to FloatTensor(data, longArrayOf(h.toLong(), w.toLong(), 3))),
        )[0]
    }

    /** One MobileSAM decode: positive/negative points plus the box corners; the best of four masks by IoU, as probabilities. */
    private fun samDecode(embedding: FloatArray, box: MaskBox, positive: List<MaskPoint>, negative: List<MaskPoint>): FloatArray {
        val coords = ArrayList<Float>()
        val labels = ArrayList<Float>()
        for (p in positive) {
            coords += p.x.toFloat()
            coords += p.y.toFloat()
            labels += 1f
        }
        for (p in negative) {
            coords += p.x.toFloat()
            coords += p.y.toFloat()
            labels += 0f
        }
        coords += listOf(box.x0.toFloat(), box.y0.toFloat(), box.x1.toFloat(), box.y1.toFloat())
        labels += listOf(2f, 3f)
        val points = labels.size
        val outputs = models.run(
            models.samDecoder,
            mapOf(
                "image_embeddings" to FloatTensor(embedding, longArrayOf(1, SAM_EMBEDDING_CHANNELS.toLong(), SAM_EMBEDDING_SIDE.toLong(), SAM_EMBEDDING_SIDE.toLong())),
                "point_coords" to FloatTensor(coords.toFloatArray(), longArrayOf(1, points.toLong(), 2)),
                "point_labels" to FloatTensor(labels.toFloatArray(), longArrayOf(1, points.toLong())),
                "mask_input" to FloatTensor(FloatArray(SAM_MASK_SIDE * SAM_MASK_SIDE), longArrayOf(1, 1, SAM_MASK_SIDE.toLong(), SAM_MASK_SIDE.toLong())),
                "has_mask_input" to FloatTensor(floatArrayOf(0f), longArrayOf(1)),
                "orig_im_size" to FloatTensor(floatArrayOf(h.toFloat(), w.toFloat()), longArrayOf(2)),
            ),
        )
        val masks = outputs[0]
        val iou = outputs[1]
        var best = 0
        for (k in 1 until SAM_MASKS) if (iou[k] > iou[best]) best = k
        val plane = w * h
        return FloatArray(plane) { sigmoid(masks[best * plane + it]) }
    }

    private fun cropPixels(pixels: IntArray, pw: Int, region: IntArray, regionW: Int, regionH: Int): IntArray =
        IntArray(regionW * regionH) { i -> pixels[(region[1] + i / regionW) * pw + region[0] + i % regionW] }

    /** Planar CHW float tensor of [pixels] resized to [side]×[side], 0..1 (the detector's input). */
    private fun resampleTensor(pixels: IntArray, pw: Int, ph: Int, side: Int): FloatTensor {
        val data = FloatArray(3 * side * side)
        for (c in 0 until 3) {
            val plane = MaskOps.resizeBilinear(MaskOps.channel(pixels, CHANNEL_SHIFTS[c]), pw, ph, side, side)
            for (i in plane.indices) data[c * side * side + i] = plane[i] / 255f
        }
        return FloatTensor(data, longArrayOf(1, 3, side.toLong(), side.toLong()))
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    private inline fun <T> stage(key: String, block: () -> T): T {
        checkpoint()
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            timings[key] = (System.nanoTime() - started) / NANOS_PER_MILLI
        }
    }
}

/**
 * Decodes the photo with EXIF rotation applied and its long edge exactly [WORK_LONG_EDGE_PX]. Mirrored orientations are
 * not flipped back; the phone photos this app takes are not mirrored.
 */
internal fun decodeWorkBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: error("Unable to open source photo")
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Source photo has no readable size" }
    val longEdge = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longEdge / (sample * 2) >= WORK_LONG_EDGE_PX) sample *= 2

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("Unable to decode source photo")
    val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    val oriented = if (degrees == 0) {
        decoded
    } else {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
    }
    val longSide = max(oriented.width, oriented.height)
    if (longSide == WORK_LONG_EDGE_PX) return oriented
    val scale = WORK_LONG_EDGE_PX.toFloat() / longSide
    return Bitmap.createScaledBitmap(
        oriented,
        (oriented.width * scale).roundToInt(),
        (oriented.height * scale).roundToInt(),
        true,
    ).also { oriented.recycle() }
}
