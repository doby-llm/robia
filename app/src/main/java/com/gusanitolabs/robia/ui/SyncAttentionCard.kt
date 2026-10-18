package com.gusanitolabs.robia.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.sync.SyncAttention
import com.gusanitolabs.robia.sync.SyncAttentionKind

enum class SyncAttentionAction {
    SignIn,
    Retry,
    OpenStorage,
    RetryPhoto,
    KeepWithoutPhoto,
    UseBackup,
    KeepThisPhone,
    ConfirmMerge,
    CancelMerge,
    UseThisAccount,
    DisconnectDrive,
}

internal data class AttentionPresentation(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val actions: List<SyncAttentionAction>,
)

/** Plain-language title, body and actions for each attention kind (TS§4.7). Every kind has at least one action. */
internal fun SyncAttentionKind.presentation(): AttentionPresentation = when (this) {
    SyncAttentionKind.SignInRequired -> AttentionPresentation(
        titleRes = R.string.sync_attention_sign_in_title,
        bodyRes = R.string.sync_attention_sign_in_body,
        actions = listOf(SyncAttentionAction.SignIn),
    )
    SyncAttentionKind.DriveFull -> AttentionPresentation(
        titleRes = R.string.sync_attention_drive_full_title,
        bodyRes = R.string.sync_attention_drive_full_body,
        actions = listOf(SyncAttentionAction.Retry, SyncAttentionAction.OpenStorage),
    )
    SyncAttentionKind.PhotoUnrecoverable -> AttentionPresentation(
        titleRes = R.string.sync_attention_photo_lost_title,
        bodyRes = R.string.sync_attention_photo_lost_body,
        actions = listOf(SyncAttentionAction.RetryPhoto, SyncAttentionAction.KeepWithoutPhoto),
    )
    SyncAttentionKind.PhotoUploadFailed -> AttentionPresentation(
        titleRes = R.string.sync_attention_photo_upload_title,
        bodyRes = R.string.sync_attention_photo_upload_body,
        actions = listOf(SyncAttentionAction.Retry),
    )
    SyncAttentionKind.BackupConflict -> AttentionPresentation(
        titleRes = R.string.sync_attention_conflict_title,
        bodyRes = R.string.sync_attention_conflict_body,
        actions = listOf(SyncAttentionAction.UseBackup, SyncAttentionAction.KeepThisPhone),
    )
    SyncAttentionKind.MergeConfirmation -> AttentionPresentation(
        titleRes = R.string.sync_merge_title,
        bodyRes = R.string.sync_merge_body,
        actions = listOf(SyncAttentionAction.ConfirmMerge, SyncAttentionAction.CancelMerge),
    )
    SyncAttentionKind.AccountMismatch -> AttentionPresentation(
        titleRes = R.string.account_mismatch_title,
        bodyRes = R.string.account_mismatch_body,
        actions = listOf(SyncAttentionAction.UseThisAccount, SyncAttentionAction.DisconnectDrive),
    )
}

@Composable
fun SyncAttentionCard(
    attention: SyncAttention,
    onAction: (SyncAttentionAction) -> Unit,
    showDetails: Boolean,
    onDetails: () -> Unit,
    actions: List<SyncAttentionAction> = attention.kind.presentation().actions,
) {
    val presentation = attention.kind.presentation()
    val errorCard = attention.kind == SyncAttentionKind.SignInRequired || attention.kind == SyncAttentionKind.DriveFull
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (errorCard) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(presentation.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(presentation.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                actions.forEachIndexed { index, action ->
                    if (index == 0) {
                        Button(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(action.labelRes()))
                        }
                    } else {
                        OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(action.labelRes()))
                        }
                    }
                }
                if (showDetails) {
                    TextButton(onClick = onDetails) { Text(stringResource(R.string.sync_details)) }
                }
            }
        }
    }
}

@StringRes
private fun SyncAttentionAction.labelRes(): Int = when (this) {
    SyncAttentionAction.SignIn -> R.string.sync_attention_sign_in_action
    SyncAttentionAction.Retry -> R.string.sync_action_retry
    SyncAttentionAction.OpenStorage -> R.string.sync_attention_open_storage
    SyncAttentionAction.RetryPhoto -> R.string.sync_attention_retry_photo
    SyncAttentionAction.KeepWithoutPhoto -> R.string.sync_attention_keep_without_photo
    SyncAttentionAction.UseBackup -> R.string.sync_conflict_use_backup
    SyncAttentionAction.KeepThisPhone -> R.string.sync_conflict_keep_phone
    SyncAttentionAction.ConfirmMerge -> R.string.sync_merge_confirm
    SyncAttentionAction.CancelMerge -> R.string.cancel
    SyncAttentionAction.UseThisAccount -> R.string.account_mismatch_use_this
    SyncAttentionAction.DisconnectDrive -> R.string.drive_disconnect
}
