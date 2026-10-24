package com.gusanitolabs.robia.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.sync.DriveSyncResult
import com.gusanitolabs.robia.sync.DriveUsage
import com.gusanitolabs.robia.sync.PhotoIntegrityReport
import com.gusanitolabs.robia.sync.PhotoIntegrityStatus
import kotlinx.coroutines.launch

/**
 * Google Drive sub-screen: the status card, the space Robia uses, then the Drive actions Settings had under "Data sync",
 * unchanged in behaviour. Every action calls a callback the shell already owns, so no sync or deletion logic lives here.
 */
@Composable
internal fun DriveSettingsScreen(
    innerPadding: PaddingValues,
    driveSyncConnectionStatus: DriveSyncConnectionStatus,
    @StringRes cloudSetupSummaryRes: Int,
    cloudIndicator: CloudSyncIndicator,
    driveBackupDeletionState: DriveBackupDeletionState,
    onCloudSetupClick: () -> Unit,
    onDeleteBackupClick: () -> Unit,
    driveUsage: DriveUsage? = null,
    onRefreshDriveUsageClick: suspend () -> Unit = {},
    /** "Check photos" row; null hides the row. Read-only: nothing is deleted or rewritten. */
    onCheckPhotosClick: (suspend () -> DriveSyncResult<PhotoIntegrityReport>)? = null,
    onRestorePhotoClick: (garmentId: String) -> Unit = {},
    onBackUpNowClick: () -> Unit = {},
) {
    val isConnected = driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected ||
        driveSyncConnectionStatus == DriveSyncConnectionStatus.NeedsAttention
    val refreshScope = rememberCoroutineScope()
    var refreshingDriveUsage by remember { mutableStateOf(false) }
    var photoCheckOpen by remember { mutableStateOf(false) }
    var photoCheck by remember { mutableStateOf<PhotoIntegrityReport?>(null) }
    var photoCheckFailed by remember { mutableStateOf(false) }
    val setupTitleRes = if (
        driveBackupDeletionState == DriveBackupDeletionState.Complete ||
        driveBackupDeletionState == DriveBackupDeletionState.NeedsAttention
    ) {
        R.string.drive_backup_again
    } else {
        R.string.data_sync_google_drive
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(cloudIndicator.headline.icon, contentDescription = null)
                        Text(
                            text = stringResource(cloudIndicator.headline.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
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
                    if (driveBackupDeletionState != DriveBackupDeletionState.None) {
                        Text(
                            text = stringResource(driveBackupDeletionState.statusRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (isConnected) {
            item {
                val context = LocalContext.current
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.drive_usage_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = driveUsage?.let { usage ->
                                    stringResource(
                                        R.string.drive_usage_value,
                                        Formatter.formatShortFileSize(context, usage.totalBytes),
                                        usage.photoCount,
                                    ) + stringResource(R.string.drive_usage_originals, usage.originalCount)
                                } ?: stringResource(R.string.drive_usage_unknown),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            driveUsage?.let { usage ->
                                val measuredAgo = DateUtils.getRelativeTimeSpanString(
                                    usage.measuredAtEpochMillis,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString()
                                Text(
                                    text = stringResource(R.string.drive_usage_updated, measuredAgo),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(
                            enabled = !refreshingDriveUsage,
                            onClick = {
                                refreshingDriveUsage = true
                                refreshScope.launch {
                                    try {
                                        onRefreshDriveUsageClick()
                                    } finally {
                                        refreshingDriveUsage = false
                                    }
                                }
                            },
                        ) {
                            if (refreshingDriveUsage) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.drive_usage_refresh))
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SettingsNavRow(
                        icon = cloudIndicator.headline.icon,
                        title = stringResource(setupTitleRes),
                        onClick = onCloudSetupClick,
                    )
                    if (isConnected) {
                        onCheckPhotosClick?.let { checkPhotos ->
                            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                            SettingsNavRow(
                                icon = Icons.Rounded.Search,
                                title = stringResource(R.string.check_photos_title),
                                onClick = {
                                    photoCheck = null
                                    photoCheckFailed = false
                                    photoCheckOpen = true
                                    refreshScope.launch {
                                        when (val result = checkPhotos()) {
                                            is DriveSyncResult.Success -> { photoCheck = result.value }
                                            else -> { photoCheckFailed = true }
                                        }
                                    }
                                },
                            )
                        }
                        Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                        SettingsNavRow(
                            icon = Icons.Rounded.Delete,
                            title = stringResource(R.string.drive_backup_delete_menu),
                            color = MaterialTheme.colorScheme.error,
                            onClick = onDeleteBackupClick,
                            trailing = null,
                        )
                    }
                }
            }
        }
    }
    if (photoCheckOpen) {
        PhotoCheckDialog(
            report = photoCheck,
            failed = photoCheckFailed,
            onRestorePhotoClick = onRestorePhotoClick,
            onBackUpNowClick = {
                photoCheckOpen = false
                onBackUpNowClick()
            },
            onDismiss = { photoCheckOpen = false },
        )
    }
}

/** Result of "Check photos": a count when all is well, otherwise each problem with the action that fixes it. */
@Composable
private fun PhotoCheckDialog(
    report: PhotoIntegrityReport?,
    failed: Boolean,
    onRestorePhotoClick: (garmentId: String) -> Unit,
    onBackUpNowClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_photos_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    failed -> Text(stringResource(R.string.check_photos_failed))
                    report == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    report.problems.isEmpty() -> Text(stringResource(R.string.check_photos_all_ok, report.okCount))
                    else -> {
                        Text(stringResource(R.string.check_photos_problems, report.problems.size))
                        report.problems.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.garmentName.ifBlank { entry.garmentId },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(entry.status.labelRes()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (entry.status == PhotoIntegrityStatus.MissingLocalFile) {
                                    TextButton(onClick = { onRestorePhotoClick(entry.garmentId) }) {
                                        Text(stringResource(R.string.check_photos_restore))
                                    }
                                }
                            }
                        }
                        if (report.problems.any { entry -> entry.status == PhotoIntegrityStatus.NotYetUploaded }) {
                            Button(onClick = onBackUpNowClick, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.check_photos_back_up_now))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@StringRes
private fun PhotoIntegrityStatus.labelRes(): Int = when (this) {
    PhotoIntegrityStatus.Ok -> R.string.check_photos_title
    PhotoIntegrityStatus.MissingLocalFile -> R.string.check_photos_missing_local
    PhotoIntegrityStatus.MissingOnDrive -> R.string.check_photos_missing_drive
    PhotoIntegrityStatus.SizeMismatch -> R.string.check_photos_size_mismatch
    PhotoIntegrityStatus.NotYetUploaded -> R.string.check_photos_not_uploaded
}
