package com.gusanitolabs.robia.media.cutout

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.gusanitolabs.robia.media.BackgroundRemovalResult
import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import com.gusanitolabs.robia.media.MlKitSubjectSegmentationBackgroundRemovalEngine
import com.gusanitolabs.robia.media.notRemovedFallback
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Receives the intermediate stages of the precise pipeline (REQ-CU-01). Stage names and file names follow
 * `tools/cutout-diag/reference_stages.py`, so `compare.py` can pair the two dumps.
 */
internal interface StageSink {
    /** A width×height mask, values 0..1, row-major. */
    fun mask(stage: String, values: FloatArray, w: Int, h: Int)

    /** A width×height ARGB image. */
    fun image(stage: String, argb: IntArray, w: Int, h: Int)

    fun json(stage: String, json: String)
}

/** The production sink: receives nothing, and the engine copies no stage when it is attached (see [wantsStages]). */
internal object NoStageSink : StageSink {
    override fun mask(stage: String, values: FloatArray, w: Int, h: Int) = Unit

    override fun image(stage: String, argb: IntArray, w: Int, h: Int) = Unit

    override fun json(stage: String, json: String) = Unit
}

/** False for [NoStageSink]: the engine skips every stage copy then. */
internal fun wantsStages(sink: StageSink): Boolean = sink !== NoStageSink

/** Writes one file per stage into [dir]; the directory is created on the first write. */
internal class DirectoryStageSink(private val dir: File) : StageSink {
    override fun mask(stage: String, values: FloatArray, w: Int, h: Int) {
        writePng(stage, IntArray(values.size) { i -> grey(values[i]) }, w, h)
    }

    override fun image(stage: String, argb: IntArray, w: Int, h: Int) = writePng(stage, argb, w, h)

    override fun json(stage: String, json: String) {
        dir.mkdirs()
        File(dir, stageFileName(stage)).writeText(json)
    }

    private fun writePng(stage: String, argb: IntArray, w: Int, h: Int) {
        dir.mkdirs()
        val bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        try {
            File(dir, stageFileName(stage)).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode $stage" }
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Equal channels: a grey value written as opaque ARGB, which `compare.py` reads as L. */
    private fun grey(value: Float): Int {
        val level = (value.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
    }
}

private val STAGE_FILE_NAMES = mapOf(
    "s1_saliency" to "s1_saliency.png",
    "s2_selection" to "s2_selection.png",
    "s3_hanger" to "s3_hanger.json",
    "s4_kill" to "s4_kill.png",
    "s4b_filled" to "s4b_filled.png",
    "s4b_reselect" to "s4b_reselect.png",
    "s5_alpha" to "s5_alpha.png",
    "final" to "final.png",
    "mlkit" to "mlkit.png",
)

/** The file a stage is written to; an unknown stage is a programming error. */
internal fun stageFileName(stage: String): String =
    STAGE_FILE_NAMES[stage] ?: error("Unknown cutout debug stage: $stage")

/** One S2 prompt point and whether its selection was accepted (≥ 60 % of it inside the coarse mask). */
internal data class S2Point(val x: Int, val y: Int, val accepted: Boolean)

/** What the last precise run did, for `report.json`; the engine fills it as the stages run. */
internal class PreciseRunInfo {
    var width = 0
    var height = 0
    val s2Points = ArrayList<S2Point>()
    var hangerBox: IntArray? = null
    var kill = false
    var bandBottom: Int? = null
    var opaqueFractionOfS2 = 0.0
    /** The band guard kept the first selection in the band (K02 (a)). */
    var bandGuard = false
    /** The garment-loss guard rejected the precise result (K02 (b)). */
    var garmentLoss = false
}

/** The `s3_hanger.json` content: `{"box":[x0,y0,x1,y1]}`, or `{"box":null}` when no plausible hanger was found. */
internal fun hangerJson(box: IntArray?): String =
    JSONObject().put("box", box?.let { JSONArray(it.toList()) } ?: JSONObject.NULL).toString()

/** The report of one photo, with the keys `compare.py` and the dump README read. */
internal fun reportJson(
    width: Int,
    height: Int,
    s2Points: List<S2Point>,
    hangerBox: IntArray?,
    kill: Boolean,
    bandBottom: Int?,
    seconds: Double,
    opaqueFractionOfS2: Double,
    bandGuard: Boolean,
    garmentLoss: Boolean,
    s2Fallback: Boolean,
    s2Accepted: Int,
    engine: String,
    fallback: String,
    error: String? = null,
): String = JSONObject()
    .put("work", JSONArray(listOf(width, height)))
    .put("s2_points", JSONArray(s2Points.map { JSONObject().put("x", it.x).put("y", it.y).put("accepted", it.accepted) }))
    .put("hanger_box", hangerBox?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
    .put("kill", kill)
    .put("band_bottom", bandBottom ?: JSONObject.NULL)
    .put("seconds", seconds)
    .put("opaque_fraction_of_s2", opaqueFractionOfS2)
    .put("band_guard", bandGuard)
    .put("garment_loss", garmentLoss)
    .put("s2_fallback", s2Fallback)
    .put("s2_accepted", s2Accepted)
    .put("engine", engine)
    .put("fallback", fallback)
    .apply { if (error != null) put("error", error) }
    .toString(1)

private const val CUTOUT_DEBUG_DIR = "cutout-debug"
private const val NANOS_PER_MILLI = 1_000_000L
private val DUMP_EXTENSIONS = setOf("jpg", "jpeg", "png")

/**
 * Debug runner (`--ez com.gusanitolabs.robia.CUTOUT_DEBUG true`, debug builds only). For every photo in
 * `cutout-debug/in` the precise pipeline runs with a [DirectoryStageSink] into `cutout-debug/out/<stem>/`, ML Kit then
 * writes `mlkit.png`, and `report.json` records both. Everything stays under the app's external files directory.
 */
internal suspend fun runCutoutDebugDump(context: Context, packReady: Boolean) {
    val root = File(context.getExternalFilesDir(null) ?: return, CUTOUT_DEBUG_DIR)
    val photos = File(root, "in")
        .listFiles { file -> file.isFile && file.extension.lowercase() in DUMP_EXTENSIONS }
        .orEmpty()
        .sortedBy { it.name }
    for (photo in photos) {
        try {
            dumpPhoto(context, photo, File(root, "out/${photo.nameWithoutExtension}"), packReady)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Log.w(CUTOUT_LOG_TAG, "cutout_debug_failed stem=${photo.nameWithoutExtension} cause=${failure::class.java.simpleName}")
        }
    }
    Log.i(CUTOUT_LOG_TAG, "cutout_debug_all_done n=${photos.size}")
}

private suspend fun dumpPhoto(context: Context, photo: File, dir: File, packReady: Boolean) {
    val stem = photo.nameWithoutExtension
    dir.mkdirs()
    if (!packReady) {
        File(dir, "report.json").writeText(JSONObject().put("error", "pack not ready").toString(1))
        Log.i(CUTOUT_LOG_TAG, "cutout_debug_done stem=$stem error=pack_not_ready")
        return
    }
    val source = Uri.fromFile(photo)

    val precise = PreciseCutoutEngine(
        packDir = File(context.filesDir, CutoutPackManifest.DIRECTORY),
        sink = DirectoryStageSink(dir),
    )
    val preciseStarted = System.nanoTime()
    var preciseResult: BackgroundRemovalResult? = null
    var error: String? = null
    try {
        preciseResult = precise.removeBackground(context, source)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        error = "${failure::class.java.simpleName}: ${failure.message}"
    }
    val preciseMs = (System.nanoTime() - preciseStarted) / NANOS_PER_MILLI
    val accepted = preciseResult?.status == BackgroundRemovalStatus.REMOVED
    preciseResult?.let { discardOutput(context, source, it) }

    val mlKitStarted = System.nanoTime()
    val mlKit = MlKitSubjectSegmentationBackgroundRemovalEngine().removeBackground(context, source)
    val mlKitMs = (System.nanoTime() - mlKitStarted) / NANOS_PER_MILLI
    if (mlKit.status == BackgroundRemovalStatus.REMOVED) {
        val output = checkNotNull(context.contentResolver.openInputStream(mlKit.outputUri)) { "Unable to read ML Kit output" }
        output.use { input -> File(dir, stageFileName("mlkit")).outputStream().use { input.copyTo(it) } }
        discardOutput(context, source, mlKit)
    }

    val info = precise.runInfo
    File(dir, "report.json").writeText(
        reportJson(
            width = info.width,
            height = info.height,
            s2Points = info.s2Points,
            hangerBox = info.hangerBox,
            kill = info.kill,
            bandBottom = info.bandBottom,
            seconds = preciseMs / 1000.0,
            opaqueFractionOfS2 = info.opaqueFractionOfS2,
            bandGuard = info.bandGuard,
            garmentLoss = info.garmentLoss,
            // S2 fell back to saliency when no prompt was accepted (the selection is then the S1 copy).
            s2Fallback = info.s2Points.none { it.accepted },
            s2Accepted = info.s2Points.count { it.accepted },
            engine = if (accepted) ENGINE_PRECISE else ENGINE_MLKIT,
            fallback = when {
                accepted -> FALLBACK_NONE
                preciseResult == null -> FALLBACK_ERROR
                else -> notRemovedFallback(preciseResult.failure)
            },
            error = error,
        ),
    )
    Log.i(CUTOUT_LOG_TAG, "cutout_debug_done stem=$stem precise_ms=$preciseMs mlkit_ms=$mlKitMs")
}

/** Deletes the output file a cutout wrote into the pictures folder; never the source photo. */
private fun discardOutput(context: Context, source: Uri, result: BackgroundRemovalResult) {
    if (result.status == BackgroundRemovalStatus.REMOVED && result.outputUri != source) {
        context.contentResolver.delete(result.outputUri, null, null)
    }
}
