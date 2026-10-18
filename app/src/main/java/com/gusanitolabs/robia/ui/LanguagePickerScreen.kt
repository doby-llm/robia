package com.gusanitolabs.robia.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.LanguagePreference

/** One picker row: [language] is what a tap stores, [selected] marks the stored preference. */
internal data class LanguageRow(
    val language: LanguagePreference,
    val selected: Boolean,
)

/** System first, then [LanguagePreference.entries] order; exactly the stored [current] is selected. */
internal fun languagePickerRows(current: LanguagePreference): List<LanguageRow> =
    LanguagePreference.entries.map { language -> LanguageRow(language = language, selected = language == current) }

/**
 * Language sub-screen: radio rows titled in each language's own name. The subtitle is the label in the current UI
 * language; System says which language it currently resolves to on this device.
 */
@Composable
internal fun LanguagePickerScreen(
    innerPadding: PaddingValues,
    currentLanguage: LanguagePreference,
    onLanguageSelected: (LanguagePreference) -> Unit,
) {
    // The app's own configuration follows the stored language, so the device language is read from the system resources.
    val deviceLanguage = Resources.getSystem().configuration.locales[0].language
    val systemResolvedLanguage = LanguagePreference.entries
        .firstOrNull { language -> language.storageValue == deviceLanguage }
        ?: LanguagePreference.English
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
                Column {
                    languagePickerRows(currentLanguage).forEachIndexed { index, row ->
                        if (index > 0) {
                            Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceContainerLow)
                        }
                        val subtitle = if (row.language == LanguagePreference.System) {
                            stringResource(R.string.language_currently, stringResource(systemResolvedLanguage.labelRes))
                        } else {
                            stringResource(row.language.labelRes)
                        }
                        LanguagePickerRow(
                            row = row,
                            subtitle = subtitle,
                            onSelect = { onLanguageSelected(row.language) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerRow(
    row: LanguageRow,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = row.selected, onClick = onSelect, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = row.selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(row.language.endonymRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Each language in its own language; only System changes with the UI language. */
private val LanguagePreference.endonymRes: Int
    get() = when (this) {
        LanguagePreference.System -> R.string.language_endonym_system
        LanguagePreference.English -> R.string.language_endonym_en
        LanguagePreference.Spanish -> R.string.language_endonym_es
        LanguagePreference.German -> R.string.language_endonym_de
    }
