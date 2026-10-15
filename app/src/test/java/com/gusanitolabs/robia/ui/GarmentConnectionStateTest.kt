package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** J02: a pending garment reads as waiting for the connection only when it is offline and Drive is enabled. */
class GarmentConnectionStateTest {
    private data class Row(
        val name: String,
        val status: GarmentSyncStatus,
        val connection: DriveSyncConnectionStatus,
        val online: Boolean,
        val expectedStatus: GarmentSyncStatus,
        val expectedWaiting: Boolean,
    )

    private val rows = listOf(
        Row(
            name = "queued offline waits",
            status = GarmentSyncStatus.Queued, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.Queued, expectedWaiting = true,
        ),
        Row(
            name = "dirty offline waits",
            status = GarmentSyncStatus.Dirty, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.Dirty, expectedWaiting = true,
        ),
        Row(
            name = "failed retryable offline waits",
            status = GarmentSyncStatus.FailedRetryable, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.FailedRetryable, expectedWaiting = true,
        ),
        Row(
            name = "queued online is an ordinary pending upload",
            status = GarmentSyncStatus.Queued, connection = DriveSyncConnectionStatus.Connected, online = true,
            expectedStatus = GarmentSyncStatus.Queued, expectedWaiting = false,
        ),
        Row(
            name = "failed retryable online retries as before",
            status = GarmentSyncStatus.FailedRetryable, connection = DriveSyncConnectionStatus.Connected, online = true,
            expectedStatus = GarmentSyncStatus.FailedRetryable, expectedWaiting = false,
        ),
        Row(
            name = "running offline keeps its own state",
            status = GarmentSyncStatus.Running, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.Running, expectedWaiting = false,
        ),
        Row(
            name = "synced offline stays synced",
            status = GarmentSyncStatus.Synced, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.Synced, expectedWaiting = false,
        ),
        Row(
            name = "needs user action offline stays a user action",
            status = GarmentSyncStatus.NeedsUserAction, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.NeedsUserAction, expectedWaiting = false,
        ),
        Row(
            name = "disconnected offline needs sign-in, not a wait",
            status = GarmentSyncStatus.Queued, connection = DriveSyncConnectionStatus.Disconnected, online = false,
            expectedStatus = GarmentSyncStatus.AuthBlocked, expectedWaiting = false,
        ),
        Row(
            name = "not configured offline stays saved on this phone",
            status = GarmentSyncStatus.Queued, connection = DriveSyncConnectionStatus.NotConfigured, online = false,
            expectedStatus = GarmentSyncStatus.Queued, expectedWaiting = false,
        ),
        Row(
            name = "disabled offline has no connection to wait for",
            status = GarmentSyncStatus.Dirty, connection = DriveSyncConnectionStatus.Disabled, online = false,
            expectedStatus = GarmentSyncStatus.Dirty, expectedWaiting = false,
        ),
        Row(
            name = "local-only garment offline is not waiting",
            status = GarmentSyncStatus.LocalOnly, connection = DriveSyncConnectionStatus.Connected, online = false,
            expectedStatus = GarmentSyncStatus.LocalOnly, expectedWaiting = false,
        ),
    )

    @Test
    fun resolvedStatusAndWaitingFollowTheTable() {
        rows.forEach { row ->
            val cloud = row.status.resolveForConnection(row.connection, row.online)
            assertEquals(row.name, row.expectedStatus, cloud.status)
            assertEquals(row.name, row.expectedWaiting, cloud.waitingForConnection)
        }
    }
}
