package com.gusanitolabs.robia

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.sync.WardrobeSyncOperation
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import com.gusanitolabs.robia.ui.RobiaApp
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

private const val EXTRA_PERFORMANCE_FIXTURE_URIS = "com.gusanitolabs.robia.PERFORMANCE_FIXTURE_URIS"
private const val EXTRA_PERFORMANCE_BATCH = "com.gusanitolabs.robia.PERFORMANCE_BATCH"
private const val DRIVE_AUTH_LOG_TAG = "RobiaDriveAuth"

class MainActivity : ComponentActivity() {
    private lateinit var authorizationClient: AuthorizationClient
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncGateway: WardrobeSyncOutboxProcessor

    private val driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        handleDriveAuthorizationResolution(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        authorizationClient = Identity.getAuthorizationClient(this)

        val app = application as RobiaApplication
        settingsRepository = app.settingsRepository
        syncGateway = app.syncGateway

        setContent {
            RobiaApp(
                settingsRepository = settingsRepository,
                wardrobeRepository = app.wardrobeRepository,
                tagRepository = app.tagRepository,
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
        setCloudAuthorizationInFlight(true)

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
                } else if (result.hasResolution()) {
                    logDriveAuthorizationOutcome(
                        outcome = "resolution_missing",
                        hasResolution = true,
                    )
                    showCloudSetupLaunchFailure()
                } else {
                    persistDriveAuthorizationResult(result)
                }
            }
            .addOnFailureListener { showCloudSetupLaunchFailure() }
    }

    private fun handleDriveAuthorizationResolution(resultCode: Int, data: Intent?) {
        setCloudAuthorizationInFlight(false)
        if (data == null) {
            val outcome = if (resultCode == Activity.RESULT_CANCELED) "cancelled" else "missing_data"
            logDriveAuthorizationOutcome(
                outcome = outcome,
                resultCode = resultCode,
                dataPresent = false,
            )
            lifecycleScope.launch {
                settingsRepository.setDriveSyncConnectionStatus(DriveSyncConnectionStatus.Disconnected)
            }
            showCloudSetupFailure(
                if (resultCode == Activity.RESULT_CANCELED) {
                    R.string.cloud_setup_cancelled
                } else {
                    R.string.cloud_setup_missing_result
                },
            )
            return
        }

        runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .onSuccess { result ->
                logDriveAuthorizationOutcome(
                    outcome = "resolution_parsed",
                    resultCode = resultCode,
                    dataPresent = true,
                    grantedScopes = result.grantedScopes,
                    hasResolution = result.hasResolution(),
                    tokenPresent = !result.accessToken.isNullOrBlank(),
                )
                persistDriveAuthorizationResult(result)
            }
            .onFailure {
                logDriveAuthorizationOutcome(
                    outcome = "parse_error",
                    resultCode = resultCode,
                    dataPresent = true,
                )
                lifecycleScope.launch {
                    settingsRepository.setDriveSyncConnectionStatus(DriveSyncConnectionStatus.Disconnected)
                }
                showCloudSetupFailure(R.string.cloud_setup_parse_failed)
            }
    }

    private fun persistDriveAuthorizationResult(result: AuthorizationResult) {
        setCloudAuthorizationInFlight(false)
        val grantedDriveScope = result.grantedScopes.any { scope ->
            scope == DRIVE_APPDATA_SCOPE
        }
        val tokenPresent = !result.accessToken.isNullOrBlank()
        val authorized = grantedDriveScope && tokenPresent
        // Armed before the status write, so the model prompt waits from the chooser result until the import cycle ends.
        setCloudImportArmed(authorized)
        logDriveAuthorizationOutcome(
            outcome = if (authorized) "authorized" else if (grantedDriveScope) "token_missing" else "scope_denied",
            grantedScopes = result.grantedScopes,
            hasResolution = result.hasResolution(),
            tokenPresent = tokenPresent,
        )
        lifecycleScope.launch {
            settingsRepository.setDriveSyncConnectionStatus(
                if (authorized) {
                    DriveSyncConnectionStatus.Connected
                } else {
                    DriveSyncConnectionStatus.Disconnected
                },
            )
            if (authorized) {
                syncGateway.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))
            }
        }.invokeOnCompletion { setCloudImportArmed(false) }
        if (!authorized) {
            showCloudSetupFailure(
                if (grantedDriveScope) {
                    R.string.cloud_setup_token_missing
                } else {
                    R.string.cloud_setup_scope_denied
                },
            )
        }
    }

    private fun showCloudSetupLaunchFailure() {
        setCloudAuthorizationInFlight(false)
        showCloudSetupFailure(R.string.cloud_setup_launch_failed)
    }

    private fun setCloudAuthorizationInFlight(inFlight: Boolean) {
        (application as RobiaApplication).setCloudAuthorizationInFlight(inFlight)
    }

    private fun setCloudImportArmed(armed: Boolean) {
        (application as RobiaApplication).setCloudImportArmed(armed)
    }

    private fun showCloudSetupFailure(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun logDriveAuthorizationOutcome(
        outcome: String,
        resultCode: Int? = null,
        dataPresent: Boolean? = null,
        grantedScopes: Iterable<String> = emptyList(),
        hasResolution: Boolean? = null,
        tokenPresent: Boolean? = null,
    ) {
        Log.i(
            DRIVE_AUTH_LOG_TAG,
            buildString {
                append("Google Drive authorization outcome=").append(outcome)
                resultCode?.let { append(" resultCode=").append(it) }
                dataPresent?.let { append(" dataPresent=").append(it) }
                val scopeNames = grantedScopes.toList()
                if (scopeNames.isNotEmpty()) append(" grantedScopes=").append(scopeNames.joinToString(","))
                hasResolution?.let { append(" hasResolution=").append(it) }
                tokenPresent?.let { append(" tokenPresent=").append(it) }
            },
        )
    }
}
