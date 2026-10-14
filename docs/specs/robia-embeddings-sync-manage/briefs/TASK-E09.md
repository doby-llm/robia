# TASK-E09 — Search and similar garments (both approved)

## Goal
A search field in Browse: "vestido rojo" keeps the active filters and orders all those garments from most to least similar (photo 70 %, name+notes 30 %). Without the model it searches words in name and notes. In each garment's detail, "See similar garments" opens Browse ordered by photo similarity to that garment.

## Read first
Mockup `browse-search.html`. TS§3.11, DEC-011. `ui/RobiaApp.kt`: `FilterBar`, `RobiaShell` (`filteredItems` derivation), `ItemDetailScreen`. `SearchRanker` (E04).

## Steps
1. `suggestions/SearchController.kt` (pure, testable): input flows `query`, `filteredIds`, `vectors`, `engineAvailable`; debounce 300 ms; output `SearchResult(orderedIds, mode: Semantic|Text|None)`. Query embedding via an injected `suspend (String) -> FloatArray?` (`holder.embedText(QUERY_PREFIX + q)`).
2. `RobiaShell`: `browseQuery` (`rememberSaveable`), `similarToGarmentId` state; call `holder.preload()` on first non-blank character; displayed list = controller output applied to `filteredItems`.
3. `FilterBar`: search pill per mockup (hint, clear ✕), line "Sorted by similarity · N garments" when active, text-mode hint with link to enable smart suggestions (opens Settings section).
4. Item detail: button "See similar garments" (secondary style) visible when that garment has an IMAGE vector; sets `similarToGarmentId`, clears query, navigates to Browse. Browse shows chip "Similar to “name”" ✕; ranking `SearchRanker.rankSimilar` over filtered items.
5. Strings from mockup table.

## Tests
`SearchControllerTest` (V-E09): debounce, semantic vs text fallback, blank query = original order, similar excludes self.

## Done when
CI green; V-E09; device V-Z08 and V-Z11 in Z01.
