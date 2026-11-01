# TASK-P01 — Target and compile SDK 36

## Goal
Google Play refuses new apps and updates that do not target Android 16 (API 36) since 2026-08-31 (Play Console help
"Target API level requirements", answer 11926878). Robia has `compileSdk = 35`, `targetSdk = 35`
(`app/build.gradle.kts:48,53`) — PLAY-B1, the first-upload blocker.

## Read first
SPEC §7.2; `artifacts/inv-r4-play-readiness.md` §1–2, §4; `app/build.gradle.kts`; `gradle/libs.versions.toml`
(AGP 8.13.2); `MainActivity.kt:70-95` (edge-to-edge already enabled); `app/src/main/AndroidManifest.xml`.

## Steps
1. Set `compileSdk = 36` and `targetSdk = 36`. Push; CI is the authority. If CI fails because a library or AGP must
   be raised for SDK 36, raise ONLY that version, the smallest one that compiles, and list it in the receipt.
2. Android 16 behaviour checklist — for each item write "checked in code: file:line / no impact" or "needs device":
   a. Edge-to-edge is mandatory (opt-out removed): screens already use `enableEdgeToEdge`; grep
      `windowOptOutEdgeToEdgeEnforcement` (must be absent) and check that every `Scaffold` passes insets.
   b. Predictive back is on by default: grep `enableOnBackInvokedCallback`, `onBackPressed` (deprecated), and confirm
      all back logic uses `BackHandler` (12 call sites) — no `onBackPressed()` overrides.
   c. Large screens: `screenOrientation`/`resizeableActivity` ignored on sw≥600dp — grep the manifest.
   d. JobScheduler/WorkManager quota changes: WorkManager version supports API 36 (check catalog version).
   e. `Intent` safety / implicit intents to own components; `MediaStore`/SAF unaffected.
   f. 16 KB page size: the staging APK already passes (audit §4); re-check by running the audit's ELF check on the
      new staging artifact if you can download it (`gh run download`), otherwise "needs coordinator".
3. No functional changes.

## Done when
V-P01-1 green; V-P01-2 checklist in the receipt; V-P01-D steps listed for Manu.

## Do not
Upgrade unrelated libraries; change features; touch other build types' signing.
