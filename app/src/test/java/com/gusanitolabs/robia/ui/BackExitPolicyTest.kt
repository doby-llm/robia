package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackExitPolicyTest {
    private val plainBrowse = BackState(
        onManage = false,
        selectionCount = 0,
        hasQuery = false,
        hasSimilarTo = false,
        hasFilters = false,
        lastExitHintMs = null,
    )

    // V-NAV-BACK-1: priority table of one press.

    @Test
    fun selectionClearsFirstEvenWithQuery() {
        val state = plainBrowse.copy(selectionCount = 2, hasQuery = true, hasFilters = true)

        assertEquals(BackAction.ClearSelection, decideBack(state, nowMs = 10_000L))
    }

    @Test
    fun queryWithoutSelectionClearsSearchAndFilters() {
        assertEquals(
            BackAction.ClearSearchAndFilters,
            decideBack(plainBrowse.copy(hasQuery = true), nowMs = 10_000L),
        )
    }

    @Test
    fun similarToWithoutSelectionClearsSearchAndFilters() {
        assertEquals(
            BackAction.ClearSearchAndFilters,
            decideBack(plainBrowse.copy(hasSimilarTo = true), nowMs = 10_000L),
        )
    }

    @Test
    fun filtersWithoutSelectionClearsSearchAndFilters() {
        assertEquals(
            BackAction.ClearSearchAndFilters,
            decideBack(plainBrowse.copy(hasFilters = true), nowMs = 10_000L),
        )
    }

    @Test
    fun manageWithNothingElseActiveGoesToBrowse() {
        assertEquals(
            BackAction.GoToBrowse,
            decideBack(plainBrowse.copy(onManage = true), nowMs = 10_000L),
        )
    }

    @Test
    fun manageWithActiveFilterClearsFilterFirst() {
        val state = plainBrowse.copy(onManage = true, hasFilters = true)

        assertEquals(BackAction.ClearSearchAndFilters, decideBack(state, nowMs = 10_000L))
    }

    @Test
    fun plainBrowseWithoutEarlierHintShowsHint() {
        assertEquals(BackAction.ShowExitHint, decideBack(plainBrowse, nowMs = 10_000L))
    }

    // V-NAV-BACK-2: the exit window.

    @Test
    fun secondPressWithinWindowExits() {
        val state = plainBrowse.copy(lastExitHintMs = 10_000L)

        assertEquals(BackAction.Exit, decideBack(state, nowMs = 11_999L))
        assertEquals(BackAction.Exit, decideBack(state, nowMs = 12_000L))
    }

    @Test
    fun secondPressAfterWindowShowsHintAgain() {
        val state = plainBrowse.copy(lastExitHintMs = 10_000L)

        assertEquals(BackAction.ShowExitHint, decideBack(state, nowMs = 12_001L))
    }

    @Test
    fun pressBeforeHintTimeNeverExits() {
        val state = plainBrowse.copy(lastExitHintMs = 10_000L)

        assertEquals(BackAction.ShowExitHint, decideBack(state, nowMs = 9_999L))
    }

    @Test
    fun manageWithStaleHintStillGoesToBrowse() {
        val state = plainBrowse.copy(onManage = true, lastExitHintMs = 10_000L)

        assertEquals(BackAction.GoToBrowse, decideBack(state, nowMs = 10_500L))
        assertEquals(BackAction.GoToBrowse, decideBack(state, nowMs = 60_000L))
    }

    @Test
    fun exitWindowIsTwoSeconds() {
        assertEquals(2000L, EXIT_WINDOW_MILLIS)
    }
}
