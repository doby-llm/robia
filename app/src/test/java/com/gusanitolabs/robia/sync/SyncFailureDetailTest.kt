package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFailureDetailTest {
    @Test
    fun aSuccessfulCycleAddsNothing() {
        assertEquals("", syncFailureDetail(SyncRunResult.Success, DriveSyncDisabledReason.UserNotConnected, "http=401 reason=authError"))
    }

    @Test
    fun anAuthBlockedCycleNamesTheHttpCodeAndReason() {
        assertEquals(" http=401 reason=authError", syncFailureDetail(SyncRunResult.AuthBlocked, null, "http=401 reason=authError"))
    }

    @Test
    fun aBlockedUploadNamesItsReasonAndTheLastHttpFailure() {
        assertEquals(
            " blocked=UserNotConnected http=403 reason=none",
            syncFailureDetail(SyncRunResult.AuthBlocked, DriveSyncDisabledReason.UserNotConnected, "http=403 reason=none"),
        )
    }

    @Test
    fun aRetryWithoutAnyDriveDetailAddsNothing() {
        assertEquals("", syncFailureDetail(SyncRunResult.Retry, null, null))
    }
}
