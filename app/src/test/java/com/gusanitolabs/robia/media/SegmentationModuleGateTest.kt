package com.gusanitolabs.robia.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SegmentationModuleGateTest {
    private class FakeModulePort(
        private val availableAtStart: Boolean = false,
        private val installedAfterPolls: Int? = null,
        private val installAccepted: Boolean = true,
    ) : ModuleInstallPort {
        var polls = 0
        var installRequests = 0

        override suspend fun isAvailable(): Boolean {
            polls++
            return availableAtStart || (installedAfterPolls != null && polls >= installedAfterPolls)
        }

        override suspend fun requestInstall(): Boolean {
            installRequests++
            return installAccepted
        }
    }

    private class VirtualClock {
        var elapsedMs = 0L
            private set

        suspend fun delay(ms: Long) {
            elapsedMs += ms
        }
    }

    @Test
    fun alreadyAvailable_returnsAvailableWithoutRequestingInstall() = runBlocking {
        val port = FakeModulePort(availableAtStart = true)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay)

        assertEquals(GateResult.Available, gate.ensureAvailable(60.seconds))
        assertEquals(0, port.installRequests)
        assertEquals(0L, clock.elapsedMs)
    }

    @Test
    fun installsAfterSeveralPolls_returnsAvailableWithinTimeout() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = 3)
        val clock = VirtualClock()
        val preparing = mutableListOf<Boolean>()
        val gate = SegmentationModuleGate(port, clock::delay)

        val result = gate.ensureAvailable(60.seconds, onPreparing = { preparing += it })

        assertEquals(GateResult.Available, result)
        assertEquals(1, port.installRequests)
        assertEquals(4_000L, clock.elapsedMs)
        assertEquals(listOf(true, false), preparing)
    }

    @Test
    fun neverInstalled_returnsUnavailableAfterTimeout() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val preparing = mutableListOf<Boolean>()
        val gate = SegmentationModuleGate(port, clock::delay)

        val result = gate.ensureAvailable(60.seconds, onPreparing = { preparing += it })

        assertTrue(result is GateResult.Unavailable)
        assertEquals(60_000L, clock.elapsedMs)
        assertEquals(1, port.installRequests)
        assertEquals(listOf(true, false), preparing)
    }

    @Test
    fun rejectedInstallRequest_returnsUnavailableWithoutWaiting() = runBlocking {
        val port = FakeModulePort(installAccepted = false)
        val clock = VirtualClock()
        val preparing = mutableListOf<Boolean>()
        val gate = SegmentationModuleGate(port, clock::delay)

        val result = gate.ensureAvailable(60.seconds, onPreparing = { preparing += it })

        assertTrue(result is GateResult.Unavailable)
        assertEquals(0L, clock.elapsedMs)
        assertEquals(emptyList<Boolean>(), preparing)
    }
}
