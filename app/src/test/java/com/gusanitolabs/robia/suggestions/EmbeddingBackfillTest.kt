package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.engine.EmbeddingFailure
import com.gusanitolabs.robia.suggestions.engine.EncodedImage
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-E06a: backfill order, stale TEXT recomputation, and skipping a garment after two failures. */
class EmbeddingBackfillTest {
    private val embedder = RecordingEmbedder()
    private val garments = mutableListOf<ClothingItem>()
    private val rows = mutableListOf<GarmentEmbeddingEntity>()
    private val saved = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private var syncRequests = 0

    private val backfill = newBackfill()

    private fun newBackfill(isModelReady: () -> Boolean = { true }) = EmbeddingBackfill(
        scope = CoroutineScope(Dispatchers.Unconfined),
        embedder = embedder,
        isModelReady = isModelReady,
        encodeImage = { photoUri -> EncodedImage(photoUri.toByteArray(), 1, 1) },
        loadGarments = { garments.toList() },
        loadStoredRows = { rows.toList() },
        saveVector = { garmentId, kind, _, inputHash, _ ->
            saved += "$garmentId:$kind"
            rows.removeAll { it.garmentId == garmentId && it.kind == kind }
            rows += row(garmentId, kind, inputHash)
        },
        deleteVector = { garmentId, kind ->
            deleted += "$garmentId:$kind"
            rows.removeAll { it.garmentId == garmentId && it.kind == kind }
        },
        requestSync = { syncRequests++ },
        clockMillis = { 0L },
    )

    @Test
    fun backfillOf45VectorsRequestsSyncThreeTimesNotFortyFive() = runBlocking {
        (1..45).forEach { index -> garments += garment("g$index", createdAt = index.toLong(), photoUri = "photo-$index", name = "") }

        backfill.runUntilIdle()

        assertEquals(45, saved.size)
        // Periodic requests after vectors 20 and 40, then one final request when the pass ends.
        assertEquals(3, syncRequests)
    }

    @Test
    fun imagesNewestFirstThenTextNewestFirst() = runBlocking {
        garments += garment("old", createdAt = 1L, photoUri = "photo-old", name = "Shirt")
        garments += garment("new", createdAt = 3L, photoUri = "photo-new", name = "Dress")
        garments += garment("middle", createdAt = 2L, photoUri = "photo-middle", name = "Skirt")

        backfill.runUntilIdle()

        assertEquals(listOf("new:image", "middle:image", "old:image", "new:text", "middle:text", "old:text"), saved)
        assertEquals(listOf("photo-new", "photo-middle", "photo-old"), embedder.imageInputs)
    }

    @Test
    fun staleTextIsRecomputedAndCurrentTextIsKept() = runBlocking {
        garments += garment("stale", createdAt = 2L, photoUri = null, name = "Red dress")
        garments += garment("current", createdAt = 1L, photoUri = null, name = "Blue jeans")
        val currentHash = sha256Hex16(garmentTextInput("Blue jeans", "")!!.toByteArray(Charsets.UTF_8))
        rows += row("stale", EmbeddingKind.TEXT, inputHash = "outdated")
        rows += row("current", EmbeddingKind.TEXT, inputHash = currentHash)

        backfill.runUntilIdle()

        assertEquals(listOf("stale:text"), saved)
        assertEquals(listOf(garmentTextInput("Red dress", "")!!), embedder.textInputs)
    }

    @Test
    fun garmentIsSkippedAfterTwoFailures() = runBlocking {
        garments += garment("broken", createdAt = 2L, photoUri = "bad-photo", name = "")
        garments += garment("fine", createdAt = 1L, photoUri = "photo-fine", name = "")

        backfill.runUntilIdle()

        assertEquals(2, embedder.imageInputs.count { it == "bad-photo" })
        assertEquals(listOf("fine:image"), saved)
        // A garment that keeps failing is left out of the pass, so the pass still ends and settles.
        assertEquals(BackfillProgress(remaining = 0, running = false, settled = true), backfill.progress.value)
    }

    @Test
    fun settlesOnlyWhenAPassFinishes() = runBlocking {
        val notReady = newBackfill(isModelReady = { false })
        garments += garment("pending", createdAt = 1L, photoUri = "photo-pending", name = "")

        notReady.runUntilIdle()
        assertEquals(BackfillProgress(remaining = 0, running = false, settled = false), notReady.progress.value)

        backfill.runUntilIdle()
        assertEquals(BackfillProgress(remaining = 0, running = false, settled = true), backfill.progress.value)
    }

    @Test
    fun emptyWardrobeSettlesOnTheFirstPass() = runBlocking {
        backfill.runUntilIdle()

        assertEquals(BackfillProgress(remaining = 0, running = false, settled = true), backfill.progress.value)
    }

    @Test
    fun blankTextClearsStoredTextVector() = runBlocking {
        garments += garment("blank", createdAt = 1L, photoUri = null, name = "", notes = "")
        rows += row("blank", EmbeddingKind.TEXT, inputHash = "anything")

        backfill.runUntilIdle()

        assertEquals(listOf("blank:text"), deleted)
        assertEquals(emptyList<String>(), saved)
    }

    private fun garment(
        id: String,
        createdAt: Long,
        photoUri: String?,
        name: String,
        notes: String = "",
    ) = ClothingItem(
        id = id,
        name = name,
        notes = notes,
        photoUri = photoUri,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt,
    )

    private fun row(garmentId: String, kind: String, inputHash: String) = GarmentEmbeddingEntity(
        garmentId = garmentId,
        kind = kind,
        modelId = EmbeddingRecipe.MODEL_ID,
        recipeId = EmbeddingRecipe.RECIPE_ID,
        dim = EmbeddingRecipe.DIMENSION,
        vector = ByteArray(EmbeddingRecipe.DIMENSION * Float.SIZE_BYTES),
        inputHash = inputHash,
        computedAtEpochMillis = 0L,
        durationMillis = 0L,
        pendingUpload = false,
    )

    private class RecordingEmbedder : TextImageEmbedder {
        val imageInputs = mutableListOf<String>()
        val textInputs = mutableListOf<String>()

        override suspend fun embedImage(png: ByteArray): FloatArray {
            val input = png.decodeToString()
            imageInputs += input
            if (input.startsWith("bad")) throw EmbeddingFailure("broken image")
            return FloatArray(EmbeddingRecipe.DIMENSION)
        }

        override suspend fun embedText(text: String): FloatArray {
            textInputs += text
            return FloatArray(EmbeddingRecipe.DIMENSION)
        }
    }
}
