package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.RestoreByteProgress
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val MIN_ETA_SAMPLE_DURATION_MILLIS = 2_000L
private const val MIN_ETA_PROGRESS_BYTES = 64L * 1024L
private const val MAX_ETA_SECONDS = 24L * 60L * 60L
private const val MAX_ETA_SAMPLE_COUNT = 4

internal data class RestoreByteProgressText(
    val completedMegabytes: String,
    val totalMegabytes: String?,
)

internal fun RestoreByteProgress.toDisplayText(): RestoreByteProgressText = RestoreByteProgressText(
    completedMegabytes = completedBytes.formatRestoreMegabytes(),
    totalMegabytes = totalBytes?.formatRestoreMegabytes(),
)

internal fun Long.formatRestoreMegabytes(): String =
    String.format(Locale.US, "%.1f MB", coerceAtLeast(0L) / BYTES_PER_MEGABYTE)

internal data class RestoreEtaSample(
    val elapsedRealtimeMillis: Long,
    val completedBytes: Long,
    val totalBytes: Long,
)

internal data class RestoreEtaState(
    val samples: List<RestoreEtaSample> = emptyList(),
) {
    fun updated(progress: CloudRestoreProgress, elapsedRealtimeMillis: Long): RestoreEtaState {
        val byteProgress = progress.byteProgress
        val totalBytes = byteProgress?.totalBytes
        if (
            progress.status != CloudRestoreStatus.Running ||
            byteProgress == null ||
            totalBytes == null ||
            byteProgress.completedBytes >= totalBytes
        ) {
            return RestoreEtaState()
        }

        val sample = RestoreEtaSample(
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            completedBytes = byteProgress.completedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes,
        )
        val previousSample = samples.lastOrNull()
        if (
            previousSample != null &&
            (sample.completedBytes < previousSample.completedBytes || sample.totalBytes != previousSample.totalBytes)
        ) {
            return copy(samples = listOf(sample))
        }
        val nextSamples = (samples + sample)
            .distinctBy(RestoreEtaSample::elapsedRealtimeMillis)
            .takeLast(MAX_ETA_SAMPLE_COUNT)

        return copy(samples = nextSamples)
    }

    fun estimate(progress: CloudRestoreProgress): RestoreEta? {
        val byteProgress = progress.byteProgress ?: return null
        val totalBytes = byteProgress.totalBytes?.takeIf { it > 0L } ?: return null
        if (progress.status != CloudRestoreStatus.Running || byteProgress.completedBytes >= totalBytes) return null

        val latest = samples.lastOrNull() ?: return null
        if (latest.completedBytes != byteProgress.completedBytes || latest.totalBytes != totalBytes) return null
        val previous = samples.dropLast(1).lastOrNull() ?: return null
        if (
            latest.elapsedRealtimeMillis <= previous.elapsedRealtimeMillis ||
            latest.completedBytes <= previous.completedBytes
        ) {
            return null
        }
        val baseline = samples.firstOrNull { sample ->
            sample.elapsedRealtimeMillis < latest.elapsedRealtimeMillis &&
                sample.completedBytes < latest.completedBytes
        } ?: return null
        val elapsedMillis = latest.elapsedRealtimeMillis - baseline.elapsedRealtimeMillis
        val downloadedBytes = latest.completedBytes - baseline.completedBytes
        if (elapsedMillis < MIN_ETA_SAMPLE_DURATION_MILLIS || downloadedBytes < MIN_ETA_PROGRESS_BYTES) return null

        val remainingBytes = totalBytes - byteProgress.completedBytes.coerceIn(0L, totalBytes)
        if (remainingBytes <= 0L) return null

        val bytesPerSecond = downloadedBytes * 1_000.0 / elapsedMillis
        if (bytesPerSecond <= 0.0) return null

        val seconds = ceil(remainingBytes / bytesPerSecond).roundToLong()
        return seconds.takeIf { it in 1L..MAX_ETA_SECONDS }?.let(RestoreEta::fromSeconds)
    }
}

internal sealed interface RestoreEta {
    data object LessThanMinute : RestoreEta
    data class AboutMinutes(val minutes: Long) : RestoreEta

    companion object {
        fun fromSeconds(seconds: Long): RestoreEta = if (seconds < 60L) {
            LessThanMinute
        } else {
            AboutMinutes(minutes = ((seconds + 30L) / 60L).coerceAtLeast(1L))
        }
    }
}
