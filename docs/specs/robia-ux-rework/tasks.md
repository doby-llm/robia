# Tasks — robia-ux-rework

Revision 1 (2026-10-17). **G-APPROVAL** = Manu writes APROBADO on the planning PR and it is merged. All tasks are `Ready` once G-APPROVAL passes and their predecessors are merged.

## Rules for every worker (read first)
0. Read `briefs/README.md`, then `briefs/TASK-<ID>.md`, then this section, then your rows in `verification-matrix.md`. `technical-specification.md` §N = TS§N; decisions UX-/SEG-/PROC-DEC-NNN in `decision-log.md`. Mockups: `mockups/*.html` (open in a browser; captions are Spanish, UI copy English).
1. Start from latest `origin/main`; branch `feat/<task-id-lowercase>-<slug>`; one PR per task; PR title `<TASK-ID>: <title>`.
2. Touch **only** the files listed under "Files". Always allowed: imports, `strings.xml` in all three locales (`values`, `values-es`, `values-de`), new test files under `app/src/test/`. Anything else: stop and ask one precise question.
3. Never build Android on the Raspberry Pi. Push and use CI (`gh pr checks <n> --watch`, `gh run view <id> --log-failed`). Python helpers under `tools/` may run on the Pi.
4. Every new user-visible string in EN/ES/DE. EN copy is given in the task/TS; write ES/DE yourself and list them in the receipt for review.
5. No behaviour change outside "In scope". No unrelated refactors. Keep existing tests green; adapt a test only where the task says so.
6. Style: existing Kotlin style (4 spaces, trailing commas). New libraries only those named in the task, at the given version.
7. Budget 90 min. At ~70 % push WIP and write the receipt. After 2 failed CI fix cycles: stop with the failing log excerpt.
8. Receipt (PR comment): commit SHA, CI run URL, each acceptance row (V-…) with evidence, ES/DE strings added, anything not done or not verified.
9. Standing Robia UI preferences (`briefs/README.md` §Preferences) apply to every UI task.
10. `strings.xml` (3 locales) is shared: add your strings as ONE contiguous block at the end of each file (before `</resources>`) headed by `<!-- <TASK-ID> -->`. If main moved, rebase and keep both blocks; this is the only conflict you may resolve yourself.
11. Source-contract tests (`RegressionSourceContractTest`, `ManagePerformanceContractTest`, `SyncAttentionMappingTest`) pin earlier designs as text. When your task intentionally replaces a pinned design (the brief names the test), rewrite that test to pin the NEW design with equivalent strength; any other failing contract test means you broke something — fix the code, not the test.

---

## U00 — Move-only split of `ui/RobiaApp.kt`
- **Reqs:** PROC-DEC-001 · TS§0 · **Predecessors:** G-APPROVAL · **Successors:** U01, U02, U03, U04, U05, U06, U08
- **Files:** `ui/RobiaApp.kt`, new `ui/BrowseGrid.kt`, `ui/BrowseFilterBar.kt`, `ui/AdvancedFiltersScreen.kt`, `ui/SettingsScreen.kt`, `ui/ItemDetailScreen.kt`; tests `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`, `ManagePerformanceContractTest.kt`, `ui/SyncAttentionMappingTest.kt`; new `tools/split-check/declarations.py`.
- **In scope:** move declarations per TS§0 table; visibility `private`→`internal` only when needed; `uiSource()` test helper; declarations check script and its output in the receipt.
- **Out of scope:** any statement change, renames, formatting of moved code.
- **Acceptance:** V-U00-1, V-U00-2. **Review:** coordinator (diff must be moves only: `git diff --stat` + script output).

## U01 — Motion and haptics primitives + haptics setting
- **Reqs:** REQ-MO-01 · UX-DEC-007 · TS§1 · **Predecessors:** U00 (one line in `ui/RobiaApp.kt`) · **Successors:** U02, U03, U05, U07, U08
- **Files:** new `core/designsystem/Motion.kt`, `core/designsystem/Haptics.kt`; `core/designsystem/Theme.kt` (provide locals), `core/model/ClothingModels.kt` (`RobiaSettings.hapticsEnabled`), `data/SettingsRepository.kt`, `ui/RobiaApp.kt` (only the `RobiaTheme {` root at ~line 448 to pass `hapticsEnabled`).
- **In scope:** TS§1 API exactly; unit tests for `hapticConstantFor` and settings default/persist.
- **Out of scope:** using the primitives in screens (later tasks).
- **Acceptance:** V-U01-1, V-U01-2.

## U02 — Browse card rework
- **Reqs:** REQ-BR-01..04, REQ-BR-06 · UX-DEC-001 · TS§2 · mockup `browse.html` (A) · **Predecessors:** U00, U01 · **Successors:** Z02
- **Files:** `ui/BrowseGrid.kt`, new `ui/BrowseCardText.kt` (the new `clip` parameter of `GarmentPhotoPlaceholder` defaults to `true`, so `ui/ItemDetailScreen.kt` is not touched), `RegressionSourceContractTest.kt` (card assertions ~766-779 per TS§2 only).
- **In scope:** card shape/clip, fixed footer, title/meta pure functions, badge mapping, removal of the "Synced" subtitle fallback, grid spacing/insets, `pressScale` on cards.
- **Out of scope:** filter bar (U03), detail screen layout (U06).
- **Acceptance:** V-U02-1..4, V-Z2-01, V-Z2-02.

## U03 — Filters: grouped screen, Reset, active-filter row, OR/AND semantics
- **Reqs:** REQ-BR-05, REQ-FI-01..04 · UX-DEC-002, UX-DEC-003 · TS§3 · mockups `filters.html`, `browse.html` · **Predecessors:** U00, U01, U02 · **Successors:** Z02
- **Files:** `ui/BrowseFilterBar.kt`, `ui/AdvancedFiltersScreen.kt`, `ui/BrowseGrid.kt` (only the `FilterBar(...)` call site inside `BrowseWardrobeScreen` to pass `onResetFilters`; U02 owns the rest of this file), `RegressionSourceContractTest.kt` (only if the FilterBar order test anchors move; order must stay as TS§3).
- **In scope:** TS§3 exactly; pure `matches` with unit tests over the semantics table in the brief.
- **Out of scope:** search behaviour, similar-to, empty-state reset semantics (unchanged).
- **Acceptance:** V-U03-1..4, V-Z2-03, V-Z2-04. **Review:** independent (filter semantics + no lost filter path).

## U04 — Manage horizontal swipe
- **Reqs:** REQ-MA-01 · UX-DEC-004 · TS§4 · **Predecessors:** U00 (both edit `ManagePerformanceContractTest.kt`) · **Successors:** Z02
- **Files:** `ui/ManageTagsScreen.kt` (tab/body region ~102-300), `app/src/test/java/com/gusanitolabs/robia/ManagePerformanceContractTest.kt` (anchors only if needed).
- **Acceptance:** V-U04-1, V-Z2-05.

## U05 — Settings rework
- **Reqs:** REQ-ST-01..03 · UX-DEC-008 · TS§5 · mockup `settings.html` · **Predecessors:** U00, U01, U03 (shared contract test file) · **Successors:** SG2a, SG2b, Z02
- **Files:** `ui/SettingsScreen.kt`, new `ui/DriveSettingsScreen.kt`, new `ui/LanguagePickerScreen.kt`, `ui/ModelDownloadUi.kt` (only to host `SmartSuggestionsSettingsSection` in a sub-screen without changing it), `ui/RobiaApp.kt` (only `RobiaRoute` entries, `RobiaNavHost` route cases for Settings / LanguageSettings / DriveSettings / SmartSuggestionsSettings, and passing existing state/callbacks from `RobiaShell` down to them — e.g. `settingsRepository.setHapticsEnabled`, the Drive callbacks `SettingsScreen` already receives), `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (anchors of `settings_isDedicatedRouteAndDiagnosticsStayDeveloperGated` only).
- **In scope:** grouped rows, sub-screens, haptics switch, endonym strings, removal of inline language rows and the dead `LanguageSettingsScreen` list.
- **Out of scope:** any Drive/sync logic, dialogs content, developer cards content.
- **Acceptance:** V-U05-1..3, V-Z2-06, V-Z2-07.

## U06 — Garment form and detail as list rows
- **Reqs:** REQ-GF-01..04 · UX-DEC-005 · TS§6 · mockup `garment-form.html` · **Predecessors:** U00, U01, U02 (uses `cardTitle` from `ui/BrowseCardText.kt`), SG1 (both edit `ui/AddEditClothingScreen.kt`) · **Successors:** U07, SG3, Z02
- **Files:** `ui/AddEditClothingScreen.kt`, `ui/ItemDetailScreen.kt`, new `ui/GarmentFieldRows.kt`.
- **In scope:** TS§6; save path unchanged; AI ✦ marker; bottom-sheet pickers; detail rows; empty "Refine" slot (hidden).
- **Out of scope:** photo processing pipeline, processing visuals (U07), name field (none).
- **Acceptance:** V-U06-1..5, V-Z2-08, V-Z2-09. **Review:** independent (save path, tag toggles, single-select categories).

## U07 — Processing visuals (sweep, masked shimmer, pop)
- **Reqs:** REQ-PF-01..04 · UX-DEC-006 · TS§7 · mockup `processing.html` · **Predecessors:** U01, U06 · **Successors:** Z02
- **Files:** new `ui/PhotoProcessingVisuals.kt`; `ui/AddEditClothingScreen.kt` (`PhotoPreview` and `PhotoProcessingStage` visibility `private`→`internal` only); `ui/BatchAddClothingScreen.kt` (`BatchPhotoPreview` only); `ui/ProcessingOverlayContrast.kt` (delete if unused; grep first).
- **Acceptance:** V-U07-1, V-U07-2, V-Z2-10.

## U08 — Review and AI feel
- **Reqs:** REQ-MO-02, REQ-MO-03 · TS§8 · mockup `motion.html` · **Predecessors:** U00, U01, U03, U06 · **Successors:** Z02
- **Files:** `ui/ReviewScaffold.kt`, `ui/TagReviewScreen.kt` (card action region ~615-740), `ui/ColorReviewScreen.kt` (~348-420), `ui/BrowseFilterBar.kt` (sparkle button modifier only; after U03 merges), `ui/ItemDetailScreen.kt` (Similar button modifier only; after U06 merges).
- **Acceptance:** V-U08-1, V-Z2-11.

## U09 — PDF A4 rework
- **Reqs:** REQ-PD-01 · UX-DEC-009 · TS§9 · mockup `pdf.html` · **Predecessors:** G-APPROVAL · **Successors:** Z02
- **Files:** `media/GarmentShareExporter.kt` (holds `GarmentShareItem` at ~512), new `media/PdfLayout.kt`, new `app/src/main/res/font/inter_regular.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`, `app/src/main/assets/licenses/inter-OFL.txt`, `RegressionSourceContractTest.kt` (the two PDF design tests, per TS§9).
- **Acceptance:** V-U09-1, V-U09-2, V-Z2-12.

## T01 — Socks and Underwear categories
- **Reqs:** REQ-TX-01, REQ-TX-02 · UX-DEC-010 · TS§T · **Predecessors:** G-APPROVAL · **Successors:** Z02
- **Files:** `core/model/DefaultTags.kt`, `suggestions/EmbeddingRecipe.kt` (`DEFAULT_LABEL_DESCRIPTIONS` and `SUGGEST_OCCASIONS_DEFAULT_OFF`), `ui/TagLocalization.kt`, `tools/embedding-precision/labels.py`, tests (`EmbeddingRecipeTest`, `GoldenFixtureTest` only if it couples to DefaultTags, new DAO seeding test).
- **Acceptance:** V-T01-1..3, V-Z2-13, V-Z2-14. **Review:** independent (seeding/tombstones/dedupe/sync of defaults).

## SG1 — ML Kit reliability and soft edges
- **Reqs:** REQ-SG-01..03 · SEG-DEC-001, SEG-DEC-002 · TS§SG1 · **Predecessors:** G-APPROVAL · **Successors:** U06, SG2a
- **Files:** listed in TS§SG1 header (engine, remover, result model, new `SegmentationModuleGate.kt`, new `AlphaRefiner.kt`, `ClothingImageStore.kt` thumbnail sample size, `BatchAddClothingScreen.kt` NeedsReview retry, `AddEditClothingScreen.kt` fallback message, `RobiaApplication.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`).
- **Acceptance:** V-SG1-1..4, V-Z2-15, V-Z2-16. **Review:** independent.

## SG2a — Precise cutout engine, model pack, dev benchmark
- **Reqs:** REQ-SG-04, REQ-SG-05, REQ-SG-07 · SEG-DEC-003, SEG-DEC-005, SEG-DEC-006 · TS§SG2a · **Predecessors:** SG1, U05 (Developer rows live in `ui/SettingsScreen.kt`) · **Successors:** G-SEG-1, SG2b, SG3
- **Files:** the full TS§SG2a "Files:" list (includes `media/PhotoBackgroundRemover.kt`, `media/BackgroundRemovalEngine.kt`, `RobiaApplication.kt`, `data/SettingsRepository.kt`, `core/model/ClothingModels.kt`, `ui/SettingsScreen.kt` Developer rows); `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro` (keep rules for ONNX Runtime and MediaPipe); `tools/segmentation-bench/make_cutout_benchmark.py` + generated `app/src/main/assets/benchmark/cutout/*.png` (synthetic only).
- **In scope:** engine + pack + `CutoutEngineRegistry` + setting `preciseCutoutEnabled` (default false) exposed only as a Developer switch "Use precise cutout"; benchmark card.
- **Out of scope:** user-facing Settings row (SG2b), refine UI (SG3).
- **Acceptance:** V-SG2-1..5. **Review:** independent (fallback chain, native session lifetime, sha pinning, no personal data in assets).
- **Device gate G-SEG-1** (Manu, after merge): see `briefs/TASK-SG2a.md` §Gate message.

## SG2b — Precise cutout in Settings
- **Reqs:** REQ-SG-04 · TS§SG2b · **Predecessors:** SG2a, U05, **G-SEG-1 PASS** · **Successors:** G-SEG-2, Z02
- **Files:** `ui/SettingsScreen.kt`, new `ui/PreciseCutoutSettings.kt`, `ui/RobiaApp.kt` (route + plumbing only), `data/SettingsRepository.kt` and `core/model/ClothingModels.kt` (default of `preciseCutoutEnabled` false → true).
- **Acceptance:** V-SG2-6, V-Z2-17.

## SG3 — Refine cutout editor
- **Reqs:** REQ-SG-06, REQ-SG-07 (tap latency) · SEG-DEC-004 · TS§SG3 · mockup `processing.html` (Refine) · **Predecessors:** U06, U07 (both edit `ui/AddEditClothingScreen.kt`), SG2a (MediaPipe dependency + bundled model + `InteractiveSelector`) · **Successors:** Z02
- **Files:** new `ui/RefineCutoutScreen.kt`, new `media/cutout/RefineSession.kt`, `ui/AddEditClothingScreen.kt` (Refine slot wiring only), `ui/RobiaApp.kt` (route only; after U05 and SG2b if they are open, rebase).
- **Acceptance:** V-SG3-1..3, V-Z2-18.

## Z02 — Device checklist
- **Predecessors:** all above merged · **Files:** `docs/specs/robia-ux-rework/artifacts/device-checklist.md` · **Acceptance:** checklist with every V-Z2 row, latest main staging APK link, grouped A (no cloud), B (photos/segmentation), C (mockup comparison).
