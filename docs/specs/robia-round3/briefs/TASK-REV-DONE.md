# TASK-REV-DONE — Completion screen with "Edit changes"

## Goal
F12 + F21 (R3-DEC-052, R3-DEC-054 item 9): when the last garment is decided the review ends in a calm, static completion screen that tells what happened in the same sign language as the chips ("+ 52 tags added · − 9 tags removed"), offers "Edit changes" (back to the list, everything in "Reviewed") and "Done" (back to Manage tags). It replaces the timed overlay for the tag review.

## Read first
`R3-DEC-052`, `R3-DEC-054` item 9; `mockups/review-redesign-3/build.py` (`done`, `.donebox .tally`) and `mockups/review-redesign-2/pick-then-decide.html` frame 11; `ui/TagReviewSession.kt` (`isComplete`, `tallies`); `ui/TagReviewScreen.kt` as left by REV-BULK; `ui/ReviewCompleteOverlay.kt` (left as it is: the colour review still uses it; `reviewCompleteText` returns null for 0); the REV-SAVE call `onFinish(changedGarments)` in `ui/RobiaApp.kt` (not edited here: `onFinish(0)` yields no overlay).

## Files
`ui/TagReviewScreen.kt`; new `ui/TagReviewComplete.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCompleteTest.kt`.

## Steps
1. When `session.isComplete(isScanning)` and the screen is not in "editing" mode, replace the list and the bottom bar by the completion view: a check in a `primaryContainer` circle (static, no `SparkleBurst`, no timer); a title, `review_complete_tags` (existing plural, N = garments updated) or `review_complete_none` when none changed; the tally row: "+ N tags added" and "− N tags removed" (plurals `review_complete_added`, `review_complete_removed`) each with a filled sign circle in the add and remove tokens and `onSurface` text, an entry only when its number is above 0; `review_complete_kept` (plural, N = garments kept) when above 0; up to five thumbnails (36×42 dp) of the updated garments; a tonal "Edit changes" button and a filled "Done", both ≥ 48 dp. The Undo snackbar of the last decision stays visible over it.
2. Edit changes sets a screen flag that shows the list again with every garment in "Reviewed" (the section expanded); the flag clears when a card has been returned to Pending and decided again, so the view does not come straight back. Done calls `onFinish(0)` (RobiaApp routes to Manage tags and shows no overlay).
3. The empty-scan card, the model-unavailable card and the skipped line keep today's behaviour; a review that found nothing does not show this screen.
4. **Strings**, block `<!-- REV-DONE -->` (EN | ES | DE): `review_complete_none` "No garments changed" | "Ninguna prenda cambió" | "Kein Kleidungsstück geändert"; `review_complete_added` one "1 tag added" | "1 etiqueta añadida" | "1 Tag hinzugefügt", other "%1$d tags added" | "%1$d etiquetas añadidas" | "%1$d Tags hinzugefügt"; `review_complete_removed` one "1 tag removed" | "1 etiqueta quitada" | "1 Tag entfernt", other "%1$d tags removed" | "%1$d etiquetas quitadas" | "%1$d Tags entfernt"; `review_complete_kept` one "1 kept as it was" | "1 se quedó igual" | "1 blieb unverändert", other "%1$d kept as they were" | "%1$d se quedaron igual" | "%1$d blieben unverändert"; `review_complete_edit` "Edit changes" | "Editar cambios" | "Änderungen bearbeiten". `review_complete_tags` and `done` exist already.
5. **Tests** (`TagReviewCompleteTest`): the V-REV-DONE-1 pure rules (view state function, tally entries, thumbnail cap, the editing flag).

## Tests
V-REV-DONE-1 (CI); V-REV-DONE-2 (REV); V-REV-D (Manu, the one DEV row of REV).

## Done when
V-REV-DONE-1, -2 pass and V-REV-D is ready for Manu; CI green.

## Do not
Edit `ReviewCompleteOverlay.kt`, `RobiaApp.kt` or the colour review; add sparkle or an auto-dismiss; add a write path.
