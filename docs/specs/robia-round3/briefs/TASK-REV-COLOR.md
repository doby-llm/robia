# TASK-REV-COLOR — Colour review: one description line

## Goal
F12 (REQ-FB-12, R3-DEC-052): Manu keeps the colour review as it is and asks for ONE description line saying that the colours on a card can be tapped to change the proposal (mockup `mockups/review-redesign-2/pick-then-decide.html`, frame 12, "today's screen + new hint").

## Read first
`ui/ColorReviewScreen.kt` `ColorReviewScreen` (~121-345: the `LazyVerticalGrid`, the `ReviewProgressHeader` item ~262, the empty card item, the candidate items); `ColorMappingStrip` (~449) to see what is tappable; `app/src/test/java/com/gusanitolabs/robia/ui/AddPhotoScrollTest.kt` (how a test reads the three `strings.xml`).

## Files
`ui/ColorReviewScreen.kt` (one new grid item only); the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/ColorReviewHintTest.kt`.

## Steps
1. Strings, block `<!-- REV-COLOR -->` at the end of each file: `color_review_tap_hint` EN "Robia suggests the colors on the right. Tap one to change it before you accept." (the app's spelling is "colors"; the mockup wrote "colours"), ES "Robia propone los colores de la derecha. Toca uno para cambiarlo antes de aceptar.", DE "Robia schlägt die Farben rechts vor. Tippe auf eine, um sie vor dem Akzeptieren zu ändern."
2. One new `item(span = { GridItemSpan(maxLineSpan) })` directly after the `ReviewProgressHeader` item, composed only while `candidates` is not empty: `Text(stringResource(R.string.color_review_tap_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)`. No new colour literal (the DARK scan tests are in force), no change to spacing elsewhere.
3. `ColorReviewHintTest`: the key exists in EN, ES and DE with the texts above.

## Tests
V-REV-COLOR-1 (CI), V-REV-COLOR-2 (REV); the device check is step 12 of V-REV-D (collected with REV-DONE).

## Done when
V-REV-COLOR-1 and -2 pass; CI green.

## Do not
Change the colour review's cards, buttons, bottom bar, counts, dialogs or semantics, `ReviewScaffold.kt`, or `ReviewCompleteOverlay.kt`.
