package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveAuthorizationStatusTest {
    @Test
    fun failedFirstSetupStaysNotConfiguredSoItCanBeRetried() {
        assertEquals(
            DriveSyncConnectionStatus.NotConfigured,
            statusAfterFailedAuthorization(DriveSyncConnectionStatus.NotConfigured),
        )
    }

    @Test
    fun failedAuthorizationOfAnExistingAccountRequiresSignIn() {
        assertEquals(DriveSyncConnectionStatus.Disconnected, statusAfterFailedAuthorization(DriveSyncConnectionStatus.Disabled))
        assertEquals(DriveSyncConnectionStatus.Disconnected, statusAfterFailedAuthorization(DriveSyncConnectionStatus.Disconnected))
        assertEquals(DriveSyncConnectionStatus.Disconnected, statusAfterFailedAuthorization(DriveSyncConnectionStatus.Connected))
        assertEquals(DriveSyncConnectionStatus.Disconnected, statusAfterFailedAuthorization(DriveSyncConnectionStatus.Syncing))
        assertEquals(DriveSyncConnectionStatus.Disconnected, statusAfterFailedAuthorization(DriveSyncConnectionStatus.NeedsAttention))
    }

    @Test
    fun everyStatusIsCoveredByTheFailureRule() {
        DriveSyncConnectionStatus.values().forEach { previous ->
            val expected = if (previous == DriveSyncConnectionStatus.NotConfigured) {
                DriveSyncConnectionStatus.NotConfigured
            } else {
                DriveSyncConnectionStatus.Disconnected
            }
            assertEquals(previous.name, expected, statusAfterFailedAuthorization(previous))
        }
    }
}
