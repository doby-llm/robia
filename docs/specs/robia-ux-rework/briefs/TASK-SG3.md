# TASK-SG3 — Photo editor (Cutout + Light), replaces the quick-edit dialog

## Goal
No automatic method removes every hanger (clip bars on skirts/shorts are still missed sometimes) and Manu wants a smart finger selection to remove leftovers AND restore parts that were cut away. He also dislikes the current quick-edit dialog (brightness/temperature) and left a full rework to the planner (UX-DEC-011). Build one full-screen **photo editor** with two tabs: **Cutout** (smart tap/swipe selection with MediaPipe Interactive Segmenter + a Brush for exact small fixes) and **Light** (today's brightness and temperature, moved unchanged). It works offline and without the downloadable pack (the 6 MB model is bundled by SG2a).

## Read first
TS§SG3; mockup `photo-editor.html`; REQ-SG-06; SG2a `media/cutout/InteractiveSelector.kt` and `media/AlphaRefiner.kt` (SG1); `ui/AddEditClothingScreen.kt`: `showQuickEdit` (~174), the `QuickEditDialog(` call and its `onSave` (~677-707: `applyAdjustments` → `reprocessEditedPhoto`), `QuickEditDialog` and helpers (~1736-2100: `QuickEditToolIconButton`, `QuickEditBrightnessSlider`, `QuickEditTemperaturePreset`, `QuickEditTool`), `reprocessEditedPhoto` (~494); `media/QuickEditImageProcessor.kt` (`applyAdjustments`, `renderDraftPreview`, `QuickEditAdjustments`, `QuickEditDraftState`); `media/EditorPhotoState.kt` (`rawSourceUri`, `currentEditedUri`); `media/ClothingImageStore.writeProcessedBitmap`.

## Steps
1. `media/cutout/RefineSession.kt` (pure Kotlin, no Android types): `w`, `h`, base alpha `FloatArray`, current alpha; `apply(mode: Mode, mask: FloatArray, hard: Boolean)`: hard (smart) masks are thresholded at 0.5 and dilated 2 px; Keep = `max(alpha, m)`, Remove = `alpha × (1 − m)`; undo stack ≥ 10 entries storing run-length-encoded previous alphas; `undo()`, `reset()`, `canUndo`.
2. `media/cutout/BrushStroke.kt` (pure): `fun brushMask(points: List<Pair<Float, Float>>, radiusPx: Float, featherPx: Float, w: Int, h: Int): FloatArray` (soft discs along the stroke, max-combined). Pure `fun brushRadiusPx(radiusDp: Float, density: Float, zoom: Float): Float = radiusDp * density / zoom`; the screen uses it with 24 dp and feather 6 dp.
3. `ui/PhotoEditorScreen.kt`: `@Composable fun PhotoEditorScreen(photoUri, rawSourceUri, photoAspectRatio, onCancel, onDone: (cutoutUri: Uri?, adjustments: QuickEditAdjustments) -> Unit)` in a full-screen `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))`; back = Cancel.
   - Top bar: ✕ (Cancel), "Edit photo", Done (enabled when something changed).
   - Preview: `transformable` pinch 1–4× + pan; Cutout tab draws a checkerboard behind transparent pixels; Light tab shows the live `renderDraftPreview` on the garment colour gradient.
   - Bottom `TabRow` Cutout | Light.
   - Cutout tab: `SingleChoiceSegmentedButtonRow` Keep | Remove, a "Brush" `FilterChip`, Undo and Reset icon buttons, hint `refine_hint`. Smart mode (Brush off): a tap → one point; a drag shorter than 600 ms → its points sampled every 24 dp; run `InteractiveSelector.select` for each point on `Dispatchers.Default`, union the masks, then `session.apply(mode, mask, hard = true)`; a 300 ms pulse marks the touch while it runs; report each select latency through a parameter `onDiagnostic: (String) -> Unit` (`refine_tap_ms=%d`); `AddEditClothingScreen` appends it to its existing `developerDiagnostics` list. Brush mode: on drag, `brushMask` from the stroke, applied with `hard = false`.
   - Light tab: move `QuickEditBrightnessSlider`, `QuickEditTemperaturePreset` and the hold-to-compare behaviour from `QuickEditDialog` unchanged (cut/paste into this file, same names, `private`).
   - Cutout source: `rawSourceUri` when present (new photos), with the current processed PNG alpha (resampled to the source size) as the start alpha; otherwise the processed PNG itself (existing garments). If its RGB under transparent pixels is cleared, Brush-Keep there restores nothing visible: show `refine_no_source_hint` "The original photo is not available, so removed parts cannot be restored." once, and say so in the receipt.
4. Done: if the cutout changed → `AlphaRefiner` edges → compose RGBA with the source RGB → crop to alpha > 24 with 4 % pad → `ClothingImageStore.writeProcessedBitmap` → uri; then `onDone(uri, adjustments)`. In `AddEditClothingScreen` the old `onSave` logic runs: `applyAdjustments` on `uri ?: current photo` (skipped when adjustments are neutral) → `reprocessEditedPhoto(editedUri)` (colours/suggestions re-detected, same processing UI). Cancel writes nothing.
5. `AddEditClothingScreen.kt`: replace the `QuickEditDialog(` call with `PhotoEditorScreen(` inside the same `if (showQuickEdit …)` guard; delete `QuickEditDialog`, `QuickEditToolIconButton`, `QuickEditTool` if no longer used (grep). The pencil in `PhotoPreview` and the "Edit photo" button from U06 keep calling `onQuickEditClick`.
6. Strings (all locales): `photo_editor_title` "Edit photo", `photo_editor_tab_cutout` "Cutout", `photo_editor_tab_light` "Light", `refine_keep` "Keep", `refine_remove` "Remove", `refine_brush` "Brush", `refine_hint` "Tap or swipe over a part to keep or remove it.", `refine_no_source_hint`, `refine_undo` / `refine_reset` content descriptions. Reuse `quick_edit_*` strings in the Light tab.

## Tests
`RefineSessionTest`, `BrushStrokeTest` (V-SG3-1); grep evidence for V-SG3-4.

## Done when
V-SG3-1..4; independent review; device row V-Z2-18 (tap median ≤ 400 ms from developer diagnostics).

## Do not
Change the automatic pipeline, the batch flow, `QuickEditImageProcessor`, stored originals, or add a route in `ui/RobiaApp.kt`.
