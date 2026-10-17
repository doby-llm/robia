# Revision 3 investigation — R16 Browse pinch zoom, R17 colour extraction, R18 Drive usage

Planner investigation on main fc45cec (2026-10-17). Code references are to `app/src/main/java/com/gusanitolabs/robia/`.

## R16 — Pinch to change Browse columns (Google Photos style)
Current code:
- Browse is one `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp))` (`ui/RobiaApp.kt:3435-3437`). Items use stable keys (`items(items, key = { it.id })`, `ui/RobiaApp.kt:3496`).
- Compose BOM 2024.12.01 (`gradle/libs.versions.toml:5`) → foundation 1.7.x: `Modifier.animateItem()` for lazy-grid placement animation and the `awaitEachGesture` / `calculateZoom` pointer APIs are available. The project already uses `awaitEachGesture` (`ui/AddEditClothingScreen.kt:1813, 2019`).

Feasibility: **yes, with no new library.**
- Gesture: a `pointerInput` on the grid's parent observes events on `PointerEventPass.Initial` and reacts only when ≥ 2 pointers are down. It accumulates `calculateZoom()` and consumes the changes only while two fingers are down, so one-finger scrolling and taps are untouched.
- Steps: when the cumulative zoom passes ×1.25 → one column fewer; below ×0.8 → one column more. At most one step per gesture threshold crossing, and the accumulator resets after each step.
- During the pinch, the grid gets a light `graphicsLayer` scale (0.94–1.06) as feedback, which springs back on release.
- Columns: `GridCells.Fixed(n)` with `n` in **2..4** for "Photos only" and **2..3** for "Photos with details" (text at 4 columns would be unreadable). The value is saved in settings (`browse_columns`, invalid → 2). It does not change the U02 default (2).
- Smoothness:
  - Items carry `Modifier.animateItem()`, so they glide to their new positions.
  - The first visible item stays anchored: read `firstVisibleItemIndex` before the change, then `scrollToItem(index)` after it.
  - Thumbnails come from the existing thumbnail cache (`ClothingImageStore.kt:257-310`). The thumbnail edge must cover 2 columns, so going from 4 to 2 columns does not request new decodes. Check the size U02 uses.
- Risks:
  - Jank on large wardrobes while every item animates. Mitigation: animate placement only, no fade.
  - Accidental zoom while scrolling with two fingers. Mitigation: the ×1.25 threshold.
  - Device check: 150 garments on the Poco F3 at 4 ↔ 2 columns, no dropped frames visible.
- Tests: pure `nextColumnCount(current, zoomAccumulated, min, max)` in a JVM test. Settings round-trip test.

## R17 — Colour extraction
Algorithm today:
- `ClothingImageStore.extractPaletteColorDiagnostics` (`media/ClothingImageStore.kt:139-157`) decodes the processed photo and samples a grid with step `maxEdge / 96` (`:382-385`), i.e. ~96 × N samples.
- `GarmentColorAnalyzer.dominantPaletteMatches` (`core/color/GarmentColorAnalyzer.kt`):
  - Pixel weights: alpha < 192 ignored; 192–239 weighted 0.35; ≥ 240 weighted 1.
  - Each sample goes to the nearest palette colour by CIELAB ΔE76 (`PaletteColorClassifier.kt`).
  - Primary colour needs ≥ 28 % of the weight; the secondary needs ≥ 18 % and ΔE ≥ 16 from the primary.
- Low-saturation samples (HSV saturation ≤ 0.12, `core/color/RgbColor.kt:13-21, 41`) may only match "neutral" swatches: black, white, `gray-charcoal`, brown (`PaletteColorClassifier.kt:30-37, 58-68`).

Finding 1 (accuracy, main issue): **there is no grey swatch.**
- The id `gray-charcoal` is displayed as "Beige / Cream" `#D8C3A5` (`core/model/DefaultTags.kt:47`).
- Grey garments, and denim (which photographs almost neutral: median saturation 0.04–0.05, CIELAB b* ≈ −4 on #80, #82, #126), are therefore forced onto brown or beige.
- Offline port of the exact algorithm (`tools/segmentation-bench/color_check.py`) on the 142 v10 cutouts:
  - Today, primary colours: black 95, beige 16, red 13, brown 9, white 4, purple 3, blue 1, none 1.
  - Grey/denim garments come out as brown or beige: #80, 82, 85, 86, 87, 119, 121, 125, 126, 129.
- Adding a **Gray** swatch `#8A8A8A`, treated as neutral, changes 30/142 results. Planner visual review of the 30:
  - About 18 improve: grey shorts and skirts, denim → Gray instead of brown/beige.
  - About 6 get worse: patterned dresses #0, #30; beige knit #53; #123; light-blue denim #85/#86 lose the "Navy / Blue" secondary they had.
  - The rest are equivalent.
- A dedicated "denim" swatch does not help: the denim pixels are too neutral.
- **Option:** add a default colour "Gray" (`gray`, `#8A8A8A`, sort 35), treated as neutral.
  - It is a **user-visible change**: a new colour in filters, PDF and review.
  - Existing garments keep their colours until the colour review re-runs.
  - `gray-charcoal` keeps its id (renaming the id would break sync).
  - Needs Manu's OK because it adds a palette entry.

Finding 2 (speed, small):
- `extractPaletteColorDiagnostics` decodes the **full-size** bitmap (`BitmapFactory::decodeStream` without `inSampleSize`, `:145`), although only a ~96-wide grid is used.
- Decoding with `inSampleSize` so that the long edge is ≤ 384 px:
  - Cuts decode memory roughly 16–28×.
  - Saves tens of ms per photo (it runs in batch, form and colour review).
- Offline, resampling to 192 px changed 14/142 results, mostly the order of two colours on patterned garments. A power-of-two `inSampleSize` at ≤ 384 px stays much closer to the current sampling, but the golden fixtures must confirm it: the colour unit tests pin the results.
- Low value; optional.

Not worth changing: ΔE76 → ΔE2000 (palette swatches are far apart; no misclassification found that depends on the metric), k-means clustering (slower, same palette mapping in the end).

## R18 — Space Robia uses in Google Drive
- Robia stores everything in the hidden `appDataFolder` (scope `drive.appdata`).
- `GoogleDriveWardrobeRepository.listAll` already lists every file in it with `size` (`sync/GoogleDriveWardrobeRepository.kt:683-697`, fields `files(id,name,modifiedTime,size)`).
- Every upload already calls `listAll` (`:352`) and so does every restore (`:1042`).
- **Feasible with no new permission and no extra request:**
  - Sum the sizes of that listing after each successful sync.
  - Store `driveUsageBytes`, `drivePhotoCount` (keys `photos/...`), `driveBackupCount` and the timestamp.
  - Show in Settings › Google Drive: "Robia uses 182 MB in your Google Drive · 143 photos · updated 5 min ago".
  - A refresh action recomputes it with one `listAll`.
- Google documents that appDataFolder content counts against the user's Drive quota (Store application-specific data guide; also seen in the Google One storage manager), so the number is meaningful.
- Device check: the value matches Google Drive › Settings › Manage apps › Robia (hidden app data size) within rounding.
