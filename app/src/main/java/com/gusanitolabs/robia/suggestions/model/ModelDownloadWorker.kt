package com.gusanitolabs.robia.suggestions.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gusanitolabs.robia.RobiaApplication
import kotlinx.coroutines.flow.first

/** Runs the model download as unique work; no foreground service or notification (TS§3.4). */
class ModelDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RobiaApplication
        val settings = app.settingsRepository
        val mobileDataApproved = settings.modelMobileDataApproved.first()
        return when (val outcome = app.modelStore.download(allowMetered = mobileDataApproved)) {
            is ModelState.Ready -> {
                if (mobileDataApproved) settings.setModelMobileDataApproved(false)
                Result.success()
            }
            ModelState.WaitingForWifi -> Result.success()
            is ModelState.Failed -> when (outcome.reason) {
                ModelFailure.CorruptDownload, ModelFailure.NoSpace -> Result.failure()
                else -> Result.retry()
            }
            else -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "robia-model-download"

        /** Wi-Fi only unless [allowMobileData] was confirmed for this download. */
        fun enqueue(context: Context, allowMobileData: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .build()
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
