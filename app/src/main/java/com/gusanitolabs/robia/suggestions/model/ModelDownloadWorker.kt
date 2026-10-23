package com.gusanitolabs.robia.suggestions.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gusanitolabs.robia.RobiaApplication
import kotlinx.coroutines.flow.first

/**
 * Runs the model download as unique work; no foreground service or notification (TS§3.4). The same worker runs the
 * precise cutout pack when [KEY_CUTOUT_PACK] is set (BAT2).
 */
class ModelDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as RobiaApplication
        return if (inputData.getBoolean(KEY_CUTOUT_PACK, false)) downloadCutoutPack(app) else downloadModel(app)
    }

    private suspend fun downloadModel(app: RobiaApplication): Result {
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

    /** A pack held back for Wi-Fi retries instead of ending, so it resumes by itself when the network returns. */
    private suspend fun downloadCutoutPack(app: RobiaApplication): Result {
        val allowMobileData = inputData.getBoolean(KEY_ALLOW_MOBILE_DATA, false)
        return when (val outcome = app.cutoutPack.download(allowMetered = allowMobileData)) {
            is ModelState.Ready -> Result.success()
            ModelState.WaitingForWifi -> Result.retry()
            is ModelState.Failed -> when (outcome.reason) {
                ModelFailure.CorruptDownload, ModelFailure.NoSpace -> Result.failure()
                else -> Result.retry()
            }
            else -> Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "robia-model-download"

        /** Unique work of the precise cutout pack, separate from [WORK_NAME] so Cancel never touches the embedding model. */
        const val CUTOUT_PACK_WORK_NAME = "cutout_pack_download"
        private const val KEY_CUTOUT_PACK = "cutout_pack"
        private const val KEY_ALLOW_MOBILE_DATA = "allow_mobile_data"

        /** Wi-Fi only unless [allowMobileData] was confirmed for this download. */
        fun enqueue(context: Context, allowMobileData: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints(allowMobileData))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Queues the cutout pack download: unmetered unless [allowMobileData]. It resumes from the partial files, so a
         * queued run also starts again by itself when Wi-Fi returns. REPLACE lets a mobile-data choice apply to work
         * that is already waiting.
         */
        fun enqueueCutoutPack(context: Context, allowMobileData: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints(allowMobileData))
                .setInputData(
                    Data.Builder()
                        .putBoolean(KEY_CUTOUT_PACK, true)
                        .putBoolean(KEY_ALLOW_MOBILE_DATA, allowMobileData)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(CUTOUT_PACK_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Stops the pack download, queued or running. Partial files stay, so the next enqueue resumes them. */
        fun cancelCutoutPack(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(CUTOUT_PACK_WORK_NAME)
        }

        private fun constraints(allowMobileData: Boolean): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .build()
    }
}
