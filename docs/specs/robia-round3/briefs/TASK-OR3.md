# TASK-OR3 — Editor: restore background from the original, "Cut out again" for saved garments

## Goal
#135 D1: Keep cannot restore removed background because the stored cutout has no pixels there (inv-r3-b.md §B4). With the original and the recipe (OR1) the editor can read the missing pixels. "Cut out again" (Redo cutout) is hidden for saved garments today because no raw source is kept.

## Read first
`artifacts/inv-r3-b.md` §B2(c), §B4; `media/cutout/RefineSession.kt` `keepAlpha` l.15-17; `ui/PhotoEditorScreen.kt` `redoSourceUri` l.164-165, load l.237-244, `writeCutout` l.913-935; `ui/AddEditClothingScreen.kt` edit mode l.595, l.672-678; OR1 `CutoutRecipe`.

## Files
`ui/PhotoEditorScreen.kt`; `media/cutout/RefineSession.kt`; `media/cutout/EditHistory.kt`; new `media/cutout/OriginalMapping.kt`; `ui/AddEditClothingScreen.kt`; `media/EditorPhotoState.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/OriginalMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `OriginalMapping`: from recipe + cutout size compute the transform cutout(x,y) → original(x,y) (crop offset, then scale if sizes differ); load the original region as an ARGB array matching the cutout canvas.
2. Editor load: when the garment has `originalUri` + recipe, provide an `originalLayer: IntArray` to the session; Keep uses it for RGB and allows alpha up from 0; Remove unchanged.
3. `redoSourceUri` returns the original for saved garments that have one; "Cut out again" runs the engine on the original (it is already the engine frame) and pushes `NewPhoto` into `EditHistory`.
4. On Done the new cutout replaces the stored one; original + recipe stay (a new recipe if "Cut out again" changed engine/options).

## Tests
V-OR3-1..3.

## Done when
V-OR3-1, V-OR3-2, V-OR3-3, V-OR3-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Change behaviour for garments without original, or write the original anywhere new.
