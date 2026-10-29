# TASK-FB3-GPU-DROP — Remove the GPU benchmark row

## Goal
ui3 F31 (REQ-FB-31, R3-DEC-066). Manu re-ran "Model benchmark (GPU)" and it still fails with `TensorBuffer must have all static dimensions`. That is the G-ADB1 verdict: this model has dynamic tensor dimensions and the LiteRT GPU backend cannot run it; production is on CPU. The dev-only row cannot work, so it goes.

## Read first
`artifacts/inv-f3-c.md` F31; `R3-DEC-066`, `R3-DEC-021` section 4 (the G-ADB1 GPU verdict); `briefs/TASK-GPU.md` (what was added); `suggestions/DevBenchmark.kt` (`measure`, `runGpuBenchmark`, `gpuReport`, `deviceLine`, the companion constants, `gpuBenchmarkReport`, `gpuUnavailableReport`); `suggestions/engine/LiteRtNativeEmbedder.kt`; `ui/SettingsScreen.kt` (the benchmark `ListItem`s and `benchmarkText` / `benchmarkRunning` / `benchmarkGpuText` / `benchmarkGpuRunning`); `GpuBenchmarkReportTest`.

## Files
`suggestions/DevBenchmark.kt`; `ui/SettingsScreen.kt` (the GPU row and its state only); the three `strings.xml` (`dev_model_benchmark_gpu`); delete `app/src/test/java/com/gusanitolabs/robia/suggestions/GpuBenchmarkReportTest.kt`.

## Steps
1. **Remove**, running `grep -rn "<symbol>" app/src scripts tools` before and after for each and pasting the "after" result (zero hits) in the receipt: `useGpu` (the `measure` parameter, its KDoc and the `if (useGpu)` branch; `measure()` stays and runs the CPU benchmark), `runGpuBenchmark`, `gpuReport`, `gpuBenchmarkReport`, `gpuUnavailableReport`, `GPU_LOG_TAG` (only the GPU path used it), `Backend.GPU` and the `Backend` import in `DevBenchmark.kt` if nothing else uses it, `benchmarkGpuText`, `benchmarkGpuRunning` (and the `!benchmarkGpuRunning` terms in the CPU row's `enabled`), the GPU `ListItem` with its preceding `Divider()`, `dev_model_benchmark_gpu` in `values`, `values-es`, `values-de`, and the file `GpuBenchmarkReportTest.kt`. Remove helpers and imports that become unused only because of this (`deviceLine` if the CPU report builds its own device line, `dot`, `normalized`, `ImageInputEncoder` imports as the compiler and a grep say).
2. **Stays** (grep to show): `DevBenchmark.measure()` and the CPU benchmark; the CPU row of `SettingsScreen.kt` and `dev_benchmark_running`, `dev_benchmark_failed`, `dev_run`, `dev_copy`, `dev_model_benchmark`; `LiteRtNativeEmbedder` with its `backend` / `visionBackend` parameters and their `Backend.CPU()` defaults (production builds the embedder on CPU; a future static-shape retry needs the parameters); `EmbeddingEngineHolder` (including `unload()`); the Python tools under `tools/` (they use `Backend.CPU()`).
3. Do not run Android builds on the Pi; CI is the check (rule 3). No new test is needed: the deleted test covered only the deleted formatter.
4. Receipt: the per-symbol grep table; the sentence for the decision log: "GPU is not supported by embeddinggemma-2-text-vision-440m (dynamic dimensions); a retry needs a static-shape export of the model."

## Tests
V-FB3-GPU-DROP-1 (independent review: the grep table); V-FB3-GPU-DROP-2 (CI); V-FB3-GPU-DROP-D (Manu).

## Done when
Rows pass; CI green; the independent review confirms every symbol is gone and the CPU benchmark and production path are untouched.

## Do not
Change the production backend, the model or the recipe, `LiteRtNativeEmbedder`'s defaults, the CPU report format, or any file outside Files.
