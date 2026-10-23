package com.gusanitolabs.robia.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.suggestions.CalibrationPair
import com.gusanitolabs.robia.suggestions.calibrationMarksJson
import com.gusanitolabs.robia.suggestions.scanCalibrationPairs

/**
 * Developer › Duplicate calibration (G-DUP): the 30 most similar same-category pairs with their IMAGE cosine. Manu marks
 * each pair Same or Different and shares the marks as JSON. The marks change nothing in the scan; DUP2 sets the threshold.
 */
@Composable
internal fun DuplicateCalibrationScreen(innerPadding: PaddingValues, items: List<ClothingItem>) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val itemsById = remember(items) { items.associateBy(ClothingItem::id) }
    val ids = remember(items) { items.map(ClothingItem::id) }
    var pairs by remember { mutableStateOf<List<CalibrationPair>?>(null) }
    var marks by remember { mutableStateOf(emptyMap<Int, Boolean>()) }
    // Keyed on the id list: a wardrobe emission that keeps the same garments keeps the marks.
    LaunchedEffect(ids) {
        pairs = scanCalibrationPairs(context, items, EmbeddingRepository(app.database.embeddingDao()))
        marks = emptyMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.duplicate_calibration_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val loaded = pairs
        if (loaded != null && loaded.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.duplicate_calibration_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (loaded != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.duplicate_calibration_progress, marks.size, loaded.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("application/json")
                                .putExtra(Intent.EXTRA_TEXT, calibrationMarksJson(loaded, marks))
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.duplicate_export_chooser)))
                        },
                    ) {
                        Text(stringResource(R.string.duplicate_export_marks))
                    }
                }
            }
            itemsIndexed(loaded) { index, pair ->
                val first = itemsById[pair.a] ?: return@itemsIndexed
                val second = itemsById[pair.b] ?: return@itemsIndexed
                val mark = marks[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DuplicatePhoto(item = first, modifier = Modifier.weight(1f).aspectRatio(0.8f))
                        DuplicatePhoto(item = second, modifier = Modifier.weight(1f).aspectRatio(0.8f))
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.duplicate_pair_title, index + 1) +
                                " · " + stringResource(R.string.duplicate_cosine, pair.cosine),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mark == true,
                                onClick = { marks = toggledMark(marks, index, true) },
                                label = { Text(stringResource(R.string.duplicate_mark_same)) },
                            )
                            FilterChip(
                                selected = mark == false,
                                onClick = { marks = toggledMark(marks, index, false) },
                                label = { Text(stringResource(R.string.duplicate_mark_different)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tapping the mark a pair already has clears it; tapping the other one switches it. */
private fun toggledMark(marks: Map<Int, Boolean>, index: Int, value: Boolean): Map<Int, Boolean> =
    if (marks[index] == value) marks - index else marks + (index to value)
