# TASK-SPK — Smart features without the shiny animation

## Goal
R3-14, answer 3A. Mockup `mockups/sparks.html`.

## Read first
`mockups/sparks.html`; `artifacts/inv-r3-a.md` §A7; `core/designsystem/Motion.kt` l.64-172; the five call sites (BrowseFilterBar l.356, ItemDetailScreen l.298, AddEditClothingScreen l.1371, ColorReviewScreen l.108, TagReviewScreen l.299).

## Files
`core/designsystem/Motion.kt`; `ui/BrowseFilterBar.kt`; `ui/ItemDetailScreen.kt`; `ui/AddEditClothingScreen.kt`; `ui/ColorReviewScreen.kt`; `ui/TagReviewScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/core/designsystem/MotionTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Filters-row sparkle: plain `IconButton` with `Icons.Outlined.AutoAwesome` tinted terracotta (`tertiary`-family token in use today; no new token).
2. Other four: normal Material button for their role + trailing ✦ icon 16 dp.
3. `SparkleBurst` played once at the control when the action completes (suggestions applied, review done, similar list shown).
4. Remove `aiSurface`, `rememberAiShimmerBrush` and the idle loop.

## Tests
V-SPK-2; contract test updated where it pinned aiSurface (equal strength: assert the burst instead).

## Done when
V-SPK-1, V-SPK-2, V-SPK-D pass; CI green.

## Do not
Change actions or copy.
