package com.gusanitolabs.robia.suggestions

import android.net.Uri
import com.gusanitolabs.robia.core.model.GarmentTag
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
