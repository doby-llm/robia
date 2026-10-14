# TASK-E01 — Model store, engine holder, developer benchmark (device gate G-DEVICE-1)

## Goal
Make the phone able to download the 388 MB EmbeddingGemma 2 model safely (Wi-Fi, resumable, checksum), load it only when needed and release it after 60 s idle, and add a developer "Model benchmark" so Manu can measure on his Poco F3 whether suggestions fit in the 2-second budget **before** any UI is built on top. If the numbers fail, the suggestion lane stops here.

## Read first
TS§3.2, §3.3, §3.4, §3.12 (benchmark). DEC-003, DEC-005, DEC-013. `RobiaApplication.kt` (B03). Settings screen in `ui/RobiaApp.kt` (find the developer section: grep `developerMode`). LiteRT-LM Android API (verified with javap on 0.18.0):
```kotlin
import com.google.ai.edge.litertlm.*
val engine = EmbeddingEngine(EmbeddingEngineConfig(
    modelPath = path, backend = Backend.CPU(), visionBackend = Backend.CPU(),
    cacheDir = context.cacheDir.absolutePath, visionTokensPerImage = 140))
engine.initialize()
val r: EmbeddingResponse = engine.computeEmbedding(listOf(InputData.Image(pngBytes)), EmbeddingOptions(normalize = true, visionTokensPerImage = 140))
val v: FloatArray = r.embedding   // size 768
engine.close()
```
If a parameter name differs, open the class in the IDE/decompiled AAR and adapt; do not change behaviour.

## Steps
1. `EmbeddingRecipe.kt`: append model/runtime constants of TS§3.1 (`MODEL_FILE_NAME`, `MODEL_URL`, `MODEL_SHA256`, `MODEL_APPROX_MB`, `VISION_TOKENS = 140`, `IMAGE_MAX_EDGE_PX`, `ENGINE_IDLE_RELEASE_MS`).
2. `suggestions/engine/ImageInputEncoder.kt` per TS§3.2.
3. `suggestions/engine/LiteRtNativeEmbedder.kt` (wraps the API above, `interface NativeEmbedder { fun embedImage(png): FloatArray; fun embedText(text): FloatArray; fun close() }`).
4. `suggestions/engine/EmbeddingEngineHolder.kt` per TS§3.3 (Mutex, idle job, `state: StateFlow<EngineState>`, validation of size 768 and finite values). Inject `factory` and a `CoroutineScope` so tests use a fake embedder and `runTest` virtual time.
5. `suggestions/model/ModelStore.kt` + `ModelDownloadWorker.kt` per TS§3.4. Put the HTTP download behind `interface ModelSource { fun open(rangeStart: Long): ModelResponse(code, contentLength, stream) }` so tests use a fake. SHA-256 is computed while streaming plus once over the final file only if the stream was resumed.
6. `RobiaApplication`: create `modelStore`, `embeddingEngineHolder`; register `ProcessLifecycleOwner.get().lifecycle` observer (ON_STOP → `holder.release()`); override `onTrimMemory(level)` (≥ `TRIM_MEMORY_RUNNING_LOW` → release).
7. `suggestions/DevBenchmark.kt`: run on `Dispatchers.Default`: load (ms) → 5× (encode `assets/benchmark/garment.png` + `embedImage`) → 19 synthetic label texts embedded once (cached) → `suggestion_ms` = encode + embedImage + scoring over 19 labels; sample `Debug.getPss()` every 200 ms for peak; `parity_cosine` vs `assets/benchmark/garment_vector.b64`. Output text block exactly like `mockups/dev-diagnostics.html` (keys: device, vision_tokens, load_ms, image_ms median/p90, suggestion_ms median/p90, peak_pss_mb, parity_cosine).
8. Settings developer section (only when dev mode on): rows "Download model (dev)" (starts the worker, shows state text) and "Model benchmark" (Run + Copy). Final user UX is E07.
9. Strings for the two dev rows in 3 locales (copy: mockup `dev-diagnostics.html`).

## Tests (new)
`suggestions/model/ModelStoreTest` and `suggestions/engine/EmbeddingEngineHolderTest` as in tasks.md E01.

## Device gate (ask Manu via the orchestrator; send exactly this)
1. Install `robia-staging-apk` from this PR's CI run on the Poco F3 (over the current app).
2. Settings → enable developer mode → "Download model (dev)" on Wi-Fi; wait until "Ready".
3. "Model benchmark" → Run → Copy → paste the text in chat.
Pass: `suggestion_ms` median ≤ 2000, `peak_pss_mb` ≤ 1300, `parity_cosine` ≥ 0.98, no crash. Fail on latency → TASK-E01b. Other failure → Blocked, report.

## Done when
CI green; V-E01a; V-E01b numbers pasted in the PR.
