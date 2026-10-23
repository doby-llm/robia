# TASK-K01 — Cutout diagnostics: stage dump and engine marker (debug)

## Goal
The Python reference pipeline keeps Manu's pink shorts complete; the app destroys them (artifacts/inv-r3-cutout.md §1). Static reading found several candidates (inv-r3-f.md §1–2) but no proof. We need the app's intermediate masks on the real phone, stage by stage, with the same file names as `tools/cutout-diag/reference_stages.py`, so `compare.py` points at the first divergent stage. We also need to know, for every cutout, which engine produced it (today a precise failure silently falls back to ML Kit).

## Read first
`media/cutout/PreciseCutoutEngine.kt` (`removeBackground` l.58, `alpha()` l.152-170, `stage()` l.337, `hangerFirst` l.256-272, `cropToAlpha`); `media/cutout/HangerRemoval.kt` (`applyBand` l.148); `media/cutout/CutoutEngineRegistry.kt` `cutoutFallbackChain` l.44-58; `media/PhotoBackgroundRemover.kt`; `MainActivity.kt` debug extras l.55-75 (`performanceFixtureUris` pattern, `BuildConfig.DEBUG`); `tools/cutout-diag/reference_stages.py` and `compare.py` (file names, report keys).

## Files
new `media/cutout/CutoutDebugDump.kt`; `media/cutout/PreciseCutoutEngine.kt`; `media/PhotoBackgroundRemover.kt`; `media/cutout/CutoutEngineRegistry.kt`; `MainActivity.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutFallbackChainTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutDebugDumpTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `CutoutDebugDump.kt`: `internal interface StageSink { fun mask(stage: String, values: FloatArray, w: Int, h: Int); fun image(stage: String, argb: IntArray, w: Int, h: Int); fun json(stage: String, json: String) }` with `object NoStageSink : StageSink` (all no-op). File-writing implementation `DirectoryStageSink(dir: File)` writes grayscale PNG (0..1 → 0..255) / ARGB PNG / text. Pure helpers `stageFileName(stage)` and `reportJson(...)` (org.json) for the tests.
2. `PreciseCutoutEngine`: constructor/`run` gets `sink: StageSink = NoStageSink`; after S1 call `sink.mask("s1_saliency", ...)`, after S2 `s2_selection` plus the prompt points in the report, S3 `s3_hanger` json `{"box":[x0,y0,x1,y1]|null}`, S4 `s4_kill`, S4b `s4b_filled` (image) and `s4b_reselect` (the band-applied selection), S5 `s5_alpha` (alpha/255), final cropped cutout `final`. Only call the sink when it is not `NoStageSink` (no extra allocation in production).
3. Fallback reason: `cutoutFallbackChain` gains `onOutcome: (CutoutOutcome) -> Unit = {}` with `data class CutoutOutcome(val engine: String /*precise|mlkit*/, val fallback: String /*none|not_active|tripped|timeout|error|not_removed*/, val ms: Long)`. `PhotoBackgroundRemover` logs `Log.i("RobiaCutout", "cutout_result engine=… fallback=… ms=…")` for every photo.
4. Debug runner: `MainActivity` honours `--ez com.gusanitolabs.robia.CUTOUT_DEBUG true` only when `BuildConfig.DEBUG`. It launches (app scope, `Dispatchers.Default`) a loop over `getExternalFilesDir(null)/cutout-debug/in/*.jpg|*.jpeg|*.png`: for each, run the precise pipeline with a `DirectoryStageSink(out/<stem>/)` (pack must be ready; otherwise write `report.json` with `"error":"pack not ready"`), then ML Kit into `out/<stem>/mlkit.png`, write `report.json`, and log `cutout_debug_done stem=… precise_ms=… mlkit_ms=…`. A final `cutout_debug_all_done n=…` line.
5. Add `tools/cutout-diag/README.md` section "App dump" only if the planning PR's README lacks it (it already documents the adb commands).

## Tests
`CutoutFallbackChainTest` (V-K01-1) with fakes for each outcome. `CutoutDebugDumpTest` (V-K01-2): file-name table, report keys, `NoStageSink` identity check.

## Done when
V-K01-1, V-K01-2, V-K01-3, V-K01-D pass; CI green.

## Do not
Change any threshold, stage order or result; run the dump in release/staging builds; write outside `getExternalFilesDir(null)/cutout-debug/`.
