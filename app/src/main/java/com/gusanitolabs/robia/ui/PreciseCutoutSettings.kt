package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.suggestions.model.ModelFailure
import com.gusanitolabs.robia.suggestions.model.ModelState
import kotlinx.coroutines.launch

private const val BYTES_PER_MB = 1_048_576L

/** Whole percent of the pack download for the Settings row; null when nothing is moving. Verifying counts as 100. */
internal fun cutoutDownloadPercent(state: ModelState): Int? = when (state) {
    is ModelState.Downloading ->
        if (state.total > 0) (state.bytes * 100 / state.total).toInt().coerceIn(0, 100) else 0
    ModelState.Verifying -> 100
    else -> null
}

/** The switch and the Settings value read On only when the pack is ready and the setting is on. */
internal fun preciseCutoutOn(state: ModelState, enabled: Boolean): Boolean = state is ModelState.Ready && enabled

/**
 * Sub-screen behind Settings > Smart features > Precise cutout. The download follows the Smart suggestions model:
 * Wi-Fi first, then the mobile-data confirmation. Downloads and deletes run on the app scope, so leaving the screen
 * does not stop them.
 */
@Composable
internal fun PreciseCutoutSettingsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val settings by app.settingsRepository.settings.collectAsState(initial = RobiaSettings())
    val packState by app.cutoutPack.state.collectAsState(initial = ModelState.NotDownloaded)
    var showMobileDataConfirm by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val packReady = packState is ModelState.Ready
    val switchOn = preciseCutoutOn(packState, settings.preciseCutoutEnabled)
    // Without allowMetered the store holds the pack in WaitingForWifi on a metered network; the user then chooses mobile data.
    val startDownload: (Boolean) -> Unit = { allowMetered ->
        app.appScope.launch { app.cutoutPack.download(allowMetered = allowMetered) }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSection(title = stringResource(R.string.precise_cutout_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.precise_cutout_body))
                    when (val state = packState) {
                        ModelState.NotDownloaded -> Button(onClick = { startDownload(false) }) {
                            Text(stringResource(R.string.precise_cutout_download))
                        }
                        ModelState.WaitingForWifi -> {
                            Text(stringResource(R.string.model_state_waiting_wifi))
                            Button(onClick = { startDownload(false) }) {
                                Text(stringResource(R.string.precise_cutout_download))
                            }
                            OutlinedButton(onClick = { showMobileDataConfirm = true }) {
                                Text(stringResource(R.string.model_use_mobile_data))
                            }
                        }
                        is ModelState.Downloading -> {
                            Text(
                                stringResource(
                                    R.string.model_state_downloading,
                                    state.bytes / BYTES_PER_MB,
                                    state.total / BYTES_PER_MB,
                                ),
                            )
                            LinearProgressIndicator(
                                progress = { if (state.total > 0) state.bytes.toFloat() / state.total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ModelState.Verifying -> Text(stringResource(R.string.model_state_verifying))
                        is ModelState.Ready -> {
                            Text(stringResource(R.string.precise_cutout_ready))
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(stringResource(R.string.precise_cutout_delete))
                            }
                        }
                        is ModelState.Failed -> {
                            Text(stringResource(cutoutFailureRes(state.reason)))
                            Button(onClick = { startDownload(false) }) {
                                Text(stringResource(R.string.batch_retry))
                            }
                        }
                    }
                }
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.precise_cutout_use_switch)) },
                    trailingContent = {
                        Switch(
                            checked = switchOn,
                            enabled = packReady,
                            onCheckedChange = { enabled ->
                                scope.launch { app.settingsRepository.setPreciseCutoutEnabled(enabled) }
                            },
                        )
                    },
                    modifier = Modifier.clickable(enabled = packReady) {
                        scope.launch { app.settingsRepository.setPreciseCutoutEnabled(!switchOn) }
                    },
                )
            }
        }
    }
    if (showMobileDataConfirm) {
        AlertDialog(
            onDismissRequest = { showMobileDataConfirm = false },
            title = { Text(stringResource(R.string.model_mobile_title)) },
            text = { Text(stringResource(R.string.precise_cutout_mobile_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMobileDataConfirm = false
                        startDownload(true)
                    },
                ) { Text(stringResource(R.string.precise_cutout_mobile_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showMobileDataConfirm = false }) {
                    Text(stringResource(R.string.model_mobile_wait))
                }
            },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.precise_cutout_delete_title)) },
            text = { Text(stringResource(R.string.precise_cutout_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        // CutoutPack.delete expects the precise engine switched off first; a new download then starts off too.
                        app.appScope.launch {
                            app.settingsRepository.setPreciseCutoutEnabled(false)
                            app.cutoutPack.delete()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@StringRes
private fun cutoutFailureRes(reason: ModelFailure): Int = when (reason) {
    ModelFailure.NoSpace -> R.string.model_state_failed_space
    ModelFailure.CorruptDownload -> R.string.model_state_failed_corrupt
    ModelFailure.NoNetwork, is ModelFailure.Http -> R.string.model_state_failed_network
}
