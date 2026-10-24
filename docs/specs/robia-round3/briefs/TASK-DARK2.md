# TASK-DARK2 — Dark mode: Browse, detail, add/edit, batch, tags

## Goal
F14 (REQ-FB-14): the Browse side of the app looks right in Dark. Most of these screens already read `MaterialTheme.colorScheme`; the work is the few literals, the backdrops behind garment cutouts, and an audit for light-only assumptions. Photo wells stay light (R3-DEC-045) so a black garment never vanishes.

## Read first
`R3-DEC-045`; `TASK-DARK1.md` (tokens, `LocalRobiaExtras`, the marker rule, `ThemeSourceScan`); then the Files below. Known spots at `ec4f419`: `ui/BrowseGrid.kt` ~748 `Color.Black.copy(alpha = .12f)` (swatch circle border) and `GarmentPhotoPlaceholder` ~778; `ui/PhotoProcessingVisuals.kt` `GlowColor`, `ShimmerPeach`, `Color.White` sweeps, and the swatch border ~223; `ui/BatchAddClothingScreen.kt` ~457 row backdrop (`surfaceContainerLow` to `primary` at 16 %); `ui/AddEditClothingScreen.kt` ~1559 photo box (`surfaceContainerLow`); `ui/ManageTagsScreen.kt` ~173 `Color.Transparent` (fine), ~728 and ~1052 parse of user colours (data, fine).

## Files
`ui/BrowseGrid.kt`; `ui/BrowseFilterBar.kt`; `ui/AdvancedFiltersScreen.kt`; `ui/ItemDetailScreen.kt`; `ui/AddEditClothingScreen.kt`; `ui/BatchAddClothingScreen.kt`; `ui/GarmentFieldRows.kt`; `ui/ManageTagsScreen.kt`; `ui/PhotoProcessingVisuals.kt`; `ui/SuggestionsProgressBanner.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeBrowseScanTest.kt`.

## Steps
1. For every `Color(0x…)`, `Color.White`, `Color.Black` in the Files: use a scheme role (the swatch borders become `onSurface` at about 24 % alpha, visible on both a light tile and a dark page) or, when the colour must not follow the theme, add `// theme-fixed: <reason>` (shimmer and glow that sit on photos; user/garment colour data).
2. Photo backdrops: the batch row backdrop gradient uses `extras.photoWellTop` to `extras.photoWellBottom`; the add/edit photo box uses `extras.photoWellTop`. In Light the values are identical to today's (`#F3F3F4`; the gradient end `#D1CFCF` is what the 16 % blend gave).
3. Audit every `containerColor =`, `.background(`, `.copy(alpha`, `Brush` in the Files for a role used as if it were light (for example `primary` as a fill with a hard-coded white label, a `surfaceContainerLow` card sitting behind a photo, a text colour taken from a swatch). Fix what you find with the nearest role; list each finding and fix in the receipt.
4. `DarkThemeBrowseScanTest`: `ThemeSourceScan` over the Files list (V-DARK2-1).

## Tests
V-DARK2-1 (CI), V-DARK2-2 (REV), V-DARK2-D (Manu).

## Done when
V-DARK2-1, V-DARK2-2 pass, V-DARK2-D is ready for Manu; CI green.

## Do not
Change layout, spacing or copy; touch `GarmentSwatchBackground.kt`, the swatch palette or the near-white rule; touch colour/tag review screens (REV); make light-theme output differ.
