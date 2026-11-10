package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus

enum class CloudSetupTapAction { StartSetup, ManualSync, SyncInProgress }

/**
 * Tapping the Drive row never reports "configured" without a way forward: a disconnected or unconfigured account starts setup again.
 */
internal fun cloudSetupTapAction(status: DriveSyncConnectionStatus): CloudSetupTapAction = when (status) {
    DriveSyncConnectionStatus.Connected,
    DriveSyncConnectionStatus.NeedsAttention,
    DriveSyncConnectionStatus.Disabled -> CloudSetupTapAction.ManualSync
    // A run or restore is already going: a tap only says so, it never writes a status or queues a second upload.
    DriveSyncConnectionStatus.Syncing -> CloudSetupTapAction.SyncInProgress
    DriveSyncConnectionStatus.NotConfigured,
    DriveSyncConnectionStatus.Disconnected -> CloudSetupTapAction.StartSetup
}
