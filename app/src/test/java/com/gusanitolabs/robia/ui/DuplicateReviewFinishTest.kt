package com.gusanitolabs.robia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateReviewFinishTest {
    // V-FB3-DUP-DONE-1: the review ends only when the groups went from some to none after the user acted.

    @Test
    fun finishesWhenTheLastGroupIsGoneAfterTheUserActed() {
        assertTrue(shouldFinishDuplicateReview(wasNonEmpty = true, nowEmpty = true, userActed = true))
    }

    @Test
    fun neverFinishesWhenTheScreenOpenedWithNoGroups() {
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = false, nowEmpty = true, userActed = true))
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = false, nowEmpty = true, userActed = false))
    }

    @Test
    fun neverFinishesWithoutAUserActionSoARescanCannotThrowSomeoneOut() {
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = true, nowEmpty = true, userActed = false))
    }

    @Test
    fun neverFinishesWhileGroupsRemain() {
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = true, nowEmpty = false, userActed = true))
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = true, nowEmpty = false, userActed = false))
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = false, nowEmpty = false, userActed = true))
        assertFalse(shouldFinishDuplicateReview(wasNonEmpty = false, nowEmpty = false, userActed = false))
    }
}
