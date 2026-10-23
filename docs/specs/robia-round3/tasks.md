# Tasks — robia-round3

**Status: proposed (planning PR).** G-APPROVAL = Manu writes APROBADO on the planning PR and it is merged. A task is Ready when G-APPROVAL passed, W0 is done and every predecessor (tasks and gates) is merged/passed.

## Rules for every worker (read first)
0. Read `briefs/README.md`, then `briefs/TASK-<ID>.md`, then your section here, then your rows in `verification-matrix.md`. Requirements: `requirements.md` (REQ-…). Evidence: `artifacts/*.md`. Mockups: `mockups/*.html` (captions Spanish, UI copy English).
1. Start from latest `origin/main`; branch `feat/<task-id-lowercase>-<slug>`; one PR per task; PR title `<TASK-ID>: <title>`.
2. Touch **only** the files listed under "Files". Always allowed: imports, `strings.xml` in all three locales, new test files under `app/src/test/`. Anything else: stop and ask one precise question.
3. Never build Android on the Raspberry Pi. Push and use CI (`gh pr checks <n> --watch`, `gh run view <id> --log-failed`). Python under `tools/` may run on the Pi.
4. Every new user-visible string in EN/ES/DE; EN copy is given in the brief; write ES/DE yourself and list them in the receipt.
5. No behaviour change outside "In scope". No unrelated refactors. Keep existing tests green; adapt a test only where the brief says so.
6. Existing Kotlin style. No new libraries.
7. Budget 90 min. At ~70 % push WIP and write the receipt. After 2 failed CI fix cycles: stop with the failing log excerpt.
8. Receipt (PR comment): commit SHA, CI run URL, each V- row with evidence, ES/DE strings, anything not done or not verified.
9. Standing Robia UI preferences (`docs/specs/robia-ux-rework/briefs/README.md` §Preferences) apply to every UI task. New UI uses existing theme tokens only (the palette change is pending).
10. `strings.xml` (3 locales): add your strings as ONE block at the end of each file headed `<!-- <TASK-ID> -->`; after a rebase keep both blocks.
11. Source-contract tests (`RegressionSourceContractTest`, `ManagePerformanceContractTest`, `SyncAttentionMappingTest`, `scripts/check_*_static.py`) pin earlier designs as text. Rewrite a pinned assertion only when your brief replaces that design, with equal strength; any other failure means the code is wrong.
12. Personal photos never enter the repository (public). Debug dumps stay on the device / planner workspace.
13. Room migrations: commit the migration SQL and its test; the coordinator commits the CI-generated schema JSON (`room-schemas` artifact) in the same PR before merge.


---

## K00 — Precise cutout off by default (G-SEG-2 fallback)
- **Reqs:** REQ-W0-02 · **Predecessors:** W0 · **Successors:** BAT2, ED2, K02, PRF
- **Files:** `core/model/ClothingModels.kt`; `data/SettingsRepository.kt`; `ui/PreciseCutoutSettings.kt`; `app/src/test/java/com/gusanitolabs/robia/data/SettingsMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PreciseCutoutSettingsTest.kt`.
- **In scope:** Default method Standard; one-time migration of existing installs to Standard; "Experimental" label on the precise option.
- **Out of scope:** Any change to the engines, the pack download or the editor Redo sheet.
- **Acceptance:** V-K00-1, V-K00-2, V-K00-D.

## K01 — Cutout diagnostics: stage dump and engine marker (debug)
- **Reqs:** REQ-CU-01 · **Predecessors:** W0 · **Successors:** ED2, K02, PRF
- **Files:** new `media/cutout/CutoutDebugDump.kt`; `media/cutout/PreciseCutoutEngine.kt`; `media/PhotoBackgroundRemover.kt`; `media/cutout/CutoutEngineRegistry.kt`; `MainActivity.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutFallbackChainTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutDebugDumpTest.kt`.
- **In scope:** Optional stage sink in the precise pipeline (no-op by default), debug-only dump runner, fallback reason + one log line per cutout.
- **Out of scope:** Any behaviour change of the cutout result; fixes (K02/K03); UI.
- **Acceptance:** V-K01-1, V-K01-2, V-K01-3, V-K01-D.

## K02 — Precise engine fixes from the device dumps
- **Reqs:** REQ-CU-02 · **Predecessors:** K01, G-ADB1 · **Successors:** K03 · **Independent review: yes**
- **Files:** `media/cutout/PreciseCutoutEngine.kt`; `media/cutout/HangerRemoval.kt`; `media/cutout/MaskOps.kt`; `media/cutout/InteractiveSelector.kt`; `media/cutout/CutoutEngineRegistry.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/HangerRemovalTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/MaskOpsTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/GarmentLossGuardTest.kt`; `media/cutout/CutoutDebugDump.kt`; `media/PhotoBackgroundRemover.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutFallbackChainTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutDebugDumpTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/InteractiveSelectionSeamTest.kt`.
- **In scope:** Closed list (a)–(c) of REQ-CU-02 plus the defects recorded in the G-ADB1 PLAN-CHANGE (d).
- **Out of scope:** Hanger-band edges (K03); editor; any new model; default method (stays Standard).
- **Acceptance:** V-K02-1, V-K02-2, V-K02-3, V-K02-4, V-K02-5.
- **PLAN-CHANGE 2026-10-23 (coordinator):** the four files added to Files are required by brief steps 2 and 5 (garment_loss chain reason, report.json keys). Denominator decision: R3-DEC-022. Round 2: `InteractiveSelectionSeamTest.kt` added to Files (R3-DEC-025).
- **Closed defect list (G-ADB1):** R3-DEC-021 (PLAN-CHANGE 2026-10-23): D1 (hypothesis, verify first), D2, D3, D4 (hypothesis), D5 (hypothesis, re-check after D1).

## K03 — Crisp edges in the hanger band
- **Reqs:** REQ-CU-03 · **Predecessors:** K02 · **Successors:** OR1 · **Independent review: yes**
- **Files:** `media/cutout/PreciseCutoutEngine.kt`; `media/cutout/HangerRemoval.kt`; `media/cutout/MaskOps.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/HangerRemovalTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/MaskOpsTest.kt`; `tools/cutout-diag/reference_stages.py`.
- **In scope:** Band-local guide + same edge profile + 2 px decontamination; the same change in the reference script so parity holds.
- **Out of scope:** Global edge filter changes outside the band; ML Kit `AlphaRefiner`.
- **Acceptance:** V-K03-1, V-K03-2, V-K03-3, V-K03-D.

## ED1 — Editor smart tool: visible, clipped, safe
- **Reqs:** REQ-ED-01, REQ-ED-02 · **Predecessors:** W0 · **Successors:** ED2, K02, PRF
- **Files:** `ui/PhotoEditorScreen.kt`; new `media/cutout/SelectionClip.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt`.
- **In scope:** Trail in smart mode, no 600 ms drop, sampling ≤ 12 points, clip to touched components, whole-garment guard + hint, dev mask overlay + tap dump, rename "Redo cutout".
- **Out of scope:** Undo/redo history (ED2); InteractiveSelector internals (K02); Keep-mode restore (OR3).
- **Acceptance:** V-ED1-1, V-ED1-2, V-ED1-3, V-ED1-4, V-ED1-D.

## ED2 — Editor: full undo/redo history
- **Reqs:** REQ-ED-03 · **Predecessors:** ED1, G-ADB1 · **Successors:** OR1 · **Independent review: yes**
- **Files:** `ui/PhotoEditorScreen.kt`; `media/cutout/RefineSession.kt`; new `media/cutout/EditHistory.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/EditHistoryTest.kt`.
- **In scope:** Operation log with checkpoints for smart/brush/reset, "Cut out again" photos as cache temp files, Undo + Redo buttons, 64 MB budget; editor-wiring defects from the G-ADB1 PLAN-CHANGE. History is session-local and temporary (R3-DEC-027): it exists only while a photo is being added, before the garment is saved; it is discarded on save and never kept across app restarts; editing the photo of an already saved garment starts with no history.
- **Out of scope:** Keeping history after the garment is saved or the editor closes; persisting history across restarts; Keep-mode restore (OR3).
- **Acceptance:** V-ED2-1, V-ED2-2, V-ED2-3, V-ED2-4, V-ED2-D.
- **G-ADB1 editor findings (R3-DEC-021):** none supported; step 4 has no defect to fix. Scope is the history only.
- **PLAN-CHANGE 2026-10-23 (PR #149 review, R3-DEC-027):** (1) Brief step 1 named `EditOp.NewPhoto`; the implementation replaced it with `PhotoHistory`, which holds the "Cut out again" photos (cut-out lives there). Accepted. (2) The 64 MB budget stays at its value. Option B (keep it) was chosen by Manu on 2026-10-23. Undo does not reach across a cut-out when the budget cannot hold the photo kept before it (about a 2048x2048 photo at the next edit); within the budget, every step can be undone. (3) History is cleared on save.

## GPU — Developer benchmark: EmbeddingGemma 2 on GPU
- **Reqs:** REQ-OF-02 · **Predecessors:** W0 · **Successors:** DUP1, ED2, K02, PRF
- **Files:** `suggestions/engine/LiteRtNativeEmbedder.kt`; `suggestions/DevBenchmark.kt`; `ui/SettingsScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/GpuBenchmarkReportTest.kt`.
- **In scope:** Backend parameter (CPU default), a developer "Model benchmark (GPU)" row, parity vs CPU, safe failure.
- **Out of scope:** Switching the app to GPU; recipe or model change.
- **Acceptance:** V-GPU-1, V-GPU-2, V-GPU-D.

## OR1 — Keep the original photo and the cutout recipe (local)
- **Reqs:** REQ-OR-01, REQ-OR-02, REQ-OR-03 (new photos) · **Predecessors:** K03, ED2, BAT1, SPK · **Successors:** CLN, EX1, OR2, OR3 · **Independent review: yes**
- **Files:** `media/BackgroundRemovalEngine.kt`; `media/PhotoBackgroundRemover.kt`; `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt`; `media/cutout/PreciseCutoutEngine.kt`; `media/ClothingImageStore.kt`; new `media/OriginalPhotoStore.kt`; `core/model/ClothingModels.kt`; `data/local/Entities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `data/LocalRepositories.kt`; `ui/AddEditClothingScreen.kt`; `ui/BatchAddClothingScreen.kt`; `media/EditorPhotoState.kt`; `app/schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/14.json (generated by CI, committed by the coordinator)`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration13To14Test.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/CutoutRecipeTest.kt`.
- **In scope:** Engines return the exact input frame + recipe; the frame is saved as JPEG q90 next to the cutout; DB v14 columns; raw copy and `subject-*.png` deleted after a successful save (new photos only).
- **Out of scope:** Drive upload/restore of originals (OR2); editor use of originals (OR3); cleanup of old orphans (CLN); export (EX1).
- **Acceptance:** V-OR1-1, V-OR1-2, V-OR1-3, V-OR1-4, V-OR1-D.

## OR2 — Back up originals to Google Drive
- **Reqs:** REQ-OR-04 · **Predecessors:** OR1 · **Successors:** DUP2, ZIP · **Independent review: yes**
- **Files:** `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `sync/PhotoBlobKeys.kt`; `sync/PhotoIntegrityCheck.kt`; `sync/DriveUsage.kt`; `ui/DriveSettingsScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/DriveUsageTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/PhotoIntegrityCheckTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/sync/OriginalBlobTest.kt`.
- **In scope:** Optional snapshot fields, `originals/<garmentId>/<hash16>.jpg` blobs, upload/restore/purge/integrity/usage for originals; schemaVersion stays 2.
- **Out of scope:** New schema version; changes to cutout photo upload/restore semantics; zip.
- **Acceptance:** V-OR2-1, V-OR2-2, V-OR2-3, V-OR2-4, V-OR2-D.

## OR3 — Editor: restore background from the original, "Cut out again" for saved garments
- **Reqs:** REQ-OR-05, REQ-ED-04 · **Predecessors:** OR1 · **Successors:** — · **Independent review: yes**
- **Files:** `ui/PhotoEditorScreen.kt`; `media/cutout/RefineSession.kt`; `media/cutout/EditHistory.kt`; new `media/cutout/OriginalMapping.kt`; `ui/AddEditClothingScreen.kt`; `media/EditorPhotoState.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/OriginalMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt`.
- **In scope:** Map the original into cutout space with the recipe; Keep paints back RGB+alpha from it; "Cut out again" offered when an original exists; reprocess parity test.
- **Out of scope:** Garments without an original (unchanged behaviour); sync; export.
- **Acceptance:** V-OR3-1, V-OR3-2, V-OR3-3, V-OR3-D.

## CLN — One-time cleanup of orphan photo files
- **Reqs:** REQ-OR-03 (existing files) · **Predecessors:** OR1 · **Successors:** — · **Independent review: yes**
- **Files:** `media/ClothingImageStore.kt`; new `media/OrphanPhotoCleanup.kt`; `RobiaApplication.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/OrphanPhotoCleanupTest.kt`.
- **In scope:** Delete unreferenced `gallery-*`, `camera-*`, `subject-*`, `cropped-subject-*` files older than 24 h once per install (flag), never referenced files.
- **Out of scope:** Restored files, thumbnails, caches, models, originals.
- **Acceptance:** V-CLN-1, V-CLN-2, V-CLN-D.

## EX1 — Export screen: photo, format and fields
- **Reqs:** REQ-EX-01, REQ-EX-02 · **Predecessors:** OR1, SPK, OUT · **Successors:** ZIP
- **Files:** new `ui/ExportScreen.kt`; `ui/ItemDetailScreen.kt`; `ui/RobiaApp.kt`; `media/GarmentShareExporter.kt`; `media/PdfLayout.kt`; `data/SettingsRepository.kt`; `core/model/ClothingModels.kt`; `app/src/test/java/com/gusanitolabs/robia/media/PdfLayoutTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/ExportOptionsTest.kt`.
- **In scope:** Route + screen per `mockups/export.html`; image card renderer; PDF with chosen fields; persisted choices.
- **Out of scope:** Zip; sharing several garments; new fonts.
- **Acceptance:** V-EX1-1, V-EX1-2, V-EX1-3, V-EX1-D.

## ZIP — Download all my data (.zip)
- **Reqs:** REQ-ZIP-01 · **Predecessors:** OR2, EX1 · **Successors:** DUP2
- **Files:** new `data/WardrobeZipExporter.kt`; `ui/DriveSettingsScreen.kt`; `ui/RobiaApp.kt`; `app/src/main/AndroidManifest.xml (comment only)`; new `app/src/test/java/com/gusanitolabs/robia/data/WardrobeZipExporterTest.kt`.
- **In scope:** SAF CreateDocument, streamed ZipOutputStream, progress + Cancel, local data only.
- **Out of scope:** Import; Drive download; models/caches.
- **Acceptance:** V-ZIP-1, V-ZIP-2, V-ZIP-D.

## DUP1 — Duplicate finder: same-photo tier, banner, review, calibration screen
- **Reqs:** REQ-DU-01 (tier 1), REQ-DU-03, REQ-DU-04, G-DUP · **Predecessors:** VIS, GPU · **Successors:** DUP2, OUT, SEL
- **Files:** new `suggestions/DuplicateScan.kt`; new `ui/DuplicateReviewScreen.kt`; new `ui/DuplicateCalibrationScreen.kt`; `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; `ui/SettingsScreen.kt`; `data/EmbeddingRepository.kt`; `app/src/main/java/com/gusanitolabs/robia/suggestions/EmbeddingBackfill.kt` (edited); `ui/ReviewScaffold.kt` (destructive colour parameter only); `app/src/main/res/values/strings.xml`, `values-es/strings.xml`, `values-de/strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/EmbeddingBackfillTest.kt` (edited).
- **In scope:** Pure scan (exact input_hash tier + scored candidate pairs), banner, review with Keep/Delete, session-only "Not duplicates", developer calibration list.
- **Out of scope:** Persisted dismissals and the cosine tier (DUP2).
- **Acceptance:** V-DUP1-1, V-DUP1-2, V-DUP1-3, V-DUP1-D.
- **PLAN-CHANGE 2026-10-23 (review round 1, NOT SAFE on PR #152):** ReviewScaffold.kt added to Files (R3-DEC-026). Required: dismissed pairs never merge across groups; banner hidden while merge proposal, review-complete overlay or any sync attention card shows; Delete in error colour; KDoc; scan entry points do not throw.
- **PLAN-CHANGE 2026-10-23 (review round 5, R3-DEC-029):** EmbeddingBackfill.kt and EmbeddingBackfillTest.kt added to Files (edited, not new): the scan waits for the embedding backfill to settle (REQ-DU-04). Duplicate groups and session dismissals survive activity recreation within one process (process-scoped holder beside DuplicateScanHistory; nothing written to disk). The calibration signature is order-insensitive, so wardrobe writes no longer reset the marks.

## DUP2 — Duplicates: "very similar" tier and remembered dismissals
- **Reqs:** REQ-DU-01 (tier 2), REQ-DU-02 · **Predecessors:** DUP1, G-DUP, OR2, ZIP · **Successors:** — · **Independent review: yes**
- **Files:** `suggestions/DuplicateScan.kt`; `ui/DuplicateReviewScreen.kt`; new `data/local/DismissedDuplicateEntities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration14To15Test.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt`.
- **In scope:** Tier 2 with `T_DUP` from the G-DUP PLAN-CHANGE + same category + same primary colour; Room v15 `dismissed_duplicate_pairs`; optional snapshot list.
- **Out of scope:** Changing the threshold rule; UI beyond the "Very similar" label.
- **Acceptance:** V-DUP2-1, V-DUP2-2, V-DUP2-3, V-DUP2-D.

## BAT1 — Batch add: close button, clean cancel, early pinned ETA, one module wait per batch
- **Reqs:** REQ-BA3-01, REQ-BA3-02, REQ-BA3-03, REQ-PD-02(a) · **Predecessors:** W0 · **Successors:** OR1, VIS · **Independent review: yes**
- **Files:** `ui/BatchAddClothingScreen.kt`; `ui/BatchEta.kt`; `ui/BatchProcessingCoordinator.kt`; `ui/RobiaApp.kt`; `media/SegmentationModuleGate.kt`; `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt`; `media/ClothingImageStore.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BatchEtaTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/SegmentationModuleGateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinatorTest.kt`.
- **In scope:** ✕ in the top bar, single discard dialog, cancel-then-delete temp files, estimator hoisted with publish-time stamps and batch start as first stamp, pinned header, ≤ 1 text change / 5 s, shared 60 s module budget per batch.
- **Out of scope:** Persisting batches across process death; per-row remove of queued items; new engines.
- **Acceptance:** V-BAT1-1, V-BAT1-2, V-BAT1-3, V-BAT1-4, V-BAT1-D.

## BAT2 — Cutout pack download: Cancel and auto-resume on Wi-Fi
- **Reqs:** REQ-PD-02(e) · **Predecessors:** K00 · **Successors:** —
- **Files:** `media/cutout/CutoutPack.kt`; `suggestions/model/ModelDownloadWorker.kt`; `ui/PreciseCutoutSettings.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutPackDownloadTest.kt`.
- **In scope:** Pack download through the existing WorkManager worker (unmetered constraint), Cancel button, resume from the partial file.
- **Out of scope:** Embedding model download behaviour (only reuse).
- **Acceptance:** V-BAT2-1, V-BAT2-D.

## VIS — Browse/detail small visuals: white garments, dev-only sync row, 4-column pill, sign-in badges
- **Reqs:** REQ-BR3-02, REQ-BR3-03, REQ-BR3-04, REQ-DT-01 · **Predecessors:** BAT1 · **Successors:** DUP1, SPK
- **Files:** `ui/GarmentSwatchBackground.kt`; `ui/BrowseGrid.kt`; `ui/BrowseCardText.kt`; `ui/ItemDetailScreen.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentSwatchBackgroundTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BrowseCardTextTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`.
- **In scope:** Neutral backdrop for near-white colours; `GarmentCloudStatusRow` only in developer mode; icon-only pill at ≥ 4 columns; no Waiting/Sign-in card badges while the sign-in banner shows; PLAN-CHANGE 2026-10-23 (feedback item 1): also no Waiting badge (Dirty, Queued, FailedRetryable) on any card while Drive is NotConfigured or Disabled; the badge stays when Drive is connected.
- **Out of scope:** Palette tokens (pending); header sync indicator.
- **Acceptance:** V-VIS-1, V-VIS-2, V-VIS-3, V-VIS-4, V-VIS-5, V-VIS-D.

## SPK — Smart features without the shiny animation
- **Reqs:** REQ-AI-01, REQ-AI-02 · **Predecessors:** VIS, W0 · **Successors:** EX1, FIT, OR1, SEL
- **Files:** `core/designsystem/Motion.kt`; `ui/BrowseFilterBar.kt`; `ui/ItemDetailScreen.kt`; `ui/AddEditClothingScreen.kt`; `ui/ColorReviewScreen.kt`; `ui/TagReviewScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/core/designsystem/MotionTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`.
- **In scope:** Remove `aiSurface` from the 5 call sites; static ✦ accent; one-shot `SparkleBurst` on completion; delete the infinite shimmer.
- **Out of scope:** Processing overlay shimmer (`MaskedShimmer`), review completion screen.
- **Acceptance:** V-SPK-1, V-SPK-2, V-SPK-D.

## FIT — Fit filter
- **Reqs:** REQ-BR3-06 · **Predecessors:** SPK · **Successors:** ANA
- **Files:** `ui/BrowseFilterBar.kt`; `ui/AdvancedFiltersScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BrowseFilterStateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PruneUnknownFiltersTest.kt`.
- **In scope:** `fit` section (Fits / Doesn't fit), chips, count, Reset.
- **Out of scope:** Editor fit values.
- **Acceptance:** V-FIT-1, V-FIT-D.

## SEL — Drag to select a range in Browse
- **Reqs:** REQ-BR3-05 · **Predecessors:** DUP1, SPK · **Successors:** PRF
- **Files:** `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; new `ui/DragSelect.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DragSelectTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`.
- **In scope:** Long-press then drag selects the range anchor..current; edge autoscroll; pinch and scroll unaffected.
- **Out of scope:** Bulk actions other than delete.
- **Acceptance:** V-SEL-1, V-SEL-2, V-SEL-D.

## PRF — Browse scroll performance at 4 columns
- **Reqs:** REQ-BR3-01 · **Predecessors:** SEL, G-ADB1 · **Successors:** ANA
- **Files:** `media/ImagePipeline.kt`; `media/ImagePipelineContracts.kt`; `ui/BoundedGarmentImage.kt`; `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; `scripts/check_image_thumbnail_pipeline_static.py`; `app/src/test/java/com/gusanitolabs/robia/media/ImagePipelineContractTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt`.
- **In scope:** Items (a)–(e) of REQ-BR3-01 in order, each kept only if it does not regress the device measurement.
- **Out of scope:** Replacing Coil; baseline profile module.
- **Acceptance:** V-PRF-1, V-PRF-2, V-PRF-3, V-PRF-4.

## ANA — Analytics screen
- **Reqs:** REQ-AN-01, REQ-AN-02, REQ-AN-03 · **Predecessors:** FIT, PRF · **Successors:** OUT
- **Files:** new `ui/AnalyticsScreen.kt`; new `core/model/WardrobeStats.kt`; `ui/BrowseFilterBar.kt`; `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/core/model/WardrobeStatsTest.kt`.
- **In scope:** Icon next to Filters, route, bar charts per group, tap → Browse filtered; PLAN-CHANGE 2026-10-23 (feedback item 2): on open, bars grow from the axis left to right in one short smooth transition (≤ 400 ms), none when reduced motion is on.
- **Out of scope:** Chart libraries; time series.
- **Acceptance:** V-ANA-1, V-ANA-2, V-ANA-3, V-ANA-4, V-ANA-D.

## OUT — Outfit lab (developer)
- **Reqs:** REQ-OF-01, G-OUT · **Predecessors:** ANA, DUP1 · **Successors:** EX1
- **Files:** new `suggestions/OutfitComposer.kt`; new `ui/OutfitLabScreen.kt`; `ui/SettingsScreen.kt`; `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/OutfitComposerTest.kt`.
- **In scope:** Seed garment or prompt → 3 outfits from existing vectors and tags; why-line; 👍/👎 log; export of ratings.
- **Out of scope:** User-facing entry; new models; training.
- **Acceptance:** V-OUT-1, V-OUT-2, V-OUT-D.

## CHK — Device checklist for round 3
- **Reqs:** all · **Predecessors:** ALL · **Successors:** —
- **Files:** new `docs/specs/robia-round3/artifacts/device-checklist.md`.
- **In scope:** Checklist of every DEV row and gate result, with the latest main staging APK link.
- **Out of scope:** Code.
- **Acceptance:** V-CHK-1.
