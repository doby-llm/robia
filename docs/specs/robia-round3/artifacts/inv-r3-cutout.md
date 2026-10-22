# inv-r3-cutout — precise cutout destroys garments, hanger-band edges, smart selection (planning evidence, 2026-10-22)

Inputs: Manu's emails "Picture cutout fail" (pink shorts: source photo 2250×4000 + app result) and "Border smoother for hangout removal issue" (red shorts result). Personal photos stay outside git (planner workspace `r3/feedback-assets/`, `r3/research/cut/`). Code facts: `inv-r3-d.md` (editor), `inv-r3-f.md` (engines). Scripts: `tools/cutout-diag/` (added by this planning PR).

## 1. The algorithm is fine on Manu's photo; the Android path is not
- Python reference `tools/segmentation-bench/pipeline_v11.py`, chosen variant (`BAND=1 FILL=median NOENC=1`), on the pink-shorts source: **complete garment**, wooden hanger bar removed (two small metal clips remain at the waistband). 15.7 s total on the Pi, peak 1.2 GB.
- App result on the same photo: interior of the shorts transparent, only the white-piped sides left; the wooden bar ends and the clips are kept.
- Therefore the defect is in the Kotlin port, its inputs, or a silent engine fallback, not in the pipeline design. The precise engine was never verified on raw photos on the device: G-SEG-1 measured speed on already-processed PNGs, and G-SEG-2 (visual) is the gate Manu now reports as failed.
- Code-level candidates (`inv-r3-f.md` §1–2, CERTAIN as code, unproven on this photo): band reselection without an area guard (`HangerRemoval.applyBand`), SAM inputs not rescaled for work sizes other than 1024 (`PreciseCutoutEngine.samEmbedding/samDecode`), distance transform metric (chamfer vs Euclidean) changing prompt points, u2netp resize without antialias, and the fallback chain hiding which engine produced a photo (no engine marker is persisted or logged).

## 2. The selection model (magic_touch) behaves the same in Python as the model itself
- MediaPipe 1.1.1 (the version the app uses) `InteractiveSegmenterLegacy` with `output_confidence_masks=True` returns ONE confidence mask (the subject), same size as the input. On the pink photo (work size 576×1024) its masks match the raw tflite model run as in the reference (512×512 input + keypoint disc): IoU 1.00, 1.00, 0.97 at three taps (0.74 at a fourth tap on the door). RGB and RGBA inputs give identical results.
- The investigator's hypothesis "`masks[0]` is the background mask" is **refuted**: there is no second mask, and mask[0] = subject.

## 3. Why the editor's smart Remove looks broken (explained without a code defect)
On a clean cutout composited on black (today) or grey (PR #134), any tap on the garment selects the **whole garment** (coverage 0.99–1.00 of the opaque area; 1–2 % outside it). A tap on transparent background selects the background (already transparent).
- "Short tap: I see a circle, then nothing happens" → tap on background: the selection is already transparent, so Remove changes nothing.
- "Parts far from the tap are erased" → tap on the garment: the whole garment is selected and removed (or large parts after the 0.5 threshold).
- "Dragging shows nothing" → smart mode draws no trail, and drags longer than 600 ms are dropped silently (`PhotoEditorScreen.kt:326`, `:484`).
- "Erratic" → union of several whole-object selections along a short drag.
The smart tool is useful for leftover pieces (a hanger bar, a strip of door) that are visible objects in the cutout. The plan (REQ-ED-01) makes that explicit, clips to touched regions, refuses whole-garment removals with a hint, and adds a device dump to catch any remaining wiring defect.

## 4. Hanger-band edges (red shorts)
Sides and hem crisp; top edge (hanger band) soft. Code causes (`inv-r3-f.md` §3): the edge filter's guide is the original photo while the band mask comes from the filled photo; the band applies a 0/1 step that the filter then blurs (~7 px); hanger-coloured fringe kept by the 5×5 support dilation. Fix in REQ-CU-03 (band-local guide and profile, 2 px decontamination).

## 5. Output sizes (for originals)
Precise: work frame long edge 1024 (or smaller), cutout cropped from it, never upsampled. Standard (ML Kit): power-of-two decode to long edge ≤ 2048 (4000 px → 2000 px), full frame. The original kept by REQ-OR-01 is exactly this engine input frame.

## Decision
- Precise off by default now (robia-ux-rework stop rule for a failed G-SEG-2).
- K01 diagnostics → ADB session with Manu's phone → K02 fixes with a closed list + diagnostic findings → K03 band edges → G-CUT visual gate.
- Editor: ED1 (semantics, clip, hint, trail, dump), ED2 (full history).
