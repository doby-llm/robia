package com.gusanitolabs.robia.ui

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.OUTFIT_LAB_RATINGS_FILE_NAME
import com.gusanitolabs.robia.suggestions.Outfit
import com.gusanitolabs.robia.suggestions.OutfitComposer
import com.gusanitolabs.robia.suggestions.outfitRatingLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SEASON_TAGS = DefaultTags.tags.filter { tag -> tag.categoryId == "season" }
private val OCCASION_TAGS = DefaultTags.tags.filter { tag -> tag.categoryId == "occasion" }

/**
 * Developer › Outfit lab (REQ-OF-01, G-OUT): a seed garment or a typed prompt, optional season and occasion, and three
 * outfit proposals with a why-line and 👍/👎. Ratings go to `filesDir/outfit_lab.jsonl` as ids and ratings only;
 * Export ratings shares that log as text.
 */
@Composable
internal fun OutfitLabScreen(innerPadding: PaddingValues, garments: List<ClothingItem>) {
    val context = LocalContext.current
    val app = context.applicationContext as RobiaApplication
    val scope = rememberCoroutineScope()
    val vectors by app.embeddingRepository
        .observeVectors(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
        .collectAsState(initial = emptyMap())
    val ratingsFile = remember { File(context.filesDir, OUTFIT_LAB_RATINGS_FILE_NAME) }
    val itemsById = remember(garments) { garments.associateBy(ClothingItem::id) }
    val tagsById = remember(garments) { garments.flatMap(ClothingItem::tags).associateBy(GarmentTag::id) }
    val seedChoices = remember(garments, vectors) {
        garments
            .filter { item -> !item.isArchived && OutfitComposer.slotOf(item) != null && vectors[item.id]?.image != null }
            .sortedBy(ClothingItem::name)
    }
    var seed by rememberSaveable { mutableStateOf<String?>(null) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf<String?>(null) }
    var occasion by rememberSaveable { mutableStateOf<String?>(null) }
    var proposals by remember { mutableStateOf<List<Outfit>?>(null) }
    var promptFailed by remember { mutableStateOf(false) }
    val ratings = remember { mutableStateMapOf<Outfit, Boolean>() }
    var ratingCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { ratingCount = withContext(Dispatchers.IO) { countRatings(ratingsFile) } }

    fun propose() {
        val trimmed = prompt.trim()
        scope.launch {
            val promptVector = if (trimmed.isEmpty()) null else embedPrompt(app, trimmed)
            promptFailed = trimmed.isNotEmpty() && promptVector == null
            proposals = if (promptFailed) {
                emptyList()
            } else {
                OutfitComposer.compose(seed, promptVector, season, occasion, garments, vectors)
            }
            ratings.clear()
        }
    }

    fun rate(outfit: Outfit, thumbsUp: Boolean) {
        ratings[outfit] = thumbsUp
        val line = outfitRatingLine(System.currentTimeMillis(), outfit, seed, season, occasion, thumbsUp) + "\n"
        scope.launch {
            withContext(Dispatchers.IO) { ratingsFile.appendText(line) }
            ratingCount += 1
        }
    }

    fun exportRatings() {
        scope.launch {
            val text = withContext(Dispatchers.IO) { ratingsFile.takeIf(File::exists)?.readText() }.orEmpty()
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.outfit_lab_export_chooser)))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.outfit_lab_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(text = stringResource(R.string.outfit_lab_seed_title), style = MaterialTheme.typography.labelLarge)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seedChoices, key = ClothingItem::id) { garment ->
                    SeedThumbnail(
                        item = garment,
                        selected = seed == garment.id,
                        onClick = { seed = if (seed == garment.id) null else garment.id },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.outfit_lab_prompt_label)) },
                placeholder = { Text(stringResource(R.string.outfit_lab_prompt_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(text = stringResource(R.string.outfit_lab_season_title), style = MaterialTheme.typography.labelLarge)
        }
        item {
            OutfitLabChipRow(tags = SEASON_TAGS, selectedId = season, onSelected = { season = it })
        }
        item {
            Text(text = stringResource(R.string.outfit_lab_occasion_title), style = MaterialTheme.typography.labelLarge)
        }
        item {
            OutfitLabChipRow(tags = OCCASION_TAGS, selectedId = occasion, onSelected = { occasion = it })
        }
        item {
            Button(
                onClick = { propose() },
                enabled = seed != null || prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.outfit_lab_generate))
            }
        }
        val results = proposals
        if (promptFailed) {
            item {
                Text(text = stringResource(R.string.outfit_lab_prompt_failed), style = MaterialTheme.typography.bodyLarge)
            }
        } else if (results != null && results.isEmpty()) {
            item {
                Text(text = stringResource(R.string.outfit_lab_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
        itemsIndexed(results.orEmpty()) { _, outfit ->
            OutfitCard(
                outfit = outfit,
                itemsById = itemsById,
                tagsById = tagsById,
                rating = ratings[outfit],
                onRate = { thumbsUp -> rate(outfit, thumbsUp) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.outfit_lab_ratings_count, ratingCount),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedButton(
                    onClick = { exportRatings() },
                    enabled = ratingCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.outfit_lab_export))
                }
            }
        }
    }
}

@Composable
private fun SeedThumbnail(item: ClothingItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        DuplicatePhoto(item = item, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
            )
        }
    }
}

@Composable
private fun OutfitLabChipRow(tags: List<GarmentTag>, selectedId: String?, onSelected: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tags, key = GarmentTag::id) { tag ->
            FilterChip(
                selected = selectedId == tag.id,
                onClick = { onSelected(if (selectedId == tag.id) null else tag.id) },
                label = { Text(tag.localizedTagLabel()) },
            )
        }
    }
}

@Composable
private fun OutfitCard(
    outfit: Outfit,
    itemsById: Map<String, ClothingItem>,
    tagsById: Map<String, GarmentTag>,
    rating: Boolean?,
    onRate: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val sharedNames = outfit.sharedTagIds.mapNotNull { id -> tagsById[id]?.localizedTagLabel(context) }
    val shares = if (sharedNames.isEmpty()) {
        null
    } else {
        context.getString(R.string.outfit_lab_why_shares, sharedNames.joinToString(", "))
    }
    val colours = if (outfit.similarColours) context.getString(R.string.outfit_lab_why_similar_colours) else null
    val why = listOfNotNull(shares, colours).joinToString(" · ").ifEmpty {
        context.getString(R.string.outfit_lab_why_none)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                outfit.garmentIds.values.forEach { id ->
                    itemsById[id]?.let { item ->
                        DuplicatePhoto(item = item, modifier = Modifier.weight(1f).aspectRatio(0.8f))
                    }
                }
            }
            Text(text = why, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = rating == true,
                    enabled = rating == null,
                    onClick = { onRate(true) },
                    label = { Text(stringResource(R.string.outfit_lab_thumbs_up)) },
                )
                FilterChip(
                    selected = rating == false,
                    enabled = rating == null,
                    onClick = { onRate(false) },
                    label = { Text(stringResource(R.string.outfit_lab_thumbs_down)) },
                )
            }
        }
    }
}

/** The prompt's embedding with the search prefix, or null when the model cannot embed it. */
private suspend fun embedPrompt(app: RobiaApplication, prompt: String): FloatArray? =
    try {
        app.embeddingEngineHolder.embedText(EmbeddingRecipe.QUERY_PREFIX + prompt)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        null
    }

private fun countRatings(file: File): Int =
    if (file.exists()) file.useLines { lines -> lines.count { line -> line.isNotBlank() } } else 0
