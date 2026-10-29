# TASK-FB3-DUP-DONE — Leave the duplicate review when it is finished

## Goal
ui3 F27 (b) (REQ-FB-27, R3-DEC-062). When the user resolves the last duplicate group the screen shows only "No possible duplicates left.". Instead, like the garment review: a short "done" screen, then Browse, never an empty screen left behind.

## Read first
`artifacts/inv-f3-b.md` F27 (b); `R3-DEC-062`; `ui/DuplicateReviewScreen.kt` (`DuplicateReviewScreen`, the delete confirmation, `markedIds`, `onNotDuplicates`); `ui/ReviewCompleteOverlay.kt` (`ReviewCompleteKind`, `ReviewCompleteText`, `reviewCompleteText`, the message `when`); `ui/RobiaApp.kt`: `reviewComplete`, `finishTagReview`, `commitColorReview`, the `ReviewCompleteOverlay(...)` call at the end of `RobiaApp`, `dismissDuplicateGroup`, `DuplicateSession.keepActive`, `replaceRoute`, `duplicatePromptVisible` (already waits for `reviewComplete`), and in `RobiaNavHost` the `onDeleteDuplicateItems` / `onDismissDuplicateGroup` parameters and the `RobiaRoute.DuplicateReview ->` branch; `ReviewCompleteTextTest`.

## Files
`ui/DuplicateReviewScreen.kt`; `ui/ReviewCompleteOverlay.kt` (the kind and its message only); `ui/RobiaApp.kt` (the `DuplicateReview` wiring only: one `RobiaNavHost` parameter, its route branch, the call in `RobiaApp`); the three `strings.xml`; `app/src/test/java/com/gusanitolabs/robia/ui/ReviewCompleteTextTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateReviewFinishTest.kt`.

## Steps
1. **Overlay kind.** `ReviewCompleteKind { Tags, Colors, Duplicates }`; `ReviewCompleteOverlay`'s message `when` gets `Duplicates` → `pluralStringResource(R.plurals.review_complete_duplicates, n, n)`. Update the KDoc of `ReviewCompleteKind` and of the overlay ("returns to Manage" is not true for duplicates). `reviewCompleteText` is unchanged (null for 0).
2. **Strings**, block `<!-- FB3-DUP-DONE -->`, plural `review_complete_duplicates`: EN one "%1$d duplicate removed", other "%1$d duplicates removed"; ES one "%1$d duplicado eliminado", other "%1$d duplicados eliminados"; DE one "%1$d Duplikat entfernt", other "%1$d Duplikate entfernt". The word is "removed": nothing merges, Delete archives the garment (the screen's own body text says so).
3. **Pure rule** in `DuplicateReviewScreen.kt` (or beside it): `internal fun shouldFinishDuplicateReview(wasNonEmpty: Boolean, nowEmpty: Boolean, userActed: Boolean): Boolean = wasNonEmpty && nowEmpty && userActed`.
4. **Screen.** Add `onAllResolved: (removedCount: Int) -> Unit` to `DuplicateReviewScreen`. State: `removedCount` (add `deleteIds.size` when the delete is confirmed, before `onDeleteItems`), `userActed` (true on "Not duplicates" and on a confirmed delete), `wasNonEmpty` (true once `groups` is non-empty), `finished` (so the callback fires once). A `LaunchedEffect(groups.isEmpty())` calls `onAllResolved(removedCount)` when the rule holds and `!finished`. Navigate on the empty `groups` (the prune is asynchronous: Room emits after the delete), never on the click. The empty text remains for a screen that was opened empty or emptied by a rescan under someone who did not act.
5. **Wiring** in `RobiaApp.kt`: a new `RobiaNavHost` parameter passed to `DuplicateReviewScreen`; the lambda sets `reviewComplete = reviewCompleteText(ReviewCompleteKind.Duplicates, n)` and calls `replaceRoute(RobiaRoute.Browse)`. With `n == 0` (only "Not duplicates") there is no overlay but Browse still opens. The stack was `[Browse, DuplicateReview]`, so `replaceRoute` leaves `[Browse]`.
6. **Tests.** `DuplicateReviewFinishTest`: the truth table of `shouldFinishDuplicateReview`; not when the screen opened empty; not without a user action. `ReviewCompleteTextTest`: `(Duplicates, 0)` null, `(Duplicates, 3)` not null; the plural exists in the three locales with the texts above.
7. Receipt: a note that `removedCount` lives in screen state (a process restart in the middle loses the count; Browse still opens), and the ES/DE strings.

## Tests
V-FB3-DUP-DONE-1, -2 (CI); V-FB3-DUP-DONE-3 (independent review); V-FB3-DUP-DONE-D (Manu).

## Done when
Rows pass; CI green; the independent review finds no BLOCKING item (navigation without the user having acted; a double finish; the delete path no longer asking for confirmation; an overlay with count 0; a file outside Files; a weakened test).

## Do not
Change the delete dialog or `onDeleteItems`, `dismissDuplicateGroup`, `DuplicateSession`, the scan or its trigger, the banner rules, or the top-bar arrow; navigate on a rescan; show an overlay for 0.
