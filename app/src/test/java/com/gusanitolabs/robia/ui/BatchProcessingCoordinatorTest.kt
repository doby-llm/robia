package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchProcessingCoordinatorTest {
    @Test
    fun cancellation_terminalizesTheCurrentItemAsInterrupted() = runBlocking {
        val drafts = mutableListOf(BatchDraftItem(orderIndex = 0, originalPhotoUri = "content://photo"))
        val processingStarted = CompletableDeferred<Unit>()
        val coordinator = BatchProcessingCoordinator(this)

        coordinator.processQueued(
            drafts = { drafts.toList() },
            processDraft = { draft ->
                drafts[0] = draft.copy(status = BatchDraftStatus.Processing)
                processingStarted.complete(Unit)
                awaitCancellation()
            },
            onInterrupted = { draft ->
                drafts[0] = draft.copy(status = BatchDraftStatus.Interrupted)
            },
        )

        processingStarted.await()
        coordinator.cancel()
        withTimeout(1_000) {
            while (drafts.single().status == BatchDraftStatus.Processing) yield()
        }

        assertEquals(BatchDraftStatus.Interrupted, drafts.single().status)
    }

    @Test
    fun serialCoordinatorCompletesAll60QueuedItemsToTerminalStates() = runBlocking {
        val drafts = MutableList(60) { index ->
            BatchDraftItem(orderIndex = index, originalPhotoUri = "content://photo/$index")
        }
        val coordinator = BatchProcessingCoordinator(this)

        coordinator.processQueued(
            drafts = { drafts.toList() },
            processDraft = { draft ->
                val index = drafts.indexOfFirst { it.id == draft.id }
                drafts[index] = draft.copy(status = BatchDraftStatus.Ready)
            },
            onInterrupted = { draft ->
                val index = drafts.indexOfFirst { it.id == draft.id }
                drafts[index] = draft.copy(status = BatchDraftStatus.Interrupted)
            },
        )

        withTimeout(1_000) {
            while (drafts.any { it.status == BatchDraftStatus.Queued }) yield()
        }

        assertTrue(drafts.all { it.status == BatchDraftStatus.Ready })
    }
}
