package com.gusanitolabs.robia.suggestions

import android.content.Context
import android.os.Build
import android.os.Debug
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
