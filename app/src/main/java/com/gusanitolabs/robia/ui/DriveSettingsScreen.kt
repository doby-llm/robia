package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus

/**
 * Google Drive sub-screen: the status card, then the Drive actions Settings had under "Data sync", unchanged in behaviour.
 * Every action calls a callback the shell already owns, so no sync or deletion logic lives here.
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
) {
    val canDeleteBackup = driveSyncConnectionStatus == DriveSyncConnectionStatus.Connected ||
        driveSyncConnectionStatus == DriveSyncConnectionStatus.NeedsAttention
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
                    if (canDeleteBackup) {
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
}
