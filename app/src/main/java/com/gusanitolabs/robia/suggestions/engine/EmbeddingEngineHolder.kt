package com.gusanitolabs.robia.suggestions.engine

import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.suggestions.model.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface TextImageEmbedder {
    /** Normalized vector of size [EmbeddingRecipe.DIMENSION]. */
    suspend fun embedImage(png: ByteArray): FloatArray
    suspend fun embedText(text: String): FloatArray
}

sealed interface EngineState {
    data class Unavailable(val reason: EngineUnavailableReason) : EngineState
    data object Idle : EngineState
    data object Loading : EngineState
    data object Ready : EngineState
}

enum class EngineUnavailableReason { ModelNotReady, LoadFailed }

class EmbeddingFailure(message: String) : IllegalStateException(message)

/**
 * Lazy, singleton, serialized access to the native embedder (DEC-013). Every load and compute holds [lock];
 * the engine is closed after [EmbeddingRecipe.ENGINE_IDLE_RELEASE_MS] without calls, or by [release].
 */
class EmbeddingEngineHolder(
    private val modelStore: ModelStore,
    private val scope: CoroutineScope,
    private val factory: (modelPath: String) -> NativeEmbedder,
    private val idleDelay: suspend (Long) -> Unit = { delay(it) },
) : TextImageEmbedder {
    private val lock = Mutex()
    private var embedder: NativeEmbedder? = null
    private var idleJob: Job? = null
    private val mutableState = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = mutableState.asStateFlow()

    override suspend fun embedImage(png: ByteArray): FloatArray =
        withEmbedder { it.embedImage(png) }.checkedVector()

    override suspend fun embedText(text: String): FloatArray =
        withEmbedder { it.embedText(text) }.checkedVector()

    /** Fire-and-forget load; does nothing useful when the model is not on disk yet. */
    fun preload() {
        scope.launch { runCatching { ensureLoaded() } }
    }

    /** Loads the engine if needed and keeps it resident until the idle timeout. */
    suspend fun ensureLoaded() {
        lock.withLock {
            idleJob?.cancel()
            try {
                loadIfNeeded()
            } finally {
                scheduleIdleRelease()
            }
        }
    }

    /** Non-suspending entry point for lifecycle callbacks; the close waits for any compute in flight. */
    fun release() {
        scope.launch { unload() }
    }

    /** Closes the engine now, waiting for any compute in flight. */
    suspend fun unload() {
        lock.withLock {
            idleJob?.cancel()
            idleJob = null
            closeLoaded()
        }
    }

    private suspend fun <T> withEmbedder(block: (NativeEmbedder) -> T): T =
        lock.withLock {
            idleJob?.cancel()
            try {
                val current = loadIfNeeded()
                withContext(Dispatchers.Default) { block(current) }
            } finally {
                scheduleIdleRelease()
            }
        }

    private suspend fun loadIfNeeded(): NativeEmbedder {
        embedder?.let { return it }
        val modelPath = (modelStore.state.value as? ModelState.Ready)?.path ?: run {
            mutableState.value = EngineState.Unavailable(EngineUnavailableReason.ModelNotReady)
            throw EmbeddingFailure("Model is not downloaded")
        }
        mutableState.value = EngineState.Loading
        val loaded = try {
            withContext(Dispatchers.Default) { factory(modelPath) }
        } catch (failure: Exception) {
            mutableState.value = EngineState.Unavailable(EngineUnavailableReason.LoadFailed)
            throw EmbeddingFailure("Model failed to load: ${failure.message}")
        }
        embedder = loaded
        mutableState.value = EngineState.Ready
        return loaded
    }

    private fun scheduleIdleRelease() {
        idleJob = scope.launch {
            idleDelay(EmbeddingRecipe.ENGINE_IDLE_RELEASE_MS)
            lock.withLock { closeLoaded() }
        }
    }

    private suspend fun closeLoaded() {
        val current = embedder ?: return
        embedder = null
        try {
            withContext(Dispatchers.Default) { current.close() }
        } finally {
            if (mutableState.value !is EngineState.Unavailable) mutableState.value = EngineState.Idle
        }
    }

    private fun FloatArray.checkedVector(): FloatArray {
        if (size != EmbeddingRecipe.DIMENSION || any { !it.isFinite() }) {
            throw EmbeddingFailure("Unexpected embedding: size=$size")
        }
        return this
    }
}
