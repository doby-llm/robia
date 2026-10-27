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
        assertFalse(showsBulkBar(TagReviewSession()))
        assertTrue(showsBulkBar(scenario()))
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
            "<string name=\"review_apply_remaining\">Apply all remaining (%1\$d)</string>",
            "<string name=\"review_bulk_title\">Apply the remaining %1\$d?</string>",
            "<string name=\"review_apply\">Apply</string>",
            "<item quantity=\"one\">Robia will add %1\$d tag and remove %2\$d on %3\$d garments. You can undo it right after.</item>",
            "<item quantity=\"other\">Robia will add %1\$d tags and remove %2\$d on %3\$d garments. You can undo it right after.</item>",
            "<item quantity=\"one\">Robia will add %1\$d tag to %2\$d garments. You can undo it right after.</item>",
            "<item quantity=\"other\">Robia will add %1\$d tags to %2\$d garments. You can undo it right after.</item>",
            "<item quantity=\"one\">1 garment updated</item>",
            "<item quantity=\"other\">%1\$d garments updated</item>",
            "<string name=\"tag_review_accept_all\">Accept all (%1\$d)</string>",
        ).forEach { line -> assertTrue(line, en.contains(line)) }
        listOf(
            "<string name=\"review_apply_remaining\">Aplicar las %1\$d restantes</string>",
            "<string name=\"review_bulk_title\">¿Aplicar las %1\$d restantes?</string>",
            "<string name=\"review_apply\">Aplicar</string>",
            "<item quantity=\"one\">Robia añadirá %1\$d etiqueta y quitará %2\$d en %3\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"other\">Robia añadirá %1\$d etiquetas y quitará %2\$d en %3\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"one\">Robia añadirá %1\$d etiqueta a %2\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"other\">Robia añadirá %1\$d etiquetas a %2\$d prendas. Podrás deshacerlo justo después.</item>",
            "<item quantity=\"one\">1 prenda actualizada</item>",
            "<item quantity=\"other\">%1\$d prendas actualizadas</item>",
        ).forEach { line -> assertTrue(line, es.contains(line)) }
        listOf(
            "<string name=\"review_apply_remaining\">Alle restlichen %1\$d übernehmen</string>",
            "<string name=\"review_bulk_title\">Die restlichen %1\$d übernehmen?</string>",
            "<string name=\"review_apply\">Übernehmen</string>",
            "<item quantity=\"one\">Robia fügt bei %3\$d Kleidungsstücken %1\$d Tag hinzu und entfernt %2\$d. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"other\">Robia fügt bei %3\$d Kleidungsstücken %1\$d Tags hinzu und entfernt %2\$d. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"one\">Robia fügt %2\$d Kleidungsstücken %1\$d Tag hinzu. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"other\">Robia fügt %2\$d Kleidungsstücken %1\$d Tags hinzu. Du kannst das gleich danach rückgängig machen.</item>",
            "<item quantity=\"one\">1 Kleidungsstück aktualisiert</item>",
            "<item quantity=\"other\">%1\$d Kleidungsstücke aktualisiert</item>",
        ).forEach { line -> assertTrue(line, de.contains(line)) }
    }

    // V-REV-BULK-2: one batch write of the write cards only; a failed batch and a nothing-changed result; one Undo of the whole batch.

    @Test
    fun confirmSendsOneBatchOfTheWriteCardsOnlyAndKeepsTheLeftOutOnesWithNoWrite() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        assertEquals(listOf("a", "b", "p", "c"), batch.batchIds)
        assertEquals(listOf(deltaA, deltaB, deltaP), batch.deltas)

        val calls = mutableListOf<List<TagDelta>>()
        val outcome = runBlocking { recordingApply(calls)(batch.deltas) }
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

    @Test
    fun aFailedBatchReturnsEveryWriteCardToPendingWithItsChips() {
        val (decided, batch) = scenario().startBulk(existingTagIds)
        val failed = decided.afterWrite(batch.writeIds, Result.failure(IllegalStateException("disk full")))

        assertEquals(listOf("a", "b", "p", "d"), failed.pending.map(ReviewCardState::garmentId))
        assertEquals(setOf(weekendKey), failed.cards.first { card -> card.garmentId == "p" }.skipped)
        assertEquals(ReviewDecision.Rejected(), failed.cards.first { card -> card.garmentId == "c" }.decision)
        assertEquals(0, failed.tallies().garmentsUpdated)
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
