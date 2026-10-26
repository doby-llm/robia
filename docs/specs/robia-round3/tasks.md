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
- **PLAN-CHANGE 2026-10-23 (R3-DEC-028):** V-K03-1 becomes a CI regression row. No synthetic scene fails on the pre-K03 behaviour (K03 worker, round 2), so V-K03-3 (compare.py on the red-shorts dump, G-ADB2) is the only evidence that the band is fixed.

## ED1 — Editor smart tool: visible, clipped, safe
- **Reqs:** REQ-ED-01, REQ-ED-02 · **Predecessors:** W0 · **Successors:** ED2, K02, PRF
- **Files:** `ui/PhotoEditorScreen.kt`; new `media/cutout/SelectionClip.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt`.
- **In scope:** Trail in smart mode, no 600 ms drop, sampling ≤ 12 points, clip to touched components, whole-garment guard + hint, dev mask overlay + tap dump, rename "Redo cutout".
- **Out of scope:** Undo/redo history (ED2); InteractiveSelector internals (K02); Keep-mode restore (OR3).
- **Acceptance:** V-ED1-1, V-ED1-2, V-ED1-3, V-ED1-4, V-ED1-D.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-043):** the model-based tap does not work on the device. UI-SMART (Round 3c) replaces the selector call with a deterministic component tap; V-ED1-D is replaced by V-UI-SMART-D.

## ED2 — Editor: full undo/redo history
- **Reqs:** REQ-ED-03 · **Predecessors:** ED1, G-ADB1 · **Successors:** OR1 · **Independent review: yes**
- **Files:** `ui/PhotoEditorScreen.kt`; `ui/AddEditClothingScreen.kt` (one-line call site `keepsHistory`); `media/cutout/RefineSession.kt`; new `media/cutout/EditHistory.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/EditHistoryTest.kt`.
- **In scope:** Operation log with checkpoints for smart/brush/reset, "Cut out again" photos as cache temp files, Undo + Redo buttons, 64 MB budget; editor-wiring defects from the G-ADB1 PLAN-CHANGE. History is session-local and temporary (R3-DEC-027): it exists only while a photo is being added, before the garment is saved; it is discarded on save and never kept across app restarts; editing the photo of an already saved garment starts with no history.
- **Out of scope:** Keeping history after the garment is saved or the editor closes; persisting history across restarts; Keep-mode restore (OR3).
- **Acceptance:** V-ED2-1, V-ED2-2, V-ED2-3, V-ED2-4, V-ED2-D.
- **G-ADB1 editor findings (R3-DEC-021):** none supported; step 4 has no defect to fix. Scope is the history only.
- **PLAN-CHANGE 2026-10-23 (R3-DEC-031):** `ui/AddEditClothingScreen.kt` added to Files (one-line call site, `keepsHistory = !isEditing`, PR #149). On a saved garment, undo works only inside the open editor session (the editor's own edits); "Cut out again" photos are not kept in history there and nothing survives closing the editor. A new garment keeps the full history of R3-DEC-027.
- **PLAN-CHANGE 2026-10-23 (PR #149 review, R3-DEC-027):** (1) Brief step 1 named `EditOp.NewPhoto`; the implementation replaced it with `PhotoHistory`, which holds the "Cut out again" photos (cut-out lives there). Accepted. (2) The 64 MB budget stays at its value. Option B (keep it) was chosen by Manu on 2026-10-23. Undo does not reach across a cut-out when the budget cannot hold the photo kept before it (about a 2048x2048 photo at the next edit); within the budget, every step can be undone. (3) History is cleared on save.

## GPU — Developer benchmark: EmbeddingGemma 2 on GPU
- **Reqs:** REQ-OF-02 · **Predecessors:** W0 · **Successors:** DUP1, ED2, K02, PRF
- **Files:** `suggestions/engine/LiteRtNativeEmbedder.kt`; `suggestions/DevBenchmark.kt`; `ui/SettingsScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/GpuBenchmarkReportTest.kt`.
- **In scope:** Backend parameter (CPU default), a developer "Model benchmark (GPU)" row, parity vs CPU, safe failure.
- **Out of scope:** Switching the app to GPU; recipe or model change.
- **Acceptance:** V-GPU-1, V-GPU-2, V-GPU-D.

## OR1 — Keep the original photo and the cutout recipe (local)
- **Reqs:** REQ-OR-01, REQ-OR-02, REQ-OR-03 (new photos) · **Predecessors:** K03, ED2, BAT1, SPK · **Successors:** CLN, EX1, OR2, OR3 · **Independent review: yes**
- **Files:** `media/BackgroundRemovalEngine.kt`; `media/PhotoBackgroundRemover.kt`; `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt`; `media/cutout/PreciseCutoutEngine.kt`; `media/ClothingImageStore.kt`; new `media/OriginalPhotoStore.kt`; `core/model/ClothingModels.kt`; `data/local/Entities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `data/LocalRepositories.kt`; `ui/AddEditClothingScreen.kt`; `ui/BatchAddClothingScreen.kt`; `media/EditorPhotoState.kt`; `app/schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/14.json (generated by CI, committed by the coordinator)`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration13To14Test.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/CutoutRecipeTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/ReplaceLocalWithRemoteTest.kt` (edited, equal strength).
- **In scope:** Engines return the exact input frame + recipe; the frame is saved as JPEG q90 next to the cutout; DB v14 columns; raw copy and `subject-*.png` deleted after a successful save (new photos only).
- **Out of scope:** Drive upload/restore of originals (OR2); editor use of originals (OR3); cleanup of old orphans (CLN); export (EX1).
- **Acceptance:** V-OR1-1, V-OR1-2, V-OR1-3, V-OR1-4, V-OR1-D.
- **PLAN-CHANGE 2026-10-23 (R3-DEC-033):** OR1 review round 1 (NOT SAFE). `data/local/LocalWardrobeSyncSnapshotRepository.kt` is added to Files for B1 only. A sync import never erases a local garment's `original_uri` / `original_recipe`: merge import keeps the local values for ids that exist locally; `replaceLocalWithRemote` reads the local original fields for the ids before `deleteAllItems` and writes them back on records with a matching id (originals of ids absent from the backup stay orphans for CLN). The rule must be JVM-testable. In batch add, `subject-*.png` is deleted only after the garment save succeeds (registered for release on save like the raw copy; deleted on discard or cancel). Drive format for originals stays OR2; deferred items are listed in R3-DEC-033.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-035):** OR1 review round 2 (B1 and B2 hold). Files corrected: the sync repository is `sync/LocalWardrobeSyncSnapshotRepository.kt` (R3-DEC-033 wrote `data/local/`; same file, wrong directory), and `app/src/test/java/com/gusanitolabs/robia/sync/ReplaceLocalWithRemoteTest.kt` is added as an edited test (B1 renamed its target; equal strength, no assertion weakened). New tests (`OriginalPhotoImportTest`, `BatchSubjectFileTest`) are new test files and need no Files entry. Acceptance IDs unchanged. Deferred, not OR1: `RobiaApp.kt` `discardOutput` also deleting `subjectUri` / `originalUri` (cancel-race orphan leak, CLN); in-memory release registry; merge keeping the local original when the remote photo changed (OR3 stale recipe); partial JPEG write.

## OR2 — Back up originals to Google Drive
- **Reqs:** REQ-OR-04 · **Predecessors:** OR1 · **Successors:** DUP2, ZIP · **Independent review: yes**
- **Files:** `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `sync/PhotoBlobKeys.kt`; `sync/PhotoIntegrityCheck.kt`; `sync/DriveUsage.kt`; `ui/DriveSettingsScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/DriveUsageTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/PhotoIntegrityCheckTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/sync/OriginalBlobTest.kt`.
- **In scope:** Optional snapshot fields, `originals/<garmentId>/<hash16>.jpg` blobs, upload/restore/purge/integrity/usage for originals; schemaVersion stays 2.
- **Out of scope:** New schema version; changes to cutout photo upload/restore semantics; zip.
- **Acceptance:** V-OR2-1, V-OR2-2, V-OR2-3, V-OR2-4, V-OR2-D.

## OR3 — Editor: restore background from the original, "Cut out again" for saved garments
- **Reqs:** REQ-OR-05, REQ-ED-04 · **Predecessors:** OR1 · **Successors:** — · **Independent review: yes**
- **Files:** `ui/PhotoEditorScreen.kt`; `media/cutout/RefineSession.kt`; `media/cutout/EditHistory.kt`; new `media/cutout/OriginalMapping.kt`; `ui/AddEditClothingScreen.kt`; `media/EditorPhotoState.kt`; `media/ClothingImageStore.kt`; `ui/BatchAddClothingScreen.kt`; `core/model/ClothingModels.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt` (R3-DEC-039, the sync-import recipe fix only); new `app/src/test/java/com/gusanitolabs/robia/media/cutout/OriginalMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt`.
- **In scope:** Map the original into cutout space with the recipe; Keep paints back RGB+alpha from it; "Cut out again" offered when an original exists; reprocess parity test.
- **Out of scope:** Garments without an original (unchanged behaviour); sync; export.
- **Acceptance:** V-OR3-1, V-OR3-2, V-OR3-3, V-OR3-D.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-036):** The OR3 worker stopped before coding: the recipe says "full frame" for ML Kit, but every Standard cutout is then trimmed by `ClothingImageStore.cropTransparentPixels` (alpha > 24 bounds, 4% padding, min 8 px) at `AddEditClothingScreen.kt`, `BatchAddClothingScreen.kt` and `PhotoEditorScreen.kt`, and that offset is not recorded, so the original-to-cutout mapping would be wrong. Files gain `media/ClothingImageStore.kt` (the crop returns/records its bounds; the cropped output is unchanged), `ui/BatchAddClothingScreen.kt` (batch call site only, to store the offset) and `core/model/ClothingModels.kt` (`CutoutRecipe` gains an optional stored-canvas offset, default null, backward-compatible JSON). The recipe records the offset at import (single and batch) and is rewritten when editor Done changes the stored canvas (the stale-recipe handling deferred from OR1). A garment whose recipe lacks the offset keeps today's behaviour: no Keep-from-original, no Cut out again. No Room schema change, no sync change. Acceptance IDs unchanged.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-039):** OR3 independent review round 1 (NOT SAFE, one BLOCKING item). In `sync/LocalWardrobeSyncSnapshotRepository.kt` (about lines 552-556, `photoUri` from `importedPhotoUri` about 666-667) a sync import takes the restored cutout photo but keeps the LOCAL original recipe (including `storedCanvasOffset`) when no restorable original comes with it; if the remote cutout canvas changed, Keep-from-original paints wrong pixels onto the synced cutout. Files gain `sync/LocalWardrobeSyncSnapshotRepository.kt` (this fix only; this supersedes the "no sync change" line above for this one case). Rule: when a sync import replaces a garment's cutout photo and no restorable original arrives with it, the local recipe (and its `storedCanvasOffset`) is cleared, so the garment falls back to today's behaviour (no Keep-from-original, no Cut out again); when the imported photo is unchanged, nothing changes. A JVM test is required (new test file, no Files entry). Second item (crash class): `PhotoEditorScreen.kt` `loadOriginalLayer` (about line 1236) throws for a missing original file and the `LaunchedEffect` (about 302-306) has no guard; `loadOriginalLayer` now returns null on any I/O failure and the editor then behaves as without an original. Deferred, not OR3: the V-OR3-3 test covers `alphaCropRect`, not `cropTransparentPixels`; Quick Edit save clears the recipe of a pre-OR3 garment; per-RefineSession canvas arrays outside `EditBudget`; `colour()` allocation per render; race of a superseded import. Acceptance IDs unchanged.

## CLN — One-time cleanup of orphan photo files
- **Reqs:** REQ-OR-03 (existing files) · **Predecessors:** OR1 · **Successors:** — · **Independent review: yes**
- **Files:** `media/ClothingImageStore.kt`; new `media/OrphanPhotoCleanup.kt`; `RobiaApplication.kt`; `ui/AddEditClothingScreen.kt` (registration only); new `app/src/test/java/com/gusanitolabs/robia/media/OrphanPhotoCleanupTest.kt`.
- **In scope:** Delete unreferenced `gallery-*`, `camera-*`, `subject-*`, `cropped-subject-*` files older than 24 h once per install (flag), never referenced files.
- **Out of scope:** Restored files, thumbnails, caches, models, originals.
- **Acceptance:** V-CLN-1, V-CLN-2, V-CLN-D.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-037):** CLN review round 1 (NOT SAFE, one BLOCKING item): an unsaved add/edit draft whose photo is older than 24 h would be deleted while in use, because `AddEditClothingScreen.kt` keeps `photoUri` and `originalPhotoUri` in `rememberSaveable` (a draft survives process death and an app update), the one-time cleanup runs at the first ON_START of the new build, and `ActivePhotoRefs` has no register call sites. Files gain `ui/AddEditClothingScreen.kt`, for registration only: register the draft's photo URIs in `ActivePhotoRefs` when they are set or restored and unregister on save or leave; no other behaviour change. The one-time run waits 60 s after the first ON_START (on IO) so a restored draft has registered before the planner runs; the run-once flag is still set only after a completed run. A planner test covers that a registered file older than 24 h is kept. Deferred: JVM test of `deleteOrphans` / the run-once path; `File.delete()` failures are not retried; V-CLN-D label wording (use Android Settings > Apps > Robia > Storage). Acceptance IDs unchanged.

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
- **Files:** new `suggestions/DuplicateScan.kt`; new `ui/DuplicateReviewScreen.kt`; new `ui/DuplicateCalibrationScreen.kt`; `ui/BrowseGrid.kt`; `ui/RobiaApp.kt`; `ui/SettingsScreen.kt`; `data/EmbeddingRepository.kt`; `app/src/main/java/com/gusanitolabs/robia/suggestions/EmbeddingBackfill.kt` (edited); `ui/ReviewScaffold.kt` (destructive colour parameter only); `app/src/main/res/values/strings.xml`, `values-es/strings.xml`, `values-de/strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/EmbeddingBackfillTest.kt` (edited); new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateCalibrationScreenTest.kt`.
- **In scope:** Pure scan (exact input_hash tier + scored candidate pairs), banner, review with Keep/Delete, session-only "Not duplicates", developer calibration list.
- **Out of scope:** Persisted dismissals and the cosine tier (DUP2).
- **Acceptance:** V-DUP1-1, V-DUP1-2, V-DUP1-3, V-DUP1-D.
- **PLAN-CHANGE 2026-10-23 (review round 1, NOT SAFE on PR #152):** ReviewScaffold.kt added to Files (R3-DEC-026). Required: dismissed pairs never merge across groups; banner hidden while merge proposal, review-complete overlay or any sync attention card shows; Delete in error colour; KDoc; scan entry points do not throw.
- **PLAN-CHANGE 2026-10-23 (review round 5, R3-DEC-029):** EmbeddingBackfill.kt and EmbeddingBackfillTest.kt added to Files (edited, not new): the scan waits for the embedding backfill to settle (REQ-DU-04). Duplicate groups and session dismissals survive activity recreation within one process (process-scoped holder beside DuplicateScanHistory; nothing written to disk). The calibration signature is order-insensitive, so wardrobe writes no longer reset the marks.
- **PLAN-CHANGE 2026-10-23 (review round 6, R3-DEC-030):** test file added to Files; groups drop garments that leave the wardrobe from any route; calibration signature includes archive state.
- **PLAN-CHANGE 2026-10-23 (R3-DEC-032):** a review returns SAFE when it has no BLOCKING finding (data loss or wrong deletion, crash or unbounded main-thread work, a violated V-DUP1-1..3 row or REQ-DU-01/03/04, a file outside Files, a weakened existing test); everything else is a DEFERRED follow-up, listed in the PR receipt and in R3-DEC-032, never a reason for another worker round. The round-7 review is the last before merge unless it finds a BLOCKING item, which gets one closed-list worker round checked only against that list; merge also needs CI green on the head and a merge of origin/main.

## DUP2 — Duplicates: "very similar" tier and remembered dismissals
- **Reqs:** REQ-DU-01 (tier 2), REQ-DU-02, REQ-FB-18(c) · **Predecessors:** DUP1, OR2, ZIP, DUP-FAST, DUP-TRIGGER (file order: after DARK3 on `ui/DuplicateReviewScreen.kt`, after SYNC-SPEED on `sync/GoogleDriveWardrobeRepository.kt`) · **Successors:** — · **Independent review: yes** (+ E2)
- **Files:** `suggestions/DuplicateScan.kt`; `ui/DuplicateReviewScreen.kt`; new `data/local/DismissedDuplicateEntities.kt`; `data/local/RobiaDatabase.kt`; `data/local/Dao.kt`; `core/model/SyncModels.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/LocalWardrobeSyncSnapshotRepository.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/data/local/Migration14To15Test.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt`.
- **In scope:** Tier 2 with **`T_DUP = 0.96`** (fixed by Manu, R3-DEC-047; no G-DUP marking) + same category + same primary colour (when both have one), built on DUP-FAST's blocked pass (collect cosine ≥ `T_DUP` inside a category block; no all-pairs list); Room v15 `dismissed_duplicate_pairs`; optional snapshot list; "Not duplicates" writes all pairs of the group and the dismissals are loaded into `DuplicateSession` at start, so a dismissed pair never returns after a restart (0.96 yields false positives for similar black dresses, so dismissals ship with the tier); "Very similar" label.
- **Out of scope:** Changing `T_DUP`; the calibration marking (G-DUP closed); the scan trigger and speed (DUP-TRIGGER, DUP-FAST); UI beyond the "Very similar" label.
- **Acceptance:** V-DUP2-1, V-DUP2-2, V-DUP2-3, V-DUP2-4, V-DUP2-D.
- **PLAN-CHANGE 2026-10-25 (R3-DEC-047, R3-DEC-051):** G-DUP removed from the predecessors; `T_DUP = 0.96` is a constant of the task, not a PLAN-CHANGE value from a gate; DUP-FAST and DUP-TRIGGER added as predecessors; `ui/RobiaApp.kt` is written after NAV-BACK and DUP-TRIGGER.

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
- **Files:** new `ui/AnalyticsScreen.kt`; new `core/model/WardrobeStats.kt`; `ui/BrowseFilterBar.kt`; `ui/BrowseGrid.kt` (entry-point wiring only, R3-DEC-034); `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/core/model/WardrobeStatsTest.kt`.
- **In scope:** Icon next to Filters, route, bar charts per group, tap → Browse filtered; PLAN-CHANGE 2026-10-23 (feedback item 2): on open, bars grow from the axis left to right in one short smooth transition (≤ 400 ms), none when reduced motion is on.
- **Out of scope:** Chart libraries; time series.
- **PLAN-CHANGE 2026-10-23 (R3-DEC-034):** `ui/BrowseGrid.kt` added to Files for the entry point only: one optional-callback parameter on `BrowseWardrobeScreen`, passed to `FilterBar` (about four lines), plus one line in `ui/RobiaApp.kt` to open the route. No other change in `BrowseGrid.kt`; SEL drag select, PRF changes, the duplicates banner and pinch stay as they are. Worker choices accepted, to be checked at V-ANA-D: the colour "Not set" bar is not tappable (Browse has no filter for no colour); zero-count bars are omitted; colours are counted by stored palette id; tapping a bar keeps an active text search.
- **Acceptance:** V-ANA-1, V-ANA-2, V-ANA-3, V-ANA-4, V-ANA-D.

## OUT — Outfit lab (developer)
- **Reqs:** REQ-OF-01, G-OUT · **Predecessors:** ANA, DUP1 · **Successors:** EX1
- **Files:** new `suggestions/OutfitComposer.kt`; new `ui/OutfitLabScreen.kt`; `ui/SettingsScreen.kt`; `ui/RobiaApp.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/OutfitComposerTest.kt`.
- **In scope:** Seed garment or prompt → 3 outfits from existing vectors and tags; why-line; 👍/👎 log; export of ratings.
- **Out of scope:** User-facing entry; new models; training.
- **Acceptance:** V-OUT-1, V-OUT-2, V-OUT-D.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-042):** the lab is dropped by Manu. Merged code is removed by OUT-DROP (Round 3c). V-OUT-D and G-OUT are closed as dropped.

## CHK — Device checklist for round 3
- **Reqs:** all · **Predecessors:** ALL · **Successors:** —
- **Files:** new `docs/specs/robia-round3/artifacts/device-checklist.md`.
- **In scope:** Checklist of every DEV row and gate result, with the latest main staging APK link.
- **Out of scope:** Code.
- **Acceptance:** V-CHK-1.

- **PLAN-CHANGE 2026-10-24 (R3-DEC-041):** CHK also lists the DEV rows of the Round 3b tasks below; its predecessors include them.
- **PLAN-CHANGE 2026-10-24 (R3-DEC-046):** CHK also lists the DEV rows of the Round 3c tasks (OUT-DROP, UI-SMART, DARK1 to DARK4) and REV when it exists; its predecessors include them. V-OUT-D is not listed (dropped).
- **PLAN-CHANGE 2026-10-25 (R3-DEC-051):** CHK also lists the DEV rows of the Round 3d tasks (NAV-BACK, ADD-UX, PICKER-FIX, REV-PROGRESS, DUP-FAST, DUP-TRIGGER; V-DUP2-D as before) and REV when it exists; its predecessors include them. G-DUP and V-DUP1-D are not listed (closed by R3-DEC-047).

---

## Round 3b — device feedback (2026-10-24)

Source: Manu's device feedback of 2026-10-24 (`feedback-2026-10-24` in the planner workspace; items F1–F13 in the REQ-FB lines of `requirements.md`). Rule from Manu: anything not mentioned is approved (R3-DEC-040). All Round 3 tasks above are merged; the Round 3b tasks start from latest `origin/main`. Same worker rules 0–13. Task list and ordering: R3-DEC-041.

**Dependency summary.** Conflict-driven: the four sync tasks share `sync/GoogleDriveWardrobeRepository.kt`, `sync/WardrobeSyncOutboxProcessor.kt` and `ui/RobiaApp.kt`, so they run in a chain; the other four are independent of everything and of each other.
```
SYNC-AUTH   <- (none)
SYNC-BANNER <- SYNC-AUTH
SYNC-ETA    <- SYNC-BANNER
SYNC-SPEED  <- SYNC-AUTH, SYNC-ETA
UI-CAL      <- (none)        (G-DUP is closed by R3-DEC-047: T_DUP = 0.96, no re-marking)
UI-WHITE    <- (none)
EX2         <- (none)
BAT3        <- (none)
CHK         <- ALL (round 3 and 3b)
```
Shared files (writers in order): `sync/GoogleDriveWardrobeRepository.kt` SYNC-AUTH → SYNC-ETA → SYNC-SPEED; `sync/WardrobeSyncOutboxProcessor.kt` SYNC-AUTH → SYNC-BANNER → SYNC-ETA; `ui/RobiaApp.kt` SYNC-BANNER → SYNC-ETA; `sync/DriveHttpErrors.kt` SYNC-AUTH → SYNC-SPEED; `sync/fakes/FakeDriveSnapshotApi.kt` SYNC-AUTH → SYNC-SPEED.

### Open decisions (D-F10 and D-F13 decided by Manu on 2026-10-24; D-F12 direction decided 2026-10-25, design round 3 open)
- **D-F10 (smart background remove, ED1): decided 2026-10-24 (R3-DEC-043, Manu).** Option C: a deterministic pure-Kotlin tap that removes the tapped connected component of the current alpha; the gesture, trail, 60 % guard, history and hint stay. Task UI-SMART (Round 3c below). If V-UI-SMART-D still fails on the device, the next step is removal of the smart tool (option B), not another fix. V-ED1-D is replaced by V-UI-SMART-D.
- **D-F13 (Outfit lab, OUT): decided 2026-10-24 (R3-DEC-042, Manu).** The Outfit lab is removed. Task OUT-DROP (Round 3c below). V-OUT-D and G-OUT are closed as dropped.
- **D-F12 (garment review redesign, colour + tag review): direction A "Pick, then decide" chosen by Manu on 2026-10-25 (R3-DEC-050); decided now: design round 3 for the red "−" language of removed tags (F21, REQ-FB-21).** Mockups: `mockups/review-redesign/`. The REV task is still unwritten and is written after design round 3 (DARK1 is merged, so REV builds on the theme tokens; REQ-FB-12; R3-DEC-046). Semantics fixed by R3-DEC-050: leaving halfway keeps every garment already decided, one tag group per review.
- **F15–F21 (Manu, 2026-10-25, R3-DEC-047 to R3-DEC-051):** F15 → NAV-BACK; F16 + F20 → ADD-UX; F17 → PICKER-FIX; F18 → DUP-FAST, DUP-TRIGGER and DUP2 (T_DUP = 0.96, G-DUP closed); F19 → REV-PROGRESS; F21 → REV (design round 3). Round 3d below.

### Remaining execution order (updated 2026-10-25, R3-DEC-051; first written 2026-10-24, R3-DEC-046)
Merged already: UI-CAL, UI-WHITE, EX2, BAT3, SYNC-AUTH, SYNC-BANNER, SYNC-ETA, OUT-DROP, UI-SMART, DARK1. Remaining, in the order the coordinator starts them; "after" is a file conflict, not a logical dependency, and a task starts as soon as its file predecessors are merged.
1. **SYNC-SPEED** (in independent review round 2). Files: `sync/GoogleDriveWardrobeRepository.kt`, `sync/DriveFileIndex.kt`, `sync/DriveHttpErrors.kt`, the fake API. DUP2 follows it on `sync/GoogleDriveWardrobeRepository.kt`.
2. **DARK2, DARK3, DARK4** (pending; mutually parallel, disjoint Files). DARK2 comes before ADD-UX (`ui/AddEditClothingScreen.kt`) and PICKER-FIX (`ui/GarmentFieldRows.kt`); DARK3 comes before DUP-FAST (`ui/DuplicateCalibrationScreen.kt`) and DUP2 (`ui/DuplicateReviewScreen.kt`); DARK4 shares nothing with Round 3d.
3. **NAV-BACK ∥ DUP-FAST** (disjoint: `ui/RobiaApp.kt` + `ui/BackExitPolicy.kt` against `suggestions/DuplicateScan.kt` + `data/EmbeddingRepository.kt` + `ui/DuplicateCalibrationScreen.kt`). NAV-BACK conflicts with nothing pending and may start at once; DUP-FAST starts after DARK3.
4. **DUP-TRIGGER** (after DUP-FAST on `suggestions/DuplicateScan.kt`, after NAV-BACK on `ui/RobiaApp.kt`).
5. **ADD-UX ∥ PICKER-FIX** (both after DARK2; disjoint: `ui/AddEditClothingScreen.kt` against `ui/ReviewScaffold.kt` + `ui/GarmentFieldRows.kt`).
6. **REV-PROGRESS** (after PICKER-FIX on `ui/ReviewScaffold.kt`; `ui/TagReviewScreen.kt` and `ui/ColorReviewScreen.kt` have no other writer before REV).
7. **DUP2** (after DUP-FAST and DUP-TRIGGER; after DARK3 on `ui/DuplicateReviewScreen.kt`; after NAV-BACK and DUP-TRIGGER on `ui/RobiaApp.kt`; after SYNC-SPEED on `sync/GoogleDriveWardrobeRepository.kt`). Independent review: yes + E2.
8. **REV** (after design round 3 and Manu's confirmation, D-F12; after PICKER-FIX and REV-PROGRESS on `ui/ReviewScaffold.kt`, `ui/TagReviewScreen.kt`, `ui/ColorReviewScreen.kt`). Not yet written.
9. **CHK** (all; last).
Lanes: sync 1 → (DUP2); dark 2 → ADD-UX ∥ PICKER-FIX → REV-PROGRESS → REV; duplicates DUP-FAST → DUP-TRIGGER → DUP2; NAV-BACK ahead of DUP-TRIGGER. At most one worker per shared file at a time.

## SYNC-AUTH — Drive sign-in errors: refresh and retry, correct classification, honest banner (F8)
- **Reqs:** REQ-FB-08 · **Predecessors:** — · **Successors:** SYNC-BANNER, SYNC-SPEED · **Independent review: yes**
- **Files:** `sync/DriveHttpErrors.kt`; `sync/GoogleDriveWardrobeRepository.kt`; `sync/WardrobeSyncOutboxProcessor.kt`; `MainActivity.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/DriveHttpErrorsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveSnapshotApi.kt`.
- **In scope:** On `Unauthorized`, run `authorize()` once more and retry the failed call once before Blocked; 403 `dailyLimitExceeded`, `sharingRateLimitExceeded`, `quotaExceeded` and status `RESOURCE_EXHAUSTED` are Transient (only 401 and 403 `forbidden`/`insufficientPermissions` stay AuthRequired; an unlisted 403 reason or an empty/HTML 403 body becomes Permanent(403), not a sign-in error); the HTTP code and reason appear in the "sync ended" log line for every non-Success outcome, and the Blocked cause is logged for ordinary uploads; the `authBlocked` flow is cleared when a cycle starts; tapping Sign in while no real disconnect exists gives visible feedback (toast) and requests a sync instead of doing nothing; `Retry-After` (seconds form) is parsed into Transient and `withRetry` waits max(backoff, Retry-After), capped at 60 s.
- **Out of scope:** Concurrency, speed, new auth flows or scopes, the merge banner (SYNC-BANNER), Drive settings UI.
- **Acceptance:** V-SYNC-AUTH-1, V-SYNC-AUTH-2, V-SYNC-AUTH-3, V-SYNC-AUTH-4, V-SYNC-AUTH-5, V-SYNC-AUTH-D.

## SYNC-BANNER — Combine/Replace card only while a proposal is pending or combining (F2)
- **Reqs:** REQ-FB-02 · **Predecessors:** SYNC-AUTH · **Successors:** SYNC-ETA · **Independent review: yes** (Replace is destructive)
- **Files:** `ui/RobiaApp.kt` (the combine timeout and the card condition only); `ui/MergeCombineState.kt`; `sync/WardrobeSyncOutboxProcessor.kt` (the `pendingMergeSummary` clearing only); `app/src/test/java/com/gusanitolabs/robia/ui/MergeCombineStateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/MergeProposalScenarioTest.kt`.
- **In scope:** The 90 s combine timeout never fires while a sync cycle is running; Combining/Failed with a newer finished cycle and no pending proposal returns to Idle; the card shows only with a pending proposal or while the phase is Combining; `pendingMergeSummary` clears once the local merge import succeeded, whatever the upload result.
- **Out of scope:** The merge decision logic, the Combine/Replace actions themselves, upload ETA or speed.
- **Acceptance:** V-SYNC-BANNER-1, V-SYNC-BANNER-2, V-SYNC-BANNER-3, V-SYNC-BANNER-4, V-SYNC-BANNER-5, V-SYNC-BANNER-D.
- **Test replaced by this design:** `MergeCombineStateTest.idleAndDoneAndFailedDoNotMove` pinned "Failed never moves". Rewrite it with equal strength: Idle and Done still never move; Failed moves to Idle only with a newer finished cycle and no pending proposal, and stays Failed while a proposal is pending or no newer cycle exists.

## SYNC-ETA — ETA in "Updating cloud backup" (F1)
- **Reqs:** REQ-FB-01 (a) · **Predecessors:** SYNC-BANNER · **Successors:** SYNC-SPEED
- **Files:** `ui/CloudRestoreProgressText.kt`; `ui/RobiaApp.kt` (the restore overlay only); `sync/WardrobeSyncGateway.kt`; `sync/DriveWardrobeRepository.kt` (callback signature only); `sync/GoogleDriveWardrobeRepository.kt` (the photo-progress callback only); `sync/WardrobeSyncOutboxProcessor.kt` (the `onPhotoProgress` lambda only); `app/src/test/java/com/gusanitolabs/robia/sync/UploadProgressForwardingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveWardrobeRepository.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/CloudRestoreProgressTextTest.kt`.
- **In scope:** Reuse `BatchEtaEstimator`; `RestoreUploadProgress` gains `reusedPhotos` (default 0) and the progress callback carries it, so instant Reuse completions do not enter the gap window; the overlay shows the ETA under the Uploading phase with the `batch_eta_*` strings (or new `cloud_restore_eta_*` strings if the wording must differ); no ETA before the first real upload completes.
- **Out of scope:** The download-phase byte ETA (unchanged), upload speed (SYNC-SPEED), the ordinary non-overlay upload path.
- **Acceptance:** V-SYNC-ETA-1, V-SYNC-ETA-2, V-SYNC-ETA-3, V-SYNC-ETA-D.

## SYNC-SPEED — Faster photo upload, safely (F1, speed)
- **Reqs:** REQ-FB-01 (b) · **Predecessors:** SYNC-AUTH, SYNC-ETA · **Successors:** — · **Independent review: yes**
- **Files:** `sync/GoogleDriveWardrobeRepository.kt`; `sync/DriveFileIndex.kt`; `sync/DriveHttpErrors.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/fakes/FakeDriveSnapshotApi.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/PhotoUploadVerifyTest.kt`; `app/src/test/java/com/gusanitolabs/robia/sync/UploadSkipTest.kt`.
- **In scope:** In this order, each kept only if the previous tests stay green: (1) skip the POST when the index lists the same name and size (as `originalReusable` does); (2) skip the find-GET when the full index lacks the name; (3) keep connections alive (disconnect only on error); (4) bounded concurrency 2–3 for upload and attachOriginal using the `Semaphore` pattern of `restorePhotoBlobs`, cancelling siblings on Unauthorized/Transient and dropping to 1 on 429/`Retry-After`; never a duplicate blob (a `findFileByName` failure must not turn into a duplicate POST); a failed cycle does not re-upload blobs already in the index.
- **Out of scope:** Resumable uploads, snapshot format, restore/download speed, ETA wiring, auth handling (SYNC-AUTH).
- **Acceptance:** V-SYNC-SPEED-1, V-SYNC-SPEED-2, V-SYNC-SPEED-3, V-SYNC-SPEED-4, V-SYNC-SPEED-D.
- **Device gate:** the 100-garment upload time is measured after the build and compared with the before number from Manu's report (about 3 s per photo, about 160 s for 114 garments); record the number, no hard target.

## UI-CAL — Duplicate calibration screen: loading and empty states (F9)
- **Reqs:** REQ-FB-09 · **Predecessors:** — · **Successors:** — (G-DUP closed by R3-DEC-047; the screen stays a developer tool)
- **Files:** `ui/DuplicateCalibrationScreen.kt`; `suggestions/DuplicateScan.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateCalibrationScreenTest.kt`.
- **In scope:** A progress indicator while the pairs are not ready; an empty state that shows the counts (active garments, with vector, hash matched, categorised); scan failures logged outside DEBUG; a pure Loading/Empty/Pairs state function with a JVM test.
- **Out of scope:** The pair selection (top 30), thresholds, the Browse duplicate scan behaviour, DUP2.
- **Acceptance:** V-UI-CAL-1, V-UI-CAL-2, V-UI-CAL-D.
- **PLAN-CHANGE 2026-10-25 (R3-DEC-047):** the 30-pair re-marking is no longer asked of Manu; V-UI-CAL-D checks only the loading and empty states.

## UI-WHITE — Neutral grey gradient for white garments (F5)
- **Reqs:** REQ-FB-05 (updates REQ-BR3-02) · **Predecessors:** — · **Successors:** —
- **Files:** `ui/GarmentSwatchBackground.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentSwatchBackgroundTest.kt`.
- **In scope:** `NEAR_WHITE_STOPS` becomes `#F3F3F4` → about `#D6D5D6` (neutral, subtle grey gradient); Browse, detail, colour review and tag review follow; the near-white rule stays.
- **Out of scope:** The rule itself, other colours' gradients, the add-flow backdrops.
- **Acceptance:** V-UI-WHITE-1, V-UI-WHITE-D.
- **Test replaced by this design:** `nearWhiteSwatchesGetTheWarmNeutralStops` pinned `#E6E0D9` → `#CFC5BA`. Rewrite it with equal strength to the new stops, plus an assertion that each stop's RGB channel spread is below 10 (neutral).

## EX2 — Export: Robia frame switch and a visible Preview (F3, F4)
- **Reqs:** REQ-FB-03, REQ-FB-04 · **Predecessors:** — · **Successors:** —
- **Files:** `ui/ExportScreen.kt`; `media/GarmentShareExporter.kt`; `media/PdfLayout.kt`; `data/SettingsRepository.kt`; `core/model/ClothingModels.kt` (`ExportOptions` gains the frame flag only); `app/src/test/java/com/gusanitolabs/robia/ui/ExportOptionsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/PdfLayoutTest.kt`.
- **In scope:** F3: a "Robia frame" switch (the white border and the logo around the image, for Image and PDF), on by default and persisted; it may be switched off only when every other Show option is off, so the output is the pure photo (Original or Robia cutout); with any field on, the switch is on and disabled. F4: Preview gives visible feedback: a loading state while it renders and a smooth scroll to the preview when it is ready; no animated scroll when reduced motion is on.
- **Out of scope:** Zip, sharing several garments, new fonts, export options other than the frame.
- **Acceptance:** V-EX2-1, V-EX2-2, V-EX2-3, V-EX2-4, V-EX2-D.
- **PLAN-CHANGE (R3-DEC-041):** `core/model/ClothingModels.kt` is in Files because `ExportOptions` lives there (one boolean field, backward-compatible default).

## BAT3 — Batch processing: the list follows the garment being processed (F7)
- **Reqs:** REQ-FB-07 · **Predecessors:** — · **Successors:** —
- **Files:** `ui/BatchAddClothingScreen.kt`; new `ui/BatchFollowState.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/BatchFollowStateTest.kt`.
- **In scope:** While a batch processes, the list scrolls smoothly to follow the garment being processed; following stops as soon as the user scrolls manually and resumes via a small "Follow" affordance or when the user returns to the bottom; no scroll animation with reduced motion (jump or none). The follow decision is a pure function in `BatchFollowState.kt`.
- **Out of scope:** Batch ordering, ETA, coordinator concurrency, row layout.
- **Acceptance:** V-BAT3-1, V-BAT3-2, V-BAT3-D.

---

## Round 3c — Manu's answers of 2026-10-24 (F10, F13, dark mode)

Source: R3-DEC-042 to R3-DEC-046. Same worker rules 0–13; all tasks start from latest `origin/main`. Rule 9 ("existing theme tokens only") stays: DARK1 is what adds the dark tokens.

**Dependency summary.** Conflict-driven (see "Remaining execution order" above for the full list with the sync chain).
```
OUT-DROP <- (none)      (file order only: after SYNC-ETA on ui/RobiaApp.kt)
UI-SMART <- (none)      (V-ED1-D replaced by V-UI-SMART-D)
DARK1    <- (none)      (file order only: after SYNC-AUTH on MainActivity.kt, after OUT-DROP on ui/RobiaApp.kt and ui/SettingsScreen.kt)
DARK2    <- DARK1
DARK3    <- DARK1       (file order: after DARK1 on ui/SettingsScreen.kt)
DARK4    <- DARK1       (file order: after UI-SMART on ui/PhotoEditorScreen.kt)
REV      <- DARK1       (not yet written; D-F12)
CHK      <- ALL
```

## OUT-DROP — Remove the Outfit lab (F13)
- **Reqs:** REQ-FB-13 · **Predecessors:** — (file order: after SYNC-ETA) · **Successors:** DARK1 (file order) · **Independent review: yes** (deletion)
- **Files:** delete `ui/OutfitLabScreen.kt`; delete `suggestions/OutfitComposer.kt`; delete `app/src/test/java/com/gusanitolabs/robia/suggestions/OutfitComposerTest.kt`; `ui/SettingsScreen.kt` (the Outfit lab row and the `onOutfitLabClick` parameter only); `ui/RobiaApp.kt` (`RobiaRoute.OutfitLab`, its bottom-bar exclusion, its `when` branch and the `onOutfitLabClick` plumbing only); `res/values/strings.xml`, `res/values-es/strings.xml`, `res/values-de/strings.xml` (the `outfit_lab_*` keys only).
- **In scope:** Remove the Outfit lab: screen, composer, its tests, the Developer › Outfit lab row, the route and navigation, all `outfit_lab_*` strings in the three locales, and with them the "Export ratings" share. Nothing else owned by the lab exists (no Room entity, DAO, ViewModel or repository; its ratings were the text file `filesDir/outfit_lab.jsonl`).
- **Out of scope:** The database and its schema (untouched; no table belonged to the lab); the shared code the lab only read: `data/GarmentVectors`, `EmbeddingRepository`, `RobiaApplication.embeddingRepository`, `EmbeddingRecipe` (search query prefix), `EmbeddingEngineHolder.embedText`, `SearchRanker`, `CategoryScorer.cosine`, `DefaultTags`; a one-time delete of the old ratings file on devices (the file is a few kilobytes of ids and ratings, local only, not part of any snapshot or zip; it stays and is ignored); other Developer rows.
- **Acceptance:** V-OUT-DROP-1, V-OUT-DROP-2, V-OUT-DROP-3, V-OUT-DROP-D.

## UI-SMART — Smart remove: deterministic tap on the loose piece (F10)
- **Reqs:** REQ-FB-10 · **Predecessors:** — · **Successors:** DARK4 (file order) · **Independent review: no**
- **Files:** `ui/PhotoEditorScreen.kt`; `media/cutout/SelectionClip.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PhotoEditorMappingTest.kt` (only if a helper it covers is removed); delete `app/src/test/java/com/gusanitolabs/robia/ui/SelectionBackgroundTest.kt` together with the `compositeOverGrey` helper; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/SmartRemoveUndoTest.kt`.
- **In scope:** In Remove mode a tap removes the connected component of the current alpha (`alpha > 0.5`, 8-connected, `MaskOps.labelComponents`) under the touch, snapped to the nearest component within a small radius; the primary component (largest area) is never removed. A drag keeps the trail and the ≤ 12 sampled points as today and removes every non-primary component its points reach; touching only the primary shows the existing hint and removes nothing. The 60 % guard stays as a backstop. The removal goes through `RefineSession.apply(Remove, mask, hard = true)`, so Undo/Redo, Done and OR3 are unchanged. With Keep selected the smart tool changes nothing and shows a new hint (use Remove or the brush). The model-based call path is removed from the editor: the `selector` lazy and its `close`, `compositeOverGrey`, the developer mask overlay and tap dump (`SelectionOverlay`, `debugSelectionDirectory`, `writeSelectionDump`, `tintedPixels`, `DEBUG_OVERLAY_*`) and `clipToTouched` with its cases, each only after a grep shows no other user.
- **Out of scope:** `InteractiveSelector.kt` (`MediaPipeInteractiveSelector` stays: `PreciseCutoutEngine` uses it as S2), `PreciseCutoutEngine`, `RefineSession`, `EditHistory`, `MaskOps`; the brush and the OR3 Keep brush; restoring removed parts with the smart tool.
- **Acceptance:** V-UI-SMART-1, V-UI-SMART-2, V-UI-SMART-3, V-UI-SMART-4, V-UI-SMART-5, V-UI-SMART-D.
- **Failure rule (R3-DEC-043):** if V-UI-SMART-D fails on the device, the follow-up task removes the smart tool (brush only); no second fix.

## DARK1 — Dark mode: theme, setting, system bars, shared components (F14)
- **Reqs:** REQ-FB-14 · **Predecessors:** — (file order: after SYNC-AUTH on `MainActivity.kt`, after OUT-DROP on `ui/RobiaApp.kt` and `ui/SettingsScreen.kt`) · **Successors:** DARK2, DARK3, DARK4, REV · **Independent review: no**
- **Files:** `core/designsystem/Color.kt`; `core/designsystem/Theme.kt`; `core/model/ClothingModels.kt` (`ThemeMode` and `RobiaSettings.themeMode` only); `data/SettingsRepository.kt`; `MainActivity.kt`; `ui/RobiaApp.kt` (the `RobiaTheme` call, one new `themeMode` parameter and the marker on the `swatchColor` fallback only); `ui/SettingsScreen.kt` (the Appearance group and its dialog only); `ui/GarmentSwatchBackground.kt`; `res/values/styles.xml`; `res/values/colors.xml`; the three `strings.xml`; `app/src/test/java/com/gusanitolabs/robia/core/designsystem/RobiaColorSchemeTest.kt` (new); `app/src/test/java/com/gusanitolabs/robia/data/SettingsMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentSwatchBackgroundTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/ThemeSourceScan.kt` (new helper); `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeSharedScanTest.kt` (new); `app/src/test/java/com/gusanitolabs/robia/media/ExportsIgnoreThemeTest.kt` (new).
- **In scope:** `ThemeMode { Light, Dark, System }` persisted as `theme_mode` (absent or unknown → Light), a `themeMode` flow and `setThemeMode` on the repository (defaults on the interface so existing fakes compile); Settings › Appearance › Theme row and radio dialog (Light, Dark, Follow system), written through `app.settingsRepository` as the cutout rows already do; `RobiaTheme(darkTheme)` with a Robia dark `ColorScheme` (table in the brief), `resolveDarkTheme(mode, systemDark)` and the extra tokens (`isDark`, `photoWellTop` `#F3F3F4`, `photoWellBottom` `#D1CFCF`, identical in both themes); `MainActivity` reads the stored mode once at start (bounded, fallback Light), collects it, passes it to `RobiaApp`, and calls `enableEdgeToEdge` with a `SystemBarStyle` from the resolved flag whenever it changes (bar icon colours, window background); a dark window style; `rememberGarmentSwatchBrush` takes its top stop from the photo-well token (same value as today in Light); the `// theme-fixed: <reason>` line marker and `// theme-fixed-file: <reason>` file marker rules (the file marker only for `GarmentSwatchBackground.kt`) and the scan helper; a guard test that the export code never reads the theme.
- **Out of scope:** Per-screen conversion (DARK2 to DARK4); garment swatch colours; a `values-night` theme or splash change; syncing the setting; any change to how Light looks; exports.
- **Acceptance:** V-DARK1-1, V-DARK1-2, V-DARK1-3, V-DARK1-4, V-DARK1-5, V-DARK1-6, V-DARK1-D.
- **Shared files (writers in order):** `MainActivity.kt` SYNC-AUTH → DARK1; `ui/RobiaApp.kt` SYNC-BANNER → SYNC-ETA → OUT-DROP → DARK1; `ui/SettingsScreen.kt` OUT-DROP → DARK1 → DARK3; `data/SettingsRepository.kt` EX2 (merged) → DARK1.

## DARK2 — Dark mode: Browse, detail, add/edit, batch, tags (F14)
- **Reqs:** REQ-FB-14 · **Predecessors:** DARK1 · **Successors:** — · **Independent review: no**
- **Files:** `ui/BrowseGrid.kt`; `ui/BrowseFilterBar.kt`; `ui/AdvancedFiltersScreen.kt`; `ui/ItemDetailScreen.kt`; `ui/AddEditClothingScreen.kt`; `ui/BatchAddClothingScreen.kt`; `ui/GarmentFieldRows.kt`; `ui/ManageTagsScreen.kt`; `ui/PhotoProcessingVisuals.kt`; `ui/SuggestionsProgressBanner.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeBrowseScanTest.kt`.
- **In scope:** Every colour literal in these files becomes a token or carries `// theme-fixed: <reason>` (known: the swatch borders `Color.Black.copy(alpha …)` in `BrowseGrid.kt` and `PhotoProcessingVisuals.kt` become a token that shows on dark; the shimmer and glow over photos stay); photo backdrops use the photo-well tokens (`BatchAddClothingScreen.kt` row backdrop, `AddEditClothingScreen.kt` photo box) so garments stay visible; an audit of every `containerColor`, `.background(` and `.copy(alpha` for light-only assumptions, with the findings in the receipt.
- **Out of scope:** Layout, spacing or copy changes; `GarmentSwatchBackground.kt` and the swatch palette (DARK1); colour and tag review screens (REV).
- **Acceptance:** V-DARK2-1, V-DARK2-2, V-DARK2-D.

## DARK3 — Dark mode: Settings, Developer, onboarding, sync, duplicates (F14)
- **Reqs:** REQ-FB-14 · **Predecessors:** DARK1 · **Successors:** — · **Independent review: no**
- **Files:** `ui/SettingsScreen.kt`; `ui/DriveSettingsScreen.kt`; `ui/PreciseCutoutSettings.kt`; `ui/LanguagePickerScreen.kt`; `ui/ModelDownloadUi.kt`; `ui/SyncAttentionCard.kt`; `ui/SuggestionDiagnosticsCard.kt`; `ui/DuplicateCalibrationScreen.kt`; `ui/DuplicateReviewScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeSettingsScanTest.kt`.
- **In scope:** The scan test over these files; a token audit as in DARK2 (state colours for Connected, Needs attention, error and merge cards, progress and download rows, dialogs); fixes for what the audit and Manu's device pass find. These files hold no colour literal today, so the PR may be the scan test and the fixes the audit finds.
- **Out of scope:** Layout or copy; the Appearance row itself (DARK1); sync logic.
- **Acceptance:** V-DARK3-1, V-DARK3-2, V-DARK3-D.

## DARK4 — Dark mode: photo editor, analytics, export screen (F14)
- **Reqs:** REQ-FB-14 · **Predecessors:** DARK1 (file order: after UI-SMART) · **Successors:** — · **Independent review: no**
- **Files:** `ui/PhotoEditorScreen.kt`; `ui/AnalyticsScreen.kt`; `ui/ExportScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeEditorScanTest.kt`.
- **In scope:** The editor keeps its transparency checkerboard, Light-tab backdrop, temperature preset swatches and over-photo chips as they are (each literal marked `// theme-fixed: <reason>`); the editor chrome (top bar, mode chips, tool row, sheets, snackbar) follows the theme; analytics bars, axis, legend and empty state have contrast in dark; the Export screen's chrome follows the theme while the preview bitmap and the exported file are unchanged, with the preview separated from the dark page by an outline.
- **Out of scope:** `media/GarmentShareExporter.kt`, `media/PdfLayout.kt` and every export output; editor behaviour; the smart tap (UI-SMART); colour and tag review screens (look-only check in V-DARK4-D).
- **Acceptance:** V-DARK4-1, V-DARK4-2, V-DARK4-D.

---

## Round 3d — Manu's feedback of 2026-10-25 (F15–F21)

Source: R3-DEC-047 to R3-DEC-051 and the planner investigation `ux2.md` (file:line leads, verified against main `ebd6467` for the ones the briefs rely on; F18 and F19 timings are estimates). Same worker rules 0–13; all tasks start from latest `origin/main`; line numbers in the briefs may be a few lines off after DARK2. Rule 2 applies: `strings.xml` in three locales and new tests are always allowed.

**Dependency summary.** Conflict-driven (full order under "Remaining execution order").
```
NAV-BACK     <- (none)           (first writer of ui/RobiaApp.kt)
ADD-UX       <- DARK2            (ui/AddEditClothingScreen.kt)
PICKER-FIX   <- DARK2            (ui/GarmentFieldRows.kt)
REV-PROGRESS <- PICKER-FIX       (ui/ReviewScaffold.kt)
DUP-FAST     <- (none)           (file order: after DARK3 on ui/DuplicateCalibrationScreen.kt)
DUP-TRIGGER  <- DUP-FAST, NAV-BACK
DUP2         <- DUP1, OR2, ZIP, DUP-FAST, DUP-TRIGGER   (file order: after DARK3, SYNC-SPEED)
REV          <- DARK1, design round 3, PICKER-FIX, REV-PROGRESS   (not yet written)
CHK          <- ALL
```

## NAV-BACK — Back peels one layer, then asks twice to exit (F15)
- **Reqs:** REQ-FB-15 · **Predecessors:** — · **Successors:** DUP-TRIGGER (file order on `ui/RobiaApp.kt`) · **Independent review: no**
- **Files:** `ui/RobiaApp.kt` (the new root `BackHandler` and its state only); new `ui/BackExitPolicy.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/BackExitPolicyTest.kt`.
- **In scope:** A pure `decideBack(state, nowMs)` in `BackExitPolicy.kt` (state: route is Browse or Manage, selection count, search query, similar-to garment, active filters, time of the last exit hint) returning one of ClearSelection, ClearSearchAndFilters, GoToBrowse, ShowExitHint, Exit. One new `BackHandler` in `RobiaApp.kt` after the existing one, enabled when `routeStack.size == 1`, no restore is in progress and the route is Browse or Manage; it applies the action: selection cleared alone; query, similar-to and filters cleared together; Manage → `replaceRoute(Browse)`; the hint is a `Toast` "Press back again to exit" (`Toast.LENGTH_SHORT`, about 2 s) and a second press inside 2000 ms calls `finish()` on the host activity (`findActivity()` exists in the file). The exit window resets when the route, query, filters or selection change. Strings `back_exit_hint` x3.
- **Out of scope:** The existing handler at `routeStack.size > 1`; Add's own `BackHandler` (discard dialog, then Browse); the review and batch handlers; dialogs and sheets (they keep priority); predictive back; any other `RobiaApp.kt` code.
- **Acceptance:** V-NAV-BACK-1, V-NAV-BACK-2, V-NAV-BACK-3, V-NAV-BACK-D.

## ADD-UX — Add screen: auto-scroll to the photo and the batch hint (F16 + F20)
- **Reqs:** REQ-FB-16, REQ-FB-20 · **Predecessors:** DARK2 · **Successors:** — · **Independent review: no**
- **Files:** `ui/AddEditClothingScreen.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/AddPhotoScrollTest.kt`.
- **In scope:** (F16) `rememberLazyListState()` on the screen's `LazyColumn`, a non-saveable `scrollRequest` counter incremented only in `queuePhotoForProcessing`, and a `LaunchedEffect(scrollRequest)` that skips 0, waits (bounded) until the photo card item is in `visibleItemsInfo`, then scrolls so the photo's top edge sits at the viewport's top edge: smooth (`animateScrollBy`, about 350 ms) or instant under `LocalReducedMotion`; no scroll when the photo is already at or above the target (a user who scrolled further is left alone, threshold about 8 dp); a user drag preempts it; rotation does not re-trigger. Pure `internal fun photoScrollDelta(...)` in this file. (F20, R3-DEC-049) the Add-mode gallery button reads "Gallery · pick several" (`gallery_multi`; ES "Galería · elige varias", DE "Galerie · mehrere") and a hint line under the photo buttons "Tip: pick several photos at once to add them as a batch." (`add_batch_hint`) shows while a new garment has no photo; pure `internal fun batchHintVisible(isEditing, hasPhoto)`. Neither change in edit mode (label stays "Gallery", no hint).
- **Out of scope:** The batch screen and its helper text; the picker sheets (PICKER-FIX); processing, photo pipeline, the editor; any other scroll behaviour of the screen; a persistent one-time tip (option C).
- **Acceptance:** V-ADD-UX-1, V-ADD-UX-2, V-ADD-UX-3, V-ADD-UX-D.

## PICKER-FIX — Pickers do not jump or lose the option count (F17)
- **Reqs:** REQ-FB-17 · **Predecessors:** DARK2 · **Successors:** REV-PROGRESS (file order on `ui/ReviewScaffold.kt`), REV · **Independent review: no**
- **Files:** `ui/ReviewScaffold.kt` (`ReviewPickerList` only); `ui/GarmentFieldRows.kt` (`TagPickerSheet` only); new `app/src/test/java/com/gusanitolabs/robia/ui/PickerStabilityTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentFieldRowsTest.kt` and `ReviewScrollHintTest.kt` (only if a helper they cover changes; existing assertions stay).
- **In scope:** The three causes of `ux2.md` F17. (1) The "N options" caption is composed from a pure `pickerCaptionVisible(optionCount, maxValue)` that does not depend on the scroll position (true when the list is long enough to scroll, decided from the option count at first composition and from `maxValue > 0` afterwards); only the bottom fade follows `scrollHintVisible(value, max)` and it is an overlay, so it never changes the height. (2) The suggested-hint row in `TagPickerSheet` is reserved (composed with alpha 0 and no semantics) whenever any suggested tag belongs to the sheet's category, instead of appearing and disappearing with the selection; a pure `sheetReservesSuggestedHint(suggestedIds, categoryTagIds)`. (3) `rememberModalBottomSheetState(skipPartiallyExpanded = true)` for `TagPickerSheet`.
- **Out of scope:** Row design, sorting, the review dialogs' own layout, strings, other sheets; REV's redesign of the review screens.
- **Acceptance:** V-PICKER-FIX-1, V-PICKER-FIX-2, V-PICKER-FIX-3, V-PICKER-FIX-D.

## REV-PROGRESS — Review progress bar follows the count (F19)
- **Reqs:** REQ-FB-19 · **Predecessors:** PICKER-FIX (file order on `ui/ReviewScaffold.kt`) · **Successors:** REV · **Independent review: no**
- **Files:** `ui/TagReviewScreen.kt` (the progress state only); `ui/ColorReviewScreen.kt` (the progress state only); `ui/ReviewScaffold.kt` (the pure function and, if needed, the progress bar only); new `app/src/test/java/com/gusanitolabs/robia/ui/ReviewProgressTest.kt`.
- **In scope:** A pure `reviewScanProgress(done, total, scanning)` (while scanning: `done / max(total, 1)` clamped to 0..1; not scanning: 1); both screens feed `ReviewProgressHeader` that value directly and the `animateFloatAsState(tween(300))` is removed (it restarted at every step, about 20-40 ms apart, and lagged far behind). `SCAN_STEP_MILLIS` (`suggestions/ReviewPlanner.kt`) is not changed: one step is 0.45 % at 222 garments, so the raw fraction already looks continuous, and the file is outside Files.
- **Out of scope:** The counts and their texts (correct already), `ReviewPlanner`, the review layout and semantics (REV), any easing or spring.
- **Acceptance:** V-REV-PROGRESS-1, V-REV-PROGRESS-2, V-REV-PROGRESS-D.

## DUP-FAST — Duplicate scan and calibration: skip fresh hashes, block by category (F18 speed)
- **Reqs:** REQ-FB-18(a), REQ-DU-04 · **Predecessors:** — (file order: after DARK3 on `ui/DuplicateCalibrationScreen.kt`) · **Successors:** DUP-TRIGGER, DUP2 · **Independent review: no**
- **Files:** `suggestions/DuplicateScan.kt`; `data/EmbeddingRepository.kt`; `ui/DuplicateCalibrationScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`.
- **In scope:** (1) `GarmentVectors` gains `computedAtEpochMillis: Long? = null`, filled from the IMAGE row (in-memory only; no Room change); a garment whose vector is provably fresh (`computedAt != null` and `item.updatedAtEpochMillis <= computedAt`) is not re-hashed: its stored `imageInputHash` is used as the current hash; unknown or newer → hashed as today. (2) The remaining hashes run with bounded parallelism (`async` with `Semaphore(3)`; the hasher is injected so the logic is JVM-testable). (3) `calibrationPairs` groups the eligible garments by category, packs the normalised vectors into one `FloatArray` per block, compares with float dots and keeps the best `top` pairs in a bounded heap, with no list of all pairs; the pass is an internal `scanBlocks`-style function that DUP2 reuses for "cosine ≥ T_DUP". The result (pairs, order, tie-break by id) equals the old algorithm. (4) A progress callback `(done, total)` from the hashing phase and `yield()` in chunks; the calibration screen shows it (determinate bar and a short label, `duplicate_calibration_progress` x3) in place of the bare spinner.
- **Out of scope:** Any threshold or tier (DUP2), the trigger (DUP-TRIGGER), Room and sync, the exact-tier rules, the Browse banner, the pair selection (top 30), the UI-CAL state function.
- **Acceptance:** V-DUP-FAST-1, V-DUP-FAST-2, V-DUP-FAST-3, V-DUP-FAST-4, V-DUP-FAST-5, V-DUP-FAST-6, V-DUP-FAST-D.

## DUP-TRIGGER — Scan whenever the garments change, record only success (F18 trigger)
- **Reqs:** REQ-FB-18(b) · **Predecessors:** DUP-FAST, NAV-BACK · **Successors:** DUP2 · **Independent review: no**
- **Files:** `suggestions/DuplicateScan.kt`; `ui/RobiaApp.kt` (the duplicate-scan `LaunchedEffect` block only); `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt`.
- **In scope:** A pure order-insensitive `duplicateScanSignature(items)` over the active garments (id and `photoUri`); `duplicateScanDue(lastSignature, signature)` replaces the first-run / 5-new / 24 h rule, which is deleted with `DUPLICATE_RESCAN_NEW_GARMENTS` if unused; `scanDuplicates` returns an outcome (Success with groups, or Failed) instead of swallowing an exception into an empty list; the history stores the signature only on Success, and a Failed scan neither records nor replaces the current groups; the effect keys on the signature so a change during a scan restarts it. Unchanged: Browse only, backfill settled, wardrobe loaded.
- **Out of scope:** Scan cost (DUP-FAST), the cosine tier and dismissals (DUP2), the banner, persisting the history to disk, any other `RobiaApp.kt` code.
- **Acceptance:** V-DUP-TRIGGER-1, V-DUP-TRIGGER-2, V-DUP-TRIGGER-3, V-DUP-TRIGGER-D.
- **Test replaced by this design:** the `duplicateScanDue` assertions in `DuplicateScanTest` (about lines 149-153) pin the 5-new / 24 h rule. Rewrite them with equal strength for the signature rule (see V-DUP-TRIGGER-1). V-DUP1-3's "once per 24 hours, 5 new garments" text is superseded.
