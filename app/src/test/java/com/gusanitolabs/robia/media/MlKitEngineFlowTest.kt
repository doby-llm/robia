package com.gusanitolabs.robia.media

import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MlKitEngineFlowTest {
    private fun moduleUnavailable() = MlKitException("Waiting for the module to be downloaded", MlKitException.UNAVAILABLE)

    @Test
    fun unavailableThenSuccess_waitsOnceAndProcessesOnce_thenRemoves() = runBlocking {
        var ensures = 0
        var processes = 0

        val result = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                if (processes == 1) throw moduleUnavailable()
                "foreground"
            },
        )

        assertEquals("foreground", result)
        assertEquals(2, ensures)
        assertEquals(2, processes)
    }

    @Test
    fun unavailableTwice_propagatesModelUnavailableAfterOneRetry() = runBlocking {
        var ensures = 0
        var processes = 0

        val thrown = assertThrows(MlKitException::class.java) {
            runBlocking {
                runWithModuleRetry(
                    ensureModule = { ensures++ },
                    process = {
                        processes++
                        throw moduleUnavailable()
                    },
                )
            }
        }

        assertEquals(2, ensures)
        assertEquals(2, processes)
        assertEquals(BackgroundRemovalFailureReason.MODEL_UNAVAILABLE, segmentationFailureReason(thrown))
    }

    @Test
    fun otherFailure_isNotRetried_andMapsToSegmentationFailed() = runBlocking {
        var ensures = 0
        var processes = 0

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runWithModuleRetry(
                    ensureModule = { ensures++ },
                    process = {
                        processes++
                        error("segmenter exploded")
                    },
                )
            }
        }

        assertEquals(1, ensures)
        assertEquals(1, processes)
        assertEquals(BackgroundRemovalFailureReason.SEGMENTATION_FAILED, segmentationFailureReason(thrown))
    }
}
