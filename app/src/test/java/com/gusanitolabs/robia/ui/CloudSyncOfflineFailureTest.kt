package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.SyncCycleSummary
import com.gusanitolabs.robia.sync.SyncRunResult
import com.gusanitolabs.robia.sync.WardrobeSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** K02: connection failures read as waiting, real errors stay errors. */
class CloudSyncOfflineFailureTest {
    @Test
    fun connectionClassesAreOffline() {
        listOf(
            "UnknownHostException",
            "SocketTimeoutException",
            "ConnectException",
            "SSLHandshakeException",
            "NoRouteToHostException",
        ).forEach { name ->
            assertTrue(name, isOfflineFailure(errorClass = name, errorMessage = null))
            assertTrue(name, isOfflineFailure(errorClass = "java.net.$name", errorMessage = null))
        }
    }

    @Test
    fun realErrorsAreNotOffline() {
        assertFalse(isOfflineFailure(errorClass = "IllegalStateException", errorMessage = "boom"))
        assertFalse(isOfflineFailure(errorClass = "IOException", errorMessage = "UnknownHostException: wrapped"))
        assertFalse(isOfflineFailure(errorClass = null, errorMessage = null))
    }

    @Test
    fun classNameInMessageIsUsedWhenClassIsMissing() {
        assertTrue(
            isOfflineFailure(
                errorClass = null,
                errorMessage = "UnknownHostException: Unable to resolve host \"www.googleapis.com\"",
            ),
        )
        assertFalse(isOfflineFailure(errorClass = null, errorMessage = "Quota exceeded: try later"))
    }

    @Test
    fun developerCardFlagsOnlyConnectionFailures() {
        val offline = indicator(errorClass = "UnknownHostException", errorMessage = "Unable to resolve host", developerMode = true)
        assertTrue(offline.offline)
        assertEquals("UnknownHostException: Unable to resolve host", offline.detail)
        assertEquals(CloudSyncHeadline.Waiting, offline.headline)

        val real = indicator(errorClass = "IllegalStateException", errorMessage = "boom", developerMode = true)
        assertFalse(real.offline)
        assertEquals("IllegalStateException: boom", real.detail)
    }

    @Test
    fun outsideDeveloperModeNothingIsFlaggedOrShown() {
        val indicator = indicator(errorClass = "UnknownHostException", errorMessage = null, developerMode = false)
        assertFalse(indicator.offline)
        assertEquals(null, indicator.detail)
    }

    private fun indicator(errorClass: String?, errorMessage: String?, developerMode: Boolean): CloudSyncIndicator {
        val state = WardrobeSyncState(
            connectionStatus = DriveSyncConnectionStatus.Connected,
            pendingOperationCount = 2,
            lastCycle = SyncCycleSummary(
                startedAtEpochMillis = 1_000L,
                endedAtEpochMillis = 2_000L,
                result = SyncRunResult.Retry,
                pendingAtStart = 2,
                errorClass = errorClass,
                errorMessage = errorMessage,
            ),
        )
        return cloudSyncIndicator(state = state, developerMode = developerMode, online = true)
    }
}
