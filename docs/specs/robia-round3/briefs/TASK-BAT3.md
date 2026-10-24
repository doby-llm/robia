# TASK-BAT3 — Batch processing: the list follows the garment being processed

## Goal
F7 (REQ-FB-07): with many garments in a batch the user has to keep scrolling down to see the one being processed. The list should follow it smoothly and let the user take control.

## Read first
`R3-DEC-041`; `ui/BatchAddClothingScreen.kt` (`LazyColumn` ~196, row states, ETA header); `ui/BatchProcessingCoordinator.kt` (read-only: which row is being processed); `core/designsystem/Motion.kt` (reduced-motion helper); `BatchEtaTest` for the pure-function test style.

## Files
`ui/BatchAddClothingScreen.kt`; new `ui/BatchFollowState.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/BatchFollowStateTest.kt` (plus imports, the three `strings.xml`).

## Steps
1. Pure `BatchFollowState` in `BatchFollowState.kt`: `following` on at start; `onUserScroll()` turns it off; `onFollowTapped()` and `onAtBottom()` turn it on; `targetIndex(rows)` = index of the garment currently processing (the first Processing row, else none). No Compose or Android types.
2. Screen: a `LaunchedEffect` keyed on the target index calls `animateScrollToItem(target)` while following; a manual drag (detect via `listState.isScrollInProgress` with the effect not the source of the scroll) calls `onUserScroll()`; when the list reaches the bottom it calls `onAtBottom()`. A small "Follow" text/icon button (string x3, e.g. "Follow") appears only while following is off and a batch is processing; tapping it resumes.
3. Reduced motion: `scrollToItem` (no animation). Do not move the ETA header or the ✕.

## Tests
V-BAT3-1 (CI), V-BAT3-2 (REV).

## Done when
V-BAT3-1, V-BAT3-2 pass, V-BAT3-D is ready for Manu; CI green.

## Do not
Change batch ordering, concurrency, ETA or row layout; scroll while the user is dragging.
