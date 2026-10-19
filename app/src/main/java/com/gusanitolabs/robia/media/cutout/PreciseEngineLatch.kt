package com.gusanitolabs.robia.media.cutout

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide record of a [LinkageError] from the precise engine: a native library or a class that cannot load (under R8 the
 * MediaPipe graph failed in its Flogger dependency). The JVM does not retry a class whose static initialiser failed; the next
 * use throws NoClassDefFoundError. Once tripped the precise engine stays unavailable until the process restarts, so new photos
 * go to ML Kit without a precise attempt each time.
 */
internal class PreciseEngineLatch {
    private val linkageFailure = AtomicReference<LinkageError?>(null)

    /** True once a [LinkageError] has been recorded; the precise engine is then reported unavailable. */
    val isTripped: Boolean
        get() = linkageFailure.get() != null

    /**
     * Runs [block] unless the latch is tripped; a tripped latch throws [IllegalStateException] whose cause is the recorded error
     * and does not run [block]. A [LinkageError] from [block] trips the latch and is rethrown. Other throwables pass through.
     */
    fun <T> guard(block: () -> T): T {
        linkageFailure.get()?.let { recorded ->
            throw IllegalStateException("Precise cutout unavailable after ${recorded.javaClass.simpleName}", recorded)
        }
        try {
            return block()
        } catch (linkage: LinkageError) {
            linkageFailure.compareAndSet(null, linkage)
            throw linkage
        }
    }
}
