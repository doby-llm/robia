# TASK-U07 — Processing visuals

## Goal
During photo processing the app dims the photo and shows a spinner (R6). Replace it with a calm "magic" sequence: light sweep → background fades and a shimmer runs only inside the garment silhouette → colours/suggestions pop in (mockup `processing.html`, UX-DEC-006).

## Read first
TS§7; `ui/AddEditClothingScreen.kt` `PhotoPreview` (~1626-1702), `PhotoProcessingStage` (~2337), where `EditorPhotoState.foregroundUri` is set (~369-371); `media/EditorPhotoState.kt`; `ui/BatchAddClothingScreen.kt` `BatchPhotoPreview` (~389-436); `ui/ProcessingOverlayContrast.kt`; `core/designsystem/Motion.kt` (`LocalReducedMotion`, `PopIn`).

## Steps
0. In `AddEditClothingScreen.kt` change `private enum class PhotoProcessingStage` to `internal` (needed by the test).
1. `ui/PhotoProcessingVisuals.kt`: `internal fun processingStep(stage: PhotoProcessingStage): Int` (pure, TS§7 mapping); `SweepOverlay`; `MaskedShimmer(foregroundUri)` (decode with the existing bounded decode helper from `ClothingImageStore` if one fits, else `BitmapFactory` with inSampleSize to ≤ 1024 px, off the main thread via `produceState`); `SegmentedProgress(steps = 3, current)`; `ProcessingStageLabel(step)` with strings `processing_step_find` "Finding your garment…", `processing_step_edges` "Cleaning the edges…", `processing_step_style` "Reading colours and style…".
2. `PhotoPreview`: when processing, draw the photo at full opacity + `SweepOverlay`; when `foregroundUri != null` animate the raw photo alpha to 0.18 (400 ms) and draw `MaskedShimmer` on top; below the photo `SegmentedProgress` + label. Remove the scrim + spinner. When processing completes, the colour swatches drawn inside `PhotoPreview` (bottom-start overlay, see mockup step 3) use `PopIn`; the metadata rows are not touched by this task.
3. `BatchPhotoPreview`: replace spinner/dim with `SweepOverlay` (no label).
4. Reduced motion: no infinite transitions; static band hidden; label + progress still shown.
5. Delete `ProcessingOverlayContrast.kt` if no longer referenced (grep).

## Tests
`ProcessingStageMappingTest` (V-U07-1).

## Done when
V-U07-1, V-U07-2.

## Do not
Change processing order, timing, the pipeline or what is saved.
