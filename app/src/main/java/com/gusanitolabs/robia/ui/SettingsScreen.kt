package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.data.toDomain
import com.gusanitolabs.robia.suggestions.DevBenchmark
import com.gusanitolabs.robia.suggestions.garmentTextInput
import com.gusanitolabs.robia.suggestions.rankSimilar
import com.gusanitolabs.robia.suggestions.sortNewestFirst
import com.gusanitolabs.robia.suggestions.sha256Hex16
import com.gusanitolabs.robia.suggestions.model.ModelDownloadWorker
import com.gusanitolabs.robia.sync.WardrobeSyncState
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SettingsScreen(
    innerPadding: PaddingValues,
    currentLanguage: LanguagePreference,
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    syncState: WardrobeSyncState,
    @StringRes cloudSetupSummaryRes: Int,
    cloudIndicator: CloudSyncIndicator,
    developerModeUnlocked: Boolean,
    developerModeEnabled: Boolean,
    onDeveloperModeEnabledChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguagePreference) -> Unit,
    onLanguageClick: () -> Unit,
    onCloudSetupClick: () -> Unit,
    driveBackupDeletionState: com.gusanitolabs.robia.core.model.DriveBackupDeletionState,
    onDeleteBackupClick: () -> Unit,
    onRestoreSyncLogClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val modelState by app.modelStore.state.collectAsState()
    val modelWorkQueued by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
            .map { infos -> infos.any { info -> info.state == WorkInfo.State.ENQUEUED } }
    }.collectAsState(initial = false)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var benchmarkText by remember { mutableStateOf<String?>(null) }
    var benchmarkRunning by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SmartSuggestionsSettingsSection(
                state = settingsModelState(modelState, modelWorkQueued),
                onDownload = { startModelDownload(context) },
                onUseMobileData = {
                    scope.launch {
                        app.settingsRepository.setModelMobileDataApproved(true)
                        startModelDownload(context, allowMobileData = true, restart = true)
                    }
                },
                onCancel = { cancelModelDownload(context) },
                onDelete = {
                    scope.launch {
                        cancelModelDownload(context)
                        app.embeddingEngineHolder.unload()
                        app.modelStore.delete()
                    }
                },
                onRetry = {
                    scope.launch {
                        val approved = app.settingsRepository.modelMobileDataApproved.first()
                        startModelDownload(context, allowMobileData = approved, restart = true)
                    }
                },
            )
        }
        item {
            SettingsSection(title = stringResource(R.string.language)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(stringResource(currentLanguage.labelRes)) },
                    leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onLanguageClick),
                )
                LanguagePreference.entries.forEach { language ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(language.labelRes),
                                fontWeight = if (language == currentLanguage) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        trailingContent = if (language == currentLanguage) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onLanguageSelected(language) },
                    )
                }
            }
        }
        if (developerModeUnlocked) {
            item {
                SettingsSection(title = stringResource(R.string.developer_mode)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.developer_mode)) },
                        supportingContent = { Text(stringResource(R.string.developer_mode_summary)) },
                        leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = developerModeEnabled,
                                onCheckedChange = onDeveloperModeEnabledChange,
                            )
                        },
                        modifier = Modifier.clickable { onDeveloperModeEnabledChange(!developerModeEnabled) },
                    )
                    if (developerModeEnabled) {
                        Divider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.restore_sync_log_title)) },
                            supportingContent = { Text(stringResource(R.string.restore_sync_log_summary)) },
                            leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onRestoreSyncLogClick),
                        )
                        Divider()
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.dev_model_benchmark)) },
                            supportingContent = {
                                val result = benchmarkText
                                if (benchmarkRunning) {
                                    Text(stringResource(R.string.dev_benchmark_running))
                                } else if (result != null) {
                                    Text(
                                        text = result,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    TextButton(
                                        enabled = !benchmarkRunning,
                                        onClick = {
                                            benchmarkRunning = true
                                            scope.launch {
                                                benchmarkText = try {
                                                    DevBenchmark(
                                                        context = context,
                                                        holder = app.embeddingEngineHolder,
                                                        syncLog = app.restoreSyncLogRepository,
                                                    ).measure()
                                                } catch (failure: Exception) {
                                                    context.getString(
                                                        R.string.dev_benchmark_failed,
                                                        failure.message ?: failure.javaClass.simpleName,
                                                    )
                                                } finally {
                                                    benchmarkRunning = false
                                                }
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.dev_run))
                                    }
                                    TextButton(
                                        enabled = benchmarkText != null && !benchmarkRunning,
                                        onClick = { clipboard.setText(AnnotatedString(benchmarkText.orEmpty())) },
                                    ) {
                                        Text(stringResource(R.string.dev_copy))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.data_sync)) {
                if (driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected ||
                    driveSyncConnectionStatus == DriveSyncConnectionStatus.NeedsAttention
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.drive_backup_delete_menu)) },
                        leadingContent = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onDeleteBackupClick),
                    )
                    Divider()
                }
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (driveBackupDeletionState == com.gusanitolabs.robia.core.model.DriveBackupDeletionState.Complete ||
                                    driveBackupDeletionState == com.gusanitolabs.robia.core.model.DriveBackupDeletionState.NeedsAttention
                                ) {
                                    R.string.drive_backup_again
                                } else {
                                    R.string.data_sync_google_drive
                                },
                            ),
                        )
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(cloudIndicator.headline.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            cloudIndicator.queueSummaryText()?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(cloudSetupSummaryRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (driveBackupDeletionState != com.gusanitolabs.robia.core.model.DriveBackupDeletionState.None) {
                                Text(
                                    text = stringResource(driveBackupDeletionState.statusRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(cloudIndicator.headline.icon, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onCloudSetupClick),
                )
            }
        }
        if (developerModeEnabled) {
            item {
                DeveloperCloudSyncCard(syncState = syncState, indicator = cloudIndicator)
            }
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            content()
        }
    }
}

/** Null hides the line: a syncing or attention state with nothing pending says nothing here rather than "synced". */
@Composable
private fun CloudSyncIndicator.queueSummaryText(): String? = when {
    headline == CloudSyncHeadline.Synced -> stringResource(R.string.garment_sync_all_synced)
    pendingCount <= 0 -> null
    headline == CloudSyncHeadline.NotConfigured || headline == CloudSyncHeadline.Disabled ->
        stringResource(R.string.garment_sync_local_changes_saved, pendingCount)
    pendingCount == 1 -> stringResource(R.string.garment_sync_pending_count_one)
    else -> stringResource(R.string.garment_sync_pending_count_many, pendingCount)
}

/** Developer mode only: the last cycle, what was pending and whether the processor is running, read from the same state. */
@Composable
private fun DeveloperCloudSyncCard(syncState: WardrobeSyncState, indicator: CloudSyncIndicator) {
    val cycle = syncState.lastCycle
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val processing = syncState.isRunningNow
    SettingsSection(title = stringResource(R.string.dev_cloud_title)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.dev_cloud_status, stringResource(indicator.headline.labelRes)),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(R.string.dev_cloud_pending, indicator.pendingCount),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(
                    R.string.dev_cloud_processing,
                    stringResource(if (processing) R.string.dev_cloud_yes else R.string.dev_cloud_no),
                ),
                style = MaterialTheme.typography.labelSmall,
            )
            if (cycle == null) {
                Text(
                    text = stringResource(R.string.dev_cloud_no_cycle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.dev_cloud_started, timeFormat.format(Date(cycle.startedAtEpochMillis))),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_ended, timeFormat.format(Date(cycle.endedAtEpochMillis))),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_result, stringResource(cycle.result.readableNameRes())),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(R.string.dev_cloud_pending_at_start, cycle.pendingAtStart),
                    style = MaterialTheme.typography.labelSmall,
                )
                indicator.detail?.let { detail ->
                    if (indicator.offline) {
                        // A connection failure is not an error: neutral wording, the raw class stays as secondary text.
                        Text(
                            text = stringResource(R.string.cloud_sync_headline_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dev_cloud_error, detail),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private val LanguagePreference.labelRes: Int
    get() = when (this) {
        LanguagePreference.System -> R.string.language_system
        LanguagePreference.English -> R.string.language_english
        LanguagePreference.Spanish -> R.string.language_spanish
        LanguagePreference.German -> R.string.language_german
    }

@Composable
internal fun LanguageSettingsScreen(innerPadding: PaddingValues) {
    val languages = stringArrayResource(R.array.language_choices)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.language_settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.language_settings_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                languages.forEachIndexed { index, language ->
                    ListItem(
                        headlineContent = { Text(language) },
                        leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    )
                    if (index != languages.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
