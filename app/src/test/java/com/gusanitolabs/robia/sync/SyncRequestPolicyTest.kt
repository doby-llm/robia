package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** H01: the rate guard may delay or merge a sync request, but it never drops one while something is pending. */
class SyncRequestPolicyTest {
    private val interval = MIN_SYNC_REQUEST_INTERVAL_MILLIS

    private fun decide(
        garments: Int = 0,
        metadata: Int = 0,
        embeddings: Int = 0,
        lastRequestAt: Long? = null,
        now: Long = 1_000_000L,
        deferredWaiting: Boolean = false,
        forced: Boolean = false,
    ) = shouldRequestSync(garments, metadata, embeddings, lastRequestAt, now, interval, deferredWaiting, forced)

    @Test
    fun nothingPendingIsDropped() {
        assertEquals(SyncRequestDecision.Drop, decide())
        assertEquals(SyncRequestDecision.Drop, decide(lastRequestAt = 0L))
        assertEquals(SyncRequestDecision.Drop, decide(deferredWaiting = true))
    }

    /** H01 finding B: a forced snapshot export or import keeps its background retry even when nothing is pending. */
    @Test
    fun forcedRequestWithNothingPendingIsEnqueued() {
        assertEquals(SyncRequestDecision.Enqueue, decide(forced = true))
        assertEquals(SyncRequestDecision.Enqueue, decide(forced = true, lastRequestAt = 0L))
        assertEquals(SyncRequestDecision.Defer(delayMillis = 4_000L), decide(forced = true, lastRequestAt = 1_000_000L - interval + 4_000L))
        assertEquals(SyncRequestDecision.Coalesce, decide(forced = true, deferredWaiting = true))
    }

    @Test
    fun anySinglePendingKindIsEnoughToRequest() {
        assertEquals(SyncRequestDecision.Enqueue, decide(garments = 1))
        assertEquals(SyncRequestDecision.Enqueue, decide(metadata = 1))
        assertEquals(SyncRequestDecision.Enqueue, decide(embeddings = 1))
    }

    @Test
    fun firstRequestOrRequestOutsideTheIntervalIsEnqueuedNow() {
        assertEquals(SyncRequestDecision.Enqueue, decide(embeddings = 1, lastRequestAt = null))
        assertEquals(SyncRequestDecision.Enqueue, decide(embeddings = 1, lastRequestAt = 1_000_000L - interval))
        assertEquals(SyncRequestDecision.Enqueue, decide(embeddings = 1, lastRequestAt = 1_000_000L - interval - 1L))
    }

    @Test
    fun requestInsideTheIntervalIsDeferredToItsEnd() {
        assertEquals(
            SyncRequestDecision.Defer(delayMillis = 4_000L),
            decide(garments = 1, lastRequestAt = 1_000_000L - interval + 4_000L),
        )
    }

    /** Finding 1: a clock that moved back must not stretch the wait past one interval. */
    @Test
    fun clockBehindLastRequestWaitsAtMostOneInterval() {
        assertEquals(
            SyncRequestDecision.Defer(delayMillis = interval),
            decide(embeddings = 1, lastRequestAt = 1_000_000L + 3_600_000L),
        )
        assertEquals(
            SyncRequestDecision.Defer(delayMillis = interval),
            decide(embeddings = 1, lastRequestAt = 1_000_000L + 1L),
        )
    }

    @Test
    fun requestWhileDeferredIsWaitingIsCoalescedNotEnqueued() {
        assertEquals(SyncRequestDecision.Coalesce, decide(embeddings = 1, lastRequestAt = 0L, deferredWaiting = true))
        assertEquals(SyncRequestDecision.Coalesce, decide(embeddings = 1, lastRequestAt = null, deferredWaiting = true))
    }

    /** Invariant over a grid: with any pending work, the decision is never Drop, so a real write always gets a request. */
    @Test
    fun pendingWorkIsNeverDropped() {
        val lastRequests = listOf(null, 0L, 1_000_000L - interval, 1_000_000L - 1L, 1_000_000L)
        for (garments in 0..2) for (metadata in 0..2) for (embeddings in 0..2) for (last in lastRequests) for (deferred in listOf(false, true)) {
            val pending = garments + metadata + embeddings
            val decision = decide(garments, metadata, embeddings, last, deferredWaiting = deferred)
            if (pending == 0) {
                assertEquals(SyncRequestDecision.Drop, decision)
            } else {
                assertNotEquals("pending=$pending last=$last deferred=$deferred", SyncRequestDecision.Drop, decision)
            }
        }
    }
}
