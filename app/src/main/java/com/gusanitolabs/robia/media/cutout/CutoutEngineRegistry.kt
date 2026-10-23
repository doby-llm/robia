package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val PRECISE_TIMEOUT: Duration = 30.seconds
private const val NANOS_PER_MILLI = 1_000_000L

/** Log tag of the one-line cutout result (`cutout_result engine=… fallback=… ms=…`) and of the debug dump. */
internal const val CUTOUT_LOG_TAG = "RobiaCutout"

internal const val ENGINE_PRECISE = "precise"
internal const val ENGINE_MLKIT = "mlkit"

/** The closed list of fallback reasons: why the precise result was not used. */
internal const val FALLBACK_NONE = "none"
internal const val FALLBACK_NOT_ACTIVE = "not_active"
internal const val FALLBACK_TRIPPED = "tripped"
internal const val FALLBACK_TIMEOUT = "timeout"
internal const val FALLBACK_ERROR = "error"
internal const val FALLBACK_NOT_REMOVED = "not_removed"

/** Which engine produced a cutout, the fallback reason and the time of the whole cutout (both engines included). */
data class CutoutOutcome(val engine: String, val fallback: String, val ms: Long)

/**
 * How [com.gusanitolabs.robia.media.PhotoBackgroundRemover] finds the precise engine. [RobiaApplication] sets both:
 * [precise] builds an engine for one photo, and [options] gives the cutout options for a new photo, read per photo.
 */
object CutoutEngineRegistry {
    @Volatile var precise: (() -> BackgroundRemovalEngine?)? = null

    @Volatile var options: () -> CutoutOptions = { cutoutOptionsOf(RobiaSettings(), packReady = false) }

    /** Whether new photos use the precise engine. */
    fun isPreciseActive(): Boolean = options().usePrecise

    /** One precise pipeline at a time per process: the single-photo flow and a batch share the 1300 MB budget. */
    private val preciseGate = Semaphore(1)

    /** A [LinkageError] from the precise engine makes it unavailable for the rest of the process. */
    internal val preciseLatch = PreciseEngineLatch()

    /** Runs [block] when no other precise pipeline runs; waiting callers suspend in order of arrival. */
    internal suspend fun <T> withPreciseGate(block: suspend () -> T): T = preciseGate.withPermit { block() }
}

/** The precise engine is used for new photos only when the pack is ready and the user switched it on. */
internal fun useEnginePrecise(packReady: Boolean, enabled: Boolean): Boolean = packReady && enabled

/**
 * precise → ML Kit. The precise result counts only when [isRemoved] accepts it and it arrives within [timeout]; a null,
 * unaccepted, timed-out or failed precise result, and any other throwable (a [LinkageError] from class or native loading
 * included), all go to [mlKit]. Cancellation of the caller always propagates. [skipReason] names why a null precise result
 * was skipped; [onOutcome] gets the engine, the fallback reason and the time once the result is known.
 */
internal suspend fun <T> cutoutFallbackChain(
    precise: suspend () -> T?,
    mlKit: suspend () -> T,
    isRemoved: (T) -> Boolean,
    timeout: Duration = PRECISE_TIMEOUT,
    skipReason: () -> String = { FALLBACK_NOT_ACTIVE },
    onOutcome: (CutoutOutcome) -> Unit = {},
): T {
    val started = System.nanoTime()
    val (accepted, fallback) = try {
        val returned = withTimeoutOrNull(timeout) { Returned(precise()) }
        val value = returned?.value
        when {
            returned == null -> null to FALLBACK_TIMEOUT
            value == null -> null to skipReason()
            isRemoved(value) -> value to FALLBACK_NONE
            else -> null to FALLBACK_NOT_REMOVED
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        null to FALLBACK_ERROR
    }
    val result = accepted ?: mlKit()
    onOutcome(
        CutoutOutcome(
            engine = if (accepted != null) ENGINE_PRECISE else ENGINE_MLKIT,
            fallback = fallback,
            ms = (System.nanoTime() - started) / NANOS_PER_MILLI,
        ),
    )
    return result
}

/** Tells a timeout (null from [withTimeoutOrNull]) apart from a precise engine that returned null. */
private class Returned<T>(val value: T?)
