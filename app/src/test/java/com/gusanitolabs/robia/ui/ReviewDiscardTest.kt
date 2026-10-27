package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
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

    // Tag review: the decision rules that replace the commit at the end (the flow is in TagReviewSaveTest).

    @Test
    fun rejectedSuggestedCardIsNeverWritten() {
        val session = TagReviewSession()
            .withCard(card("kept", ReviewChange.Add("casual")))
            .withCard(card("rejected", ReviewChange.Add("casual")))
            .reject("rejected")

        val (_, delta) = session.apply("rejected", existingTagIds = setOf("casual"))

        assertNull(delta)
        assertEquals(listOf("kept"), session.pending.map { card -> card.garmentId })
    }

    @Test
    fun rejectedManualChoiceIsNeverWrittenEvenWhenChosenBefore() {
        val session = TagReviewSession()
            .withCard(card("manual", needsChoice = true, offered = setOf("casual")))
            .choose("manual", "casual")
            .reject("manual")

        val (after, delta) = session.apply("manual", existingTagIds = setOf("casual"))

        assertNull(delta)
        assertTrue(after.cards.single().decision is ReviewDecision.Rejected)
    }

    @Test
    fun leftCountCountsOpenCardsOnly() {
        val session = TagReviewSession()
            .withCard(card("open1", ReviewChange.Add("casual")))
            .withCard(card("open2", ReviewChange.Add("weekend")))
            .withCard(card("accepted", ReviewChange.Add("evening")))
            .withCard(card("discarded", ReviewChange.Add("casual")))
            .withCard(card("manual", needsChoice = true, offered = setOf("casual")))
            .apply("accepted", existingTagIds = setOf("casual", "weekend", "evening")).first
            .reject("discarded")

        // The applied and the discarded cards are closed; the two open cards and the manual choice (no choice yet) are left.
        assertEquals(3, session.leftCount)
        assertEquals(0, session.reject("open1").reject("open2").choose("manual", "casual").reject("manual").leftCount)
    }

    @Test
    fun reviewFinishesOnlyAfterTheScanAndWhenEveryFoundCardIsDecided() {
        val decided = TagReviewSession().withCard(card("a", ReviewChange.Add("casual"))).reject("a")
        val open = decided.withCard(card("b", ReviewChange.Add("weekend")))

        assertTrue(shouldFinishTagReview(decided, isScanning = false, pickerOpen = false, discardDialogOpen = false, finished = false, revertsInFlight = false))
        assertFalse(shouldFinishTagReview(decided, isScanning = true, pickerOpen = false, discardDialogOpen = false, finished = false, revertsInFlight = false))
        assertFalse(shouldFinishTagReview(open, isScanning = false, pickerOpen = false, discardDialogOpen = false, finished = false, revertsInFlight = false))
    }

    private fun card(
        garmentId: String,
        vararg changes: ReviewChange,
        needsChoice: Boolean = false,
        offered: Set<String> = emptySet(),
    ) = ReviewCardState(
        garmentId = garmentId,
        changes = ReviewChanges(changes = changes.toList(), needsChoice = needsChoice),
        liveGroupTagIds = emptySet(),
        offeredTagIds = offered,
    )

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
