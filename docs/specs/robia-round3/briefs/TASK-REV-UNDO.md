# TASK-REV-UNDO — Undo snackbar, "Reviewed · N" and "Review again"

## Goal
F12 (REQ-FB-12, R3-DEC-052, R3-DEC-054 item 5): a decision must be easy to take back. After every decision a snackbar offers Undo; decided garments live in a collapsed "Reviewed · N" section where "Review again" brings one back. Undo and "Review again" are the same step: revert what the garment's Apply wrote (nothing for a Reject) and put the card back where it was with the user's chips as they left them.

## Read first
`R3-DEC-054` items 4–5; `mockups/review-redesign-2/pick-then-decide.html` (frames 7, 8 and 10: shrinking list, snackbar, Reviewed section) and `build.py` (`snack`, `reviewed_section`, `rhead`); `ui/TagReviewSession.kt` (`revert`, `reviewed`, `appliedResult`); `ui/TagReviewScreen.kt` as left by REV-SAVE (`onApply`, `onRevert`); `ui/ReviewScaffold.kt` `ReviewAcceptedCard` (~178: `leaving` is local state and is never reset when `resolved` goes back to false); `ui/PhotoEditorScreen.kt` (`SnackbarHostState` use, ~223).

## Files
`ui/TagReviewScreen.kt`; `ui/ReviewScaffold.kt` (`ReviewAcceptedCard` only); the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewUndoTest.kt`.

## Steps
1. **Snackbar.** A `SnackbarHost` over the list, above the bottom bar. After a decision: Applied with a non-empty delta → plural `review_snack_applied` ("N changes applied", N = units), Rejected or Keep as is → `review_snack_kept`; Got it → none. Short duration, one at a time, a new decision replaces it, the action is Undo (`R.string.undo`). A pure function decides the message from the decision (`TagReviewUndoTest`).
2. **Undo.** Undo of the latest decision = `session.revert(id)`: for an Applied garment call `onRevert(listOf(appliedDelta))` (REV-SAVE's write path), for a Rejected one no write; the card returns to its scan position with `skipped` and the chosen tag kept. While that garment's write is in flight Undo is disabled (the snackbar action does nothing; `revert` refuses). A second Undo for the same decision does nothing. If the revert write fails, the card stays decided (it remains in Reviewed) and a snackbar `review_revert_failed` says so.
3. **Reviewed section.** After the last pending card, a row "Reviewed · N" (`review_section_reviewed`) with Show / Hide (`review_show`, `review_hide`), collapsed by default, shown only when N > 0; expanded, one row per decided garment, most recent first: thumbnail (36×42 dp), identity line, outcome (`review_snack_applied` plural or `review_snack_kept`) and a 48 dp "Review again" (`review_again`) text button = Undo for that garment. The pinned header shows "N reviewed ▾" (`review_reviewed_n`) when N > 0; tapping it expands the section and scrolls to it.
4. **`ReviewAcceptedCard`.** When `resolved` goes from true back to false, reset `leaving` to false so a garment undone while it is still leaving is visible again (and the screen removes the id from its hidden set). Behaviour for the colour review (which never un-resolves) is unchanged; one small pure rule if you extract it.
5. **Strings**, block `<!-- REV-UNDO -->` (EN | ES | DE): `review_snack_applied` one "1 change applied" | "1 cambio aplicado" | "1 Änderung übernommen", other "%1$d changes applied" | "%1$d cambios aplicados" | "%1$d Änderungen übernommen"; `review_snack_kept` "Kept as it was" | "Se queda como estaba" | "Bleibt unverändert"; `review_section_reviewed` "Reviewed · %1$d" | "Revisadas · %1$d" | "Geprüft · %1$d"; `review_reviewed_n` "%1$d reviewed" | "%1$d revisadas" | "%1$d geprüft"; `review_show` "Show" | "Mostrar" | "Anzeigen"; `review_hide` "Hide" | "Ocultar" | "Ausblenden"; `review_again` "Review again" | "Revisar otra vez" | "Erneut prüfen"; `review_revert_failed` "Couldn't undo. The garment stays as updated." | "No se pudo deshacer. La prenda se queda actualizada." | "Rückgängig fehlgeschlagen. Das Kleidungsstück bleibt aktualisiert."
6. **Tests** (`TagReviewUndoTest`): the V-REV-UNDO-1 to -3 rows, driven through the session and the screen's pure helpers with a recording `onRevert` fake.

## Tests
V-REV-UNDO-1, -2, -3 (CI); V-REV-UNDO-4 (independent review).

## Done when
Rows pass; CI green; the receipt states how an Undo that arrives while a write is in flight is refused and how the failure case behaves.

## Do not
Add a write path (use `onRevert`), undo the whole tag change (that stays Back's Discard), change the colour review's screen, or add bulk apply or the completion screen.
