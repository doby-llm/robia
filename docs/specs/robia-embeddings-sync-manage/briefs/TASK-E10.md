# TASK-E10 — Item-detail diagnostics (developer mode)

## Goal
For tuning without guessing: in developer mode the garment detail shows why a suggestion was or was not applied (top-5 scores, margins, mode, model, recipe, inputs, timings) and offers "Recompute embedding" and "Export input image".

## Read first
Mockup `dev-diagnostics.html`. TS§3.12. `SuggestionService` (E05), `EmbeddingBackfill` (E06). `ItemDetailScreen`, developer-mode flags in `RobiaApp.kt`.

## Steps
1. `SuggestionService.diagnose(garment): SuggestionDiagnostics?` — uses the stored IMAGE vector (no image inference), current tags and examples; returns category + occasion results, model/recipe ids, `computedAt`, durations, inputs present.
2. `ui/SuggestionDiagnosticsCard.kt`: monospace block exactly like the mockup + two buttons. Recompute → `embeddingRepository.deleteAll(id)` + `backfill.prioritize(id)`. Export → same exporter as E05.
3. Visibility predicate `showSuggestionDiagnostics(devUnlocked, devEnabled)` (pure function).

## Tests
`DiagnosticsVisibilityTest` (V-E10).

## Done when
CI green; V-E10.
