package com.gusanitolabs.robia.ui

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

private const val BATCH_LOG_TAG = "RobiaBatch"

internal typealias DiscardOutput = (BatchDraftItem, CutoutStageResult) -> Unit

/** Cutouts allowed in flight at once. The suggestion step is always single. SG2a lowers this for the precise engine. */
internal object BatchParallelism {
    @Volatile var cutouts: () -> Int = { 2 }
}

/** Developer timing lines (Logcat `RobiaBatch`). Unit tests replace it: android.util.Log is not available on the JVM. */
internal object BatchTelemetry {
    @Volatile var log: (String) -> Unit = { line -> Log.d(BATCH_LOG_TAG, line) }
}

/**
 * Owns the single batch-processing job for the app shell, rather than a transient screen composition.
 *
 * The job keeps an ordered window of up to `parallelism + 1` garments. Their cutouts run while the oldest
 * garment is suggested, and final states are published in screen order.
 */
internal class BatchProcessingCoordinator(
    private val scope: CoroutineScope,
) {
    private var processingJob: Job? = null
    private var interruptActiveDrafts: (() -> Unit)? = null
    private var restartRequested = false
    private var jobOwnedDraftIds: MutableSet<String> = mutableSetOf()

    internal val isRunning: Boolean
        get() = processingJob?.isActive == true

    /**
     * [discardOutput] receives the draft and a cutout result that will not be published (the job was cancelled): the
     * caller deletes the files it points to.
     */
    fun processQueued(
        drafts: () -> List<BatchDraftItem>,
        cutout: suspend (BatchDraftItem) -> CutoutStageResult,
        suggest: suspend (BatchDraftItem, CutoutStageResult.Ready) -> BatchDraftItem,
        publish: (BatchDraftItem) -> Unit,
        onInterrupted: (BatchDraftItem) -> Unit,
        discardOutput: DiscardOutput = { _, _ -> },
    ) {
        if (isRunning) {
            // A Retry or a new Queued draft arrived while the job runs. The job picks it up before it ends.
            restartRequested = true
            // The restarted pass processes these drafts, so cancel() must terminalize them too.
            jobOwnedDraftIds.addAll(drafts().filter { draft -> draft.status == BatchDraftStatus.Queued }.map(BatchDraftItem::id))
            return
        }

        val ownedDraftIds = drafts().map(BatchDraftItem::id).toMutableSet()
        jobOwnedDraftIds = ownedDraftIds

        fun terminalizeActiveDrafts() {
            drafts()
                .filter { draft -> draft.id in ownedDraftIds && draft.isProcessingActive() }
                .forEach(onInterrupted)
        }

        // Mark the old batch synchronously when cancel() is called. This prevents a new batch
        // replacing the list before the cancelled coroutine gets its chance to observe it.
        interruptActiveDrafts = ::terminalizeActiveDrafts
        restartRequested = false
        processingJob = scope.launch {
            // Outside try, so the cancellation path can see cutouts that finished but were never published.
            val window = ArrayDeque<Pair<BatchDraftItem, Deferred<TimedCutout>>>()
            try {
                val parallelism = BatchParallelism.cutouts().coerceIn(1, 2)
                val permits = Semaphore(parallelism)
                val batchStartedAt = System.nanoTime()
                var dispatched = 0

                publishInOrder@ while (true) {
                    // A cancelled job whose loop never suspends would otherwise keep dispatching.
                    coroutineContext.ensureActive()

                    // (a) Keep the window full, so the next cutouts run while the oldest garment is suggested.
                    while (window.size < parallelism + 1) {
                        val next = drafts()
                            .filter { draft ->
                                draft.status == BatchDraftStatus.Queued &&
                                    window.none { (windowDraft, _) -> windowDraft.id == draft.id }
                            }
                            .minByOrNull(BatchDraftItem::orderIndex)
                            ?: break
                        publish(next.copy(status = BatchDraftStatus.Processing, explicitlyAccepted = false, errorMessage = null))
                        window.addLast(next to async { permits.withPermit { timedCutout(next, cutout, discardOutput) } })
                        dispatched++
                    }

                    // (b) Nothing in flight: finish, unless a Retry or a new draft arrived meanwhile.
                    if (window.isEmpty()) {
                        if (restartRequested || drafts().any { draft -> draft.status == BatchDraftStatus.Queued }) {
                            restartRequested = false
                            continue@publishInOrder
                        }
                        break
                    }

                    // (c) Suggest the oldest garment, one at a time, and publish its final state.
                    val (draft, pending) = window.removeFirst()
                    val waitStartedAt = System.nanoTime()
                    val timed = try {
                        pending.await()
                    } catch (cancellation: CancellationException) {
                        // The cutout may have finished before the cancel. Its output would be lost with this coroutine.
                        pending.discardIfCompleted(draft, discardOutput)
                        throw cancellation
                    }
                    val waitMs = elapsedMsSince(waitStartedAt)
                    val suggestStartedAt = System.nanoTime()
                    val finished = try {
                        when (val result = timed.result) {
                            is CutoutStageResult.Failed -> draft.copy(status = BatchDraftStatus.Failed, errorMessage = result.message)
                            is CutoutStageResult.Ready -> suggestOrFail(draft, result, suggest)
                        }
                    } catch (cancellation: CancellationException) {
                        discardOutput(draft, timed.result)
                        throw cancellation
                    }
                    val suggestMs = elapsedMsSince(suggestStartedAt)
                    // A cancelled job must not publish a result it finished after cancellation.
                    ensureActiveOrDiscard(draft, timed.result, discardOutput)
                    publish(finished)
                    BatchTelemetry.log("batch_item idx=${draft.orderIndex} cutout_ms=${timed.elapsedMs} suggest_ms=$suggestMs wait_ms=$waitMs")
                }
                BatchTelemetry.log("batch_total_ms=${elapsedMsSince(batchStartedAt)} items=$dispatched")
            } catch (cancellation: CancellationException) {
                // Cancellation can happen from background/process loss without going through
                // cancel(), so terminalize every queued and processing item here as well.
                terminalizeActiveDrafts()
                // A cutout that finished before the cancel was never published, so its output goes too.
                window.forEach { (draft, pending) -> pending.discardIfCompleted(draft, discardOutput) }
                throw cancellation
            } finally {
                if (processingJob === coroutineContext[Job]) {
                    interruptActiveDrafts = null
                    processingJob = null
                }
            }
        }
    }

    /**
     * Cancels the running job and returns it. The caller joins it before deleting any file the job may still write.
     */
    fun cancel(): Job? {
        interruptActiveDrafts?.invoke()
        interruptActiveDrafts = null
        val job = processingJob
        job?.cancel()
        processingJob = null
        return job
    }

    private suspend fun suggestOrFail(
        draft: BatchDraftItem,
        cutout: CutoutStageResult.Ready,
        suggest: suspend (BatchDraftItem, CutoutStageResult.Ready) -> BatchDraftItem,
    ): BatchDraftItem = try {
        suggest(draft, cutout)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Exception) {
        draft.copy(status = BatchDraftStatus.Failed, errorMessage = throwable.message?.takeIf { it.isNotBlank() })
    }
}

private class TimedCutout(val result: CutoutStageResult, val elapsedMs: Long)

private suspend fun timedCutout(
    draft: BatchDraftItem,
    cutout: suspend (BatchDraftItem) -> CutoutStageResult,
    discardOutput: DiscardOutput,
): TimedCutout {
    val startedAt = System.nanoTime()
    val result = try {
        cutout(draft)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Exception) {
        CutoutStageResult.Failed(throwable.message?.takeIf { it.isNotBlank() })
    }
    // A cutout that returns after cancellation must not hand its Ready result to the publish step.
    ensureActiveOrDiscard(draft, result, discardOutput)
    return TimedCutout(result, elapsedMsSince(startedAt))
}

/** Throws if the job was cancelled, after giving the result that will not be published to [discardOutput]. */
private suspend fun ensureActiveOrDiscard(draft: BatchDraftItem, result: CutoutStageResult, discardOutput: DiscardOutput) {
    try {
        coroutineContext.ensureActive()
    } catch (cancellation: CancellationException) {
        discardOutput(draft, result)
        throw cancellation
    }
}

/** Hands the output of a cutout that finished but will not be published to [discardOutput]. */
private fun Deferred<TimedCutout>.discardIfCompleted(draft: BatchDraftItem, discardOutput: DiscardOutput) {
    if (!isCompleted || isCancelled) return
    runCatching { getCompleted() }.onSuccess { timed -> discardOutput(draft, timed.result) }
}

private fun elapsedMsSince(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
