package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cancellation_terminalizesCurrentAndQueuedItemsAsInterrupted() = runBlocking {
        val drafts = MutableList(3) { index ->
            BatchDraftItem(
                orderIndex = index,
                originalPhotoUri = "content://photo/$index",
            )
        }
        val processingStarted = CompletableDeferred<Unit>()
        val coordinator = BatchProcessingCoordinator(this)

        coordinator.processQueued(
            drafts = { drafts.toList() },
            processDraft = { draft ->
                val index = drafts.indexOfFirst { it.id == draft.id }
                drafts[index] = draft.copy(status = BatchDraftStatus.Processing)
                processingStarted.complete(Unit)
                awaitCancellation()
            },
            onInterrupted = { draft ->
                val index = drafts.indexOfFirst { it.id == draft.id }
                drafts[index] = draft.copy(status = BatchDraftStatus.Interrupted)
            },
        )

        processingStarted.await()
        coordinator.cancel()

        withTimeout(1_000) {
            while (drafts.any { it.status == BatchDraftStatus.Processing || it.status == BatchDraftStatus.Queued }) yield()
        }

        assertTrue(drafts.all { it.status == BatchDraftStatus.Interrupted })
    }

    @Test
    fun cancellation_lateOldCoroutineDoesNotTerminalizeReplacementBatch() = runBlocking {
        val oldDrafts = MutableList(2) { index ->
            BatchDraftItem(
                orderIndex = index,
                originalPhotoUri = "content://old/$index",
            )
        }
        val replacementDrafts = mutableListOf(
            BatchDraftItem(
                orderIndex = 0,
                originalPhotoUri = "content://replacement/queued",
            ),
            BatchDraftItem(
                orderIndex = 1,
                originalPhotoUri = "content://replacement/processing",
                status = BatchDraftStatus.Processing,
            ),
        )
        var visibleDrafts = oldDrafts
        val processingStarted = CompletableDeferred<Unit>()
        val coordinator = BatchProcessingCoordinator(this)

        coordinator.processQueued(
            drafts = { visibleDrafts.toList() },
            processDraft = { draft ->
                val index = visibleDrafts.indexOfFirst { it.id == draft.id }
                visibleDrafts[index] = draft.copy(status = BatchDraftStatus.Processing)
                processingStarted.complete(Unit)
                awaitCancellation()
            },
            onInterrupted = { draft ->
                val index = visibleDrafts.indexOfFirst { it.id == draft.id }
                if (index >= 0) {
                    visibleDrafts[index] = visibleDrafts[index].copy(status = BatchDraftStatus.Interrupted)
                }
            },
        )

        processingStarted.await()
        coordinator.cancel()
        visibleDrafts = replacementDrafts
        yield()

        assertTrue(oldDrafts.all { it.status == BatchDraftStatus.Interrupted })
        assertEquals(BatchDraftStatus.Queued, replacementDrafts[0].status)
        assertEquals(BatchDraftStatus.Processing, replacementDrafts[1].status)
    }

    @Test
    fun interruptedForBatch_terminalizesQueuedAndProcessingDraftsAndPreservesFields() {
        val queued = BatchDraftItem(
            id = "queued",
            orderIndex = 0,
            originalPhotoUri = "content://original",
            photoUri = "content://edited",
            photoAspectRatio = 0.75f,
            status = BatchDraftStatus.Queued,
            explicitlyAccepted = true,
            errorMessage = "old message",
            name = "linen shirt",
            notes = "summer",
            selectedTagIds = listOf("category-shirt"),
            fitValue = 2,
            selectedPrimaryColorId = "main-blue",
            selectedSecondaryColorId = "main-white",
            createdAtEpochMillis = 123L,
        )
        val processing = queued.copy(id = "processing", status = BatchDraftStatus.Processing)
        val ready = queued.copy(id = "ready", status = BatchDraftStatus.Ready)

        val interruptedQueued = queued.interruptedForBatch("Interrupted")
        val interruptedProcessing = processing.interruptedForBatch("Interrupted")

        assertEquals(queued.copy(status = BatchDraftStatus.Interrupted, errorMessage = "Interrupted"), interruptedQueued)
        assertEquals(processing.copy(status = BatchDraftStatus.Interrupted, errorMessage = "Interrupted"), interruptedProcessing)
        assertEquals(null, ready.interruptedForBatch("Interrupted"))
    }

    @Test
    fun acceptedCount_readyItemsAreImplicitlyAcceptedButReviewNeedsExplicitAcceptance() {
        val drafts = listOf(
            BatchDraftItem(orderIndex = 0, originalPhotoUri = "content://ready", status = BatchDraftStatus.Ready),
            BatchDraftItem(orderIndex = 1, originalPhotoUri = "content://review", status = BatchDraftStatus.NeedsReview),
            BatchDraftItem(
                orderIndex = 2,
                originalPhotoUri = "content://accepted-review",
                status = BatchDraftStatus.NeedsReview,
                explicitlyAccepted = true,
            ),
        )

        assertEquals(2, drafts.count(BatchDraftItem::isAcceptedForSave))
        assertFalse(drafts[1].isAcceptedForSave())
        assertTrue(drafts[2].isAcceptedForSave())
    }

    @Test
    fun saveSelection_savesFourReadyItemsAndRevalidatesLateStateChanges() {
        val drafts = (MutableList(4) { index ->
            BatchDraftItem(
                orderIndex = index,
                originalPhotoUri = "content://ready/$index",
                status = BatchDraftStatus.Ready,
            )
        } + MutableList(3) { index ->
            BatchDraftItem(
                orderIndex = index + 4,
                originalPhotoUri = "content://review/$index",
                status = BatchDraftStatus.NeedsReview,
            )
        }).toMutableList()
        val requestedIds = drafts.filter(BatchDraftItem::isAcceptedForSave).map(BatchDraftItem::id).toSet()

        assertEquals(4, drafts.acceptedForSave(requestedIds).size)
        assertTrue(drafts.acceptedForSave(requestedIds).all { it.status == BatchDraftStatus.Ready })

        drafts[0] = drafts[0].copy(status = BatchDraftStatus.Failed)
        val lateRevalidated = drafts.acceptedForSave(requestedIds)
        assertEquals(3, lateRevalidated.size)
        assertFalse(lateRevalidated.any { it.id == drafts[0].id })
    }

    @Test
    fun saveGate_requiresTerminalProcessingAndAtLeastOneAcceptedDraft() {
        val ready = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://ready",
            status = BatchDraftStatus.Ready,
        )
        val queued = BatchDraftItem(
            orderIndex = 1,
            originalPhotoUri = "content://queued",
            status = BatchDraftStatus.Queued,
        )
        val pendingReview = BatchDraftItem(
            orderIndex = 2,
            originalPhotoUri = "content://review",
            status = BatchDraftStatus.NeedsReview,
        )

        assertTrue(listOf(ready).canSaveBatch())
        assertFalse(listOf(ready, queued).canSaveBatch())
        assertFalse(listOf(pendingReview).canSaveBatch())
        assertFalse(emptyList<BatchDraftItem>().canSaveBatch())
    }

    @Test
    fun partialSave_removesOnlyAcceptedSnapshotAndRetainsUnresolvedDrafts() {
        val ready = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://ready",
            status = BatchDraftStatus.Ready,
        )
        val pendingReview = BatchDraftItem(
            orderIndex = 1,
            originalPhotoUri = "content://review",
            status = BatchDraftStatus.NeedsReview,
        )
        val failed = BatchDraftItem(
            orderIndex = 2,
            originalPhotoUri = "content://failed",
            status = BatchDraftStatus.Failed,
        )
        val drafts = listOf(ready, pendingReview, failed)
        val savedIds = drafts.acceptedForSave(setOf(ready.id)).map(BatchDraftItem::id).toSet()
        val remaining = drafts.filterNot { it.id in savedIds }

        assertEquals(setOf(ready.id), savedIds)
        assertEquals(2, remaining.size)
        assertEquals(
            setOf(BatchDraftStatus.NeedsReview, BatchDraftStatus.Failed),
            remaining.map(BatchDraftItem::status).toSet(),
        )
    }

    @Test
    fun retry_clearsReviewAcceptanceAndQueuesOnlyRetryableFailures() {
        val failed = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://failed",
            status = BatchDraftStatus.Failed,
            explicitlyAccepted = true,
        )
        val interrupted = failed.copy(status = BatchDraftStatus.Interrupted)

        assertEquals(BatchDraftStatus.Queued, failed.retryForBatch().status)
        assertFalse(failed.retryForBatch().explicitlyAccepted)
        assertEquals(BatchDraftStatus.Queued, interrupted.retryForBatch().status)
        assertEquals(BatchDraftStatus.Ready, failed.copy(status = BatchDraftStatus.Ready).retryForBatch().status)
    }

    @Test
    fun keepOriginalAcceptance_isExplicitAndBackWithoutAcceptanceRemainsNeedsReview() {
        val review = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://review",
            status = BatchDraftStatus.NeedsReview,
        )
        val edited = ClothingItemForBatchTestFactory.item(review.id)

        val unresolved = edited.toBatchDraftFromExisting(review, emptyList(), acceptOriginalPhoto = false)
        val accepted = edited.toBatchDraftFromExisting(review, emptyList(), acceptOriginalPhoto = true)

        assertEquals(BatchDraftStatus.NeedsReview, unresolved.status)
        assertFalse(unresolved.explicitlyAccepted)
        assertEquals(BatchDraftStatus.NeedsReview, accepted.status)
        assertTrue(accepted.explicitlyAccepted)
    }

    private object ClothingItemForBatchTestFactory {
        fun item(id: String) = com.gusanitolabs.robia.core.model.ClothingItem(
            id = id,
            name = "",
            notes = "",
            photoUri = "content://review",
            tags = emptyList(),
            fitValue = null,
            colorMetrics = com.gusanitolabs.robia.core.model.ClothingColorMetrics(),
            isFavorite = false,
            isArchived = false,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
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
