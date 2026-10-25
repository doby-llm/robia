# TASK-REV-PROGRESS — Review progress bar follows the count

## Goal
F19 (REQ-FB-19): in Review garments with 222 garments the bar was about half full at item 219 and jumped to 100 % after the last. The counting is right (`scannedCount` +1 per garment over `garments.size`, and the text uses the same counters). The cause is `animateFloatAsState(progress, tween(300))`: the target moves every 20-40 ms (`ReviewPlanner.scanEach` adds `delay(16)` per garment), each retarget restarts an ease-in tween from the current value, so the bar covers only a few percent of its gap per restart and lags; at the end `isScanning = false` sets the target to 1 and it catches up.

## Read first
`ux2.md` §F19; `ui/TagReviewScreen.kt` (~374-445: `scannedCount`, `progress`, `animatedProgress`); `ui/ColorReviewScreen.kt` (~240-255: `processedCount`, `animatedProgress`); `ui/ReviewScaffold.kt` `ReviewProgressHeader` (~243; it already takes a raw `Float` and clamps it, no animation inside); `suggestions/ReviewPlanner.kt` `scanEach` (~100) and `SCAN_STEP_MILLIS` (~197); `TagReviewScanTest`.

## Files
`ui/TagReviewScreen.kt` (progress state only); `ui/ColorReviewScreen.kt` (progress state only); `ui/ReviewScaffold.kt` (the pure function; the bar only if needed); new `app/src/test/java/com/gusanitolabs/robia/ui/ReviewProgressTest.kt`.

## Steps
1. `ReviewScaffold.kt`: `internal fun reviewScanProgress(done: Int, total: Int, scanning: Boolean): Float` — not scanning → 1f; scanning → `(done.coerceIn(0, total.coerceAtLeast(0)).toFloat() / total.coerceAtLeast(1))`.
2. Both screens: replace the `progress`/`animatedProgress` pair with `reviewScanProgress(scannedCount or processedCount, count, isScanning)` and pass it to `ReviewProgressHeader` directly. Remove the `animateFloatAsState`/`tween` imports if unused. One step is 0.45 % at 222 garments, so no spring is needed; do not add easing.
3. Leave `SCAN_STEP_MILLIS` alone: `ReviewPlanner.kt` is outside Files, and the 16 ms step is not the bug (222 steps are about 3.6 s of delay in total); if you think it should shrink, say so in the receipt.
4. Check that the "N of M" text and `isScanning` transitions are unchanged and that the finished state still shows 100 %.

## Tests
V-REV-PROGRESS-1 (CI); V-REV-PROGRESS-2 (REV); V-REV-PROGRESS-D (Manu).

## Done when
V-REV-PROGRESS-1, -2 pass, V-REV-PROGRESS-D is ready for Manu; CI green.

## Do not
Change the review layout, semantics or copy (REV will rewrite these screens; keep the diff small so the rewrite is not blocked); touch `ReviewPlanner.kt`.
