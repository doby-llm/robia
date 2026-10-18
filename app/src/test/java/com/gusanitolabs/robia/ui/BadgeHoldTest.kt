package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeHoldTest {
    private val waiting = badgeFor(GarmentSyncStatus.Dirty)!!
    private val uploading = badgeFor(GarmentSyncStatus.Running)!!

    @Test
    fun shownBadgeIsHeldUntilTheHoldHasPassed() {
        val shownAt = 1_000L
        assertEquals(waiting, visibleBadge(waiting, shownAt, null, shownAt + BADGE_HOLD_MILLIS - 1))
        assertNull(visibleBadge(waiting, shownAt, null, shownAt + BADGE_HOLD_MILLIS))
    }

    @Test
    fun newBadgeAppearsAtOnceWhenNothingIsShown() {
        assertEquals(waiting, visibleBadge(null, 0L, waiting, 0L))
    }

    @Test
    fun shownBadgeIsReplacedOnlyAfterTheHold() {
        val shownAt = 1_000L
        assertEquals(waiting, visibleBadge(waiting, shownAt, uploading, shownAt + BADGE_HOLD_MILLIS - 1))
        assertEquals(uploading, visibleBadge(waiting, shownAt, uploading, shownAt + BADGE_HOLD_MILLIS))
    }

    @Test
    fun nothingShownAndNothingCurrentStaysHidden() {
        assertNull(visibleBadge(null, 0L, null, 5_000L))
    }
}
