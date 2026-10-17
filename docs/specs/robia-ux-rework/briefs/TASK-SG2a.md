# TASK-SG2a — Precise cutout engine, model pack, dev benchmark

## Goal
ML Kit leaks white walls/doors into white garments and keeps hangers (R7). The planner benchmarked 10 pipelines on Manu's 142 photos on a Raspberry Pi 5 (`artifacts/seg-benchmark.md`); the winner (v10) uses four small permissive models. Port it to Android as an optional downloadable pack with ML Kit as fallback, plus a developer benchmark used for gate G-SEG-1. Users see nothing yet (SG2b adds the Settings row).

## Read first
TS§SG2a (I/O contracts, constants, hashes); SEG-DEC-003/005/006; `ui/SettingsScreen.kt` Developer section (U05 layout); `tools/segmentation-bench/pipeline_v10.py` (in the repo since the planning PR) and its imports `bench.py` (`run`), `bench_v2.py` (`guided`, `magic`, `core_points`), `bench_v3.py` (`third_points`), `bench_v4.py` (`detect`), `bench_v6.py` (`MobileSam`) — this is the executable reference; `suggestions/model/ModelStore.kt`, `ModelDownloadWorker.kt`, `ui/ModelDownloadUi.kt` (download UX pattern), `suggestions/DevBenchmark.kt` (benchmark card pattern), `media/PhotoBackgroundRemover.kt`, `media/AlphaRefiner.kt` (SG1).

## Steps
1. Verify every model URL: `curl -sL <url> | sha256sum` and size equal the TS table. Mismatch → stop.
2. Gradle: `onnxruntime-android = "1.31.0"` (`com.microsoft.onnxruntime:onnxruntime-android`), `mediapipe-tasks-vision = "1.1.1"` (`com.google.mediapipe:tasks-vision`). `app/proguard-rules.pro`: `-keep class ai.onnxruntime.** { *; }` and `-keep class com.google.mediapipe.** { *; }` (plus whatever the libraries document); `androidResources { noCompress += "tflite" }` if required by MediaPipe.
3. `ModelStore`: add constructor params `fileName`, `expectedSha256`, `url` with defaults equal to today's Gemma constants; no behaviour change for Gemma (existing tests stay green). `CutoutPack` = list of 4 `ModelStore`-like entries in `filesDir/cutout_pack/`; pack state = all Ready; download sequentially with the same unmetered rule; delete removes all four.
4. `MaskOps.kt` (pure Kotlin on `FloatArray`/`BooleanArray` + w,h): `thirdPoints(hard)`, `distanceTransform` (two-pass chamfer is fine), `overlap`, `connectedComponents` (8-connectivity, iterative), `dropIslands(minFrac = 0.10)`, `bbox`, `dilate(k)`, `resizeBilinear`. Port behaviour from the Python, not the code style.
5. `OnnxSessions.kt`: helpers to run u2netp, D-FINE (decode boxes, sigmoid class 101, threshold 0.25, plausibility) and MobileSAM encoder/decoder with the exact tensor names/shapes of TS§SG2a; sessions created per photo and closed with `use {}`; 4 intra-op threads.
6. `InteractiveSelector.kt`: interface `select(bitmap, x, y): FloatArray` + MediaPipe implementation (`InteractiveSegmenter`, bundled `assets/models/magic_touch.tflite`, confidence masks); create once per photo, close after.
7. `HangerRemoval.kt` and `PreciseCutoutEngine.kt` implementing `BackgroundRemovalEngine`: stages S1-S5 exactly as TS; 30 s timeout; output via `ClothingImageStore.writeProcessedBitmap` (crop to alpha > 24, 4 % pad — reuse existing crop if it matches); per-stage timings in `BackgroundRemovalResult` diagnostics (add an optional `timings: Map<String, Long>` field).
8. Engine choice exactly as TS§SG2a "Engine selection": `CutoutEngineRegistry` set by `RobiaApplication`; setting `preciseCutoutEnabled` (default **false**, key `precise_cutout_enabled`, added to `RobiaSettings`/`SettingsRepository`/`toRobiaSettings()`); pure `useEnginePrecise(packReady, enabled)`; `PhotoBackgroundRemover()` keeps its no-arg constructor (call sites untouched) and reads the registry per photo: precise → on exception/timeout ML Kit → original.
9. Developer section in `ui/SettingsScreen.kt` (rows only): switch "Use precise cutout" bound to `preciseCutoutEnabled` + "Download cutout pack" + "Run cutout benchmark" (runs 2 rounds over `assets/benchmark/cutout/*.png` + last 5 garment photos; prints per-stage median/p90 ms and peak PSS; Copy).
10. `tools/segmentation-bench/make_cutout_benchmark.py`: draws 3 synthetic garment-on-hanger scenes (PIL: dress on a wire hanger against a light door; white top on white; skirt on a clip hanger) → `app/src/main/assets/benchmark/cutout/`. No real photos.
11. Report APK size delta; apply the abiFilters fallback if > 25 MB.

## Tests
`MaskOpsTest` (V-SG2-2: synthetic shapes with known answers, including a "hanger" thin bar above a rectangle and the 15 % cap), `ModelStoreTest` additions (V-SG2-3), `EngineChoiceTest` (fallback order with fakes, `useEnginePrecise` truth table).

## Gate message (coordinator relays to Manu after merge, Spanish)
"G-SEG-1: instala el APK de staging <link>. Settings > (7 toques en Settings) Developer > activa 'Use precise cutout' > 'Download cutout pack' (Wi-Fi, ~91 MB) > 'Run cutout benchmark'. Hazlo en tu móvil (Poco F3), enchufado, frío y sin ahorro de batería. Pulsa Copy y pégame el resultado. Pasa si: mediana sin percha ≤ 4000 ms, con percha ≤ 8000 ms, PSS ≤ 1300 MB."

## Done when
V-SG2-1..5; independent review; gate message in the receipt with the artifact link.

## Do not
Add a user-facing Settings row (SG2b), the refine screen (SG3), or commit personal photos; do not keep sessions resident between photos.
