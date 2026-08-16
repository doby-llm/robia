package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Owns the single batch-processing job for the app shell, rather than a transient screen composition.
 */
internal class BatchProcessingCoordinator(
    private val scope: CoroutineScope,
) {
    private var processingJob: Job? = null
    private var interruptActiveDrafts: (() -> Unit)? = null

    fun processQueued(
        drafts: () -> List<BatchDraftItem>,
        processDraft: suspend (BatchDraftItem) -> Unit,
        onInterrupted: (BatchDraftItem) -> Unit,
    ) {
        if (processingJob?.isActive == true) return

        val ownedDraftIds = drafts().map(BatchDraftItem::id).toSet()

        fun terminalizeActiveDrafts() {
            drafts()
                .filter { draft -> draft.id in ownedDraftIds && draft.isProcessingActive() }
                .forEach(onInterrupted)
        }

        // Mark the old batch synchronously when cancel() is called. This prevents a new batch
        // replacing the list before the cancelled coroutine gets its chance to observe it.
        interruptActiveDrafts = ::terminalizeActiveDrafts
        processingJob = scope.launch {
            try {
                while (true) {
                    val next = drafts().firstOrNull { it.status == BatchDraftStatus.Queued } ?: break
                    processDraft(next)
                }
            } catch (cancellation: CancellationException) {
                // Cancellation can happen from background/process loss without going through
                // cancel(), so terminalize every queued and processing item here as well.
                terminalizeActiveDrafts()
                throw cancellation
            } finally {
                if (processingJob === coroutineContext[Job]) {
                    interruptActiveDrafts = null
                    processingJob = null
                }
            }
        }
    }

    fun cancel() {
        interruptActiveDrafts?.invoke()
        interruptActiveDrafts = null
        processingJob?.cancel()
        processingJob = null
    }
}
