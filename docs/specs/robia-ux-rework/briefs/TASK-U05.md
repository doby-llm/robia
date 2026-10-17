# TASK-U05 — Settings rework

## Goal
Settings adds one inline row per language and mixes card styles (R12). Rebuild it as grouped navigation rows with sub-screens (mockup `settings.html`), with no change to any Drive or model behaviour.

## Read first
TS§5; `ui/SettingsScreen.kt` (after U00): `SettingsScreen`, `SettingsSection`, `LanguageSettingsScreen`, `DeveloperCloudSyncCard`, `LanguagePreference.labelRes`; `ui/ModelDownloadUi.kt` `SmartSuggestionsSettingsSection`; `RobiaRoute` and `RobiaNavHost` in `ui/RobiaApp.kt` (`LanguageSettings` route exists at ~285); `handleSettingsClick` (developer unlock by tapping the Settings nav item 7 times — unchanged).

## Steps
1. Inventory first: list in the receipt every row/button/dialog trigger currently in `SettingsScreen` and where it goes (table "old → new location"). Nothing may disappear.
2. `SettingsNavRow(icon, title, subtitle?, value?, trailing: @Composable (() -> Unit)? = chevron, onClick?)` + `SettingsGroup(label) { rows }` (Card `surfaceContainerLowest`, 16 dp, 1 dp dividers).
3. Root: groups Smart features (Smart suggestions → `SmartSuggestionsSettings` sub-screen that renders the existing `SmartSuggestionsSettingsSection` unchanged), Backup (Google Drive → `DriveSettingsScreen`; value On/Off/Not connected from existing state), General (Language → picker, value = current label; Haptic feedback switch → `setHapticsEnabled`), About (Robia + `BuildConfig.VERSION_NAME`), Developer (only when unlocked: existing developer rows and `DeveloperCloudSyncCard` moved as they are).
4. `DriveSettingsScreen` (new file): account/status card + every Drive action row and dialog trigger moved verbatim from the old Data sync section (connect/backup again/restore if present/delete backup with its existing confirmation, error colour).
5. `LanguagePickerScreen` (new file): radio rows from `LanguagePreference.entries`; title = endonym strings `language_endonym_system` ("System default"), `language_endonym_en` "English", `language_endonym_es` "Español", `language_endonym_de` "Deutsch" (endonyms identical in all locales except System); subtitle = existing `language_*` label (UI language) and for System "Currently %1$s". Selecting calls the existing language setter.
6. `RobiaApp.kt`: add `DriveSettings` and `SmartSuggestionsSettings` routes, point `LanguageSettings` to `LanguagePickerScreen`, and pass down what the new screens need from `RobiaShell` (the callbacks/state `SettingsScreen` receives today, plus `onHapticsChange = { scope.launch { settingsRepository.setHapticsEnabled(it) } }`, mirroring how `setLanguagePreference` is called at ~462). No other change in this file.
6b. `RegressionSourceContractTest.settings_isDedicatedRouteAndDiagnosticsStayDeveloperGated` (~629): adapt anchors only (`SettingsScreen` now `internal` in `ui/SettingsScreen.kt`; end marker = next declaration in that file). Its checks must still hold: route `RobiaRoute.Settings -> SettingsScreen(`, `developerModeUnlocked`, `if (developerModeEnabled)` and `onRestoreSyncLogClick` inside `SettingsScreen`.
7. Delete the inline per-language `ListItem` loop and the old read-only `LanguageSettingsScreen`; keep `language_choices` only if still referenced (grep).

## Tests
`LanguagePickerTest` on the pure `languagePickerRows(current)` (V-U05-1).

## Done when
V-U05-1..3 + the inventory table in the receipt.

## Do not
Change Drive/sync logic, dialog copy, model download logic or developer unlock.
