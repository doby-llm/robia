# TASK-SEL — Drag to select a range in Browse

## Goal
R3-06. Premise check: multi-select by long-press and bulk delete already exist (inv-r3-a.md §A4); the gap is selecting many at once.

## Read first
`artifacts/inv-r3-a.md` §A4; `ui/BrowseGrid.kt` pinch `pointerInput` l.260-293, `combinedClickable` l.510-515; `ui/RobiaApp.kt` selection l.903, 1139-1155.

## Files
`ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; new `ui/DragSelect.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DragSelectTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Pure `DragSelect` state: anchor, current, baseline set → selected set.
2. Grid-level `pointerInput` that activates only after a long-press started selection; hit-test via `gridState.layoutInfo.visibleItemsInfo`; autoscroll with `scrollBy` in a loop while in an edge zone.
3. Card-level long-press semantics and TalkBack actions kept.

## Tests
V-SEL-1, V-SEL-2.

## Done when
V-SEL-1, V-SEL-2, V-SEL-D pass; CI green.

## Do not
Change delete, archive or pinch.
