package com.gusanitolabs.robia.suggestions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import com.gusanitolabs.robia.sync.RestoreSyncLogEvent
import com.gusanitolabs.robia.sync.RestoreSyncLogRepository
import com.gusanitolabs.robia.suggestions.engine.EmbeddingEngineHolder
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import com.gusanitolabs.robia.suggestions.engine.LiteRtNativeEmbedder
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.suggestions.model.ModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Developer-only measurement of the G-DEVICE-1 numbers (TS§3.12). Cold-loads the engine, runs [RUNS] suggestions on the
 * bundled garment image and returns the diagnostic block shown in `mockups/dev-diagnostics.html`.
 */
class DevBenchmark(
    private val context: Context,
    private val holder: EmbeddingEngineHolder,
    private val modelStore: ModelStore,
    private val syncLog: RestoreSyncLogRepository,
) {
    /** [useGpu] measures the LiteRT GPU backend instead, see [runGpuBenchmark]. */
    suspend fun measure(useGpu: Boolean = false): String = withContext(Dispatchers.Default) {
        coroutineScope {
            val peakPssKb = AtomicLong(0)
            val sampler = launch {
                while (isActive) {
                    peakPssKb.getAndUpdate { max(it, Debug.getPss()) }
                    delay(PSS_SAMPLE_MS)
                }
            }
            try {
                if (useGpu) runGpuBenchmark(peakPssKb) else runBenchmark(peakPssKb)
            } finally {
                sampler.cancel()
            }
        }
    }

    /**
     * The GPU row: the bundled image on the GPU backend, compared with the CPU vector of the same image. The CPU vector
     * comes from the production embedder, which is then released so that the peak PSS covers the GPU engine alone. Any
     * failure other than a cancellation is reported as `GPU unavailable`, not thrown.
     */
    private suspend fun runGpuBenchmark(peakPssKb: AtomicLong): String {
        val asset = context.assets.open(IMAGE_ASSET).use { it.readBytes() }
        val cpuVector = holder.embedImage(ImageInputEncoder.encodeBytes(asset).bytes).normalized()
        holder.unload()
        peakPssKb.set(0)
        val report = try {
            gpuReport(asset, cpuVector, peakPssKb)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            gpuUnavailableReport(deviceLine(), failure)
        }
        Log.i(GPU_LOG_TAG, report)
        return report
    }

    private suspend fun gpuReport(asset: ByteArray, cpuVector: FloatArray, peakPssKb: AtomicLong): String {
        val modelPath = (modelStore.state.value as? ModelState.Ready)?.path ?: error("Model is not downloaded")
        val started = System.nanoTime()
        val gpu = LiteRtNativeEmbedder(
            cacheDir = context.cacheDir.absolutePath,
            modelPath = modelPath,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
        )
        val loadMs = (System.nanoTime() - started) / NANOS_PER_MS
        try {
            val imageMs = mutableListOf<Long>()
            var gpuVector = FloatArray(0)
            repeat(RUNS) {
                val imageStarted = System.nanoTime()
                val vector = gpu.embedImage(ImageInputEncoder.encodeBytes(asset).bytes)
                imageMs += (System.nanoTime() - imageStarted) / NANOS_PER_MS
                if (gpuVector.isEmpty()) gpuVector = vector
            }
            return gpuBenchmarkReport(
                deviceLine = deviceLine(),
                loadMs = loadMs,
                imageMs = imageMs,
                peakPssMb = peakPssKb.get() / KB_PER_MB,
                parity = dot(gpuVector.normalized(), cpuVector),
            )
        } finally {
            gpu.close()
        }
    }

    private fun deviceLine(): String =
        "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) API ${Build.VERSION.SDK_INT}"

    private suspend fun runBenchmark(peakPssKb: AtomicLong): String {
        holder.unload()
        val loadMs = elapsedMs { holder.ensureLoaded() }

        val labelVectors = (1..LABEL_COUNT).map { holder.embedText("synthetic benchmark label $it") }
        val asset = context.assets.open(IMAGE_ASSET).use { it.readBytes() }

        val imageMs = mutableListOf<Long>()
        val suggestionMs = mutableListOf<Long>()
        var onDeviceVector = FloatArray(0)
        repeat(RUNS) {
            val started = System.nanoTime()
            val encoded = ImageInputEncoder.encodeBytes(asset)
            val vector = holder.embedImage(encoded.bytes)
            val embedded = System.nanoTime()
            labelScores(vector, labelVectors)
            val scored = System.nanoTime()
            imageMs += (embedded - started) / NANOS_PER_MS
            suggestionMs += (scored - started) / NANOS_PER_MS
            if (onDeviceVector.isEmpty()) onDeviceVector = vector
        }

        val reference = floatArrayFromBase64(
            context.assets.open(VECTOR_ASSET).bufferedReader().use { it.readText() }.trim(),
        )
        val parity = dot(onDeviceVector.normalized(), reference.normalized())

        val report = listOf(
            "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) API ${Build.VERSION.SDK_INT}",
            "vision_tokens=${EmbeddingRecipe.VISION_TOKENS}",
            "load_ms=$loadMs",
            "image_ms median=${imageMs.median()} p90=${imageMs.p90()} (n=$RUNS)",
            "suggestion_ms median=${suggestionMs.median()} p90=${suggestionMs.p90()}",
            "peak_pss_mb=${peakPssKb.get() / KB_PER_MB}",
            "parity_cosine=${String.format(Locale.US, "%.4f", parity)}",
        ).joinToString("\n")

        syncLog.append(
            RestoreSyncLogEvent(
                correlationId = "model-benchmark-${System.currentTimeMillis()}",
                phase = null,
                status = null,
                message = report.replace('\n', ' '),
            ),
        )
        return report
    }

    /** Scoring cost of a suggestion: one dot product per cached label vector. */
    private fun labelScores(vector: FloatArray, labelVectors: List<FloatArray>): List<Float> =
        labelVectors.map { dot(vector, it) }

    private inline fun elapsedMs(block: () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / NANOS_PER_MS
    }

    private companion object {
        const val RUNS = 5
        const val LABEL_COUNT = 19
        const val PSS_SAMPLE_MS = 200L
        const val IMAGE_ASSET = "benchmark/garment.png"
        const val VECTOR_ASSET = "benchmark/garment_vector.b64"
        const val NANOS_PER_MS = 1_000_000L
        const val KB_PER_MB = 1024L
        const val GPU_LOG_TAG = "RobiaBenchmark"
    }
}

/**
 * Developer measurement of the background removal engine (the G-SEG-1 instrument): runs [ROUNDS] rounds over the bundled
 * synthetic cutout scenes and the given garment photos, then reports the total and per-stage median and p90 and the peak PSS.
 */
class CutoutBenchmark(
    private val context: Context,
    private val engine: BackgroundRemovalEngine,
) {
    suspend fun measure(photoUris: List<Uri>): String = withContext(Dispatchers.Default) {
        coroutineScope {
            val peakPssKb = AtomicLong(0)
            val sampler = launch {
                while (isActive) {
                    peakPssKb.getAndUpdate { max(it, Debug.getPss()) }
                    delay(PSS_SAMPLE_MS)
                }
            }
            try {
                runBenchmark(photoUris, peakPssKb)
            } finally {
                sampler.cancel()
            }
        }
    }

    private suspend fun runBenchmark(photoUris: List<Uri>, peakPssKb: AtomicLong): String {
        val failures = mutableListOf<InputFailure>()
        val runInputs = benchmarkScenes(failures) + photoUris.mapNotNull { probePhoto(it, failures) }
        val totalMs = mutableListOf<Long>()
        val stageMs = LinkedHashMap<String, MutableList<Long>>()
        var fallbacks = 0
        var active = runInputs
        repeat(ROUNDS) {
            val survivors = mutableListOf<BenchmarkInput>()
            for (input in active) {
                val started = System.nanoTime()
                try {
                    val result = engine.removeBackground(context, input.uri)
                    totalMs += (System.nanoTime() - started) / NANOS_PER_MS
                    if (result.status != BackgroundRemovalStatus.REMOVED) fallbacks++
                    for ((stage, millis) in result.timings) stageMs.getOrPut(stage) { mutableListOf() }.add(millis)
                    survivors += input
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    failures += input.failure(failure, fromEngine = true)
                } catch (failure: LinkageError) {
                    // A class or native library that cannot load is one failed input, not a crashed app.
                    failures += input.failure(failure, fromEngine = true)
                }
            }
            active = survivors
        }

        return cutoutBenchmarkReport(
            deviceLine = "device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) API ${Build.VERSION.SDK_INT}",
            runInputs = runInputs.size,
            okInputs = active.size,
            rounds = ROUNDS,
            totalMs = totalMs,
            stageMs = stageMs,
            fallbacks = fallbacks,
            peakPssMb = peakPssKb.get() / KB_PER_MB,
            failures = failures,
        )
    }

    /**
     * The bundled synthetic scenes, copied to the cache so the engine can read them through a file Uri. A scene that fails to
     * copy is recorded in [failures] and left out.
     */
    private fun benchmarkScenes(failures: MutableList<InputFailure>): List<BenchmarkInput> {
        val names = context.assets.list(SCENE_DIRECTORY).orEmpty().filter { it.endsWith(".png") }.sorted()
        return names.mapNotNull { name ->
            val file = File(context.cacheDir, "cutout-benchmark-$name")
            val input = BenchmarkInput(kind = "scene", uri = Uri.fromFile(file), name = name)
            try {
                context.assets.open("$SCENE_DIRECTORY/$name").use { asset ->
                    file.outputStream().use { output -> asset.copyTo(output) }
                }
                input
            } catch (failure: Exception) {
                failures += input.failure(failure)
                null
            }
        }
    }

    /** Opens the photo before the engine runs. A photo that cannot be opened is recorded in [failures] and is not a sample. */
    private fun probePhoto(uri: Uri, failures: MutableList<InputFailure>): BenchmarkInput? {
        val input = BenchmarkInput(kind = "photo", uri = uri, name = "${uri.scheme.orEmpty()}:${uri.lastPathSegment ?: "unknown"}")
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: error("Unable to open source photo")
            stream.close()
            input
        } catch (failure: Exception) {
            failures += input.failure(failure)
            null
        }
    }

    /**
     * One line of the failure report: the exception class and message and its causes, with the input's URI and paths removed.
     * [fromEngine] marks a failure thrown by the background removal engine; a RuntimeException from it gets the root-cause frames.
     */
    private fun BenchmarkInput.failure(failure: Throwable, fromEngine: Boolean = false): InputFailure {
        val secrets = listOfNotNull(uri.toString(), uri.path).filter { it.length > 1 }
        return InputFailure(kind = kind, name = name, reason = failureReason(failure, secrets, fromEngine))
    }

    private class BenchmarkInput(val kind: String, val uri: Uri, val name: String)

    private companion object {
        const val ROUNDS = 2
        const val SCENE_DIRECTORY = "benchmark/cutout"
        const val PSS_SAMPLE_MS = 200L
        const val NANOS_PER_MS = 1_000_000L
        const val KB_PER_MB = 1024L
    }
}

/** An input that was skipped: [kind] is `scene` or `photo`, [name] is the asset name or `scheme:lastPathSegment`. */
internal data class InputFailure(val kind: String, val name: String, val reason: String)

/** The `failed_input=` line for one skipped input. */
internal fun failedInputLine(failure: InputFailure): String =
    "failed_input=${failure.kind}:${failure.name} reason=${failure.reason}"

/**
 * `Class: message`, then ` caused by Class: message` for up to [CAUSE_LEVELS] causes. Class simple names and messages only, with
 * each [secrets] entry replaced by `<uri>`, absolute paths replaced by `<path>` and no line breaks. No stack trace, except that
 * a RuntimeException from the engine ([fromEngine]) ends with ` frames=` and the first root-cause frames, see [rootFrames].
 */
internal fun failureReason(failure: Throwable, secrets: List<String>, fromEngine: Boolean = false): String {
    fun describe(error: Throwable): String {
        val message = secrets.fold(error.message.orEmpty()) { text, secret -> text.replace(secret, "<uri>") }
            .replace(ABSOLUTE_PATH, "<path>")
            .replace('\n', ' ')
            .ifEmpty { "no message" }
        return "${error.javaClass.simpleName}: $message"
    }
    val causes = generateSequence(failure.cause) { it.cause }.take(CAUSE_LEVELS).map { " caused by ${describe(it)}" }
    val frames = if (fromEngine && failure is RuntimeException) rootFrames(failure) else null
    return describe(failure) + causes.joinToString("") + (frames?.let { " frames=$it" } ?: "")
}

/**
 * The first [FRAME_LEVELS] stack frames of the root cause that belong to Google's libraries, ONNX Runtime or this app, as
 * `class.method` joined by `<`. Class names are kept as they are; `<init>` and `<clinit>` become `init` and `clinit`. Null when
 * no frame matches. The walk is bounded so that a cyclic cause chain cannot loop.
 */
private fun rootFrames(failure: Throwable): String? {
    val root = generateSequence(failure) { it.cause }.take(MAX_CAUSE_WALK).last()
    val frames = root.stackTrace.asSequence()
        .filter { frame -> FRAME_PACKAGES.any { frame.className.startsWith(it) } }
        .take(FRAME_LEVELS)
        .map { frame -> "${frame.className}.${frame.methodName.trim('<', '>')}" }
        .toList()
    return frames.takeIf { it.isNotEmpty() }?.joinToString("<")
}

private const val CAUSE_LEVELS = 4
private const val FRAME_LEVELS = 3
private const val MAX_CAUSE_WALK = 32
private val FRAME_PACKAGES = listOf("com.google.", "ai.onnxruntime.", "com.gusanitolabs.robia.")
private val ABSOLUTE_PATH = Regex("""/[^\s"']+""")

/**
 * The developer report of a [CutoutBenchmark] run. Skipped inputs are listed one per line and are never samples. With no input
 * left there is nothing to summarise, so the report says so and omits the timings instead of failing.
 */
internal fun cutoutBenchmarkReport(
    deviceLine: String,
    runInputs: Int,
    okInputs: Int,
    rounds: Int,
    totalMs: List<Long>,
    stageMs: Map<String, List<Long>>,
    fallbacks: Int,
    peakPssMb: Long,
    failures: List<InputFailure>,
): String = buildList {
    add(deviceLine)
    add("inputs_ok=$okInputs inputs_failed=${failures.size}")
    val photosSkipped = failures.count { it.kind == "photo" }
    if (photosSkipped > 0) add("photos_skipped=$photosSkipped")
    if (okInputs == 0) add("error=no inputs succeeded")
    failures.forEach { add(failedInputLine(it)) }
    add("samples=${totalMs.size} ($runInputs inputs x $rounds rounds)")
    if (totalMs.isNotEmpty()) {
        add("total_ms median=${totalMs.median()} p90=${totalMs.p90()}")
        for ((stage, millis) in stageMs) add("stage_$stage median=${millis.median()} p90=${millis.p90()}")
    }
    add("fallbacks=$fallbacks")
    add("peak_pss_mb=$peakPssMb")
}.joinToString("\n")

/**
 * The developer report of a successful GPU run: the GPU engine's load time, image timings, peak PSS and the cosine of its
 * vector with the CPU vector of the same image ([parity]).
 */
internal fun gpuBenchmarkReport(
    deviceLine: String,
    loadMs: Long,
    imageMs: List<Long>,
    peakPssMb: Long,
    parity: Float,
): String = listOf(
    deviceLine,
    "backend=gpu vision_backend=gpu vision_tokens=${EmbeddingRecipe.VISION_TOKENS}",
    "load_ms=$loadMs",
    "image_ms median=${imageMs.median()} p90=${imageMs.p90()} (n=${imageMs.size})",
    "peak_pss_mb=$peakPssMb",
    "parity_cosine=${String.format(Locale.US, "%.4f", parity)}",
).joinToString("\n")

/** `GPU unavailable: Class: message` for a GPU run that threw; the message has no line breaks and no absolute paths. */
internal fun gpuUnavailableReport(deviceLine: String, failure: Throwable): String {
    val message = failure.message.orEmpty()
        .replace(ABSOLUTE_PATH, "<path>")
        .replace('\n', ' ')
        .ifEmpty { "no message" }
    return listOf(deviceLine, "GPU unavailable: ${failure.javaClass.simpleName}: $message").joinToString("\n")
}

private fun List<Long>.median(): Long = sorted()[size / 2]

/** Nearest-rank 90th percentile. */
private fun List<Long>.p90(): Long = sorted()[(size * 9 + 9) / 10 - 1]
