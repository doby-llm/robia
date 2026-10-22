# TASK-K02 — Precise engine fixes from the device dumps

## Goal
Make the Kotlin precise pipeline match the reference on Manu's photos. G-ADB1 (coordinator with Manu's phone) records the first divergent stage per photo in a PLAN-CHANGE; this task fixes exactly those defects plus three guards that hold regardless: no band reselection may wipe the garment, no final result may lose most of the garment, and SAM inputs must be scaled as the reference for every work size.

## Read first
The G-ADB1 PLAN-CHANGE in `decision-log.md` (defect list with stage names and IoU); `artifacts/inv-r3-cutout.md`, `artifacts/inv-r3-f.md` §1 (divergences D1–D7); `tools/segmentation-bench/pipeline_v11.py` (`hanger_mask`, band rule l.157-161, 85 % guard l.162-163) and `bench_v6.py` `MobileSam.embed/predict` (resize + coord scaling); Kotlin `PreciseCutoutEngine.kt` `samEmbedding/samDecode` l.274-320, `hangerFirst`, `HangerRemoval.applyBand` l.148-155, `MaskOps.thirdPoints/corePoints/distance` l.52-125.

## Files
`media/cutout/PreciseCutoutEngine.kt`; `media/cutout/HangerRemoval.kt`; `media/cutout/MaskOps.kt`; `media/cutout/InteractiveSelector.kt`; `media/cutout/CutoutEngineRegistry.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/HangerRemovalTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/MaskOpsTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/GarmentLossGuardTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. (a) `HangerRemoval.guardedBand(reselected, previous, support, bandBottom, w)`: compute the opaque count of `previous` inside the band (rows < bandBottom, excluding support); if `reselected` keeps < 85 % of it, return `applyBand(previous, previous, …)` for the band (i.e. keep the first selection) and log `band_guard=1`. Use it in `hangerFirst`.
2. (b) After S5, `opaque(alpha > 127) / count(S2 hard)`; below 0.70 → the engine returns a not-REMOVED result with failure reason `garment_loss`; `cutoutFallbackChain` reports fallback `garment_loss` (add the value) and ML Kit runs.
3. (c) SAM: resize the work image so its long edge is exactly 1024 before `samEmbedding` (bilinear), scale point and box coordinates by the same factor, and scale the decoded mask back to the work size, exactly as `bench_v6.py`. The 64×64 embedding then always matches.
4. (d) Fix each defect listed in the G-ADB1 PLAN-CHANGE, in its order, each with a JVM test that fails on the old code (synthetic masks/fakes). If a listed defect cannot be reproduced in a JVM test, say so in the receipt.
5. Keep `NoStageSink` behaviour from K01; new guard decisions are written to `report.json` (`band_guard`, `garment_loss`).

## Tests
V-K02-1..3 as listed; one test per (d) item. All existing `media/cutout` tests stay green.

## Done when
V-K02-1, V-K02-2, V-K02-3, V-K02-4, V-K02-5 pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Change thresholds not named here, enable the precise method by default, change hanger-band edges (K03), or touch UI files.
