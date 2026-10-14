# TASK-S05 — Sync attention UX

## Goal
Replace the useless "Sync failed — retry/details" text with clear states: per-garment labels (Synced / Pending upload / Uploading…) and, only when the user must act, a card with the cause in plain words and buttons that work.

## Read first
Mockup `mockups/sync-states.html` (layout + copy), tasks.md S05 (string table), `ui/RobiaApp.kt`: `garmentCloudStatusLabel`, the icon/tint helpers next to it, `cloudSetupSummaryRes`, `itemStatusLabelRes`, Browse screen top area, `ItemDetailScreen`. Spec TS§4.7.

## Steps
1. Add all strings from tasks.md S05 + the mockup table in EN/ES/DE.
2. `ui/SyncAttentionCard.kt`: `@Composable fun SyncAttentionCard(attention: SyncAttention, onAction: (SyncAttentionAction) -> Unit, showDetails: Boolean, onDetails: () -> Unit)`; container color `errorContainer` for SignIn/DriveFull, surface card for photo issues (see mockup). Pure mapping function `internal fun SyncAttentionKind.presentation(): AttentionPresentation(titleRes, bodyRes, actions: List<SyncAttentionAction>)` — test this.
3. Browse: show the first attention card above the grid (only if list non-empty). Item detail: card for attentions containing that garment id; otherwise a small status row ("Pending upload" + help text from the mockup).
4. Wire actions: SignIn → `onRequestCloudSetup`; Retry → `requestSync`; Open storage → `Intent(ACTION_VIEW, Uri.parse("https://one.google.com/storage"))`; Retry photo → existing RetryRestoredPhoto work; Keep without photo → new gateway call clearing the guard; Conflict → dialog with 3 buttons; Merge → dialog per mockup.
5. Labels: replace `FailedRetryable` text with "Pending upload"; remove string `garment_sync_failed_retryable` from 3 locales.
6. "Details" visible only when developer mode is enabled → `RobiaRoute.DeveloperSyncLog`.

## Tests
`SyncAttentionMappingTest` (V-S09): every kind → title, body, ≥ 1 action. Grep proves `garment_sync_failed_retryable` is gone.

## Done when
CI green; V-S09; visual check in Z01 (V-Z10).
