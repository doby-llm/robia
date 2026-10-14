# TASK-E04 — Scoring library (pure Kotlin)

## Goal
Implement all suggestion maths as plain Kotlin with no Android dependency, so it is fully tested in CI: category choice, occasion choice, which garments a tag change affects, and search ranking. Later tasks only call these functions.

## Read first
DEC-008, DEC-009, DEC-010, DEC-011 (decision-log.md) — they are the exact algorithm. TS§3.1 (recipe/text rules), §3.6, §3.10, §3.11. Fixture `app/src/test/resources/suggestions/golden-scores.json` (from E02). `suggestions/VectorMath.kt`, `EmbeddingRecipe.kt` (from D01).

## Steps
1. `EmbeddingRecipe.kt`: append scoring constants of TS§3.1 (`CLASSIFICATION_PREFIX`, `QUERY_PREFIX`, `DOCUMENT_PREFIX`, margins, `KNN_TOP`, weights, `SUGGEST_OCCASIONS_DEFAULT_OFF`) and `DEFAULT_LABEL_DESCRIPTIONS` copied from `tools/embedding-precision/labels.py` (second element of each tuple, 19 categories + 6 occasions). Functions `labelTextFor(tag: GarmentTag): String` (3 rules in TS§3.1; "renamed" = `TagDisplayPolicy.shouldUseStoredName(tag)` returns true — read that function to confirm its meaning) and `garmentTextInput(name, notes): String?`. Model/runtime constants are added by E01; if E01 merged first, keep both sets.
2. `CategoryScorer.kt`: `scoreLabels(image, labels: List<Pair<String, FloatArray>>, examples: Map<String, List<FloatArray>>)` → text cosine per label; knn = mean of top-`min(3, n)` cosines among that label's examples; COMBINED iff ≥ 2 labels have knn; score per DEC-008 step 3. `selectCategory(scored, mode)` → marked iff gap ≥ margin. Sorting: score desc, tagId asc.
3. `OccasionSelector.kt`: same `scoreLabels`; `selectOccasions` → DEC-009 (strict `>`); `flag_off` handled by caller-provided boolean `suggestOccasions`.
4. `ReviewPlanner.kt`: `TagChangeSet` + `ReviewProposal(garmentId, before: Set<String>, proposed: Set<String>, kind: Suggested|ChooseManually)` and `plan(...)` per DEC-010. Garments without IMAGE vector: counted in `skipped` (except category-deleted → `ChooseManually`).
5. `SearchRanker.kt`: `rankByQuery(itemIds, query, vectors)` (0.7/0.3; TEXT term omitted if no TEXT vector → image cosine only; items without IMAGE last, ordered by name via a provided `nameOf`), `rankSimilar(selfId, itemIds, vectors)` (exclude self), `textFallback(items, query)` (NFD, strip `\p{Mn}`, lowercase; every query word must be contained in name+notes).
6. Every function deterministic; no `Random`, no clock.

## Tests (new, `suggestions/`)
`CategoryScorerTest`, `OccasionSelectorTest`, `ReviewPlannerTest`, `SearchRankerTest`, `EmbeddingRecipeTest` (25 descriptions; every default `category-*`/`occasion-*` id in `DefaultTags` has one; `labelTextFor` 3 branches), `GoldenFixtureTest` (load JSON from classpath `/suggestions/golden-scores.json`, run both modes, assert rank order, selections and |Δscore| ≤ 1e-4). Use dim-4 hand-made vectors for branch tests; write the expected numbers in comments.

## Done when
CI green; V-E04a…e.
