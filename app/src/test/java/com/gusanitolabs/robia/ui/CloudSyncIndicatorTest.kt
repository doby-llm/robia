package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind
import com.gusanitolabs.robia.sync.SyncCycleSummary
import com.gusanitolabs.robia.sync.SyncRunResult
import com.gusanitolabs.robia.sync.WardrobeSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** G03: one pure function drives the Browse icon, the Settings lines and the developer card. */
class CloudSyncIndicatorTest {
    private data class Row(
        val name: String,
        val status: DriveSyncConnectionStatus,
        val pending: Int,
        /** True when a restore is running; the processor's own "processing" is [DriveSyncConnectionStatus.Syncing]. */
        val restoring: Boolean,
        val attention: List<SyncAttentionKind>,
        val lastCycleResult: SyncRunResult? = null,
        val online: Boolean = true,
        val expectedHeadline: CloudSyncHeadline,
        val expectedAttention: Boolean,
    )

    private val rows = listOf(
        Row(
            name = "connected, nothing pending",
            status = DriveSyncConnectionStatus.Connected, pending = 0, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Synced, expectedAttention = false,
        ),
        Row(
            name = "connected, changes waiting",
            status = DriveSyncConnectionStatus.Connected, pending = 3, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Pending, expectedAttention = false,
        ),
        Row(
            name = "processing with nothing pending never says synced",
            status = DriveSyncConnectionStatus.Syncing, pending = 0, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Syncing, expectedAttention = false,
        ),
        Row(
            name = "processing with changes pending",
            status = DriveSyncConnectionStatus.Syncing, pending = 2, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Syncing, expectedAttention = false,
        ),
        Row(
            name = "restore running while connected",
            status = DriveSyncConnectionStatus.Connected, pending = 0, restoring = true, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Syncing, expectedAttention = false,
        ),
        Row(
            name = "connected with an attention item and changes",
            status = DriveSyncConnectionStatus.Connected, pending = 4, restoring = false,
            attention = listOf(SyncAttentionKind.DriveFull),
            expectedHeadline = CloudSyncHeadline.Attention, expectedAttention = true,
        ),
        Row(
            name = "needs attention status",
            status = DriveSyncConnectionStatus.NeedsAttention, pending = 0, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Attention, expectedAttention = true,
        ),
        Row(
            name = "account disconnected with changes",
            status = DriveSyncConnectionStatus.Disconnected, pending = 4, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Error, expectedAttention = true,
        ),
        Row(
            name = "last cycle needed sign-in",
            status = DriveSyncConnectionStatus.Connected, pending = 0, restoring = false, attention = emptyList(),
            lastCycleResult = SyncRunResult.AuthBlocked,
            expectedHeadline = CloudSyncHeadline.Error, expectedAttention = true,
        ),
        Row(
            name = "processing wins over attention",
            status = DriveSyncConnectionStatus.Syncing, pending = 0, restoring = false,
            attention = listOf(SyncAttentionKind.DriveFull),
            expectedHeadline = CloudSyncHeadline.Syncing, expectedAttention = false,
        ),
        Row(
            name = "not configured",
            status = DriveSyncConnectionStatus.NotConfigured, pending = 0, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.NotConfigured, expectedAttention = false,
        ),
        Row(
            name = "disabled keeps the local count",
            status = DriveSyncConnectionStatus.Disabled, pending = 5, restoring = false, attention = emptyList(),
            expectedHeadline = CloudSyncHeadline.Disabled, expectedAttention = false,
        ),
        Row(
            name = "offline with changes waits for the connection, not syncing",
            status = DriveSyncConnectionStatus.Connected, pending = 3, restoring = false, attention = emptyList(),
            online = false,
            expectedHeadline = CloudSyncHeadline.Waiting, expectedAttention = false,
        ),
        Row(
            name = "offline while a run is still in flight never says syncing",
            status = DriveSyncConnectionStatus.Syncing, pending = 2, restoring = false, attention = emptyList(),
            online = false,
            expectedHeadline = CloudSyncHeadline.Waiting, expectedAttention = false,
        ),
        Row(
            name = "offline with nothing pending says nothing is waiting",
            status = DriveSyncConnectionStatus.Syncing, pending = 0, restoring = false, attention = emptyList(),
            online = false,
            expectedHeadline = CloudSyncHeadline.Synced, expectedAttention = false,
        ),
        Row(
            name = "offline with an attention item still asks for attention",
            status = DriveSyncConnectionStatus.Connected, pending = 3, restoring = false,
            attention = listOf(SyncAttentionKind.DriveFull), online = false,
            expectedHeadline = CloudSyncHeadline.Attention, expectedAttention = true,
        ),
        Row(
            name = "offline with the account disconnected is an error",
            status = DriveSyncConnectionStatus.Disconnected, pending = 4, restoring = false, attention = emptyList(),
            online = false,
            expectedHeadline = CloudSyncHeadline.Error, expectedAttention = true,
        ),
        Row(
            name = "offline and not configured stays not configured",
            status = DriveSyncConnectionStatus.NotConfigured, pending = 2, restoring = false, attention = emptyList(),
            online = false,
            expectedHeadline = CloudSyncHeadline.NotConfigured, expectedAttention = false,
        ),
        Row(
            name = "online transient retry with changes also waits",
            status = DriveSyncConnectionStatus.Connected, pending = 2, restoring = false, attention = emptyList(),
            lastCycleResult = SyncRunResult.Retry,
            expectedHeadline = CloudSyncHeadline.Waiting, expectedAttention = false,
        ),
        Row(
            name = "online with changes after a successful cycle is pending",
            status = DriveSyncConnectionStatus.Connected, pending = 2, restoring = false, attention = emptyList(),
            lastCycleResult = SyncRunResult.Success,
            expectedHeadline = CloudSyncHeadline.Pending, expectedAttention = false,
        ),
    )

    @Test
    fun headlineCountAndAttentionFollowTheTable() {
        rows.forEach { row ->
            val state = WardrobeSyncState(
                connectionStatus = row.status,
                pendingOperationCount = row.pending,
                restoreProgress = if (row.restoring) runningRestore() else null,
                attention = row.attention.map { kind -> SyncAttention(kind) },
                lastCycle = row.lastCycleResult?.let { result -> cycle(result) },
            )
            val indicator = cloudSyncIndicator(state, developerMode = false, online = row.online)
            assertEquals(row.name, row.expectedHeadline, indicator.headline)
            assertEquals(row.name, row.pending, indicator.pendingCount)
            assertEquals(row.name, row.expectedAttention, indicator.attention)
        }
    }

    @Test
    fun detailCarriesTheLastFailureOnlyInDeveloperMode() {
        val failed = WardrobeSyncState(
            connectionStatus = DriveSyncConnectionStatus.Connected,
            pendingOperationCount = 1,
            lastCycle = cycle(SyncRunResult.Retry, errorClass = "java.net.SocketTimeoutException", errorMessage = "timeout"),
        )
        assertEquals(
            "java.net.SocketTimeoutException: timeout",
            cloudSyncIndicator(failed, developerMode = true).detail,
        )
        assertNull(cloudSyncIndicator(failed, developerMode = false).detail)
    }

    @Test
    fun detailShowsAMessageEvenWithoutAClass() {
        val failed = WardrobeSyncState(
            connectionStatus = DriveSyncConnectionStatus.Connected,
            pendingOperationCount = 1,
            lastCycle = cycle(SyncRunResult.Retry, errorMessage = "Drive sync was interrupted."),
        )
        assertEquals("Drive sync was interrupted.", cloudSyncIndicator(failed, developerMode = true).detail)
    }

    @Test
    fun everyCycleResultHasItsOwnReadableName() {
        val results = listOf<SyncRunResult?>(
            null,
            SyncRunResult.Success,
            SyncRunResult.Retry,
            SyncRunResult.AuthBlocked,
            SyncRunResult.Conflict,
            SyncRunResult.NeedsUser,
        )
        assertEquals(R.string.dev_cloud_cancelled, (null as SyncRunResult?).readableNameRes())
        assertEquals(results.size, results.map { it.readableNameRes() }.toSet().size)
    }

    private fun runningRestore() = CloudRestoreProgress(
        phase = CloudRestorePhase.Downloading,
        completedWork = 0,
        totalWork = 1,
        status = CloudRestoreStatus.Running,
    )

    private fun cycle(
        result: SyncRunResult?,
        errorClass: String? = null,
        errorMessage: String? = null,
    ) = SyncCycleSummary(
        startedAtEpochMillis = 1_000L,
        endedAtEpochMillis = 2_000L,
        result = result,
        pendingAtStart = 1,
        errorClass = errorClass,
        errorMessage = errorMessage,
    )
}
