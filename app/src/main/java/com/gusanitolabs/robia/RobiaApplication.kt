package com.gusanitolabs.robia

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.engine.EmbeddingEngineHolder
import com.gusanitolabs.robia.suggestions.engine.LiteRtNativeEmbedder
import com.gusanitolabs.robia.suggestions.model.HttpModelSource
import com.gusanitolabs.robia.suggestions.model.ModelStore
import com.gusanitolabs.robia.sync.FileRestoreSyncLogRepository
import com.gusanitolabs.robia.sync.GoogleDriveWardrobeRepository
import com.gusanitolabs.robia.sync.LocalWardrobeSyncSnapshotRepository
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

internal val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class RobiaApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val modelStore by lazy {
        ModelStore(
            rootDir = File(filesDir, "models"),
            source = HttpModelSource(EmbeddingRecipe.MODEL_URL),
            isUnmetered = { isUnmeteredNetworkActive() },
        )
    }

    val embeddingEngineHolder by lazy {
        EmbeddingEngineHolder(
            modelStore = modelStore,
            scope = appScope,
            factory = { modelPath -> LiteRtNativeEmbedder(cacheDir = cacheDir.absolutePath, modelPath = modelPath) },
        )
    }

    val database by lazy { RobiaDatabase.getInstance(this) }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(settingsDataStore)
    }

    val wardrobeRepository by lazy { LocalWardrobeRepository(database.wardrobeDao()) }

    val tagRepository by lazy { LocalTagRepository(database.tagDao(), database.syncTombstoneDao()) }

    val syncSnapshotRepository by lazy {
        LocalWardrobeSyncSnapshotRepository(
            database = database,
            wardrobeDao = database.wardrobeDao(),
            tagDao = database.tagDao(),
            syncTombstoneDao = database.syncTombstoneDao(),
        )
    }

    val restoreSyncLogRepository by lazy { FileRestoreSyncLogRepository(applicationContext) }

    val syncGateway: WardrobeSyncOutboxProcessor by lazy {
        WardrobeSyncOutboxProcessor(
            settingsRepository = settingsRepository,
            wardrobeRepository = wardrobeRepository,
            snapshotRepository = syncSnapshotRepository,
            driveRepository = GoogleDriveWardrobeRepository(
                context = applicationContext,
                authorizationClient = Identity.getAuthorizationClient(this),
                driveScope = Scope(DRIVE_APPDATA_SCOPE),
            ),
            restoreSyncLogRepository = restoreSyncLogRepository,
            scope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) embeddingEngineHolder.release()
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) embeddingEngineHolder.release()
    }

    private fun isUnmeteredNetworkActive(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
