package com.gusanitolabs.robia

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.suggestions.EmbeddingBackfill
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import com.gusanitolabs.robia.suggestions.engine.EmbeddingEngineHolder
import com.gusanitolabs.robia.suggestions.engine.LiteRtNativeEmbedder
import com.gusanitolabs.robia.suggestions.model.HttpModelSource
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.suggestions.model.ModelStore
import com.gusanitolabs.robia.sync.DriveSyncScheduler
import com.gusanitolabs.robia.sync.FileRestoreSyncLogRepository
import com.gusanitolabs.robia.sync.GoogleDriveWardrobeRepository
import com.gusanitolabs.robia.sync.LocalWardrobeSyncSnapshotRepository
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

internal val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class RobiaApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val onlineState = MutableStateFlow(false)

    /** Whether the device has an internet network right now; the cloud sync indicator shows "Waiting for connection" without one. */
    val online: StateFlow<Boolean> = onlineState.asStateFlow()

    private val cloudAuthorizationInFlightState = MutableStateFlow(false)

    /** True while the Google account chooser or its result is pending; first-run prompts wait behind it. In memory only. */
    val cloudAuthorizationInFlight: StateFlow<Boolean> = cloudAuthorizationInFlightState.asStateFlow()

    fun setCloudAuthorizationInFlight(inFlight: Boolean) {
        cloudAuthorizationInFlightState.value = inFlight
    }

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

    val embeddingRepository by lazy { EmbeddingRepository(database.embeddingDao()) }

    /** Computes missing and stale vectors in the background while the app is open (TS§3.8). */
    val embeddingBackfill by lazy {
        EmbeddingBackfill(
            scope = appScope,
            embedder = embeddingEngineHolder,
            isModelReady = { modelStore.state.value is ModelState.Ready },
            encodeImage = { photoUri -> ImageInputEncoder.encode(this, Uri.parse(photoUri)) },
            loadGarments = { wardrobeRepository.observeActiveItems().first() },
            loadStoredRows = { database.embeddingDao().getForRecipe(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID) },
            saveVector = { garmentId, kind, vector, inputHash, durationMs ->
                embeddingRepository.upsert(garmentId, kind, vector, inputHash, durationMs)
            },
            deleteVector = { garmentId, kind -> embeddingRepository.delete(garmentId, kind) },
            // Each new vector is pending upload; the sync cycle sends it without changing any garment revision.
            requestSync = { reason -> syncGateway.requestSyncInBackground(reason) },
        )
    }

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
            syncWorkScheduler = DriveSyncScheduler(applicationContext),
            scope = appScope,
            embeddingRepository = embeddingRepository,
            onRestoreImported = { embeddingBackfill.resume() },
        )
    }

    override fun onCreate() {
        super.onCreate()
        registerConnectivityCallback()
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> embeddingBackfill.resume()
                Lifecycle.Event.ON_STOP -> {
                    embeddingBackfill.pause()
                    embeddingEngineHolder.release()
                }
                else -> Unit
            }
        })
        appScope.launch {
            modelStore.state.collect { state -> if (state is ModelState.Ready) embeddingBackfill.resume() }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) embeddingEngineHolder.release()
    }

    private fun registerConnectivityCallback() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        onlineState.value = connectivity.hasActiveInternet()
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            // Each callback re-reads the active network: onLost can arrive after the replacement network was already announced.
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                onlineState.value = connectivity.hasActiveInternet()
            }

            override fun onLost(network: Network) {
                onlineState.value = connectivity.hasActiveInternet()
            }
        })
    }

    private fun ConnectivityManager.hasActiveInternet(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isUnmeteredNetworkActive(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
