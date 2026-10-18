package com.gusanitolabs.robia.suggestions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import com.gusanitolabs.robia.sync.RestoreSyncLogEvent
import com.gusanitolabs.robia.sync.RestoreSyncLogRepository
import com.gusanitolabs.robia.suggestions.engine.EmbeddingEngineHolder
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
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
    private val syncLog: RestoreSyncLogRepository,
) {
    suspend fun measure(): String = withContext(Dispatchers.Default) {
        coroutineScope {
            val peakPssKb = AtomicLong(0)
            val sampler = launch {
                while (isActive) {
                    peakPssKb.getAndUpdate { max(it, Debug.getPss()) }
                    delay(PSS_SAMPLE_MS)
                }
            }
            try {
                runBenchmark(peakPssKb)
            } finally {
                sampler.cancel()
            }
        }
    }

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

    private fun List<Long>.median(): Long = sorted()[size / 2]

    /** Nearest-rank 90th percentile. */
    private fun List<Long>.p90(): Long = sorted()[(size * 9 + 9) / 10 - 1]

    private companion object {
        const val RUNS = 5
        const val LABEL_COUNT = 19
        const val PSS_SAMPLE_MS = 200L
        const val IMAGE_ASSET = "benchmark/garment.png"
        const val VECTOR_ASSET = "benchmark/garment_vector.b64"
        const val NANOS_PER_MS = 1_000_000L
        const val KB_PER_MB = 1024L
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
        val inputs = benchmarkSceneUris() + photoUris
        val totalMs = mutableListOf<Long>()
        val stageMs = LinkedHashMap<String, MutableList<Long>>()
        var fallbacks = 0
        repeat(ROUNDS) {
            for (uri in inputs) {
                val started = System.nanoTime()
                val result = engine.removeBackground(context, uri)
                totalMs += (System.nanoTime() - started) / NANOS_PER_MS
                if (result.status != BackgroundRemovalStatus.REMOVED) fallbacks++
                for ((stage, millis) in result.timings) stageMs.getOrPut(stage) { mutableListOf() }.add(millis)
            }
        }

        val report = buildList {
            add("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) API ${Build.VERSION.SDK_INT}")
            add("samples=${totalMs.size} (${inputs.size} inputs x $ROUNDS rounds)")
            add("total_ms median=${totalMs.median()} p90=${totalMs.p90()}")
            for ((stage, millis) in stageMs) add("stage_$stage median=${millis.median()} p90=${millis.p90()}")
            add("fallbacks=$fallbacks")
            add("peak_pss_mb=${peakPssKb.get() / KB_PER_MB}")
        }.joinToString("\n")
        return report
    }

    /** The bundled synthetic scenes, copied to the cache so the engine can read them through a file Uri. */
    private fun benchmarkSceneUris(): List<Uri> {
        val names = context.assets.list(SCENE_DIRECTORY).orEmpty().filter { it.endsWith(".png") }.sorted()
        return names.map { name ->
            val file = File(context.cacheDir, "cutout-benchmark-$name")
            context.assets.open("$SCENE_DIRECTORY/$name").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(file)
        }
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    /** Nearest-rank 90th percentile. */
    private fun List<Long>.p90(): Long = sorted()[(size * 9 + 9) / 10 - 1]

    private companion object {
        const val ROUNDS = 2
        const val SCENE_DIRECTORY = "benchmark/cutout"
        const val PSS_SAMPLE_MS = 200L
        const val NANOS_PER_MS = 1_000_000L
        const val KB_PER_MB = 1024L
    }
}
