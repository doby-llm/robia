# TASK-B02 — Batch screen as a list + ETA

## Goal
Manu (revision 2): the batch grid repeats each tile's status chip as text below the photo, the long sentence "Processing 12 items… Save becomes available after processing finishes. Accepted 0. Review 0. Failed 0. Interrupted 0." appears both in the header and in the bottom bar, and there is no time estimate. He wants one row per garment showing the detected category and occasions (a grid would become irregular), no duplicated text, and an approximate ETA next to the progress bar (REQ-BA-02, REQ-BA-03, UX-DEC-012; mockup `batch.html`).

## Read first
TS§B02; `ui/BatchAddClothingScreen.kt`: `BatchAddClothingScreen` (~111-260: counters, `saveHelper`, grid, bottom `Surface`), `BatchProgressHeader`, `BatchProgressBar`, `BatchDraftTile` (~300-370), `BatchStatusBadge` (keep unchanged: pinned by `batchStatusBadge_labelsAcceptedNeedsReviewWithVisualStatus`), `BatchPhotoPreview` (U07 sweep); `TagLocalization.kt` (how a tag label is localized); `RegressionSourceContractTest` `batchSave_requiresExplicitReviewAcceptanceAndRevalidatesAtSaveBoundary` (~11-44: it asserts `batch_helper_summary` exists in strings.xml) and `batchSave_keepsFailureActionsAndDoesNotMakeEditingFailedItemsSaveable` (~45-58: Retry/Discard `TextButton`s must stay).

## Steps
1. New `ui/BatchEta.kt` (pure): `class BatchEtaEstimator(private val clock: () -> Long)`: `fun onTerminal()` records a completion time; `fun estimate(remaining: Int): BatchEta?` = null until 2 completions; raw = mean of the last ≤ 4 gaps between completions × remaining; smoothing: may drop freely, may rise at most 20 % over the previously shown value; `sealed interface BatchEta { data class Seconds(val s: Int); data class Minutes(val m: Int); data object AlmostDone }` (< 10 s → AlmostDone; < 90 s → Seconds rounded up to 10; else Minutes rounded). Also `internal fun batchRowSummary(draft, availableTags, localize: (GarmentTag) -> String, collator): String` (category · occasions alphabetical joined ", "; "" while Queued/Processing or when nothing) and `internal fun batchAttentionLine(needsReview, failed, interrupted): List<Pair<Int, Int>>` (plural res + count, empty when all 0).
2. Screen: replace `LazyVerticalGrid` with `LazyColumn` (contentPadding 16/24/16/bottom bar height + 16, spacing 10 dp). Header item: title, `batch_processed_count` "%1$d of %2$d processed", `BatchProgressBar` with the ETA text at its end (`labelMedium`, onSurfaceVariant; hidden when null; the estimator lives in `remember` and is fed via `LaunchedEffect(processedCount)`). Remove the helper text from the header.
3. `BatchDraftRow(draft, position, totalCount, summary, onClick, onRetry, onDiscard)`: `Card` row, min height 88 dp: thumbnail 72×88 dp (`BatchPhotoPreview`, clip 12 dp), then a column: line 1 = "%1$d of %2$d" (`labelMedium`) + `Spacer(weight)` + `BatchStatusBadge` (unchanged); line 2 = `summary` (`bodyMedium`, maxLines 2, ellipsis) — or for Failed/NeedsReview-not-accepted the `errorMessage` (error colour for Failed) and the existing Retry / Discard `TextButton`s (same lambdas, same `isRetryable` rule + SG1's NeedsReview retry). Same semantics/contentDescription strings as the tile; Queued rows at alpha 0.62 as today. Delete `BatchDraftTile` and the status text under it.
4. Bottom bar: only the Save button (unchanged code) and, above it, the attention line joined " · " when non-empty (e.g. "2 need review · 1 failed"), plus `saveStatusMessage` as today. `batch_helper_active_summary` is no longer used: delete it from the 3 locales. `batch_helper_summary`: delete it too and replace the assertion `strings.contains("name=\"batch_helper_summary\"")` with one for `batch_attention_review` (rule 11, same test, nothing else changed).
5. Strings (all locales): `batch_processed_count` "%1$d of %2$d processed", `batch_row_position` "%1$d of %2$d", `batch_eta_seconds` "About %1$d s left", `batch_eta_minutes` "About %1$d min left", `batch_eta_almost_done` "Almost done", plurals `batch_attention_review` "%1$d needs review" / "%1$d need review", `batch_attention_failed` "%1$d failed", `batch_attention_interrupted` "%1$d interrupted".

## Tests
`BatchEtaTest` (fake clock: null before 2; mean of last 4 gaps; +20 % cap; AlmostDone; minutes) and `BatchRowSummaryTest` (V-B02-1, V-B02-2).

## Done when
V-B02-1..3, CI green; device row V-Z2-22.

## Do not
Change processing (B01), `BatchStatusBadge`, save/accept rules, the draft editor, or `ui/RobiaApp.kt`.
