package com.gusanitolabs.robia.media.cutout

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG2-4 / V-SG2-6 engine choice: the truth table of [useEnginePrecise] and the precise → ML Kit fallback order. */
class EngineChoiceTest {
    @Test
    fun useEnginePrecise_isTrueOnlyWhenThePackIsReadyAndTheSettingIsOn() {
        assertTrue(useEnginePrecise(packReady = true, enabled = true))
        assertFalse(useEnginePrecise(packReady = true, enabled = false))
        assertFalse(useEnginePrecise(packReady = false, enabled = true))
        assertFalse(useEnginePrecise(packReady = false, enabled = false))
    }

    @Test
    fun fallbackChain_usesPreciseWhenItSucceeds() {
        val calls = mutableListOf<String>()
        val result = runBlocking {
            cutoutFallbackChain(
                precise = { calls += "precise"; "precise-result" },
                mlKit = { calls += "mlkit"; "mlkit-result" },
                isRemoved = { true },
            )
        }

        assertEquals("precise-result", result)
        assertEquals(listOf("precise"), calls)
    }

    @Test
    fun fallbackChain_goesToMlKitWhenPreciseIsOffTimedOutOrFails() {
        val offOrTimedOut = runBlocking {
            cutoutFallbackChain(precise = { null }, mlKit = { "mlkit-result" }, isRemoved = { true })
        }
        val failed = runBlocking {
            cutoutFallbackChain(precise = { error("model missing") }, mlKit = { "mlkit-result" }, isRemoved = { true })
        }

        assertEquals("mlkit-result", offOrTimedOut)
        assertEquals("mlkit-result", failed)
    }

    @Test
    fun fallbackChain_propagatesCancellationWithoutFallingBack() {
        var mlKitCalled = false

        assertThrows(CancellationException::class.java) {
            runBlocking {
                cutoutFallbackChain(
                    precise = { throw CancellationException("cancelled by the caller") },
                    mlKit = { mlKitCalled = true; "mlkit-result" },
                    isRemoved = { true },
                )
            }
        }
        assertFalse(mlKitCalled)
    }
}
