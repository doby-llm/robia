package com.gusanitolabs.robia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.sync.LocalWardrobeSyncSnapshotRepository
import com.gusanitolabs.robia.sync.FileRestoreSyncLogRepository
import com.gusanitolabs.robia.sync.GoogleDriveWardrobeRepository
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import com.gusanitolabs.robia.ui.RobiaApp
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val EXTRA_PERFORMANCE_FIXTURE_URIS = "com.gusanitolabs.robia.PERFORMANCE_FIXTURE_URIS"
private const val EXTRA_PERFORMANCE_BATCH = "com.gusanitolabs.robia.PERFORMANCE_BATCH"

class MainActivity : ComponentActivity() {
    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncGateway: WardrobeSyncOutboxProcessor

    private val driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
                .onSuccess(::persistDriveAuthorizationResult)
                .onFailure { showCloudSetupLaunchFailure() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        authorizationClient = Identity.getAuthorizationClient(this)

        val database = RobiaDatabase.getInstance(applicationContext)
        settingsRepository = DataStoreSettingsRepository(settingsDataStore)
        val wardrobeRepository = LocalWardrobeRepository(database.wardrobeDao())
        val tagRepository = LocalTagRepository(database.tagDao(), database.syncTombstoneDao())
        val syncSnapshotRepository = LocalWardrobeSyncSnapshotRepository(
            database = database,
            wardrobeDao = database.wardrobeDao(),
            tagDao = database.tagDao(),
            syncTombstoneDao = database.syncTombstoneDao(),
        )
        syncGateway = WardrobeSyncOutboxProcessor(
            settingsRepository = settingsRepository,
            wardrobeRepository = wardrobeRepository,
            snapshotRepository = syncSnapshotRepository,
            driveRepository = GoogleDriveWardrobeRepository(
                context = applicationContext,
                authorizationClient = authorizationClient,
                driveScope = Scope(DRIVE_APPDATA_SCOPE),
            ),
            restoreSyncLogRepository = FileRestoreSyncLogRepository(applicationContext),
            scope = lifecycleScope,
        )

        setContent {
            RobiaApp(
                settingsRepository = settingsRepository,
                wardrobeRepository = wardrobeRepository,
                tagRepository = tagRepository,
                syncGateway = syncGateway,
                onRequestCloudSetup = ::requestGoogleDriveAuthorization,
                performanceFixtureUris = performanceFixtureUris(),
                performanceBatch = intent.getBooleanExtra(EXTRA_PERFORMANCE_BATCH, false),
            )
        }
    }

    /** Debug-only CI seam; release builds cannot populate synthetic benchmark data. */
    private fun performanceFixtureUris(): List<String> =
        if (BuildConfig.DEBUG) intent.performanceFixtureUriStrings(EXTRA_PERFORMANCE_FIXTURE_URIS) else emptyList()

    @Suppress("DEPRECATION")
    private fun Intent.performanceFixtureUriStrings(extraName: String): List<String> {
        // Read the raw debug extra so adb --esa String[] and legacy ArrayList<String>
        // both avoid typed-getter ClassCastExceptions.
        return when (val extra = extras?.get(extraName)) {
            is Array<*> -> extra.toStringListOrEmpty()
            is ArrayList<*> -> extra.toStringListOrEmpty()
            else -> emptyList()
        }
    }

    private fun Array<*>.toStringListOrEmpty(): List<String> = asList().toStringListOrEmpty()

    private fun Iterable<*>.toStringListOrEmpty(): List<String> {
        val strings = mutableListOf<String>()
        for (value in this) {
            val stringValue = value as? String ?: return emptyList()
            strings += stringValue
        }
        return strings
    }

    private fun requestGoogleDriveAuthorization() {
        lifecycleScope.launch { settingsRepository.markCloudSetupPromptInteracted() }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                if (result.hasResolution() && pendingIntent != null) {
                    driveAuthorizationLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    )
                } else {
                    persistDriveAuthorizationResult(result)
                }
            }
            .addOnFailureListener { showCloudSetupLaunchFailure() }
    }

    private fun persistDriveAuthorizationResult(result: AuthorizationResult) {
        val grantedDriveScope = result.grantedScopes.any { scope ->
            scope == DRIVE_APPDATA_SCOPE
        }
        lifecycleScope.launch {
            settingsRepository.setDriveSyncConnectionStatus(
                if (grantedDriveScope) {
                    DriveSyncConnectionStatus.Connected
                } else {
                    DriveSyncConnectionStatus.Disconnected
                },
            )
            if (grantedDriveScope) {
                syncGateway.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))
            }
        }
    }

    private fun showCloudSetupLaunchFailure() {
        Toast.makeText(this, R.string.cloud_setup_launch_failed, Toast.LENGTH_LONG).show()
    }
}
