package com.gusanitolabs.robia.media

import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitEngineFlowTest {
    private class ModuleUnavailable : RuntimeException("Waiting for the module to be downloaded")

    // Constructing MlKitException throws on the JVM, so retry tests inject a stand-in for the UNAVAILABLE code.
    private val isModuleUnavailable: (Throwable) -> Boolean = { it is ModuleUnavailable }

    @Test
    fun unavailableThenSuccess_waitsOnceAndProcessesOnce() = runBlocking {
        var ensures = 0
        var processes = 0

        val attempt = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                if (processes == 1) throw ModuleUnavailable()
                "foreground"
            },
            isModuleUnavailable = isModuleUnavailable,
        )

        assertEquals(SegmentationAttempt.Success("foreground"), attempt)
        assertEquals(2, ensures)
        assertEquals(2, processes)
    }

    @Test
    fun unavailableTwice_failsAsModuleUnavailableAfterOneRetry() = runBlocking {
        var ensures = 0
        var processes = 0

        val attempt = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                throw ModuleUnavailable()
            },
            isModuleUnavailable = isModuleUnavailable,
        )

        assertEquals(BackgroundRemovalFailureReason.MODEL_UNAVAILABLE, (attempt as SegmentationAttempt.Failed).reason)
        assertEquals(2, ensures)
        assertEquals(2, processes)
    }

    @Test
    fun otherFailure_isNotRetried_andMapsToSegmentationFailed() = runBlocking {
        var ensures = 0
        var processes = 0

        val attempt = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                error("segmenter exploded")
            },
            isModuleUnavailable = isModuleUnavailable,
        )

        assertEquals(BackgroundRemovalFailureReason.SEGMENTATION_FAILED, (attempt as SegmentationAttempt.Failed).reason)
        assertEquals(1, ensures)
        assertEquals(1, processes)
    }

    @Test
    fun cancellationFromProcess_propagatesAndIsNotRetried() = runBlocking {
        var ensures = 0
        var processes = 0

        val thrown = runCatching {
            runWithModuleRetry(
                ensureModule = { ensures++ },
                process = {
                    processes++
                    throw CancellationException("cancelled")
                },
                isModuleUnavailable = isModuleUnavailable,
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(1, ensures)
        assertEquals(1, processes)
    }

    @Test
    fun isModuleUnavailableCode_isTrueOnlyForTheUnavailableCode() {
        assertTrue(isModuleUnavailableCode(MlKitException.UNAVAILABLE))
        assertFalse(isModuleUnavailableCode(0))
    }

    @Test
    fun segmentationFailureReason_mapsOnlyTheUnavailablePredicateToModelUnavailable() {
        assertEquals(
            BackgroundRemovalFailureReason.MODEL_UNAVAILABLE,
            segmentationFailureReason(ModuleUnavailable(), isModuleUnavailable),
        )
        assertEquals(
            BackgroundRemovalFailureReason.SEGMENTATION_FAILED,
            segmentationFailureReason(IllegalStateException("segmenter exploded"), isModuleUnavailable),
        )
    }

    @Test
    fun missingForegroundMask_isSegmentationFailedNotModuleUnavailable() {
        assertEquals(BackgroundRemovalFailureReason.SEGMENTATION_FAILED, missingForegroundMaskFailure.reason)
    }
}
