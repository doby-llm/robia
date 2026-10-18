package com.gusanitolabs.robia.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MlKitEngineFlowTest {
    private class ModuleUnavailable : RuntimeException("Waiting for the module to be downloaded")

    private val isModuleUnavailable: (Throwable) -> Boolean = { it is ModuleUnavailable }

    @Test
    fun unavailableThenSuccess_waitsOnceAndProcessesOnce() = runBlocking {
        var ensures = 0
        var processes = 0

        val result = runWithModuleRetry(
            ensureModule = { ensures++ },
            process = {
                processes++
                if (processes == 1) throw ModuleUnavailable()
                "foreground"
            },
            isModuleUnavailable = isModuleUnavailable,
        )

        assertEquals("foreground", result)
        assertEquals(2, ensures)
        assertEquals(2, processes)
    }

    @Test
    fun unavailableTwice_propagatesAfterOneRetry() = runBlocking {
        var ensures = 0
        var processes = 0

        assertThrows(ModuleUnavailable::class.java) {
            runBlocking {
                runWithModuleRetry(
                    ensureModule = { ensures++ },
                    process = {
                        processes++
                        throw ModuleUnavailable()
                    },
                    isModuleUnavailable = isModuleUnavailable,
                )
            }
        }

        assertEquals(2, ensures)
        assertEquals(2, processes)
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
                    isModuleUnavailable = isModuleUnavailable,
                )
            }
        }

        assertEquals(1, ensures)
        assertEquals(1, processes)
        assertEquals(BackgroundRemovalFailureReason.SEGMENTATION_FAILED, segmentationFailureReason(thrown))
    }
}
