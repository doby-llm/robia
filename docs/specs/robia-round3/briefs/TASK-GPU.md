# TASK-GPU — Developer benchmark: EmbeddingGemma 2 on GPU

## Goal
Manu asked whether models can use the GPU (R3-12). The app runs EmbeddingGemma 2 on CPU (~1.6 s/garment). LiteRT-LM offers a GPU backend; we measure it on the Poco F3 (Adreno 650) before planning any switch (artifacts/inv-r3-models.md §Verdict).

## Read first
`suggestions/engine/LiteRtNativeEmbedder.kt` l.15-35; `suggestions/DevBenchmark.kt` (`measure`, `runBenchmark`, parity cosine); `ui/SettingsScreen.kt` developer section l.147-270 (existing "Model benchmark" row).

## Files
`suggestions/engine/LiteRtNativeEmbedder.kt`; `suggestions/DevBenchmark.kt`; `ui/SettingsScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/GpuBenchmarkReportTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `LiteRtNativeEmbedder(cacheDir, modelPath, backend: Backend = Backend.CPU(), visionBackend: Backend = Backend.CPU())`.
2. `DevBenchmark.measure(useGpu: Boolean = false)`: with GPU builds a separate embedder with `Backend.GPU()` for both, runs load + 5 image embeddings of the bundled benchmark image, peak PSS, and parity = cosine(GPU vector, CPU vector of the same image) using the CPU embedder; closes the GPU engine afterwards. Any non-cancellation `Throwable` → report `GPU unavailable: <SimpleName>: <message>`.
3. Developer row "Model benchmark (GPU)" (string `dev_model_benchmark_gpu`) next to the existing one; result text as the CPU row; also `Log.i("RobiaBenchmark", report)`.

## Tests
V-GPU-1.

## Done when
V-GPU-1, V-GPU-2, V-GPU-D pass; CI green.

## Do not
Use GPU anywhere else, change `EmbeddingRecipe`, or bundle new native libraries without checking the LiteRT-LM AAR already contains the GPU delegate (if it does not, stop and report).
