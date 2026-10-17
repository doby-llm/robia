# TASK-C01 — Gray default colour (+ cheaper colour decode)

## Goal
Manu (rev 3, R17, answer "1. Sí"): the palette has no grey. The id `gray-charcoal` is shown as "Beige / Cream" (#D8C3A5), and low-saturation pixels may only become black / white / beige / brown, so grey and denim garments are labelled brown or beige. Add a default **Gray** colour (COL-DEC-001; evidence `artifacts/inv-r3-zoom-colors-storage.md`).

## Read first
`core/model/DefaultTags.kt:44-56` (`mainColors`), `core/color/PaletteColorClassifier.kt:30-37, 58-68` (`NEUTRAL_PALETTE_IDS`), `data/LocalRepositories.kt:203-240` (`deleteMainColor` tombstone, `seedDefaultsIfNeeded` seeds colours only when the table is empty), `data/local/Dao.kt:483` (`seedMainColors` = INSERT IGNORE), `media/ClothingImageStore.kt:139-157, 382-385` (colour decode and sample step); tests `core/model/DefaultTagsTest.kt` (~111-121), `core/color/PaletteColorClassifierTest.kt` (~21-29), `core/color/GarmentColorAnalyzerTest.kt`.

## Steps
1. `DefaultTags.mainColors`: add `MainColor(id = "gray", name = "Gray", hex = "#8A8A8A", sortOrder = 35, isDefault = true)`. Do NOT rename or recolour `gray-charcoal` (its id is synced).
2. `PaletteColorClassifier`: add `"gray"` to `NEUTRAL_PALETTE_IDS`.
3. Existing installs: `seedDefaultsIfNeeded` only seeds colours into an empty table, so add in the same function: if no colour with id `gray` exists AND there is no `main_color` tombstone for `gray`, insert it with `seedMainColors(listOf(gray.toEntity()))`. A user who deleted Gray never gets it back. Existing garments keep their stored colours; they change only when the user runs the colour review.
4. Strings: the colour names come from `MainColor.name`, so no string resource is needed. Check `DisplayColorLabel` / `color_gray` (currently "Beige" in strings.xml): leave it unchanged and note it in the receipt.
5. Optional speed fix (do it only if steps 1-4 are green): in `extractPaletteColorDiagnostics`, decode with `BitmapFactory.Options().inSampleSize` = largest power of two that keeps the long edge ≥ 512 px (decode bounds first). Keep the 96-wide sample grid. If any existing colour test changes its expected result, revert this step and say so in the receipt (offline check on 142 photos: 138/142 identical, 140/142 same primary).
6. Tests: update `DefaultTagsTest` counts (11 → 12 defaults) and add `grayIsNeutralDefault`; `PaletteColorClassifierTest`: a mid grey `#808080` now maps to `gray`, beige `#D8C3A5` still maps to `gray-charcoal`; new `GraySeedTest` (sqlite-jdbc like the other repository tests, or a pure helper `shouldSeedGray(existingIds, tombstoneIds)` if the repository is not testable on JVM).

## Done when
V-C01-1..3, CI green; device row V-Z2-24.

## Do not
Change ΔE metric, thresholds (28 % / 18 % / ΔE 16), existing colour ids or hex values, or re-run colour detection on existing garments.
