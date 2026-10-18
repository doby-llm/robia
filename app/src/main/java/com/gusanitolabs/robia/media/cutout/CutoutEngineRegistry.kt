package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import kotlinx.coroutines.CancellationException

/**
 * How [com.gusanitolabs.robia.media.PhotoBackgroundRemover] finds the precise engine. [RobiaApplication] sets both:
 * [precise] builds an engine for one photo, and [isPreciseActive] answers whether new photos use it.
 */
object CutoutEngineRegistry {
    @Volatile var precise: (() -> BackgroundRemovalEngine?)? = null

    @Volatile var isPreciseActive: () -> Boolean = { false }
}

/** The precise engine is used for new photos only when the pack is ready and the user switched it on. */
internal fun useEnginePrecise(packReady: Boolean, enabled: Boolean): Boolean = packReady && enabled

/**
 * precise → ML Kit. A null precise result (off, or timed out), any precise failure, and a non-cancellation exception
 * all go to [mlKit]. Cancellation of the caller always propagates.
 */
internal suspend fun <T> cutoutFallbackChain(precise: suspend () -> T?, mlKit: suspend () -> T): T {
    val preciseResult = try {
        precise()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        null
    }
    return preciseResult ?: mlKit()
}
