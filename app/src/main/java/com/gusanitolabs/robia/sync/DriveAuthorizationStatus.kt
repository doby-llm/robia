package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus

/**
 * A failed or cancelled authorization must not claim a connection. A first setup stays NotConfigured so the user can start it
 * again; an account that was already connected moves to Disconnected (sign-in required).
 */
internal fun statusAfterFailedAuthorization(previous: DriveSyncConnectionStatus): DriveSyncConnectionStatus =
    if (previous == DriveSyncConnectionStatus.NotConfigured) {
        DriveSyncConnectionStatus.NotConfigured
    } else {
        DriveSyncConnectionStatus.Disconnected
    }
