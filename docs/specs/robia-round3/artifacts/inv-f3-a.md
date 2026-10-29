# f3-a: F21–F26, tag review screen and its completion (main b7b5476)

Paths are under `app/src/main/java/com/gusanitolabs/robia/`. Contrast ratios are my estimates.

## F21 Sparks icon invisible in dark mode
**Cause.** The prompt's confirm button is a filled `Button`, so its container is `primary`. Its icon is tinted `onTertiaryContainer` (`ui/TagReviewScreen.kt:516-520`, same in `ui/ColorReviewScreen.kt:105-108`).
- Dark `primary` is `#EAD7D1` (`core/designsystem/Color.kt:62`). Dark `onTertiaryContainer` is `#FFDCC7` (`Color.kt:59`, `Theme.kt:70-71`). Light on light is about 1.1:1.
- Light is `#C37F50` on `#1D100D`, about 5.8:1, so it was never noticed. DARK1 only pinned text pairs (`RobiaColorSchemeTest`).

**Same tint elsewhere (not broken in dark):** `ItemDetailScreen.kt:252` and `AddEditClothingScreen.kt:1520` (tonal buttons, about 9:1 in dark; light is about 2.4:1, an existing weakness), and `BrowseFilterBar.kt:389` (on the surface). `SparkleBurst` uses `tertiary` (`Motion.kt:101`), fine in both themes, so the overlay needs no change.

**Fix.** Add the extras token `RobiaExtras.sparkOnPrimary`. Light = `RobiaOnTertiaryContainer` (Light stays byte for byte, per DARK1). Dark = new `#6D390F` (the existing dark `tertiaryContainer` value, about 6.8:1 on `#EAD7D1`). Use it in the two prompt dialogs only. Simpler alternative: drop the tint and inherit `onPrimary`; the light icon turns white.
- Files: `core/designsystem/Theme.kt`, `Color.kt`, `ui/TagReviewScreen.kt`, `ui/ColorReviewScreen.kt`.
- JVM test: add a `sparkOnPrimary` over `primary` ≥ 3:1 pair for both schemes in `RobiaColorSchemeTest`.
- Strings: none. Risk: low.

## F22 Progress bar too abrupt
**Current.** After the scan, `ReviewLeftHeader` (`ui/TagReviewCard.kt:271`) draws `LinearProgressIndicator(progress)` with no animation. `progress = decided/found` (`ui/TagReviewCardModel.kt:99`), where `decided` is `session.reviewed.size`.
- It steps by 1/found at the instant of the decision, while the card is still leaving for 450 ms (`ReviewScaffold.kt:73,76`).
- "Apply all remaining" jumps it to 100%. Undo and Review again step it back.
- At scan end the scan header (raw `reviewScanProgress`, 100%) is replaced by `ReviewLeftHeader` at 0%: a visible 100→0 drop.
- Cards leaving the list do not move it. It tracks decisions, not list membership.

**Fix.** Animate only inside `ReviewLeftHeader`: `animateFloatAsState(progress.coerceIn(0f,1f), tween(400, FastOutSlowInEasing))`, or `snap()` under `LocalReducedMotion`. Do not animate the scan header: REV-PROGRESS removed its tween because the target moved every 16–40 ms. Decisions are seconds apart, so a tween is safe here. Optionally cross-fade the two headers at scan end (200 ms).
- Files: `ui/TagReviewCard.kt`, `ui/ReviewScaffold.kt` (pure `reviewProgressAnimationMillis(reducedMotion)`).
- JVM test: that function, and a source check that the scan header has no `animateFloatAsState`. The motion itself is device-only.
- Strings: none. Risk: low; the 100→0 drop needs an eyes-on check.

## F23 Remove the per-decision snackbar
**Current.** `showUndoSnack` (`ui/TagReviewScreen.kt:682`), called at L696, L1007 and L1013, sets `undoSnack`. The `LaunchedEffect(undoSnack)` (L790) shows "N changes applied" or "Kept as it was" with Undo. Got it already shows nothing (`undoSnackOutcome` returns null).

**Remove for single decisions:**
- `showUndoSnack` and its three callers.
- `keptMessage` (L578) and `ReviewUndoSnack.outcome`.
- The "turn into Kept" and "dismiss the undo snack" branches in `writeDecision`.
- The single-garment case of `undoEnabled` in the `SnackbarHost`.

**Keep:**
- `undoSnack` as the bulk snackbar only ("N garments updated" + Undo).
- The failure messages.
- `undoDecision`, `canUndoNow`, `reviewAgainEnabled`. `ReviewedRow` (L1137), `bulkRevert` and the bulk Undo use them.
- `undoSnackOutcome`, `reviewOutcomeOf`, `ReviewOutcome` and the strings `review_snack_applied` and `review_snack_kept`. Reviewed rows show them, and `TagReviewUndoTest:196-235` pins them in all three locales.

**Other changes:**
- Dismiss a standing bulk snackbar before showing a failure, as the revert failure does. Otherwise it queues behind 4 s.
- A single decision no longer replaces a bulk snackbar; its Undo stays valid per garment through `canUndoNow`.
- REV-DONE step 1 said the last Undo snackbar stays over the completion view. Now only the bulk one can.
- The finish guards (`doneFinishes`, `shouldFinishTagReview`) read the session, not the snackbar, so they are unchanged.

**User loses** the 4 s one-tap Undo. **Replaced by** "N reviewed ▾" (L895) → Reviewed rows → "Review again" (the same code path as Undo), and "Edit changes" on the completion view. Both are two taps.

Tests: `TagReviewUndoTest` tests pure functions and stays green. Add a source-contract test that `showUndoSnack(` is gone. Files: `ui/TagReviewScreen.kt`, the test. Strings: none removed. Risk: low.

## F24 Long-press on a chip proposes another tag
**Today.** A tap toggles `skipped` by change key (`TagReviewSession.toggle`, `ui/TagReviewSession.kt:75`). Chips use `toggleable` (`ui/ReviewChangeChips.kt:63`). The chooser (`TagReviewPickerDialog`, `TagReviewScreen.kt:1241`) is single-choice and opens at L823 only for a `needsChoice` card.

**Semantics: replace in place.** Add pure `ReviewChanges.replaceTag(key, newTagId)` (`suggestions/ReviewChanges.kt`) and `TagReviewSession.replaceChip(garmentId, key, newTagId)`. Because `changes` itself is rewritten, `deltaFor`, `primaryButtonFor`, `noTagsLeft`, `bulkPlan` and `commitDeltaFor` need no change.

| Chip | Long-press |
|---|---|
| `Add` (occasion add, or lone category add) | `Add(old)` → `Add(new)` at the same position (no re-sort). Drop the old key from `skipped`: picking means on. |
| `Swap(from,to)` | Replace `to` only; same `skipped` migration. |
| Chosen tag (`needsChoice`) | Existing `choose()`. "None" clears it. |
| `Remove`, locked or not | Nothing. Tap still toggles. |

- **Several add chips:** only the pressed chip changes. The picker offers `offeredTagIds` minus the tags in the other chips. It includes the original proposal, so the user can go back.
- **Session guard:** `newTag in card.offeredTagIds` and the card is Pending. That keeps writes inside the group, on a tag that exists and is not on the garment. `applyDelta` re-checks the category rule at write time.
- **Shown as** a normal green "+" chip. Use the `chosen=true` description path ("Set X") for TalkBack.
- **Gesture:** replace `toggleable` with `combinedClickable(onLongClick, onLongClickLabel)` plus `semantics { role = Switch; toggleableState }`, and a haptic. It is already used in `BrowseGrid.kt:477`.
- **Picker:** target becomes `(garmentId, key)`; add a `showNone` flag, hidden for Add and Swap (tap-to-skip does that).
- **Writes and Undo:** the delta carries the new tag, and `AppliedDelta.added` records it. Undo and Review again revert it through `revertDelta`. The card comes back with the replaced chip kept. Bulk uses replaced chips.
- **Pure tests** (`ReviewChangesTest`, `TagReviewSessionTest`):
  - replace of an Add, a Swap and the chosen tag;
  - `skipped` migration;
  - rejection of a tag outside the offered set or used by another chip;
  - decided cards untouched;
  - delta after replace;
  - replace → Apply → Undo restores the original tags;
  - bulk plan after replace;
  - a long-press on a Remove is a no-op.
- **Files:** `suggestions/ReviewChanges.kt`, `ui/TagReviewSession.kt`, `ui/ReviewChangeChips.kt`, `ui/TagReviewCard.kt`, `ui/TagReviewScreen.kt`, 3× `strings.xml` (long-click label: EN "Choose another tag", ES "Elegir otra etiqueta", DE "Anderes Tag wählen").
- **Independent review: YES.** It changes what a decision writes (REV-CARD BLOCKING list: write outside the group, double apply after Undo).
- **Unknown:** with the hint gone (F25) the gesture has no visible cue. Ask Manu about a small affordance, e.g. a trailing "⌄" on Add chips.

## F25 Remove "Tap a change to skip it…"
- The only use is `Text(stringResource(R.string.review_hint_changes))` in the pinned header (`ui/TagReviewScreen.kt:912-916`). Delete it.
- Delete `review_hint_changes` from the three `strings.xml` (L908 in each).
- Remove `"review_hint_changes"` from the key list in `TagReviewCardModelTest.kt:320`.
- The colour review uses a separate string, `color_review_tap_hint` (`ColorReviewScreen.kt:272`, strings L896). It is not shared, so keep it (confirm with Manu).
- Risk: none.

## F26 Completion view and transition
**When the overlay shows today.** `finishTagReview` (`ui/RobiaApp.kt:1500-1508`) sets `reviewComplete = reviewCompleteText(Tags, writtenGarments.size)`, which is null for 0 (`ReviewCompleteOverlay.kt:49`).
- Done on the completion view calls `onFinish(emptyList())` (`TagReviewScreen.kt:1080`): count 0, **no overlay**. REV-DONE designed that.
- Done with the list up calls `onFinish(writtenGarments)`, so ≥1 written garment gives the overlay (check + sparks, 1.6 s). That happens when Done is the bottom bar but `completionShown` is false:
  - cards left that cannot be bulk-applied (unchosen choosers, only kept cards);
  - after "Edit changes" when nothing is pending (`editState` ≠ Off).
- That is the "sometimes". The colour review always shows it (`commitColorReview`, `RobiaApp.kt:1561`).

**Why it feels abrupt.**
- `completionShown` (L808) flips as soon as the last write lands. The grid is swapped for `TagReviewCompletion` (L918) while the last card is still mid-exit.
- The whole pinned header stays above it (L875-916): the "0 left" title, the progress bar, "N reviewed", the context lines and the hint. That is the "too much information".
- `TagReviewCompletion` (`ui/TagReviewComplete.kt:107`) has a static check, with no sparks or animation.

**Option A (recommended): the completion view takes the overlay look.**
1. Hide the pinned header when the completion is shown (`AnimatedVisibility`, fade + shrink, 250 ms). "Edit changes" still reaches Reviewed.
2. Wait for the last card's exit: `completionReady = completionShown && no visible card left`, a pure function of `hiddenIds`. Re-check the `LaunchedEffect(completionShown)` at L810, which pre-hides all candidates and would defeat the wait.
3. Extract the overlay's badge (72 dp circle, check scale 0→1 over 300 ms, `SparkleBurst`) into a shared `ReviewCompleteBadge` used by both. Fade the title, tally, thumbnails and buttons in staggered (250 ms), and cross-fade grid → completion (300 ms). Reduced motion uses `snap()`; `SparkleBurst` already draws nothing.
4. Done keeps `onFinish(emptyList())` immediately, so there is no delayed finish and no new race. An optional 200 ms fade-out needs `finished` guards on Edit changes, bulk Undo and Review again.

**Guards kept:**
- `doneEnabled && revertingIds.isEmpty()` and `doneFinishes` (L1076-1083) are unchanged.
- `showsCompletion` and its tests are unchanged.
- A bulk Undo that returns cards to Pending hides the completion, as now.

**Option B (not recommended).** Done always passes `writtenGarments`. The overlay then shows after every Done, but it repeats the check and adds 1.6 s after the user has seen the summary.

**Files:** `ui/TagReviewComplete.kt`, `ui/ReviewCompleteOverlay.kt`, `ui/TagReviewScreen.kt`. Strings: none. JVM tests: the `completionReady` function, plus the existing complete tests unchanged. Risk: medium (layout swap, the `hiddenIds` effect).
**Unknown:** whether Manu wants the sparks on entering the completion (A) or after Done (B).

## Recommended task split
| Id | Items | Files | Predecessors | Indep. review |
|---|---|---|---|---|
| R3F-1 | F21, F25 | `Theme.kt`, `Color.kt`, `TagReviewScreen.kt`, `ColorReviewScreen.kt`, 3× `strings.xml`, `RobiaColorSchemeTest`, `TagReviewCardModelTest` | — | no |
| R3F-2 | F23 | `TagReviewScreen.kt`, `TagReviewUndoTest` / contract test | R3F-1 | no |
| R3F-3 | F22 | `TagReviewCard.kt`, `ReviewScaffold.kt`, new progress test | — (parallel to R3F-1) | no |
| R3F-4 | F26 | `TagReviewComplete.kt`, `ReviewCompleteOverlay.kt`, `TagReviewScreen.kt`, `TagReviewCompleteTest` | R3F-2 | no |
| R3F-5 | F24 | `ReviewChanges.kt`, `TagReviewSession.kt`, `ReviewChangeChips.kt`, `TagReviewCard.kt`, `TagReviewScreen.kt`, 3× `strings.xml`, tests | R3F-3, R3F-4, R3F-1 | **yes** |

Serial on `TagReviewScreen.kt`: R3F-1 → R3F-2 → R3F-4 → R3F-5. On `TagReviewCard.kt`: R3F-3 → R3F-5. On `strings.xml`: R3F-1 → R3F-5.
