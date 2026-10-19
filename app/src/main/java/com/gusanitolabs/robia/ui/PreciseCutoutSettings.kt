package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.BatchCutoutMode
import com.gusanitolabs.robia.core.model.CutoutEdges
import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import com.gusanitolabs.robia.core.model.HangerSensitivity
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

/** Hanger options need the precise engine: the pack is ready and the method is Automatic (SG4). */
internal fun cutoutHangerOptionsAvailable(packReady: Boolean, engine: CutoutEngineChoice): Boolean =
    packReady && engine == CutoutEngineChoice.Automatic

/** The single-choice row whose dialog is open; null when none is. */
private enum class OpenCutoutChoice { Method, HangerDetection, Edges, BatchSpeed }

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
    var openChoice by rememberSaveable { mutableStateOf<OpenCutoutChoice?>(null) }
    val scope = rememberCoroutineScope()
    val packReady = packState is ModelState.Ready
    val hangerAvailable = cutoutHangerOptionsAvailable(packReady, settings.cutoutEngine)
    val hangerDetectionEnabled = hangerAvailable && settings.cutoutRemoveHanger
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
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.cutout_settings_title)) {
                CutoutChoiceItem(
                    headline = stringResource(R.string.cutout_method),
                    value = stringResource(settings.cutoutEngine.labelRes()),
                    supporting = stringResource(
                        if (settings.cutoutEngine == CutoutEngineChoice.Automatic) {
                            R.string.cutout_method_automatic_hint
                        } else {
                            R.string.cutout_method_standard_hint
                        },
                    ),
                    enabled = true,
                    onClick = { openChoice = OpenCutoutChoice.Method },
                )
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cutout_remove_hangers)) },
                    supportingContent = if (hangerAvailable) null else ({ Text(stringResource(R.string.cutout_needs_precise)) }),
                    trailingContent = {
                        Switch(
                            checked = settings.cutoutRemoveHanger,
                            enabled = hangerAvailable,
                            onCheckedChange = { enabled ->
                                scope.launch { app.settingsRepository.setCutoutRemoveHanger(enabled) }
                            },
                        )
                    },
                    modifier = Modifier.clickable(enabled = hangerAvailable) {
                        scope.launch { app.settingsRepository.setCutoutRemoveHanger(!settings.cutoutRemoveHanger) }
                    },
                )
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                CutoutChoiceItem(
                    headline = stringResource(R.string.cutout_hanger_detection),
                    value = stringResource(settings.cutoutHangerSensitivity.labelRes()),
                    supporting = if (settings.cutoutHangerSensitivity == HangerSensitivity.High) {
                        stringResource(R.string.cutout_hanger_high_hint)
                    } else {
                        null
                    },
                    enabled = hangerDetectionEnabled,
                    onClick = { openChoice = OpenCutoutChoice.HangerDetection },
                )
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                CutoutChoiceItem(
                    headline = stringResource(R.string.cutout_edges),
                    value = stringResource(settings.cutoutEdges.labelRes()),
                    supporting = null,
                    enabled = true,
                    onClick = { openChoice = OpenCutoutChoice.Edges },
                )
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                CutoutChoiceItem(
                    headline = stringResource(R.string.batch_speed),
                    value = stringResource(settings.batchCutoutMode.labelRes()),
                    supporting = if (settings.batchCutoutMode == BatchCutoutMode.MemorySaver) {
                        stringResource(R.string.batch_speed_hint)
                    } else {
                        null
                    },
                    enabled = true,
                    onClick = { openChoice = OpenCutoutChoice.BatchSpeed },
                )
            }
        }
    }
    when (openChoice) {
        OpenCutoutChoice.Method -> CutoutChoiceDialog(
            title = stringResource(R.string.cutout_method),
            options = listOf(CutoutEngineChoice.Automatic, CutoutEngineChoice.Standard),
            labelOf = { it.labelRes() },
            current = settings.cutoutEngine,
            onSelected = { value ->
                openChoice = null
                scope.launch { app.settingsRepository.setCutoutEngine(value) }
            },
            onDismiss = { openChoice = null },
        )
        OpenCutoutChoice.HangerDetection -> CutoutChoiceDialog(
            title = stringResource(R.string.cutout_hanger_detection),
            options = listOf(HangerSensitivity.Normal, HangerSensitivity.High),
            labelOf = { it.labelRes() },
            current = settings.cutoutHangerSensitivity,
            onSelected = { value ->
                openChoice = null
                scope.launch { app.settingsRepository.setCutoutHangerSensitivity(value) }
            },
            onDismiss = { openChoice = null },
        )
        OpenCutoutChoice.Edges -> CutoutChoiceDialog(
            title = stringResource(R.string.cutout_edges),
            options = listOf(CutoutEdges.Soft, CutoutEdges.Sharp),
            labelOf = { it.labelRes() },
            current = settings.cutoutEdges,
            onSelected = { value ->
                openChoice = null
                scope.launch { app.settingsRepository.setCutoutEdges(value) }
            },
            onDismiss = { openChoice = null },
        )
        OpenCutoutChoice.BatchSpeed -> CutoutChoiceDialog(
            title = stringResource(R.string.batch_speed),
            options = listOf(BatchCutoutMode.Balanced, BatchCutoutMode.MemorySaver),
            labelOf = { it.labelRes() },
            current = settings.batchCutoutMode,
            onSelected = { value ->
                openChoice = null
                scope.launch { app.settingsRepository.setBatchCutoutMode(value) }
            },
            onDismiss = { openChoice = null },
        )
        null -> Unit
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
                        // The precise engine follows the pack state (usePrecise needs a ready pack), so the method choice stays as it is.
                        app.appScope.launch { app.cutoutPack.delete() }
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

@StringRes
private fun CutoutEngineChoice.labelRes(): Int = when (this) {
    CutoutEngineChoice.Automatic -> R.string.cutout_method_automatic
    CutoutEngineChoice.Standard -> R.string.cutout_method_standard
}

@StringRes
private fun HangerSensitivity.labelRes(): Int = when (this) {
    HangerSensitivity.Normal -> R.string.cutout_hanger_normal
    HangerSensitivity.High -> R.string.cutout_hanger_high
}

@StringRes
private fun CutoutEdges.labelRes(): Int = when (this) {
    CutoutEdges.Soft -> R.string.cutout_edges_soft
    CutoutEdges.Sharp -> R.string.cutout_edges_sharp
}

@StringRes
private fun BatchCutoutMode.labelRes(): Int = when (this) {
    BatchCutoutMode.Balanced -> R.string.batch_speed_balanced
    BatchCutoutMode.MemorySaver -> R.string.batch_speed_memory_saver
}

/** One single-choice row of the Cutout sub-screen: the headline, the current value, and an optional supporting line. */
@Composable
private fun CutoutChoiceItem(
    headline: String,
    value: String,
    supporting: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val supportingSlot: (@Composable () -> Unit)? = if (supporting == null) {
        null
    } else {
        { Text(supporting) }
    }
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supportingSlot,
        trailingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** Radio-button dialog for one setting. Cancel, outside tap and back close it without a change. */
@Composable
private fun <T> CutoutChoiceDialog(
    title: String,
    options: List<T>,
    labelOf: (T) -> Int,
    current: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == current,
                                onClick = { onSelected(option) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(
                            text = stringResource(labelOf(option)),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
