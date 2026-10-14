# TASK-E07 — Model download UX

## Goal
Users who never touch developer mode must be able to get the model: a one-time "Download now / Later" prompt after updating, a permanent "Smart suggestions" section in Settings (download, progress, waiting for Wi-Fi, use mobile data with confirmation, delete), with clear error messages.

## Read first
Mockup `mockups/model-download.html` (all copy in its table). `ModelStore` states (E01). Settings screen in `ui/RobiaApp.kt`.

## Steps
1. `ui/ModelDownloadUi.kt`: `ModelPromptDialog`, `MobileDataConfirmDialog`, `SmartSuggestionsSettingsSection(state, onDownload, onUseMobileData, onCancel, onDelete, onRetry)`.
2. Prompt policy as a pure function `shouldShowModelPrompt(promptShown: Boolean, state: ModelState): Boolean` = `!promptShown && state is NotDownloaded`. Set `model_prompt_shown = true` on either button.
3. Show the prompt once from `RobiaShell` (after the existing cloud-setup prompt logic, never both at the same time).
4. Settings section placed above "Language" (see mockup). Remove the dev-only "Download model (dev)" row from E01 (benchmark row stays).
5. Strings from the mockup table, 3 locales.

## Tests
`ModelPromptPolicyTest` (V-E07).

## Done when
CI green; V-E07; visual in Z01.
