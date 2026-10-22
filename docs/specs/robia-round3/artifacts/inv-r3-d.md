# Photo editor D1–D3 and PR134: read-only findings

I did not build or run tests; everything below is from reading the source. CERTAIN means seen in code. HYPOTHESIS means inferred and needs a device check.

## Premise check

- **D1:** matches. Smart select is broken as described, but the "drag shows nothing" part is partly designed behaviour (see below).
- **D2: PREMISE DOES NOT MATCH.** There is no redo-of-undo anywhere. The only undo is `RefineSession.undo()` (`RefineSession.kt:67`). The button labelled "Redo" is "Redo cutout" (`strings.xml:782`, Spanish `Repetir recorte`), which re-runs the engine on the raw photo. The user likely means both "re-apply undone edit" and "longer history".
- **D3:** no premise issue.
- **PR134:** the current tree is the pre-PR state (`PhotoEditorScreen.kt:286-295` still passes `point.x.roundToInt()` directly), so the diff applies. I did not compile it.

## D1: smart select

### Path (CERTAIN)
`pointerInput` (`PhotoEditorScreen.kt:454-496`) → `finishGesture` (`:310-331`) → `runSmartSelection` (`:274-308`, under `sourceMutex`, one `select()` per sampled point, union by max at `:294`) → `InteractiveSelector.select` (`InteractiveSelector.kt:32-48`) → `RefineSession.apply(mode, union, hard=true)` (`RefineSession.kt:53-64`).

### Gesture: why a drag shows nothing (CERTAIN)
- `:326`: `if (moved && uptime - startedAt >= SMART_DRAG_MAX_MS(600)) return`. Any drag lasting 600 ms or more is dropped silently. The comment calls this an "accepted choice".
- `:484`: the trail is drawn only when `brush` is on, so smart mode shows no trail.
- `:329`: the pulse is placed at the touch-down point only (`PulseMark(touchedAt)`), drawn at `:555-563`, `PULSE_MS=300` (`:137`), radius 20dp (`:138`).
- `:299-303`: a `select()` failure only writes a diagnostic. There is no snackbar.
- `:328`: a short drag is sampled every `SMART_SPACING_DP=24dp` (`:327`, converted by `brushRadiusPx` at `BrushStroke.kt:8`).

### Coordinates: mapping is consistent (CERTAIN)
- `screenToPhoto` (`:846-852`) inverts `fitRect` (`:836-843`) and the preview `graphicsLayer` (`:532-537`: scale about centre, then translate). I checked the forward and inverse transforms and they match.
- The selector gets the same bitmap that is displayed: `preview` is composed from `current.source` at the same size (`:251-252`). No crop, letterbox or downscale sits between them.
- The brush uses the same `screenToPhoto` (`:320`, `commitStroke` `:264-272`) and works for the user. That is evidence the mapping is not the bug.
- Row-major indexing is consistent: `RefineSession.kt:115` and `InteractiveSelector.kt:43` both use `y*w+x`. I found no transposition.

### Selector input (CERTAIN unless noted)
- `InteractiveSelector.kt:23,55`: model `models/magic_touch.tflite`. No input-size or format option is set; MediaPipe handles resizing. The model's internal name in the file is `point2mask_objects_512x512_2023_04_18`, so the input is probably 512×512 (HYPOTHESIS).
- `:33`: keypoint `NormalizedKeypoint.create(x/w, y/h)`. x and y order is correct. It is the pixel's left/top edge, not its centre: a 0.5 px offset, negligible.
- `:34`: `BitmapImageBuilder(bitmap)` with no rotation. The bitmap is already EXIF-corrected at decode.
- `:24`: `setOutputConfidenceMasks(true)`. No category mask is requested.
- `:38`: **`val mask = masks[0]`**. The model's `labels.txt` entry, read from the tflite metadata, is `background\nsubject`. Index 0 is therefore the background class.
- HYPOTHESIS (high prior): MediaPipe returns one confidence mask per label in label order, so `masks[0]` is the background mask. That single choice fits every symptom:
  - Remove, far parts erased: the background mask is thresholded (`RefineSession.kt:96-97`) and then removed (`:57-63`, `current*(1-m)`). Everything not garment goes, including far parts.
  - Tap, nothing happens: a tap on the garment has background≈0, so the selection is empty.
  - Erratic: multi-point union (`PhotoEditorScreen.kt:294`) of background blobs.
  - Keep (the default, `PhotoEditorScreen.kt:196`), nothing happens: `keepAlpha` never raises pixels whose base alpha is below 0.05 (`RefineSession.kt:16-17`). Most background is base 0.
- `:39`: size check (mask must equal bitmap size). No check of `mask.imageFormat`; `:40-43` reads the buffer as native-order floats, which is correct only for FLOAT32 confidence masks. Add a format guard.
- `:46`: masks are closed after copying. Correct.

### Mask-to-alpha (CERTAIN)
- `RefineSession.kt:55`: hard → `threshold` (`≥0.5`, `:96-97`) → `dilate` disc r=2 (`:99-119`, `HARD_DILATION_PX=2`, `:6`).
- **No connected-component restriction.** The result is not clipped to the tapped region. `MaskOps.labelComponents` (`MaskOps.kt:148`) and `dropIslands` (`:188`) exist but are used only by the precise engine (`PreciseCutoutEngine.kt:168`).

### Every place a coordinate or mask could be wrong
1. `InteractiveSelector.kt:38`: wrong channel, the top suspect.
2. `RefineSession.kt:57-63`: Remove uses `1-m`, so a background mask removes the background.
3. `RefineSession.kt:16-17`: Keep cannot restore pixels with base alpha below 0.05, because their RGB is lost.
4. `PhotoEditorScreen.kt:288-291`: `roundToInt` instead of `floor`, up to one pixel off. The PR fixes this.
5. `PhotoEditorScreen.kt:251-252` and `:274`: the selector sees `current.source`, whose transparent pixels have RGB 0 (the PR comment says Android premultiplies). Black background bleeds into garments. The PR fixes this.
6. `InteractiveSelector.kt:33`: pixel edge rather than centre, negligible.
7. `InteractiveSelector.kt:40-43`: no format check (see above).

### Diagnostic and fixes
- **Device diagnostic (cheap):** in `select()`, log `masks.size`, `masks[0][tap]`, `masks[1][tap]` and the count ≥0.5 for each. Alternatively, flip the index to 1 and see whether Remove now removes the non-garment. That single A/B settles the channel question.
- **Dev overlay:** plumb the pre-threshold `union` into a state `ImageBitmap` and draw it with the existing overlay pattern (`:540-553`).
- **Minimal fixes (JVM-testable where marked):**
  - (a) Use the subject index (`1`) and assert `masks.size == 2` (`InteractiveSelector.kt:38`). Not JVM-testable; depends on device result.
  - (b) Keep only the connected component(s) containing the tap pixels, using `MaskOps.labelComponents` on the thresholded union (`MaskOps.kt:148`). JVM-testable with synthetic masks, `MaskOpsTest` style.
  - (c) Drop the 600 ms limit (`PhotoEditorScreen.kt:326`) and show the trail in smart mode (`:484`). Pure logic, JVM-testable.
  - (d) Keep semantics (`RefineSession.kt:16-17`): product decision.
  - (e) Use the PR's `selectPixelOf`.

## D2: undo and redo

### Current (CERTAIN)
- `RefineSession.kt:7`: `UNDO_DEPTH = 10`. `pushUndo` (`:91-94`) runs on `apply` (`:56`), `reset` (`:76`) and `replaceBase` (`:85`). `replaceBase` is unused by the editor, only tested (`RefineSessionTest.kt:159-193`).
- Snapshot: `(base reference, RunLengthAlpha(current))` (`:123`). `base` is shared, not copied, because it is never mutated. `RunLengthAlpha` (`:126-154`) stores values and lengths, 8 bytes per run.
- Editor level: a single `replaced: CutoutPhoto?` slot (`PhotoEditorScreen.kt:205`). `undoEdit` (`:334-345`) first undoes the session, then restores `replaced`. `redoCutout` (`:351-381`) builds a new `RefineSession` with no history, sets `replaced = photo`, and recycles the previous `replaced` (`:362-364`).
- Net effect: 10 edits, plus one "Repetir recorte" level. A second Repetir recorte drops the first photo's whole history.

### Memory
- Raw alpha per step is 4 bytes/px. At the ML Kit working size of about 2016×1512 (3.05 Mpx), that is 12.2 MB.
- RLE costs 8 bytes per run. Hard masks have few runs, so a step is probably tens of KB (HYPOTHESIS: measure). Soft brush edges and noisy base alpha can make RLE bigger than raw: worst case 8 bytes/px, i.e. 24 MB per step, 244 MB for 10 steps. The bound is by count, not bytes.

### Full-history design (proposal)
An operation log, with checkpoints:

```kotlin
internal sealed interface EditOp {
    val mode: RefineSession.Mode
    class Hard(override val mode: RefineSession.Mode, val selected: BitRuns) : EditOp   // post-dilation 0/1, RLE or bitset (w*h/8 = 384 KB at 3 Mpx raw)
    class Brush(override val mode: RefineSession.Mode, val points: FloatArray, val radiusPx: Float, val featherPx: Float) : EditOp  // regenerate with brushMask (BrushStroke.kt:17)
    class ReplaceBase(val photoId: Int) : EditOp   // bitmap held outside the log
}
// state(cursor) = base ⊕ ops[0 until cursor]; RLE checkpoint every K=8 ops bounds replay cost.
// undo: cursor--; redo: cursor++; a new op truncates the redo tail.
// budget in bytes (e.g. 48 MB): evict oldest ops by folding them into a checkpoint base.
```

- Replay is exact because `base` is fixed and `Keep`/`Remove` depend only on `base` and `current`.
- Repetir recorte is the hard part. Each run produces a new ARGB bitmap (12.2 MB ML Kit, 3 MB precise). Undo across it needs the old bitmap. Keep at most one or two bitmaps and make older Repetir recorte steps non-undoable, or the memory budget breaks.
- `replaceBase` (`RefineSession.kt:84-89`) already undoes base changes, but it cannot restore RGB, which differs per Redo. Use it only if the RGB is also kept.

### Smart-tap transient allocation (CERTAIN, at 3 Mpx)
Each 12.2 MB buffer: `union` (`:281`), `select` return (`InteractiveSelector.kt:43`) plus MediaPipe native masks, `threshold` (`RefineSession.kt:96`), `dilate` out (`:102`), new `current` (`:57`). Preview adds `argbOf` (`PhotoEditorScreen.kt:875-879`), `out` IntArray and Bitmap (`:884-886`). PR134 adds `selectionBitmap` plus its IntArray. Roughly 100–150 MB churn per tap at 3 Mpx.

## D3: working resolution

- **Editor load:** `photoUri` is `canonicalEditorUri` (`EditorPhotoState.kt:18-19`): current edited, else cropped, else foreground, else raw. It is decoded at full stored size with no `inSampleSize` (`PhotoEditorScreen.kt:237-242`). The editor is called from `AddEditClothingScreen.kt:673-678`.
- **Stored cutout size, by producer:**
  - ML Kit (default): decode subsampled to long edge ≤2048 (`MlKitSubjectSegmentationBackgroundRemovalEngine.kt:27`, `:201-208`, `:178-190`). Example: 4032×3024 → 2016×1512.
  - Precise: long edge 1024 (`PreciseCutoutEngine.kt:22`, `workSize` `:368-375`, decode `:383-420`, scaled at `:419`).
  - Raw source: not kept for saved garments. For photos picked in this session, `rawSourceUri` is the full-resolution input (`AddEditClothingScreen.kt:675`).
- **Editor edits and saves at stored size.** `writeCutout` (`PhotoEditorScreen.kt:913-935`) refines at the same size, writes PNG, then crops to the alpha bounding box (`ClothingImageStore.kt:215-229`). No scale.
- **Downscales on the path:** ML Kit decode (2048), precise work size (1024), and the Drive copy (`ClothingImageStore.kt:530`, `:60-68`, WebP 2048). The synced copy is not the local PNG. `AlphaRefiner.kt:40` scales the guided-filter radius, not the image.
- **Model resolution (HYPOTHESIS):** at 512 model pixels, a 2016 px photo has ~4 px per model pixel. This limits selection edges and is consistent with the 2 px dilation.

## RobiaApp.kt touch points

None on any fix path. `PhotoEditorScreen` is not referenced in `RobiaApp.kt`. The only cutout-related call there, `RobiaApp.kt:1161-1171` (`processQueuedBatchDrafts` → `cutoutStage`), is the batch-add pipeline. The editor's entry point and `onDiagnostic` wiring are in `AddEditClothingScreen.kt:673-678`. `RobiaApp.kt`'s `RobiaShell` (`:816-2002`) is hot, and nothing here needs to touch it.

## PR134 (FIX9)

**Fixes:**
- The floor pixel mapping (`selectPixelOf`, `PhotoEditorScreen.kt` in PR) fixes up to 1 px off-by-one. It does not explain far-away erasure.
- The grey composite (`compositeOverGrey`) fixes the black RGB under transparent pixels in the selector input. HYPOTHESIS: this may reduce erratic blobs from the black background, but it does not change the channel.
- The `redo_cutout_done` diagnostic is observability only.

**Does not fix:**
- `InteractiveSelector.kt:38` (`masks[0]`). This is the leading cause of far-away erasure and "no pasa nada". The PR does not touch this file.
- No connected-component clipping (`RefineSession.kt:55`).
- The 600 ms drag drop (`PhotoEditorScreen.kt:326`) and the missing smart-mode trail (`:484`).
- Keep's inability to restore base-zero pixels (`RefineSession.kt:16-17`).
- Undo depth and redo-of-undo.

**Coupling:** the grey composite reads `session.currentAlpha()`, so it depends on the session's current state. Changing `RefineSession` alpha semantics would change the selector input too.

**Suggested order:** run the channel A/B diagnostic first. If `masks[0]` is confirmed as background, fix (a), then (b), then (c). Mapping and grey background are already covered by the PR.
