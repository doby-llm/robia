package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackExitMachineTest {
    private var now = 10_000L
    private val machine = BackExitMachine { now }

    private val plainBrowse = BackState(
        onManage = false,
        selectionCount = 0,
        hasQuery = false,
        hasSimilarTo = false,
        hasFilters = false,
        lastExitHintMs = null,
    )
    private val browseKey = listOf<Any?>("Browse", "", null, "none", emptySet<String>())
    private val otherKey = listOf<Any?>("Browse", "shirt", null, "none", emptySet<String>())

    // V-FB3-BACK-1: the exit window, measured with an injected clock.

    @Test
    fun pressesA1999msApartExit() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 1_999L
        assertEquals(BackAction.Exit, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun pressesA2000msApartExit() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 2_000L
        assertEquals(BackAction.Exit, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun pressesA2001msApartShowHintAgain() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 2_001L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun changedContextKeyBetweenPressesStartsOver() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 500L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, otherKey))

        // The hint is now the one for otherKey, so a press in that context exits.
        now += 500L
        assertEquals(BackAction.Exit, machine.onBack(plainBrowse, otherKey))
    }

    @Test
    fun clockGoingBackwardsNeverExits() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now -= 1_000L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now -= 5_000L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun selectionClearsWithoutStoringHint() {
        val selected = plainBrowse.copy(selectionCount = 2)

        assertEquals(BackAction.ClearSelection, machine.onBack(selected, browseKey))
        assertFalse(machine.hintVisible())

        // No hint was stored, so the next plain press only shows the hint.
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun selectionClearsAndForgetsPendingHint() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 500L
        assertEquals(BackAction.ClearSelection, machine.onBack(plainBrowse.copy(selectionCount = 1), browseKey))
        assertFalse(machine.hintVisible())

        now += 500L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun searchAndFiltersClearWithoutStoringHint() {
        assertEquals(
            BackAction.ClearSearchAndFilters,
            machine.onBack(plainBrowse.copy(hasQuery = true), otherKey),
        )
        assertFalse(machine.hintVisible())

        assertEquals(
            BackAction.ClearSearchAndFilters,
            machine.onBack(plainBrowse.copy(hasSimilarTo = true), otherKey),
        )
        assertEquals(
            BackAction.ClearSearchAndFilters,
            machine.onBack(plainBrowse.copy(hasFilters = true), otherKey),
        )
        assertFalse(machine.hintVisible())
    }

    @Test
    fun manageGoesToBrowseWithoutStoringHint() {
        val manage = plainBrowse.copy(onManage = true)
        val manageKey = listOf<Any?>("ManageTags", "", null, "none", emptySet<String>())

        assertEquals(BackAction.GoToBrowse, machine.onBack(manage, manageKey))
        assertFalse(machine.hintVisible())
    }

    @Test
    fun pressAfterManageGoesToBrowseShowsHintFirst() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 100L
        assertEquals(BackAction.GoToBrowse, machine.onBack(plainBrowse.copy(onManage = true), browseKey))

        now += 100L
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun afterExitANewPressShowsHintAgain() {
        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))

        now += 500L
        assertEquals(BackAction.Exit, machine.onBack(plainBrowse, browseKey))
        assertFalse(machine.hintVisible())

        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
    }

    @Test
    fun hintVisibleInsideWindowOnly() {
        assertFalse(machine.hintVisible())

        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
        assertTrue(machine.hintVisible())

        now += 1_999L
        assertTrue(machine.hintVisible())

        now += 2L
        assertFalse(machine.hintVisible())
    }

    @Test
    fun liveHintAgeReportsElapsedTimeForSameContextOnly() {
        assertNull(machine.liveHintAgeMs(browseKey))

        assertEquals(BackAction.ShowExitHint, machine.onBack(plainBrowse, browseKey))
        now += 300L

        assertEquals(300L, machine.liveHintAgeMs(browseKey))
        assertNull(machine.liveHintAgeMs(otherKey))
    }
}
