package com.gusanitolabs.robia.suggestions.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe

/** Blocking native calls; callers must run them off the main thread. */
interface NativeEmbedder {
    fun embedImage(png: ByteArray): FloatArray
    fun embedText(text: String): FloatArray
    fun close()
}

class LiteRtNativeEmbedder(cacheDir: String, modelPath: String) : NativeEmbedder {
    private val engine = EmbeddingEngine(
        EmbeddingEngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir = cacheDir,
            visionTokensPerImage = EmbeddingRecipe.VISION_TOKENS,
        ),
    ).also { it.initialize() }

    private val options = EmbeddingOptions(
        normalize = true,
        visionTokensPerImage = EmbeddingRecipe.VISION_TOKENS,
    )

    override fun embedImage(png: ByteArray): FloatArray =
        engine.computeEmbedding(listOf(InputData.Image(png)), options).embedding

    override fun embedText(text: String): FloatArray =
        engine.computeEmbedding(listOf(InputData.Text(text)), options).embedding

    override fun close() = engine.close()
}
