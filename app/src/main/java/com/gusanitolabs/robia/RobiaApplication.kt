package com.gusanitolabs.robia

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.sync.FileRestoreSyncLogRepository
import com.gusanitolabs.robia.sync.GoogleDriveWardrobeRepository
import com.gusanitolabs.robia.sync.LocalWardrobeSyncSnapshotRepository
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class RobiaApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
}
