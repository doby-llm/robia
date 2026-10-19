package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val PRECISE_TIMEOUT: Duration = 30.seconds

/**
 * How [com.gusanitolabs.robia.media.PhotoBackgroundRemover] finds the precise engine. [RobiaApplication] sets both:
 * [precise] builds an engine for one photo, and [isPreciseActive] answers whether new photos use it.
 */
object CutoutEngineRegistry {
    @Volatile var precise: (() -> BackgroundRemovalEngine?)? = null

    @Volatile var isPreciseActive: () -> Boolean = { false }

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
 * included), all go to [mlKit]. Cancellation of the caller always propagates.
 */
internal suspend fun <T> cutoutFallbackChain(
    precise: suspend () -> T?,
    mlKit: suspend () -> T,
    isRemoved: (T) -> Boolean,
    timeout: Duration = PRECISE_TIMEOUT,
): T {
    val preciseResult = try {
        withTimeoutOrNull(timeout) { precise() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        null
    }
    return preciseResult?.takeIf(isRemoved) ?: mlKit()
}
