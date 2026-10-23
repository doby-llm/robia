package com.gusanitolabs.robia.media

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val subjectSegmenterOptions: SubjectSegmenterOptions = SubjectSegmenterOptions.Builder()
    .enableForegroundBitmap()
    .enableForegroundConfidenceMask()
    .build()

/** Play services side of the optional subject-segmentation module (SEG-DEC-001). Injectable for JVM tests. */
interface ModuleInstallPort {
    suspend fun isAvailable(): Boolean
    suspend fun requestInstall(): Boolean
}

class PlayModuleInstallPort(context: Context) : ModuleInstallPort {
    private val appContext = context.applicationContext

    override suspend fun isAvailable(): Boolean = withSegmenter { segmenter ->
        ModuleInstall.getClient(appContext).areModulesAvailable(segmenter).await().areModulesAvailable()
    }

    override suspend fun requestInstall(): Boolean = withSegmenter { segmenter ->
        val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
        ModuleInstall.getClient(appContext).installModules(request).await()
        true
    }

    private suspend fun withSegmenter(block: suspend (SubjectSegmenter) -> Boolean): Boolean {
        val segmenter = SubjectSegmentation.getClient(subjectSegmenterOptions)
        return try {
            block(segmenter)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            false
        } finally {
            segmenter.close()
        }
    }
}

sealed interface GateResult {
    data object Available : GateResult
    data class Unavailable(val reason: String) : GateResult
}

/**
 * Makes sure the subject-segmentation module is installed before ML Kit is asked to segment.
 * Installing is requested once and then polled; [onPreparing] is true only while that wait runs.
 *
 * Between [startBatch] and [endBatch] every wait shares one [BATCH_MODULE_WAIT_BUDGET_MS] budget, so a batch of
 * photos waits for the module once in total. Outside a batch each call waits up to its own timeout.
 */
class SegmentationModuleGate(
    private val port: ModuleInstallPort,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val activeWaits = AtomicInteger(0)
    private val _preparing = MutableStateFlow(false)

    /** Monotonic time (ms) when the current batch's wait budget ends; null outside a batch. */
    @Volatile
    private var batchWaitDeadlineMs: Long? = null

    /** True while at least one [ensureAvailable] call is waiting for the module to install. */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    fun startBatch() {
        batchWaitDeadlineMs = nowMs() + BATCH_MODULE_WAIT_BUDGET_MS
    }

    fun endBatch() {
        batchWaitDeadlineMs = null
    }

    suspend fun ensureAvailable(
        timeout: Duration = 60.seconds,
        onPreparing: (Boolean) -> Unit = {},
    ): GateResult {
        if (port.isAvailable()) return GateResult.Available

        val waitMs = waitBudgetMs(timeout.inWholeMilliseconds)
        if (waitMs <= 0L) return GateResult.Unavailable("Module wait budget for this batch is spent")

        onPreparing(true)
        markWaiting(true)
        try {
            return withTimeoutOrNull<GateResult>(waitMs) {
                if (!port.requestInstall()) return@withTimeoutOrNull GateResult.Unavailable("Module install request was rejected")

                var waitedMs = 0L
                while (waitedMs < waitMs) {
                    delayFn(POLL_INTERVAL_MS)
                    waitedMs += POLL_INTERVAL_MS
                    if (port.isAvailable()) return@withTimeoutOrNull GateResult.Available
                }
                GateResult.Unavailable("Module not available after $waitMs ms")
            } ?: GateResult.Unavailable("Module not available after $waitMs ms")
        } finally {
            markWaiting(false)
            onPreparing(false)
        }
    }

    /** The wait this call may use: its own [requestedMs], cut down to what is left of the batch budget. */
    private fun waitBudgetMs(requestedMs: Long): Long {
        val deadline = batchWaitDeadlineMs ?: return requestedMs
        return minOf(requestedMs, deadline - nowMs())
    }

    private fun markWaiting(waiting: Boolean) {
        _preparing.value = activeWaits.addAndGet(if (waiting) 1 else -1) > 0
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val BATCH_MODULE_WAIT_BUDGET_MS = 60_000L
    }
}

/** One gate per process, shared by the app start warm-up and the background-removal engine. */
object SegmentationModuleGates {
    @Volatile
    private var instance: SegmentationModuleGate? = null

    fun get(context: Context): SegmentationModuleGate = instance ?: synchronized(this) {
        instance ?: SegmentationModuleGate(PlayModuleInstallPort(context)).also { instance = it }
    }
}
