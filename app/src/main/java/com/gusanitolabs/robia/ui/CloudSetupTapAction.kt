package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus

enum class CloudSetupTapAction { StartSetup, ManualSync }

/**
 * Tapping the Drive row never reports "configured" without a way forward: a disconnected or unconfigured account starts setup again.
 */
internal fun cloudSetupTapAction(status: DriveSyncConnectionStatus): CloudSetupTapAction = when (status) {
    DriveSyncConnectionStatus.Connected,
    DriveSyncConnectionStatus.NeedsAttention,
    DriveSyncConnectionStatus.Syncing,
    DriveSyncConnectionStatus.Disabled -> CloudSetupTapAction.ManualSync
    DriveSyncConnectionStatus.NotConfigured,
    DriveSyncConnectionStatus.Disconnected -> CloudSetupTapAction.StartSetup
}
