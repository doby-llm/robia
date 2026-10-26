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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.gusanitolabs.robia.suggestions.CalibrationCounts
import com.gusanitolabs.robia.suggestions.CalibrationPair
import com.gusanitolabs.robia.suggestions.DuplicatePairKey
import com.gusanitolabs.robia.suggestions.calibrationMarksJson
import com.gusanitolabs.robia.suggestions.scanCalibrationPairs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Developer › Duplicate calibration (G-DUP): the 30 most similar same-category pairs with their IMAGE cosine. Manu marks
 * each pair Same or Different and shares the marks as JSON. The marks change nothing in the scan; DUP2 sets the threshold.
 */
@Composable
internal fun DuplicateCalibrationScreen(innerPadding: PaddingValues, items: List<ClothingItem>) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val itemsById = remember(items) { items.associateBy(ClothingItem::id) }
    // A same-id garment can get a new photo or category while this screen is open; its cosine then describes the old photo.
    val signatures = remember(items) { calibrationSignatures(items) }
    var pairs by remember { mutableStateOf<List<CalibrationPair>?>(null) }
    var counts by remember { mutableStateOf(CalibrationCounts.NONE) }
    // Keyed by the pair's garment ids, not by row, so a rescan cannot move a mark onto another pair.
    var marks by remember { mutableStateOf(emptyMap<DuplicatePairKey, Boolean>()) }
    // The scan reports from its IO thread; a StateFlow carries (done, total) to the composition safely.
    val preparing = remember { MutableStateFlow(0 to 0) }
    val preparingProgress by preparing.collectAsState()
    // Keyed on the signatures: a wardrobe emission that changes no photo, category or archive state keeps the scan and the marks.
    LaunchedEffect(signatures) {
        pairs = null
        marks = emptyMap()
        preparing.value = 0 to 0
        val scan = scanCalibrationPairs(
            context,
            items,
            EmbeddingRepository(app.database.embeddingDao()),
            onProgress = { done, total -> preparing.value = done to total },
        )
        counts = scan.counts
        pairs = scan.pairs
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
        val state = calibrationUiState(pairs, counts)
        if (state is CalibrationUiState.Empty) {
            item {
                Text(
                    text = stringResource(
                        R.string.duplicate_calibration_empty,
                        state.counts.active,
                        state.counts.withVector,
                        state.counts.hashMatched,
                        state.counts.categorised,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (state is CalibrationUiState.Pairs) {
            val loaded = state.pairs
            val markRows = calibrationMarkRows(loaded, marks)
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
                                .putExtra(Intent.EXTRA_TEXT, calibrationMarksJson(loaded, markRows))
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
                val key = DuplicatePairKey.of(pair.a, pair.b)
                val mark = marks[key]
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
                                onClick = { marks = toggledMark(marks, key, true) },
                                label = { Text(stringResource(R.string.duplicate_mark_same)) },
                            )
                            FilterChip(
                                selected = mark == false,
                                onClick = { marks = toggledMark(marks, key, false) },
                                label = { Text(stringResource(R.string.duplicate_mark_different)) },
                            )
                        }
                    }
                }
            }
        } else {
            item {
                val (done, total) = preparingProgress
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.duplicate_calibration_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.duplicate_calibration_preparing, done, total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Tapping the mark a pair already has clears it; tapping the other one switches it. */
internal fun <K> toggledMark(marks: Map<K, Boolean>, key: K, value: Boolean): Map<K, Boolean> =
    if (marks[key] == value) marks - key else marks + (key to value)

/** What a calibration row was computed from. A change in any field makes the row stale. */
internal data class CalibrationSignature(
    val id: String,
    val photoUri: String?,
    val categoryId: String?,
    val isArchived: Boolean,
)

/**
 * Sorted by garment id: the wardrobe list is ordered by favourite and last update, so every write reorders it, and a
 * reorder alone must not reset the marks.
 */
internal fun calibrationSignatures(items: List<ClothingItem>): List<CalibrationSignature> = items.sortedBy(ClothingItem::id).map { item ->
    CalibrationSignature(
        id = item.id,
        photoUri = item.photoUri,
        // The category tag, in the "category" group of DefaultTags.
        categoryId = item.tags.firstOrNull { tag -> tag.categoryId == "category" }?.id,
        isArchived = item.isArchived,
    )
}

/** The marks by row index, the form [calibrationMarksJson] reads. Each pair is looked up by its garment ids. */
internal fun calibrationMarkRows(pairs: List<CalibrationPair>, marks: Map<DuplicatePairKey, Boolean>): Map<Int, Boolean> =
    pairs.withIndex().mapNotNull { (index, pair) ->
        marks[DuplicatePairKey.of(pair.a, pair.b)]?.let { mark -> index to mark }
    }.toMap()

/** What the calibration list shows: the scan is still running, found no pairs, or found pairs. */
internal sealed interface CalibrationUiState {
    data object Loading : CalibrationUiState
    data class Empty(val counts: CalibrationCounts) : CalibrationUiState
    data class Pairs(val pairs: List<CalibrationPair>) : CalibrationUiState
}

/** Null pairs mean the scan has not finished, so the screen never shows the description alone. [counts] only matter when empty. */
internal fun calibrationUiState(pairs: List<CalibrationPair>?, counts: CalibrationCounts): CalibrationUiState = when {
    pairs == null -> CalibrationUiState.Loading
    pairs.isEmpty() -> CalibrationUiState.Empty(counts)
    else -> CalibrationUiState.Pairs(pairs)
}
