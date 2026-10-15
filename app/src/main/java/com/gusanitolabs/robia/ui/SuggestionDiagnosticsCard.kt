package com.gusanitolabs.robia.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.suggestions.SuggestionDiagnostics
import com.gusanitolabs.robia.suggestions.monospaceLines
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The diagnostics card shows only when developer mode is both unlocked and switched on (TS§3.12). */
internal fun showSuggestionDiagnostics(devUnlocked: Boolean, devEnabled: Boolean): Boolean = devUnlocked && devEnabled

/**
 * Developer-only card on the garment detail: the monospace scoring block of the stored vector, plus
 * "Recompute embedding" and "Export input image". [diagnostics] is null while loading or when the model is not Ready.
 */
@Composable
internal fun SuggestionDiagnosticsCard(
    diagnostics: SuggestionDiagnostics?,
    hasPhoto: Boolean,
    statusMessage: String?,
    onRecomputeClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    val computedAtFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dev_suggestion_diagnostics),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (diagnostics == null) {
                Text(
                    text = stringResource(R.string.dev_diagnostics_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lines = diagnostics.monospaceLines() +
                    "computed ${computedAtFormat.format(Date(diagnostics.computedAtEpochMillis))}"
                Text(
                    text = lines.joinToString(separator = "\n"),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                )
            }
            statusMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecomputeClick, enabled = hasPhoto) {
                    Text(stringResource(R.string.dev_recompute_embedding))
                }
                OutlinedButton(onClick = onExportClick, enabled = hasPhoto) {
                    Text(stringResource(R.string.developer_export_suggestion_input))
                }
            }
        }
    }
}

/**
 * Writes the scaled suggestion input PNG to Pictures/Robia. Same MediaStore steps as the capture-screen export
 * (AddEditClothingScreen), kept local so that screen's helper stays private.
 */
internal fun saveDiagnosticsInputPng(context: Context, png: ByteArray): Uri {
    val values = ContentValues().apply {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        put(MediaStore.Images.Media.DISPLAY_NAME, "robia-suggestion-input-$stamp.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Robia")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val resolver = context.contentResolver
    val target = resolver.insert(collection, values) ?: error("MediaStore insert failed")
    try {
        resolver.openOutputStream(target)?.use { output -> output.write(png) } ?: error("Output stream unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    } catch (failure: Exception) {
        resolver.delete(target, null, null)
        throw failure
    }
    return target
}
