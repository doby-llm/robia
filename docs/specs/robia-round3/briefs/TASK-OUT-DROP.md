# TASK-OUT-DROP — Remove the Outfit lab

## Goal
F13 (REQ-FB-13, R3-DEC-042): Manu dropped the Outfit lab (the embeddings only pick similar colours). Delete it completely from the app and keep everything the lab merely read. Independent review: yes, because this is a deletion.

## Read first
`R3-DEC-042`; `TASK-OUT.md` (what was built); `ui/OutfitLabScreen.kt`; `suggestions/OutfitComposer.kt`; the Developer section of `ui/SettingsScreen.kt` (~224-260); `ui/RobiaApp.kt` (`RobiaRoute.OutfitLab` ~334, bottom-bar exclusion ~1935, `pushRoute` ~2100, route branch ~2970, parameter plumbing ~2861, ~3109, ~3135).

## What the lab owns (delete) and what is shared (keep)
Owned by the lab, found by `git grep -n -i "outfit" -- app/src` at `ec4f419`:
- `ui/OutfitLabScreen.kt` and `suggestions/OutfitComposer.kt` (with `Outfit`, `OutfitSlot`, `OUTFIT_LAB_RATINGS_FILE_NAME`, `outfitRatingLine`), and `suggestions/OutfitComposerTest.kt`.
- In `ui/SettingsScreen.kt`: the `onOutfitLabClick` parameter and the Developer row (headline `outfit_lab_title`).
- In `ui/RobiaApp.kt`: `RobiaRoute.OutfitLab`, its entry in the bottom-bar exclusion list, the `RobiaRoute.OutfitLab ->` branch of the route `when`, the `onOutfitLabClick` parameter of the shell functions and the lambda that pushes the route.
- Strings: the 19 `outfit_lab_*` keys, each in `values`, `values-es` and `values-de` (lines ~846-864; EN keys: title, summary_settings, summary, seed_title, prompt_label, prompt_hint, season_title, occasion_title, generate, prompt_failed, empty, why_shares, why_similar_colours, why_none, thumbs_up, thumbs_down, ratings_count, export, export_chooser).
- The ratings: no Room entity, DAO, ViewModel or repository. The only storage is `filesDir/outfit_lab.jsonl` and the share intent in the screen (text via `Intent.ACTION_SEND`; no `FileProvider` path entry).

Shared, must stay (each verified in use elsewhere): `data/GarmentVectors` and `EmbeddingRepository` (duplicate scan, search, `SuggestionService`), `RobiaApplication.embeddingRepository`, `EmbeddingRecipe` including `QUERY_PREFIX` (used by search in `RobiaApp.kt` ~1007), `EmbeddingEngineHolder.embedText` (search), `SearchRanker`, `CategoryScorer.cosine`, `core/model/DefaultTags`, the `org.json` usage elsewhere, `golden-scores.json` (embedding labels; the word "outfit" in it is label text), the strings `wardrobe_intro` and `sample_sneakers_notes`.

Database: leave it alone. No table or column exists for the lab, so there is nothing to drop and no migration. Do not change `RobiaDatabase`, DAOs or `app/schemas/`.

On-device leftover: `outfit_lab.jsonl` stays on devices that used the lab (ids and ratings, a few kB, local only, not in the Drive snapshot or the zip, which do not enumerate `filesDir`). Do not add cleanup code.

## Files
Delete `ui/OutfitLabScreen.kt`, `suggestions/OutfitComposer.kt`, `app/src/test/java/com/gusanitolabs/robia/suggestions/OutfitComposerTest.kt`; edit `ui/SettingsScreen.kt`, `ui/RobiaApp.kt`, the three `strings.xml` (plus imports that became unused).

## Steps
1. Remove the route, its branch, its bottom-bar exclusion and the click plumbing in `RobiaApp.kt`; remove the Developer row and the parameter in `SettingsScreen.kt`. Keep the dividers consistent (no double divider, no trailing divider).
2. Delete the three Kotlin files.
3. Remove the 19 `outfit_lab_*` strings from the three locales. No block header comment is needed for a removal.
4. Remove imports that became unused in the two edited files; do not touch anything else in them.
5. Run `git grep -n -E "OutfitLab|OutfitComposer|OUTFIT_LAB|outfit_lab|onOutfitLabClick" -- app/src` and paste the empty result in the receipt.

## Tests
V-OUT-DROP-1, V-OUT-DROP-2 (existing tests stay green unchanged, notably `RegressionSourceContractTest`), V-OUT-DROP-3 (independent review), V-OUT-DROP-D for Manu.

## Done when
V-OUT-DROP-1 to V-OUT-DROP-3 pass, V-OUT-DROP-D is ready for Manu; CI green.

## Do not
Touch the database, schema, DAOs or sync; remove any shared embedding, search or duplicate code; add cleanup of the old ratings file; reword other Developer rows.
