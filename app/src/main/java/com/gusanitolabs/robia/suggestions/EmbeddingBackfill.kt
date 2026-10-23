package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.suggestions.engine.EncodedImage
import com.gusanitolabs.robia.suggestions.engine.TextImageEmbedder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * [remaining] garments still waiting for a vector; [running] while the loop is active (TS§3.8). [settled] once a pass
 * found nothing left to do, so [remaining] = 0 is known rather than not yet computed (DUP1 scan gate).
 */
data class BackfillProgress(val remaining: Int = 0, val running: Boolean = false, val settled: Boolean = false)

/** One vector that is missing or stale for a garment. A [ClearText] removes a TEXT vector whose garment has no name or notes. */
private sealed interface BackfillTask {
    val garmentId: String

    data class Image(override val garmentId: String, val photoUri: String) : BackfillTask

    data class Text(override val garmentId: String, val text: String, val inputHash: String) : BackfillTask

    data class ClearText(override val garmentId: String) : BackfillTask
}

/** A garment is skipped for the rest of the process after this many failed attempts. */
private const val MAX_ATTEMPTS_PER_GARMENT = 2

/** Saved vectors per periodic sync request; each request uploads everything pending, so one per vector is O(N^2) bytes. */
private const val SYNC_EVERY_SAVES = 20

/**
 * Computes the missing IMAGE vectors and the stale TEXT vectors of saved garments, one at a time, newest first
 * (TS§3.8). It runs only while [resume]d and the model is Ready; [pause] stops it at the next suspension point.
 */
class EmbeddingBackfill(
    private val scope: CoroutineScope,
    private val embedder: TextImageEmbedder,
    private val isModelReady: () -> Boolean,
    private val encodeImage: suspend (photoUri: String) -> EncodedImage,
    private val loadGarments: suspend () -> List<ClothingItem>,
    private val loadStoredRows: suspend () -> List<GarmentEmbeddingEntity>,
    private val saveVector: suspend (garmentId: String, kind: String, vector: FloatArray, inputHash: String, durationMs: Long) -> Unit,
    private val deleteVector: suspend (garmentId: String, kind: String) -> Unit,
    /** Asks the sync processor for a run; [reason] is logged with it. */
    private val requestSync: (reason: String) -> Unit,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val _progress = MutableStateFlow(BackfillProgress())
    val progress: StateFlow<BackfillProgress> = _progress.asStateFlow()

    private val failures = ConcurrentHashMap<String, Int>()
    private val unsyncedSaves = AtomicInteger(0)
    private var job: Job? = null

    fun resume() {
        if (job?.isActive == true) return
        job = scope.launch { runUntilIdle() }
    }

    fun pause() {
        job?.cancel()
        job = null
    }

    /** Processes missing and stale vectors until none are left, the model is not Ready, or the coroutine is cancelled. */
    internal suspend fun runUntilIdle() {
        _progress.value = _progress.value.copy(running = true)
        try {
            while (isModelReady()) {
                currentCoroutineContext().ensureActive()
                val tasks = pendingTasks()
                // An empty list ends the pass: from then on [remaining] is known, so the duplicate scan may read the vectors.
                _progress.value = BackfillProgress(
                    remaining = tasks.map { it.garmentId }.distinct().size,
                    running = true,
                    settled = _progress.value.settled || tasks.isEmpty(),
                )
                val next = tasks.firstOrNull() ?: break
                runTask(next)
                yield()
            }
        } finally {
            flushSync()
            _progress.value = _progress.value.copy(running = false)
        }
    }

    /** One request per [SYNC_EVERY_SAVES] saved vectors, and one more when the pass ends, pauses or is cancelled. */
    private fun recordSave() {
        if (unsyncedSaves.incrementAndGet() >= SYNC_EVERY_SAVES) flushSync()
    }

    private fun flushSync() {
        if (unsyncedSaves.getAndSet(0) > 0) requestSync("backfill")
    }

    /** Image tasks first, then TEXT tasks; each group is newest garment first. Garments past the attempt limit are left out. */
    private suspend fun pendingTasks(): List<BackfillTask> {
        val garments = loadGarments()
            .filterNot { garment -> (failures[garment.id] ?: 0) >= MAX_ATTEMPTS_PER_GARMENT }
            .sortedByDescending { garment -> garment.createdAtEpochMillis }
        val stored = loadStoredRows().filter { row ->
            row.modelId == EmbeddingRecipe.MODEL_ID && row.recipeId == EmbeddingRecipe.RECIPE_ID
        }
        val storedImageIds = stored.filter { it.kind == EmbeddingKind.IMAGE }.map { it.garmentId }.toSet()
        val storedTextHashes = stored.filter { it.kind == EmbeddingKind.TEXT }.associate { it.garmentId to it.inputHash }

        val imageTasks = garments.mapNotNull { garment ->
            val photoUri = garment.photoUri
            if (photoUri != null && garment.id !in storedImageIds) BackfillTask.Image(garment.id, photoUri) else null
        }
        val textTasks = garments.mapNotNull { garment ->
            val text = garmentTextInput(garment.name, garment.notes)
            val storedHash = storedTextHashes[garment.id]
            when {
                text == null -> if (storedHash != null) BackfillTask.ClearText(garment.id) else null
                storedHash == null || storedHash != textHash(text) -> BackfillTask.Text(garment.id, text, textHash(text))
                else -> null
            }
        }
        return imageTasks + textTasks
    }

    private suspend fun runTask(task: BackfillTask) {
        try {
            when (task) {
                is BackfillTask.Image -> {
                    val started = clockMillis()
                    val encoded = encodeImage(task.photoUri)
                    val vector = embedder.embedImage(encoded.bytes)
                    saveVector(task.garmentId, EmbeddingKind.IMAGE, vector, sha256Hex16(encoded.bytes), clockMillis() - started)
                    recordSave()
                }
                is BackfillTask.Text -> {
                    val started = clockMillis()
                    val vector = embedder.embedText(task.text)
                    saveVector(task.garmentId, EmbeddingKind.TEXT, vector, task.inputHash, clockMillis() - started)
                    recordSave()
                }
                is BackfillTask.ClearText -> {
                    deleteVector(task.garmentId, EmbeddingKind.TEXT)
                    recordSave()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A failed garment is retried at most once more in this process, then skipped until the next start.
            failures.merge(task.garmentId, 1) { attempts, more -> attempts + more }
        }
    }

    private fun textHash(text: String): String = sha256Hex16(text.toByteArray(Charsets.UTF_8))
}
