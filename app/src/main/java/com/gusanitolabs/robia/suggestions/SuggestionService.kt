package com.gusanitolabs.robia.suggestions

import android.net.Uri
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.engine.EncodedImage
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** IMAGE vector of a saved garment and the tag ids it carries; the kNN examples for TS§3.6. */
data class SavedExample(val garmentId: String, val tagIds: List<String>, val imageVector: FloatArray)

sealed interface SuggestionOutcome {
    data object Unavailable : SuggestionOutcome

    class Ready(
        val categoryResult: CategoryResult,
        val occasionResult: OccasionResult?,
        val imageVector: FloatArray,
        val imageInputHash: String,
        val durationMs: Long,
    ) : SuggestionOutcome {
        /** Tags the editor may mark: the category only above its margin, plus the selected occasions. */
        val predictedTagIds: Set<String>
            get() = (listOfNotNull(categoryResult.marked) + occasionResult?.selected.orEmpty()).toSet()
    }
}

/**
 * Image vector computed at capture and kept until save, so it is not computed twice. Equality ignores the array.
 */
class SuggestionCapture(
    val photoUri: String,
    val imageVector: FloatArray,
    val imageInputHash: String,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is SuggestionCapture &&
            photoUri == other.photoUri &&
            imageInputHash == other.imageInputHash &&
            durationMs == other.durationMs

    override fun hashCode(): Int = listOf(photoUri, imageInputHash, durationMs).hashCode()
}

/**
 * Capture-time suggestions (TS§3.7). It never edits tags: the editor applies a Ready outcome through its
 * override-aware reconciliation.
 */
class SuggestionService(
    private val embedder: TextImageEmbedder,
    private val isModelReady: () -> Boolean,
    private val encodeImage: suspend (Uri) -> EncodedImage,
    private val savedExamples: suspend () -> List<SavedExample>,
    private val preloadEngine: () -> Unit,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val labelVectors = ConcurrentHashMap<String, FloatArray>()

    fun preload() = preloadEngine()

    suspend fun suggestForPhoto(
        photoUri: Uri,
        tags: List<GarmentTag>,
        selectedCategoryId: String?,
        excludeGarmentId: String? = null,
    ): SuggestionOutcome = suggestForEncodedImage(tags, selectedCategoryId, excludeGarmentId) { encodeImage(photoUri) }

    /** Scoring path behind [suggestForPhoto]; takes the encoder output so it can run without an Android `Uri`. */
    internal suspend fun suggestForEncodedImage(
        tags: List<GarmentTag>,
        selectedCategoryId: String?,
        excludeGarmentId: String?,
        encode: suspend () -> EncodedImage,
    ): SuggestionOutcome {
        if (!isModelReady()) return SuggestionOutcome.Unavailable
        val startedAt = clockMillis()
        return try {
            val encoded = encode()
            val image = embedder.embedImage(encoded.bytes)
            val (categoryResult, occasionResult) = scoreImage(image, tags, selectedCategoryId, excludeGarmentId)
            SuggestionOutcome.Ready(
                categoryResult = categoryResult,
                occasionResult = occasionResult,
                imageVector = image,
                imageInputHash = sha256Hex16(encoded.bytes),
                durationMs = clockMillis() - startedAt,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Suggestions are optional: any failure leaves the photo and its tags exactly as they are.
            SuggestionOutcome.Unavailable
        }
    }

    /** Category and occasion results for one IMAGE vector against the current tags and saved examples (TS§3.6). */
    private suspend fun scoreImage(
        image: FloatArray,
        tags: List<GarmentTag>,
        selectedCategoryId: String?,
        excludeGarmentId: String?,
    ): Pair<CategoryResult, OccasionResult?> {
        val examples = savedExamples()
            .filter { example -> example.garmentId != excludeGarmentId }
            .flatMap { example -> example.tagIds.map { tagId -> tagId to example.imageVector } }
            .groupBy({ it.first }, { it.second })

        val (categoryScored, categoryMode) = scoreLabels(image, tagLabels(tags, "category"), examples)
        val categoryResult = selectCategory(categoryScored, categoryMode)

        // The occasion rule uses the category the user picked, or else the one marked here.
        val resultingCategoryId = selectedCategoryId ?: categoryResult.marked
        val suggestOccasions = resultingCategoryId?.let { id -> tags.firstOrNull { it.id == id }?.suggestOccasions } ?: true
        val occasionLabels = if (suggestOccasions) tagLabels(tags, "occasion") else emptyList()
        val occasionResult = when {
            !suggestOccasions -> selectOccasions(emptyList(), categoryMode, suggestOccasions = false)
            occasionLabels.isEmpty() -> null
            else -> {
                val (occasionScored, occasionMode) = scoreLabels(image, occasionLabels, examples)
                selectOccasions(occasionScored, occasionMode, suggestOccasions = true)
            }
        }
        return categoryResult to occasionResult
    }

    /**
     * Developer diagnostics of a saved garment (TS§3.12). Reuses its stored IMAGE vector, so no image inference runs;
     * only label vectors may be computed. Null when the model is not Ready or the garment has no current IMAGE vector.
     */
    suspend fun diagnose(
        garment: ClothingItem,
        tags: List<GarmentTag>,
        storedRows: List<GarmentEmbeddingEntity>,
    ): SuggestionDiagnostics? {
        if (!isModelReady()) return null
        val rows = storedRows.filter { row -> row.modelId == EmbeddingRecipe.MODEL_ID && row.recipeId == EmbeddingRecipe.RECIPE_ID }
        val imageRow = rows.firstOrNull { row -> row.kind == EmbeddingKind.IMAGE } ?: return null
        val textRow = rows.firstOrNull { row -> row.kind == EmbeddingKind.TEXT }
        val selectedCategoryId = garment.tags.firstOrNull { tag -> tag.categoryId == "category" }?.id
        val (categoryResult, occasionResult) = try {
            scoreImage(imageRow.vector.toFloatArrayLe(), tags, selectedCategoryId, excludeGarmentId = garment.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return null
        }
        return SuggestionDiagnostics(
            categoryResult = categoryResult,
            occasionResult = occasionResult,
            modelId = imageRow.modelId,
            recipeId = imageRow.recipeId,
            computedAtEpochMillis = imageRow.computedAtEpochMillis,
            imageDurationMs = imageRow.durationMillis,
            textDurationMs = textRow?.durationMillis,
            inputsPresent = buildList {
                add("image")
                if (textRow != null) add("text(name+notes)")
            },
        )
    }

    /**
     * Fresh IMAGE vector of a garment photo for "Recompute embedding". Returns null when the model is not Ready or any
     * step fails; the caller stores the result only when it is non-null, so the stored vector is kept on failure.
     */
    suspend fun recomputeImage(photoUri: Uri): SuggestionCapture? {
        if (!isModelReady()) return null
        return try {
            val started = clockMillis()
            val encoded = encodeImage(photoUri)
            val vector = embedder.embedImage(encoded.bytes)
            SuggestionCapture(photoUri.toString(), vector, sha256Hex16(encoded.bytes), clockMillis() - started)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            null
        }
    }

    private suspend fun tagLabels(tags: List<GarmentTag>, categoryId: String): List<Pair<String, FloatArray>> =
        tags.filter { tag -> tag.categoryId == categoryId }.map { tag -> tag.id to labelVector(tag) }

    private suspend fun labelVector(tag: GarmentTag): FloatArray {
        val text = labelTextFor(tag)
        val key = "${EmbeddingRecipe.MODEL_ID}|${EmbeddingRecipe.RECIPE_ID}|$text"
        labelVectors[key]?.let { return it }
        return embedder.embedText(text).also { labelVectors[key] = it }
    }
}

/** First 16 hex characters of SHA-256 (TS§3.1). */
internal fun sha256Hex16(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { "%02x".format(it) }.take(16)

/** Stored IMAGE vector facts and the scoring of a saved garment, for the developer diagnostics card (TS§3.12). */
data class SuggestionDiagnostics(
    val categoryResult: CategoryResult,
    val occasionResult: OccasionResult?,
    val modelId: String,
    val recipeId: String,
    val computedAtEpochMillis: Long,
    val imageDurationMs: Long,
    val textDurationMs: Long?,
    val inputsPresent: List<String>,
)

/** Monospace lines of [SuggestionDiagnostics]; technical text, not translated. */
fun SuggestionDiagnostics.monospaceLines(): List<String> = buildList {
    add("mode=${categoryResult.mode}  margin=${formatScore(categoryResult.margin)}  marked=${categoryResult.marked ?: "none"}")
    add("reason=${categoryResult.reason}")
    categoryResult.ranked.take(DIAGNOSTIC_TOP).forEach { add("  category ${it.diagnosticLine()}") }
    occasionResult?.let { occasion ->
        add("occasion g12=${occasion.g12?.let(::formatScore) ?: "n/a"} g23=${occasion.g23?.let(::formatScore) ?: "n/a"} selected=${occasion.selected.ifEmpty { listOf("none") }.joinToString()}")
        occasion.ranked.take(DIAGNOSTIC_TOP).forEach { add("  occasion ${it.diagnosticLine()}") }
    }
    add("model=$modelId recipe=$recipeId")
    add("inputs=${inputsPresent.joinToString()}")
    add("image ${imageDurationMs} ms · text ${textDurationMs?.let { "$it ms" } ?: "n/a"}")
}

/** Developer diagnostics lines for an outcome (TS§3.12). */
fun SuggestionOutcome.diagnosticLines(): List<String> = when (this) {
    SuggestionOutcome.Unavailable -> listOf("Suggestions: unavailable (model not ready)")
    is SuggestionOutcome.Ready -> buildList {
        add("Suggestions: mode=${categoryResult.mode}, duration=${durationMs}ms")
        categoryResult.ranked.take(DIAGNOSTIC_TOP).forEach { add("  category ${it.diagnosticLine()}") }
        add("  category margin=${formatScore(categoryResult.margin)} marked=${categoryResult.marked ?: "none"} reason=${categoryResult.reason}")
        occasionResult?.let { occasion ->
            occasion.ranked.take(DIAGNOSTIC_TOP).forEach { add("  occasion ${it.diagnosticLine()}") }
            add(
                "  occasion g12=${occasion.g12?.let(::formatScore) ?: "n/a"} g23=${occasion.g23?.let(::formatScore) ?: "n/a"} " +
                    "selected=${occasion.selected.ifEmpty { listOf("none") }.joinToString()} reason=${occasion.reason}",
            )
        }
    }
}

private const val DIAGNOSTIC_TOP = 5

private fun LabelScore.diagnosticLine(): String =
    "$tagId text=${formatScore(text)} knn=${knn?.let(::formatScore) ?: "n/a"} score=${formatScore(score)}"

private fun formatScore(value: Float): String = String.format(Locale.US, "%.4f", value)
