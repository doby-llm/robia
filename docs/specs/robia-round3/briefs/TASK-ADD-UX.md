# TASK-ADD-UX — Add screen: auto-scroll to the photo and the batch hint

## Goal
F16 (REQ-FB-16): after picking or taking a photo in Add, the user has to scroll a little so the image and the small buttons below fit; Manu wants a smooth automatic scroll so the image's top edge aligns with the top edge of the screen. F20 (REQ-FB-20, R3-DEC-049): nothing says that several gallery photos can be picked at once (batch mode); the button is a plain "Gallery" and the only hint (`batch_helper_empty`) is inside the batch screen.

## Read first
`R3-DEC-049`; `ux2.md` §F16 and §F20; `ui/AddEditClothingScreen.kt`: `queuePhotoForProcessing` (~298), the screen's `LazyColumn` (~828; photo card is item 1, contentPadding 24 dp, no `LazyListState`), `PhotoCaptureCardWithLaunchers` (~1291, `OpenMultipleDocuments` ~1313, `onBatchPhotosSelected`), the gallery `TextButton` with `R.string.gallery` (~1442), `LocalReducedMotion` (`core/designsystem/Motion.kt`); `ui/BatchFollowState.kt` and `BatchAddClothingScreen.kt` for the pattern BAT3 used to follow with a smooth scroll.

## Files
`ui/AddEditClothingScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/AddPhotoScrollTest.kt` (plus imports and the three `strings.xml`).

## Steps
1. Pure helpers in `AddEditClothingScreen.kt` (`internal`): `photoScrollDelta(itemTopPx, thresholdPx)` returning the scroll distance that puts the photo's top edge at the viewport's top edge (0 when `itemTopPx <= thresholdPx`, never negative) and `batchHintVisible(isEditing, hasPhoto) = !isEditing && !hasPhoto`. The caller computes `itemTopPx` as the item's distance from the viewport's top edge; check in the Compose source or docs whether `LazyListItemInfo.offset` already excludes `beforeContentPadding` and use `layoutInfo.beforeContentPadding` accordingly (the investigation's `offset − padding` is a lead, not a fact); state the convention in the receipt.
2. F16: `val listState = rememberLazyListState()` on the `LazyColumn`; `var scrollRequest by remember { mutableIntStateOf(0) }` (not saveable, so rotation resets it); increment it only in `queuePhotoForProcessing`. `LaunchedEffect(scrollRequest)`: return on 0; `withTimeoutOrNull(~1.5 s)` wait with `snapshotFlow` until the photo item (index 1) is in `visibleItemsInfo`; compute the delta; if > 0, `listState.animateScrollBy(delta, tween(350))` or, under `LocalReducedMotion`, `scrollBy(delta)`. The target is the viewport's top edge (Manu's words), with no extra margin; keep the margin as one named constant (0 dp) so it can change after the device pass. The existing-item effect (~628) must not trigger it. In edit mode the photo is normally already at the target, so the delta is 0 and nothing moves.
3. F20: `gallery_multi` ("Gallery · pick several") replaces `R.string.gallery` on the button only when the card is in Add mode (add a parameter to `PhotoCaptureCardWithLaunchers`/the card; edit mode keeps "Gallery"). The hint line `add_batch_hint` ("Tip: pick several photos at once to add them as a batch.") sits under the photo buttons while `batchHintVisible`. Use `bodySmall` and `onSurfaceVariant`; no new colour literal (the DARK2 scan test is in force).
4. Strings: `gallery_multi` ES "Galería · elige varias", DE "Galerie · mehrere"; `add_batch_hint` ES "Consejo: elige varias fotos a la vez para añadirlas como lote.", DE "Tipp: Wähle mehrere Fotos auf einmal, um sie als Stapel hinzuzufügen." Block `<!-- ADD-UX -->`.

## Tests
V-ADD-UX-1, V-ADD-UX-2 (CI); V-ADD-UX-3 (REV); V-ADD-UX-D (Manu).

## Done when
V-ADD-UX-1..3 pass, V-ADD-UX-D is ready for Manu; CI green.

## Do not
Change the batch screen, the photo pipeline, the picker sheets (PICKER-FIX) or other scroll behaviour; add a persistent one-time tip; scroll when the user is already further down than the target.
