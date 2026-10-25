# TASK-PICKER-FIX — Pickers do not jump or lose the option count

## Goal
F17 (REQ-FB-17): in the category, occasion and location pickers the text "22 options" disappears at random after a little scrolling or after selecting an option, and the whole menu shifts. Three causes (`ux2.md` §F17, ranked), all in the same two composables: (1) `ReviewPickerList` composes the caption (and its 8 dp padding) only while `scrollHintVisible(value, max)` holds; at the bottom of a long list it leaves the composition, the column shrinks by about 24 dp and a bottom-anchored sheet or centred dialog jumps. (2) `TagPickerSheet` shows or hides a suggested-hint line above the list whenever a selection changes `sheetShowsSuggestedHint`. (3) `ModalBottomSheet` keeps `skipPartiallyExpanded = false`, so a tall sheet opens half-expanded and the first drag expands it before the list moves.

## Read first
`ux2.md` §F17; `ui/ReviewScaffold.kt`: `scrollHintVisible` (~85), `ReviewPickerList` (~201-240); `ui/GarmentFieldRows.kt`: `sheetShowsSuggestedHint` (~70), `TagPickerSheet` (~180-230), `TagPickerRow`; tests `ReviewScrollHintTest`, `GarmentFieldRowsTest`; M3 1.3.1 `rememberModalBottomSheetState`.

## Files
`ui/ReviewScaffold.kt` (`ReviewPickerList` only); `ui/GarmentFieldRows.kt` (`TagPickerSheet` only); new `app/src/test/java/com/gusanitolabs/robia/ui/PickerStabilityTest.kt`; `GarmentFieldRowsTest.kt` and `ReviewScrollHintTest.kt` only if a helper they cover changes (their assertions stay).

## Steps
1. Caption: add `internal fun pickerCaptionVisible(optionCount: Int, maxValue: Int): Boolean` (true when `optionCount > PICKER_CAPTION_MIN_OPTIONS` (a named constant, about 8: rows are about 48 dp against the 360 dp cap) or `maxValue > 0`). `ReviewPickerList` composes the caption from it; the scroll position is not an input. The bottom fade stays driven by `scrollHintVisible(value, max)` but only as an overlay inside the `Box` (it must not change the column's height). `scrollHintVisible` and its test stay unchanged.
2. Suggested hint: add `internal fun sheetReservesSuggestedHint(suggestedIds, categoryTagIds): Boolean` (any suggested id belongs to the category). In `TagPickerSheet` compose the hint line whenever it is reserved, with `Modifier.alpha(if (sheetShowsSuggestedHint(...)) 1f else 0f)` and cleared semantics while hidden, so its height is constant for the sheet's lifetime. `sheetShowsSuggestedHint` and its test stay unchanged. When no suggested tag belongs to the category no row exists at all.
3. `val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` passed to `ModalBottomSheet` in `TagPickerSheet`.
4. Tests in `PickerStabilityTest`: V-PICKER-FIX-1 and -2. Height stability needs Compose tests, which the project does not have; say so in the receipt and leave it to V-PICKER-FIX-D.

## Tests
V-PICKER-FIX-1, V-PICKER-FIX-2 (CI); V-PICKER-FIX-3 (REV); V-PICKER-FIX-D (Manu).

## Done when
V-PICKER-FIX-1..3 pass, V-PICKER-FIX-D is ready for Manu; CI green.

## Do not
Change row design, sorting, strings, other sheets or the review screens (REV rebuilds them); introduce a LazyColumn; add new colour literals.
