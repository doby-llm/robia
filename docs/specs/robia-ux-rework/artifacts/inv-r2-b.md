# Robia UX investigation: R5, R6, R11, R14

Read-only pass over the source tree. Paths are relative to `app/src/main/java/com/gusanitolabs/robia/` unless noted. Line numbers are from the current files. Not verified: anything that needs a device run.

## R5: Add-item form layout

**PREMISE DOES NOT MATCH (two columns).** In `AddEditClothingScreen` nothing renders categories or occasions in a two-column grid. The only side-by-side pair is the primary/secondary colour circles (`AddEditClothingScreen.kt:846-860`, `Row` with `weight(1f)` each).

**CERTAIN: form order, top to bottom** (`AddEditClothingScreen.kt:768-957`, one `LazyColumn`, 24 dp content padding, 16 dp spacing):
1. Title and body text, `headlineLarge` (776-787).
2. `PhotoCaptureCard` (1520-1623): `Card` with `surfaceContainerLowest`, `PhotoPreview` at 4:3 or the photo's aspect ratio, `clip(extraLarge)`. Below it is a row with Gallery (`TextButton`) and a camera `Button` (1552-1580). A status line and a retry button appear only on fallback (1581-1618).
3. Helper text, `bodySmall` (823-829).
4. Notes: `CardSection` with an `OutlinedTextField`, `minLines = 3` (832-842).
5. "Extracted colours": `CardSection` with two `PaletteColorCircle`s, 72 dp circles, labelled with the colour name or "None" (845-861, 2148-2181).
6. `MetadataCaptureSection` (992-1049), inside a `CardSection`. Each block is a `MetadataShellCard` (1302-1345: `surfaceContainer`, elevation 0, 14 dp padding, 20 dp icon, uppercase `labelMedium` title, subtitle):
   - Category: full-width `OutlinedButton` showing the one selected label. Tapping opens `SingleSelectMetadataDialog` (1007-1015, 1052-1122, 1125-1173). Selected state uses `secondaryContainer` plus a check icon.
   - Season: `FlowRow` of `FilterChip`s (1016-1024, 1259-1299).
   - Fit: one `FilterChip`, label `fit_good` (1025-1028, 1219-1257).
   - Location: same single-select button as Category (1029-1037).
   - Occasions: `FlowRow` of `FilterChip`s (1038-1046).
7. Action row (874-943): Delete as a 64 dp `OutlinedButton` when editing, otherwise a `TextButton` Cancel (weight 1). Save is a `Button` (weight 2) with a Save icon, enabled only when `canSaveItem` (640).
8. Developer diagnostics only when developer mode is on (945-956).

**CERTAIN: default option counts** (`core/model/DefaultTags.kt`):
- Category: 19 tags (lines 12-30). Jewelry and Accessories have `suggestOccasions = false` (28-29).
- Season: 4 (31-34).
- Occasions: 6 (35-40).
- Location: 1, "Main closet" (41).
- Fit: 1 chip.
- Colours: 11 (44-56).

Category (19) and Location (1) go into dialogs. Season (4) and Occasions (6) are visible chip clusters, which is the likely source of the uneven rows.

**CERTAIN: visual inconsistencies.** The outer `CardSection` uses `surfaceContainerLowest` with 20 dp padding and 1 dp elevation (2123-2145). The inner `MetadataShellCard`s use `surfaceContainer`, 0 dp elevation and 14 dp padding (1309-1316). That is a card-in-card with two background tones and two paddings.

**CERTAIN: AI hints.** Suggestions run silently during photo processing (`AddEditClothingScreen.kt:455-463`, `556-564`) and merge into `selectedTagIds` via `applyPredictedTags` (237-248). The chips have no "suggested" marker. Model-owned tags (`modelOwnedTagIds`, line 177) are tracked only in logic, and the `FilterChip` at 1281-1294 does not show them.

**Batch screen** (`ui/BatchAddClothingScreen.kt:112-258`): `LazyVerticalGrid` with `GridCells.Adaptive(160.dp)`, which gives two columns on a typical phone. Tiles are 3:4 (`BatchDraftTile` 321-386). Save is a full-width `Button` in a bottom `Surface` (218-256).

**HYPOTHESIS:** the "dos columnas" complaint is about the Batch grid or the detail-screen 2×2 metadata grid (`RobiaApp.kt:4503`, `DetailMetadataGrid`), not the Add form.

**Fix touch points:** `AddEditClothingScreen.kt` 992-1049 (`MetadataCaptureSection`), 1302-1345 (`MetadataShellCard`), 1259-1299 (`MetadataTagCard`), 2148-2181 (`PaletteColorCircle`). Also the shared `CardSection` at 2123.

## R6: Processing overlay

**CERTAIN: the overlay today.**
- Add form: `PhotoPreview` (`AddEditClothingScreen.kt:1626-1702`). When `isProcessing`, it draws a `scrimColor` Box over the photo (1679-1702), then a `CircularProgressIndicator` and the stage label (`bodyMedium`, SemiBold).
- Batch tile: `BatchPhotoPreview` (`BatchAddClothingScreen.kt:389-436`, overlay at 425-434). It shows only a spinner, no label. A queued tile is also dimmed with `alpha(0.62f)` (344).
- Colours: `rememberProcessingOverlayColors` (`ui/ProcessingOverlayContrast.kt:17-43`). It samples central luminance off the main thread (`ClothingImageStore.estimateCentralLuminance`, line 185). Dark image → white content, black scrim at alpha 0.32. Light image → black content, white scrim at alpha 0.40 (constants 50-52). This is the "less visible photo" effect. There is no motion.

**CERTAIN: states.**
- Stages (`PhotoProcessingStage`, `AddEditClothingScreen.kt:2337-2343`): RemovingBackground, CroppingPicture, ExtractingColor, PreparingSuggestions, ApplyingQuickEdit. Each has a string under `processing_stage_*` in `values/strings.xml:255-259`.
- Editor background status (`media/EditorPhotoState.kt:34-42`): None, Queued, RemovingBackground, ForegroundReady, CroppedForegroundReady, OriginalFallback, QuickEdited.
- Batch status (`BatchAddClothingScreen.kt:102-109`): Queued, Processing, NeedsReview, Ready, Failed, Interrupted. There are no per-stage labels in batch.

**CERTAIN: timings.** No timing constants exist. The pipeline only records `elapsed()` diagnostics per step (`AddEditClothingScreen.kt:343-486`). Whether ML Kit segmentation or suggestion embedding dominates is unmeasured.

**CERTAIN: what is visible when.**
- Add form, start: `canonicalPhotoUri` = `currentEditedUri`, which is the raw source (`:287-288`, `:326-327`). The raw photo sits under the scrim while segmentation runs.
- After segmentation returns (`:369-371`), `currentEditedUri` becomes the ML Kit foreground PNG (`result.outputUri`). That PNG has transparency. It is still shown under the scrim during the crop, colour and suggestion stages, because `canonicalEditorUri` reads `currentEditedUri` first (`EditorPhotoState.kt:21-22`).
- Batch: the draft keeps `originalPhotoUri` (`BatchAddClothingScreen.kt:86`). `photoUri` changes only in the final `onDraftUpdated` at 534. The foreground is never stored in a draft mid-processing. `BatchDraftItem` (83-100) has no foreground field.

**CERTAIN: mask availability.** `MlKitSubjectSegmentationBackgroundRemovalEngine.kt:35-38` builds the segmenter with `enableForegroundBitmap()` only. The result is an alpha foreground bitmap, written as a PNG (`:55-68`). No confidence mask is exposed. The silhouette is the PNG's alpha, available from the moment segmentation returns, before cropping. Nothing exposes it as a separate mask.

**HYPOTHESIS (shimmer clipped to the garment):** feasible in Compose with `graphicsLayer(compositingStrategy = Offscreen)` and a moving gradient drawn with `BlendMode.SrcIn` over the foreground PNG. Keep the scrim and spinner behind it. Not tested on device.

**Fix touch points:**
- Add form: `PhotoPreview` 1626-1702, and `processSelectedPhoto` 319-492 only if the new effect needs the foreground earlier.
- Batch: `BatchPhotoPreview` 389-436, `processBatchDraft` 483-~570. Persist the foreground by adding a field to `BatchDraftItem` (83-100) and setting it right after line 504.
- Shared: `ProcessingOverlayContrast.kt`, used by both screens and `ReviewScaffold`'s callers.

**Coupling:** the `BatchProcessingCoordinator` (`ui/BatchProcessingCoordinator.kt`, process-level job) is unaffected. The batch foreground field touches the draft model, so it also touches R5/R14 only indirectly.

## R11: Garment PDF export

**CERTAIN: pipeline.** `media/GarmentShareExporter.kt`:
- Uses `android.graphics.pdf.PdfDocument` (line 57), one page, fully offline.
- Page width 720 pt (26). Height from `calculatePdfPageHeight` (392-418), minimum 1280 (27).
- Writes to `cacheDir/robia_shares` (494-497). Shared through `FileProvider` authority `<package>.fileprovider` (499-503) and `launchShareChooser` (`RobiaApp.kt:5062`) with MIME `application/pdf`.
- Single garment only. `createSharePdf` takes one item (49-69). The only caller is `ItemDetailScreen` (`RobiaApp.kt:4127`, `4191`). The PNG export (`createShareImage`, 33-47) is a raw copy of the stored photo.

**CERTAIN: elements, in draw order** (`drawGarmentCard`, 82-131). Content width is 632 pt (margins 44 pt, `contentRight` 676).
1. Background fill `rgb(250,249,247)` (89).
2. Photo frame at y=44: height is the content width times the photo's aspect, clamped to 657-784 pt (420-423). Photo is `drawBitmapFitContain` with no corner radius (103-104, 133-145). Paint has `ANTI_ALIAS_FLAG` only, no `FILTER_BITMAP_FLAG` (143).
3. Gradient overlay tinted with the primary colour, alpha 46 at the bottom and white at 92 (147-179). Controlled by `ENABLE_PDF_IMAGE_GRADIENT_OVERLAY = true` (25, 105-107).
4. Name: 48 pt bold, up to 3 lines, line height 1.05, at image bottom plus 36 (109-111).
5. Notes: 20 pt, up to 5 lines, line height 1.42 (113-115).
6. Divider `rgb(227,226,224)`, 1.5 pt (117).
7. Colour section label: 13 pt bold, letter spacing 0.08, at +42 (119).
8. Colour chips: up to 306 pt wide, 104 pt tall, radius 14, 20 pt gap (121, 181-233). Swatch circle radius 29 at left+48. Role label 13 pt at +34, colour name 24 pt at +72. A "none" colour gets an X (225-228).
9. Metadata grid (`drawMetadataGrid`, 235-291): up to 4 cells (Category, Season, Occasion, Fit). Two columns, gap 10, cell width 311, minimum height 154, radius 12, 34 pt icon. Label 15 pt, values 25 pt, wrapping (125, 255-275). Location is not included (`RobiaApp.kt:5112-5137`).
10. Footer (371-390): 136 pt tall, `rgb(244,243,241)`. Robia logo 42 pt centred, two ✦ glyphs at ±38 pt at 20 pt, "Created with Robia" 13 pt bold with letter spacing 0.12.

**CERTAIN: fonts.** Only `Typeface.DEFAULT` and `DEFAULT_BOLD` (477), which is the system sans. There is no `res/font` directory. `assets/` holds only `robia_logo.png` and a `benchmark/` folder. The notes at `docs/ux/pdf_render/garment_pdf_rendering_notes.md` describe an "Inter-like hierarchy", which the code does not implement.

**CERTAIN: layout issue.** The footer is pinned to the bottom of a page of at least 1280 pt (129). Short content leaves a large empty band between the metadata and the footer.

**HYPOTHESIS (why it looks "ugly"):**
- The photo is downscaled without bilinear filtering, so it looks soft or jagged.
- The primary-colour tint muddies the garment.
- The ✦ glyph may fall back to a different font in Roboto.
- The large empty band.

**Fix touch points:** `GarmentShareExporter.kt` 82-131, 147-179, 371-390, 392-418, and `Paint` construction in 133-145. Data mapping is `RobiaApp.kt:5112-5151`. `RegressionSourceContractTest.kt:906` reads `toGarmentShareItem` as source text, so renaming or restructuring it may break that test.

## R14: "Magia" on AI and review actions

**PREMISE MOSTLY MATCHES, with one exception.** There is no haptic feedback anywhere: no `HapticFeedback`, `performHapticFeedback` or `LocalHapticFeedback` in the app. There is no custom `indication` or `ripple`, so every control uses the default Material ripple. There are no `Modifier.animate*`, `graphicsLayer` motion or press effects.

The exception is the review cards. `ReviewAcceptedCard` (`ui/ReviewScaffold.kt:107-130`) does animate: the resolved card stays 200 ms (`:64`), then fades and shrinks over 250 ms (`:67`, 123-127) before `onGone`. It is used by `ColorReviewScreen.kt:289` and `TagReviewScreen.kt:519`.

**CERTAIN: AI-related entry points.**
- Browse smart-search `IconButton` with `Icons.Outlined.AutoAwesome`, shown when search is hidden (`RobiaApp.kt:3634-3641`). It opens `ModelPromptDialog` (3687-3699).
- Browse search field: pill-shaped `OutlinedTextField`, leading AutoAwesome icon (3703-3722). It sits in `AnimatedVisibility(expandVertically + fadeIn)` (3611-3620) inside `BrowseWardrobeScreen` (3392-3516).
- Similar garments: `FilledTonalButton` "item_see_similar" (4211-4215) and `SimilarGarmentsChip` (3726-3737).
- Progress banner `SuggestionsProgressBanner` (3464).
- Colour review prompt: `Button(onReview)` at `ColorReviewScreen.kt:102` in `ColorReviewPromptDialog` (92-113).
- Tag review prompt: `Button(onReview, enabled = modelReady)` at `TagReviewScreen.kt:291` in `TagReviewPromptDialog` (280-306).
- Automatic photo suggestions and colour detection have no button. They run inside `processSelectedPhoto` (`AddEditClothingScreen.kt:429-463`).
- Model settings: `SmartSuggestionsSettingsSection` (`ModelDownloadUi.kt:106-160`).

**CERTAIN: accept and discard.**
- Tag review (`TagReviewScreen.kt:699-730`): accept `Button` shows a check icon and "Accepted" label after acceptance. Discard is an `OutlinedButton`. Both are 48 dp high, `weight(1f)`, with `ReviewCardActionPadding` of 8 dp horizontal (`ReviewScaffold.kt:101`).
- Colour review (`ColorReviewScreen.kt:384-414`): same pattern, but only the label changes. No icon.
- Bottom bar: `ReviewBottomBar` (`ReviewScaffold.kt:265-295`), a 56 dp `Button` with no animation.

**CERTAIN: existing animations.**
- Progress bars use `animateFloatAsState` with `tween` at 300 ms (`BatchAddClothingScreen.kt:298-301`, `TagReviewScreen.kt:427-429`, `ColorReviewScreen.kt:241-243`) and 450 ms (`RobiaApp.kt:2824-2826`).
- `ReviewProgressHeader` (`ReviewScaffold.kt:179-211`) uses a non-animated `LinearProgressIndicator`.
- A pulsing cloud icon, `PulsingCloudIcon` (`RobiaApp.kt:2419-2438`), is the only infinite animation.

**Coupling:** `ReviewScaffold.kt:61` says the look must stay identical across the colour and tag review screens. Any new accept/discard motion must change both at once. `ReviewAcceptedCard` is shared.

**Fix touch points:** `ReviewScaffold.kt` 107-130 (`ReviewAcceptedCard`), 265-295 (`ReviewBottomBar`), 297-319 (dialog). `TagReviewScreen.kt` 615-740. `ColorReviewScreen.kt` 348-420. Entry points at `RobiaApp.kt` 3634-3641 and 3703-3722.

## Risks

- **R5 and R14** both touch the card styling. Keep the `ReviewScaffold` change separate from the form restyle.
- **R6 batch** needs a `BatchDraftItem` field, which changes the draft data model.
- **R11** tests read source text. Check `RegressionSourceContractTest.kt` before renaming anything in `GarmentShareExporter` or `toGarmentShareItem`.
- **Hot file:** `RobiaApp.kt` is 5569 lines. The relevant regions are `RobiaShell` 859-2013 (review dialogs at 1583-1660, batch hookups at 1196 and 1922), `BrowseWardrobeScreen` 3392-3516, `FilterBar` 3576-3701, `BrowseSearchField` 3703-3723, `ItemDetailScreen` 4104-4335, `DetailMediaCard` 4337-4400, `DetailMetadataGrid` 4503-4553, `toGarmentShareItem` 5112-5151.
