package com.gusanitolabs.robia.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gusanitolabs.robia.RobiaApplication

/** Background entry point; WorkManager owns the backoff when the run reports [SyncRunResult.Retry]. */
class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val processor = (applicationContext as RobiaApplication).syncGateway
        // Periodic runs carry no input data; every one-time request names its reason.
        val reason = inputData.getString(DriveSyncScheduler.REASON_KEY) ?: "periodic"
        return when (processor.runOnce(SyncTrigger.Background, reason)) {
            SyncRunResult.Retry -> Result.retry()
            else -> Result.success()
        }
    }
}
