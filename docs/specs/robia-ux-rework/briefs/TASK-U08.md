# TASK-U08 — Review and AI feel + completion screen

## Goal
Accept/Discard and AI buttons feel flat (R14), and Manu asked for a short, subtle "done" moment after a review (revision 2). Add a directional exit for review cards, the AI shimmer style on AI entry points, and a 1.6 s completion screen with a few sparks after a tag or colour review is committed. No haptics (rejected by Manu). Review logic must not change (mockup `motion.html`).

## Read first
TS§8; REQ-MO-02..04; UX-DEC-014; `ui/ReviewScaffold.kt` (`ReviewAcceptedCard` 107-130, `ReviewBottomBar` 266-295; comment at line 61: colour and tag reviews must look identical); `ui/TagReviewScreen.kt` 615-740; `ui/ColorReviewScreen.kt` 348-420; sparkle `IconButton` in `ui/BrowseFilterBar.kt`; Similar button in `ui/ItemDetailScreen.kt`; `ui/RobiaApp.kt` `commitTagReview` (~1329) and `commitColorReview` (~1360) and where the `RobiaRoute.ManageTags` content is drawn; `core/designsystem/Motion.kt` (`pressScale`, `aiSurface`, `SparkleBurst`, `LocalReducedMotion`).

## Steps
1. `ReviewAcceptedCard(…, direction: ExitDirection = Accept)`: exit = `slideOutHorizontally { ±40.dp px } + fadeOut + shrinkVertically`, 250 ms, keeping the existing 200 ms hold. Discarded cards (if they currently vanish) use the same component with `Discard` direction — only where the existing code already removes the card; do not add a new hold or state.
2. `pressScale` on the per-card Accept/Discard buttons and the `ReviewBottomBar` button.
3. AI style: sparkle button in Browse and Similar button in detail get `aiSurface(active = <existing busy flag if any, else false>)`; the primary AI action of `ColorReviewPromptDialog` and `TagReviewPromptDialog` too.
4. New `ui/ReviewCompleteOverlay.kt`: `enum class ReviewKind { Tags, Colors }`; `data class ReviewCompleteText(val kind: ReviewKind, val count: Int)`; pure `internal fun reviewCompleteText(kind: ReviewKind, changedGarments: Int): ReviewCompleteText?` (null when ≤ 0); `@Composable fun ReviewCompleteOverlay(text: ReviewCompleteText, onFinished: () -> Unit)` = full-screen `surface` box, centred 72 dp `primaryContainer` circle with a check icon (scale-in 300 ms), `SparkleBurst` around it, below it `pluralStringResource(review_complete_tags | review_complete_colors, count, count)` in `titleMedium`; `LaunchedEffect(Unit) { delay(reviewCompleteDurationMs(reducedMotion)); onFinished() }` with pure `internal fun reviewCompleteDurationMs(reducedMotion: Boolean): Long` (1600 / 1200); tap anywhere → `onFinished()`.
5. `RobiaApp.kt`: `var reviewComplete by remember { mutableStateOf<ReviewCompleteText?>(null) }`; in `commitTagReview` set `reviewComplete = reviewCompleteText(ReviewKind.Tags, updatedItems.size)` before `replaceRoute(RobiaRoute.ManageTags)`; in `commitColorReview` set it from the `updatedItems` argument size (not the removed-colour clear list). Draw `reviewComplete?.let { ReviewCompleteOverlay(it) { reviewComplete = null } }` above the route content (same place other full-screen overlays are drawn, e.g. the restore overlay). Discard paths never set it.
6. Strings (all locales, plurals): `review_complete_tags` "Tags updated on %1$d garment" / "…garments" (ES "Etiquetas actualizadas en %1$d prenda(s)", DE "Tags bei %1$d Kleidungsstück(en) aktualisiert"), `review_complete_colors` "Colours updated on %1$d garment(s)" (ES "Colores actualizados en …", DE "Farben bei … aktualisiert").

## Tests
`ReviewCompleteTextTest` (V-U08-2); grep evidence for V-U08-3. Receipt shows the `git diff` of review files limited to modifier/animation lines (V-U08-1).

## Done when
V-U08-1..3, CI green.

## Do not
Change accept/discard/commit/undo logic, counters, dialogs' text or order; add vibration; loop the sparkle animation.
