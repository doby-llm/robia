# TASK-T01 — Socks and Underwear categories

## Goal
There is no category for socks or underwear, so a photo of socks is suggested as Trousers (R15). Add exactly two default categories. Evidence (UX-DEC-010): on Manu's 142 labelled photos, adding them changes top-1 for only 2 garments; a "Sleepwear" label would steal 32 and is rejected.

## Read first
TS§T; `core/model/DefaultTags.kt` (tags list lines 12-30; `category-jewelry` is the template: `suggestOccasions = false`); `suggestions/EmbeddingRecipe.kt` `DEFAULT_LABEL_DESCRIPTIONS` (31-57); `ui/TagLocalization.kt` `defaultTagLabelRes` (20-52); `data/LocalRepositories.kt` `seedDefaultsIfNeeded` (~230-238) and `core/model/TagDedupePlanner.kt` (`DefaultTagSeeds.withoutDeleted`); `tools/embedding-precision/labels.py`; tests `suggestions/EmbeddingRecipeTest.kt` (asserts 25), `suggestions/GoldenFixtureTest.kt` (frozen fixture with 19 categories); existing DAO tests under `app/src/test/java/com/gusanitolabs/robia/data/` for the sqlite-jdbc setup pattern.

## Steps
1. `DefaultTags.tags`: append the two entries from TS§T after `category-coats`.
2. `EmbeddingRecipe.SUGGEST_OCCASIONS_DEFAULT_OFF` (line ~28) += `"category-socks", "category-underwear"`. `DEFAULT_LABEL_DESCRIPTIONS`: `"category-socks" to "socks or tights: hosiery worn on the feet and legs"`, `"category-underwear" to "underwear: bra, briefs, boxers or lingerie worn under clothes"`. Same in `labels.py` CATEGORIES (name, description).
3. Strings `tag_socks`, `tag_underwear` (EN Socks / Underwear, ES Calcetines / Ropa interior, DE Socken / Unterwäsche) and the two mappings in `defaultTagLabelRes`.
4. Tests: `EmbeddingRecipeTest` 25 → 27 and assert both ids/texts. `GoldenFixtureTest`: the fixture is frozen parity data — do not regenerate it; if any assertion compares fixture labels with `DefaultTags`/`DEFAULT_LABEL_DESCRIPTIONS` sizes, change it to compare only the ids present in the fixture. New `DefaultCategorySeedingTest` (sqlite-jdbc, follow the existing DAO test setup): (a) fresh seed → 21 rows in category "category"; (b) tombstone for `category-socks` → seed does not insert it; (c) a custom tag named "Socks" in category "category" + seed → after `mergeDuplicateTags`, one row remains and its id is `category-socks` (existing built-in-wins rule), garments repointed. This is the accepted behaviour (UX-DEC-010). If (c) contradicts the existing planner rule, stop and report (do not change the planner). Sync: do not queue the new rows; seeding stays local like every default (TS§T).
5. Check the icon map and Manage list need nothing (generic category icon).

## Done when
V-T01-1..3, CI green. Independent review (seeding, tombstones, dedupe, sync of defaults via `isCustomOrModifiedDefault`).

## Do not
Add other categories, re-tag existing garments, change margins/thresholds, regenerate golden fixtures, touch `sync/`.
