package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-COL-08 logic: draft, view, diff, undo, prune and leave decision (V-C04-1, V-C04-2) and save form (V-C04-4). */
class CollectionMembersSessionTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    private fun session(saved: Set<String>) = CollectionMembersSession.open("tag-1", saved)

    @Test
    fun openStartsWithDraftEqualToSavedAndNoChanges() {
        val s = session(setOf("a", "c"))
        assertEquals(setOf("a", "c"), s.draft)
        assertEquals(MembersView.All, s.view)
        assertFalse(s.hasChanges)
        assertEquals(emptySet<String>(), s.added)
        assertEquals(emptySet<String>(), s.removed)
    }

    @Test
    fun toggleAddsAndRemovesAndDiffFollows() {
        val s = session(setOf("a", "c"))
            .toggle("b")   // added
            .toggle("c")   // removed
        assertEquals(setOf("a", "b"), s.draft)
        assertEquals(setOf("b"), s.added)
        assertEquals(setOf("c"), s.removed)
        assertTrue(s.hasChanges)
    }

    @Test
    fun togglingBackToSavedStateHasNoChanges() {
        val s = session(setOf("a")).toggle("b").toggle("b")
        assertFalse(s.hasChanges)
    }

    @Test
    fun undoAllResetsDraftAndKeepsView() {
        val s = session(setOf("a"))
            .withView(MembersView.NotIn)
            .toggle("b")
            .toggle("a")
            .undoAll()
        assertEquals(setOf("a"), s.draft)
        assertEquals(MembersView.NotIn, s.view)
        assertFalse(s.hasChanges)
    }

    @Test
    fun withDraftReplacesTheDraftOnly() {
        val s = session(setOf("a")).withDraft(setOf("d", "e"))
        assertEquals(setOf("a"), s.saved)
        assertEquals(setOf("d", "e"), s.draft)
        assertEquals(setOf("d", "e"), s.added)
        assertEquals(setOf("a"), s.removed)
    }

    @Test
    fun visibleAppliesViewOnDraftMembershipInGivenOrder() {
        val s = session(setOf("a", "c")).toggle("d")   // draft = a, c, d
        assertEquals(ids, s.visible(ids))
        assertEquals(listOf("a", "c", "d"), s.withView(MembersView.InCollection).visible(ids))
        assertEquals(listOf("b", "e"), s.withView(MembersView.NotIn).visible(ids))
    }

    @Test
    fun visibleJudgesDraftNotSaved() {
        // "b" is saved but toggled out of the draft: the draft is empty, so every garment is Not in.
        val s = session(setOf("b")).toggle("b")
        assertEquals(ids, s.withView(MembersView.NotIn).visible(ids))
        assertEquals(emptyList<String>(), s.withView(MembersView.InCollection).visible(ids))
    }

    @Test
    fun pruneDropsVanishedGarmentsFromSavedAndDraft() {
        val s = session(setOf("a", "b"))
            .toggle("c")              // draft = a, b, c
            .pruned(setOf("a", "c", "d"))
        // "b" vanished: it is neither saved nor drafted, so it is not reported as removed.
        assertEquals(setOf("a"), s.saved)
        assertEquals(setOf("a", "c"), s.draft)
        assertEquals(setOf("c"), s.added)
        assertEquals(emptySet<String>(), s.removed)
        assertTrue(s.hasChanges)
    }

    @Test
    fun pruneOnlyVanishedGarmentsLeavesNoChanges() {
        val s = session(setOf("a", "b")).pruned(setOf("a"))
        assertEquals(setOf("a"), s.saved)
        assertEquals(setOf("a"), s.draft)
        assertFalse(s.hasChanges)
    }

    @Test
    fun leaveWithoutChangesLeaves() {
        assertEquals(LeaveDecision.Leave, session(setOf("a")).leaveDecision())
    }

    @Test
    fun leaveWithChangesAsksSaveOrDiscard() {
        assertEquals(LeaveDecision.AskSaveDiscard, session(setOf("a")).toggle("b").leaveDecision())
    }

    @Test
    fun leaveDecisionDoesNotWriteOrChangeTheSession() {
        // Outside tap = Keep editing: the decision is a query, so the draft is exactly as it was.
        val s = session(setOf("a")).toggle("b")
        val before = s.toSaveable()
        s.leaveDecision()
        assertEquals(before, s.toSaveable())
        assertTrue(s.hasChanges)
    }

    @Test
    fun collectionStillExistsOnlyWhileItsIdIsAvailable() {
        assertTrue(collectionStillExists("tag-1", setOf("tag-1", "tag-2")))
        assertFalse(collectionStillExists("tag-1", setOf("tag-2")))
        assertFalse(collectionStillExists("tag-1", emptySet()))
    }

    @Test
    fun saveableFormIsIdViewThenSavedThenDraftEntries() {
        val s = session(setOf("b", "a")).withView(MembersView.InCollection).toggle("c")
        assertEquals(
            listOf("tag-1", "InCollection", "s:a", "s:b", "d:a", "d:b", "d:c"),
            s.toSaveable(),
        )
    }

    @Test
    fun saveableFormRoundTrips() {
        val states = listOf(
            session(emptySet()),
            session(setOf("a", "b")),
            session(setOf("a", "b")).toggle("a").withView(MembersView.NotIn),
            session(setOf("x:1", "d:2")).toggle("s:9").withView(MembersView.InCollection),
            session(setOf("a")).withDraft(emptySet()),
        )
        for (s in states) {
            assertEquals(s, CollectionMembersSession.fromSaveable(s.toSaveable()))
        }
    }

    @Test
    fun fromSaveableRejectsMalformedLists() {
        assertNull(CollectionMembersSession.fromSaveable(emptyList()))
        assertNull(CollectionMembersSession.fromSaveable(listOf("tag-1")))
        assertNull(CollectionMembersSession.fromSaveable(listOf("tag-1", "Unknown")))
        assertNull(CollectionMembersSession.fromSaveable(listOf("tag-1", "All", "x:a")))
    }
}
