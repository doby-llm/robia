# TASK-DUP-TRIGGER — Scan whenever the garments change, record only success

## Goal
F18 (REQ-FB-18 b): Manu added two identical garments from the same photo; the calibration screen scored them 1.0 but Browse showed no "review duplicates" prompt (it did once for another garment). Cause: the Browse scan runs only when `duplicateScanDue` says so (first run of the process, or 5 new garments, or a changed count and 24 h since the last), and `DuplicateScanHistory.last` is in-process only, so two new garments on the same day never scan; and `last` is recorded even when `scanDuplicates` swallowed an exception and returned an empty list. After DUP-FAST a scan is cheap, so the rule can be: scan when something changed.

## Read first
`R3-DEC-051`; `ux2.md` §F18 (b); `suggestions/DuplicateScan.kt` (`duplicateScanDue`, `DuplicateScanRecord`, `DuplicateScanHistory`, `scanDuplicates`, `DuplicateSession`); `ui/RobiaApp.kt` the duplicate-scan `LaunchedEffect` (~1122-1130) and the `keepActive` effect after it; `DuplicateScanTest` (~149-153).

## Files
`suggestions/DuplicateScan.kt`; `ui/RobiaApp.kt` (the duplicate-scan `LaunchedEffect` block only); `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`.

## Steps
1. `duplicateScanSignature(items: List<ClothingItem>): String` (or a `Long` hash): over the active (non-archived) garments, sorted by id, of `id + photoUri`. Order-insensitive; unaffected by name, tags or notes. Check that a photo replacement always produces a new `photoUri` (new file name); if some path rewrites the same URI in place, report it and add `updatedAtEpochMillis` to the signature for that case only.
2. `duplicateScanDue(lastSignature: String?, signature: String) = lastSignature != signature`. Delete `DUPLICATE_RESCAN_NEW_GARMENTS`, the 24 h constant and `DuplicateScanRecord` if nothing else uses them; `DuplicateScanHistory` holds `lastSignature` (still in-process).
3. `scanDuplicates` returns a `DuplicateScanOutcome` (`Success(groups)` / `Failed`) instead of an empty list for an exception; cancellation still propagates; log one line outside DEBUG for a failure (class and message only, no photo data). A pure helper decides the new history: `Success` → the signature, `Failed` → the previous value.
4. `RobiaApp.kt`: the effect is keyed on the signature (computed with `remember(clothingItems)`) plus the existing keys; on `Success` call `DuplicateSession.replaceGroups` and record the signature; on `Failed` change nothing (groups stay, history unchanged, the next key change or Browse visit retries). Conditions unchanged: Browse, wardrobe loaded, backfill settled with nothing remaining.
5. Rewrite the `duplicateScanDue` assertions in `DuplicateScanTest` with equal strength (V-DUP-TRIGGER-1).
6. Note for the receipt: tier 1 needs equal stored `input_hash`; two garments from one photo whose cutouts differ slightly have different hashes even at cosine 1.0, so the prompt for them needs DUP2's cosine tier. That is expected and not a bug of this task.

## Tests
V-DUP-TRIGGER-1, V-DUP-TRIGGER-2 (CI); V-DUP-TRIGGER-3 (REV); V-DUP-TRIGGER-D (Manu).

## Done when
V-DUP-TRIGGER-1..3 pass, V-DUP-TRIGGER-D is ready for Manu; CI green.

## Do not
Persist the history, change scan cost or the cosine tier, touch other `RobiaApp.kt` code, or weaken the replaced test.
