package com.gusanitolabs.robia.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Asks for a background sync run; the processor decides what, if anything, to do. */
interface SyncWorkScheduler {
    fun requestSync()
    fun ensurePeriodic()
    fun cancelAll()
}

object NoOpSyncWorkScheduler : SyncWorkScheduler {
    override fun requestSync() = Unit
    override fun ensurePeriodic() = Unit
    override fun cancelAll() = Unit
}

/** WorkManager implementation: one unique run for pending work, plus a 6-hour safety net while connected. */
class DriveSyncScheduler(private val context: Context) : SyncWorkScheduler {
    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(connectedConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(SYNC_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun connectedConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val SYNC_WORK_NAME = "robia-drive-sync"
        const val PERIODIC_WORK_NAME = "robia-drive-sync-periodic"
        private const val BACKOFF_SECONDS = 30L
        private const val PERIODIC_HOURS = 6L
    }
}
