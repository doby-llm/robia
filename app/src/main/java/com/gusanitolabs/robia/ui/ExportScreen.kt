package com.gusanitolabs.robia.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.designsystem.LocalReducedMotion
import com.gusanitolabs.robia.core.model.ExportField
import com.gusanitolabs.robia.core.model.ExportFormat
import com.gusanitolabs.robia.core.model.ExportOptions
import com.gusanitolabs.robia.core.model.ExportPhoto
import com.gusanitolabs.robia.media.GarmentShareExporter
import com.gusanitolabs.robia.media.GarmentShareItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Export (REQ-EX-01): photo, format and fields for one garment. The choices persist through [onOptionsChange]. The preview
 * is the rendered card, or page 1 of the PDF; Share opens the system chooser.
 */
@Composable
internal fun ExportScreen(
    innerPadding: PaddingValues,
    item: GarmentShareItem,
    originalUri: Uri?,
    options: ExportOptions,
    onOptionsChange: (ExportOptions) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.share_garment_chooser_title)
    val imageShareErrorMessage = stringResource(R.string.image_share_error)
    val pdfShareErrorMessage = stringResource(R.string.pdf_share_error)
    val noShareAppMessage = stringResource(R.string.share_no_app_error)
    val hasOriginal = originalUri != null
    val photo = options.photoFor(hasOriginal)
    val shareItem = if (photo == ExportPhoto.Original && originalUri != null) item.copy(imageUri = originalUri) else item
    val showFrame = resolveFrame(options).first
    val reducedMotion = LocalReducedMotion.current
    val scrollState = rememberScrollState()
    var previewTop by remember { mutableFloatStateOf(0f) }
    var rendering by remember { mutableStateOf(false) }
    var preview by remember(options, item, originalUri) { mutableStateOf<Bitmap?>(null) }

    // The preview sits below the fold: once it is laid out, scroll to its top (no animation with reduced motion).
    LaunchedEffect(preview) {
        if (preview == null) return@LaunchedEffect
        withFrameNanos { }
        val target = previewTop.roundToInt().coerceIn(0, scrollState.maxValue)
        if (reducedMotion) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
    }

    fun showPreview() {
        rendering = true
        scope.launch {
            val bitmap = runCatching {
                withContext(Dispatchers.IO) { renderPreview(context, shareItem, options, showFrame) }
            }.getOrNull()
            rendering = false
            if (bitmap != null) {
                preview = bitmap
            } else {
                Toast.makeText(
                    context,
                    if (options.format == ExportFormat.Image) imageShareErrorMessage else pdfShareErrorMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun share() {
        scope.launch {
            val isImage = options.format == ExportFormat.Image
            val shareUri = runCatching {
                withContext(Dispatchers.IO) {
                    if (isImage) {
                        GarmentShareExporter.createShareCard(context, shareItem, options.fields, showFrame)
                    } else {
                        GarmentShareExporter.createSharePdf(context, shareItem, options.fields, showFrame)
                    }
                }
            }.getOrNull()
            if (shareUri != null) {
                val launched = context.launchShareChooser(
                    uri = shareUri,
                    mimeType = if (isImage) "image/png" else "application/pdf",
                    chooserTitle = chooserTitle,
                )
                if (!launched) {
                    Toast.makeText(context, noShareAppMessage, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(
                    context,
                    if (isImage) imageShareErrorMessage else pdfShareErrorMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(stringResource(R.string.export_photo_section))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = photo == ExportPhoto.Robia,
                onClick = { onOptionsChange(options.copy(photo = ExportPhoto.Robia)) },
                label = { Text(stringResource(R.string.export_photo_robia)) },
            )
            FilterChip(
                selected = photo == ExportPhoto.Original,
                enabled = hasOriginal,
                onClick = { onOptionsChange(options.copy(photo = ExportPhoto.Original)) },
                label = { Text(stringResource(R.string.export_photo_original)) },
            )
        }
        Text(
            text = stringResource(R.string.export_photo_original_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.export_format_section))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = options.format == ExportFormat.Image,
                onClick = { onOptionsChange(options.copy(format = ExportFormat.Image)) },
                label = { Text(stringResource(R.string.export_format_image)) },
            )
            FilterChip(
                selected = options.format == ExportFormat.Pdf,
                onClick = { onOptionsChange(options.copy(format = ExportFormat.Pdf)) },
                label = { Text(stringResource(R.string.export_format_pdf)) },
            )
        }

        SectionTitle(stringResource(R.string.export_show_section))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                ExportField.entries.forEach { field ->
                    val checked = field in options.fields
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Switch,
                                onValueChange = { on ->
                                    onOptionsChange(
                                        options.copy(fields = if (on) options.fields + field else options.fields - field),
                                    )
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(field.labelRes()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(checked = checked, onCheckedChange = null)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FrameRow(
                    resolved = resolveFrame(options),
                    onCheckedChange = { on -> onOptionsChange(options.copy(showFrame = on)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = ::showPreview,
                enabled = !rendering,
                modifier = Modifier.weight(1f),
            ) {
                if (rendering) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.export_preview))
                }
            }
            Button(
                onClick = ::share,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_share))
            }
        }

        preview?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.export_preview_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                    .onGloballyPositioned { previewTop = it.positionInParent().y },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * The Robia frame switch (REQ-FB-03), under the Show switches. It may be off only when no field is shown; with any field on
 * it reads as on and is locked, and the hint says why. The stored choice is kept, so turning the fields off restores it.
 */
internal fun resolveFrame(options: ExportOptions): Pair<Boolean, Boolean> =
    if (options.fields.isEmpty()) options.showFrame to true else true to false

@Composable
private fun FrameRow(resolved: Pair<Boolean, Boolean>, onCheckedChange: (Boolean) -> Unit) {
    val (checked, enabled) = resolved
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.export_frame_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.export_frame_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.export_frame_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@StringRes
private fun ExportField.labelRes(): Int = when (this) {
    ExportField.Name -> R.string.export_field_name
    ExportField.Category -> R.string.metadata_category
    ExportField.Colors -> R.string.colors_section
    ExportField.Occasions -> R.string.metadata_occasions
    ExportField.Seasons -> R.string.export_field_seasons
    ExportField.Fit -> R.string.metadata_fit
    ExportField.Location -> R.string.metadata_location
    ExportField.Notes -> R.string.export_field_notes
}

private fun renderPreview(context: Context, item: GarmentShareItem, options: ExportOptions, showFrame: Boolean): Bitmap =
    when (options.format) {
        ExportFormat.Image -> GarmentShareExporter.renderShareCard(context, item, options.fields, showFrame)
        ExportFormat.Pdf -> GarmentShareExporter.renderPdfPreview(
            context,
            GarmentShareExporter.createSharePdf(context, item, options.fields, showFrame),
        )
    }
