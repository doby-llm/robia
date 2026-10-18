package com.gusanitolabs.robia.media

import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitEngineFlowTest {
    private fun moduleUnavailable() = MlKitException("Waiting for the module to be downloaded", MlKitException.UNAVAILABLE)

    @Test
    fun unavailableThenSuccess_waitsOnceAndProcessesOnce() = runBlocking {
        var ensures = 0
        var processes = 0

        val attempt = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                if (processes == 1) throw moduleUnavailable()
                "foreground"
            },
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
                throw moduleUnavailable()
            },
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
            )
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(1, ensures)
        assertEquals(1, processes)
    }

    @Test
    fun segmentationFailureReason_isModelUnavailableOnlyForTheUnavailableCode() {
        assertTrue(isModuleUnavailableError(moduleUnavailable()))
        assertEquals(BackgroundRemovalFailureReason.MODEL_UNAVAILABLE, segmentationFailureReason(moduleUnavailable()))
        assertEquals(
            BackgroundRemovalFailureReason.SEGMENTATION_FAILED,
            segmentationFailureReason(MlKitException("bad input", MlKitException.INVALID_ARGUMENT)),
        )
        assertEquals(
            BackgroundRemovalFailureReason.SEGMENTATION_FAILED,
            segmentationFailureReason(IllegalStateException("segmenter exploded")),
        )
    }

    @Test
    fun missingForegroundMask_isSegmentationFailedNotModuleUnavailable() {
        assertEquals(BackgroundRemovalFailureReason.SEGMENTATION_FAILED, missingForegroundMaskFailure.reason)
    }
}
