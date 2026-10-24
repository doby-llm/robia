# TASK-DARK3 — Dark mode: Settings, Developer, onboarding, sync, duplicates

## Goal
F14 (REQ-FB-14): the Settings family, the Drive and sync surfaces, the onboarding prompts and the duplicate screens look right in Dark. These files hold no colour literal today (checked at `ec4f419`), so the code change is small: a scan test plus whatever the audit and Manu's pass find.

## Read first
`R3-DEC-045`; `TASK-DARK1.md`; the Files below. State colours to look at: `ui/DriveSettingsScreen.kt` and `ui/SyncAttentionCard.kt` (Connected, Needs attention, error, merge card), `ui/ModelDownloadUi.kt` (download and progress rows), `ui/PreciseCutoutSettings.kt`, `ui/LanguagePickerScreen.kt`, `ui/SuggestionDiagnosticsCard.kt`, `ui/DuplicateCalibrationScreen.kt`, `ui/DuplicateReviewScreen.kt` (garments shown in pairs: backdrops behind photos use the photo-well tokens, like DARK2), `ui/SettingsScreen.kt` (SettingsGroup and SettingsSection cards use `surfaceContainerLowest`).

## Files
`ui/SettingsScreen.kt`; `ui/DriveSettingsScreen.kt`; `ui/PreciseCutoutSettings.kt`; `ui/LanguagePickerScreen.kt`; `ui/ModelDownloadUi.kt`; `ui/SyncAttentionCard.kt`; `ui/SuggestionDiagnosticsCard.kt`; `ui/DuplicateCalibrationScreen.kt`; `ui/DuplicateReviewScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeSettingsScanTest.kt`.

## Steps
1. `DarkThemeSettingsScanTest`: `ThemeSourceScan` over the Files list (V-DARK3-1). It should pass without source changes; if it does not, convert or mark the literal as in DARK2.
2. Audit as in DARK2 step 3 for every file in the list; the state colours must be scheme roles (`primary`, `tertiary`, `error` and their containers), never fixed hex. Record findings and fixes in the receipt; "none found" is a valid result for a file.
3. Photos in the duplicate screens sit on a photo-well backdrop (`extras.photoWellTop`).

## Tests
V-DARK3-1 (CI), V-DARK3-2 (REV), V-DARK3-D (Manu).

## Done when
V-DARK3-1, V-DARK3-2 pass, V-DARK3-D is ready for Manu; CI green. If the audit finds nothing, the PR is the scan test and an honest receipt; the coordinator may then merge this task into the DARK2 pass for the device check.

## Do not
Change the Appearance row (DARK1), sync or duplicate logic, layout or copy.
