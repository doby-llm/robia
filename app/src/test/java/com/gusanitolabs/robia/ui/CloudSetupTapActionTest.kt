package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSetupTapActionTest {
    @Test
    fun unconfiguredOrDisconnectedAccountStartsSetupAgain() {
        assertEquals(CloudSetupTapAction.StartSetup, cloudSetupTapAction(DriveSyncConnectionStatus.NotConfigured))
        assertEquals(CloudSetupTapAction.StartSetup, cloudSetupTapAction(DriveSyncConnectionStatus.Disconnected))
    }

    @Test
    fun activeOrDisabledDriveRunsManualSync() {
        assertEquals(CloudSetupTapAction.ManualSync, cloudSetupTapAction(DriveSyncConnectionStatus.Connected))
        assertEquals(CloudSetupTapAction.ManualSync, cloudSetupTapAction(DriveSyncConnectionStatus.NeedsAttention))
        assertEquals(CloudSetupTapAction.ManualSync, cloudSetupTapAction(DriveSyncConnectionStatus.Disabled))
    }

    @Test
    fun runningSyncOnlyReportsProgress() {
        assertEquals(CloudSetupTapAction.SyncInProgress, cloudSetupTapAction(DriveSyncConnectionStatus.Syncing))
    }

    @Test
    fun everyStatusHasAnExplicitAction() {
        val expected = mapOf(
            DriveSyncConnectionStatus.Disabled to CloudSetupTapAction.ManualSync,
            DriveSyncConnectionStatus.NotConfigured to CloudSetupTapAction.StartSetup,
            DriveSyncConnectionStatus.Disconnected to CloudSetupTapAction.StartSetup,
            DriveSyncConnectionStatus.Connected to CloudSetupTapAction.ManualSync,
            DriveSyncConnectionStatus.Syncing to CloudSetupTapAction.SyncInProgress,
            DriveSyncConnectionStatus.NeedsAttention to CloudSetupTapAction.ManualSync,
        )
        assertEquals(DriveSyncConnectionStatus.values().toSet(), expected.keys)
        expected.forEach { (status, action) -> assertEquals(status.name, action, cloudSetupTapAction(status)) }
    }
}
