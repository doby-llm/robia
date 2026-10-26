package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.ProposalKind
import com.gusanitolabs.robia.suggestions.ReviewProposal
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ReviewDiscardTest {
    private val category = GarmentTag(id = "top", categoryId = "category", name = "Tops", sortOrder = 0)

    // Tag review: discard and the commit, accept-all count, and auto-close.

    @Test
    fun discardedSuggestedCardIsNotCommitted() {
        val kept = candidate("kept", ProposalKind.Suggested)
        val discarded = candidate("discarded", ProposalKind.Suggested)

        assertEquals(
            listOf(kept),
            tagReviewCommitCandidates(listOf(kept, discarded), acceptedIds = emptySet(), discardedIds = setOf("discarded")),
        )
    }

    @Test
    fun discardedManualChoiceIsNotCommittedEvenWhenAcceptedBefore() {
        val manual = candidate("manual", ProposalKind.ChooseManually)

        assertEquals(
            emptyList<TagReviewCandidate>(),
            tagReviewCommitCandidates(listOf(manual), acceptedIds = setOf("manual"), discardedIds = setOf("manual")),
        )
    }

    @Test
    fun acceptAllCountsOpenSuggestedCardsOnly() {
        val candidates = listOf(
            candidate("open1", ProposalKind.Suggested),
            candidate("open2", ProposalKind.Suggested),
            candidate("accepted", ProposalKind.Suggested),
            candidate("discarded", ProposalKind.Suggested),
            candidate("manual", ProposalKind.ChooseManually),
        )

        assertEquals(4, tagReviewOpenAcceptableCount(candidates, closedIds = emptySet()))
        assertEquals(2, tagReviewOpenAcceptableCount(candidates, closedIds = setOf("accepted", "discarded")))
        assertEquals(0, tagReviewOpenAcceptableCount(candidates, closedIds = setOf("open1", "open2", "accepted", "discarded")))
    }

    @Test
    fun reviewClosesWhenEveryCardIsAcceptedOrDiscarded() {
        val ids = setOf("accepted", "discarded")

        assertTrue(
            shouldAutoCommit(
                candidateIds = ids,
                closedIds = setOf("accepted") + setOf("discarded"),
                isScanning = false,
                committed = false,
                pickerOpen = false,
                discardDialogOpen = false,
            ),
        )
        assertFalse(
            shouldAutoCommit(
                candidateIds = ids + "open",
                closedIds = setOf("accepted", "discarded"),
                isScanning = false,
                committed = false,
                pickerOpen = false,
                discardDialogOpen = false,
            ),
        )
    }

    // Colour review: the commit rule and the removed-colour clearing.

    @Test
    fun colourCommitRuleSkipsDiscardedCardsAndUnresolvedOnes() {
        assertFalse(colorReviewIncludedInCommit(accepted = false, discarded = false, hasConcreteTarget = false))
        assertTrue(colorReviewIncludedInCommit(accepted = false, discarded = false, hasConcreteTarget = true))
        assertTrue(colorReviewIncludedInCommit(accepted = true, discarded = false, hasConcreteTarget = false))
        assertFalse(colorReviewIncludedInCommit(accepted = false, discarded = true, hasConcreteTarget = true))
        assertFalse(colorReviewIncludedInCommit(accepted = true, discarded = true, hasConcreteTarget = true))
    }

    @Test
    fun garmentLeftOutOfACommitIsClearedOfARemovedColour() {
        val blueGarment = garment("g1", primary = "blue", secondary = "red")
        val result = garmentsWithRemovedColors(listOf(blueGarment), removedIds = listOf("blue"), excludeIds = emptySet())

        val cleared = result.single().colorMetrics
        assertNull(cleared.primaryPaletteColorId)
        assertNull(cleared.primaryRawValue)
        // The other slot is kept.
        assertEquals("red", cleared.secondaryPaletteColorId)
    }

    @Test
    fun garmentInTheCommitIsLeftToTheCommit() {
        val blueGarment = garment("g1", primary = "blue", secondary = null)

        assertEquals(
            emptyList<ClothingItem>(),
            garmentsWithRemovedColors(listOf(blueGarment), removedIds = listOf("blue"), excludeIds = setOf("g1")),
        )
    }

    @Test
    fun garmentWithoutARemovedColourIsNotWritten() {
        val red = garment("g1", primary = "red", secondary = null)

        assertEquals(emptyList<ClothingItem>(), garmentsWithRemovedColors(listOf(red), removedIds = listOf("blue"), excludeIds = emptySet()))
        assertEquals(emptyList<ClothingItem>(), garmentsWithRemovedColors(listOf(red), removedIds = emptyList(), excludeIds = emptySet()))
    }

    @Test
    fun everyRemovedColourIsClearedInOneGarment() {
        val mixed = garment("g1", primary = "blue", secondary = "green")

        val cleared = garmentsWithRemovedColors(listOf(mixed), removedIds = listOf("blue", "green"), excludeIds = emptySet()).single()

        assertNull(cleared.colorMetrics.primaryPaletteColorId)
        assertNull(cleared.colorMetrics.secondaryPaletteColorId)
    }

    // Zero-garment guard.

    @Test
    fun tagReviewNeedsAnActiveGarment() {
        assertFalse(hasReviewableGarments(emptyList(), ReviewKind.Tag))
        assertFalse(hasReviewableGarments(listOf(garment("a", isArchived = true)), ReviewKind.Tag))
        assertTrue(hasReviewableGarments(listOf(garment("a", isArchived = true), garment("b")), ReviewKind.Tag))
    }

    @Test
    fun colourReviewNeedsAnActiveGarmentWithAPhoto() {
        assertFalse(hasReviewableGarments(listOf(garment("a")), ReviewKind.Colour))
        assertFalse(hasReviewableGarments(listOf(garment("a", photo = "content://a", isArchived = true)), ReviewKind.Colour))
        assertTrue(hasReviewableGarments(listOf(garment("a", photo = "content://a")), ReviewKind.Colour))
    }

    @Test
    fun colourRestoreReviewsEveryActiveGarment() {
        assertFalse(hasReviewableGarments(emptyList(), ReviewKind.ColourRestore))
        assertFalse(hasReviewableGarments(listOf(garment("a", isArchived = true)), ReviewKind.ColourRestore))
        assertTrue(hasReviewableGarments(listOf(garment("a")), ReviewKind.ColourRestore))
    }

    // The offer does not wait for the sync that follows the write.

    @Test
    fun offerDoesNotWaitForTheSyncThatFollowsTheWrite() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        // The database part finishes at once. The sync after it is suspended and never returns, like a cycle stuck on Drive.
        val syncNeverReturns = CompletableDeferred<Unit>()
        val databaseWrite = scope.launch {
            delay(5)
            scope.launch { syncNeverReturns.await() }
        }
        try {
            val request = withTimeout(2_000) {
                offerTagReviewRequest(
                    changeSet = deleteChangeSet(),
                    readGarments = { afterPreviousWrite(databaseWrite) { listOf(garment("g1")) } },
                    write = {},
                )
            }
            assertNotNull(request)
        } finally {
            scope.cancel()
        }
    }

    // The detach: a sync that blocks does not hold the snapshot or the prompt back, and the snapshot is read before the write.

    @Test
    fun snapshotAndPromptDoNotWaitForASyncThatBlocks() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        // Every enqueue blocks, like a Drive call that never returns during the test.
        val enqueueReturns = CompletableDeferred<Unit>()
        val events = CopyOnWriteArrayList<String>()
        val writes = ReviewWrites(scope) { _ ->
            events.add("enqueue")
            enqueueReturns.await()
        }
        try {
            // An earlier save: its database part finishes at once, its sync stays blocked.
            writes.launch { listOf(WardrobeSyncOperation.UpsertTags(setOf("earlier"))) }

            val request = withTimeout(2_000) {
                offerTagReviewRequest(
                    changeSet = deleteChangeSet(),
                    readGarments = { afterPreviousWrite(writes.latest) { events.add("read"); listOf(garment("g1")) } },
                    write = {
                        events.add("write")
                        writes.launch { listOf(WardrobeSyncOperation.UpsertTags(setOf(category.id))) }
                    },
                )
            }

            assertNotNull(request)
            // The prompt was offered while every sync was still blocked.
            assertFalse(enqueueReturns.isCompleted)
            // The snapshot is read before the write it describes.
            assertEquals(listOf("read", "write"), events.filter { event -> event == "read" || event == "write" })
        } finally {
            enqueueReturns.complete(Unit)
            scope.cancel()
        }
    }

    // Discard and cancel run as the latest review write. Their plan must read garments without joining that write.

    @Test
    fun undoCompletesWhenItsPlanReadsGarmentsWithoutJoiningIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val request = TagReviewRequest(deleteChangeSet(), listOf(garment("g1")))
        val writes = ReviewWrites(scope) { _ -> }
        try {
            // The save being undone is still in flight: the undo must wait for it, then build its plan.
            writes.launch {
                delay(20)
                listOf(WardrobeSyncOperation.UpsertTags(setOf(category.id)))
            }
            val applied = CompletableDeferred<ReviewUndoPlan>()

            writes.launchUndo(
                buildPlan = { tagReviewUndoPlan(request, currentGarments = listOf(garment("g1")), now = 0L) },
                applyPlan = { plan ->
                    applied.complete(plan)
                    emptyList()
                },
            )

            // A plan that joined the latest write here would wait on the undo itself and time out.
            val plan = withTimeout(2_000) { applied.await() }
            assertEquals(listOf(category), plan.restoreTags)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun offerIsNotMadeWhenOnlyArchivedGarmentsExist() = runBlocking {
        var written = false

        val request = offerTagReviewRequest(
            changeSet = deleteChangeSet(),
            readGarments = { listOf(garment("g1", isArchived = true)) },
            write = { written = true },
        )

        assertNull(request)
        // The change is still saved: only the review is skipped.
        assertTrue(written)
    }

    @Test
    fun offerIsMadeWithTheSnapshotTakenBeforeTheWrite() = runBlocking {
        val changeSet = deleteChangeSet()
        val snapshot = listOf(garment("g1"))
        var written = false

        val request = offerTagReviewRequest(changeSet, readGarments = { snapshot }, write = { written = true })

        assertEquals(TagReviewRequest(changeSet, snapshot), request)
        assertTrue(written)
    }

    @Test
    fun noChangeSetMeansNoOfferButTheWriteStillLands() = runBlocking {
        var written = false

        assertNull(offerTagReviewRequest(changeSet = null, readGarments = { listOf(garment("g1")) }, write = { written = true }))
        assertTrue(written)
    }

    private fun candidate(id: String, kind: ProposalKind): TagReviewCandidate {
        val item = garment(id)
        return TagReviewCandidate(
            item = item,
            proposal = ReviewProposal(garmentId = id, before = emptySet(), proposed = setOf(category.id), kind = kind),
            live = item,
        )
    }

    private fun deleteChangeSet() = TagChangeSet(
        id = "delete",
        operation = TagOperation.Deleted,
        categoryId = "category",
        touchedTagIds = setOf(category.id),
        beforeTags = listOf(category),
        afterTags = emptyList(),
    )

    private fun garment(
        id: String,
        photo: String? = null,
        primary: String? = null,
        secondary: String? = null,
        isArchived: Boolean = false,
    ) = ClothingItem(
        id = id,
        name = id,
        photoUri = photo,
        tags = listOf(category),
        colorMetrics = ClothingColorMetrics(
            primaryPaletteColorId = primary,
            secondaryPaletteColorId = secondary,
        ),
        isArchived = isArchived,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
