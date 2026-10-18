# TASK-U06 — Garment form and detail as list rows

## Goal
The add form shows cards inside cards with two tones, and the detail screen shows 5 metadata boxes in 2 columns (one cell empty) (R5, UX-DEC-005). Replace both with one list of equal rows and bottom-sheet pickers (mockup `garment-form.html`). The save path must not change.

## Read first
TS§6; `ui/AddEditClothingScreen.kt`: `MetadataCaptureSection` (~992-1049), `MetadataSelectorCard`, `SingleSelectMetadataDialog` (~1052-1173), `FitChoiceCard` (~1219), `MetadataTagCard` (~1259-1299), `MetadataShellCard` (~1302-1345), `PaletteColorCircle` (~2148), `CardSection` (~2123), `modelOwnedTagIds` (~177), `applyPredictedTags` (~237), save `onClick` (~905-940), `PhotoCaptureCard` (~1520-1623); `ui/ItemDetailScreen.kt` `DetailMetadataGrid`, `DetailMetadataCard`; `toggleTag` helper used by the chips (single-select for category/location).

## Steps
1. New `ui/GarmentFieldRows.kt`: `GarmentFieldRow`, `TagPickerSheet` (TS§6). Pure helpers: `fieldValueText(selected: List<GarmentTag>, collator, none: String): String` (alphabetical, ", "), `isAiSuggested(selectedIds, modelOwnedIds): Boolean` (non-empty and all in model-owned).
2. Add form: replace the metadata section and the colours `CardSection` with ONE `Card` of rows: Category (single, sheet radio, "None" first), Occasions (multi, sheet checkboxes), Season (multi), Colours (opens the existing colour picker flow used by `PaletteColorCircle` clicks; value = primary · secondary names with 14 dp swatches), Location (single), Fit (row with a `Switch` bound to the existing fit value: on = `fit_good`; keep the same stored values). Notes `OutlinedTextField` stays, in its own card below.
   Selection changes call the SAME state updates the chips did (`onSelectedTagIdsChange(selectedTagIds.toggleTag(...))` with the same single/multi flags).
3. `PhotoCaptureCard`: next to Retake/Gallery add a `FilledTonalButton("Edit photo")` (✦ icon, styled with `Modifier.aiSurface()` from U01) that calls the existing `onQuickEditClick` (same enable rule as the pencil inside `PhotoPreview`, `AddEditClothingScreen.kt` ~1662, which stays). Until SG3 it still opens today's quick-edit dialog; SG3 points it to the new editor. Retake/Gallery unchanged.
4. Detail: replace `DetailMetadataGrid` with a Card of `GarmentFieldRow(onClick = null)` rows (Category, Occasions, Season, Location, Fit). Title of the screen uses `cardTitle` from `ui/BrowseCardText.kt` (U02).
5. Delete now-unused composables (`MetadataShellCard`, `MetadataTagCard`, `MetadataSelectorCard`, `SingleSelectMetadataDialog`, `FitChoiceCard`, `DetailMetadataGrid`, `DetailMetadataCard`) after grepping that nothing else uses them.
6. Strings: `field_category` etc. — reuse `metadata_category`, `metadata_season`, `metadata_occasions`, `metadata_location`, `metadata_fit` and existing "None"; new: `field_suggested_hint` "✦ Suggested by Robia. Tap to change.", `edit_photo_action` "Edit photo" (ES "Editar foto", DE "Foto bearbeiten").

## Tests
`GarmentFieldRowsTest` (V-U06-1): value text sorting, empty → None, `isAiSuggested` cases.

## Done when
V-U06-1..4; reviewer confirms V-U06-2 (save block identical) and V-U06-3 (grep empty).

## Do not
Change `processSelectedPhoto`, suggestion logic, the save call, batch logic, or add a name field.
