package com.gusanitolabs.robia.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.suggestions.model.ModelDownloadWorker
import com.gusanitolabs.robia.suggestions.model.ModelFailure
import com.gusanitolabs.robia.suggestions.model.ModelState

private const val BYTES_PER_MB = 1_048_576L

/** The one-time prompt appears only while the model is missing and the prompt has not been answered yet. */
fun shouldShowModelPrompt(promptShown: Boolean, state: ModelState): Boolean =
    !promptShown && state is ModelState.NotDownloaded

/** The Browse search field exists only while the model is Ready; otherwise a sparkle button opens [ModelPromptDialog]. */
fun browseSearchVisible(state: ModelState): Boolean = state is ModelState.Ready

/** A queued download that WorkManager holds back for network constraints is shown as waiting for Wi-Fi. */
fun settingsModelState(stored: ModelState, queued: Boolean): ModelState =
    if (stored is ModelState.NotDownloaded && queued) ModelState.WaitingForWifi else stored

/** [restart] cancels queued work first so a new network policy (mobile data) takes effect. */
fun startModelDownload(context: Context, allowMobileData: Boolean = false, restart: Boolean = false) {
    if (restart) WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    ModelDownloadWorker.enqueue(context, allowMobileData)
}

fun cancelModelDownload(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
}

@Composable
fun ModelPromptDialog(
    onLater: () -> Unit,
    onDownloadNow: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.model_prompt_title)) },
        text = {
            Column {
                Text(stringResource(R.string.model_prompt_body))
                Text(
                    text = stringResource(R.string.model_prompt_size),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownloadNow) { Text(stringResource(R.string.model_prompt_now)) }
        },
        dismissButton = {
            Row {
                // Mobile data is chosen in Settings, so a held-back (WaitingForWifi) download points there.
                onOpenSettings?.let { open ->
                    TextButton(onClick = open) { Text(stringResource(R.string.settings)) }
                }
                TextButton(onClick = onLater) { Text(stringResource(R.string.model_prompt_later)) }
            }
        },
    )
}

@Composable
fun MobileDataConfirmDialog(
    onWait: () -> Unit,
    onUseData: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onWait,
        title = { Text(stringResource(R.string.model_mobile_title)) },
        text = { Text(stringResource(R.string.model_mobile_body)) },
        confirmButton = {
            TextButton(onClick = onUseData) { Text(stringResource(R.string.model_mobile_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onWait) { Text(stringResource(R.string.model_mobile_wait)) }
        },
    )
}

@Composable
fun SmartSuggestionsSettingsSection(
    state: ModelState,
    onDownload: () -> Unit,
    onUseMobileData: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    var showMobileDataConfirm by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.model_settings_section)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                ModelState.NotDownloaded -> Button(onClick = onDownload) {
                    Text(stringResource(R.string.model_state_not_downloaded))
                }
                ModelState.WaitingForWifi -> {
                    Text(stringResource(R.string.model_state_waiting_wifi))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showMobileDataConfirm = true }) {
                            Text(stringResource(R.string.model_use_mobile_data))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
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
                    Text(
                        text = stringResource(R.string.model_state_downloading_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
                ModelState.Verifying -> Text(stringResource(R.string.model_state_verifying))
                is ModelState.Ready -> {
                    Text(stringResource(R.string.model_state_ready))
                    OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.model_delete)) }
                }
                is ModelState.Failed -> {
                    Text(stringResource(failureMessageRes(state.reason)))
                    Button(onClick = onRetry) { Text(stringResource(R.string.batch_retry)) }
                }
            }
        }
    }
    if (showMobileDataConfirm) {
        MobileDataConfirmDialog(
            onWait = { showMobileDataConfirm = false },
            onUseData = {
                showMobileDataConfirm = false
                onUseMobileData()
            },
        )
    }
}

@StringRes
private fun failureMessageRes(reason: ModelFailure): Int = when (reason) {
    ModelFailure.NoSpace -> R.string.model_state_failed_space
    ModelFailure.CorruptDownload -> R.string.model_state_failed_corrupt
    ModelFailure.NoNetwork, is ModelFailure.Http -> R.string.model_state_failed_network
}
