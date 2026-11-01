# TASK-C03 — Collections in the editor, item detail and filters

## Goal
Every editor/detail/filter surface lists categories by hardcoded id, so collections are invisible there, and the
editor's picker would even be titled "Seasons" and be single-select for an unknown id
(`ui/AddEditClothingScreen.kt:1185-1196`). Add collections to these surfaces per SPEC R4-D06 (mockup frames 8, 9).
Garment edits already carry every tag id through save (`AddEditClothingScreen.kt:697,951,1009`), so memberships are
preserved — keep it that way.

## Read first
SPEC R4-D06, REQ-COL-06/07; mockup frames 8 and 9; `artifacts/inv-r4-collections-sites.md` §2.4–2.5;
`ui/AddEditClothingScreen.kt:1097-1200` (field rows, `TagPickerSheet` title/multi), `ui/GarmentFieldRows.kt:189-262`;
`ui/ItemDetailScreen.kt:528-566`; `ui/AdvancedFiltersScreen.kt:110-240`, `TagFilterCard` (:310-), `tagChipSlots` (:439-444);
`ui/BrowseFilterBar.kt:126-176` (`BrowseFilterState`), `:579-585` (`categoryTitleRes`).

## Steps
1. New pure file `ui/CollectionFieldRules.kt`:
   `fun isMultiSelectCategory(categoryId: String): Boolean` = occasion, season, collection;
   `fun showsCollectionRow(availableCollectionCount: Int, garmentCollectionCount: Int): Boolean` = either > 0;
   `fun showsCollectionDetailRow(garmentCollectionCount: Int): Boolean` = > 0;
   `fun showsCollectionFilterCard(availableCollectionCount: Int): Boolean` = > 0.
2. `AddEditClothingScreen.kt`: replace `multi = categoryId == "occasion" || categoryId == "season"` with
   `isMultiSelectCategory(categoryId)`; add `"collection" -> R.string.metadata_collections` to the picker title `when`
   (keep `else -> metadata_season` for season). Add a `GarmentFieldRow` for collections AFTER the Occasions row
   (frame 8), icon `categoryIconFor("collection")`, value = selected collection names alphabetical joined by ", ",
   `aiSuggested = false`, shown per `showsCollectionRow`. Sheet subtitle `choose_collections_hint` only if
   `TagPickerSheet` already supports a subtitle; otherwise skip it (do not change GarmentFieldRows.kt).
3. `ItemDetailScreen.kt`: a Collections row after Occasions, per `showsCollectionDetailRow`. Share metadata unchanged.
4. `AdvancedFiltersScreen.kt`: `collectionTags = availableTags.filter { it.categoryId == "collection" }` and a
   `TagFilterCard(categoryId = "collection", …)` after the Occasion card, shown per `showsCollectionFilterCard`, with
   the existing "Not set" option labelled `filter_collection_none` ("Not in any") if the card supports a custom label;
   otherwise the existing "Not set" wording is acceptable — say which in the receipt.
5. `BrowseFilterBar.kt` `categoryTitleRes`: `"collection" -> R.string.filter_collection`. Nothing else in that file.
6. Tests: `CollectionFieldRulesTest` (V-C03-1, V-C03-4 via resource-id mapping functions if you extract them; else a
   source-contract assertion), V-C03-2 (garment in 2 collections keeps both after the editor's save-set helper — if
   no helper exists, extract the one-liner that builds the saved tags into `CollectionFieldRules` and use it),
   V-C03-3 in `BrowseFilterStateTest` (add cases only).

## Done when
V-C03-1 … V-C03-4 green in CI; V-C03-D in the receipt.

## Do not
Touch export/share/card text/analytics; add "create collection" in the sheet; change RobiaApp.kt or GarmentFieldRows.kt.
