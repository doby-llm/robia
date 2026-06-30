package com.gusanitolabs.robia

import android.content.Context
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
import com.gusanitolabs.robia.ui.RobiaApp
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class MainActivity : ComponentActivity() {
    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var settingsRepository: SettingsRepository

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

        setContent {
            RobiaApp(
                settingsRepository = settingsRepository,
                wardrobeRepository = wardrobeRepository,
                tagRepository = tagRepository,
                onRequestCloudSetup = ::requestGoogleDriveAuthorization,
            )
        }
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
        val grantedDriveScope = result.grantedScopes.any { scope -> scope == DRIVE_APPDATA_SCOPE }
        lifecycleScope.launch {
            settingsRepository.setDriveSyncConnectionStatus(
                if (grantedDriveScope) {
                    DriveSyncConnectionStatus.Connected
                } else {
                    DriveSyncConnectionStatus.Disconnected
                },
            )
        }
    }

    private fun showCloudSetupLaunchFailure() {
        Toast.makeText(this, R.string.cloud_setup_launch_failed, Toast.LENGTH_LONG).show()
    }
}
