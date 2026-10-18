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
 */
class SegmentationModuleGate(
    private val port: ModuleInstallPort,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    private val activeWaits = AtomicInteger(0)
    private val _preparing = MutableStateFlow(false)

    /** True while at least one [ensureAvailable] call is waiting for the module to install. */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    suspend fun ensureAvailable(
        timeout: Duration = 60.seconds,
        onPreparing: (Boolean) -> Unit = {},
    ): GateResult {
        if (port.isAvailable()) return GateResult.Available

        onPreparing(true)
        markWaiting(true)
        try {
            return withTimeoutOrNull<GateResult>(timeout) {
                if (!port.requestInstall()) return@withTimeoutOrNull GateResult.Unavailable("Module install request was rejected")

                var waitedMs = 0L
                while (waitedMs < timeout.inWholeMilliseconds) {
                    delayFn(POLL_INTERVAL_MS)
                    waitedMs += POLL_INTERVAL_MS
                    if (port.isAvailable()) return@withTimeoutOrNull GateResult.Available
                }
                GateResult.Unavailable("Module not available after $timeout")
            } ?: GateResult.Unavailable("Module not available after $timeout")
        } finally {
            markWaiting(false)
            onPreparing(false)
        }
    }

    private fun markWaiting(waiting: Boolean) {
        _preparing.value = activeWaits.addAndGet(if (waiting) 1 else -1) > 0
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
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
