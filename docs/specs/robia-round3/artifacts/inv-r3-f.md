# Robia cutout investigation (read-only, static code only)

Scope: I read the source and the Python reference. I did not run any model, Gradle, or the photos. Anything about the actual photo is HYPOTHESIS. Paths are relative to `app/src/main/java/com/gusanitolabs/robia/` unless shown otherwise.

## 0. Engine map and how a user reaches each engine (CERTAIN)

- **Defaults:** engine `Automatic`, hanger removal `true`, sensitivity `Normal`, edges `Soft` (`core/model/ClothingModels.kt:129-133`).
- **Settings UI:** `ui/PreciseCutoutSettings.kt`. Engine options at :224. Hanger toggle at :175-183 and sensitivity at :235-240 are enabled only when `packReady && Automatic` (:64-65, :86-87). Edges at :246-251.
- **Persistence:** `data/SettingsRepository.kt:33-40`.
- **Batch path:** `RobiaApplication.kt:100` and `:198` build options with `cutoutOptionsOf(settings, packReady)`.
- **`cutoutOptionsOf`** (`media/cutout/CutoutOptions.kt:24-32`): `usePrecise = packReady && Automatic`. Pack not downloaded means ML Kit runs silently, even with Automatic selected.
- **Redo sheet:** `ui/PhotoEditorScreen.kt:356` calls `redoCutoutOptions(choice, CutoutEngineRegistry.options())`. Choices are offered at :630 only when pack ready and not tripped. `CutoutOptions.kt:44`: "Precise" forces `removeHanger=true` even if the global toggle is off. "PreciseKeepHanger" (:45) sets it false.
- **Fallback chain** (`media/PhotoBackgroundRemover.kt:16-31`, `media/cutout/CutoutEngineRegistry.kt:44-58`): precise, with a 30 s timeout (:12), accepted only if `REMOVED`. Any null, timeout, or throwable goes to ML Kit. **The photo the user saw may be ML Kit output.** ML Kit does no hanger removal, which would also explain a kept hanger.

## 1. Stage-by-stage trace and Python parity

Kotlin: `media/cutout/PreciseCutoutEngine.kt`. Python: `tools/segmentation-bench/pipeline_v11.py` with helpers.

| Stage | Kotlin | Python | Status |
|---|---|---|---|
| Decode to work size, long edge 1024 | `workSize` :371-376, `decodeWorkBitmap` :382-420 | `load`/`thumbnail` (`bench.py:22-25`) | Equal. Small photos are not upscaled (see D2). |
| S1 u2netp saliency | `saliency` :173-191 | `run()` (`bench.py:28-44`) | Minor divergence D3 |
| S2 magic_touch at 3 points, ≥60 % overlap | `garmentSelection` :194-205, `thirdPoints` (`MaskOps.kt:91-120`) | `coarse_and_select` (`pipeline_v11.py:30-39`), `third_points` (`bench_v3.py:26-38`) | Logic equal. Distance metric differs (D1). |
| S3 D-FINE hanger, top-5, threshold, plausibility, second-pass region | `findHanger`, `hangerSearchRegion`, `isPlausible` (`HangerRemoval.kt:23-64`), `detectHangers` :220-243 | `hanger_mask` :57-75, `detect` (`bench_v4.py:25-40`) | Equal. HANGER=101 matches (`bench_v4.py:22`). |
| S4 SAM box-only hanger mask, garment SAM with core points and negative point, kill rule, 15 % cap | `hangerKill` :246-253, `killMask` :74-102 | `hanger_mask` :76-87 | Logic equal. Core-point metric differs (D1). |
| S4b median fill (ring 25, blur 9×9), reselect on filled photo, band rule | `hangerFirst` :256-272, `fill` :111-138, `applyBand` :148-155 | `pipeline_v11.py:143-161` | Equal for the BAND=1 FILL=median variant. |
| S5 edges: guided r=3, eps=1e-3 on luminance, interior >0.97, support 5×5, truncate | `refineAlpha` :316-327, `luminance` :330-333, call :167-169 | `finish` (`pipeline_v11.py:42-47`), `guided` (`bench_v2.py:19-26`) | Equal. Guide is the original photo in both. |
| Island filter | `dropIslands` `MaskOps.kt:188-198` | `drop_islands` (`pipeline_v10.py:32-39`) | Equal |
| Crop to alpha >24, pad 4 % | `cropToAlpha` :110-134 | `save` (`pipeline_v11.py:50-54`) | Equal |

**Divergences (file:line on both sides):**

- **D1 Distance metric.** Python uses Euclidean `cv2.distanceTransform(DIST_L2, 5)` (`bench_v3.py:31`, `bench_v2.py:32`). Kotlin uses chamfer 1/√2 (`MaskOps.kt:52-85`), called from `thirdPoints` :101 and `corePoints` :125. This changes the magic-touch prompt points and the garment SAM core points (`PreciseCutoutEngine.kt:249`). CERTAIN as code difference; effect on the photo is HYPOTHESIS.
- **D2 SAM resize.** Python `embed` rescales the long edge to 1024 and scales the point coordinates (`bench_v6.py:26-27`, `:33`). Kotlin never rescales (`PreciseCutoutEngine.kt:275-284`, `:311`). They agree only when the work long edge is exactly 1024. Photos smaller than 1024 keep native size (`:371-376`). Kotlin also hard-codes a 64×64 embedding (`:26`, `:306`). That is correct only if the encoder pads to square internally. HYPOTHESIS: unverified against the ONNX export.
- **D3 u2netp resize.** Python uses PIL BILINEAR (antialiased) and quantises to uint8 (`bench.py:30`, `:41`). Kotlin uses a 2-tap bilinear with no antialias (`MaskOps.kt:248-268`) and keeps floats (`PreciseCutoutEngine.kt:175`). Small coarse-mask shifts are possible.
- **D4 magic_touch preprocessing.** Python resizes to 512×512 and draws a radius-6 keypoint disc (`bench_v2.py:101-103`). Kotlin passes the work bitmap with a normalised keypoint to MediaPipe `InteractiveSegmenterLegacy` (`media/cutout/InteractiveSelector.kt:33-34`). Not verifiable statically.
- **D5 High sensitivity** threshold 0.12 (`CutoutOptions.kt:13`) has no reference counterpart. Product decision, not a bug.
- **D6 Safety guard absent in chosen variant.** The Python 85 %-area fallback (`pipeline_v11.py:162-163`) runs only when BAND is unset. Chosen BAND=1 skips it. Kotlin matches BAND=1, so it has no guard either.
- **D7 Enclosed-background rule.** Not ported. Python `enclosed_bg` (`pipeline_v11.py:90-113`) is disabled for A by NOENC=1 (`:164-168`). Consistent with Kotlin.

## 2. F1: garment interior removed, hanger kept

**PREMISE DOES NOT MATCH (for a colour-rule cause).** No colour-keyed deletion exists in shipped code. `grep -i enclosed|NOENC app/src/main` finds nothing. The only colour rule is the Python `enclosed_bg`, off in the chosen variant. The "dark garments only" claim is not explained by any colour rule in the code. Colour still enters via luminance, the guide, and MediaPipe.

Where the interior can disappear, ranked by code evidence:

1. **S4b band rule, most consistent with "hanger kept, interior gone, strips survive."** Above `bandBottom` the whole selection is replaced by `garmentSelection(saliency(filled))` (`PreciseCutoutEngine.kt:261-267`, `HangerRemoval.kt:148-155`). `bandBottom = hanger.y1 + max(0.6·hanger.h, 0.08·garment.h)` (`HangerRemoval.kt:141-142`). There is no area guard (D6). If the reselection on the median-filled photo loses the garment body and keeps only high-contrast piping, the band reproduces the reported residue. A tall D-FINE box can make the band cover most of a short garment. HYPOTHESIS for this photo; code path CERTAIN.
2. **S4 contained-kill path** (`HangerRemoval.kt:96-98`). If ≥85 % of the SAM box-only mask lies inside the box, every hanger-mask pixel in the box is killed, with no garment check. If the D-FINE box overlaps the garment top, garment pixels are killed. Capped at 15 % of `garmentArea` (`:101`). This bounds the damage, so it is less likely to explain a whole-interior loss.
3. **S2 selection, unrelated to hanger.** If S1/S2 already lacks the interior, the hanger stage is not the cause. **Diagnostic:** run the same photo as "PreciseKeepHanger" (`CutoutOptions.kt:45`). If the interior is still missing, look at S1/S2, not the hanger.
4. **Engine fallback.** If precise timed out, failed, or was not `REMOVED`, the output is ML Kit (`CutoutEngineRegistry.kt:44-58`). ML Kit has no hanger removal (a kept hanger is expected) and its subject mask can drop low-contrast interiors. **Check which engine produced the photo before tuning the precise path.** I did not find a persisted engine marker in the paths I read.

## 3. F2: soft edge where the hanger was

Constants: guided filter radius 3 and eps 1e-3 (`MaskOps.kt:28-29`); fill ring 25 px (`HangerRemoval.kt:16`); kill dilation 9×9 (`:15`); fill blur 9×9 (`:17`, `:127`); output support dilation 5×5 (`MaskOps.kt:321`).

Mechanisms in the band, all CERTAIN in code, impact HYPOTHESIS:

1. **Guide mismatch.** The band's selection comes from the filled photo (`PreciseCutoutEngine.kt:261`). The edge guide is luminance of the original photo (`:168`, `MaskOps.luminance` :330-333). The guided filter aligns edges only where the original luminance has an edge. The band boundary is defined by filled colours, so it is not aligned and the filter returns a box-averaged transition over about 7 px.
2. **Artificial step at the cut.** `applyBand` zeroes `support` (the 9×9-dilated kill) in both regions (`HangerRemoval.kt:150-151`). That makes a 0/1 step inside the garment, which the guided filter then softens.
3. **Hanger fringe.** Output RGB is the original photo (`PreciseCutoutEngine.kt:76`), with no colour decontamination. Hanger-coloured pixels just outside the 9×9 kill keep alpha through the 5×5 support dilation. This reads as a blurry or dirty edge.

Minimal fix options (pick one):
- (a) Use a guide from the filled photo inside the band only.
- (b) Binarise alpha inside the band (smoothstep sharp profile, as `CutoutEdges.Sharp` does at `MaskOps.kt:319`).
- (c) Replace RGB within about 2 px of the kill with fill colour before composing.

## 4. F3: output resolution

- **Precise:** the mask is computed at the work size (long edge 1024, or native if smaller). `cropToAlpha` crops that bitmap with 4 % pad. The stored PNG has long edge ≤1024 plus pad, not upsampled (`PreciseCutoutEngine.kt:74-83`, `ClothingImageStore.kt:232-239`, PNG at 100). For 2250×4000, the work image is 576×1024.
- **ML Kit:** decoded with `inSampleSize` so long edge ≤2048 (`segmentationSampleSize`, `MlKitSubjectSegmentationBackgroundRemovalEngine.kt:201-208`). For 2250×4000, that is 1125×2000. Output is the full frame, **not cropped**, composed with alpha (`:260-266`). Alpha comes from the confidence mask, or the foreground bitmap if the size check fails (`:235-252`).
- **Original:** `originalUri = sourceUri` (`:89`, `originalFallback` in `PhotoBackgroundRemover.kt:50`). No copy is made here. The full-resolution source is kept as-is.

## 5. Hot file: `ui/RobiaApp.kt`

No fix for F1–F3 needs `RobiaApp.kt` edits.

- Cutout call sits in `processQueuedBatchDrafts()` at **:1161-1190**, with the `cutout = { draft -> cutoutStage(...) }` lambda at :1164-1171. That calls `cutoutStage` in `ui/BatchAddClothingScreen.kt:558`.
- The settings route is `RobiaRoute.PreciseCutoutSettings` at :297-298. Navigation is at :2876 inside `RobiaNavHost` (:2629-2947).
- Files a fix touches: `media/cutout/PreciseCutoutEngine.kt` (`alpha` :152-170, `hangerFirst` :256-272), `media/cutout/HangerRemoval.kt` (`applyBand` :148-155, `fill` :111-138), `media/cutout/MaskOps.kt` (`refineAlpha` :316-327, distance transform :52-85). Settings UI is `ui/PreciseCutoutSettings.kt`.

## 6. Risks and coupling

- The F1 band guard (restore the 85 % area check, `pipeline_v11.py:162-163`) and the F2 band-local guide both change `applyBand`/`hangerFirst`. Land them together and test against the same photo.
- A global change to `refineAlpha` (F2 fix a) affects every precise output. Keep it band-local.
- D1 (distance metric) changes prompts for every photo. Fix it only with a reference comparison (the FIX10 CI check).
- Unit tests exist: `HangerRemovalTest.kt`, `MaskOpsTest.kt`, `CutoutFallbackChainTest.kt`. I did not read them.
- ML Kit `AlphaRefiner` (`media/AlphaRefiner.kt`) is separate. Precise changes do not touch it.

## 7. Not verified

- No model run. Interior loss and edge softness on the actual photo are HYPOTHESIS.
- ONNX export shapes (SAM encoder output for non-square 1024×576, D2) and the `magic_touch` asset (D4) were not checked.
- Whether a persisted engine marker exists (section 2, item 4) was not checked.
