package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.sync.CloudRestorePhase
import com.gusanitolabs.robia.sync.CloudRestoreProgress
import com.gusanitolabs.robia.sync.CloudRestoreStatus
import com.gusanitolabs.robia.sync.RestoreByteProgress
import com.gusanitolabs.robia.sync.RestoreUploadProgress
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val MIN_ETA_SAMPLE_DURATION_MILLIS = 2_000L
private const val MIN_ETA_PROGRESS_BYTES = 64L * 1024L
private const val MAX_ETA_SECONDS = 24L * 60L * 60L
private const val ETA_RATE_SMOOTHING = 0.2
private const val ETA_MIN_CHANGE_INTERVAL_MILLIS = 5_000L

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

/**
 * Smoothed download-rate estimate. The rate is seeded from the cumulative transfer since the first
 * sample that passed 2 s and 64 KB, then updated by EWMA. Samples without new bytes keep the last
 * rate, and the shown value changes at most every 5 s and never increases.
 */
internal data class RestoreEtaState(
    val anchor: RestoreEtaSample? = null,
    val lastSample: RestoreEtaSample? = null,
    val bytesPerSecond: Double? = null,
    val shown: RestoreEta? = null,
    val shownAtElapsedRealtimeMillis: Long? = null,
) {
    fun updated(progress: CloudRestoreProgress, elapsedRealtimeMillis: Long): RestoreEtaState {
        val byteProgress = progress.byteProgress
        val totalBytes = byteProgress?.totalBytes?.takeIf { it > 0L }
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
        val base = if (
            anchor == null ||
            lastSample == null ||
            anchor.totalBytes != totalBytes ||
            sample.completedBytes < lastSample.completedBytes ||
            sample.elapsedRealtimeMillis <= lastSample.elapsedRealtimeMillis
        ) {
            RestoreEtaState(anchor = sample, lastSample = sample)
        } else {
            this
        }
        val start = base.anchor ?: sample
        val previous = base.lastSample ?: sample
        val newBytes = sample.completedBytes - previous.completedBytes
        val smoothedRate = base.bytesPerSecond
        val rate = when {
            newBytes <= 0L -> smoothedRate
            smoothedRate == null -> {
                val elapsed = sample.elapsedRealtimeMillis - start.elapsedRealtimeMillis
                val transferred = sample.completedBytes - start.completedBytes
                if (elapsed >= MIN_ETA_SAMPLE_DURATION_MILLIS && transferred >= MIN_ETA_PROGRESS_BYTES) {
                    transferred * 1_000.0 / elapsed
                } else {
                    null
                }
            }
            else -> {
                val instantaneous = newBytes * 1_000.0 / (sample.elapsedRealtimeMillis - previous.elapsedRealtimeMillis)
                ETA_RATE_SMOOTHING * instantaneous + (1 - ETA_RATE_SMOOTHING) * smoothedRate
            }
        }?.takeIf { it > 0.0 }

        val remainingBytes = totalBytes - sample.completedBytes
        val candidate = rate?.let { bytesPerSecond ->
            ceil(remainingBytes / bytesPerSecond).roundToLong()
                .takeIf { it in 1L..MAX_ETA_SECONDS }
                ?.let(RestoreEta::fromSeconds)
        }
        val current = base.shown
        val shownAt = base.shownAtElapsedRealtimeMillis
        val changeAllowed = shownAt == null || elapsedRealtimeMillis - shownAt >= ETA_MIN_CHANGE_INTERVAL_MILLIS
        val shouldShowCandidate = candidate != null &&
            (current == null || (candidate.rank <= current.rank && candidate != current && changeAllowed))

        return base.copy(
            lastSample = sample,
            bytesPerSecond = rate,
            shown = if (shouldShowCandidate) candidate else current,
            shownAtElapsedRealtimeMillis = if (shouldShowCandidate) elapsedRealtimeMillis else shownAt,
        )
    }

    /** The last shown estimate. It is retained across updates that carry no new bytes, so the line never blinks out. */
    fun estimate(progress: CloudRestoreProgress): RestoreEta? {
        val byteProgress = progress.byteProgress ?: return null
        val totalBytes = byteProgress.totalBytes?.takeIf { it > 0L } ?: return null
        if (progress.status != CloudRestoreStatus.Running || byteProgress.completedBytes >= totalBytes) return null
        return shown
    }
}

internal sealed interface RestoreEta {
    /** Ordering key: a shown estimate may only stay or drop, never rise. */
    val rank: Long

    data object LessThanMinute : RestoreEta {
        override val rank: Long = 0L
    }

    data class AboutMinutes(val minutes: Long) : RestoreEta {
        override val rank: Long get() = minutes
    }

    companion object {
        /** Under 60 s: "Less than a minute". Under 10 min: nearest minute. Otherwise: nearest 5 minutes. */
        fun fromSeconds(seconds: Long): RestoreEta = when {
            seconds < 60L -> LessThanMinute
            seconds < 600L -> AboutMinutes(minutes = ((seconds + 30L) / 60L).coerceAtLeast(1L))
            else -> AboutMinutes(minutes = ((seconds + 150L) / 300L) * 5L)
        }
    }
}

/** The line under the phase label. The fixed 12-step count only describes milestones, so real work shows real counts. */
internal sealed interface RestoreStepLine {
    data object Hidden : RestoreStepLine
    data class Steps(val remaining: Int, val total: Int) : RestoreStepLine
    data class PhotosUploaded(val completed: Int, val total: Int) : RestoreStepLine
    data object Finishing : RestoreStepLine
}

/**
 * Downloading shows its own photo and byte lines. Uploading counts photos, and reads "Finishing" once the
 * photo loop is done and the snapshot upload (the tail) is still running.
 */
internal fun CloudRestoreProgress.stepLine(): RestoreStepLine = when (phase) {
    CloudRestorePhase.Downloading -> RestoreStepLine.Hidden
    CloudRestorePhase.Uploading -> uploadProgress?.let { upload ->
        if (upload.completedPhotos >= upload.totalPhotos) {
            RestoreStepLine.Finishing
        } else {
            RestoreStepLine.PhotosUploaded(upload.completedPhotos, upload.totalPhotos)
        }
    } ?: RestoreStepLine.Hidden
    else -> RestoreStepLine.Steps(remaining = remainingWork, total = totalWork)
}

/**
 * Determinate fraction for the bar of the current stage, or null for an indeterminate indicator.
 * Download: bytes, or items when a size is unknown or every item has finished. Upload: photos.
 */
internal fun CloudRestoreProgress.stageBarFraction(): Float? {
    val itemFraction = itemProgress?.takeIf { it.totalItems > 0 }?.let { items ->
        items.completedItems.coerceIn(0, items.totalItems).toFloat() / items.totalItems
    }
    return when (phase) {
        CloudRestorePhase.Downloading -> if (itemFraction == 1f) itemFraction else byteProgress?.fraction ?: itemFraction
        CloudRestorePhase.Uploading -> uploadProgress?.takeIf { it.totalPhotos > 0 }?.let { upload ->
            upload.completedPhotos.coerceIn(0, upload.totalPhotos).toFloat() / upload.totalPhotos
        }
        else -> null
    }
}

/**
 * ETA for the photo loop of "Updating cloud backup". Each real upload that finished since [previous] is one gap for
 * [estimator]; a Reuse finishes at once and adds no gap. Null until the first real upload completes, and once nothing
 * remains. The estimate is a batch estimate: remaining = total - completed.
 */
internal fun uploadEtaAfter(
    estimator: BatchEtaEstimator,
    previous: RestoreUploadProgress?,
    current: RestoreUploadProgress,
): BatchEta? {
    val realUploadsBefore = (previous?.completedPhotos ?: 0) - (previous?.reusedPhotos ?: 0)
    val realUploadsNow = current.completedPhotos - current.reusedPhotos
    repeat((realUploadsNow - realUploadsBefore).coerceAtLeast(0)) { estimator.onTerminal() }
    val remaining = current.totalPhotos - current.completedPhotos
    return if (remaining > 0) estimator.estimate(remaining) else null
}
