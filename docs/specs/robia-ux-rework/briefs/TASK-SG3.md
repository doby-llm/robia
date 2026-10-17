# TASK-SG3 — Refine cutout editor

## Goal
No automatic method removed every hanger (clip bars on skirts/shorts are missed about half the time) and Manu asked for a smart selection tool. Add a "Refine" editor: Keep/Remove mode, one tap selects the whole piece with MediaPipe Interactive Segmenter (bundled, works offline and without the pack).

## Read first
TS§SG3; mockup `processing.html` (Refine phone); SG2a `InteractiveSelector` and `AlphaRefiner`; `ui/AddEditClothingScreen.kt` `PhotoCaptureCard(onRefine)` slot (U06), `EditorPhotoState` (`rawSourceUri`, `currentEditedUri`), the existing colour re-extraction step after a photo change (follow how Quick Edit updates the photo: `QuickEditImageProcessor` + editor state).

## Steps
1. `RefineSession` (pure): base alpha `FloatArray`, `apply(mode, selection: FloatArray)` → Keep = max(alpha, sel>0.5), Remove = alpha × (1 − (sel>0.5 dilated 2 px)); undo stack (≥ 10 entries, store run-length-encoded masks), `reset()`.
2. `RefineCutoutScreen`: loads the source (raw photo if available, else processed PNG; starting alpha = current PNG alpha resampled), checkerboard background, Keep/Remove segmented buttons, Undo, Reset, Done/Cancel (Cancel = no write; back = Cancel). Tap → `InteractiveSelector.select` on a background dispatcher, a short pulse at the tap point while it runs, then apply. Measure tap latency; log to developer diagnostics.
3. Done → `AlphaRefiner` edges → compose RGBA with the source RGB → crop → `writeProcessedBitmap` → update editor state like Quick Edit does → existing colour extraction runs.
4. Route `RefineCutout` in `RobiaApp.kt` (route + nav case only); `onRefine` passed to `PhotoCaptureCard` when a photo exists and processing is not running.
5. Strings: `refine_cutout_title` "Refine cutout", `refine_keep` "Keep", `refine_remove` "Remove", `refine_hint` "Tap a part to keep or remove it.", undo/reset content descriptions.

## Tests
`RefineSessionTest` (V-SG3-1).

## Done when
V-SG3-1..3; device row V-Z2-18 (includes tap ≤ 400 ms median, from developer diagnostics).

## Do not
Change the automatic pipeline, batch flow, or stored originals.
