# TASK-ANA — Analytics screen

## Goal
R3-10. Mockup `mockups/analytics.html`.

## Read first
`mockups/analytics.html`; `artifacts/inv-r3-c.md` §C3; `ui/BrowseFilterBar.kt` row l.337-375; `ui/RobiaApp.kt` routes l.249-298, filters l.881.

## Files
new `ui/AnalyticsScreen.kt`; new `core/model/WardrobeStats.kt`; `ui/BrowseFilterBar.kt`; `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/core/model/WardrobeStatsTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `WardrobeStats` pure; groups: Categories, Colours, Occasions, Seasons, Fit, Locations, then user-created tag categories.
2. Screen: header "N garments · archived not counted", one card per group, Compose `Canvas`/`Box` bars, count right, swatch for colours.
3. Icon `Icons.Outlined.BarChart` (content description `analytics_open` "Wardrobe stats") right of Filters.

## Tests
V-ANA-1, V-ANA-2.

## Done when
V-ANA-1, V-ANA-2, V-ANA-D pass; CI green.

## Do not
Add a library or new DB queries.

## PLAN-CHANGE 2026-10-23 (feedback item 2): bars grow on open
Source: feedback/2026-10-23-manu-telegram.md item 2.

4. Bar growth on open: when the screen is shown, each bar animates its width from 0 to its value, from the axis (left) to the right, in one short smooth transition (<= 400 ms; Compose animateFloatAsState or Animatable; no new library). A pure function maps progress (0..1, clamped) to width and is monotonic and equals the final width at progress 1. When reduced motion is on (system animator duration scale 0), show the final width at once.
5. Tests: V-ANA-3 is the pure mapping test in WardrobeStatsTest. V-ANA-4 is a review item: the animation runs on screen entry, not on every data change or recomposition.
