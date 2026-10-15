package com.gusanitolabs.robia.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncCycleSummaryTest {
    @Test
    fun resultNameIsAnExplicitLiteralForEveryOutcome() {
        val expected = mapOf(
            SyncRunResult.Success to "Success",
            SyncRunResult.Retry to "Retry",
            SyncRunResult.AuthBlocked to "AuthBlocked",
            SyncRunResult.Conflict to "Conflict",
            SyncRunResult.NeedsUser to "NeedsUser",
        )
        expected.forEach { (result, name) -> assertEquals(name, summary(result).resultName) }
    }

    @Test
    fun cancelledCycleHasNoResultName() {
        assertNull(summary(null).resultName)
    }

    private fun summary(result: SyncRunResult?) = SyncCycleSummary(
        startedAtEpochMillis = 1L,
        endedAtEpochMillis = 2L,
        result = result,
        pendingAtStart = 0,
    )
}
