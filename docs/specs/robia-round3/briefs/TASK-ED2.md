# TASK-ED2 — Editor: full undo/redo history

## Goal
Manu wants the whole history ("idealmente disponer de todo el historial"). Today: 10 undo steps, no redo of an undone edit, and "Redo cutout" keeps one previous photo only (inv-r3-d.md §D2).

## Read first
`artifacts/inv-r3-d.md` §D2 (current design and the proposed op log); `media/cutout/RefineSession.kt` (UNDO_DEPTH l.7, `apply/undo/reset/pushUndo`, `RunLengthAlpha`); `media/cutout/BrushStroke.kt` `brushMask`; `ui/PhotoEditorScreen.kt` `replaced` slot l.205, `undoEdit` l.334-345, `redoCutout` l.351-381; G-ADB1 PLAN-CHANGE (editor-wiring defects, if any).

## Files
`ui/PhotoEditorScreen.kt`; `media/cutout/RefineSession.kt`; new `media/cutout/EditHistory.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/EditHistoryTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `EditHistory.kt`: `sealed interface EditOp { Hard(mode, selectedRuns: BitRuns) ; Brush(mode, points: FloatArray, radiusPx, featherPx) ; Reset ; NewPhoto(tempFile: File) }`, `class EditHistory(base: FloatArray, w, h, budgetBytes: Long = 64L shl 20)` with `push(op)`, `undo()`, `redo()`, `canUndo/canRedo`, `current(): FloatArray`, checkpoints (RLE alpha) every 8 ops, oldest ops folded into a checkpoint when over budget, `close()` deletes temp files. Replay must reproduce `RefineSession` results bit-exactly (reuse its `apply` math; extract it as pure functions if needed).
2. `RefineSession` delegates its undo to `EditHistory` (remove `UNDO_DEPTH`); its public API used by the screen stays or is replaced consistently.
3. Screen: Undo (↶) and Redo (↷) icon buttons, enabled by `canUndo/canRedo`; "Cut out again" pushes `NewPhoto` (previous photo written to `cacheDir/editor-history/<uuid>.png`); undo across it restores the previous photo and its alpha state.
4. Apply each editor-wiring fix from the G-ADB1 PLAN-CHANGE (closed list) with a test.

## Tests
V-ED2-1..3; `RefineSessionTest` adapted only where the depth limit was pinned (keep assertion strength).

## Done when
V-ED2-1, V-ED2-2, V-ED2-3, V-ED2-4, V-ED2-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Persist history, change Light, change the selector or the clip rules from ED1.
