package com.gusanitolabs.robia.media.cutout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.gusanitolabs.robia.BuildConfig
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import com.gusanitolabs.robia.media.BackgroundRemovalFailure
import com.gusanitolabs.robia.media.BackgroundRemovalFailureReason
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
 * A [LinkageError] from the sessions or the selector also trips [CutoutEngineRegistry.preciseLatch] before it is rethrown.
 * Sessions and the selector live for one photo only.
 */
internal class PreciseCutoutEngine(
    private val packDir: File,
    private val selectorFactory: (Context) -> InteractiveSelector = ::MediaPipeInteractiveSelector,
    /** Read once per photo; the editor's Redo passes a fixed set for its one-off run. */
    private val options: () -> CutoutOptions = { CutoutEngineRegistry.options() },
    /** Debug dump of every stage (K01); the default copies nothing. */
    private val sink: StageSink = NoStageSink,
) : BackgroundRemovalEngine {
    /** Facts of the run for the debug dump; one engine serves one photo, so this is never reused. */
    internal val runInfo = PreciseRunInfo()

    /**
     * One photo at a time per process ([CutoutEngineRegistry.withPreciseGate]); the caller's 30 s budget includes the wait
     * for that gate. The budget cannot pre-empt a native ONNX or MediaPipe call: a call that is already running finishes
     * first, and the checkpoints between stages stop the rest of the pipeline.
     */
    override suspend fun removeBackground(context: Context, sourceUri: Uri): BackgroundRemovalResult =
        CutoutEngineRegistry.withPreciseGate {
            withContext(Dispatchers.Default) {
                val cutout = options()
                val timings = LinkedHashMap<String, Long>()
                val work = timings.timed("decode") { decodeWorkBitmap(context, sourceUri) }
                runInfo.width = work.width
                runInfo.height = work.height
                try {
                    val argb = IntArray(work.width * work.height).also { work.getPixels(it, 0, work.width, 0, 0, work.width, work.height) }
                    val callerContext = coroutineContext
                    val alpha = CutoutEngineRegistry.preciseLatch.guard {
                        OnnxSessions(packDir).use { models ->
                            selectorFactory(context).use { selector ->
                                CutoutRun(
                                    models, selector, work, argb, timings, cutout,
                                    checkpoint = { callerContext.ensureActive() },
                                    sink = sink,
                                    info = runInfo,
                                ).alpha()
                            }
                        }
                    }
                    if (alpha == null) {
                        // K02 (b): the precise result lost most of the garment, so it is not used and the chain falls back.
                        // The failure message names the reason, which PhotoBackgroundRemover maps to the fallback reason.
                        if (BuildConfig.DEBUG) Log.i(CUTOUT_LOG_TAG, "garment_loss=1")
                        return@withContext BackgroundRemovalResult(
                            originalUri = sourceUri,
                            outputUri = sourceUri,
                            status = BackgroundRemovalStatus.ORIGINAL_FALLBACK,
                            failure = BackgroundRemovalFailure(
                                reason = BackgroundRemovalFailureReason.SEGMENTATION_FAILED,
                                message = FALLBACK_GARMENT_LOSS,
                            ),
                            timings = timings.toMap(),
                        )
                    }
                    val composed = Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888)
                    val cropped = try {
                        composed.setPixels(IntArray(argb.size) { i -> ((alpha[i].toInt() and 0xFF) shl 24) or (argb[i] and 0xFFFFFF) }, 0, work.width, 0, 0, work.width, work.height)
                        cropToAlpha(composed, alpha)
                    } catch (failure: Throwable) {
                        composed.recycle()
                        throw failure
                    }
                    if (wantsStages(sink)) {
                        val pixels = IntArray(cropped.width * cropped.height).also { cropped.getPixels(it, 0, cropped.width, 0, 0, cropped.width, cropped.height) }
                        sink.image("final", pixels, cropped.width, cropped.height)
                    }
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
    private val cutout: CutoutOptions,
    /** Throws when the caller was cancelled (the 30 s budget); stages are CPU-bound and cannot be interrupted otherwise. */
    private val checkpoint: () -> Unit,
    private val sink: StageSink,
    private val info: PreciseRunInfo,
) {
    private val w = work.width
    private val h = work.height

    /**
     * Per-pixel alpha 0..255 of the work photo, or null when the garment-loss guard (K02 (b)) rejects it. With hanger removal
     * off, S3–S4b and their models are skipped.
     */
    fun alpha(): ByteArray? {
        val coarse = stage("s1") { saliency(argb) }
        if (wantsStages(sink)) sink.mask("s1_saliency", coarse, w, h)
        val selection = stage("s2") { garmentSelection(coarse, work, info.s2Points) }
        info.s2Completed = true
        if (wantsStages(sink)) sink.mask("s2_selection", selection, w, h)
        val hard = MaskOps.thresholded(selection)
        val garment = MaskOps.bbox(hard, w, h) ?: error("Garment selection is empty")
        val garmentArea = MaskOps.count(hard)
        val s1Area = MaskOps.countAbove(coarse)

        val result = applyHangerGate(
            removeHanger = cutout.removeHanger,
            threshold = cutout.hangerThreshold,
            selection = selection,
            detect = { threshold ->
                val hanger = stage("s3") { detectHanger(garment, threshold) }
                info.hangerBox = hanger?.let { intArrayOf(it.x0, it.y0, it.x1, it.y1) }
                if (wantsStages(sink)) sink.json("s3_hanger", hangerJson(info.hangerBox))
                hanger
            },
            kill = { hanger ->
                stage("s4") { hangerKill(hanger, selection, garment, garmentArea) }?.also { kill ->
                    info.kill = true
                    if (wantsStages(sink)) sink.mask("s4_kill", FloatArray(kill.size) { if (kill[it]) 1f else 0f }, w, h)
                }
            },
            reselect = { hanger, kill ->
                stage("s4b") { hangerFirst(selection, kill, hanger, garment.height) }.also { reselected ->
                    if (wantsStages(sink)) sink.mask("s4b_reselect", reselected, w, h)
                }
            },
        )
        val alpha = stage("s5") {
            MaskOps.refineAlpha(MaskOps.luminance(argb), MaskOps.dropIslands(result, w, h), w, h, cutout.edges)
        }
        val opaque = alpha.count { (it.toInt() and 0xFF) > 127 }
        if (wantsStages(sink)) {
            sink.mask("s5_alpha", FloatArray(alpha.size) { (alpha[it].toInt() and 0xFF) / 255f }, w, h)
            info.opaqueFractionOfS2 = opaque.toDouble() / max(garmentArea, 1)
        }
        if (garmentLost(opaque, s1Area, garmentArea)) {
            info.garmentLoss = true
            return null
        }
        return alpha
    }

    /** S1: u2netp saliency, min-max normalised and resampled to the work size. The 320 input is antialiased as PIL does (D4). */
    private fun saliency(pixels: IntArray): FloatArray {
        val planes = CHANNEL_SHIFTS.map { MaskOps.channel(pixels, it) }
        val resized = planes.map { MaskOps.resizeBilinearAntialias(it, w, h, SALIENCY_SIZE, SALIENCY_SIZE) }
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

    /**
     * S2: magic_touch at three automatic points; a selection counts when ≥ 60 % of it lies in the coarse mask. [points]
     * receives each prompt point and its verdict (the first S2 only; the S4b re-selection passes none).
     */
    private fun garmentSelection(coarse: FloatArray, bitmap: Bitmap, points: MutableList<S2Point>? = null): FloatArray {
        val hard = MaskOps.thresholded(coarse)
        val selection = FloatArray(w * h)
        for (point in MaskOps.thirdPoints(hard, w, h)) {
            val mask = selector.select(bitmap, point.x, point.y)
            val maskHard = MaskOps.thresholded(mask)
            val accepted = MaskOps.count(maskHard) > 0 && MaskOps.overlap(maskHard, hard) >= GARMENT_OVERLAP_MIN
            points?.add(S2Point(point.x, point.y, accepted))
            if (accepted) {
                for (i in selection.indices) selection[i] = max(selection[i], mask[i])
            }
        }
        return if (selection.any { it > 0f }) selection else coarse.copyOf()
    }

    /** S3: D-FINE Hanger class; the first pass on the whole photo, then the top band of the garment if nothing is plausible. */
    private fun detectHanger(garment: MaskBox, hangerThreshold: Float): MaskBox? =
        HangerRemoval.findHanger(garment, w, h, hangerThreshold) { region, threshold ->
            if (region == null) {
                detectHangers(argb, w, h, threshold)
            } else {
                val regionW = region[2] - region[0]
                val regionH = region[3] - region[1]
                detectHangers(cropPixels(argb, w, region, regionW, regionH), regionW, regionH, threshold)
            }
        }

    /** Detections at or above [threshold] among the top candidates, best first, boxes in the coordinates of [pixels]. */
    private fun detectHangers(pixels: IntArray, pw: Int, ph: Int, threshold: Float): List<Pair<Float, MaskBox>> {
        val input = resampleTensor(pixels, pw, ph, DETECTOR_SIZE)
        val outputs = models.run(models.hangerDetector, mapOf("pixel_values" to input))
        val logits = outputs[0]
        val boxes = outputs[1]
        val queries = logits.size / DFINE_CLASSES
        val scores = FloatArray(queries) { q -> sigmoid(logits[q * DFINE_CLASSES + HANGER_CLASS]) }
        val result = ArrayList<Pair<Float, MaskBox>>()
        for (q in (0 until queries).sortedByDescending { scores[it] }.take(TOP_HANGER_CANDIDATES)) {
            val score = scores[q]
            if (score < threshold) break
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
        if (wantsStages(sink)) sink.image("s4b_filled", filled, w, h)
        val filledBitmap = Bitmap.createBitmap(filled, w, h, Bitmap.Config.ARGB_8888)
        try {
            val reselected = garmentSelection(saliency(filled), filledBitmap)
            val bandBottom = HangerRemoval.bandBottom(hanger, garmentHeight, h)
            info.bandBottom = bandBottom
            val banded = HangerRemoval.guardedBand(
                reselected = reselected,
                previous = selection,
                support = support,
                bandBottom = bandBottom,
                w = w,
            )
            info.bandGuard = banded.guardTripped
            if (banded.guardTripped && BuildConfig.DEBUG) Log.i(CUTOUT_LOG_TAG, "band_guard=1")
            return banded.selection
        } finally {
            filledBitmap.recycle()
        }
    }

    /**
     * MobileSAM image embedding of the work photo, resized so its long edge is exactly 1024 with bilinear sampling, as
     * `bench_v6.MobileSam.embed` does (K02 (c)). Work photos up to 1024 are upscaled to it; a 1024 photo is used as it is.
     */
    private fun samEmbedding(): FloatArray {
        val (inputW, inputH) = MaskOps.samInputSize(w, h)
        val data = FloatArray(inputW * inputH * 3)
        for (c in 0 until 3) {
            val plane = MaskOps.channel(argb, CHANNEL_SHIFTS[c])
            val resized = if (inputW == w && inputH == h) plane else MaskOps.resizeBilinear(plane, w, h, inputW, inputH)
            for (i in resized.indices) data[i * 3 + c] = resized[i].roundToInt().coerceIn(0, 255).toFloat()
        }
        return models.run(
            models.samEncoder,
            mapOf("input_image" to FloatTensor(data, longArrayOf(inputH.toLong(), inputW.toLong(), 3))),
        )[0]
    }

    /**
     * One MobileSAM decode: positive/negative points plus the box corners, scaled by [MaskOps.samScale]; the best of four masks
     * by IoU, as probabilities. `orig_im_size` stays the work size, so the mask comes back at the work size (`bench_v6.predict`).
     */
    private fun samDecode(embedding: FloatArray, box: MaskBox, positive: List<MaskPoint>, negative: List<MaskPoint>): FloatArray {
        val prompt = MaskOps.samPrompt(w, h, positive, negative, box)
        val points = prompt.labels.size
        val outputs = models.run(
            models.samDecoder,
            mapOf(
                "image_embeddings" to FloatTensor(embedding, longArrayOf(1, SAM_EMBEDDING_CHANNELS.toLong(), SAM_EMBEDDING_SIDE.toLong(), SAM_EMBEDDING_SIDE.toLong())),
                "point_coords" to FloatTensor(prompt.coords, longArrayOf(1, points.toLong(), 2)),
                "point_labels" to FloatTensor(prompt.labels, longArrayOf(1, points.toLong())),
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
 * The hanger gate of the precise pipeline (S3 detect, S4 kill, S4b hanger-first re-selection). With [removeHanger] off,
 * [selection] is returned as it is and no stage runs. Otherwise S3 gets [threshold]; a null from S3 or S4 ends the gate
 * with [selection], and S4b's result replaces it. Pure: the stages are the caller's lambdas, so tests pass fakes.
 */
internal fun applyHangerGate(
    removeHanger: Boolean,
    threshold: Float,
    selection: FloatArray,
    detect: (threshold: Float) -> MaskBox?,
    kill: (hanger: MaskBox) -> BooleanArray?,
    reselect: (hanger: MaskBox, kill: BooleanArray) -> FloatArray,
): FloatArray {
    if (!removeHanger) return selection
    val hanger = detect(threshold) ?: return selection
    val killMask = kill(hanger) ?: return selection
    return reselect(hanger, killMask)
}

/** Share of the garment the final opaque area must keep (K02 (b)). */
private const val GARMENT_KEEP_FRACTION = 0.70

/**
 * The garment-loss guard (K02 (b), with the S1 comparison of R3-DEC-021 D3): the final opaque area (alpha > 127) is compared
 * with the larger of the S1 and S2 hard areas, so a weak S2 cannot hide a lost garment. Below 70 % of that area the precise
 * result is rejected.
 */
internal fun garmentLost(opaque: Int, s1Area: Int, s2Area: Int): Boolean =
    opaque < GARMENT_KEEP_FRACTION * max(s1Area, s2Area)

/**
 * Work size of a photo: a long edge above [WORK_LONG_EDGE_PX] is scaled down to it; a smaller photo keeps its size, as the
 * reference pipeline (`thumbnail((1024, 1024))`) never enlarges.
 */
internal fun workSize(width: Int, height: Int): Pair<Int, Int> {
    val longSide = max(width, height)
    if (longSide <= WORK_LONG_EDGE_PX) return width to height
    val scale = WORK_LONG_EDGE_PX.toFloat() / longSide
    return (width * scale).roundToInt() to (height * scale).roundToInt()
}

/**
 * Decodes the photo with EXIF rotation applied and sized by [workSize]. Mirrored orientations are not flipped back; the
 * phone photos this app takes are not mirrored.
 */
internal fun decodeWorkBitmap(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open source photo")
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
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
    val orientation = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (failure: Throwable) {
        decoded.recycle()
        throw failure
    }
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
    val (targetWidth, targetHeight) = workSize(oriented.width, oriented.height)
    if (targetWidth == oriented.width && targetHeight == oriented.height) return oriented
    return Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true).also { oriented.recycle() }
}
