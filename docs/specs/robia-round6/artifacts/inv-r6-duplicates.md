# DU0: why the "Possible duplicates" banner does not always appear

Each cause below was read in the code. Where I could not run the app, I say so.

## 1. Another prompt hides the banner: partly confirmed

- **Where:** `RobiaApp.kt:2007-2011`. `duplicatePromptVisible` sets `duplicateBannerCount` to 0 whenever `browseAttentions.isNotEmpty()` (line 2010). The banner is drawn only when the count is above 0 (`BrowseGrid.kt:338`).
- **CERTAIN:** the gate exists. The groups are kept in `DuplicateSession`, but the banner is not drawn.
- **Which cards persist:** the cards come from `WardrobeSyncOutboxProcessor.kt:1528-1538`.
  - **HYPOTHESIS:** `SignInRequired` (when `authBlocked`), `DriveFull`, `PhotoUploadFailed`, `PhotoUnrecoverable` and `AccountMismatch` stay until the user fixes them, so the banner stays hidden for as long as one is showing.
  - **Not confirmed:** `SignInRequired` with Drive never set up. It needs `pendingOperationCount > 0` while disconnected, and I could not confirm that happens (see `RobiaApp.kt:1216`, "an unconfigured sync makes this a no-op").
- **Smallest fix:** drop `browseAttentions.isNotEmpty()` from `duplicatePromptVisible` and keep only the transient prompts. The attention card is already in the same grid as the banner, so the two can show together.
- **Fits DU1's Files?** Yes, it is in `RobiaApp.kt`.
- **Risk:** removing it changes how Browse looks for other features. The plan's review rule (h) says a DU0 CERTAIN fix that changes another feature's behaviour is BLOCKING. Since the card persistence is only a HYPOTHESIS, I recommend **not** doing this in DU1. List it as a follow-up and let the user decide whether the banner may share the screen with a sync card.

## 2. The automatic scan waits for the backfill: only the no-model case holds

- **Where:** `RobiaApp.kt:1185-1188`, `EmbeddingBackfill.kt:79-99`.
- **Failed garments do not keep `remaining` above 0:** `pendingTasks` (`EmbeddingBackfill.kt:111-114`) filters out any garment with 2 or more failures. A failing garment drops out of `remaining`, the pass ends with an empty list, and `settled` becomes true. The planning hypothesis is **refuted**. The skipped garment simply has no vector, so it never takes part in a scan.
- **CERTAIN, by design:** with no model, or a model that is not Ready, `runUntilIdle` never enters its loop (`EmbeddingBackfill.kt:82`). `settled` stays false and no automatic scan runs. The garments have no vectors either, so there is nothing to compare. This is not a bug. DU1's row is hidden in that state, so the case is covered.
- **Fix:** none needed.

## 3. A stale or missing stored hash drops the garment: CERTAIN in code, HYPOTHESIS for how often

- **Where:** `DuplicateScan.kt:556`. `eligibleGarments` drops any garment whose current photo hash differs from `imageInputHash`. The backfill never repairs this. `EmbeddingBackfill.kt:121-123` creates an image task only when the garment has no stored IMAGE row (`garment.id !in storedImageIds`). It never compares hashes.
- **Effect:** a garment whose photo was replaced without a fresh vector keeps its old row and is excluded from every scan for good. Vectors are normally written at save time (`RobiaApp.kt:3932-3934`), and only when `capture.photoUri == item.photoUri`. So the gap appears when a save happens without a matching capture, for example the model was not Ready at that moment. The same applies after a restore that brought a vector with a different hash.
- **I could not reproduce this without a device.**
- **Smallest fix:** let the backfill treat a stored row as stale when the hash no longer matches. That needs encoding the photo, which is costly, and it lives in `EmbeddingBackfill.kt` and the save path.
- **Fits DU1's Files?** **No.** Report it as a follow-up.
- **What DU1 can do:** the planned `duplicate_check_not_ready` line already counts these garments. It must count "no vector **or** stale vector", not just "no vector".

## 4. Threshold, same-category rule and colour rule: report only

- **Where:** `T_DUP = 0.96` (`DuplicateScan.kt:65`), compared at `DuplicateScan.kt:192`.
- **CERTAIN, in code:**
  - Two separate photos of the same garment can score below 0.96. That is expected and **not a bug**.
  - A garment **without a category tag** can never be "Very similar". `blocksByCategory` (`DuplicateScan.kt:260-261`) drops it, and `scanHashCandidates` (`DuplicateScan.kt:468-472`) does not hash it.
  - Pairs whose primary colours differ are also rejected (`DuplicateScan.kt:205-206`).
- **Only the "Same photo" tier works for an uncategorised garment.**
- **Fix:** none. The plan says not to change `T_DUP` or the colour rule. DU1's copy should not promise that every pair is found.

## 5. A scan with missing vectors records a Success signature: CERTAIN, the strongest cause

- **Where:**
  - `DuplicateScan.kt:345-349`: `nextDuplicateScanSignature` records the signature on any `Success`.
  - `DuplicateScan.kt:391-395`: `collectDuplicateScan` returns `Success` even when garments have no vector. It simply leaves them out.
  - `RobiaApp.kt:1199`: records it.
  - `DuplicateScan.kt:100-103`: the signature lives only in memory.
- **The signature ignores vectors:** it is built from garment ids and photo URIs only (`DuplicateScan.kt:336-339`). A vector arriving later cannot change it.
- **Why a gap exists in a normal session:**
  - The backfill is resumed in only three places (`RobiaApplication.kt:186`, `:196`, `:207`): after a restore, on app start, and when the model becomes Ready. Saving a garment does not resume it. Once a pass has finished, nothing starts another until the next foreground.
  - `BackfillProgress` stays `settled = true, remaining = 0` after that pass (`EmbeddingBackfill.kt:86-90`). The gate at `RobiaApp.kt:1187` therefore passes immediately for a new garment whose vector does not exist yet.
- **The failing sequence:**
  1. The user adds a garment, and its vector is missing.
  2. Browse scans straight away and records the new signature as Success.
  3. The backfill later computes the vector. `remaining` goes to 1 and back to 0, which re-runs the effect.
  4. The signature is unchanged, so `duplicateScanDue` returns false and the vector is never compared until the app process restarts.
- **I could not run this sequence on a device.** It follows from the call sites above.
- **Smallest fix:** in `scanDuplicates` / `collectDuplicateScan`, report the scan as incomplete when an active garment with a photo has no vector. Add that outcome next to Success and Failed, and have `nextDuplicateScanSignature` keep the previous signature for it. The effect already re-runs when `remaining` returns to 0, so the scan then repeats once the vectors exist. Groups found so far may still be published.
- **Fits DU1's Files?** **Yes.** It touches `DuplicateScan.kt` and the duplicate-scan region of `RobiaApp.kt`. It changes only the duplicate feature, so rule (h) is not triggered.
- **Related, outside DU1's Files:** adding `embeddingBackfill.resume()` after a garment save would close the gap at its source.
- **Linked to DU1:** a forced scan can call `resume()` first. That is in `RobiaApp.kt` and fits.

## Summary for DU1

| # | Cause | Status | Fits DU1's Files? | Action |
|---|-------|--------|-------------------|--------|
| 5 | Incomplete scan records Success | CERTAIN | Yes | Fix in DU1. |
| 1 | Attention card hides the banner | gate CERTAIN, persistence HYPOTHESIS | Yes | Follow-up (rule h). |
| 3 | Stale vector never repaired | CERTAIN in code, frequency HYPOTHESIS | No | Follow-up. `not_ready` must count stale vectors. |
| 4 | Threshold, category and colour rules | CERTAIN, by design | n/a | No change. |
| 2 | Failed garments block `remaining` | refuted (only no model blocks, by design) | n/a | None. |
