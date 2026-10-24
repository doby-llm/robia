# TASK-UI-SMART — Smart remove: deterministic tap on the loose piece

## Goal
F10 (REQ-FB-10, R3-DEC-043): Manu chose option C of the planner report (`ui.md` F10, in the planner workspace). The model-based smart tap returns a mask that does not depend on the tap (R3-DEC-021 D1: byte-identical `mask.png` for every tap, `clipped.png` empty in 17 of 20 dumps). Replace it by pure Kotlin: a tap removes the loose piece it touches.

Failure rule (Manu): if V-UI-SMART-D fails on the device after this task, the follow-up is removal of the smart tool (the brush becomes the only tool), not another fix attempt. State this in the PR description.

## Read first
`R3-DEC-043`; `ui/PhotoEditorScreen.kt`: `runSmartSelection` (~384-450), `finishGesture` (~456-472), the pointer-input block (~669-740), trail and pulse drawing (~756-800), `selectPixelOf`, `smartSelectionPoints`, `samplePath` (~1107-1140), `compositeOverGrey` (~1171), debug dump (~1297-1345), `DisposableEffect` that closes the selector (~276-290); `media/cutout/SelectionClip.kt` (`clipToTouched`, `removesMostOfGarment`); `media/cutout/MaskOps.kt` `labelComponents` (~154) and `Labeling`; `RefineSession.apply` and `undo`/`redo`; `SelectionClipTest`, `SmartGestureTest`, `SelectionBackgroundTest`, `PhotoEditorMappingTest`.

## Files
`ui/PhotoEditorScreen.kt`; `media/cutout/SelectionClip.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PhotoEditorMappingTest.kt` (only if a helper it covers is removed); delete `app/src/test/java/com/gusanitolabs/robia/ui/SelectionBackgroundTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/SmartRemoveUndoTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `SelectionClip.kt`: add pure functions on primitive arrays.
   - `componentsToRemove(alpha: FloatArray, w: Int, h: Int, touched: List<Pair<Int, Int>>, snapRadiusPx: Int): ComponentPick` with `ComponentPick(mask: FloatArray, touchedPrimary: Boolean, removedComponents: Int)`.
   - Components: `MaskOps.labelComponents(BooleanArray(n) { alpha[it] > 0.5f }, w, h)` (8-connected). Primary = the label with the largest area; equal areas: the lowest label (first in raster order).
   - Each touched pixel resolves to the component at that pixel; if it is background, to the nearest labelled pixel within `snapRadiusPx` (Euclidean distance, ties by smaller y then smaller x); beyond the radius it resolves to nothing.
   - `mask` holds 1f on every pixel of every resolved non-primary component and 0f elsewhere. `touchedPrimary` is true when any point resolved to the primary.
   - `snapRadiusPhotoPx(dp: Float, density: Float, fitScale: Float, zoom: Float): Int` = `ceil(dp * density / (fitScale * zoom))` clamped to 2..48; the editor uses 16 dp.
   - Keep `removesMostOfGarment` and `SELECTION_THRESHOLD`. Delete `clipToTouched` and its cases once nothing else uses it.
2. `PhotoEditorScreen.kt`, `runSmartSelection`: under `sourceMutex`, skip when `current.source.isRecycled` (unchanged). In Remove mode: `val pick = componentsToRemove(current.session.currentAlpha(), w, h, touched, radius)` on `Dispatchers.Default`. If `pick.removedComponents == 0`: when `pick.touchedPrimary` show `editor_smart_whole_garment` (existing string), otherwise do nothing and push no op. If `removesMostOfGarment(pick.mask, alpha)` show the same hint and push nothing. Otherwise `current.session.apply(RefineSession.Mode.Remove, pick.mask, hard = true)`, `photoHistory.discardRedo()`, `revision++`. In Keep mode do not call `apply`; show the new hint `editor_smart_keep_hint` (EN: "Smart tap removes loose pieces. Choose Remove, or use the brush to keep parts."; ES/DE yourself). Report one diagnostic line `refine_component_tap components=<n> primary=<bool>` through `onDiagnostic`.
3. Gesture, trail, pulse, `SMART_MAX_POINTS`, `smartSelectionPoints`, `samplePath`, `selectPixelOf`, the brush path and `commitStroke` stay exactly as they are.
4. Remove the dead native call path from `PhotoEditorScreen.kt`, each item only after `git grep` shows no other user: the `MediaPipeInteractiveSelector` import and the `selector` lazy, the `selector.value.close()` line in the dispose block (keep the rest of that block), `compositeOverGrey` (and delete `SelectionBackgroundTest`), `SelectionOverlay`, `debugOverlay` and its drawing and timeout, `debugSelectionDirectory`, `writeSelectionDump`, `tintedPixels`, `DEBUG_OVERLAY_*`. If something is used elsewhere, leave it and say so in the receipt.
5. Do not touch `media/cutout/InteractiveSelector.kt`, `PreciseCutoutEngine.kt`, `InteractiveSelectorContractTest`, `InteractiveSelectionSeamTest`: `PreciseCutoutEngine` uses `MediaPipeInteractiveSelector` as S2.
6. Tests (all JVM, synthetic arrays): V-UI-SMART-1 to V-UI-SMART-4 as written in the matrix. `SmartRemoveUndoTest` builds a `RefineSession` on a small synthetic alpha (a garment blob plus a loose blob), applies the picked mask, and checks `currentAlpha()` after apply, `undo()` and `redo()`.

## Tests
V-UI-SMART-1 to V-UI-SMART-4 (CI), V-UI-SMART-5 (REV), V-UI-SMART-D (Manu).

## Done when
V-UI-SMART-1 to V-UI-SMART-5 pass, V-UI-SMART-D is ready for Manu; CI green. The PR description repeats the failure rule.

## Do not
Change `RefineSession`, `EditHistory`, `MaskOps`, the brush, the OR3 Keep brush or any engine; restore removed parts with the smart tool; add a library or a model call; keep the old selector path "just in case".
