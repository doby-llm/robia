# TASK-SG1 — ML Kit reliability and soft edges

## Goal
Photo cleanup fails "randomly" with `MODEL_UNAVAILABLE: Waiting for the subject segmentation optional module to be downloaded` and Retry sometimes works (R4); edges look stair-stepped (R8). Root cause (certain, SEG-DEC-001, `artifacts/inv-r2-c.md` §R4): the app never asks Play services for the optional module and never waits/retries; the alpha is ML Kit's foreground bitmap used as-is at full input resolution, and thumbnails are power-of-two subsampled. Fix both without changing the fallback contract (original photo is always kept).

## Read first
TS§SG1; `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt` (all), `media/PhotoBackgroundRemover.kt`, `media/BackgroundRemovalEngine.kt`, `media/ClothingImageStore.kt` (`writeProcessedBitmap` 238-252, `getOrCreateBoundedThumbnail` 254-314, `thumbnailDecodeSampleSize`), `ui/AddEditClothingScreen.kt` (`processSelectedPhoto` 319-492, fallback status 467-483, retry 1608-1617), `ui/BatchAddClothingScreen.kt` (`processBatchDraft` 483-566, retry only for Failed/Interrupted 571-572, UI 378-382), `RobiaApplication.kt`; the official Google Play services documentation page "Ensuring API availability with ModuleInstallClient" (`ModuleInstall.getClient`, `areModulesAvailable`, `installModules`, `ModuleInstallRequest`, `InstallStatusListener`); reference Python `tools/segmentation-bench/bench_v2.py` `guided()`.

## Steps
1. Gradle: `play-services-base = "18.11.1"` in the catalog + `implementation(libs.play.services.base)`.
2. `SegmentationModuleGate.kt`: `interface ModuleInstallPort { suspend fun isAvailable(): Boolean; suspend fun requestInstall(): Boolean }`; `PlayModuleInstallPort(context)` wrapping `ModuleInstall.getClient(context)` with the segmenter client as `OptionalModuleApi` (`SubjectSegmentation.getClient(options)` implements it; if it does not compile, stop and report the exact compiler message); `class SegmentationModuleGate(port: ModuleInstallPort, delayFn: suspend (Long) -> Unit = { delay(it) })` with `suspend fun ensureAvailable(timeout: Duration = 60.seconds, onPreparing: (Boolean) -> Unit = {}): GateResult` (`sealed interface GateResult { data object Available; data class Unavailable(val reason: String) }`); after `requestInstall` it polls `isAvailable()` every 2 s through `delayFn` until timeout, calling `onPreparing(true/false)` around the wait.
3. `RobiaApplication`: after startup idle (where other idle work is scheduled, or `ProcessLifecycleOwner` start), launch `gate.ensureAvailable(60.seconds)` once in the app scope; never on the main thread; ignore result (it only warms).
4. Engine: before `process`, `gate.ensureAvailable(timeout = 60.seconds, onPreparing = ...)`; during the wait the UI shows `background_removal_preparing` (expose a `StateFlow<Boolean> preparing` from the gate or a callback the screen already observes — choose the smallest change; explain in receipt). On `MlKitException` UNAVAILABLE: wait via the gate once more, then process again once; otherwise fallback as today with reason `MODEL_UNAVAILABLE`.
5. Options: add `enableForegroundConfidenceMask()`. Build alpha: `AlphaRefiner.refine(argb: IntArray, conf: FloatArray, w, h)` (smoothstep 0.35-0.65 → guided filter on luminance r = round(3 × max(w,h)/1024), eps 1e-3 → clamp interior > 0.97 to 1) → compose ARGB with original RGB → `writeProcessedBitmap`. If the confidence mask is null, use the foreground bitmap's alpha as `conf`.
6. Input: decode the source with EXIF orientation applied and long edge ≤ 2048 (reuse an existing decode helper if present), `InputImage.fromBitmap(bitmap, 0)`.
7. Thumbnails: change `thumbnailDecodeSampleSize` so the decoded long edge is ≥ 2 × target (then bilinear `createScaledBitmap(…, true)` as today).
8. User copy: on fallback with MODEL_UNAVAILABLE show `background_removal_fallback_module`; other reasons keep the current text.
9. Batch: show the existing Retry action also for `NeedsReview` items whose reason is a background fallback (`retryBatchDraft`).

## Tests
`SegmentationModuleGateTest` (fake port + fake `delayFn` that advances a counter instead of sleeping; same injected-delay pattern as `EmbeddingEngineHolder.idleDelay`), `MlKitEngineFlowTest` (fake segmenter port: wrap `segmenter.process` behind a small interface so the flow is testable), `AlphaRefinerTest` (V-SG1-3).

## Done when
V-SG1-1..4; independent review.

## Do not
Replace ML Kit, add the precise engine (SG2a), change cropping padding/threshold, change what is stored on fallback.
