package com.gusanitolabs.robia

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.preferencesDataStore
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.ui.RobiaApp
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

private val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

class MainActivity : ComponentActivity() {
    private lateinit var authorizationClient: AuthorizationClient

    private val driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
                .onFailure { showCloudSetupLaunchFailure() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        authorizationClient = Identity.getAuthorizationClient(this)

        val database = RobiaDatabase.getInstance(applicationContext)
        val settingsRepository = DataStoreSettingsRepository(settingsDataStore)
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
                }
            }
            .addOnFailureListener { showCloudSetupLaunchFailure() }
    }

    private fun showCloudSetupLaunchFailure() {
        Toast.makeText(this, R.string.cloud_setup_launch_failed, Toast.LENGTH_LONG).show()
    }
}
