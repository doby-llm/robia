# TASK-UI-CAL — Duplicate calibration screen: loading and empty states

## Goal
F9 (REQ-FB-09): Developer › Duplicate calibration showed only the description at the top. Cause (medium confidence): no loading state; `pairs` stays null until `scanCalibrationPairs` finishes, which re-encodes every active photo to hash it (tens of seconds for 115 garments). Second candidate: an empty result (no vectors yet, stale hashes, no categories, or a swallowed exception whose log is DEBUG-only). This unblocks G-DUP: Manu redoes the 30 pairs on a build containing this task.

## Read first
`R3-DEC-041`; planner investigation `ui.md` §F9; `ui/DuplicateCalibrationScreen.kt` (~55-81); `suggestions/DuplicateScan.kt` (`scanCalibrationPairs`, `calibrationPairs` ~126-140, hash check ~266, DEBUG-only log ~213-215); `DuplicateScanTest`; `DuplicateCalibrationScreenTest`.

## Files
`ui/DuplicateCalibrationScreen.kt`; `suggestions/DuplicateScan.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateCalibrationScreenTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `DuplicateScan.kt`: the calibration scan also returns counts (active garments, with vector, hash matched, categorised) next to the pairs (a small data class); failures are logged outside DEBUG too (one `Log.w` line with the exception class and message, no photo data).
2. Pure `calibrationUiState(pairs, counts)` → `Loading` (null), `Empty(counts)`, `Pairs(list)`; `DuplicateCalibrationScreen` renders a `CircularProgressIndicator` with a short label for Loading and a message with the four counts for Empty (strings x3, e.g. "No pairs to compare yet. Active: %1$d · with vector: %2$d · photo matched: %3$d · with category: %4$d").
3. Keep the existing signature/marks logic untouched.

## Tests
V-UI-CAL-1, V-UI-CAL-2.

## Done when
V-UI-CAL-1, V-UI-CAL-2 pass, V-UI-CAL-D is ready for Manu; CI green. The PR receipt says Manu must redo the 30 pairs (G-DUP) on the build containing this task.

## Do not
Change the pair selection (top 30), thresholds or the Browse duplicate scan; wait for the embedding backfill here.
