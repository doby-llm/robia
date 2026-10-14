package com.gusanitolabs.robia.suggestions

/** Embedding model and recipe identity. Vectors from any other pair are ignored, never mixed. */
object EmbeddingRecipe {
    const val MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"
    const val RECIPE_ID = "eg2-r1"
    const val DIMENSION = 768
    const val MODEL_FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm"
    const val MODEL_SHA256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0"
    const val MODEL_APPROX_MB = 388
    const val VISION_TOKENS = 140
    const val IMAGE_MAX_EDGE_PX = 1024
    const val ENGINE_IDLE_RELEASE_MS = 60_000L
}
