package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.RestoreByteProgress
import com.gusanitolabs.robia.sync.RestoreItemProgress
import com.gusanitolabs.robia.sync.RestoreUploadProgress
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
            RestoreByteProgressText(completedMegabytes = "0.5 MB", totalMegabytes = "2.1 MB"),
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
    fun etaEstimator_keepsPreviouslyValidEstimateAfterAStalledSample() {
        val state = RestoreEtaState()
            .updated(restoreProgress(completedBytes = 0L, totalBytes = 20_971_520L), 0L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 2_000L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L), 4_000L)

        assertEquals(
            RestoreEta.LessThanMinute,
            state.estimate(restoreProgress(completedBytes = 2_097_152L, totalBytes = 20_971_520L)),
        )
    }

    @Test
    fun etaEstimator_showsNoNullFrameWhileBytesAreUnchanged() {
        val first = RestoreEtaState()
            .updated(restoreProgress(completedBytes = 0L, totalBytes = 125_829_120L), 0L)
            .updated(restoreProgress(completedBytes = 2_097_152L, totalBytes = 125_829_120L), 2_000L)
        val stalledProgress = restoreProgress(completedBytes = 2_097_152L, totalBytes = 125_829_120L)
        val stalled = first.updated(stalledProgress, 3_000L)

        assertEquals(first.estimate(stalledProgress), stalled.estimate(stalledProgress))
        assertEquals(RestoreEta.AboutMinutes(2L), stalled.estimate(stalledProgress))
    }

    @Test
    fun etaEstimator_changesShownValueAtMostEveryFiveSecondsAndNeverIncreases() {
        val total = 10_000_000L
        val slowStart = RestoreEtaState(
            anchor = RestoreEtaSample(elapsedRealtimeMillis = 0L, completedBytes = 0L, totalBytes = total),
            lastSample = RestoreEtaSample(elapsedRealtimeMillis = 0L, completedBytes = 0L, totalBytes = total),
            bytesPerSecond = 1_000_000.0,
            shown = RestoreEta.AboutMinutes(10L),
            shownAtElapsedRealtimeMillis = 0L,
        )
        val soonAfter = slowStart.updated(restoreProgress(completedBytes = 1_000_000L, totalBytes = total), 1_000L)
        val fiveSecondsLater = soonAfter.updated(restoreProgress(completedBytes = 2_000_000L, totalBytes = total), 6_000L)

        assertEquals(RestoreEta.AboutMinutes(10L), soonAfter.estimate(restoreProgress(completedBytes = 1_000_000L, totalBytes = total)))
        assertEquals(RestoreEta.LessThanMinute, fiveSecondsLater.estimate(restoreProgress(completedBytes = 2_000_000L, totalBytes = total)))
    }

    @Test
    fun etaEstimator_neverRaisesAShownEstimate() {
        val total = 1_000_000L
        val shownLessThanMinute = RestoreEtaState(
            anchor = RestoreEtaSample(elapsedRealtimeMillis = 0L, completedBytes = 0L, totalBytes = total),
            lastSample = RestoreEtaSample(elapsedRealtimeMillis = 1_000L, completedBytes = 100_000L, totalBytes = total),
            bytesPerSecond = 1_000.0,
            shown = RestoreEta.LessThanMinute,
            shownAtElapsedRealtimeMillis = 1_000L,
        )
        val slowerNow = shownLessThanMinute.updated(
            restoreProgress(completedBytes = 101_000L, totalBytes = total),
            10_000L,
        )

        assertEquals(RestoreEta.LessThanMinute, slowerNow.estimate(restoreProgress(completedBytes = 101_000L, totalBytes = total)))
    }

    @Test
    fun etaFormatting_bucketBoundariesAreStable() {
        assertEquals(RestoreEta.LessThanMinute, RestoreEta.fromSeconds(59L))
        assertEquals(RestoreEta.AboutMinutes(1L), RestoreEta.fromSeconds(60L))
        assertEquals(RestoreEta.AboutMinutes(10L), RestoreEta.fromSeconds(599L))
        assertEquals(RestoreEta.AboutMinutes(10L), RestoreEta.fromSeconds(600L))
        assertEquals(RestoreEta.AboutMinutes(15L), RestoreEta.fromSeconds(900L))
    }

    @Test
    fun stageBarFraction_usesBytesForDownloadAndItemsWhenSizesAreUnknown() {
        assertEquals(
            0.25f,
            stageProgress(CloudRestorePhase.Downloading, byteProgress = RestoreByteProgress(1_000_000L, 4_000_000L))
                .stageBarFraction(),
        )
        assertEquals(
            0.5f,
            stageProgress(
                CloudRestorePhase.Downloading,
                itemProgress = RestoreItemProgress(completedItems = 1, totalItems = 2),
                byteProgress = RestoreByteProgress(1_000_000L, null),
            ).stageBarFraction(),
        )
        assertEquals(
            1f,
            stageProgress(
                CloudRestorePhase.Downloading,
                itemProgress = RestoreItemProgress(completedItems = 3, totalItems = 3),
                byteProgress = RestoreByteProgress(3_000_000L, 4_000_000L),
            ).stageBarFraction(),
        )
    }

    @Test
    fun stageBarFraction_usesPhotosForUploadAndIndeterminateElsewhere() {
        assertEquals(
            0.25f,
            stageProgress(CloudRestorePhase.Uploading, uploadProgress = RestoreUploadProgress(2, 8)).stageBarFraction(),
        )
        assertNull(stageProgress(CloudRestorePhase.Uploading).stageBarFraction())
        assertNull(stageProgress(CloudRestorePhase.Preparing).stageBarFraction())
        assertNull(stageProgress(CloudRestorePhase.Applying).stageBarFraction())
    }

    @Test
    fun uploadEta_waitsForARealUploadAndReuseCompletionsAddNoGap() {
        var nowMillis = 0L
        val estimator = BatchEtaEstimator { nowMillis }
        var previous: RestoreUploadProgress? = null
        fun step(progress: RestoreUploadProgress, atMillis: Long): BatchEta? {
            nowMillis = atMillis
            return uploadEtaAfter(estimator, previous, progress).also { previous = progress }
        }

        // Instant Reuse completions at the start: no real upload yet, so no ETA.
        assertNull(step(RestoreUploadProgress(0, 6, 0), 0L))
        assertNull(step(RestoreUploadProgress(2, 6, 2), 0L))
        // The first real upload at 10 s: 3 photos left at 10 s each.
        assertEquals(BatchEta.Seconds(30), step(RestoreUploadProgress(3, 6, 2), 10_000L))
        // A Reuse at 15 s leaves the gap list alone; 2 photos left at 10 s each.
        assertEquals(BatchEta.Seconds(20), step(RestoreUploadProgress(4, 6, 3), 15_000L))
        // One more real upload at 20 s: 1 photo left, two gaps of 10 s.
        assertEquals(BatchEta.Seconds(10), step(RestoreUploadProgress(5, 6, 3), 20_000L))
        // Nothing remains: no ETA.
        assertNull(step(RestoreUploadProgress(6, 6, 3), 25_000L))
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

    @Test
    fun stepLine_countsRealPhotosAndSaysFinishingAfterTheLastPhoto() {
        // Upload milestones from the photo loop, then the snapshot tail with every photo done.
        val upload = listOf(0, 1, 3).map { completed ->
            stageProgress(CloudRestorePhase.Uploading, uploadProgress = RestoreUploadProgress(completed, 3)).stepLine()
        }

        assertEquals(
            listOf(
                RestoreStepLine.PhotosUploaded(0, 3),
                RestoreStepLine.PhotosUploaded(1, 3),
                RestoreStepLine.Finishing,
            ),
            upload,
        )
        assertEquals(RestoreStepLine.Hidden, stageProgress(CloudRestorePhase.Uploading).stepLine())
        assertEquals(RestoreStepLine.Hidden, stageProgress(CloudRestorePhase.Downloading).stepLine())
        assertEquals(RestoreStepLine.Steps(remaining = 6, total = 8), stageProgress(CloudRestorePhase.Preparing).stepLine())
    }

    private fun stageProgress(
        phase: CloudRestorePhase,
        itemProgress: RestoreItemProgress? = null,
        byteProgress: RestoreByteProgress? = null,
        uploadProgress: RestoreUploadProgress? = null,
    ): CloudRestoreProgress = CloudRestoreProgress(
        phase = phase,
        completedWork = 2,
        totalWork = 8,
        itemProgress = itemProgress,
        byteProgress = byteProgress,
        uploadProgress = uploadProgress,
    )

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
