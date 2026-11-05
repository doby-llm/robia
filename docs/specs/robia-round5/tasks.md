# Tasks — robia-round5

**Status: PROPOSED.** A task is Ready when G-APPROVAL passed and every predecessor is merged/passed. Spec: `SPEC.md`.
Paths are relative to `app/src/main/java/com/gusanitolabs/robia/` unless they start with `app/`, `.github/`, `tools/`
or `docs/`.

## Rules for every code worker (read first)
0. Read `briefs/README.md`, then `briefs/TASK-<ID>.md`, then your section here, then the SPEC sections the brief names.
1. Start from latest `origin/main`; branch `feat/<task-id-lowercase>-<slug>`; one PR per task; title `<TASK-ID>: <title>`.
2. Touch **only** the files under "Files". Always allowed: imports, `strings.xml` in all three locales
   (`app/src/main/res/values{,-es,-de}/strings.xml`), new test files under `app/src/test/` (or `tools/play/tests/`).
   Anything else: stop and ask one precise question.
3. Never build Android on the Raspberry Pi. Push and use CI (`gh pr checks <n> --watch`, `gh run view <id> --log-failed`).
   If `pull_request` CI does not start, run `gh workflow run android-apk.yml --ref <branch>`.
4. User-visible copy: SPEC §1.2 verbatim. Strings in ONE block at the end of each strings.xml headed `<!-- <TASK-ID> -->`.
5. No behaviour change outside "In scope". No refactors. No new Android libraries.
6. Source-contract tests (`RegressionSourceContractTest`, `DarkThemeBrowseScanTest`, `scripts/check_*_static.py`) pin
   earlier designs as text. Rewrite a pinned assertion only when your brief replaces that design, with equal strength.
7. Colours only through `MaterialTheme.colorScheme.*` / `MaterialTheme.extras`; no `Color(0x…)` without
   `// theme-fixed:` and a reason. Check light and dark of BOTH palettes.
8. Budget 90 min. At ~70 % push WIP and write the receipt. After 2 failed CI fix cycles stop with the failing log excerpt.
   A compiler `OutOfMemoryError` in CI is a rerun (`gh run rerun <id> --failed`), not a fix attempt.
9. Receipt (PR comment): commit SHA, CI run URL for that SHA, each V- row with evidence (test name), ES/DE strings
   added, anything not done or not verified, every interpretation you chose where the brief was silent.
10. The repository is PUBLIC: no photos, keys, tokens, service-account JSON or home paths. 11. Do not merge.

## Graph
```
G-APPROVAL -> TH1, REL1, PL1, CR1          (parallel; disjoint files)
TH1 -> TH2                                  (SettingsScreen / strings after the model exists)
TH2, REL1 -> CHK5 -> G-CODE (Manu)
G-APPROVAL -> PL3                           (tools/play, independent of the app code)
G-CODE, PL1 -> PL2 (release AAB of the G-CODE commit)
G-CODE, CR1 -> CAP -> CR2 -> G-CR (Manu approves videos + stills)
G-CODE -> PB1 (create app + App content, browser agent) -> PB2 (Manu: first AAB to Internal testing, needs PL2)
PB2 -> PB3 (SHA-1 OAuth client, consent screen, service account invite + key)
PB3, PL3, G-CR -> PL5 (listing + screenshots + closed-track release via API)
PL5, G-TESTERS -> PB4 (testers, countries, send closed test for review) -> 14 days -> PB5 (apply for production)
PB5 approved -> PL6 (production release, Manu's explicit go)
```
Hot files: `SettingsScreen.kt`, `RobiaApp.kt`, `MainActivity.kt` → TH1 then TH2; `app/build.gradle.kts` and
`.github/workflows/android-apk.yml` → REL1 only.
Denominator for "Hechos X de Y": code + coordinator tasks TH1, TH2, REL1, PL1, PL3, CHK5, CR1, CAP, CR2, PL2, PL5 = **11**.
PB1–PB5 and PL6 are Hermes/Manu steps tracked in the issue but not counted.

## TH1 — Palette model, Tobacco & Bordeaux tokens, theme wiring (Haiku worker)
- **Predecessors:** G-APPROVAL · Brief `briefs/TASK-TH1.md` · Review: none (no data writes), coordinator diff read.
- **Files:** `core/model/ClothingModels.kt`; `data/SettingsRepository.kt`; new `core/designsystem/TobaccoBordeauxColor.kt`;
  `core/designsystem/Theme.kt`; `MainActivity.kt`; `ui/RobiaApp.kt` (only the `RobiaApp` signature and the `RobiaTheme(`
  call); `ui/AddEditClothingScreen.kt`, `ui/BatchAddClothingScreen.kt`, `ui/BrowseGrid.kt`, `ui/DuplicateReviewScreen.kt`
  (only the `RobiaLightColorScheme.*` photo-well sites); `ui/PhotoEditorScreen.kt` (comments only).
- **In scope:** REQ-TH-1..6b, 8. **Out:** Settings UI and strings (TH2).
- **Acceptance:** V-TH1-1 … V-TH1-6.

## TH2 — Settings "Colour palette" row and dialog (Haiku worker)
- **Predecessors:** TH1 · Brief `briefs/TASK-TH2.md` · Review: none.
- **Files:** `ui/SettingsScreen.kt`; 3× strings.xml.
- **In scope:** REQ-TH-7, copy SPEC §1.2. **Acceptance:** V-TH2-1 … V-TH2-3.

## REL1 — Version 1.0.0 and `capture` build (Haiku worker)
- **Predecessors:** G-APPROVAL · Brief `briefs/TASK-REL1.md` · Review: none.
- **Files:** `app/build.gradle.kts`; `.github/workflows/android-apk.yml`; `README.md` (CI artifacts list only).
- **In scope:** SPEC §1.3. **Acceptance:** V-REL1-1 … V-REL1-3.

## PL1 — Upload key and release secrets (coordinator, host only, no PR)
- **Predecessors:** G-APPROVAL. Runbook `briefs/PLAY-RUNBOOK.md` §PL1. **Acceptance:** V-PL1-1, V-PL1-2.

## PL3 — `tools/play` Play Developer API CLI (Haiku worker)
- **Predecessors:** G-APPROVAL · Brief `briefs/TASK-PL3.md` · Review: one Opus 5.5 high read-only review (it can
  publish to a store; BLOCKING = any path that commits without `--commit`, reaches `production` without both guards,
  prints/logs key material, or uploads an AAB whose sha256 was not verified).
- **Files:** new `tools/play/**` only. **Acceptance:** rows in the brief (V-PL3-*).

## CHK5 — Device checklist for G-CODE (coordinator, docs PR)
- **Predecessors:** TH2, REL1 merged. **Files:** new `docs/specs/robia-round5/artifacts/device-checklist.md`.
- Latest main `robia-staging-apk` uploaded to Manu's Drive (configured API) + signer check; Spanish steps for
  V-TH-D1 … V-TH-D5; message Manu. G-CODE passes on his reply (rule: anything not mentioned passed).

## CR1 — Storyboards, shot list, listing copy (creative lane, Opus 5.5 high)
- **Predecessors:** G-APPROVAL (paper only; uses the current UI as reference) · Brief `briefs/TASK-CR.md` §Phase 1.
- **Output:** `docs/specs/robia-round5/creative/` (storyboard-vertical.md, storyboard-horizontal.md, shotlist.md,
  listing-<locale>.md, credits.md) in a docs PR. Gate: Manu reads the two storyboards (Spanish summary).

## CAP — ADB capture session (coordinator + Manu's phone)
- **Predecessors:** G-CODE, CR1 merged · `briefs/CAP-SHOTLIST.md` + `creative/shotlist.md`. Output outside git.
- **Acceptance:** every shot id in `creative/shotlist.md` present for EN/ES/DE in `manifest.json`; V-CAP-1 … V-CAP-3.

## CR2 — Build and render videos, screenshots, feature graphic, icon (creative lane, Opus 5.5 high)
- **Predecessors:** CAP · Brief `briefs/TASK-CR.md` §Phase 2. Output: `~/.local/share/robia-planning/r5/creative-out/`
  (outside git, large) + small PNG/MP4 previews in Manu's Drive. Gate G-CR.

## PL2 — Release AAB of the G-CODE commit (coordinator)
- **Predecessors:** G-CODE, PL1. `gh workflow run android-apk.yml --ref main -f release=true`; download
  `robia-release-aab`; check sha256, `versionName 1.0.0`, signer = upload key (`jarsigner -verify -certs`).

## PB1–PB5 — Play Console steps (Hermes browser agent + Manu) · PL5, PL6 — API steps (coordinator)
See `briefs/PLAY-RUNBOOK.md` (order, who, stop points) and `briefs/PLAY-CONSOLE-SHEET.md` (every answer).
