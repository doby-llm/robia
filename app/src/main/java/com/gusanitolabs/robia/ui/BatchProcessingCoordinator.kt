package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the single batch-processing job for the app shell, rather than a transient screen composition.
 */
internal class BatchProcessingCoordinator(
    private val scope: CoroutineScope,
) {
    private var processingJob: Job? = null

    fun processQueued(
        drafts: () -> List<BatchDraftItem>,
        processDraft: suspend (BatchDraftItem) -> Unit,
        onInterrupted: (BatchDraftItem) -> Unit,
    ) {
        if (processingJob?.isActive == true) return

        processingJob = scope.launch {
            while (true) {
                val next = drafts().firstOrNull { it.status == BatchDraftStatus.Queued } ?: break
                try {
                    processDraft(next)
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    onInterrupted(next)
                    throw cancellation
                }
            }
        }
    }

    fun cancel() {
        processingJob?.cancel()
        processingJob = null
    }
}
