# TASK-NAV-BACK — Back peels one layer, then asks twice to exit

## Goal
F15 (REQ-FB-15, R3-DEC-048): today Back exits the app from plain, searched, filtered and selection Browse and from Manage. Tabs use `replaceRoute`, so these routes have a stack of size 1 and the only root handler (enabled for restore or `routeStack.size > 1`) does not cover them. Add already has its own `BackHandler` (discard dialog if there are unsaved changes, else Browse) and is correct.

## Read first
`R3-DEC-048`; planner investigation `ux2.md` §F15; `ui/RobiaApp.kt`: `replaceRoute`/`popRoute` (~1195-1210), the existing `BackHandler` (~1672), `selectedBrowseItemIds` (~958), `browseQuery`, `similarToGarmentId`, `browseFilters` (~933-935, cleared together at ~2015-2017), `findActivity()` (~3338), the existing `Toast.makeText` calls (~1540); `ui/BrowseFilterBar.kt` `hasActiveFilters` (~135).

## Files
`ui/RobiaApp.kt` (the new handler and its state only); new `ui/BackExitPolicy.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/BackExitPolicyTest.kt` (plus imports and the three `strings.xml`).

## Steps
1. `BackExitPolicy.kt`: `data class BackState(onManage: Boolean, selectionCount: Int, hasQuery: Boolean, hasSimilarTo: Boolean, hasFilters: Boolean, lastExitHintMs: Long?)`, `sealed interface BackAction { ClearSelection; ClearSearchAndFilters; GoToBrowse; ShowExitHint; Exit }`, `const val EXIT_WINDOW_MILLIS = 2000L`, `fun decideBack(state, nowMs): BackAction`. Order: selection → query/similar-to/filters (together) → Manage → Browse → exit hint → exit (hint shown at most 2000 ms ago and not in the future). Pure, no Android types.
2. `RobiaApp.kt`: after the existing handler add `BackHandler(enabled = routeStack.size == 1 && restoreProgress == null && (currentRoute == RobiaRoute.Browse || currentRoute == RobiaRoute.ManageTags))`. Build the state, call `decideBack(state, SystemClock.elapsedRealtime())`, apply the action (clear selection = `selectedBrowseItemIds = emptySet()`; clear all = query, similar-to and `BrowseFilterState()` as the clear-all block does; GoToBrowse = `replaceRoute(RobiaRoute.Browse)`; ShowExitHint = `Toast.LENGTH_SHORT` with `back_exit_hint` and store the time; Exit = `context.findActivity()?.finish()`).
3. Keep the hint time in a `remember` state and reset it (`null`) when the route, query, similar-to, filters or selection change (a `LaunchedEffect` on those keys, or derive from them).
4. Strings: EN "Press back again to exit", ES "Pulsa atrás otra vez para salir", DE "Zum Beenden erneut Zurück drücken". Add as the `<!-- NAV-BACK -->` block.

## Tests
V-NAV-BACK-1, V-NAV-BACK-2 (CI); V-NAV-BACK-3 (REV); V-NAV-BACK-D (Manu).

## Done when
V-NAV-BACK-1..3 pass, V-NAV-BACK-D is ready for Manu; CI green.

## Do not
Touch the existing handler, Add's, the review or batch handlers; add a predictive-back manifest flag; change anything else in `RobiaApp.kt` (DUP-TRIGGER and DUP2 write the same file after you).
