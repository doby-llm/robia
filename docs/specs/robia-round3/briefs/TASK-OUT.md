# TASK-OUT — Outfit lab (developer)

## Goal
R3-16, answer 7A. Evidence: EmbeddingGemma 2 vectors pick the right item in 63 % of Polyvore FITB questions zero-shot, at trained-model level, with no new model (artifacts/inv-r3-models.md).

## Read first
`artifacts/inv-r3-models.md`; `suggestions/SearchRanker.kt`, `CategoryScorer.cosine`, `EmbeddingRecipe` (query prefix); `core/model/DefaultTags.kt`; `ui/SettingsScreen.kt` developer section.

## Files
new `suggestions/OutfitComposer.kt`; new `ui/OutfitLabScreen.kt`; `ui/SettingsScreen.kt`; `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/OutfitComposerTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `OutfitComposer.compose(seed: String?, prompt: FloatArray?, season: String?, occasion: String?, items, vectors, n = 3)`; slots from category ids (top, bottom, dress/jumpsuit, outerwear, shoes, accessory/bag/jewelry).
2. Lab screen (Developer › Outfit lab): pick seed or type a prompt (embedded with the search prefix), optional season/occasion chips, 3 cards with photos + why-line ("Shares: Summer, Everyday · similar colours"), 👍/👎.
3. Ratings in `filesDir/outfit_lab.jsonl`; "Export ratings" shares it.

## Tests
V-OUT-1, V-OUT-2.

## Done when
V-OUT-1, V-OUT-2, V-OUT-D pass; CI green.

## Do not
Show it outside developer mode or add models.
