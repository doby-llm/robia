package com.gusanitolabs.robia.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
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

        fun nowMs(): Long = elapsedMs
    }

    private class SwitchableModulePort : ModuleInstallPort {
        var available = false
        var installRequests = 0

        override suspend fun isAvailable(): Boolean = available

        override suspend fun requestInstall(): Boolean {
            installRequests++
            return true
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
    fun preparingFlow_isTrueDuringWaitAndClearsAfterInstall() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = 3)
        val clock = VirtualClock()
        val seenDuringWait = mutableListOf<Boolean>()
        lateinit var gate: SegmentationModuleGate
        gate = SegmentationModuleGate(port, { ms ->
            seenDuringWait += gate.preparing.value
            clock.delay(ms)
        })

        assertFalse(gate.preparing.value)
        assertEquals(GateResult.Available, gate.ensureAvailable(60.seconds))

        assertEquals(listOf(true, true), seenDuringWait)
        assertFalse(gate.preparing.value)
    }

    @Test
    fun preparingFlow_clearsAfterTimeout() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay)

        assertTrue(gate.ensureAvailable(6.seconds) is GateResult.Unavailable)

        assertFalse(gate.preparing.value)
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
        assertEquals(listOf(true, false), preparing)
        assertFalse(gate.preparing.value)
    }

    @Test
    fun requestInstallHangs_timesOutAsUnavailable_andClearsPreparing() = runBlocking {
        val gate = SegmentationModuleGate(HangingInstallPort())
        val preparing = mutableListOf<Boolean>()

        val result = gate.ensureAvailable(50.milliseconds, onPreparing = { preparing += it })

        assertTrue(result is GateResult.Unavailable)
        assertEquals(listOf(true, false), preparing)
        assertFalse(gate.preparing.value)
    }

    @Test
    fun cancellationDuringWait_propagatesAndClearsPreparing() = runBlocking {
        val gate = SegmentationModuleGate(FakeModulePort(installedAfterPolls = null), { awaitCancellation() })
        var propagated = false
        val waiter = launch {
            try {
                gate.ensureAvailable(60.seconds)
            } catch (cancellation: CancellationException) {
                propagated = true
                throw cancellation
            }
        }

        gate.preparing.first { it }
        waiter.cancelAndJoin()

        assertTrue(propagated)
        assertFalse(gate.preparing.value)
    }

    @Test
    fun batch_waitBudgetIsSixtySecondsAndRetryDoesNotResetIt() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay, clock::nowMs)

        gate.startBatch()
        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)
        assertEquals(60_000L, clock.elapsedMs)

        // The retry in runWithModuleRetry asks again: the budget is spent, so it returns at once.
        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)
        assertEquals(60_000L, clock.elapsedMs)
        assertEquals(1, port.installRequests)
        assertFalse(gate.preparing.value)
    }

    @Test
    fun batch_laterCallWaitsOnlyForWhatIsLeftOfTheBudget() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay, clock::nowMs)

        gate.startBatch()
        gate.ensureAvailable(20.seconds)
        assertEquals(20_000L, clock.elapsedMs)

        gate.ensureAvailable(60.seconds)
        assertEquals(60_000L, clock.elapsedMs)
    }

    @Test
    fun batch_fastPathStillWinsWhenTheModuleAppearsAfterTheBudgetIsSpent() = runBlocking {
        val port = SwitchableModulePort()
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay, clock::nowMs)

        gate.startBatch()
        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)

        port.available = true
        assertEquals(GateResult.Available, gate.ensureAvailable(60.seconds))
        assertEquals(1, port.installRequests)
        assertEquals(60_000L, clock.elapsedMs)
    }

    @Test
    fun batch_concurrentCallsShareOneBudget_laterCallWaitsOnlyForTheRemainder() = runTest {
        val port = FakeModulePort(installedAfterPolls = null)
        val gate = SegmentationModuleGate(port, { delay(it) }, { currentTime })

        gate.startBatch()
        val first = async { gate.ensureAvailable(60.seconds) }
        delay(20.seconds)
        // Started while the first call is still waiting: it must end at the batch deadline (60 s), not 20 s + 60 s.
        val second = async { gate.ensureAvailable(60.seconds) }
        val results = awaitAll(first, second)

        assertTrue(results.all { it is GateResult.Unavailable })
        assertEquals(60_000L, currentTime)
        assertEquals(2, port.installRequests)
    }

    @Test
    fun outsideBatch_eachCallWaitsItsOwnTimeout() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay, clock::nowMs)

        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)
        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)
        assertEquals(120_000L, clock.elapsedMs)
        assertEquals(2, port.installRequests)
    }

    @Test
    fun endBatch_restoresPerCallTimeouts() = runBlocking {
        val port = FakeModulePort(installedAfterPolls = null)
        val clock = VirtualClock()
        val gate = SegmentationModuleGate(port, clock::delay, clock::nowMs)

        gate.startBatch()
        gate.ensureAvailable(60.seconds)
        gate.endBatch()
        assertTrue(gate.ensureAvailable(60.seconds) is GateResult.Unavailable)

        assertEquals(120_000L, clock.elapsedMs)
    }

    private class HangingInstallPort : ModuleInstallPort {
        override suspend fun isAvailable(): Boolean = false

        override suspend fun requestInstall(): Boolean = awaitCancellation()
    }
}
