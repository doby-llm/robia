# TASK-M01 — Manage redesign + scroll performance

## Goal
Manage scrolls with lag because each tag section is one huge list item (Category has 19 rows drawn at once) and because the app re-maps every garment on every recomposition, even on Manage. Redesign Manage as in the approved mockup (tabs per tag type, tag search, one list item per row) and stop the unnecessary garment mapping.

## Read first
Mockup `manage.html`. TS§6. `ui/ManageTagsScreen.kt`, `ui/TagLocalization.kt` (`localizedTagLabel`), `ui/RobiaApp.kt` (`RobiaShell`: `toUiWardrobeItems`, `filteredItems`). Reference only (do not merge): commit `4f875a7` on branch `kanban/t_b1bd1d9d/manage-performance-remediate` shows a non-Composable `localizedTagLabel(context)`; view it with `git show 4f875a7`.

## Steps
1. `TagLocalization.kt`: add `fun GarmentTag.localizedTagLabel(context: Context): String` (non-Composable; same logic as the Composable one, which should delegate to it).
2. `ManageTagsScreen.kt`: layout TS§6 — search field, `ScrollableTabRow` (tag categories in existing order + "Colors"), `rememberSaveable` selected tab; LazyColumn with keyed items (`section-header:`, `tag-row:` with `contentType = "tag-row"`, `section-empty:`); Colors tab = existing palette card unchanged. Labels: `remember(tags, configuration.locales) { tags.associate { it.id to it.localizedTagLabel(context) } }`. Row shows description line and "No occasions" badge only when those fields exist (they do after D01; values are edited in E08).
3. `RobiaApp.kt`: make `toUiWardrobeItems` non-Composable taking `context`; compute inside `remember(context, clothingItems, syncState, needsWardrobeUiModels)`; `needsWardrobeUiModels` true only on Browse, ItemDetail, AdvancedFilters (TagReview is added by E08 — leave a comment).
4. Strings from the mockup table.

## Tests
`ManagePerformanceContractTest` (V-M01): source assertions on `ManageTagsScreen.kt` (`"tag-row:"`, `contentType = "tag-row"`) and that `toUiWardrobeItems` is not preceded by `@Composable` in `RobiaApp.kt`.

## Done when
CI green; V-M01; device V-Z01 in Z01.
