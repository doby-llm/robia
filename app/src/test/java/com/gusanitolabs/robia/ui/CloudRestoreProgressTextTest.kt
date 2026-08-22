package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.RestoreByteProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudRestoreProgressTextTest {
    @Test
    fun megabyteProgress_usesOneDecimalAndNeverFallsBackToRawBytes() {
        assertEquals("0.0 MB", 0L.formatRestoreMegabytes())
        assertEquals("0.0 MB", 512L.formatRestoreMegabytes())
        assertEquals("1.0 MB", 1_048_576L.formatRestoreMegabytes())
        assertEquals("1.6 MB", 1_572_864L.formatRestoreMegabytes())
        assertEquals("2.8 MB", 2_786_253L.formatRestoreMegabytes())
        assertEquals("37.4 MB", 37_360_210L.formatRestoreMegabytes())
        assertEquals("0.0 MB", (-1L).formatRestoreMegabytes())

        assertEquals(
            RestoreByteProgressText(completedMegabytes = "0.5 MB", totalMegabytes = "2.0 MB"),
            RestoreByteProgress(completedBytes = 524_288L, totalBytes = 2_097_152L).toDisplayText(),
        )
        assertEquals(
            RestoreByteProgressText(completedMegabytes = "0.5 MB", totalMegabytes = null),
            RestoreByteProgress(completedBytes = 524_288L, totalBytes = null).toDisplayText(),
        )
    }

    @Test
    fun etaEstimator_waitsForEnoughValidByteSamples() {
        val progress = restoreProgress(completedBytes = 1_048_576L, totalBytes = 20_971_520L)
        val oneSample = RestoreEtaState().updated(progress, elapsedRealtimeMillis = 0L)

        assertNull(oneSample.estimate(progress))
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 1_048_576L, totalBytes = 20_971_520L), 0L)
                .updated(restoreProgress(completedBytes = 1_049_000L, totalBytes = 20_971_520L), 2_000L)
                .estimate(restoreProgress(completedBytes = 1_049_000L, totalBytes = 20_971_520L)),
        )
    }

    @Test
    fun etaEstimator_usesByteRateAndFormatsFriendlyBuckets() {
        val aboutTwoMinutes = RestoreEtaState()
            .updated(restoreProgress(completedBytes = 0L, totalBytes = 125_829_120L), 0L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 125_829_120L), 2_000L)
            .estimate(restoreProgress(completedBytes = 2_097_152L, totalBytes = 125_829_120L))

        assertEquals(RestoreEta.AboutMinutes(2L), aboutTwoMinutes)
        assertEquals(RestoreEta.LessThanMinute, RestoreEta.fromSeconds(45L))
    }

    @Test
    fun etaEstimator_hidesUnknownStalledCompleteTerminalAndAbsurdProgress() {
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 0L, totalBytes = null), 0L)
                .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = null), 2_000L)
                .estimate(restoreProgress(completedBytes = 2_097_152L, totalBytes = null)),
        )
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 0L)
                .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 2_000L)
                .estimate(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L)),
        )
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 20_971_520L, totalBytes = 20_971_520L), 2_000L)
                .estimate(restoreProgress(completedBytes = 20_971_520L, totalBytes = 20_971_520L)),
        )
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 0L, totalBytes = 20_971_520L), 0L)
                .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 2_000L)
                .updated(
                    restoreProgress(
                        completedBytes = 2_097_152L,
                        totalBytes = 20_971_520L,
                        status = CloudRestoreStatus.Failed,
                    ),
                    3_000L,
                )
                .estimate(
                    restoreProgress(
                        completedBytes = 2_097_152L,
                        totalBytes = 20_971_520L,
                        status = CloudRestoreStatus.Failed,
                    ),
                ),
        )
        assertNull(
            RestoreEtaState()
                .updated(restoreProgress(completedBytes = 0L, totalBytes = 20_000_000_000L), 0L)
                .updated(restoreProgress(completedBytes = 65_536L, totalBytes = 20_000_000_000L), 2_000L)
                .estimate(restoreProgress(completedBytes = 65_536L, totalBytes = 20_000_000_000L)),
        )
    }

    @Test
    fun etaEstimator_hidesPreviouslyValidEstimateAfterAStalledSample() {
        val state = RestoreEtaState()
            .updated(restoreProgress(completedBytes = 0L, totalBytes = 20_971_520L), 0L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 2_000L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 4_000L)

        assertNull(
            state.estimate(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L)),
        )
    }

    @Test
    fun etaEstimator_resetsWhenRetryProgressRestartsOrTotalChanges() {
        val previousRestore = RestoreEtaState()
            .updated(restoreProgress(completedBytes = 0L, totalBytes = 20_971_520L), 0L)
            .updated(restoreProgress(completedBytes = 10_485_760L, totalBytes = 20_971_520L), 2_000L)
        val restartedOnePhotoRetry = previousRestore.updated(
            restoreProgress(completedBytes = 524_288L, totalBytes = 1_048_576L),
            3_000L,
        )
        val changedTotal = previousRestore.updated(
            restoreProgress(completedBytes = 11_010_048L, totalBytes = 22_020_096L),
            3_000L,
        )

        assertNull(
            restartedOnePhotoRetry.estimate(restoreProgress(completedBytes = 524_288L, totalBytes = 1_048_576L)),
        )
        assertNull(
            changedTotal.estimate(restoreProgress(completedBytes = 11_010_048L, totalBytes = 22_020_096L)),
        )
    }

    private fun restoreProgress(
        completedBytes: Long,
        totalBytes: Long?,
        status: CloudRestoreStatus = CloudRestoreStatus.Running,
    ): CloudRestoreProgress = CloudRestoreProgress(
        phase = CloudRestorePhase.Downloading,
        completedWork = 2,
        totalWork = 8,
        status = status,
        byteProgress = RestoreByteProgress(completedBytes, totalBytes),
    )
}
