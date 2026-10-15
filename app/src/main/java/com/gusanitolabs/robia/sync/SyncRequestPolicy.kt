package com.gusanitolabs.robia.sync

/**
 * Minimum gap between two WorkManager sync requests. A request inside the gap waits for one deferred request at its end,
 * so back-to-back cycles cannot chain on each other (H01, PLAN-CHANGE 17).
 */
internal const val MIN_SYNC_REQUEST_INTERVAL_MILLIS = 10_000L

internal sealed interface SyncRequestDecision {
    /** Nothing is pending, so a run would have nothing to send. */
    data object Drop : SyncRequestDecision

    /** Outside the interval: enqueue now. */
    data object Enqueue : SyncRequestDecision

    /** Inside the interval and no deferred request is waiting: enqueue once, [delayMillis] from now. */
    data class Defer(val delayMillis: Long) : SyncRequestDecision

    /** A deferred request is already waiting. It starts after this write, so this request merges into it. */
    data object Coalesce : SyncRequestDecision
}

/**
 * Pure rate guard for [SyncWorkScheduler.requestSync]. A real write is never dropped: a request is only dropped when
 * nothing is pending, and a request is only merged into a deferred one that has not started yet.
 */
internal fun shouldRequestSync(
    pendingGarments: Int,
    pendingMetadata: Int,
    pendingEmbeddings: Int,
    lastRequestAt: Long?,
    now: Long,
    minIntervalMs: Long,
    deferredWaiting: Boolean = false,
): SyncRequestDecision {
    if (pendingGarments + pendingMetadata + pendingEmbeddings == 0) return SyncRequestDecision.Drop
    if (deferredWaiting) return SyncRequestDecision.Coalesce
    val last = lastRequestAt ?: return SyncRequestDecision.Enqueue
    val waitMillis = last + minIntervalMs - now
    return if (waitMillis > 0L) SyncRequestDecision.Defer(waitMillis) else SyncRequestDecision.Enqueue
}
