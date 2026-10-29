# f3-b: F27, F28, F29 (main b7b5476, read-only)

Paths are under `app/src/main/java/com/gusanitolabs/robia/` unless noted.

## F27 Duplicate review screen

### (a) "Untitled garment" on every row
- **Cause:** `ui/DuplicateReviewScreen.kt:184` renders `item.name.ifBlank { stringResource(R.string.browse_similar_untitled) }`. Garments are saved without a name (`ClothingItem.name` is `""` by default, `core/model/ClothingModels.kt:9`), so every row shows the fallback "Untitled garment" (`res/values/strings.xml:601`).
- **Data a garment really has:**
  - `tags` (category, occasion, season; `GarmentTag.name`, `categoryId`).
  - `colorMetrics.primaryDisplayLabel` and `primaryPaletteColorName`.
  - `notes`, `fitValue`.
  - Browse already shows these: `ui/BrowseCardText.kt:32` `cardTitle` (name, else category label, else untitled) and `:38` `cardMeta` (occasions, then seasons). The tag labels come from `tag.localizedTagLabel(context)` (`ui/BrowseGrid.kt:902`).
- **Proposed fix:**
  - Add a pure helper `duplicateRowLine(name: String, tags: List<UiTag>, colourLabel: String?, collator): String?` in `ui/BrowseCardText.kt` or a new `ui/DuplicateRowText.kt`.
  - It returns the trimmed name if non-blank, else `"<category label> · <colour>"`, else `cardMeta`, else `null`.
  - `DuplicateGarmentRow` draws the `Text` only when the result is non-null. The Keep/Delete chips stay, and the 72 dp photo carries identity.
  - A group row for a garment with no data then shows photo + chips only.
- **Files:**
  - `ui/DuplicateReviewScreen.kt`
  - `ui/BrowseCardText.kt` (the helper reuses `cardTitle` and `cardMeta`)
  - new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateRowTextTest.kt`
- **JVM tests:** name wins; category plus colour; meta only; everything empty gives null; whitespace name treated as blank.
- **Strings:** none new. `browse_similar_untitled` stays (used by `BrowseGrid.kt:591`, `ItemDetailScreen.kt:200`, `BrowseFilterBar.kt:491`).

### (b) Empty screen after finishing
- **Today:**
  - Browse banner → `onDuplicateReviewClick = { pushRoute(RobiaRoute.DuplicateReview) }` (`ui/RobiaApp.kt:2250`), so the stack is `[Browse, DuplicateReview]`.
  - Delete goes through `onDeleteDuplicateItems = onDeleteItems` (`:2252`). This is not `deleteItemsAndReturnToBrowse` (`:1303`), so there is no navigation.
  - "Not duplicates" goes through `dismissDuplicateGroup` (`:1186`).
  - Groups are pruned by `DuplicateSession.keepActive` (`:1184`), `DuplicateSession.dismiss` (`:1187`), and the rescan.
  - At zero groups the screen shows only `duplicate_empty`, "No possible duplicates left." (`ui/DuplicateReviewScreen.kt:66-72`).
  - The bottom bar is hidden on this route (`ui/RobiaApp.kt:2083`), so the only exit is the top-bar arrow (`:1995`) or Back.
- **Completion mechanism to reuse:**
  - `var reviewComplete by remember { mutableStateOf<ReviewCompleteText?>(null) }` (`:1013`).
  - It is set at `:1506` (`finishTagReview`) and `:1561` (colour commit), followed by `replaceRoute(...)`.
  - It is rendered at `:2294-2296` with `ReviewCompleteOverlay(text, onFinished = { reviewComplete = null })`.
  - `ReviewCompleteKind { Tags, Colors }` (`ui/ReviewCompleteOverlay.kt:43`), `reviewCompleteText(kind, n)` returns null for n ≤ 0 (`:49`), and the message `when` is at `:72-75`.
  - The banner is already hidden while `reviewComplete != null` (`:1972`).
- **Proposed fix:**
  1. Add `ReviewCompleteKind.Duplicates`, a message branch using the new `review_complete_duplicates` plural, and an updated doc comment ("returns to Manage" becomes "leaves the review").
  2. Add an `onAllResolved: (removedCount: Int) -> Unit` callback to `DuplicateReviewScreen`. The screen keeps `removedCount` and a `userActed` flag. "Delete" adds `deleteIds.size` to `removedCount` at `:113`. "Not duplicates" sets `userActed = true` at `:85`.
  3. Add `LaunchedEffect(groups.isEmpty())`. It fires only when groups go from non-empty to empty and `userActed` is true. This avoids a rescan emptying the list under a user who is just looking.
  4. In `RobiaApp.kt` at the `DuplicateReview` branch (route host around `:3144`) and the `RobiaNavHost` parameter list (`:3037-3041`), wire the callback to `reviewComplete = reviewCompleteText(ReviewCompleteKind.Duplicates, n); replaceRoute(RobiaRoute.Browse)`.
  5. When n = 0 (only "Not duplicates" used) there is no overlay, but still navigate to Browse. The empty screen never remains.
  6. Make the first-empty rule a pure function `shouldFinishDuplicateReview(wasNonEmpty, nowEmpty, userActed)`.
- **Files:**
  - `ui/DuplicateReviewScreen.kt`
  - `ui/ReviewCompleteOverlay.kt`
  - `ui/RobiaApp.kt`
  - `res/values/strings.xml`, `res/values-es/strings.xml`, `res/values-de/strings.xml`
  - `app/src/test/java/com/gusanitolabs/robia/ui/ReviewCompleteTextTest.kt` (edit)
  - new `DuplicateReviewFinishTest.kt`
- **Strings** (plural `review_complete_duplicates`):

  | Lang | one | other |
  |---|---|---|
  | EN | `%1$d duplicate removed` | `%1$d duplicates removed` |
  | ES | `%1$d duplicado eliminado` | `%1$d duplicados eliminados` |
  | DE | `%1$d Duplikat entfernt` | `%1$d Duplikate entfernt` |

- **Decision for Manu / coordinator:** the brief asks for "merged", but nothing merges (it deletes). "Removed" is accurate.
- **Risks:**
  - `removedCount` lives in screen state. A process restart or an activity recreation while the screen is open resets it, so the overlay shows no count, though the navigation still happens.
  - The prune is asynchronous: groups empty only after Room emits. Navigating on `groups.isEmpty()` is correct, not on click.
  - The overlay `Surface` is full-screen, so it covers the top bar for 1.6 s. That matches the other reviews.

## F28 "Press back again to exit" never works

### What exists
- Policy: `ui/BackExitPolicy.kt:29-37` (`decideBack`, window `EXIT_WINDOW_MILLIS = 2000`, `:23`).
- Wiring, `ui/RobiaApp.kt`:
  - `:1756` stack handler, enabled when `restoreProgress != null || routeStack.size > 1`.
  - `:1768-1773` hint state plus the `LaunchedEffect(currentRoute, browseQuery, similarToGarmentId, browseFilters, selectedBrowseItemIds) { lastExitHintMs = null }`.
  - `:1775-1802` the root handler: `Toast.makeText(...).show()` at `:1797`, `finish()` at `:1800`.
- Manifest (`app/src/main/AndroidManifest.xml`): no `android:enableOnBackInvokedCallback`, no `launchMode`. `MainActivity` has no `onBackPressed`/`onKeyDown` override. `targetSdk 35` and `activity-compose 1.9.3` (`gradle/libs.versions.toml:6`) mean the legacy back path is used, which `BackHandler` supports on Android 13.
- Other `BackHandler`s: `TagReviewScreen.kt:818`, `ColorReviewScreen.kt:247`, `AddEditClothingScreen.kt:773`, `BatchAddClothingScreen.kt:158`. They are composed only on their own routes, so none sits above Browse.
- Stack at root: tabs use `replaceRoute` (`RobiaApp.kt:2092`), so Browse sits alone at size 1 and the root handler is the enabled one.

### What NAV-BACK's tests covered
- Only `BackExitPolicyTest` (`app/src/test/.../ui/BackExitPolicyTest.kt`): `decideBack` as a pure priority table and the 2000 ms window (V-NAV-BACK-1 and -2).
- They pass because they feed `decideBack` a hand-built `lastExitHintMs`. They never exercise the Compose wiring that stores the hint, resets it, shows the toast, or calls `finish()`.
- V-NAV-BACK-3 was a code read. V-NAV-BACK-D is Manu's device row, and it is the row F28 now fails.

### Root cause
I could not find a logic defect by reading. The state machine is correct, the handler is enabled at root, `findActivity()` (`RobiaApp.kt:3486`) resolves the activity, and nothing above Browse swallows Back. So I cannot cite a single guilty line without a device trace. **Unknown, ranked by what the code allows:**
1. **The hint is invisible or lost on Manu's device (Xiaomi, API 33).** The only user feedback is a platform `Toast` (`:1797`). If the OEM suppresses or delays it, the user never sees "press again", and the first press looks like a no-op. Nothing in-app proves the first press ran.
2. **Reset by a transient key change.** The effect at `:1771` resets the hint on any change of five unrelated state keys, run asynchronously after composition. A busy Browse (sync, scan, prune at `:1249`) could change a key and clear the hint inside the 2 s window. I found no assignment that changes a key as a side effect of the press itself, so this is plausible but not shown.
3. **IME or focus eats a press.** If the search field has focus, the first Back closes the keyboard and never reaches the dispatcher, so exit needs 3 presses.

### Proposed fix (robust under all three, with a diagnostic)
- **Pure part** (new `ui/BackExitMachine.kt`): `class BackExitMachine(private val clock: () -> Long)`. It holds `hintAtMs` and `contextKey` (a data class of route, query, similar-to, filters, selection). `onBack(state): BackAction` takes the same inputs as `decideBack` and handles the reset itself: a press whose `contextKey` differs from the stored one starts over, so no `LaunchedEffect` is needed. `ShowExitHint` stores `(now, key)`, and `Exit` requires the same key within the window. It also exposes `hintVisible(now)` for the pill.
- **Wiring:**
  - Replace `lastExitHintMs`/`LaunchedEffect` (`RobiaApp.kt:1768-1773`) with `remember { BackExitMachine { SystemClock.elapsedRealtime() } }`.
  - Show the hint as an in-app pill (bottom `Surface`, auto-hides after 2 s), keeping the toast only as a secondary signal. This removes cause 1.
  - Add `Log.d("BackExit", action...)` at each branch, so the next device run shows whether the press arrived.
- **Files:** `ui/BackExitMachine.kt` (new), `ui/BackExitPolicy.kt` (kept, called by the machine), `ui/RobiaApp.kt`, new `BackExitMachineTest.kt`, and the three `strings.xml` only if the pill gets a new string (reuse `back_exit_hint`: none).
- **JVM tests:** two presses 1999 ms apart exit; 2001 ms shows the hint again; a key change between presses restarts; a clock going backwards never exits; Manage still goes to Browse; the hint is not visible after the window.
- **Risks:**
  - This is a redesign without a proven cause. It could still fail if the press never arrives (cause 3), which the log will show.
  - Adding a predictive-back manifest flag was explicitly excluded by NAV-BACK. Do not add it here.
  - Touching `ui/RobiaApp.kt` again.
- **Unknowns:**
  - Did the toast appear at all on build 0de712b? Did the second press close the app or show the toast again? Manu's one-line answer picks between causes 1 and 2.
  - Does the search field hold focus on Browse at rest?

## F29 Prompt after deleting a tag nobody has

### Where the prompt is decided
- `deleteTagAndOfferReview` (`ui/RobiaApp.kt:1490`) calls `offerTagReviewAfterWrite(deletedTagChangeSet(...))` (`:1474`).
- `offerTagReviewRequest` (`ui/TagReviewScreen.kt:354-371`) reads a live snapshot of all garments from Room, calls `write()`, then returns a request unless `changeSet == null`, the snapshot failed, or `!hasReviewableGarments(garments, ReviewKind.Tag)`.
- `hasReviewableGarments` (`ui/ReviewScaffold.kt:120-123`) for `Tag` is only `items.any { !it.isArchived }`, so any single active garment makes a prompt, whether or not it holds the deleted tag.
- Planner confirms what a delete affects: `deletedOccasion` and `deletedCategory` both return `null` when `before.none { it in changeSet.touchedTagIds }` (`suggestions/ReviewPlanner.kt:127-136`, `:137-148`). So with no garment holding the tag the review is guaranteed empty.
- The colour flow already has the same guard: `hasReviewableGarments(clothingItems, ReviewKind.Colour)` before `pendingColorReviewChangeSet` (`RobiaApp.kt:1461`).

### Proposed fix
- Add a pure `internal fun hasAffectedGarments(changeSet: TagChangeSet, garments: List<ClothingItem>): Boolean` in `ui/TagReviewScreen.kt`, next to `affectedGarmentCount` (`:107`).
  - `Deleted`: `garments.any { !it.isArchived && it.tags.any { tag -> tag.id in changeSet.touchedTagIds } }`.
  - Other operations: `true`, unchanged behaviour.
- Use it in `offerTagReviewRequest` (`:369`): `|| !hasAffectedGarments(changeSet, garments)` returns `null`. The write has already landed, so the tag is deleted and no prompt shows. This is the same path as the "no active garment" case.
- **Real time:** the snapshot is read from Room after the previous review write joins, immediately before `write()`, so it reflects the live garments, not a stale UI list. It is a count over an in-memory list (garments × their tags), with no model, embedding, or vectors. It is cheap.
- **Other operations:**
  - Add and rename: not covered. Whether a garment "qualifies" needs the scan, which needs label embeddings and image vectors. That is the expensive path, so keep the existing "any active garment" check.
  - Restore default tags: `restoredTagChangeSet` already returns `null` when nothing is added or renamed (`:417-431`), so no prompt, and it is unaffected.
  - Delete of non-reviewed kinds (season, etc.) is already `null` (`:411`).
- **Archived-only holders:** with this check, a tag held only by archived garments gives no prompt, because the scan reads only active garments. The write still strips the tag from them. This matches the existing "no active garment" behaviour, but means no Discard/undo for those archived garments. Flag for Manu.
- **Side note:** `affectedGarmentCount` (`:107`) counts archived garments for the header. It is not part of this fix, but is inconsistent with the scan. Mention only.
- **Files:** `ui/TagReviewScreen.kt`, `app/src/test/java/com/gusanitolabs/robia/ui/ReviewDiscardTest.kt` (edit; it already calls `offerTagReviewRequest`, `:183`, `:212`, `:268`, `:285`, `:295`), new test cases in `TagReviewScanTest.kt` for the helper. `RegressionSourceContractTest.kt:248-253` greps the body of `offerTagReviewRequest`, so re-check it still matches.
- **JVM tests:**
  - Deleted category held by no garment: `null`, and `write` still ran.
  - Deleted occasion held only by an archived garment: `null`.
  - Held by one active garment: request returned.
  - Added, renamed and restored: unchanged.
- **Strings:** none.
- **Risks:** low. Snapshot-read failure still returns `null` (existing).
- **Unknowns:** none that block.

## Recommended task split

| Id | Scope | Files | Predecessors | Independent review |
|---|---|---|---|---|
| **F29-NOPROMPT** | `hasAffectedGarments` guard in `offerTagReviewRequest` | `ui/TagReviewScreen.kt`, `ReviewDiscardTest.kt`, `TagReviewScanTest.kt`, `RegressionSourceContractTest.kt` (check only) | none (does not touch `RobiaApp.kt`) | no |
| **F27-ROW** | Drop "Untitled", build the row line | `ui/DuplicateReviewScreen.kt`, `ui/BrowseCardText.kt`, new `DuplicateRowTextTest.kt` | none; share `DuplicateReviewScreen.kt` with F27-DONE, so run before it | no |
| **F27-DONE** | `Duplicates` kind, plural strings, finish callback and navigation | `ui/DuplicateReviewScreen.kt`, `ui/ReviewCompleteOverlay.kt`, `ui/RobiaApp.kt`, the three `strings.xml`, `ReviewCompleteTextTest.kt`, new `DuplicateReviewFinishTest.kt` | F27-ROW (same screen file), and any other open `ui/RobiaApp.kt` writer | yes (navigation and data-loss adjacent) |
| **F28-BACK** | `BackExitMachine`, in-app hint, log; needs Manu's toast answer first | `ui/BackExitMachine.kt`, `ui/BackExitPolicy.kt`, `ui/RobiaApp.kt`, new `BackExitMachineTest.kt` | F27-DONE (shared `ui/RobiaApp.kt`; one writer at a time) | yes (previous attempt passed CI and failed on device) |

Parallel: F29-NOPROMPT ∥ F27-ROW, then F27-DONE → F28-BACK (serial on `ui/RobiaApp.kt`). F28-BACK goes last so the diagnostic log and Manu's answer on the toast can land first.
