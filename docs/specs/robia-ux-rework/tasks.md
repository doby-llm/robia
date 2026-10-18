# Tasks — robia-ux-rework

**Approved 2026-10-18.** Revision 3 (2026-10-17; rev-1 feedback in `feedback/2026-10-17-manu-rev1-feedback.md`, rev-2 feedback and answers in `feedback/2026-10-17-manu-rev2-feedback.md`). **G-APPROVAL** = Manu writes APROBADO on the planning PR and it is merged. All tasks are `Ready` once G-APPROVAL passes and their predecessors are merged.

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
- **Reqs:** PROC-DEC-001 · TS§0 · **Predecessors:** G-APPROVAL · **Successors:** U02, U03, U04, U05, U06, U08
- **Files:** `ui/RobiaApp.kt`, new `ui/BrowseGrid.kt`, `ui/BrowseFilterBar.kt`, `ui/AdvancedFiltersScreen.kt`, `ui/SettingsScreen.kt`, `ui/ItemDetailScreen.kt`; tests `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`, `ManagePerformanceContractTest.kt`, `ui/SyncAttentionMappingTest.kt`; new `tools/split-check/declarations.py`.
- **In scope:** move declarations per TS§0 table; visibility `private`→`internal` only when needed; `uiSource()` test helper; declarations check script and its output in the receipt.
- **Out of scope:** any statement change, renames, formatting of moved code.
- **Acceptance:** V-U00-1, V-U00-2. **Review:** coordinator (diff must be moves only: `git diff --stat` + script output).

## U01 — Motion primitives + Browse layout setting
- **Reqs:** REQ-MO-01, REQ-BR-07 (setting only) · UX-DEC-007 · TS§1 · **Predecessors:** G-APPROVAL · **Successors:** U02, U03, U05, U07, U08
- **Files:** new `core/designsystem/Motion.kt`; `core/designsystem/Theme.kt` (provide `LocalReducedMotion`), `core/model/ClothingModels.kt` (`BrowseLayout`, `RobiaSettings.browseLayout`), `data/SettingsRepository.kt` (+ every `SettingsRepository` test fake).
- **In scope:** TS§1 API exactly (no haptics); `toRobiaSettings()` extraction; settings default/persist tests.
- **Out of scope:** using the primitives in screens (later tasks).
- **Acceptance:** V-U01-1, V-U01-2.

## U02 — Browse card rework + photos-only layout
- **Reqs:** REQ-BR-01..04, REQ-BR-06, REQ-BR-07 · UX-DEC-001 · TS§2 · mockup `browse.html` · **Predecessors:** U00, U01, B01 (`ui/RobiaApp.kt` order) · **Successors:** Z02
- **Files:** `ui/BrowseGrid.kt`, new `ui/BrowseCardText.kt` (the new `clip` parameter of `GarmentPhotoPlaceholder` defaults to `true`, so `ui/ItemDetailScreen.kt` is not touched), `RegressionSourceContractTest.kt` (card assertions ~766-779 per TS§2 only), `ui/RobiaApp.kt` (the `BrowseWardrobeScreen(` call: pass `settings.browseLayout`, one line).
- **In scope:** card shape/clip, fixed footer, title/meta pure functions, badge mapping, removal of the "Synced" subtitle fallback, 2-column grid spacing/insets, `GarmentPhotoTile` (photos only, default), `pressScale` on cards.
- **Out of scope:** filter bar (U03), detail screen layout (U06).
- **Acceptance:** V-U02-1..6, V-Z2-01, V-Z2-02, V-Z2-19.

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
- **Files:** `ui/SettingsScreen.kt`, new `ui/DriveSettingsScreen.kt`, new `ui/LanguagePickerScreen.kt`, `ui/ModelDownloadUi.kt` (only to host `SmartSuggestionsSettingsSection` in a sub-screen without changing it), `ui/RobiaApp.kt` (only `RobiaRoute` entries, `RobiaNavHost` route cases for Settings / LanguageSettings / DriveSettings / SmartSuggestionsSettings, and passing existing state/callbacks from `RobiaShell` down to them — e.g. `settingsRepository.setBrowseLayout`, the Drive callbacks `SettingsScreen` already receives), `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (anchors of `settings_isDedicatedRouteAndDiagnosticsStayDeveloperGated` only).
- **In scope:** grouped rows, sub-screens, Browse layout row + dialog, endonym strings, removal of inline language rows and the dead `LanguageSettingsScreen` list.
- **Out of scope:** any Drive/sync logic, dialogs content, developer cards content.
- **Acceptance:** V-U05-1..3, V-Z2-06, V-Z2-07.

## U06 — Garment form and detail as list rows
- **Reqs:** REQ-GF-01..04 · UX-DEC-005 · TS§6 · mockup `garment-form.html` · **Predecessors:** U00, U01, U02 (uses `cardTitle` from `ui/BrowseCardText.kt`), SG1 (both edit `ui/AddEditClothingScreen.kt`) · **Successors:** U07, SG3, Z02
- **Files:** `ui/AddEditClothingScreen.kt`, `ui/ItemDetailScreen.kt`, new `ui/GarmentFieldRows.kt`.
- **In scope:** TS§6; save path unchanged; AI ✦ marker; bottom-sheet pickers; detail rows; the photo card's existing quick-edit button relabelled "Edit photo" (still opens the current dialog until SG3).
- **Out of scope:** photo processing pipeline, processing visuals (U07), name field (none).
- **Acceptance:** V-U06-1..5, V-Z2-08, V-Z2-09. **Review:** independent (save path, tag toggles, single-select categories).

## U07 — Processing visuals (sweep, masked shimmer, pop)
- **Reqs:** REQ-PF-01..04 · UX-DEC-006 · TS§7 · mockup `processing.html` · **Predecessors:** U01, U06, B01 (`ui/BatchAddClothingScreen.kt`) · **Successors:** Z02
- **Files:** new `ui/PhotoProcessingVisuals.kt`; `ui/AddEditClothingScreen.kt` (`PhotoPreview` and `PhotoProcessingStage` visibility `private`→`internal` only); `ui/BatchAddClothingScreen.kt` (`BatchPhotoPreview` only); `ui/ProcessingOverlayContrast.kt` (delete if unused; grep first).
- **Acceptance:** V-U07-1, V-U07-2, V-Z2-10.

## U08 — Review and AI feel + completion screen
- **Reqs:** REQ-MO-02, REQ-MO-03, REQ-MO-04 · TS§8 · mockup `motion.html` · **Predecessors:** U00, U01, U03, U05 (both edit `ui/RobiaApp.kt`), U06 · **Successors:** Z02
- **Files:** new `ui/ReviewCompleteOverlay.kt`, `ui/RobiaApp.kt` (`commitTagReview` / `commitColorReview` + overlay host above the Manage route only), `ui/ReviewScaffold.kt`, `ui/TagReviewScreen.kt` (card action region ~615-740), `ui/ColorReviewScreen.kt` (~348-420), `ui/BrowseFilterBar.kt` (sparkle button modifier only; after U03 merges), `ui/ItemDetailScreen.kt` (Similar button modifier only; after U06 merges).
- **Acceptance:** V-U08-1..3, V-Z2-11, V-Z2-20.

## U09 — PDF A4 rework
- **Reqs:** REQ-PD-01, REQ-PD-02 · UX-DEC-009, UX-DEC-016 · TS§9 · mockup `pdf.html` (3 cases) · **Predecessors:** U00 (`RegressionSourceContractTest.kt`) · **Successors:** Z02
- **Files:** `media/GarmentShareExporter.kt` (holds `GarmentShareItem` at ~512), new `media/PdfLayout.kt`, new `app/src/main/res/font/inter_regular.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`, `app/src/main/assets/licenses/inter-OFL.txt`, `RegressionSourceContractTest.kt` (the two PDF design tests, per TS§9).
- **Acceptance:** V-U09-1, V-U09-2, V-Z2-12.

## T01 — Socks and Underwear categories + Accessories description
- **Reqs:** REQ-TX-01..03 · UX-DEC-010 · TS§T · **Predecessors:** G-APPROVAL · **Successors:** Z02
- **Files:** `core/model/DefaultTags.kt`, `suggestions/EmbeddingRecipe.kt` (`DEFAULT_LABEL_DESCRIPTIONS` and `SUGGEST_OCCASIONS_DEFAULT_OFF`), `ui/TagLocalization.kt`, `tools/embedding-precision/labels.py`, tests (`EmbeddingRecipeTest`, `GoldenFixtureTest` only if it couples to DefaultTags, new DAO seeding test).
- **Acceptance:** V-T01-1..3, V-Z2-13, V-Z2-14. **Review:** independent (seeding/tombstones/dedupe/sync of defaults).

## SG1 — ML Kit reliability and soft edges
- **Reqs:** REQ-SG-01..03 · SEG-DEC-001, SEG-DEC-002 · TS§SG1 · **Predecessors:** G-APPROVAL · **Successors:** U06, SG2a
- **Files:** listed in TS§SG1 header (engine, remover, result model, new `SegmentationModuleGate.kt`, new `AlphaRefiner.kt`, `ClothingImageStore.kt` thumbnail sample size, `BatchAddClothingScreen.kt` NeedsReview retry, `AddEditClothingScreen.kt` fallback message, `RobiaApplication.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`).
- **Acceptance:** V-SG1-1..4, V-Z2-15, V-Z2-16. **Review:** independent.

## SG2a — Precise cutout engine, model pack, dev benchmark
- **Reqs:** REQ-SG-04, REQ-SG-05, REQ-SG-07 · SEG-DEC-003, SEG-DEC-005, SEG-DEC-006 · TS§SG2a · **Predecessors:** SG1, U05 (Developer rows live in `ui/SettingsScreen.kt`), B01 (`BatchParallelism`) · **Successors:** G-SEG-1, SG2b, SG3
- **Files:** the full TS§SG2a "Files:" list (includes `media/PhotoBackgroundRemover.kt`, `media/BackgroundRemovalEngine.kt`, `RobiaApplication.kt`, `data/SettingsRepository.kt`, `core/model/ClothingModels.kt`, `ui/SettingsScreen.kt` Developer rows); `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro` (keep rules for ONNX Runtime and MediaPipe); `tools/segmentation-bench/make_cutout_benchmark.py` + generated `app/src/main/assets/benchmark/cutout/*.png` (synthetic only).
- **In scope:** engine incl. the S4b hanger-first stage + pack + `CutoutEngineRegistry` + setting `preciseCutoutEnabled` (default false) exposed only as a Developer switch "Use precise cutout"; benchmark card.
- **Out of scope:** user-facing Settings row (SG2b), photo editor (SG3).
- **Acceptance:** V-SG2-1..5. **Review:** independent (fallback chain, native session lifetime, sha pinning, no personal data in assets).
- **Device gate G-SEG-1** (Manu, after merge): see `briefs/TASK-SG2a.md` §Gate message.

## SG2b — Precise cutout in Settings
- **Reqs:** REQ-SG-04 · TS§SG2b · **Predecessors:** SG2a, U05, **G-SEG-1 PASS** · **Successors:** G-SEG-2, Z02
- **Files:** `ui/SettingsScreen.kt`, new `ui/PreciseCutoutSettings.kt`, `ui/RobiaApp.kt` (route + plumbing only), `data/SettingsRepository.kt` and `core/model/ClothingModels.kt` (default of `preciseCutoutEnabled` false → true).
- **Acceptance:** V-SG2-6, V-Z2-17.

## SG3 — Photo editor (Cutout + Light), replaces the quick-edit dialog
- **Reqs:** REQ-SG-06, REQ-SG-07 (tap latency) · SEG-DEC-004, UX-DEC-011 · TS§SG3 · mockup `photo-editor.html` · **Predecessors:** U06, U07 (both edit `ui/AddEditClothingScreen.kt`), SG2a (MediaPipe dependency + bundled model + `InteractiveSelector`) · **Successors:** Z02
- **Files:** new `ui/PhotoEditorScreen.kt`, new `media/cutout/RefineSession.kt`, new `media/cutout/BrushStroke.kt`, `ui/AddEditClothingScreen.kt` (the `QuickEditDialog(` call becomes `PhotoEditorScreen(`; `QuickEditDialog` + its private helpers are deleted after moving the Light controls into the new file).
- **In scope:** TS§SG3; Done path reuses `reprocessEditedPhoto`; Cancel writes nothing.
- **Out of scope:** automatic pipeline, batch flow, stored originals, new light tools.
- **Acceptance:** V-SG3-1..4, V-Z2-18. **Review:** independent (save path, existing-garment source, no data loss on Cancel).

## B01 — Pipelined batch processing
- **Reqs:** REQ-BA-01 · PROC-DEC-004 · TS§B01 · **Predecessors:** SG1 (both edit `ui/BatchAddClothingScreen.kt`), U00 (`ui/RobiaApp.kt`) · **Successors:** B02, Z02
- **Files:** `ui/BatchProcessingCoordinator.kt` (incl. `BatchParallelism`), `ui/BatchAddClothingScreen.kt` (`processBatchDraft` split into `cutoutStage` / `suggestStage` only), `ui/RobiaApp.kt` (`processQueuedBatchDrafts` block only), `app/src/test/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinatorTest.kt`, `RegressionSourceContractTest.kt` (`batchCoordinator_processesAll60QueuedItemsSeriallyToTerminalStates` only).
- **In scope:** two-stage pipeline, ordered publication, cancellation/retry semantics, tests (a)–(f) of TS§B01.
- **Out of scope:** UI (B02), two embeddings in parallel, changing the cutout or suggestion code.
- **Acceptance:** V-B01-1..3, V-Z2-21 (`BatchParallelism.cutouts` defaults to 2; SG2a wires the precise case). **Review:** independent (concurrency, cancellation, ordering, no lost draft).

## B02 — Batch screen as a list + ETA
- **Reqs:** REQ-BA-02, REQ-BA-03, REQ-PF-04 · UX-DEC-012 · TS§B02 · mockup `batch.html` · **Predecessors:** B01, U07 (both edit `ui/BatchAddClothingScreen.kt`) · **Successors:** Z02
- **Files:** `ui/BatchAddClothingScreen.kt` (screen UI only), new `ui/BatchEta.kt`, `RegressionSourceContractTest.kt` (`batch_helper_summary` string assertion only).
- **Acceptance:** V-B02-1..3, V-Z2-22.

## U10 — Pinch to change Browse columns (rev 3)
- **Reqs:** REQ-BR-08 · UX-DEC-017 · TS§2b · **Predecessors:** U02 (`BrowseGrid.kt`), U05 (`RobiaApp.kt` settings plumbing order) · **Successors:** Z02
- **Files:** `ui/BrowseGrid.kt` (`BrowseWardrobeScreen` grid + gesture only), new `ui/BrowseColumns.kt`, `ui/RobiaApp.kt` (`BrowseWardrobeScreen(` call site only), `data/SettingsRepository.kt`, `core/model/ClothingModels.kt` (`RobiaSettings.browseColumns`), test fakes implementing `SettingsRepository`.
- **Acceptance:** V-U10-1..3, V-Z2-23.

## C01 — Gray default colour (rev 3)
- **Reqs:** REQ-CO-01, REQ-CO-02 · COL-DEC-001 · **Predecessors:** T01 (`DefaultTags.kt`, `DefaultTagsTest.kt`), SG1 (`ClothingImageStore.kt`) · **Successors:** Z02
- **Files:** `core/model/DefaultTags.kt` (`mainColors` only), `core/color/PaletteColorClassifier.kt` (`NEUTRAL_PALETTE_IDS` only), `data/LocalRepositories.kt` (`seedDefaultsIfNeeded` only), `media/ClothingImageStore.kt` (`extractPaletteColorDiagnostics` decode only, optional step), tests `DefaultTagsTest.kt`, `PaletteColorClassifierTest.kt`, new `GraySeedTest.kt`.
- **Acceptance:** V-C01-1..3, V-Z2-24.

## D01 — Drive space used by Robia (rev 3)
- **Reqs:** REQ-SY-01 · DATA-DEC-001 · **Predecessors:** U05 (`DriveSettingsScreen.kt`) · **Successors:** D02, Z02
- **Files:** new `sync/DriveUsage.kt`, new `sync/DriveUsageStore.kt`, `sync/GoogleDriveWardrobeRepository.kt` (after each `listAll` + new `refreshDriveUsage`), `sync/DriveWardrobeRepository.kt`, `sync/WardrobeSyncGateway.kt` (`driveUsage` field + `refreshDriveUsage`), `sync/WardrobeSyncOutboxProcessor.kt` (`toWardrobeSyncState` + gateway method only), `ui/DriveSettingsScreen.kt`, `RobiaApplication.kt` (store wiring only), test fakes of the gateway/repository.
- **Acceptance:** V-D01-1..3, V-Z2-25.

## D02 — Photo integrity: exact upload/restore, account binding, Check photos (rev 3)
- **Reqs:** REQ-SY-02..05 · SYNC-DEC-001 · **Predecessors:** D01 (same sync files and `DriveSettingsScreen.kt`) · **Successors:** Z02
- **Files:** `sync/GoogleDriveWardrobeRepository.kt`, `sync/DriveWardrobeRepository.kt`, `sync/WardrobeSyncGateway.kt`, `sync/WardrobeSyncOutboxProcessor.kt`, `sync/SyncAttention.kt`, new `sync/PhotoIntegrityCheck.kt`, `MainActivity.kt` (`persistDriveAuthorizationResult` only), `data/SettingsRepository.kt` + `core/model/ClothingModels.kt` (two account fields), `ui/DriveSettingsScreen.kt`, `ui/SyncAttentionCard.kt` (new kind), `ui/RobiaApp.kt` (attention action wiring only), `app/src/test/java/com/gusanitolabs/robia/ui/SyncAttentionMappingTest.kt` (new kind only, rule 11), new tests.
- **In scope:** steps 1-5 of the brief. **Out of scope:** original-photo backup, key format change, deleting orphans.
- **Acceptance:** V-D02-1..6, V-Z2-26, V-Z2-27. **Review:** independent (no path may delete or overwrite a photo; account mismatch blocks every write).

## SG4 — Cutout settings + Redo cutout (PLAN-CHANGE 1, 2026-10-18)
- **Reqs:** REQ-SG-08, REQ-SG-09 · SEG-DEC-009 · TS§SG4 · **Predecessors:** SG2b (`PreciseCutoutSettings.kt`, setting), SG3 (`PhotoEditorScreen.kt`, `RefineSession.kt`), B01 (`BatchParallelism`) · **Successors:** Z02
- **Files:** `core/model/ClothingModels.kt` (`RobiaSettings` cutout fields), `data/SettingsRepository.kt`, new `media/cutout/CutoutOptions.kt`, `media/cutout/CutoutEngineRegistry.kt`, `media/cutout/PreciseCutoutEngine.kt`, `media/cutout/HangerRemoval.kt`, `media/AlphaRefiner.kt`, `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt` (edge profile argument only), `ui/PreciseCutoutSettings.kt`, `ui/SettingsScreen.kt` (row title only), `ui/PhotoEditorScreen.kt`, `media/cutout/RefineSession.kt`, `RobiaApplication.kt` (registry/BatchParallelism wiring only), test fakes of `SettingsRepository`, tests.
- **Acceptance:** V-SG4-1..5, V-Z2-28..30. **Review:** independent (defaults equal today's behaviour; migration of `preciseCutoutEnabled`; Redo never writes until Done).


- **Predecessors:** all above merged · **Files:** `docs/specs/robia-ux-rework/artifacts/device-checklist.md` · **Acceptance:** checklist with every V-Z2 row, latest main staging APK link, grouped A (no cloud), B (photos/segmentation), C (mockup comparison).
