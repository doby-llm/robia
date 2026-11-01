# TASK-P02 — Release hygiene: privacy-policy link, developer mode off in release

## Goal
Play Console requires a privacy-policy URL and expects it reachable from the app for apps that handle personal data
(photos, Google account) — PLAY-B2. Developer mode (10 taps on Settings, `ui/RobiaApp.kt:277-278,1618-1631`) exposes
benchmark and debug-dump tools in the release build — PLAY-S1.

## Read first
SPEC §7.2; `ui/SettingsScreen.kt:128-300` (groups, `developerModeUnlocked` :134/:234); `ui/RobiaApp.kt`
`handleSettingsClick` (:1618-1631); `app/build.gradle.kts` (`buildConfig = true`, build types debug/release/staging).

## Steps
1. New `ui/ReleaseGates.kt`:
   `const val PRIVACY_POLICY_URL = "https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md"` (public repo `doby-llm/robia-privacy-policy`, R4-DEC-013)
   `fun developerModeAllowed(buildType: String): Boolean = buildType != "release"`.
2. `RobiaApp.kt` `handleSettingsClick`: return before counting taps when `!developerModeAllowed(BuildConfig.BUILD_TYPE)`.
   Where developer mode is read for UI (`settings.developerModeUnlocked`/`developerModeEnabled` passed to screens),
   AND it with `developerModeAllowed(BuildConfig.BUILD_TYPE)` at the single place RobiaApp derives it (one line).
3. `SettingsScreen.kt`, group General: row "Privacy policy" (`settings_privacy_policy`; ES "Política de privacidad";
   DE "Datenschutzerklärung") with an external-link icon, opening `PRIVACY_POLICY_URL` via `Intent.ACTION_VIEW`
   (catch `ActivityNotFoundException` silently).
4. Tests `ReleaseGatesTest` (V-P02-1 pure + contract that `handleSettingsClick` calls `developerModeAllowed`), V-P02-2.

## Done when
V-P02-1, V-P02-2 green; V-P02-D for Manu.

## Do not
Edit the policy text (task POL owns `doby-llm/robia-privacy-policy`); change debug/staging behaviour.
