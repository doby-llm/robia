# TASK-U03 — Filters: grouped screen, Reset, active-filter row, OR/AND

## Goal
The Advanced Filters screen shows bare uppercase titles over loose chips ("floating labels", R2); resetting needs two taps (R3); and selecting two seasons returns only garments carrying BOTH because `matches` uses `selectedTagIds.all(...)` while Category/Location are forced single-select (UX-DEC-003). Rebuild per mockup `filters.html` + browse variant A bar.

## Read first
TS§3; `ui/BrowseFilterBar.kt` (`BrowseFilterState`, `FilterBar`, toggle helpers `toggleTag`, `toggleSingleValueTag`); `ui/AdvancedFiltersScreen.kt`; call site of `FilterBar(` in `ui/BrowseGrid.kt` (`BrowseWardrobeScreen`); `onResetFilters` wiring in `ui/RobiaApp.kt` (do not edit it: it already resets `browseFilters`).

## Steps
1. `BrowseFilterState.matches(item, paletteColors)`: implement the semantics table below as a pure function and keep the signature. A selected tag that the item does not carry tells you nothing about its category, so add a field `selectedTagCategories: Map<String, String>` (tagId → categoryId) filled by the toggle helpers, so `matches` needs no extra parameter. Default empty map; the toggle adds/removes entries together with `selectedTagIds`.
2. Replace `toggleSingleValueTag` usages with the multi toggle; delete it if unused.
3. `FilterBar`: new parameter `onResetFilters: () -> Unit`; after the Filters chip `if (hasActiveFilters) TextButton(onResetFilters) { Text(stringResource(R.string.reset_filters_short)) }`; below the Row an `ActiveFilterChipsRow(filters, availableTags, paletteColors, onFiltersChange)` (LazyRow, `InputChip(selected = true, trailingIcon = close)`), one chip per selected tag (label = localized), per "Not set" category ("<Category>: Not set"), per colour (12 dp swatch leading), favourites. ✕ removes exactly that entry.
4. `BrowseGrid.kt`: pass `onResetFilters` to `FilterBar(` (only that line).
5. `AdvancedFiltersScreen`: `Scaffold` per TS§3 with `FilterCard` sections (Favourites switch row; Category; Season; Occasion; Location; Colour grid). Tag sections: `FlowRow` showing the first N chips that fit 2 rows — simplest robust approach: show the first 8 chips sorted + a "+%d more" chip when more exist; tapping expands to all (state per section, `rememberSaveable`). "Not set" chip last in each tag section. Selected count badge per section. Bottom bar `Button(onShowResults) { Text(pluralStringResource(R.plurals.filters_show_count, n, n)) }`. Top bar: ✕ (back), title, `TextButton("Reset")` enabled iff `hasActiveFilters`.
6. Sorting with `Collator.getInstance(currentLocale)`.

## Semantics table (unit test every row) — item tags vs selection → expected
| Selection | Item tags | Pass |
|---|---|---|
| none | any | yes |
| Summer, Winter (season) | Summer | yes |
| Summer, Winter | Spring | no |
| Dresses (category) + Summer | Dresses, Winter | no |
| Dresses + Summer | Dresses, Summer | yes |
| Dresses, Skirts | Skirts | yes |
| Category "Not set" | no category tag | yes |
| Category "Not set" + Dresses | Dresses | yes |
| Category "Not set" + Dresses | Skirts | no |
| colours Red, Black | primary Black | yes |
| favourites + Dresses | Dresses, not favourite | no |

## Tests
`BrowseFilterStateTest` (table above, V-U03-1), count/hasActive (V-U03-2), sorting (V-U03-4).

## Done when
V-U03-1..4; independent review passes V-U03-3.

## Do not
Change search, similar-to, the empty-state reset, `RobiaApp.kt`, or the saved/hoisted state type beyond the added map field.
