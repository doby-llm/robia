# Tasks — robia-round4

**Status: APPROVED 2026-11-01 (G-APPROVAL passed when the planning PR merges).** A task is
Ready when G-APPROVAL passed and every predecessor (tasks and gates) is merged/passed. Spec: `SPEC.md`. Paths are
relative to `app/src/main/java/com/gusanitolabs/robia/` unless they start with `app/`, `.github/` or `docs/`.

## Rules for every worker (read first)
0. Read `briefs/README.md`, then `briefs/TASK-<ID>.md`, then your section here, then the SPEC sections the brief names.
   Evidence with file:line: `artifacts/inv-r4-*.md`. Look and copy: `mockups/collections.html` (copy table at the end).
1. Start from latest `origin/main`; branch `feat/<task-id-lowercase>-<slug>`; one PR per task; title `<TASK-ID>: <title>`.
2. Touch **only** the files under "Files". Always allowed: imports, `strings.xml` in all three locales
   (`app/src/main/res/values{,-es,-de}/strings.xml`), new test files under `app/src/test/`. Anything else: stop and
   ask one precise question.
3. Never build Android on the Raspberry Pi. Push and use CI (`gh pr checks <n> --watch`, `gh run view <id> --log-failed`).
   If `pull_request` CI does not start, run `gh workflow run android-apk.yml --ref <branch>`.
4. User-visible copy: use the EN/ES/DE strings of the mockup table verbatim (key names may change; meaning may not).
   Strings go in ONE block at the end of each strings.xml headed `<!-- <TASK-ID> -->`; after a merge keep both blocks.
5. No behaviour change outside "In scope". No refactors. No new libraries (exception: P01 SDK bump, P03 none).
6. Source-contract tests (`RegressionSourceContractTest`, `ManagePerformanceContractTest`, `DarkThemeBrowseScanTest`,
   `scripts/check_*_static.py`) pin earlier designs as text. Rewrite a pinned assertion only when your brief replaces
   that design, with equal strength. `ManageTagsScreen.kt` must never contain the text `Card(`.
7. New UI uses existing theme tokens (`MaterialTheme.colorScheme.*`, `core/designsystem/Theme.kt`); no `Color(0x…)`
   without `// theme-fixed:` and a reason; check light and dark.
8. Budget 90 min. At ~70 % push WIP and write the receipt. After 2 failed CI fix cycles stop with the failing log excerpt.
   A compiler `OutOfMemoryError` in CI is a rerun (`gh run rerun <id> --failed`), not a fix attempt.
9. Receipt (PR comment): commit SHA, CI run URL for that SHA, each V- row with evidence (test name or reasoning),
   ES/DE strings added, anything not done or not verified, and every interpretation you chose where the brief was silent.
10. Personal photos/dumps never enter the repository (public).
11. Do not merge. The coordinator merges.

## Graph
```
G-APPROVAL -> C01, C04, P01
C01 -> C02 -> C03
C02, C04 -> C05
C05 -> P02          (RobiaApp.kt lease)
P01 -> P02 -> P03   (SettingsScreen.kt lease)
P02 -> P04 -> G-PLAY (DEFERRED: Manu decides when; upload key secrets + Console) -> P05
P03 -> POL          (policy repo, uses the data-safety facts)
C03, C05, P03 -> CHK
G-CLN passed (Manu 2026-11-01: categories A, B, C, E) -> CLN (coordinator, can start in wave 1)
CLN: independent, coordinator-run, after Manu's cleanup answers (G-CLN)
```
Shared hot files and their order (one live writer at a time):
`ui/ManageTagsScreen.kt`: C02 → C05 · `ui/RobiaApp.kt`: C02 → C05 → P02 · strings.xml x3: everyone (tagged
blocks) · `data/local/Dao.kt`, `data/WardrobeRepository.kt`, `data/LocalRepositories.kt`: C01 only ·
`app/build.gradle.kts`: P01 → P04 · `.github/workflows/android-apk.yml`: P04 only · `ui/SettingsScreen.kt`: P02 → P03.
C03 and C04 touch no hot file except strings.xml, so they can run in parallel with the chain.

---

## C01 — Collections data layer and upgrade-safe sync equivalence
- **Reqs:** REQ-COL-01, -04, -05, -10 · **Predecessors:** G-APPROVAL · **Successors:** C02 · **Independent review: yes (Opus 5.5 high)**
- **Files:** `core/model/DefaultTags.kt`; `data/local/Dao.kt` (TagDao only); `data/WardrobeRepository.kt` (TagRepository only);
  `data/LocalRepositories.kt` (LocalTagRepository only); `sync/SnapshotEquivalence.kt`; new `core/model/CollectionMembership.kt`;
  tests: `app/src/test/java/com/gusanitolabs/robia/core/model/DefaultTagsTest.kt` (only if an assertion counts categories),
  new `…/core/model/CollectionMembershipTest.kt`, new `…/data/CollectionMembershipSqlTest.kt`,
  `…/sync/SnapshotEquivalenceTest.kt`, `…/sync/MergeDecisionTest.kt`, new `…/sync/CollectionSnapshotRoundTripTest.kt`.
- **In scope:** R4-D01, R4-D03, R4-D04 (+ its fallback gate), R4-D08/D09 unchanged behaviour pinned by tests.
- **Out of scope:** any UI; Room schema/version; sync schema version; embeddings.
- **Acceptance:** V-C01-1 … V-C01-6.

## C02 — Manage › Collections tab: create, rename, delete with confirmation, ± entry point
- **Reqs:** REQ-COL-02, -03, -09 · **Predecessors:** C01 · **Successors:** C03, C05
- **Files:** `ui/ManageTagsScreen.kt`; `ui/CategoryIcons.kt`; `ui/RobiaApp.kt` (ONLY the `RobiaRoute.ManageTags ->`
  branch of `RobiaNavHost`: pass `collectionMemberCounts`); new `ui/CollectionManageRules.kt`; tests:
  `…/ManagePerformanceContractTest.kt` (only if an anchor moves, equal strength), new `…/ui/CollectionManageRulesTest.kt`.
- **In scope:** mockup frames 1, 2, 7; R4-D05, R4-D07; new `ManageTagsScreen` parameters
  `collectionMemberCounts: Map<String, Int> = emptyMap()` and `onEditMembers: ((GarmentTag) -> Unit)? = null`; the ±
  `IconButton` is rendered only for `categoryId == "collection"` rows AND when `onEditMembers != null` (C05 wires it,
  so after C02 alone the icon is not visible yet — expected).
- **Out of scope:** the members screen; other categories' rows (no ± there, ever); any other RobiaApp region.
- **Acceptance:** V-C02-1 … V-C02-5.

## C03 — Collections in the garment editor, item detail and filters
- **Reqs:** REQ-COL-06, -07, -09 · **Predecessors:** C02 · **Successors:** CHK
- **Files:** `ui/AddEditClothingScreen.kt`; `ui/ItemDetailScreen.kt`; `ui/AdvancedFiltersScreen.kt`; `ui/BrowseFilterBar.kt`
  (`categoryTitleRes` only); new `ui/CollectionFieldRules.kt`; tests: new `…/ui/CollectionFieldRulesTest.kt`,
  `…/ui/BrowseFilterStateTest.kt` (add cases only).
- **In scope:** R4-D06 (mockup frames 8, 9).
- **Out of scope:** export, share, card text, analytics, creating a collection from the sheet.
- **Acceptance:** V-C03-1 … V-C03-5.

## C04 — Members session model and paint-drag (pure)
- **Reqs:** REQ-COL-08 (logic part) · **Predecessors:** G-APPROVAL · **Successors:** C05
- **Files:** new `ui/CollectionMembersSession.kt`; new `ui/CollectionDragPaint.kt`; tests: new
  `…/ui/CollectionMembersSessionTest.kt`, new `…/ui/CollectionDragPaintTest.kt`.
- **In scope:** draft state, view filter All/In/Not in, diff (add/remove), Undo all, prune of vanished garments,
  leave-decision function, paint-drag range logic. No Compose UI, no Android types.
- **Out of scope:** UI, `DragSelect.kt` (untouched).
- **Acceptance:** V-C04-1 … V-C04-4.

## C05 — Collection members screen and route
- **Reqs:** REQ-COL-08, -09 · **Predecessors:** C02, C04 (and C01 via C02) · **Successors:** CHK · **Independent review: yes (Opus 5.5 high)**
- **Files:** new `ui/CollectionMembersScreen.kt`; `ui/RobiaApp.kt` (regions listed in the brief only);
  `ui/ManageTagsScreen.kt` (none expected; only if wiring the existing `onEditMembers` parameter requires it);
  `ui/BrowseGrid.kt` (only `private` → `internal` on `GarmentPhotoTile` and `GarmentGridCard`); tests:
  `…/ui/DarkThemeBrowseScanTest.kt` (add the new file to its scan list), new `…/ui/CollectionMembersWiringContractTest.kt`.
- **In scope:** R4-D10 … R4-D16 (mockup frames 3–6), save via `TagRepository.setTagMembership` + `UpsertItem` ops.
- **Out of scope:** Browse multi-select "add to collection"; snackbar; any change to `DragSelect.kt` or Browse behaviour.
- **Acceptance:** V-C05-1 … V-C05-7.

## P01 — Target and compile SDK 36
- **Reqs:** PLAY-B1 · **Predecessors:** G-APPROVAL · **Successors:** P02
- **Files:** `app/build.gradle.kts` (`compileSdk`, `targetSdk` only); `gradle/libs.versions.toml` (only if CI says a
  dependency/AGP needs a bump for SDK 36 — list it in the receipt); `MainActivity.kt` and `app/src/main/AndroidManifest.xml`
  only for Android 16 behaviour fixes named in the brief.
- **In scope:** API 36 build green; Android 16 behaviour check list in the brief; staging APK for G-DEV-P01.
- **Out of scope:** features, other dependency upgrades.
- **Acceptance:** V-P01-1 … V-P01-3.

## P02 — Release hygiene in the app: privacy link, developer mode off in release
- **Reqs:** PLAY-B2 (in-app part), PLAY-S1 · **Predecessors:** P01, C05 (RobiaApp.kt lease) · **Successors:** P03, P04
- **Files:** `ui/SettingsScreen.kt`; `ui/RobiaApp.kt` (only `handleSettingsClick` and the developer-mode gate);
  new `ui/ReleaseGates.kt`; tests: new `…/ui/ReleaseGatesTest.kt`.
- **In scope:** Settings › General: "Privacy policy" row opening `PRIVACY_POLICY_URL` (constant, value in brief) in the
  browser; developer mode cannot be unlocked or shown when `BuildConfig.BUILD_TYPE == "release"`.
- **Out of scope:** writing the policy text (Manu / docs), licences screen (P03).
- **Acceptance:** V-P02-1 … V-P02-3.

## P03 — Licences and data-safety facts
- **Reqs:** §7.1 licences, §7.3 · **Predecessors:** P02 (SettingsScreen.kt lease) · **Successors:** CHK
- **Files:** new `ui/OpenSourceLicensesScreen.kt`; new `app/src/main/assets/licenses/third_party.md`; `ui/SettingsScreen.kt`
  (one row); `ui/RobiaApp.kt` NOT allowed (open the screen as a full-screen dialog from Settings); new
  `docs/specs/robia-round4/artifacts/data-safety.md`; tests: new `…/ui/LicensesAssetTest.kt`.
- **In scope:** list every bundled/downloaded model and SDK with licence + source URL; confirm D-FINE and magic_touch
  licences from upstream; data-safety facts from the merged manifest of the release variant.
- **Out of scope:** Play Console entry (Manu).
- **Acceptance:** V-P03-1 … V-P03-3.

## P04 — Release build pipeline ready for Play
- **Reqs:** PLAY-M1, M3, S2, S4 · **Predecessors:** P02 · **Successors:** G-PLAY
- **Files:** `app/build.gradle.kts` (release block: `versionCode` from env/CI with fallback 1, release `ndk.abiFilters`);
  `.github/workflows/android-apk.yml` (release-aab job only: versionCode = `github.run_number + 100`, upload AAB +
  `mapping.txt` artifacts, `bundletool`-free 16 KB check script call); new `scripts/check_aab_16kb.py`; tests: none
  (Python script has a self-test `--self-test`).
- **In scope:** job runs green **once Manu has added the 4 secrets**; without them it fails with the existing clear message.
- **Out of scope:** uploading to Play; Play Console; changing staging/debug.
- **Acceptance:** V-P04-1 … V-P04-3.

## POL — Update the public privacy policy repo
- **Predecessors:** P03 · **Successors:** — · **Repo:** `doby-llm/robia-privacy-policy` (NOT robia; own clone, branch `docs/robia-round4`, PR, merge on review by coordinator — that repo has no CI)
- **Files:** `README.md`, `DELETE_ACCOUNT.md` of that repo only.
- **In scope:** exactly the edits in `artifacts/privacy-policy-changes.md`. **Out of scope:** any other wording, a website, GitHub Pages.
- **Acceptance:** V-POL-1.

## P05 — First internal-testing upload (Manu + coordinator) — DEFERRED
- **Predecessors:** G-PLAY (deferred until Manu has new screenshots/colours and says go) · **Successors:** —
- **Not a worker task.** Coordinator triggers `release-aab` (workflow_dispatch `release=true`), downloads the AAB,
  gives Manu the steps in the P04 brief §"Manu steps"; Manu uploads to Internal testing and confirms Drive sign-in on
  the Play-installed build. Result recorded in the tracking issue.

## CHK — Device checklist round 4
- **Predecessors:** C03, C05, P03 · **Files:** new `docs/specs/robia-round4/artifacts/device-checklist.md` (docs only).
- Latest main `robia-staging-apk` link + signer check, plain-Spanish steps for every V-*-D row, destructive steps last.

## CLN — Workspace cleanup (coordinator, host only, no PR)
- **Predecessors:** G-CLN — passed 2026-11-01 (approved categories A, B, C, E; D = remote branches NOT approved) · Brief: `briefs/TASK-CLN.md`.
