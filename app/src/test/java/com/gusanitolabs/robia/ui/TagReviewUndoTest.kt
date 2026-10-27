package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.AppliedDelta
import com.gusanitolabs.robia.suggestions.ApplyResult
import com.gusanitolabs.robia.suggestions.ReviewChange
import com.gusanitolabs.robia.suggestions.ReviewChanges
import com.gusanitolabs.robia.suggestions.TagDelta
import com.gusanitolabs.robia.suggestions.applyDelta
import com.gusanitolabs.robia.suggestions.revertDelta
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REV-UNDO: the undo snackbar, Undo and "Review again" over the session, and the screen's pure helpers. */
class TagReviewUndoTest {
    private val evening = GarmentTag(id = "evening", categoryId = "occasion", name = "Evening", sortOrder = 0)
    private val casual = GarmentTag(id = "casual", categoryId = "occasion", name = "Casual", sortOrder = 1)
    private val weekend = GarmentTag(id = "weekend", categoryId = "occasion", name = "Weekend", sortOrder = 2)
    private val tags = listOf(evening, casual, weekend)
    private val existingTagIds = setOf("evening", "casual", "weekend")

    // V-REV-UNDO-1: after a decision one snackbar says what happened; Undo is the latest decision's revert, refused in flight.

    @Test
    fun aDecisionHasOneSnackbarOutcomeAndGotItHasNone() {
        val (applying, _) = session(card("a", ReviewChange.Add("casual"))).apply("a", existingTagIds)
        assertEquals(ReviewOutcome.Changes(1), undoSnackOutcome(applying.cards.single()))

        val rejected = session(card("r", ReviewChange.Add("casual"))).reject("r")
        assertEquals(ReviewOutcome.KeptAsIs, undoSnackOutcome(rejected.cards.single()))

        // Got it: the only change is a locked removal, so nothing is written and the snackbar says nothing.
        val (gotIt, _) = session(card("n", ReviewChange.Remove("evening", locked = true))).apply("n", existingTagIds)
        assertNull(undoSnackOutcome(gotIt.cards.single()))
    }

    @Test
    fun aSwapCountsAsOneChangeAndAKeptWriteReadsAsKeptAsIs() {
        val swap = card("s", ReviewChange.Swap(fromId = "evening", toId = "casual", fromLocked = false))
        assertEquals(1, cardUnits(swap))
        assertEquals(2, cardUnits(card("t", ReviewChange.Add("casual"), ReviewChange.Add("weekend"))))

        // A write that found nothing to change comes back with no item: the card is kept as it was.
        val (applying, _) = session(card("g", ReviewChange.Add("casual"))).apply("g", existingTagIds)
        val nothingChanged = applying.appliedResult("g", ApplyResult(item = null, applied = AppliedDelta("g", emptySet(), emptySet())))
        assertEquals(ReviewOutcome.KeptAsIs, undoSnackOutcome(nothingChanged.cards.single()))
    }

    @Test
    fun undoOfAnAppliedGarmentWritesTheEffectiveDeltaOnceAndReturnsTheCardToItsPlace() {
        val start = session(
            card("a", ReviewChange.Add("casual"), ReviewChange.Add("weekend")).copy(skipped = setOf("add:weekend")),
            card("b", ReviewChange.Add("casual")),
            card("c", ReviewChange.Add("casual")),
        )
        val (applying, delta) = start.apply("a", existingTagIds)
        assertEquals(TagDelta("a", remove = emptySet(), add = setOf("casual")), delta)
        val landed = applying.appliedResult("a", ApplyResult(item = garment("a"), applied = AppliedDelta("a", emptySet(), setOf("casual"))))
        val write = RecordingRevert()

        val undone = undo(landed, "a", write)

        assertEquals(listOf(listOf(AppliedDelta("a", emptySet(), setOf("casual")))), write.calls)
        assertEquals(listOf("a", "b", "c"), undone.pending.map { card -> card.garmentId })
        assertEquals(setOf("add:weekend"), undone.cards.first { card -> card.garmentId == "a" }.skipped)
    }

    @Test
    fun undoOfARejectedGarmentWritesNothing() {
        val rejected = session(card("r", ReviewChange.Add("casual"))).reject("r")
        val write = RecordingRevert()

        val undone = undo(rejected, "r", write)

        assertTrue(write.calls.isEmpty())
        assertEquals(listOf("r"), undone.pending.map { card -> card.garmentId })
    }

    @Test
    fun undoIsRefusedWhileTheWriteIsInFlight() {
        val (applying, _) = session(card("g", ReviewChange.Add("casual"))).apply("g", existingTagIds)

        assertFalse(canUndoNow(applying, "g", emptyList()))
        assertEquals(applying, undo(applying, "g", RecordingRevert()))
        assertEquals(applying, applying.revert("g").first)
    }

    @Test
    fun aSecondUndoOfTheSameDecisionDoesNothing() {
        val landed = landedApply(session(card("g", ReviewChange.Add("casual"))), "g")
        val write = RecordingRevert()

        val once = undo(landed, "g", write)
        val twice = undo(once, "g", write)

        assertEquals(1, write.calls.size)
        assertFalse(canUndoNow(once, "g", emptyList()))
        assertEquals(once, twice)
    }

    @Test
    fun aFailedRevertKeepsTheGarmentDecidedAndInReviewed() {
        val landed = landedApply(session(card("g", ReviewChange.Add("casual"))), "g")
        val write = RecordingRevert(answer = { Result.failure<List<ApplyResult>>(IllegalStateException("disk full")) })

        val after = undo(landed, "g", write)

        assertEquals(1, write.calls.size)
        assertEquals(landed, after)
        assertEquals(listOf("g"), after.reviewed.map { card -> card.garmentId })
        assertTrue(canUndoNow(after, "g", emptyList()))
    }

    @Test
    fun aRevertWithNoItemIsAFailedRevertAndAFullResultIsNot() {
        val applied = AppliedDelta("g", emptySet(), setOf("casual"))

        assertFalse(revertLanded(Result.success(listOf(ApplyResult(item = null, applied = applied)))))
        assertFalse(revertLanded(Result.failure(IllegalStateException())))
        assertTrue(revertLanded(Result.success(listOf(ApplyResult(item = garment("g"), applied = applied)))))
    }

    // V-REV-UNDO-2: "Review again" is the same revert; apply, undo and apply writes the same delta once; the session keeps its order.

    @Test
    fun reviewAgainOnAKeptCardIsTheSameStepAndWritesNothing() {
        val rejected = session(card("r", ReviewChange.Add("casual"))).reject("r")

        assertTrue(canUndoNow(rejected, "r", emptyList()))
        val write = RecordingRevert()
        assertEquals(listOf("r"), undo(rejected, "r", write).pending.map { card -> card.garmentId })
        assertTrue(write.calls.isEmpty())
    }

    @Test
    fun applyUndoAndApplyAgainGivesTheSameDeltaAndTheRevertWritesOnce() {
        val start = session(card("g", ReviewChange.Add("casual")))
        val firstDelta = deltaOf(start, "g")
        val landed = landedApply(start, "g")
        val write = RecordingRevert()
        val undone = undo(landed, "g", write)

        val (applyingAgain, secondDelta) = undone.apply("g", existingTagIds)
        val relanded = applyingAgain.appliedResult("g", ApplyResult(item = garment("g"), applied = AppliedDelta("g", emptySet(), setOf("casual"))))

        assertEquals(firstDelta, secondDelta)
        assertEquals(listOf(listOf(AppliedDelta("g", emptySet(), setOf("casual")))), write.calls)
        assertEquals(1, relanded.appliedGarmentCount)
    }

    @Test
    fun applyUndoAndRejectLeavesTheGarmentTagsAsTheyWere() {
        val original = garment("g", tags = listOf(evening))
        var live: ClothingItem? = original
        val start = session(card("g", ReviewChange.Add("casual")))

        val (applying, delta) = start.apply("g", existingTagIds)
        val applied = applyDelta(live, delta!!, tags, "occasion", now = 1)
        live = applied.item
        val landed = applying.appliedResult("g", applied)
        val write = RecordingRevert(answer = { reverts ->
            val result = revertDelta(live, reverts.single(), tags, "occasion", now = 2)
            live = result.item
            Result.success(listOf(result))
        })
        val undone = undo(landed, "g", write)
        val rejected = undone.reject("g")

        assertEquals(original.tags, live?.tags)
        assertTrue(rejected.cards.single().decision is ReviewDecision.Rejected)
    }

    @Test
    fun theReviewedListIsMostRecentFirstAndItsCountMatchesTheDecisions() {
        val twoDecided = session(card("a", ReviewChange.Add("casual")), card("b", ReviewChange.Add("casual")), card("c", ReviewChange.Add("casual")))
            .reject("a")
            .reject("b")
        val reviewed = twoDecided.reviewed.map { card -> card.garmentId }

        assertEquals(listOf("b", "a"), reviewed)
        assertEquals(2, reviewed.size)
        val threeDecided = twoDecided.reject("c")
        assertEquals(listOf("c", "b", "a"), threeDecided.reviewed.map { card -> card.garmentId })
        assertEquals(3, threeDecided.reviewed.size)
    }

    // V-REV-UNDO-3: the strings of the brief exist in EN, ES and DE, with the plural forms of the applied count.

    @Test
    fun theUndoStringsExistInEveryLocaleWithTheBriefsWording() {
        val en = resource("app/src/main/res/values/strings.xml")
        val es = resource("app/src/main/res/values-es/strings.xml")
        val de = resource("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<item quantity=\"one\">1 change applied</item>"))
        assertTrue(en.contains("<item quantity=\"other\">%1\$d changes applied</item>"))
        assertTrue(es.contains("<item quantity=\"one\">1 cambio aplicado</item>"))
        assertTrue(es.contains("<item quantity=\"other\">%1\$d cambios aplicados</item>"))
        assertTrue(de.contains("<item quantity=\"one\">1 Änderung übernommen</item>"))
        assertTrue(de.contains("<item quantity=\"other\">%1\$d Änderungen übernommen</item>"))
        listOf(
            "<string name=\"review_snack_kept\">Kept as it was</string>",
            "<string name=\"review_section_reviewed\">Reviewed · %1\$d</string>",
            "<string name=\"review_reviewed_n\">%1\$d reviewed</string>",
            "<string name=\"review_show\">Show</string>",
            "<string name=\"review_hide\">Hide</string>",
            "<string name=\"review_again\">Review again</string>",
            "<string name=\"review_revert_failed\">Couldn’t undo. The garment stays as updated.</string>",
        ).forEach { line -> assertTrue(line, en.contains(line)) }
        listOf(
            "<string name=\"review_snack_kept\">Se queda como estaba</string>",
            "<string name=\"review_section_reviewed\">Revisadas · %1\$d</string>",
            "<string name=\"review_reviewed_n\">%1\$d revisadas</string>",
            "<string name=\"review_show\">Mostrar</string>",
            "<string name=\"review_hide\">Ocultar</string>",
            "<string name=\"review_again\">Revisar otra vez</string>",
            "<string name=\"review_revert_failed\">No se pudo deshacer. La prenda se queda actualizada.</string>",
        ).forEach { line -> assertTrue(line, es.contains(line)) }
        listOf(
            "<string name=\"review_snack_kept\">Bleibt unverändert</string>",
            "<string name=\"review_section_reviewed\">Geprüft · %1\$d</string>",
            "<string name=\"review_reviewed_n\">%1\$d geprüft</string>",
            "<string name=\"review_show\">Anzeigen</string>",
            "<string name=\"review_hide\">Ausblenden</string>",
            "<string name=\"review_again\">Erneut prüfen</string>",
            "<string name=\"review_revert_failed\">Rückgängig fehlgeschlagen. Das Kleidungsstück bleibt aktualisiert.</string>",
        ).forEach { line -> assertTrue(line, de.contains(line)) }
    }

    // The accepted card's reset rule: a card leaves only while it is resolved; undone while leaving, it is visible again.

    @Test
    fun aCardLeavesOnlyWhileItIsResolvedAndIsVisibleAgainWhenUndone() {
        assertTrue(reviewCardLeaving(leaving = true, resolved = true))
        assertFalse(reviewCardLeaving(leaving = true, resolved = false))
        assertFalse(reviewCardLeaving(leaving = false, resolved = true))
        assertFalse(reviewCardLeaving(leaving = false, resolved = false))
    }

    // Helpers. The screen runs Undo as: the session's revert gives the delta, the write runs, and the session changes only once
    // the revert has landed. [undo] is that step, so a failed revert leaves the card decided.

    /** The onRevert fake: every call is recorded; it answers with a landed result unless [answer] says otherwise. */
    private inner class RecordingRevert(private val answer: ((List<AppliedDelta>) -> Result<List<ApplyResult>>)? = null) {
        val calls = mutableListOf<List<AppliedDelta>>()

        fun run(applied: List<AppliedDelta>): Result<List<ApplyResult>> {
            calls += applied
            return answer?.invoke(applied) ?: landedFor(applied)
        }
    }

    private fun undo(session: TagReviewSession, garmentId: String, write: RecordingRevert): TagReviewSession {
        if (!canUndoNow(session, garmentId, emptyList())) return session
        val (reverted, delta) = session.revert(garmentId)
        if (delta == null) return reverted
        return if (revertLanded(write.run(listOf(delta)))) session.revert(garmentId).first else session
    }

    private fun landedApply(session: TagReviewSession, garmentId: String): TagReviewSession {
        val (applying, delta) = session.apply(garmentId, existingTagIds)
        val applied = AppliedDelta(garmentId, emptySet(), delta?.add ?: emptySet())
        return applying.appliedResult(garmentId, ApplyResult(item = garment(garmentId), applied = applied))
    }

    private fun deltaOf(session: TagReviewSession, garmentId: String): TagDelta? = session.apply(garmentId, existingTagIds).second

    private fun landedFor(applied: List<AppliedDelta>): Result<List<ApplyResult>> =
        Result.success(applied.map { delta -> ApplyResult(item = garment(delta.garmentId), applied = delta) })

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
