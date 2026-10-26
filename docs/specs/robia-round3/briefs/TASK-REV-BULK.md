# TASK-REV-BULK — Apply all remaining, with confirmation

## Goal
F12 (REQ-FB-12, R3-DEC-052): 100+ garments need one tap, guarded. "Apply all remaining (N)" in the bottom bar, a confirmation that says how many tags are added and removed on how many garments, one batch write, and one Undo for the whole batch.

## Read first
`R3-DEC-052`, `R3-DEC-054`; `mockups/review-redesign-3/build.py` (`bulk_bar`, `review_apply_remaining`, `review_bulk_title`, `review_bulk_body`) and `mockups/review-redesign-2/pick-then-decide.html` frame 9 (the dialog); `ui/TagReviewSession.kt` (`bulkPlan`, `applyBulk`); `ui/TagReviewScreen.kt` as left by REV-UNDO (the old bottom bar with `tag_review_accept_all` and `tag_review_save_accepted`); `ui/ReviewScaffold.kt` `ReviewBottomBar` (used as is).

## Files
`ui/TagReviewScreen.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewBulkTest.kt`.

## Steps
1. **Bottom bar.** `ReviewBottomBar` label `review_apply_remaining` with N = `bulkPlan().garments`; disabled while the scan runs and when N is 0 (the bar stays so the layout does not jump). The old labels are no longer used by this screen (their strings stay in the files).
2. **Confirmation.** An `AlertDialog`: title `review_bulk_title` (N), body `review_bulk_body` (tags added, tags removed, garments) or, when 0 tags are removed, `review_bulk_body_adds` (tags added, garments); confirm `review_apply` ("Apply"), dismiss `R.string.cancel`. The numbers come from the plan, not from a second count. A tapped confirm closes the dialog first and runs once (a second tap in the same frame does nothing).
3. **Apply.** `session.applyBulk()` decides exactly the planned cards: cards with something to apply are applied with their current chips, all-left-out cards become kept (no write), locked-only cards are dismissed (no write), an unchosen manual-choice card stays in the list. One `onApply(batch)` call with the deltas; results fill the session per garment; a failed batch returns every card of it to Pending and shows the existing failure snackbar.
4. **Undo.** The snackbar `review_snack_bulk` (plural "N garments updated") with Undo: one `onRevert(allEffectiveDeltas)` for the batch and every garment of the batch returns to Pending (the kept and dismissed ones too), chips as they were. A failed revert leaves the batch decided and shows `review_revert_failed` (REV-UNDO). It is the latest decision, so a newer decision replaces it as for single garments; per-garment "Review again" keeps working afterwards.
5. **Strings**, block `<!-- REV-BULK -->` (EN | ES | DE): `review_apply_remaining` "Apply all remaining (%1$d)" | "Aplicar las %1$d restantes" | "Alle restlichen %1$d übernehmen"; `review_bulk_title` "Apply the remaining %1$d?" | "¿Aplicar las %1$d restantes?" | "Die restlichen %1$d übernehmen?"; `review_bulk_body` "Robia will add %1$d tags and remove %2$d on %3$d garments. You can undo it right after." | "Robia añadirá %1$d etiquetas y quitará %2$d en %3$d prendas. Podrás deshacerlo justo después." | "Robia fügt bei %3$d Kleidungsstücken %1$d Tags hinzu und entfernt %2$d. Du kannst das gleich danach rückgängig machen."; `review_bulk_body_adds` "Robia will add %1$d tags to %2$d garments. You can undo it right after." | "Robia añadirá %1$d etiquetas a %2$d prendas. Podrás deshacerlo justo después." | "Robia fügt %2$d Kleidungsstücken %1$d Tags hinzu. Du kannst das gleich danach rückgängig machen."; `review_apply` "Apply" | "Aplicar" | "Übernehmen"; `review_snack_bulk` one "1 garment updated" | "1 prenda actualizada" | "1 Kleidungsstück aktualisiert", other "%1$d garments updated" | "%1$d prendas actualizadas" | "%1$d Kleidungsstücke aktualisiert". Singular forms of "tags" in the two bodies: make them quantity strings keyed on the number of tags if the grammar needs it, and list your choice in the receipt.
6. **Tests** (`TagReviewBulkTest`): the V-REV-BULK-1 and -2 rows with a recording `onApply`/`onRevert`.

## Tests
V-REV-BULK-1, -2 (CI); V-REV-BULK-3 (independent review).

## Done when
Rows pass; CI green.

## Do not
Add a write path, change the colour review's "Accept all", or add the completion screen.
