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
