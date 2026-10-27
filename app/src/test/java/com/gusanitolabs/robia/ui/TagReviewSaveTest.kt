package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewButton
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TagReviewSaveTest {
    private val evening = GarmentTag(id = "evening", categoryId = "occasion", name = "Evening", sortOrder = 0)
    private val casual = GarmentTag(id = "casual", categoryId = "occasion", name = "Casual", sortOrder = 1)
    private val weekend = GarmentTag(id = "weekend", categoryId = "occasion", name = "Weekend", sortOrder = 2)

    // V-REV-SAVE-1: the decision flow over the session, as the screen drives it.

    @Test
    fun applyWritesOneDeltaOfTheActiveChipsAndASecondTapDoesNothing() {
        val (applied, delta) = session(card("g", ReviewChange.Add("casual"))).apply("g", existingTagIds = setOf("casual"))

        assertEquals(TagDelta("g", remove = emptySet(), add = setOf("casual")), delta)
        assertTrue(applied.cards.single().decision is ReviewDecision.Applied)
        val (again, secondDelta) = applied.apply("g", existingTagIds = setOf("casual"))
        assertNull(secondDelta)
        assertEquals(applied, again)
    }

    @Test
    fun rejectKeepAsIsAndGotItWriteNothing() {
        val rejected = session(card("r", ReviewChange.Add("casual"))).reject("r")
        assertTrue(rejected.cards.single().decision is ReviewDecision.Rejected)

        val (keptAsIs, keepDelta) = session(card("k", ReviewChange.Add("casual")).copy(skipped = setOf("add:casual")))
            .apply("k", existingTagIds = setOf("casual"))
        assertNull(keepDelta)
        assertTrue(keptAsIs.cards.single().decision is ReviewDecision.Rejected)

        // Got it: the only change is a locked removal, so the card is decided with nothing to write.
        val (gotIt, gotItDelta) = session(card("n", ReviewChange.Remove("evening", locked = true))).apply("n", existingTagIds = emptySet())
        assertNull(gotItDelta)
        assertTrue(gotIt.cards.single().decision is ReviewDecision.Applied)
    }

    @Test
    fun aWriteThatFailsReturnsTheCardToPendingWithItsChips() {
        val (applying, _) = session(card("g", ReviewChange.Add("casual"))).apply("g", existingTagIds = setOf("casual"))
        assertTrue(writesInFlight(applying))

        val failed = applying.afterWrite(listOf("g"), Result.failure(IllegalStateException("disk full")))

        assertEquals(1, failed.leftCount)
        assertFalse(writesInFlight(failed))
        assertEquals(setOf("add:casual"), failed.cards.single().changes.changes.map { change -> change.key }.toSet())
    }

    @Test
    fun aSuccessfulWriteDecidesEveryResultsCard() {
        val (applying, _) = session(card("a", ReviewChange.Add("casual")), card("b", ReviewChange.Add("weekend")))
            .apply("a", existingTagIds = setOf("casual", "weekend"))
        val (both, _) = applying.apply("b", existingTagIds = setOf("casual", "weekend"))

        val settled = both.afterWrite(
            listOf("a", "b"),
            Result.success(
                listOf(
                    ApplyResult(garment("a"), AppliedDelta("a", emptySet(), setOf("casual"))),
                    ApplyResult(null, AppliedDelta("b", emptySet(), emptySet())),
                ),
            ),
        )

        assertTrue(settled.cards.first { card -> card.garmentId == "a" }.decision is ReviewDecision.Applied)
        assertTrue(settled.cards.first { card -> card.garmentId == "b" }.decision is ReviewDecision.Rejected)
        assertFalse(writesInFlight(settled))
    }

    @Test
    fun aChooseManuallyCardWithoutAChoiceIsNeverWrittenAndKeepsTheReviewOpen() {
        val manual = session(card("m", needsChoice = true, offered = setOf("casual")))
        val pending = manual.cards.single()

        assertFalse(canDecideNow(pending, chosenTagId = null))
        assertEquals(1, manual.leftCount)
        assertFalse(finishes(manual, isScanning = false))

        val chosen = manual.choose("m", "casual")
        assertTrue(canDecideNow(chosen.cards.single(), chosenTagId = "casual"))
        val (_, delta) = chosen.apply("m", existingTagIds = setOf("casual"))
        assertEquals(setOf("casual"), delta?.add)
    }

    @Test
    fun aCardThatComesBackAfterAFailedWriteGetsANewKey() {
        assertTrue(reviewCardKey("g", returns = 0) != reviewCardKey("g", returns = 1))
    }

    // V-REV-SAVE-2: only decided garments are written, and leaving keeps exactly those.

    @Test
    fun aRejectedGarmentIsNeverInAWriteAndOnlyDecidedOnesAre() {
        val start = session(
            card("a", ReviewChange.Add("casual")),
            card("b", ReviewChange.Add("weekend")),
            card("c", ReviewChange.Add("evening")),
        )
        val (afterA, deltaA) = start.apply("a", existingTagIds = setOf("casual", "weekend", "evening"))
        val rejectedB = afterA.reject("b")
        val (afterC, deltaC) = rejectedB.apply("c", existingTagIds = setOf("casual", "weekend", "evening"))

        val written = listOfNotNull(deltaA, deltaC).map(TagDelta::garmentId)
        assertEquals(listOf("a", "c"), written)
        assertTrue(afterC.cards.first { card -> card.garmentId == "b" }.decision is ReviewDecision.Rejected)
    }

    @Test
    fun leavingAfterTwoAppliesHasWrittenExactlyThoseTwo() {
        val (applyingA, _) = session(
            card("a", ReviewChange.Add("casual")),
            card("b", ReviewChange.Add("weekend")),
            card("c", ReviewChange.Add("evening")),
        ).apply("a", existingTagIds = setOf("casual", "weekend", "evening"))
        val (applyingB, _) = applyingA.apply("b", existingTagIds = setOf("casual", "weekend", "evening"))
        val reviewed = applyingB
            .appliedResult("a", ApplyResult(garment("a"), AppliedDelta("a", emptySet(), setOf("casual"))))
            .appliedResult("b", ApplyResult(garment("b"), AppliedDelta("b", emptySet(), setOf("weekend"))))

        assertEquals(2, writtenGarmentCount(reviewed))
        assertEquals(LeaveKind.Leave, tagReviewLeaveKind(writtenGarmentCount(reviewed)))
    }

    @Test
    fun aWriteInFlightOrWithNothingChangedIsNotCountedAsWritten() {
        val (inFlight, _) = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds = setOf("casual"))
        assertEquals(0, writtenGarmentCount(inFlight))

        val nothingChanged = inFlight.appliedResult("a", ApplyResult(null, AppliedDelta("a", emptySet(), emptySet())))
        assertEquals(0, writtenGarmentCount(nothingChanged))
        assertEquals(LeaveKind.Discard, tagReviewLeaveKind(writtenGarmentCount(nothingChanged)))
    }

    @Test
    fun aRejectedOnlyOrGotItOnlyReviewIsDiscardWhenLeft() {
        val rejectedOnly = session(card("r", ReviewChange.Add("casual"))).reject("r")
        val gotItOnly = session(card("n", ReviewChange.Remove("evening", locked = true))).apply("n", emptySet()).first

        assertEquals(LeaveKind.Discard, tagReviewLeaveKind(writtenGarmentCount(rejectedOnly)))
        assertEquals(LeaveKind.Discard, tagReviewLeaveKind(writtenGarmentCount(gotItOnly)))
    }

    // V-REV-SAVE-1 (finish): the review ends only after the scan and when every found card is decided.

    @Test
    fun theReviewFinishesOnlyAfterTheScanAndWhenEveryFoundCardIsDecided() {
        val pending = session(card("a", ReviewChange.Add("casual")), card("b", ReviewChange.Add("weekend")))
        val decided = pending.reject("a").reject("b")

        assertFalse(finishes(pending, isScanning = false))
        assertFalse(finishes(decided, isScanning = true))
        assertTrue(finishes(decided, isScanning = false))
        assertFalse(finishes(TagReviewSession(), isScanning = false))
        assertFalse(shouldFinishTagReview(decided, isScanning = false, pickerOpen = true, discardDialogOpen = false, finished = false))
        assertFalse(shouldFinishTagReview(decided, isScanning = false, pickerOpen = false, discardDialogOpen = true, finished = false))
        assertFalse(shouldFinishTagReview(decided, isScanning = false, pickerOpen = false, discardDialogOpen = false, finished = true))
    }

    @Test
    fun theReviewDoesNotFinishWhileAWriteIsInFlight() {
        val (inFlight, _) = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds = setOf("casual"))

        assertFalse(finishes(inFlight, isScanning = false))
    }

    // V-REV-SAVE-3: the serial write, the live reads and the one-batch rule, on fakes.

    @Test
    fun twoAppliesAndARevertOnTheSameGarmentRunInOrderInOneChain() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val store = LiveStore(listOf(garment("g", tags = listOf(evening))), tags = listOf(evening, casual, weekend))
        val writes = ReviewWrites(scope) { _ -> }
        try {
            val gate = CompletableDeferred<Unit>()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                writes.launchAwaited {
                    gate.await()
                    store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual")))))
                }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                writes.launchAwaited { store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("weekend"))))) }
            }
            val revert = async(start = CoroutineStart.UNDISPATCHED) {
                writes.launchAwaited {
                    store.write(listOf(TagReviewChange.Revert(AppliedDelta("g", emptySet(), setOf("casual")))))
                }
            }
            // Nothing after the gated write has run yet: the chain holds them back.
            assertEquals(emptyList<String>(), store.order)
            gate.complete(Unit)

            withTimeout(2_000) { listOf(first, second, revert).awaitAll() }

            // The revert ran after the apply it reverses: casual is gone, weekend stays.
            assertEquals(setOf("evening", "weekend"), store.tagIdsOf("g"))
            assertEquals(3, store.order.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theApplyReadsTheLiveGarmentSoATagEditedAfterTheSnapshotIsKept() {
        // The snapshot showed no tags; the live row has evening (edited since). Only casual is added.
        val store = LiveStore(listOf(garment("g", tags = listOf(evening))), tags = listOf(evening, casual))

        runBlocking { store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual"))))) }

        assertEquals(setOf("evening", "casual"), store.tagIdsOf("g"))
    }

    @Test
    fun aTagThatWasMergedAwayIsNeverAdded() {
        // weekend is not in the live tag table any more: a delta that names it adds nothing.
        val store = LiveStore(listOf(garment("g", tags = listOf(evening))), tags = listOf(evening, casual))

        runBlocking {
            store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("weekend", "casual")))))
        }

        assertEquals(setOf("evening", "casual"), store.tagIdsOf("g"))
    }

    @Test
    fun anArchivedOrMissingGarmentIsSkippedAndNeverRecreated() {
        val store = LiveStore(listOf(garment("archived", tags = listOf(evening), isArchived = true)), tags = listOf(evening, casual))

        val (results, operations) = runBlocking {
            store.write(
                listOf(
                    TagReviewChange.Apply(TagDelta("archived", emptySet(), setOf("casual"))),
                    TagReviewChange.Apply(TagDelta("missing", emptySet(), setOf("casual"))),
                ),
            )
        }

        assertEquals(listOf(null, null), results.map(ApplyResult::item))
        assertEquals(emptyList<String>(), store.upsertedIds)
        assertEquals(emptyList<WardrobeSyncOperation>(), operations)
    }

    @Test
    fun oneBatchIsOneUpsertAndEachWrittenGarmentGetsOneUpsertOperation() {
        val store = LiveStore(
            listOf(garment("g1", tags = listOf(evening)), garment("g2", tags = listOf(evening))),
            tags = listOf(evening, casual),
        )

        val (results, operations) = runBlocking {
            store.write(
                listOf(
                    TagReviewChange.Apply(TagDelta("g1", emptySet(), setOf("casual"))),
                    TagReviewChange.Apply(TagDelta("g2", emptySet(), setOf("casual"))),
                ),
            )
        }

        assertEquals(1, store.upserts.size)
        assertEquals(listOf("g1", "g2"), store.upsertedIds)
        assertEquals(listOf("g1", "g2"), results.mapNotNull { result -> result.item?.id })
        assertEquals(listOf("g1", "g2"), operations.map { operation -> (operation as WardrobeSyncOperation.UpsertItem).itemId })
    }

    @Test
    fun twoChangesToOneGarmentInOneBatchSeeEachOther() {
        val store = LiveStore(listOf(garment("g", tags = listOf(evening))), tags = listOf(evening, casual, weekend))

        runBlocking {
            store.write(
                listOf(
                    TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual"))),
                    TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("weekend"))),
                ),
            )
        }

        assertEquals(setOf("evening", "casual", "weekend"), store.tagIdsOf("g"))
        assertEquals(1, store.upserts.size)
    }

    @Test
    fun anApplyThatChangesNothingWritesNothing() {
        val store = LiveStore(listOf(garment("g", tags = listOf(casual))), tags = listOf(evening, casual))

        val (results, operations) = runBlocking {
            store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual")))))
        }

        assertNull(results.single().item)
        assertEquals(emptyList<String>(), store.upsertedIds)
        assertEquals(emptyList<WardrobeSyncOperation>(), operations)
    }

    @Test
    fun aFailedWriteIsAFailedResultAndTheNextWriteStillRuns() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val store = LiveStore(listOf(garment("g", tags = listOf(evening))), tags = listOf(evening, casual))
        val writes = ReviewWrites(scope) { _ -> }
        try {
            val failed = writes.launchAwaited {
                writeTagReviewChanges(
                    categoryId = "occasion",
                    changes = listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual")))),
                    readGarment = { id -> store.garments[id] },
                    readTags = { store.tags },
                    upsert = { throw IllegalStateException("disk full") },
                    now = 100L,
                )
            }
            val next = writes.launchAwaited {
                store.write(listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual")))))
            }

            assertTrue(failed.isFailure)
            assertTrue(next.isSuccess)
            assertEquals(setOf("evening", "casual"), store.tagIdsOf("g"))
        } finally {
            scope.cancel()
        }
    }

    // V-REV-SAVE-4 (B1): Done ends the review and writes nothing new; Back keeps the discard question.

    @Test
    fun doneOnAnEmptyScanFinishesAndBackStillAsksToDiscard() {
        val empty = TagReviewSession()

        assertTrue(doneEnabled(isScanning = false, session = empty))
        assertTrue(doneFinishes(finished = false, session = empty))
        assertFalse(doneFinishes(finished = true, session = empty))
        assertEquals(LeaveKind.Discard, tagReviewLeaveKind(empty.appliedGarmentCount))
    }

    @Test
    fun doneWithOnlyPendingCardsFinishesAndWritesNothingForThem() {
        val pending = session(
            card("a", ReviewChange.Add("casual")),
            card("b", needsChoice = true, offered = setOf("weekend")),
        )

        assertTrue(doneFinishes(finished = false, session = pending))
        assertEquals(2, pending.leftCount)
        assertEquals(0, pending.appliedGarmentCount)
        assertFalse(writesInFlight(pending))
    }

    @Test
    fun doneIsNotAvailableWhileAWriteIsInFlightOrTheScanRuns() {
        val (inFlight, _) = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds = setOf("casual"))

        assertFalse(doneEnabled(isScanning = true, session = TagReviewSession()))
        assertFalse(doneEnabled(isScanning = false, session = inFlight))
        assertFalse(doneFinishes(finished = false, session = inFlight))
    }

    // V-REV-SAVE-4 (B2): a write still in flight counts for Back, so its undo never runs under a kept tag change.

    @Test
    fun aWriteInFlightMakesBackLeaveButIsNotInTheWrittenText() {
        val (inFlight, _) = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds = setOf("casual"))

        assertEquals(0, writtenGarmentCount(inFlight))
        assertEquals(1, inFlight.appliedGarmentCount)
        assertEquals(LeaveKind.Leave, tagReviewLeaveKind(inFlight.appliedGarmentCount))
    }

    // V-REV-SAVE-4 (B3): a chooser card with no tag keeps its Keep as is disabled, as it does nothing.

    @Test
    fun aChooseManuallyKeepAsIsIsDisabledUntilATagIsChosen() {
        val manual = session(card("m", needsChoice = true, offered = setOf("casual"))).cards.single()

        assertEquals(ReviewButton.KeepAsIs, buttonOf(manual, chosenTagId = null))
        assertFalse(canDecideNow(manual, chosenTagId = null))
        assertTrue(canDecideNow(manual, chosenTagId = "casual"))
    }

    // V-REV-SAVE-5 (T1): every changed garment is enqueued once, an unchanged one and a failed write are not.

    @Test
    fun aWriteEnqueuesOneUpsertPerChangedGarmentAndNothingForAnUnchangedOneOrAFailedWrite() = runBlocking {
        // Unconfined: the enqueue runs inside the write, so the recorded operations are complete once the write returns.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val enqueued = CopyOnWriteArrayList<WardrobeSyncOperation>()
        val writes = ReviewWrites(scope) { operation -> enqueued.add(operation) }
        val store = LiveStore(
            listOf(
                garment("changed", tags = listOf(evening)),
                garment("unchanged", tags = listOf(casual)),
                garment("failing"),
            ),
            tags = listOf(evening, casual),
        )
        try {
            writes.launchAwaited {
                store.write(
                    listOf(
                        TagReviewChange.Apply(TagDelta("changed", emptySet(), setOf("casual"))),
                        TagReviewChange.Apply(TagDelta("unchanged", emptySet(), setOf("casual"))),
                    ),
                )
            }
            withTimeout(2_000) { while (enqueued.isEmpty()) delay(1) }
            assertEquals(listOf("changed"), upsertedIdsOf(enqueued))

            val failed = writes.launchAwaited {
                writeTagReviewChanges(
                    categoryId = "occasion",
                    changes = listOf(TagReviewChange.Apply(TagDelta("failing", emptySet(), setOf("casual")))),
                    readGarment = { id -> store.garments[id] },
                    readTags = { store.tags },
                    upsert = { throw IllegalStateException("disk full") },
                    now = 100L,
                )
            }

            assertTrue(failed.isFailure)
            assertEquals(listOf("changed"), upsertedIdsOf(enqueued))
        } finally {
            scope.cancel()
        }
    }

    // V-REV-SAVE-5 (T2): a cancelled write is a cancellation for its caller, never a failed Result.

    @Test
    fun aCancelledWaitingWriteIsCancelledForItsCallerAndNotAFailedResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val writes = ReviewWrites(scope) { _ -> }
        try {
            val gate = CompletableDeferred<Unit>()
            val caller = async(start = CoroutineStart.UNDISPATCHED) {
                writes.launchAwaited {
                    gate.await()
                    "never" to emptyList<WardrobeSyncOperation>()
                }
            }

            // The write is waiting on the gate; cancelling the gate cancels the write while the caller waits for it.
            gate.cancel(CancellationException("cancelled while waiting"))

            val outcome = runCatching { withTimeout(2_000) { caller.await() } }
            assertTrue(outcome.exceptionOrNull() is CancellationException)
        } finally {
            scope.cancel()
        }
    }

    // V-REV-SAVE-5 (T3): the row is built from the live garment, not from the snapshot the review opened with.

    @Test
    fun aWriteBuildsTheRowFromTheLiveGarmentNotTheSnapshotTheReviewOpenedWith() {
        // The review opened with the garment untagged; the database has evening since. Only casual is added to the live row.
        val snapshot = garment("g", tags = emptyList())
        val live = garment("g", tags = listOf(evening))
        val reads = CopyOnWriteArrayList<String>()
        val upserted = CopyOnWriteArrayList<ClothingItem>()

        val (results, _) = runBlocking {
            writeTagReviewChanges(
                categoryId = "occasion",
                changes = listOf(TagReviewChange.Apply(TagDelta("g", emptySet(), setOf("casual")))),
                readGarment = { id -> reads.add(id); live },
                readTags = { listOf(evening, casual) },
                upsert = { items -> upserted.addAll(items) },
                now = 100L,
            )
        }

        assertEquals(listOf("g"), reads.toList())
        assertTrue(upserted.single().tags != snapshot.tags)
        assertEquals(setOf("evening", "casual"), upserted.single().tags.map(GarmentTag::id).toSet())
        assertEquals(setOf("evening", "casual"), results.single().item?.tags?.map(GarmentTag::id)?.toSet())
    }

    private fun upsertedIdsOf(operations: List<WardrobeSyncOperation>): List<String> =
        operations.filterIsInstance<WardrobeSyncOperation.UpsertItem>().map(WardrobeSyncOperation.UpsertItem::itemId)

    private fun finishes(session: TagReviewSession, isScanning: Boolean): Boolean =
        shouldFinishTagReview(session, isScanning, pickerOpen = false, discardDialogOpen = false, finished = false)

    private fun session(vararg cards: ReviewCardState): TagReviewSession =
        cards.fold(TagReviewSession()) { session, card -> session.withCard(card) }

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

    private fun garment(
        id: String,
        tags: List<GarmentTag> = emptyList(),
        isArchived: Boolean = false,
    ) = ClothingItem(
        id = id,
        name = id,
        tags = tags,
        isArchived = isArchived,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    /** Live garments and tags for [writeTagReviewChanges]; every upsert is recorded, and every write is recorded in [order]. */
    private inner class LiveStore(garments: List<ClothingItem>, val tags: List<GarmentTag>) {
        val garments = garments.associateBy(ClothingItem::id).toMutableMap()
        val upserts = CopyOnWriteArrayList<List<ClothingItem>>()
        val order = CopyOnWriteArrayList<String>()

        val upsertedIds: List<String>
            get() = upserts.flatten().map(ClothingItem::id)

        fun tagIdsOf(garmentId: String): Set<String> = garments.getValue(garmentId).tags.map(GarmentTag::id).toSet()

        suspend fun write(changes: List<TagReviewChange>): Pair<List<ApplyResult>, List<WardrobeSyncOperation>> {
            order.add(changes.joinToString { change -> change.garmentId })
            return writeTagReviewChanges(
                categoryId = "occasion",
                changes = changes,
                readGarment = { id -> garments[id] },
                readTags = { tags },
                upsert = { items ->
                    upserts.add(items)
                    items.forEach { item -> garments[item.id] = item }
                },
                now = 100L,
            )
        }
    }
}
