package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REV-BULK: Apply all remaining over the session: the bar, the confirmation counts, one batch write and one Undo. */
class TagReviewBulkTest {
    private val existingTagIds = setOf("evening", "casual", "weekend")

    /**
     * a: add (planned write); b: swap (one garment, one removal, one addition); p: two adds, "weekend" left out (planned write);
     * c: its only add left out (kept, no write); d: needs a choice and has none (stays Pending); e: a locked removal only (not offered).
     */
    private fun scenario(): TagReviewSession = session(
        card("a", ReviewChange.Add("casual")),
        card("b", ReviewChange.Swap(fromId = "evening", toId = "casual", fromLocked = false)),
        card("p", ReviewChange.Add("casual"), ReviewChange.Add("weekend")).copy(skipped = setOf(weekendKey)),
        card("c", ReviewChange.Add("weekend")).copy(skipped = setOf(weekendKey)),
        ReviewCardState(
            garmentId = "d",
            changes = ReviewChanges(changes = emptyList(), needsChoice = true),
            liveGroupTagIds = emptySet(),
            offeredTagIds = setOf("casual"),
        ),
        card("e", ReviewChange.Remove("evening", locked = true)),
    )

    private val weekendKey = ReviewChange.Add("weekend").key
    private val deltaA = TagDelta("a", remove = emptySet(), add = setOf("casual"))
    private val deltaB = TagDelta("b", remove = setOf("evening"), add = setOf("casual"))
    private val deltaP = TagDelta("p", remove = emptySet(), add = setOf("casual"))

    // V-REV-BULK-1: the bar count is the plan, the bar is off while scanning and at 0, and the confirmation states the plan.

    @Test
    fun theBarCountIsTheBulkPlanAndTheBarIsDisabledWhileScanningAndAtZero() {
        val start = scenario()
        val garments = start.bulkPlan(existingTagIds).garments
        assertEquals(3, garments)
        assertEquals(listOf("a", "b", "p"), start.bulkPlan(existingTagIds).deltas.map(TagDelta::garmentId))

        assertTrue(bulkBarEnabled(isScanning = false, session = start, revertsInFlight = false, garments = garments))
        assertFalse(bulkBarEnabled(isScanning = true, session = start, revertsInFlight = false, garments = garments))
        assertFalse(bulkBarEnabled(isScanning = false, session = start, revertsInFlight = false, garments = 0))
        assertFalse(bulkBarEnabled(isScanning = false, session = start, revertsInFlight = true, garments = garments))

        val (inFlight, _) = start.startBulk(existingTagIds)
        assertFalse(bulkBarEnabled(isScanning = false, session = inFlight, revertsInFlight = false, garments = 3))
    }

    @Test
    fun theBarAppliesTheRestOnlyWhileThereAreCardsAndStaysDoneWithout() {
        assertFalse(showsBulkBar(TagReviewSession(), isScanning = false, revertsInFlight = false, garments = 0))
        assertTrue(showsBulkBar(scenario(), isScanning = false, revertsInFlight = false, garments = 3))
    }

    // V-REV-BULK-3 (B1): a chooser left with nothing to write (deleting the last category) or kept cards must not trap the review.

    @Test
    fun aChooserWithNothingToWriteShowsDoneNotADisabledBulkBar() {
        val chooser = ReviewCardState(
            garmentId = "x",
            changes = ReviewChanges(changes = emptyList(), needsChoice = true),
            liveGroupTagIds = emptySet(),
            offeredTagIds = emptySet(),
        )
        val chooserOnly = session(chooser)
        assertEquals(0, chooserOnly.bulkPlan(existingTagIds).garments)
        assertFalse(showsBulkBar(chooserOnly, isScanning = false, revertsInFlight = false, garments = 0))
        assertTrue(doneEnabled(isScanning = false, session = chooserOnly))

        // N > 0 shows the bulk bar; a scan, or a write or revert in flight, shows it disabled rather than Done.
        assertTrue(showsBulkBar(chooserOnly, isScanning = false, revertsInFlight = false, garments = 2))
        assertTrue(showsBulkBar(chooserOnly, isScanning = true, revertsInFlight = false, garments = 0))
        assertTrue(showsBulkBar(chooserOnly, isScanning = false, revertsInFlight = true, garments = 0))
        val (inFlight, _) = scenario().startBulk(existingTagIds)
        assertEquals(0, inFlight.bulkPlan(existingTagIds).garments)
        assertTrue(showsBulkBar(inFlight, isScanning = false, revertsInFlight = false, garments = 0))
        assertFalse(bulkBarEnabled(isScanning = false, session = inFlight, revertsInFlight = false, garments = 0))
    }

    @Test
    fun theConfirmationCountsAreThePlanAndTheAddsOnlyTextIsForNoRemovals() {
        val plan = scenario().bulkPlan(existingTagIds)
        assertEquals(3, plan.tagsAdded)
        assertEquals(1, plan.tagsRemoved)
        assertEquals(3, plan.garments)
        assertFalse(bulkBodyAddsOnly(plan))

        val addsOnly = session(card("a", ReviewChange.Add("casual"))).bulkPlan(existingTagIds)
        assertEquals(1, addsOnly.tagsAdded)
        assertEquals(0, addsOnly.tagsRemoved)
        assertEquals(1, addsOnly.garments)
        assertTrue(bulkBodyAddsOnly(addsOnly))
    }

    @Test
    fun cancelChangesNothing() {
        val start = scenario()
        // The confirmation only reads the plan; Cancel never calls startBulk.
        start.bulkPlan(existingTagIds)
        assertEquals(5, start.leftCount)
        assertEquals(3, start.bulkPlan(existingTagIds).garments)
    }

    @Test
    fun theBulkStringsExistInEveryLocaleWithTheBriefsWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        listOf(
            "<plurals name=\"review_apply_remaining\">",
            "<item quantity=\"one\">Apply the remaining garment</item>",
            "<item quantity=\"other\">Apply all remaining (%1\$d)</item>",
            "<plurals name=\"review_bulk_title\">",
            "<item quantity=\"one\">Apply the remaining garment?</item>",
            "<item quantity=\"other\">Apply the remaining %1\$d?</item>",
            "<plurals name=\"review_bulk_tags\">",
            "<item quantity=\"one\">%1\$d tag</item>",
            "<item quantity=\"other\">%1\$d tags</item>",
            "<plurals name=\"review_bulk_body\">",
            "<item quantity=\"one\">Robia will add %1\$s and remove %2\$d on 1 garment. You can undo it right after.</item>",
            "<item quantity=\"other\">Robia will add %1\$s and remove %2\$d on %3\$d garments. You can undo it right after.</item>",
            "<plurals name=\"review_bulk_body_adds\">",
            "<item quantity=\"one\">Robia will add %1\$s to 1 garment. You can undo it right after.</item>",
            "<item quantity=\"other\">Robia will add %1\$s to %2\$d garments. You can undo it right after.</item>",
            "<item quantity=\"one\">1 garment updated</item>",
            "<item quantity=\"other\">%1\$d garments updated</item>",
            "<string name=\"review_apply\">Apply</string>",
            "<string name=\"tag_review_accept_all\">Accept all (%1\$d)</string>",
            "<string name=\"tag_review_save_accepted\">Save %1\$d accepted</string>",
        ).forEach { line -> assertTrue(line, en.contains(line)) }
        listOf(
            "<item quantity=\"one\">Aplicar la prenda restante</item>",
            "<item quantity=\"other\">Aplicar las %1\$d restantes</item>",
            "<item quantity=\"one\">¿Aplicar la prenda restante?</item>",
            "<item quantity=\"other\">¿Aplicar las %1\$d restantes?</item>",
            "<item quantity=\"one\">%1\$d etiqueta</item>",
            "<item quantity=\"other\">%1\$d etiquetas</item>",
            "<item quantity=\"one\">Robia añadirá %1\$s y quitará %2\$d en 1 prenda. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"other\">Robia añadirá %1\$s y quitará %2\$d en %3\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"one\">Robia añadirá %1\$s a 1 prenda. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"other\">Robia añadirá %1\$s a %2\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"one\">1 prenda actualizada</item>",
            "<item quantity=\"other\">%1\$d prendas actualizadas</item>",
            "<string name=\"review_apply\">Aplicar</string>",
            "<string name=\"tag_review_accept_all\">Aceptar todo (%1\$d)</string>",
            "<string name=\"tag_review_save_accepted\">Guardar %1\$d aceptados</string>",
        ).forEach { line -> assertTrue(line, es.contains(line)) }
        listOf(
            "<item quantity=\"one\">Das restliche Kleidungsstück übernehmen</item>",
            "<item quantity=\"other\">Alle restlichen %1\$d übernehmen</item>",
            "<item quantity=\"one\">Das restliche Kleidungsstück übernehmen?</item>",
            "<item quantity=\"other\">Die restlichen %1\$d übernehmen?</item>",
            "<item quantity=\"one\">%1\$d Tag</item>",
            "<item quantity=\"other\">%1\$d Tags</item>",
            "<item quantity=\"one\">Robia fügt bei 1 Kleidungsstück %1\$s hinzu und entfernt %2\$d. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"other\">Robia fügt bei %3\$d Kleidungsstücken %1\$s hinzu und entfernt %2\$d. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"one\">Robia fügt 1 Kleidungsstück %1\$s hinzu. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"other\">Robia fügt %2\$d Kleidungsstücke %1\$s hinzu. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"one\">1 Kleidungsstück aktualisiert</item>",
            "<item quantity=\"other\">%1\$d Kleidungsstücke aktualisiert</item>",
            "<string name=\"review_apply\">Übernehmen</string>",
            "<string name=\"tag_review_accept_all\">Alle akzeptieren (%1\$d)</string>",
            "<string name=\"tag_review_save_accepted\">%1\$d akzeptierte Änderungen speichern</string>",
        ).forEach { line -> assertTrue(line, de.contains(line)) }
    }

    // V-REV-BULK-2: one batch write of the write cards only; a failed batch and a nothing-changed result; one Undo of the whole batch.

    @Test
    fun confirmSendsOneBatchOfTheWriteCardsOnlyAndKeepsTheLeftOutOnesWithNoWrite() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        assertEquals(listOf("a", "b", "p", "c"), batch.batchIds)
        assertEquals(listOf(deltaA, deltaB, deltaP), batch.deltas)

        // T(1): the screen's write is writeBulkBatch, ONE onApply call with the batch's deltas.
        val calls = mutableListOf<List<TagDelta>>()
        val outcome = runBlocking { writeBulkBatch(batch, recordingApply(calls)) }
        assertEquals(listOf(listOf(deltaA, deltaB, deltaP)), calls)

        val after = decided.afterWrite(batch.writeIds, outcome)
        assertEquals(ReviewDecision.Rejected(), after.cards.first { card -> card.garmentId == "c" }.decision)
        assertEquals(listOf("d"), after.pending.map(ReviewCardState::garmentId))
        assertEquals(ReviewDecision.Pending, after.cards.first { card -> card.garmentId == "e" }.decision)
        val tallies = after.tallies()
        assertEquals(3, tallies.garmentsUpdated)
        assertEquals(3, tallies.tagsAdded)
        assertEquals(1, tallies.tagsRemoved)
        assertEquals(1, tallies.garmentsKept)
    }

    @Test
    fun aResultWithNoItemIsNothingAppliedAndIsNotCounted() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        val outcome = Result.success(
            listOf(
                ApplyResult(item = null, applied = AppliedDelta("a", emptySet(), emptySet())),
                landedResult(deltaB),
                landedResult(deltaP),
            ),
        )
        assertEquals(listOf("b", "p"), writtenGarmentIds(outcome))

        val after = decided.afterWrite(batch.writeIds, outcome)
        assertEquals(ReviewDecision.Rejected(nothingApplied = true), after.cards.first { card -> card.garmentId == "a" }.decision)
        assertEquals(2, after.tallies().garmentsUpdated)
    }

    // V-REV-BULK-4 (B2): a failed batch returns EVERY card of the batch (the writes and the kept ones) to Pending, with its chips.

    @Test
    fun aFailedBatchReturnsEveryCardOfTheBatchToPendingWithItsChips() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        val failed = decided.afterBulkWrite(batch, Result.failure(IllegalStateException("disk full")))

        assertEquals(listOf("a", "b", "p", "c", "d"), failed.pending.map(ReviewCardState::garmentId))
        assertEquals(setOf(weekendKey), failed.cards.first { card -> card.garmentId == "p" }.skipped)
        assertEquals(ReviewDecision.Pending, failed.cards.first { card -> card.garmentId == "c" }.decision)
        assertEquals(0, failed.tallies().garmentsUpdated)
        assertEquals(0, failed.tallies().garmentsKept)
    }

    @Test
    fun reviewAgainIsRefusedForAKeptCardWhileTheBatchIsInFlight() {
        val (inFlight, _) = scenario().startBulk(existingTagIds)
        // "c" is kept by the batch: Review again would be allowed on its own, and is refused while the batch is in flight.
        assertTrue(reviewAgainEnabled(inFlight, "c", emptyList(), bulkInFlight = false))
        assertFalse(reviewAgainEnabled(inFlight, "c", emptyList(), bulkInFlight = true))
        assertFalse(reviewAgainEnabled(inFlight, "a", emptyList(), bulkInFlight = true))
        assertFalse(reviewAgainEnabled(inFlight, "a", listOf("a"), bulkInFlight = false))
    }

    // T(2): the bulk snackbar counts the results with an item, not the batch.

    @Test
    fun theBulkSnackAnnouncesOnlyTheGarmentsWithAnItem() {
        val (_, batch) = scenario().startBulk(existingTagIds)
        val outcome = Result.success(
            listOf(
                ApplyResult(item = null, applied = AppliedDelta("a", emptySet(), emptySet())),
                landedResult(deltaB),
                landedResult(deltaP),
            ),
        )
        assertEquals(BulkUndo(garments = 2, batch = listOf("a", "b", "p", "c")), bulkUndoFor(batch, outcome))
        assertNull(bulkUndoFor(batch, Result.failure(IllegalStateException("disk full"))))
    }

    // T(3): the finish waits for a bulk revert, whose garments are in flight until its one write lands.

    @Test
    fun theFinishWaitsForABulkRevertInFlight() {
        // One garment, written and decided: the review is complete, so only the revert in flight keeps it open.
        val (decided, batch) = session(card("a", ReviewChange.Add("casual"))).startBulk(existingTagIds)
        val written = decided.afterBulkWrite(batch, Result.success(batch.deltas.map { delta -> landedResult(delta) }))
        val revert = written.bulkRevert(batch.batchIds, emptyList())
        val reverting = mutableListOf<String>()
        reverting.markBulkRevertInFlight(revert)
        assertEquals(listOf("a"), reverting)

        fun finishes(revertsInFlight: Boolean) = shouldFinishTagReview(
            session = written,
            isScanning = false,
            pickerOpen = false,
            discardDialogOpen = false,
            finished = false,
            revertsInFlight = revertsInFlight,
        )
        assertFalse(finishes(revertsInFlight = reverting.isNotEmpty()))
        assertTrue(finishes(revertsInFlight = false))
    }

    @Test
    fun undoRevertsTheWholeBatchInOneWriteWithTheDeltasTheWriteReturned() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        // The write found casual already on b, so its applied delta has no add: Undo reverses what was written, not what was intended.
        val results = batch.deltas.map { delta ->
            if (delta.garmentId == "b") {
                ApplyResult(item = garment("b"), applied = AppliedDelta("b", removed = setOf("evening"), added = emptySet()))
            } else {
                landedResult(delta)
            }
        }
        val written = decided.afterWrite(batch.writeIds, Result.success(results))

        val revert = written.bulkRevert(batch.batchIds, emptyList())
        assertEquals(listOf("c"), revert.localIds)
        assertEquals(listOf("a", "b", "p"), revert.writeIds)
        assertEquals(
            listOf(
                AppliedDelta("a", emptySet(), setOf("casual")),
                AppliedDelta("b", setOf("evening"), emptySet()),
                AppliedDelta("p", emptySet(), setOf("casual")),
            ),
            revert.deltas,
        )

        val reverts = mutableListOf<List<AppliedDelta>>()
        val revertOutcome = runBlocking { recordingRevert(reverts)(revert.deltas) }
        assertEquals(1, reverts.size)

        val landed = bulkRevertLanded(revert.writeIds, revertOutcome)
        assertEquals(listOf("a", "b", "p"), landed)
        val undone = landed.fold(revert.localIds.fold(written) { session, id -> session.revert(id).first }) { session, id ->
            session.revert(id).first
        }
        // Every garment of the batch is back in the list in scan order, with its chips.
        assertEquals(listOf("a", "b", "p", "c", "d"), undone.pending.map(ReviewCardState::garmentId))
        assertEquals(setOf(weekendKey), undone.cards.first { card -> card.garmentId == "p" }.skipped)
        assertEquals(0, undone.tallies().garmentsUpdated)
    }

    @Test
    fun aSecondUndoDoesNothingAndAnUndoOfAGarmentInFlightIsRefused() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        val written = decided.afterWrite(batch.writeIds, Result.success(batch.deltas.map { delta -> landedResult(delta) }))
        val revert = written.bulkRevert(batch.batchIds, emptyList())
        val undone = revert.localIds.fold(written) { session, id -> session.revert(id).first }
            .let { session -> revert.writeIds.fold(session) { current, id -> current.revert(id).first } }

        val again = undone.bulkRevert(batch.batchIds, emptyList())
        assertTrue(again.writeIds.isEmpty())
        assertTrue(again.localIds.isEmpty())

        // While the revert of "a" is in flight, only the other garments are sent.
        val inFlight = written.bulkRevert(batch.batchIds, listOf("a"))
        assertEquals(listOf("b", "p"), inFlight.writeIds)
    }

    @Test
    fun aRevertThatDidNotLandKeepsItsGarmentsDecided() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        val written = decided.afterWrite(batch.writeIds, Result.success(batch.deltas.map { delta -> landedResult(delta) }))
        val revert = written.bulkRevert(batch.batchIds, emptyList())

        assertEquals(emptyList<String>(), bulkRevertLanded(revert.writeIds, Result.failure(IllegalStateException("disk full"))))

        // A null item (an archived garment) does not land; the others do.
        val partial = Result.success(
            listOf(
                ApplyResult(item = null, applied = AppliedDelta("a", emptySet(), emptySet())),
                ApplyResult(item = garment("b"), applied = AppliedDelta("b", setOf("evening"), emptySet())),
                ApplyResult(item = garment("p"), applied = AppliedDelta("p", emptySet(), setOf("casual"))),
            ),
        )
        val landed = bulkRevertLanded(revert.writeIds, partial)
        assertEquals(listOf("b", "p"), landed)
        val afterPartial = landed.fold(written) { session, id -> session.revert(id).first }
        assertTrue(canUndoNow(afterPartial, "a", emptyList()))
        assertFalse(canUndoNow(afterPartial, "b", emptyList()))
    }

    @Test
    fun aSecondConfirmWhileTheBatchIsInFlightFindsNothingToWrite() {
        val (inFlight, _) = scenario().startBulk(existingTagIds)
        assertTrue(writesInFlight(inFlight))
        val (_, second) = inFlight.startBulk(existingTagIds)
        assertTrue(second.deltas.isEmpty())
    }

    private fun recordingApply(
        calls: MutableList<List<TagDelta>>,
    ): suspend (List<TagDelta>) -> Result<List<ApplyResult>> = { deltas ->
        calls.add(deltas)
        Result.success(deltas.map { delta -> landedResult(delta) })
    }

    private fun recordingRevert(
        calls: MutableList<List<AppliedDelta>>,
    ): suspend (List<AppliedDelta>) -> Result<List<ApplyResult>> = { deltas ->
        calls.add(deltas)
        Result.success(deltas.map { delta -> ApplyResult(item = garment(delta.garmentId), applied = delta) })
    }

    private fun landedResult(delta: TagDelta) = ApplyResult(
        item = garment(delta.garmentId),
        applied = AppliedDelta(delta.garmentId, removed = delta.remove, added = delta.add),
    )

    private fun session(vararg cards: ReviewCardState): TagReviewSession =
        cards.fold(TagReviewSession()) { session, card -> session.withCard(card) }

    private fun card(
        garmentId: String,
        vararg changes: ReviewChange,
    ) = ReviewCardState(
        garmentId = garmentId,
        changes = ReviewChanges(changes = changes.toList(), needsChoice = false),
        liveGroupTagIds = emptySet(),
        offeredTagIds = emptySet(),
    )

    private fun garment(id: String, tags: List<GarmentTag> = emptyList()) = ClothingItem(
        id = id,
        name = id,
        tags = tags,
        isArchived = false,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
