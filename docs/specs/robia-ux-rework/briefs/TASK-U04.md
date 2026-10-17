# TASK-U04 — Manage horizontal swipe

## Goal
Manage tabs can only be changed by tapping the tab row (R1). Add swipe between tabs with `HorizontalPager` while keeping the hoisted tab id and the existing per-tab list untouched.

## Read first
TS§4; `ui/ManageTagsScreen.kt` lines ~102-300 (`ScrollableTabRow` 159-185, `activeTabId` fallback 127, body `LazyColumn` from 186); `ManagePerformanceContractTest.kt` (anchors).

## Steps
1. Extract the body `LazyColumn` for one tab into `@Composable private fun ManageTabPage(tabId: String, …)` — same code, parameters for whatever it reads.
2. Add pager state, `LaunchedEffect`s and `HorizontalPager` exactly as TS§4 (`key = { tabs[it] }`, `beyondViewportPageCount = 0`). Opt-in `@OptIn(ExperimentalFoundationApi::class)` if the compiler asks.
3. Tab click: `scope.launch { pagerState.animateScrollToPage(i) }` (keep calling `onSelectedTabIdChange` through the settledPage effect only — one source of truth).
4. Keep the stale-id fallback (line ~127) working: if `activeTabId` is not in tabs, page 0.

## Tests
No JVM UI test available; keep `ManagePerformanceContractTest` green, adapting only anchors that named the moved block (same property). Add a tiny pure test if you extract an index helper (`pageFor(tabs, id)`).

## Done when
V-U04-1, CI green; receipt states the device row V-Z2-05.

## Do not
Change tag/colour rows, dialogs, review triggers, or `RobiaApp.kt`.
