# TASK-REV-CARD — Tag review cards with signed chips (still one commit at the end)

## Goal
F12 + F21 (R3-DEC-052 to R3-DEC-054): the tag review stops showing "From → To" boxes and a picker. Each garment is a card with its changes as signed chips (green filled "+", red filled "−" with strike-through, hollow sign = left out), explicit Reject and a counted Apply, a pinned "N left" counter, and the list shrinks as garments are decided. This slice is the look and the per-tag toggles; persistence is unchanged (one commit at the end) so the writes are reviewed apart in REV-SAVE.

## Read first
`R3-DEC-052` to `R3-DEC-054`; the mockup `mockups/review-redesign-3/index.html` and `signed-chips.html` (and `build.py`: `CSS3` for sizes, `chip`, `swap_chip`, `card_signed`, `footer`, `identity`, `rhead`; the string table `S`); `mockups/review-redesign-2/pick-then-decide.html` (card flow); merged `suggestions/ReviewChanges.kt` (REV-MODEL), `ui/TagReviewSession.kt` (REV-SESSION), the tokens of REV-TOKENS (`MaterialTheme.extras.addColors/removeColors`); `ui/TagReviewScreen.kt` (`TagReviewScreen` ~340, `commitUpdatedItems` ~408, the grid ~473, `TagReviewHeader` ~584, `TagReviewCandidateCard` ~619, `TagReviewPickerDialog` ~768); `ui/ReviewScaffold.kt` (`ReviewAcceptedCard`, `ReviewProgressHeader`, `ReviewBottomBar`: all stay); `DisplayColorLabel.stringRes` (how `AddEditClothingScreen` names a colour); `ui/ManageTagsScreen.kt` `sortedAlphabetically`.

## Files
`ui/TagReviewScreen.kt` (the screen body, the candidate card and the picker use; `offerTagReviewRequest`, the change-set functions, `tagReviewUndoPlan`, `TagReviewPromptDialog`, `shouldAutoCommit` and the commit-candidate functions stay); new `ui/ReviewChangeChips.kt`; new `ui/TagReviewCard.kt`; new `ui/TagReviewCardModel.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCardModelTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeReviewScanTest.kt`.

## Steps
1. **Pure model** (`TagReviewCardModel.kt`): `chipVisual(change, skipped)` → a small value (kind add/remove/swap, filled or hollow sign, struck text, solid or dashed outline, switch or static) per the table below; `chipDescriptionKey(...)` choosing the `review_cd_*` string; `identityLine(categoryLabel, colourLabel)` ("Category · Colour", no garment name, no separator when the colour is unknown, the category line falls back to `tag_review_none` "No category"); `nowLine(groupLabelsBefore)` (occasion group only; empty → `review_none`); `reviewHeaderState(isScanning, isPreparing, leftCount, decided, found)` (scan header while scanning, else "N left" and progress = decided / found, 0 when nothing is found, never above 1). Everything the V-REV-CARD rows test lives here.
2. **Chips** (`ReviewChangeChips.kt`): `ReviewChangeChip` (a `toggleable` with `Role.Switch`, state description and action text from the `review_cd_*` strings; 36 dp chip inside a 48 dp row; chip padding 0 12 0 6 dp, corner 10 dp, border 1 dp, text `labelLarge` 14 sp medium, ellipsized at the card width; the sign circle 22 dp, never shrinks, the glyph from `Icons.Rounded.Add` / `Icons.Rounded.Remove` at 14 dp) and `ReviewSwapChip` (a 4 dp padded frame, corner 14 dp, 1 dp border, "−" chip, an arrow glyph, "+" chip; the frame is ONE switch; with a locked "−" half the frame is static and only the "+" chip is the switch). State table (never colour alone):

   | State | Sign | Fill and text | Outline |
   |---|---|---|---|
   | add on | filled circle in `addColors.sign`, "+" in `onSign` | `container`, text `onContainer` | solid, `border` |
   | add left out | hollow circle (1.5 dp ring `outline`), "+" in `onSurfaceVariant` | transparent, text `onSurfaceVariant` | dashed, `outline` |
   | remove on | filled circle `removeColors.sign`, "−" in `onSign` | `container`, text `onContainer`, struck 1.5 dp | solid, `border` |
   | remove kept | hollow circle, "−" in `onSurface` | transparent, text `onSurface`, no strike | solid, `outline` |
   | locked removal | as "remove on" | as "remove on" | not a switch: no ripple, no switch semantics, description `review_cd_removed` |
   | swap on / skipped | both halves "on" / old half plain "kept", new half ghost | | frame solid / dashed |

   Colours from `MaterialTheme.extras` and the scheme only; a 150 ms cross-fade of fill and sign when toggled (none under `LocalReducedMotion`), a light haptic tick, no movement, chips never re-sort. Add a `// theme-fixed:` marker with a reason to any literal you cannot avoid (there should be none).
3. **Card** (`TagReviewCard.kt`): container `surfaceContainerLowest`, 1 dp `outlineVariant`, corner 16 dp, padding 12 dp; head: the garment thumbnail (the existing `TagReviewPhoto`, 52×60 dp, corner 12 dp), the identity line (14 sp semibold) and, for occasions, the "Now: …" line (12.5 sp, `onSurfaceVariant`); then the chips in a `FlowRow` (8 dp gaps, each in a 48 dp row) in the order REV-MODEL gives (swap, removals, additions); then the quiet lines: `review_none_left_occ` with an outlined "!" mark when `noTagsLeft`, `review_all_skipped` when every unit is left out; then the footer: when `showsReject` an outlined pill Reject (`review_reject`) and a tonal pill primary (`review_apply_n` plural / `review_apply_n_of_m`), the primary 1.45× as wide, both ≥ 48 dp; otherwise one tonal pill `review_keep_as_is` or `review_got_it`. A `needsChoice` card shows the locked chip and a tonal "Choose" button (`tag_review_choose`, the line `tag_review_choose_manually`) that opens the existing single-choice `TagReviewPickerDialog`; the chosen tag becomes a "+" chip. Suggested cards no longer open a picker.
4. **Screen**: derive each candidate's `ReviewChanges` with `reviewChangesFor` (group ids = today's `kindIds`, existing tag ids = `changeSet.afterTags`, labels = `labelsById`); keep per-card chip state in a `TagReviewSession`. Decisions still go into today's id lists: Apply and Keep as is and Got it → `acceptedIds`, Reject → `discardedIds`, so the exit animation, `hiddenIds`, the bottom bar ("Accept all (N)", "Save N accepted", "Done"), `shouldAutoCommit` and Back/Discard behave as they do now. `commitUpdatedItems` writes each accepted garment as its snapshot copy with the tags `applyDelta` gives (delta from the chips; `existingTags` = `changeSet.afterTags`; a card with an empty delta is not written). Pinned header above the grid (not scrolling): `reviewHeaderState`: while scanning the existing scan `ReviewProgressHeader`; after the scan "N left" (plural `review_left`) and the review progress bar; below it the existing `TagReviewHeader` texts and the hint line `review_hint_changes`.
5. **Strings**, block `<!-- REV-CARD -->` in all three files (EN | ES | DE); plurals have `one` and `other`:
   - `review_reject` Reject | Rechazar | Ablehnen
   - `review_apply_n` one "Apply 1 change" | "Aplicar 1 cambio" | "1 Änderung übernehmen"; other "Apply %1$d changes" | "Aplicar %1$d cambios" | "%1$d Änderungen übernehmen"
   - `review_apply_n_of_m` "Apply %1$d of %2$d" | "Aplicar %1$d de %2$d" | "%1$d von %2$d übernehmen"
   - `review_keep_as_is` "Keep as is" | "Dejar como está" | "Unverändert lassen"
   - `review_got_it` "Got it" | "Entendido" | "Verstanden"
   - `review_all_skipped` "All changes skipped: the garment stays as it is." | "Todos los cambios saltados: la prenda se queda como está." | "Alle Änderungen ausgelassen: Das Kleidungsstück bleibt unverändert."
   - `review_none_left_occ` "After this, the garment has no occasions." | "Después de esto, la prenda no tendrá ocasiones." | "Danach hat das Kleidungsstück keine Anlässe mehr."
   - `review_hint_changes` "Tap a change to skip it, then apply or reject. Reviewed garments leave the list." | "Toca un cambio para saltarlo y luego aplica o rechaza. Las prendas revisadas salen de la lista." | "Tippe auf eine Änderung, um sie auszulassen, dann übernimm oder lehne ab. Geprüfte Kleidungsstücke verschwinden aus der Liste."
   - `review_left` one "1 left" | "Queda 1" | "Noch 1"; other "%1$d left" | "Quedan %1$d" | "Noch %1$d"
   - `review_now` "Now: %1$s" | "Ahora: %1$s" | "Jetzt: %1$s"; `review_none` "none" | "ninguna" | "keine"
   - `review_cd_add` "Add %1$s. Double-tap to leave it out." | "Añadir %1$s. Toca dos veces para dejarla fuera." | "%1$s hinzufügen. Doppeltippen, um es wegzulassen."
   - `review_cd_add_off` "%1$s will not be added. Double-tap to add it." | "%1$s no se añadirá. Toca dos veces para añadirla." | "%1$s wird nicht hinzugefügt. Doppeltippen, um es hinzuzufügen."
   - `review_cd_remove` "Remove %1$s. Double-tap to keep it." | "Quitar %1$s. Toca dos veces para conservarla." | "%1$s entfernen. Doppeltippen, um es zu behalten."
   - `review_cd_remove_off` "%1$s stays. Double-tap to remove it." | "%1$s se queda. Toca dos veces para quitarla." | "%1$s bleibt. Doppeltippen, um es zu entfernen."
   - `review_cd_swap` "%1$s instead of %2$s. Double-tap to keep %2$s." | "%1$s en lugar de %2$s. Toca dos veces para conservar %2$s." | "%1$s statt %2$s. Doppeltippen, um %2$s zu behalten."
   - `review_cd_swap_off` "Stays %2$s. Double-tap to change to %1$s." | "Se queda en %2$s. Toca dos veces para cambiar a %1$s." | "Bleibt %2$s. Doppeltippen, um zu %1$s zu wechseln."
   - `review_cd_removed` "%1$s was already removed." | "%1$s ya se quitó." | "%1$s wurde bereits entfernt."
   - `review_cd_replace` "Set %1$s. Double-tap to leave it out." | "Poner %1$s. Toca dos veces para dejarla fuera." | "%1$s setzen. Doppeltippen, um es wegzulassen."
   - `review_cd_replace_off` "%1$s will not be set. Double-tap to set it." | "%1$s no se pondrá. Toca dos veces para ponerla." | "%1$s wird nicht gesetzt. Doppeltippen, um es zu setzen."
6. `DarkThemeReviewScanTest`: `ThemeSourceScan` over `TagReviewScreen.kt`, `ColorReviewScreen.kt`, `ReviewScaffold.kt`, `ReviewChangeChips.kt`, `TagReviewCard.kt`, `TagReviewCardModel.kt` (use the helper as `DarkThemeSharedScanTest` does). Both tag tests that pin the commit functions (`TagReviewAcceptAllTest`, the tag part of `ReviewDiscardTest`) stay unchanged and green.

## Tests
V-REV-CARD-1, -2, -3 (CI); V-REV-CARD-4 (REV); V-REV-CARD-5 (independent review).

## Done when
Rows pass; CI green; the receipt lists the ES/DE strings, the source of the colour text in the identity line, and says what happened to the picker on suggested cards (removed on purpose, R3-DEC-054 item 7).

## Do not
Save per garment, change Back/Discard or the dialogs, add Undo, a Reviewed section, bulk wording or the completion screen (later tasks); touch `RobiaApp.kt`, `ReviewScaffold.kt`, the colour review, `ReviewPlanner.kt`; show the garment name; show chips for the restore merge; hard-code a colour.
