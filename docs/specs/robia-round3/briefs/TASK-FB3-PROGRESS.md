# TASK-FB3-PROGRESS — Smooth progress bar after the scan

## Goal
ui3 F22 (REQ-FB-23, R3-DEC-058). In the tag review, once the scan has ended, the "N left" progress bar moves in steps: Apply jumps it, "Apply all remaining" jumps it to full, Undo steps it back. It should glide.

## Read first
`artifacts/inv-f3-a.md` F22; `R3-DEC-058` and `R3-DEC-051` (why the scan header must NOT animate); `ui/TagReviewCard.kt` (`ReviewLeftHeader`); `ui/ReviewScaffold.kt` (`reviewScanProgress`, `ReviewProgressHeader`, `REVIEW_ACCEPT_*` constants); `ui/TagReviewCardModel.kt` (`reviewHeaderState`: progress is decided / found); `ui/TagReviewScreen.kt` where the two headers are chosen (`headerState.showsScan`); `ReviewProgressTest`.

## Files
`ui/TagReviewCard.kt` (`ReviewLeftHeader` only); `ui/ReviewScaffold.kt` (the new pure function only); `app/src/test/java/com/gusanitolabs/robia/ui/ReviewProgressTest.kt`.

## Steps
1. In `ReviewScaffold.kt`, next to `reviewScanProgress`, add `internal fun reviewProgressAnimationMillis(reducedMotion: Boolean): Int` returning 400, or 0 when reduced motion is on.
2. In `ReviewLeftHeader`, feed the bar from `animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = ...)`: `tween(reviewProgressAnimationMillis(false), easing = FastOutSlowInEasing)`, or `snap()` when `LocalReducedMotion.current` (`core.designsystem`). The bar keeps its 8 dp height and `CircleShape` clip.
3. Do NOT touch `ReviewProgressHeader` (the scan header): REV-PROGRESS removed its tween because the scan target moves every 16 to 40 ms and a restarted tween lagged. Decisions are seconds apart, so a tween is safe only here.
4. Optional (skip if it needs a change outside the two files): a 200 ms cross-fade between the scan header and `ReviewLeftHeader` at the end of the scan. If you skip it, say so in the receipt; the 100 % to 0 % drop at that moment is judged on the device (V-FB3-PROGRESS-D).
5. **Tests** in `ReviewProgressTest`: the two values of `reviewProgressAnimationMillis`; the existing `reviewScanProgress` cases unchanged; a source-contract test that the body of `ReviewProgressHeader` in `ReviewScaffold.kt` contains neither `animateFloatAsState` nor `tween` (read the file the way `TagReviewUndoTest.resource` does).

## Tests
V-FB3-PROGRESS-1 (CI); V-FB3-PROGRESS-2 (REV); V-FB3-PROGRESS-D (Manu). The motion itself is device-only.

## Done when
Rows pass; CI green; the receipt says whether step 4 was done.

## Do not
Animate the scan header, change `reviewHeaderState` or the counts, touch the colour review, or add a spring.
