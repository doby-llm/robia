package com.gusanitolabs.robia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gusanitolabs.robia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Asset read by [OpenSourceLicensesDialog]; LicensesAssetTest checks that every model URL in the code is listed in it. */
internal const val THIRD_PARTY_LICENSES_ASSET = "licenses/third_party.md"

/**
 * Full-screen plain-text list of the licences. Lines starting with "# " or "## " are headings, everything else is body
 * text. Close, outside tap and back change nothing. There is no route: Settings › General opens this dialog.
 */
@Composable
internal fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            context.assets.open(THIRD_PARTY_LICENSES_ASSET).bufferedReader().use { it.readText() }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_open_source_licenses),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.open_source_licenses_close)) }
                }
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    items(text.lines()) { line -> LicenseLine(line) }
                }
            }
        }
    }
}

@Composable
private fun LicenseLine(line: String) {
    when {
        line.startsWith("## ") -> Text(
            text = line.removePrefix("## "),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        line.startsWith("# ") -> Text(
            text = line.removePrefix("# "),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
        else -> Text(text = line, style = MaterialTheme.typography.bodyMedium)
    }
}
