**Scope:** read-only investigation of `main` (5505ed6). Nothing was edited, built or run. "Certain" means read from code; "hypothesis" means inferred and needs a device log or test to confirm.

## Premise check

- **(1) Discard button:** premise matches. Only Accept exists (`TagReviewScreen.kt:489-492`, `ColorReviewScreen.kt:290-292`). The existing "Discard" is the back-arrow whole-change undo (`RobiaApp.kt:1285-1295`, `1351-1357`), so the name collides.
- **(1) "Accept all (N)" counts open cards only:** premise does not match. `TagReviewScreen.kt:359,512-513` counts accepted cards too (they stay in `candidates` until commit). Colour uses `acceptableCount + acceptedIds.size` (`ColorReviewScreen.kt:314`).
- **(2) "Not sure if a review opens with 0 garments":** it does. No guard exists on any entry point (wired directly at `RobiaApp.kt:1885-1890`).
- **(3) "write() is awaited before the prompt":** premise does not match. `write()` only calls `launchReviewWrite`, which returns immediately (`RobiaApp.kt:418-420`, `1248`). Only the snapshot read is awaited (`1239-1240`).
- **(3) "restoreDefaultTags causes the delay":** does not match. It runs inside the launched job (`RobiaApp.kt:552-561`) and is not awaited.
- **(3) "Scan/model load before the prompt":** does not match. `prepareTagReviewScan` runs after the user taps Review (`TagReviewScreen.kt:340-341`).
- **(4) "Manu said Z26 PASS for Dresses":** partly. The Z26 checklist row covers Dresses restore plus description edit (`device-checklist.md:52`). Manu's Z26 comments in feedback 5 are about the description-edit UI only, so the Dresses result is implied, not stated.

## (1) Discard per card

**Facts that shape the semantics**
- Save-first is real for tags. `deleteEditableTagAndReferences` removes the garment refs in the same transaction (`Dao.kt:502-508`). A deleted tag is already gone from garments before the review. The comment at `TagReviewScreen.kt:312-313` ("a deleted tag is still the current value of a garment") is stale.
- Colours are different. Deleting a colour removes only the palette row (`LocalRepositories.kt:201-205`). Garments keep the dangling id until commit or "Not now" (`RobiaApp.kt:1297-1313`).
- A restore-defaults review writes nothing until commit (`requiresCommitOnSave`, `RobiaApp.kt:1219-1228`).
- Tag commit rebuilds from `afterTags` filtered by `proposed` (`TagReviewScreen.kt:360-369`). A discarded Suggested card still gets committed (see the bug below).

**Closed behaviour table (proposed)**

| Change | Accept | Discard (per card) |
|---|---|---|
| Added tag (category or occasion) | Apply proposal | Garment unchanged, no write (your rule) |
| Renamed / described tag | Apply proposal (may replace category) | Garment unchanged |
| Restored default | Apply proposal | Garment unchanged. **Never write from the snapshot**: merges repoint refs to built-in ids (`LocalRepositories.kt:220-221`), the snapshot still has the old ids |
| Deleted occasion | Proposal = before − deleted (adds nothing) | Same garment tags as Accept, so no write needed |
| Deleted category, Suggested | Assign winner | No write. Garment stays with no category (your rule) |
| Deleted category, ChooseManually | User picks; unresolved cards block auto-close | No write. Garment stays with no category |
| Colour Added / Edited | Apply proposal | Garment unchanged |
| Colour Deleted | Clear the deleted slot (`clothingItemsWithoutDeletedColor`) | Clear the deleted slot on that garment (the same clear, applied per garment) |
| Colour RestoredDefault | Commit writes palette + garment | Garment unchanged; **palette restore still applies** (see the decision below) |

**Inconsistencies to fix**
1. **Discarded cards get committed.** `tagReviewCommitCandidates` keeps every `Suggested` candidate (`TagReviewScreen.kt:111-116`). Auto-commit then fires with discarded cards counted as closed, so a discarded Suggested card is applied. Fix: exclude discarded ids here and in colour `commitItems` (`ColorReviewScreen.kt:210-212`).
2. **Colour RestoredDefault leaves dangling refs.** The restore deletes palette rows (`RobiaApp.kt:811-816`), but the clear only covers garments in `updatedItems` (`:1320`). A discarded garment that references a removed colour keeps the dangling id. Fix: clear every removed colour id on all active garments not in `updatedItems`.
3. **Deleted colour discard conflicts with the code comment.** `colorReviewUndoPlan` says a discard never clears deleted colours (`ColorReviewScreen.kt:599-603`). That is the back-arrow path. Per-card Discard should clear the slot on that garment, which the existing commit clear already does once the discarded garment is left out of `updatedItems`.
4. **Auto-close vs back arrow for colour RestoredDefault.** Auto-close with only discards: the palette restore still applies (commit runs). Back arrow: nothing applies (`colorReviewUndoPlan` returns empty). Your rule says "close without committing anything." I recommend keeping the palette restore on auto-close, since the user already confirmed the restore in Manage. This is a deviation from the literal rule: please confirm.
5. **Back arrow after per-card discards.** `tagReviewUndoPlan` restores deleted tags on garments whose cards were discarded (`restoredGarmentTags`). Recommend: back arrow undoes the whole change, and discards only take effect on commit. Say so in the dialog text.
6. **Discarded + manual picker.** A picker can't be open on a discarded card (modal). Discard should drop `edits[id]`. Accept is final today, so Discard only applies to Open cards.
7. **Stale-snapshot overwrite (hypothesis, low-moderate).** Commit writes the full snapshot row (`TagReviewScreen.kt:364`, `LocalRepositories.kt:70-74`). A sync pull during the review gets reverted. Fix: re-read with `observeItem(id).first()` (`LocalRepositories.kt:37-38`, no Dao change) and copy only tags or colour fields.

**Location and transition**
- State: add `discardedIds` next to `acceptedIds` and `hiddenIds` (`TagReviewScreen.kt:322-324`, `ColorReviewScreen.kt:125-128`). Keep `hiddenIds` shared as the "closed" set.
- Transition: reuse `ReviewAcceptedCard` (`ReviewScaffold.kt:91-114`) with `resolved = accepted || discarded`. It has the same 200 ms + 250 ms fade/shrink, so yes, the same discreet hide.
- **Accept all (N):** count open cards with a concrete target only. If N = 0 and accepted > 0, show "Save N accepted." Else "Done."
- **Auto-close:** all candidates accepted or discarded, not scanning, no picker open, once only (`shouldAutoCommit`). If nothing is accepted, no garment write happens (tags already do this at `RobiaApp.kt:1269-1274`).

## (2) 0-garment guard

Active garments are the right count: the scan already ignores archived ones (`TagReviewScreen.kt:319`), and `observeActiveItems` is `is_archived = 0` (`Dao.kt:17-19`). Archived garments stay in the snapshot for undo.

| Entry | Today | Guard location | User-visible result after fix |
|---|---|---|---|
| Tag save / rename / description | Prompt set unconditionally (`RobiaApp.kt:1249`); screen shows empty card (`TagReviewScreen.kt:464-471`) | `offerTagReviewAfterWrite`, `RobiaApp.kt:1249`: set `pendingTagReview` only if `garments.any { !it.isArchived }` | No prompt. Save still happens |
| Tag delete (incl. occasion/category) | Same | Same line | No prompt |
| Restore defaults (tags) | Same | Same line | No prompt |
| Colour save | Prompt unconditional (`RobiaApp.kt:1203`) | Check `clothingItems` for `isEligibleForColorReview` (`ColorReviewScreen.kt:757`, make internal). If none, skip prompt | No prompt |
| Colour delete | Same (`RobiaApp.kt:1216`) | If none eligible, call `declineColorReviewProposal` directly (`RobiaApp.kt:1309`) instead of skipping, so inactive/no-photo refs still get cleared | No prompt; deleted colour still cleared from garments |
| Colour restore defaults | Opens screen directly (`RobiaApp.kt:1229`) | If none eligible, `commitColorReview(changeSet, emptyList())` (palette still applied) | No screen; restore still applied |

No other entry point exists (`RobiaRoute.TagReview`/`ColorReview` are only reached from these paths).

## (3) Delayed popup

**Chain before the prompt (certain, from code)**
1. Confirm dialog closes and calls `onRestoreDefaultTags` (`ManageTagsScreen.kt:290-300`).
2. `offerTagReviewAfterWrite` runs on `reviewScope`: `readGarmentsForReview()` → `afterPreviousWrite(lastReviewWrite)` → **`join()` the previous review write** → `getAllItemsForSync()` (`RobiaApp.kt:562-564`, `5367-5370`, `Dao.kt:25-27`).
3. `write()` launches `restoreDefaultTags` in the background (`RobiaApp.kt:1248`, `552-561`).
4. `pendingTagReview` is set (`:1249`).

The restore itself never delays the prompt. The only wait is step 2's join. It is long when the previous review write (tag/colour save or commit, `onSaveItems` at `:514-521`) is still running its `syncGateway.enqueue`. That enqueue runs `runCycle` inline under the sync mutex (`WardrobeSyncOutboxProcessor.kt:243-264`, `726-732`), and it can include Drive HTTP calls. The usual sequence is "commit a review → go to Manage → Default," which matches Manu's case.

**Measurement (estimates, not measured):** the Room read of a few hundred rows is about 10–100 ms. An idle cycle is tens of ms. A cycle with pending Drive uploads is about 0.3–3 s per request, which fits the 2–3 s. Confirm with logcat: add `Log.d` timings around the join and the read at `RobiaApp.kt:1239`.

**Proposed fix (no sync/ or Dao change)**
- Track only the DB part of each write in `lastReviewWrite` (`RobiaApp.kt:417-420`). Launch `enqueue` on `app.appScope` after the DB write, not joined. The sync ops carry ids (`UpsertItem(id)`, `UpsertTags(ids)`), so the cycle reads current rows and detaching is safe. Do this at the call site, not in `WardrobeSyncOutboxProcessor.enqueue`.
- Keep the invariant: `readGarmentsForReview()` then `write()`, in one coroutine, both awaited in that order. The read is milliseconds, so no in-memory snapshot is needed.
- Don't use `withTimeoutOrNull` for the archived read. A timeout would drop archived garments and break undo.
- Other paths with the same join: `onUndoReview` (`RobiaApp.kt:565-569`) for cancel-prompt and discard; commits (`onSaveItems`, `onCommitMainColorChange`); every tag/colour write. Not delayed: colour save/delete prompts (`:1189-1217`, no join); the colour restore route (`:1229`). Also a second tag action within the window is silently dropped (`canStartTagReviewAction`, `:1235`).

## (4) Dresses restore

Code path is in place: `reviewScanLabelText` uses the bare name for touched tags with a blank description (`TagReviewScreen.kt:123-128`). Restored Dresses has no stored description (`DefaultTags.kt:16`), so the branch is taken. Without it, `labelTextFor` would use the default description (`EmbeddingRecipe.kt:64-71`). No problem is visible. Only the scan log line can confirm the result. No action needed.

## Fixes, in order

1. **Detach enqueue from the join** (`RobiaApp.kt:417-420`, `513-605`, `1239-1248`, `565-594`). Test: a suspending fake gateway; assert `pendingTagReview` is set before `enqueue` returns. Extract `offerReview` to make it testable.
2. **0-garment guards** (`RobiaApp.kt:1203`, `1216`, `1229`, `1249`). Pure `hasReviewableGarments(items, kind)`. Tests in `ReviewPlannerTest` and `ColorReviewRuleTest`.
3. **Discard state and filters** (`TagReviewScreen.kt:111-116`, `322-324`, `359`, `473-494`, `512-513`; `ColorReviewScreen.kt:125-128`, `210-212`, `312-318`). Pure `openAcceptableCount` and `commitCandidates` excluding discarded. Tests in `TagReviewAcceptAllTest` and new `shouldAutoCommit` cases.
4. **Colour RestoredDefault dangling-ref clear** (`RobiaApp.kt:1297-1320`, `811-816`). Test: a garment referencing a removed colour, excluded from `updatedItems`, gets cleared.
5. **Commit re-reads the current row** (`TagReviewScreen.kt:360-369`; `LocalRepositories.kt:37-38`). Medium priority.
6. **Timing log** around `RobiaApp.kt:1239` (join vs read).
7. Optional: rename the per-card label so it doesn't collide with the back arrow's "Discard changes."

**No change needed in `sync/` or `Dao`.** Fixes 1, 4 and 5 are in `RobiaApp.kt`, the review screens and `LocalRepositories.kt`.

**Decisions for you:** (a) colour RestoredDefault on auto-close: keep the palette restore (recommended) or drop it; (b) back arrow after per-card discards: undo everything (recommended); (c) Accept all counts open cards only (as above).
