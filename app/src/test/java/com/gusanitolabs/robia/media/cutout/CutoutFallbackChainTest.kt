package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The precise → ML Kit chain under virtual time: the 30 s budget and the REMOVED check are part of the chain. */
@OptIn(ExperimentalCoroutinesApi::class)
class CutoutFallbackChainTest {
    private data class Outcome(val status: BackgroundRemovalStatus, val source: String)

    private val preciseRemoved = Outcome(BackgroundRemovalStatus.REMOVED, "precise")

    @Test
    fun preciseRemoved_isUsedAndMlKitIsNotCalled() = runTest {
        var mlKitCalls = 0

        val result = cutoutFallbackChain(
            precise = { preciseRemoved },
            mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
        )

        assertEquals(preciseRemoved, result)
        assertEquals(0, mlKitCalls)
    }

    @Test
    fun preciseOriginalFallback_goesToMlKitOnce() = runTest {
        var mlKitCalls = 0

        val result = cutoutFallbackChain(
            precise = { Outcome(BackgroundRemovalStatus.ORIGINAL_FALLBACK, "precise") },
            mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
        )

        assertEquals("mlkit", result.source)
        assertEquals(1, mlKitCalls)
    }

    @Test
    fun preciseDelaysPast30Seconds_goesToMlKitOnce() = runTest {
        var mlKitCalls = 0

        val result = cutoutFallbackChain(
            precise = { delay(31.seconds); preciseRemoved },
            mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
        )

        assertEquals("mlkit", result.source)
        assertEquals(1, mlKitCalls)
        assertEquals(30_000L, testScheduler.currentTime)
    }

    @Test
    fun preciseFinishingJustInsideTheBudget_isUsed() = runTest {
        var mlKitCalls = 0

        val result = cutoutFallbackChain(
            precise = { delay(29.seconds); preciseRemoved },
            mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
        )

        assertEquals(preciseRemoved, result)
        assertEquals(0, mlKitCalls)
    }

    @Test
    fun preciseThrows_goesToMlKitOnce() = runTest {
        var mlKitCalls = 0

        val result = cutoutFallbackChain(
            precise = { error("model missing") },
            mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
            isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
        )

        assertEquals("mlkit", result.source)
        assertEquals(1, mlKitCalls)
    }

    @Test
    fun callerCancellationFromPrecise_propagatesAndMlKitIsNotCalled() = runTest {
        var mlKitCalls = 0

        val thrown = try {
            cutoutFallbackChain(
                precise = { throw CancellationException("cancelled by the caller") },
                mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
                isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
            )
            null
        } catch (cancellation: CancellationException) {
            cancellation
        }

        assertTrue(thrown is CancellationException)
        assertEquals(0, mlKitCalls)
    }

    @Test
    fun selectorInitialisationError_goesToMlKitOnce_andLaterPhotosSkipThePreciseEngine() = runTest {
        val latch = PreciseEngineLatch()
        var selectorFactoryCalls = 0
        var mlKitCalls = 0
        val precise: suspend () -> Outcome? = {
            latch.guard {
                selectorFactoryCalls++
                throw ExceptionInInitializerError(IllegalStateException("no caller found on the stack for: x7.c"))
            }
        }
        val mlKit: suspend () -> Outcome = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") }

        val first = cutoutFallbackChain(precise = precise, mlKit = mlKit, isRemoved = { it.status == BackgroundRemovalStatus.REMOVED })

        assertEquals("mlkit", first.source)
        assertEquals(1, selectorFactoryCalls)
        assertEquals(1, mlKitCalls)
        assertTrue(latch.isTripped)

        val second = cutoutFallbackChain(precise = precise, mlKit = mlKit, isRemoved = { it.status == BackgroundRemovalStatus.REMOVED })

        assertEquals("mlkit", second.source)
        assertEquals(1, selectorFactoryCalls)
        assertEquals(2, mlKitCalls)
    }

    @Test
    fun cancellationFromSelectorInitialisation_propagates_andTheLatchStaysUntripped() = runTest {
        val latch = PreciseEngineLatch()
        var mlKitCalls = 0

        val thrown = try {
            cutoutFallbackChain(
                precise = { latch.guard { throw CancellationException("cancelled by the caller") } },
                mlKit = { mlKitCalls++; Outcome(BackgroundRemovalStatus.REMOVED, "mlkit") },
                isRemoved = { it.status == BackgroundRemovalStatus.REMOVED },
            )
            null
        } catch (cancellation: CancellationException) {
            cancellation
        }

        assertTrue(thrown is CancellationException)
        assertEquals(0, mlKitCalls)
        assertFalse(latch.isTripped)
    }
}
