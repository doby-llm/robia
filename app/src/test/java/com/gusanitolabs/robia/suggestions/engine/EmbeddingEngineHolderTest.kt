package com.gusanitolabs.robia.suggestions.engine

import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import com.gusanitolabs.robia.suggestions.model.ModelSource
import com.gusanitolabs.robia.suggestions.model.ModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class EmbeddingEngineHolderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun idleRelease_waitsSixtySecondsThenClosesEngine() {
        val requestedDelay = CompletableDeferred<Long>()
        val letIdleFinish = CompletableDeferred<Unit>()
        val embedder = FakeEmbedder()
        val holder = EmbeddingEngineHolder(
            modelStore = readyModelStore(),
            scope = scope,
            factory = { embedder },
            idleDelay = { milliseconds ->
                requestedDelay.complete(milliseconds)
                letIdleFinish.await()
            },
        )

        runBlocking {
            withTimeout(5_000) {
                holder.embedText("hello")
                assertEquals(60_000L, requestedDelay.await())
                assertEquals(EngineState.Ready, holder.state.value)
                letIdleFinish.complete(Unit)
                holder.state.first { it == EngineState.Idle }
            }
        }
        assertTrue(embedder.closed.get())
    }

    @Test
    fun calls_areSerialized() {
        val embedder = FakeEmbedder()
        val holder = EmbeddingEngineHolder(
            modelStore = readyModelStore(),
            scope = scope,
            factory = { embedder },
            idleDelay = { awaitCancellation() },
        )

        runBlocking {
            withTimeout(10_000) {
                coroutineScope {
                    repeat(20) { launch { holder.embedImage(ByteArray(1)) } }
                }
            }
        }
        assertEquals(1, embedder.maxInFlight.get())
    }

    @Test
    fun appStop_releasesEngine() {
        val embedder = FakeEmbedder()
        val holder = EmbeddingEngineHolder(
            modelStore = readyModelStore(),
            scope = scope,
            factory = { embedder },
            idleDelay = { awaitCancellation() },
        )

        runBlocking {
            withTimeout(5_000) {
                holder.embedText("hello")
                holder.release()
                holder.state.first { it == EngineState.Idle }
            }
        }
        assertTrue(embedder.closed.get())
    }

    @Test
    fun wrongDimension_isRejected() {
        val holder = EmbeddingEngineHolder(
            modelStore = readyModelStore(),
            scope = scope,
            factory = { FakeEmbedder(size = 3) },
            idleDelay = { awaitCancellation() },
        )

        assertThrows(EmbeddingFailure::class.java) {
            runBlocking { holder.embedText("hello") }
        }
    }

    @Test
    fun missingModel_isUnavailableAndNeverLoads() {
        var factoryCalls = 0
        val holder = EmbeddingEngineHolder(
            modelStore = ModelStore(File(temp.root, "models"), ModelSource { error("unused") }, isUnmetered = { true }),
            scope = scope,
            factory = {
                factoryCalls++
                FakeEmbedder()
            },
            idleDelay = { awaitCancellation() },
        )

        assertThrows(EmbeddingFailure::class.java) {
            runBlocking { holder.embedText("hello") }
        }
        assertEquals(EngineState.Unavailable(EngineUnavailableReason.ModelNotReady), holder.state.value)
        assertEquals(0, factoryCalls)
    }

    private fun readyModelStore(): ModelStore {
        val models = File(temp.root, "models").apply { mkdirs() }
        File(models, EmbeddingRecipe.MODEL_FILE_NAME).writeText("fake model")
        File(models, "${EmbeddingRecipe.MODEL_FILE_NAME}.sha256").writeText(EmbeddingRecipe.MODEL_SHA256)
        return ModelStore(models, ModelSource { error("unused") }, isUnmetered = { true })
    }

    private class FakeEmbedder(private val size: Int = EmbeddingRecipe.DIMENSION) : NativeEmbedder {
        val closed = AtomicBoolean(false)
        val maxInFlight = AtomicInteger(0)
        private val inFlight = AtomicInteger(0)

        override fun embedImage(png: ByteArray): FloatArray = compute()

        override fun embedText(text: String): FloatArray = compute()

        override fun close() {
            closed.set(true)
        }

        private fun compute(): FloatArray {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
            Thread.sleep(2)
            inFlight.decrementAndGet()
            return FloatArray(size) { 0.5f }
        }
    }
}
