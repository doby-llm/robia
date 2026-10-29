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
- **PLAN-CHANGE 2026-10-26 (R3-DEC-055):** "REV when it exists" is now the Round 3e tasks REV-TOKENS, REV-MODEL, REV-SESSION, REV-COLOR, REV-CARD, REV-SAVE, REV-UNDO, REV-BULK and REV-DONE (predecessors of CHK); CHK lists their one DEV row, V-REV-D.
- **PLAN-CHANGE 2026-10-29 (R3-DEC-056, R3-DEC-068):** the checklist is marked with Manu's feedback 3 (not mentioned = pass) and gains the Round 3f DEV rows; the new docs task FB3-CHK (Round 3f) finishes it after the 3f code tasks.

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

### Open decisions (D-F10 and D-F13 decided by Manu on 2026-10-24; D-F12 decided by Manu on 2026-10-26)
- **D-F10 (smart background remove, ED1): decided 2026-10-24 (R3-DEC-043, Manu).** Option C: a deterministic pure-Kotlin tap that removes the tapped connected component of the current alpha; the gesture, trail, 60 % guard, history and hint stay. Task UI-SMART (Round 3c below). If V-UI-SMART-D still fails on the device, the next step is removal of the smart tool (option B), not another fix. V-ED1-D is replaced by V-UI-SMART-D.
- **D-F13 (Outfit lab, OUT): decided 2026-10-24 (R3-DEC-042, Manu).** The Outfit lab is removed. Task OUT-DROP (Round 3c below). V-OUT-D and G-OUT are closed as dropped.
- **D-F12 (garment review redesign, colour + tag review): decided by Manu on 2026-10-26 (R3-DEC-052).** Direction A "Pick, then decide" (R3-DEC-050) with the signed chips of design round 3 (`mockups/review-redesign-3/`): green "+" for added tags, red "−" with strike-through for removed tags, never colour alone. Card semantics R3-DEC-053 (coordinator, overturnable), what the code forces R3-DEC-054, the nine tasks REV-TOKENS to REV-DONE R3-DEC-055 (Round 3e below). The colour review keeps its screen and gains one description line.
- **F15–F21 (Manu, 2026-10-25, R3-DEC-047 to R3-DEC-051):** F15 → NAV-BACK; F16 + F20 → ADD-UX; F17 → PICKER-FIX; F18 → DUP-FAST, DUP-TRIGGER and DUP2 (T_DUP = 0.96, G-DUP closed); F19 → REV-PROGRESS; F21 → REV (Round 3e). Round 3d below.

### Remaining execution order (updated 2026-10-29, R3-DEC-068; earlier versions R3-DEC-046 and R3-DEC-055)
Merged already: every task above this line (W0 to CHK, Round 3b, 3c, 3d and 3e, FIX-GRAY #209) and the device checklist (#213). Remaining: Round 3f (below), in the order the coordinator starts them; "after" is a file conflict, not a logical dependency, and a task starts as soon as its file predecessors are merged.
1. **Lane C, any time, in parallel:** FB3-CUT-NAMES ∥ FB3-GPU-DROP ∥ FB3-SMART-DROP (disjoint files; the three `strings.xml` one at a time). FB3-GPU-DROP and FB3-SMART-DROP: independent review yes.
2. **Lane D:** FB3-DUP-ROW → FB3-DUP-DONE → FB3-BACK. FB3-DUP-DONE and FB3-BACK: independent review yes.
3. **Lane A (tag review, serial on `ui/TagReviewScreen.kt`):** FB3-SPARK-HINT → FB3-NOSNACK → FB3-NOPROMPT → FB3-COMPLETE → FB3-CHIP-PICK. FB3-COMPLETE also waits for FB3-DUP-DONE (`ui/ReviewCompleteOverlay.kt`). FB3-CHIP-PICK: independent review yes.
4. **Lane B:** FB3-PROGRESS (`ui/TagReviewCard.kt`, `ui/ReviewScaffold.kt`), anywhere before FB3-CHIP-PICK (`ui/TagReviewCard.kt`).
5. **FB3-CHK** (docs; all; last).
At most one worker per shared file at a time. `ui/RobiaApp.kt`: FB3-DUP-DONE → FB3-BACK. `ui/TagReviewScreen.kt`: the five lane A tasks. `ui/ReviewCompleteOverlay.kt`: FB3-DUP-DONE → FB3-COMPLETE. `ui/TagReviewCard.kt`: FB3-PROGRESS → FB3-CHIP-PICK. `ui/DuplicateReviewScreen.kt`: FB3-DUP-ROW → FB3-DUP-DONE. `ui/PhotoEditorScreen.kt`: FB3-SMART-DROP only. `ui/SettingsScreen.kt`, `suggestions/DevBenchmark.kt`: FB3-GPU-DROP only. `core/model/ClothingModels.kt`, `data/SettingsRepository.kt`, `media/cutout/CutoutOptions.kt`, `ui/PreciseCutoutSettings.kt`: FB3-CUT-NAMES only.

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
REV      <- DARK1       (written in Round 3e: REV-TOKENS to REV-DONE, R3-DEC-055)
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
REV          <- DARK1, design round 3, PICKER-FIX, REV-PROGRESS   (written in Round 3e: REV-TOKENS to REV-DONE, R3-DEC-055)
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

---

## Round 3e — Garment review redesign (REV, F12 + F21)

Source: R3-DEC-052 to R3-DEC-055 (Manu approved the design on 2026-10-26; mockups `mockups/review-redesign-3/` for the signed chips, `mockups/review-redesign-2/pick-then-decide.html` for the card flow). Same worker rules 0–13; all tasks start from latest `origin/main`; line numbers in the briefs may be a few lines off. Rule 2 applies: `strings.xml` in three locales and new tests are always allowed. Rule 9 is refined: new UI uses the existing theme tokens plus the add/remove tokens of REV-TOKENS; a colour literal needs a `// theme-fixed: <reason>` marker (the DARK scan tests are in force); contrast ≥ 4.5:1 in light and dark; tap targets ≥ 48 dp.

**Dependency summary.** Conflict-driven (full order under "Remaining execution order").
```
REV-TOKENS  <- (none)           (DARK1 merged)
REV-MODEL   <- (none)
REV-COLOR   <- (none)           (first writer of the strings.xml blocks)
REV-SESSION <- REV-MODEL
REV-CARD    <- REV-TOKENS, REV-MODEL, REV-SESSION, REV-COLOR   (file order on strings.xml)
REV-SAVE    <- REV-CARD, DUP-TRIGGER                          (ui/RobiaApp.kt; with DUP2 one at a time)
REV-UNDO    <- REV-SAVE
REV-BULK    <- REV-UNDO
REV-DONE    <- REV-BULK
CHK         <- ALL
```
Shared files (writers in order): `ui/TagReviewScreen.kt` REV-CARD → REV-SAVE → REV-UNDO → REV-BULK → REV-DONE; `ui/RobiaApp.kt` DUP-TRIGGER → (DUP2 | REV-SAVE); `ui/ReviewScaffold.kt` REV-UNDO only; `ui/ColorReviewScreen.kt` REV-COLOR only; `core/designsystem/Color.kt` and `Theme.kt` REV-TOKENS only; the three `res/values*/strings.xml` REV-COLOR → REV-CARD → REV-SAVE → REV-UNDO → REV-BULK → REV-DONE (one `<!-- REV-… -->` block each). Every other new file has one owner.

**What is reviewed (from the code, R3-DEC-054).** Only category and occasion groups, one group per review. A change is a set difference inside the group: removed = before − proposed, added = proposed − before. Locked removal = a tag that no longer exists (deleted before the review opened). Swap = a category group with exactly one removed and one added tag.

## REV-TOKENS — Add and remove colour tokens for the review chips (F21)
- **Reqs:** REQ-FB-21, REQ-FB-12 · **Predecessors:** — · **Successors:** REV-CARD · **Independent review: no**
- **Files:** `core/designsystem/Color.kt`; `core/designsystem/Theme.kt` (`RobiaExtras`, `RobiaLightExtras`, `RobiaDarkExtras` and the new colour holder only); new `app/src/test/java/com/gusanitolabs/robia/core/designsystem/ReviewChangeColorsTest.kt`.
- **In scope:** `RobiaChangeColors(sign, onSign, container, onContainer, border)` and two members on `RobiaExtras`, `addColors` and `removeColors`, with the light and dark values of the mockup (table in the brief); both extras instances filled. The pairs are tested for WCAG contrast ≥ 4.5:1 in light and dark: sign against the card (`surfaceContainerLowest` of the scheme), on-container against container, on-sign against sign; the existing light scheme hex pins and dark table stay green.
- **Out of scope:** Any change to `ColorScheme` roles, to a screen, to the swatch palette; the border colour contrast (decorative; the sign and the fill carry the meaning).
- **Acceptance:** V-REV-TOKENS-1, V-REV-TOKENS-2.

## REV-MODEL — What a review card says and what applying does (pure; F12 + F21)
- **Reqs:** REQ-FB-12, REQ-FB-21 · **Predecessors:** — · **Successors:** REV-SESSION, REV-CARD, REV-SAVE · **Independent review: yes** (defines the apply and revert of user data)
- **Files:** new `suggestions/ReviewChanges.kt`; new `app/src/test/java/com/gusanitolabs/robia/suggestions/ReviewChangesTest.kt`.
- **In scope:** (1) `ReviewChange` = `Add(tagId)`, `Remove(tagId, locked)`, `Swap(fromId, toId, fromLocked)` with a stable `key`, derived from a `ReviewProposal` inside one group (`reviewChangesFor`), with `needsChoice` for a `ChooseManually` proposal; adds of tags that do not exist now are dropped; tags of other groups are ignored. (2) The fixed order: swaps, removals, additions, each alphabetical by label (collator, primary strength), ties by id. (3) The primary button model from the changes and the left-out keys: `Apply(n)` (a swap counts as one), `ApplyPartial(on, of)`, `KeepAsIs`, `GotIt` (only locked chips); Reject is shown with the first two only. (4) `noTagsLeft` for the quiet note (occasion group, the garment has occasions now, none after). (5) `TagDelta`, `AppliedDelta` and `applyDelta(live, delta, existingTags, group, now)`: live tags − removed + added inside the group, adds only for tags that exist now, a category group never ends with two categories (the add applies only when no other category remains, and the removal only with it), an archived or missing garment is left alone, the result carries the delta that actually changed; `revertDelta` is `applyDelta` of the inverse of that effective delta, so a tag the user changed in between is not touched.
- **Out of scope:** `ReviewPlanner` and its proposals, any UI, any write, sync, session state (REV-SESSION).
- **Acceptance:** V-REV-MODEL-1, V-REV-MODEL-2, V-REV-MODEL-3, V-REV-MODEL-4, V-REV-MODEL-5, V-REV-MODEL-6.

## REV-SESSION — Decision state of a tag review (pure; F12 + F21)
- **Reqs:** REQ-FB-12, REQ-FB-21 · **Predecessors:** REV-MODEL · **Successors:** REV-CARD, REV-SAVE, REV-UNDO, REV-BULK, REV-DONE · **Independent review: no**
- **Files:** new `ui/TagReviewSession.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewSessionTest.kt`.
- **In scope:** An immutable `TagReviewSession` over the cards in scan order: per card the changes, the left-out keys, the chosen tag of a `needsChoice` card and the decision `Pending`, `Applied(intended, applied?)` (`applied == null` while the write is in flight) or `Rejected`. Pure transitions: add a card (idempotent by garment id), `toggle` (a locked removal never toggles; a swap with a locked half toggles only the replacement), `choose`, `apply` (the button decides: `Apply*` → Applied with the delta, `KeepAsIs` → Rejected, `GotIt` → Applied with an empty delta and no write), `reject`, `appliedResult`, `applyFailed` (back to Pending), `revert` (Applied with a result or Rejected → Pending, chip choices kept, returns the delta to reverse, none for Rejected and empty deltas; refused while a write is in flight). Queries: left count, pending cards in scan order, reviewed cards most recent first, applied-garment count (non-empty intended delta, in flight included), `bulkPlan` (cards with something to apply or to dismiss; `needsChoice` without a choice stays out; counts of tags added, tags removed and garments, locked removals not counted as removed), `applyBulk`, completion tallies (garments updated, tags added, tags removed, garments kept) and `isComplete(scanning)` (scan ended, at least one card, none Pending), `leaveKind(appliedCount)` = `Discard` (0) or `Leave`.
- **Out of scope:** Compose, writes, strings, the planner, the model's apply and revert (REV-MODEL).
- **Acceptance:** V-REV-SESSION-1, V-REV-SESSION-2, V-REV-SESSION-3, V-REV-SESSION-4.

## REV-COLOR — Colour review: one description line (F12, Manu 2026-10-26)
- **Reqs:** REQ-FB-12 · **Predecessors:** — · **Successors:** REV-CARD (file order on `strings.xml`) · **Independent review: no**
- **Files:** `ui/ColorReviewScreen.kt` (one new line of text only); the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/ColorReviewHintTest.kt`.
- **In scope:** One line of body text under the progress header: "Robia suggests the colors on the right. Tap one to change it before you accept." (`color_review_tap_hint`; ES "Robia propone los colores de la derecha. Toca uno para cambiarlo antes de aceptar.", DE "Robia schlägt die Farben rechts vor. Tippe auf eine, um sie vor dem Akzeptieren zu ändern."), `bodyMedium` in `onSurfaceVariant`, shown while there is at least one card, no new colour literal.
- **Out of scope:** Every other part of the colour review (cards, buttons, bottom bar, counts, dialogs, semantics), the shared scaffold.
- **Acceptance:** V-REV-COLOR-1, V-REV-COLOR-2.

## REV-CARD — Tag review cards with signed chips (F12 + F21; still one commit at the end)
- **Reqs:** REQ-FB-12, REQ-FB-21 · **Predecessors:** REV-TOKENS, REV-MODEL, REV-SESSION, REV-COLOR · **Successors:** REV-SAVE · **Independent review: yes** (the commit now writes the per-chip choices; the reviewer checks that a chip left out is never written and a kept tag is never removed)
- **Files:** `ui/TagReviewScreen.kt` (the screen body, the candidate card and the picker use; the offer, change-set and undo-plan functions stay); new `ui/ReviewChangeChips.kt`; new `ui/TagReviewCard.kt`; new `ui/TagReviewCardModel.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCardModelTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DarkThemeReviewScanTest.kt`.
- **In scope:** Each candidate is a card (container `surfaceContainerLowest`, `outlineVariant` border): thumbnail and a description line "Category · Colour" (never the garment name), "Now: …" for the occasion group, the chips in the fixed order with the REV-TOKENS colours (filled sign circle, strike-through on removals, hollow sign and dashed ghost for an add left out, plain chip for a removal kept, one framed unit for a swap, locked removals static), each chip a switch (36 dp chip in a 48 dp row, TalkBack state and action text, 150 ms cross-fade, none under reduced motion, a light haptic tick, chips never re-sort), the quiet "no occasions" and "all changes skipped" lines, and the footer: Reject and the counted primary button (`Apply n changes` / `Apply n of m` / `Keep as is` / `Got it`), both 48 dp, Apply the wider. A pinned header above the list: "N left" and the review progress bar once the scan has ended (while it runs, the existing scan header and its REV-PROGRESS bar stay), plus the existing change-set header texts and one hint line. The list shrinks with the existing exit animation. A `ChooseManually` card shows the locked chip and a "Choose" button that opens the existing single-choice picker; the choice becomes a "+" chip; the picker is removed from every other card. Per-tag toggles live in the REV-SESSION state; Apply and Reject still go into today's accepted and discarded id lists and the one commit at the end (Done, Accept all, auto commit) now writes each accepted garment through REV-MODEL's `applyDelta` on its snapshot copy with the chip choices; Back and Discard are unchanged. The bottom bar and its strings stay as they are. `DarkThemeReviewScanTest` scans the review files for unmarked colour literals.
- **Out of scope:** Saving per garment and the Back dialog (REV-SAVE), Undo, Reviewed section, Apply all remaining wording (REV-UNDO, REV-BULK), completion (REV-DONE), the colour review, `ReviewScaffold.kt`, `ReviewPlanner`, `RobiaApp.kt`.
- **Acceptance:** V-REV-CARD-1, V-REV-CARD-2, V-REV-CARD-3, V-REV-CARD-4, V-REV-CARD-5.
- **Tests that stay as they are:** `TagReviewAcceptAllTest` and the tag part of `ReviewDiscardTest` (the id lists and `shouldAutoCommit` are untouched here; REV-SAVE replaces them).

## REV-SAVE — Save each garment as it leaves the list; leave halfway (F21, R3-DEC-050)
- **Reqs:** REQ-FB-21, REQ-FB-12 · **Predecessors:** REV-CARD, DUP-TRIGGER (file order on `ui/RobiaApp.kt`; DUP2 one at a time) · **Successors:** REV-UNDO · **Independent review: yes** (writes user data)
- **Files:** `ui/TagReviewScreen.kt`; `ui/RobiaApp.kt` (the tag review wiring only: `commitTagReview`, `discardTagReview`, `requestTagReviewDiscard`, the discard dialog, the `TagReviewScreen` call, a new serial apply path next to `ReviewWrites`); the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewSaveTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewAcceptAllTest.kt` and `app/src/test/java/com/gusanitolabs/robia/ui/ReviewDiscardTest.kt` (the tag assertions replaced, equal strength).
- **In scope:** The screen is driven by `TagReviewSession`. Apply (and Keep as is, Got it, Reject) decides the garment at once: Reject, Keep as is and Got it write nothing; Apply sends its `TagDelta` to `onApply`, a suspend callback that returns the effective `AppliedDelta` (or a failure that puts the card back with a snackbar "Couldn't save. The garment is back in the list."). RobiaApp runs it in the same serial chain as the other review writes: wait for the previous write, plain-read the live garments and the live tags (never `readGarmentsForReview`), `applyDelta`, `wardrobeRepository.upsertItems` for the changed garments, `WardrobeSyncOperation.UpsertItem` for each, detached as `ReviewWrites.launch` does. `onRevert` (wired now, used by REV-UNDO) runs the same chain with `revertDelta`. The commit-at-end path, `tagReviewCommitCandidates`, `tagReviewOpenAcceptableCount` and `shouldAutoCommit` are removed; when the scan has ended and no card is Pending the screen calls `onFinish(changedGarments)`, which keeps today's completion overlay and route to Manage tags for now. Leaving: the screen reports the applied-garment count; Back with 0 applied asks today's question (Discard undoes the whole tag change), with ≥ 1 applied asks "Leave the review?" (`review_leave_title`, plural `review_leave_body`: "N garments are already updated. The rest stay as they are.", `review_leave`) and Leave keeps the tag change and every applied garment and goes to Manage tags; Keep reviewing is unchanged. A pending write finishes when the screen is left (it runs on the app scope).
- **Out of scope:** Undo, Reviewed section, bulk, completion screen (later tasks), the offer, snapshot and prompt code, `tagReviewUndoPlan`, the colour review, DB schema.
- **Acceptance:** V-REV-SAVE-1, V-REV-SAVE-2, V-REV-SAVE-3, V-REV-SAVE-4, V-REV-SAVE-5.
- **Tests replaced by this design:** `TagReviewAcceptAllTest` (both tests) and in `ReviewDiscardTest` `discardedSuggestedCardIsNotCommitted`, `discardedManualChoiceIsNotCommittedEvenWhenAcceptedBefore`, `acceptAllCountsOpenSuggestedCardsOnly`, `reviewClosesWhenEveryCardIsAcceptedOrDiscarded` pin the commit-at-end functions. Rewrite them with equal strength for the new rules: a Rejected garment and an unchosen `ChooseManually` garment are never written; only decided garments are written; the review finishes only when the scan has ended and every found card is decided. The colour tests of `ReviewDiscardTest` stay as they are.

## REV-UNDO — Undo snackbar, "Reviewed · N" and "Review again" (F12)
- **Reqs:** REQ-FB-12 · **Predecessors:** REV-SAVE · **Successors:** REV-BULK · **Independent review: yes** (reverts user data)
- **Files:** `ui/TagReviewScreen.kt`; `ui/ReviewScaffold.kt` (`ReviewAcceptedCard` only); the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewUndoTest.kt`.
- **In scope:** After every decision a snackbar (short duration, one at a time, a new decision replaces it): "N changes applied" (plural) or "Kept as it was", with Undo; Undo is the latest decision's `revert` (a write through `onRevert` for an Applied garment, none for a Rejected one) and the card returns to its place in the list with its chips as the user left them; Undo is disabled while that garment's write is in flight. "Got it" shows no snackbar. A collapsed section "Reviewed · N" with Show/Hide after the pending cards, and "N reviewed ▾" in the pinned header that opens it: each row is the description line, the outcome ("N changes applied" / "Kept as it was") and a 48 dp "Review again", which is the same `revert`. A failed revert keeps the garment decided and shows `review_revert_failed`. `ReviewAcceptedCard` resets when `resolved` goes back to false, so a garment undone while it is still leaving comes back instead of staying invisible; the colour review's behaviour is unchanged.
- **Out of scope:** Apply all remaining, completion screen, the write path (REV-SAVE), the colour review's screen.
- **Acceptance:** V-REV-UNDO-1, V-REV-UNDO-2, V-REV-UNDO-3, V-REV-UNDO-4.

## REV-BULK — Apply all remaining, with confirmation (F12)
- **Reqs:** REQ-FB-12 · **Predecessors:** REV-UNDO · **Successors:** REV-DONE · **Independent review: yes** (batch write of user data)
- **Files:** `ui/TagReviewScreen.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewBulkTest.kt`.
- **In scope:** The bottom bar becomes "Apply all remaining (N)" (N = garments the bulk plan would change; disabled while the scan runs and when it is 0) and opens a confirmation (`review_bulk_title`; the body states tags added, tags removed (omitted when 0) and garments; confirm "Apply", dismiss "Cancel"). Confirm runs REV-SESSION's `applyBulk`: each remaining card is applied with its current chips (cards with every change left out count as kept, locked-only cards are dismissed, a `ChooseManually` card without a choice stays in the list), as ONE `onApply` batch; the snackbar says "N garments updated" (plural) with an Undo that reverts the whole batch in one write and returns those cards to the list. The old strings `tag_review_accept_all` and `tag_review_save_accepted` stay in the files, unused by this screen.
- **Out of scope:** Completion screen, the colour review's "Accept all", any new write path.
- **Acceptance:** V-REV-BULK-1, V-REV-BULK-2, V-REV-BULK-3.

## REV-DONE — Completion screen with "Edit changes" (F12)
- **Reqs:** REQ-FB-12, REQ-FB-21 · **Predecessors:** REV-BULK · **Successors:** — · **Independent review: no**
- **Files:** `ui/TagReviewScreen.kt`; new `ui/TagReviewComplete.kt`; the three `strings.xml`; new `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCompleteTest.kt`.
- **In scope:** When `isComplete(scanning)` the list is replaced by a static completion view (no sparkle, no auto-dismiss): a check in a soft circle, the existing plural `review_complete_tags` ("Tags updated on N garments") or `review_complete_none` ("No garments changed") when none changed, the tally "+ N tags added · − N tags removed" in the add and remove colours with filled sign circles (an entry shows only when its number is above 0), "N kept as they were" (`review_complete_kept`, plural), up to five thumbnails of the updated garments, a tonal "Edit changes" button that returns to the list with every garment in "Reviewed", and a filled "Done" that calls `onFinish(0)` (the screen has shown its own completion, so RobiaApp's overlay does not appear) and returns to Manage tags. The Undo snackbar stays reachable on the way in. "Edit changes" sets a screen flag that keeps the list visible until a card has been returned to Pending and decided again, so the completion view does not come straight back. The empty-scan card, the model-unavailable card and the skipped line keep today's behaviour.
- **Out of scope:** `ReviewCompleteOverlay.kt` (still used by the colour review), the colour review, any write path.
- **Acceptance:** V-REV-DONE-1, V-REV-DONE-2, V-REV-D.

---

## Round 3f — Manu's device feedback 3 (2026-10-27, build 0de712b; ui3 F21 to F32)

Source: R3-DEC-056 to R3-DEC-068 and the investigations `artifacts/inv-f3-a.md` (tag review), `inv-f3-b.md` (duplicates, back, delete prompt), `inv-f3-c.md` (cutout names, GPU, smart eraser), all verified against main `b7b5476`; the briefs cite symbols, not line numbers (lines drift as tasks merge). Same worker rules 0–13; all tasks start from latest `origin/main`. Rule 2 applies: `strings.xml` in three locales and new tests are always allowed. Rule 9 stays: existing theme tokens, light and dark; a colour literal needs a `// theme-fixed: <reason>` marker (the DARK scan tests are in force). Items are written "ui3 F21" to "ui3 F32" (the file `feedback/ui3.md`); the F21 of Round 3d is REQ-FB-21.

**Dependency summary.** Conflict-driven (full order under "Remaining execution order").
```
FB3-SPARK-HINT <- (none)                     (first writer of ui/TagReviewScreen.kt in 3f)
FB3-NOSNACK    <- FB3-SPARK-HINT             (ui/TagReviewScreen.kt)
FB3-NOPROMPT   <- FB3-NOSNACK                (ui/TagReviewScreen.kt)
FB3-COMPLETE   <- FB3-NOPROMPT, FB3-DUP-DONE (ui/TagReviewScreen.kt; ui/ReviewCompleteOverlay.kt)
FB3-CHIP-PICK  <- FB3-COMPLETE, FB3-PROGRESS (ui/TagReviewScreen.kt, ui/TagReviewCard.kt)
FB3-PROGRESS   <- (none)
FB3-DUP-ROW    <- (none)
FB3-DUP-DONE   <- FB3-DUP-ROW                (ui/DuplicateReviewScreen.kt)
FB3-BACK       <- FB3-DUP-DONE               (ui/RobiaApp.kt)
FB3-CUT-NAMES  <- (none)
FB3-GPU-DROP   <- (none)
FB3-SMART-DROP <- (none)
FB3-CHK        <- ALL
```
Shared files (writers in order): `ui/TagReviewScreen.kt` FB3-SPARK-HINT → FB3-NOSNACK → FB3-NOPROMPT → FB3-COMPLETE → FB3-CHIP-PICK; `ui/TagReviewCard.kt` FB3-PROGRESS → FB3-CHIP-PICK; `ui/ReviewCompleteOverlay.kt` FB3-DUP-DONE → FB3-COMPLETE; `ui/DuplicateReviewScreen.kt` FB3-DUP-ROW → FB3-DUP-DONE; `ui/RobiaApp.kt` FB3-DUP-DONE → FB3-BACK; the three `res/values*/strings.xml` by any task, one at a time (one `<!-- FB3-… -->` block each). Every other file has one owner.

## FB3-SPARK-HINT — Visible spark icon in the review prompts; drop the skip hint (ui3 F21 + F25)
- **Reqs:** REQ-FB-22 · **Predecessors:** — · **Successors:** FB3-NOSNACK · **Independent review: no**
- **Files:** `core/designsystem/Color.kt`; `core/designsystem/Theme.kt` (`RobiaExtras`, `RobiaLightExtras`, `RobiaDarkExtras` only); `ui/TagReviewScreen.kt` (the icon tint of `TagReviewPromptDialog` and the `review_hint_changes` text of the pinned header only); `ui/ColorReviewScreen.kt` (the icon tint of `ColorReviewPromptDialog` only); the three `strings.xml` (delete `review_hint_changes`); `app/src/test/java/com/gusanitolabs/robia/core/designsystem/RobiaColorSchemeTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCardModelTest.kt` (the key list only).
- **In scope:** (F21) `RobiaExtras.sparkOnPrimary`: light = `RobiaOnTertiaryContainer` (byte for byte), dark = `#6D390F`; both extras instances filled; the sparkle `Icon` of the two prompt dialogs tinted with it. A contrast test: `sparkOnPrimary` over `primary` at least 3:1 in both schemes. (F25) Delete the `Text(stringResource(R.string.review_hint_changes))` of the pinned header and the string in the three files; remove `"review_hint_changes"` from the key list of `TagReviewCardModelTest`.
- **Out of scope:** The other users of `onTertiaryContainer` (`ItemDetailScreen`, `AddEditClothingScreen`, `BrowseFilterBar`); `SparkleBurst` (uses `tertiary`, fine in both themes); the colour review's `color_review_tap_hint`; any scheme role; the light hex pins of `RobiaColorSchemeTest` (stay green untouched).
- **Acceptance:** V-FB3-SPARK-HINT-1, V-FB3-SPARK-HINT-2, V-FB3-SPARK-HINT-3, V-FB3-SPARK-HINT-D.

## FB3-PROGRESS — Smooth progress bar after the scan (ui3 F22)
- **Reqs:** REQ-FB-23 · **Predecessors:** — · **Successors:** FB3-CHIP-PICK (file order on `ui/TagReviewCard.kt`) · **Independent review: no**
- **Files:** `ui/TagReviewCard.kt` (`ReviewLeftHeader` only); `ui/ReviewScaffold.kt` (the new pure function only); `app/src/test/java/com/gusanitolabs/robia/ui/ReviewProgressTest.kt`.
- **In scope:** Pure `reviewProgressAnimationMillis(reducedMotion: Boolean): Int` (400, or 0 under reduced motion) beside `reviewScanProgress`; `ReviewLeftHeader` draws its bar from `animateFloatAsState(progress.coerceIn(0f, 1f), tween(that, FastOutSlowInEasing))` (`snap()` when 0, with `LocalReducedMotion`). A source-contract test that `ReviewProgressHeader` (the scan header) still has no `animateFloatAsState` or `tween`. Optional: a 200 ms cross-fade of the scan header into the "N left" header at scan end (skip it if it needs a change outside the two files).
- **Out of scope:** The scan header and `reviewScanProgress` (REV-PROGRESS); `reviewHeaderState` and the counts; the colour review; the completion view (FB3-COMPLETE).
- **Acceptance:** V-FB3-PROGRESS-1, V-FB3-PROGRESS-2, V-FB3-PROGRESS-D.

## FB3-NOSNACK — No snackbar for a single decision (ui3 F23)
- **Reqs:** REQ-FB-24 · **Predecessors:** FB3-SPARK-HINT (file order) · **Successors:** FB3-NOPROMPT · **Independent review: no**
- **Files:** `ui/TagReviewScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewUndoTest.kt` (a new source-contract test; every existing assertion stays).
- **In scope:** Remove `showUndoSnack` and its three call sites (Apply, Reject, and the write-result branch of `writeDecision`), `keptMessage`, `ReviewUndoSnack.outcome`, the "turn into Kept" and "dismiss the undo snack" branches of `writeDecision` (its failure branch keeps `bringBack` and the failure message), and the single-garment case of `undoEnabled` in the `SnackbarHost`. `undoSnack` becomes the bulk snackbar only. A standing bulk snackbar is dismissed before the apply-failed message shows (as the revert-failed message already does). Update the comment on the finish guard if it mentions the snackbar.
- **Out of scope:** `undoDecision`, `canUndoNow`, `reviewAgainEnabled`, `bulkRevert`, `undoBulk`, `ReviewedRow`, `undoSnackOutcome`, `reviewOutcomeOf`, `ReviewOutcome` and the strings `review_snack_applied` / `review_snack_kept` (the Reviewed rows use them); the bulk snackbar and its Undo; `doneFinishes` and `shouldFinishTagReview`; no string is removed.
- **Acceptance:** V-FB3-NOSNACK-1, V-FB3-NOSNACK-2, V-FB3-NOSNACK-D.
- **Supersedes:** REV-UNDO step 1 (a snackbar after every decision) for single decisions, and REV-DONE step 1's sentence "The Undo snackbar of the last decision stays visible over it": now only the bulk snackbar can be up over the completion view (R3-DEC-059).

## FB3-NOPROMPT — No review question when nobody holds the deleted tag (ui3 F29)
- **Reqs:** REQ-FB-29 · **Predecessors:** FB3-NOSNACK (file order) · **Successors:** FB3-COMPLETE · **Independent review: no**
- **Files:** `ui/TagReviewScreen.kt` (`hasAffectedGarments` and `offerTagReviewRequest` only); `app/src/test/java/com/gusanitolabs/robia/ui/ReviewDiscardTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewScanTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (check only; edit only if a pinned text of `offerTagReviewRequest` moves, with equal strength).
- **In scope:** Pure `internal fun hasAffectedGarments(changeSet: TagChangeSet, garments: List<ClothingItem>): Boolean` next to `affectedGarmentCount`: for `TagOperation.Deleted`, true when an active garment (`!isArchived`) has a tag whose id is in `changeSet.touchedTagIds`; for every other operation true. `offerTagReviewRequest` returns null also when `!hasAffectedGarments(changeSet, garments)`; the snapshot read still comes before `write()` and the write always lands.
- **Out of scope:** Added, renamed and restored tags (they need the scan); `affectedGarmentCount` (header count, archived included); `ReviewPlanner`; the offer wiring in `RobiaApp.kt`; the colour review's guard.
- **Acceptance:** V-FB3-NOPROMPT-1, V-FB3-NOPROMPT-2, V-FB3-NOPROMPT-3, V-FB3-NOPROMPT-D.
- **Flag (R3-DEC-064):** a tag held only by archived garments now also gives no prompt; the receipt states it.

## FB3-COMPLETE — A calm completion view that arrives like the overlay (ui3 F26)
- **Reqs:** REQ-FB-26 · **Predecessors:** FB3-NOPROMPT (file order on `ui/TagReviewScreen.kt`), FB3-DUP-DONE (file order on `ui/ReviewCompleteOverlay.kt`) · **Successors:** FB3-CHIP-PICK · **Independent review: no**
- **Files:** `ui/TagReviewScreen.kt` (the completion swap, the pinned header's visibility, the `LaunchedEffect(completionShown)` and the last-decision time only); `ui/TagReviewComplete.kt`; `ui/ReviewCompleteOverlay.kt` (extract the shared badge only); `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCompleteTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/ReviewCompleteTextTest.kt` (only if a helper it covers changes).
- **In scope:** (1) The pinned header (the "N left" title and bar, "N reviewed ▾", the context lines) is not composed while the completion is up; "Edit changes" still reaches Reviewed. (2) Pure `completionWaitMillis(nowMs, lastDecisionMs)`: the part of `REVIEW_ACCEPT_HIGHLIGHT_MILLIS + REVIEW_ACCEPT_EXIT_MILLIS` (450 ms) left since the last decision, 0 when none or past; the screen records the time of every decision (Apply, Reject, Keep as is, Got it, bulk) and swaps the list for the completion when `showsCompletion` is true and that wait has passed (`completionReady`); the "pre-hide every candidate" effect runs at the swap, not at `completionShown`. (3) A shared `ReviewCompleteBadge` (72 dp `primaryContainer` circle, check scaling 0 to 1 over 300 ms, `SparkleBurst` count 7) used by `ReviewCompleteOverlay` (look unchanged) and by `TagReviewCompletion`; there the title, tally, thumbnails and Edit changes fade in one after the other (250 ms steps), and the list cross-fades to the completion (300 ms). `LocalReducedMotion`: no animation, no burst. (4) Done is unchanged: `onFinish(emptyList())` at once.
- **Out of scope:** `onFinish`, `finishTagReview` and `RobiaApp.kt`; `showsCompletion`, `shouldFinishTagReview`, `doneEnabled`, `doneFinishes`, the Edit-changes state machine; the colour review overlay (look and timing); any new string; a delayed finish.
- **Acceptance:** V-FB3-COMPLETE-1, V-FB3-COMPLETE-2, V-FB3-COMPLETE-D.
- **Why a time-based wait:** a decided card that is off-screen in the lazy grid is never composed, so it never reports its exit; counting hidden cards would never reach zero (R3-DEC-061).

## FB3-CHIP-PICK — Long-press a "+" chip to choose another tag (ui3 F24)
- **Reqs:** REQ-FB-25 · **Predecessors:** FB3-COMPLETE (file order on `ui/TagReviewScreen.kt`), FB3-PROGRESS (file order on `ui/TagReviewCard.kt`) · **Successors:** — · **Independent review: yes** (changes what a decision writes)
- **Files:** `suggestions/ReviewChanges.kt`; `ui/TagReviewSession.kt`; `ui/ReviewChangeChips.kt`; `ui/TagReviewCard.kt`; `ui/TagReviewCardModel.kt`; `ui/TagReviewScreen.kt` (the picker state and dialog, and the chip callback wiring only); the three `strings.xml`; `app/src/test/java/com/gusanitolabs/robia/suggestions/ReviewChangesTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewSessionTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCardModelTest.kt`.
- **In scope:** Pure `ReviewChanges.replaceTag(key, newTagId)` and `TagReviewSession.replaceChip(garmentId, key, newTagId)` as in R3-DEC-060 (an `Add` becomes `Add(new)` in place, a `Swap` keeps `from` and replaces `to`; the old key leaves `skipped`; refused unless the card is Pending and `newTag in card.offeredTagIds` and not used by another chip of the card; `Remove` and locked chips are refused). A pure picker-options function (offered tags minus the tags of the other chips, original proposal included). Chips: `toggleable` becomes `combinedClickable` (tap toggles as today, long press asks for the replacement) with the switch role and state description kept, a light haptic on long press, and `onLongClickLabel` "Choose another tag" (ES "Elegir otra etiqueta", DE "Anderes Tag wählen") on replaceable chips only; the chosen tag of a `needsChoice` card also opens the picker on long press, with "None" shown (it calls the existing `choose`). The picker dialog gets a target `(garmentId, key)` and hides "None" for Add and Swap.
- **Out of scope:** `deltaFor`, `primaryButtonFor`, `noTagsLeft`, `bulkPlan`, `commitDeltaFor`, `applyDelta`, `revertDelta` (they read the rewritten `changes`); a visible hint or affordance; multi-choice pickers; the colour review.
- **Acceptance:** V-FB3-CHIP-PICK-1, V-FB3-CHIP-PICK-2, V-FB3-CHIP-PICK-3, V-FB3-CHIP-PICK-4, V-FB3-CHIP-PICK-D.
- **BLOCKING list for the independent review:** a write outside the offered group; a tag the garment already has; a double apply after Undo or "Review again"; a bulk plan that still uses the old tag; a replace on a decided card; a "−" or locked chip that changes; a file outside Files; a weakened existing test.

## FB3-DUP-ROW — Duplicate rows without "Untitled garment" (ui3 F27 a)
- **Reqs:** REQ-FB-27 · **Predecessors:** — · **Successors:** FB3-DUP-DONE (file order on `ui/DuplicateReviewScreen.kt`) · **Independent review: no**
- **Files:** `ui/DuplicateReviewScreen.kt` (`DuplicateGarmentRow` and its label inputs only); `ui/BrowseCardText.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateRowTextTest.kt`.
- **In scope:** Pure `internal fun duplicateRowLine(name: String, tags: List<UiTag>, colourLabel: String?, collator: Collator): String?` in `BrowseCardText.kt`: the trimmed name when not blank; else the known parts of "<category label> · <colour>" joined by the existing separator (one part alone shows alone); else `cardMeta(tags, collator)` when not empty; else null. `DuplicateGarmentRow` draws the `Text` only when the result is not null and no longer uses `browse_similar_untitled`; the 72 dp photo and the Keep/Delete chips stay. The row builds its `UiTag` list with `localizedTagLabel(context)` as `BrowseGrid.kt` does and maps `primaryDisplayLabel` to the same `color_*` strings the add flow uses (Unknown → null).
- **Out of scope:** `browse_similar_untitled` (three other users); the group card, the tier line, the buttons, the empty text; `ui/TagReviewCard.kt`.
- **Acceptance:** V-FB3-DUP-ROW-1, V-FB3-DUP-ROW-2, V-FB3-DUP-ROW-D.

## FB3-DUP-DONE — Leave the duplicate review when it is finished (ui3 F27 b)
- **Reqs:** REQ-FB-27 · **Predecessors:** FB3-DUP-ROW · **Successors:** FB3-COMPLETE (file order on `ui/ReviewCompleteOverlay.kt`), FB3-BACK (file order on `ui/RobiaApp.kt`) · **Independent review: yes** (navigation and delete flow)
- **Files:** `ui/DuplicateReviewScreen.kt`; `ui/ReviewCompleteOverlay.kt` (the kind and its message only); `ui/RobiaApp.kt` (the `DuplicateReview` wiring only: the `RobiaNavHost` parameter, its route branch and the call in `RobiaApp`); the three `strings.xml`; `app/src/test/java/com/gusanitolabs/robia/ui/ReviewCompleteTextTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateReviewFinishTest.kt`.
- **In scope:** `ReviewCompleteKind.Duplicates` and the plural `review_complete_duplicates` (EN one "%1$d duplicate removed", other "%1$d duplicates removed"; ES "%1$d duplicado eliminado" / "%1$d duplicados eliminados"; DE "%1$d Duplikat entfernt" / "%1$d Duplikate entfernt"); the doc comment of `ReviewCompleteKind` updated ("returns to Manage" is no longer true for every kind). Pure `shouldFinishDuplicateReview(wasNonEmpty, nowEmpty, userActed)` = all three true. `DuplicateReviewScreen(onAllResolved: (removedCount: Int) -> Unit)`: it keeps `removedCount` (the ids of a confirmed Delete) and `userActed` ("Not duplicates" or a confirmed Delete), remembers that the groups were non-empty, and calls `onAllResolved` once when the rule holds. `RobiaApp` wires it to `reviewComplete = reviewCompleteText(ReviewCompleteKind.Duplicates, n)` and `replaceRoute(RobiaRoute.Browse)`; with n = 0 there is no overlay but Browse opens. The empty text stays only when nobody acted.
- **Out of scope:** The delete confirmation dialog and `onDeleteItems`; `dismissDuplicateGroup`, `DuplicateSession`, the scan and its trigger; the banner rules (it already waits for `reviewComplete`); the top-bar arrow.
- **Acceptance:** V-FB3-DUP-DONE-1, V-FB3-DUP-DONE-2, V-FB3-DUP-DONE-3, V-FB3-DUP-DONE-D.
- **BLOCKING list for the independent review:** navigation without the user having acted; a double finish; the delete path no longer asking for confirmation; an overlay with count 0; a file outside Files; a weakened existing test.

## FB3-BACK — Back-to-exit that can be tested and seen (ui3 F28)
- **Reqs:** REQ-FB-28 · **Predecessors:** FB3-DUP-DONE (file order on `ui/RobiaApp.kt`) · **Successors:** — · **Independent review: yes** (the previous attempt passed CI and failed on the device)
- **Files:** new `ui/BackExitMachine.kt`; `ui/BackExitPolicy.kt` (only if the machine needs a small hook; `decideBack` and its test stay); `ui/RobiaApp.kt` (the back-exit block only: `lastExitHintMs`, its `LaunchedEffect`, the root `BackHandler`, the hint pill); new `app/src/test/java/com/gusanitolabs/robia/ui/BackExitMachineTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BackExitPolicyTest.kt` (unchanged, must stay green).
- **In scope:** `class BackExitMachine(clock: () -> Long)` holding the hint time and a context key (any equatable value: route, query, similar-to, filters, selection); `onBack(state, contextKey)` returns the same `BackAction`s as `decideBack`, forgets a hint whose key differs or whose time is in the future, and records the hint on `ShowExitHint`; `hintVisible()` for the pill. No `LaunchedEffect` for the reset. The root `BackHandler` is enabled for every route when `routeStack.size == 1` and no restore is running; routes that have their own handler (Add, batch add, tag review, colour review) keep priority because their handlers are composed later (verify and list in the receipt, with every `replaceRoute` target). The hint shows as a bottom pill (theme tokens, auto-hides after the exit window, no animation under reduced motion) in addition to the existing toast; `Log.d("BackExit", ...)` at every branch: action, route name, milliseconds since the hint, flags only (never the search text). Reuse `back_exit_hint`.
- **Out of scope:** The existing `routeStack.size > 1` handler; the child handlers; predictive back and the manifest flag (excluded by NAV-BACK); the layers of REQ-FB-15 (their order does not change).
- **Acceptance:** V-FB3-BACK-1, V-FB3-BACK-2, V-FB3-BACK-3, V-FB3-BACK-D.
- **Open question for Manu (in the device row):** does the hint show on the first press, and on which screen? A tester with ADB reads the `BackExit` log (candidate causes in R3-DEC-063).

## FB3-CUT-NAMES — Cutout methods Standard, Precise, Automatic (ui3 F30)
- **Reqs:** REQ-FB-30 · **Predecessors:** — · **Successors:** — · **Independent review: no**
- **Files:** `core/model/ClothingModels.kt` (`CutoutEngineChoice`, `RobiaSettings.preciseCutoutEnabled` only); `data/SettingsRepository.kt` (the stored-name read only); `media/cutout/CutoutOptions.kt` (`cutoutOptionsOf` only); `ui/PreciseCutoutSettings.kt`; the three `strings.xml`; `app/src/test/java/com/gusanitolabs/robia/data/SettingsMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PreciseCutoutSettingsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/CutoutHangerOptionsTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/CutoutOptionsTest.kt`.
- **In scope:** `CutoutEngineChoice { Automatic, Standard, Precise }` (stored names unchanged; an unknown stored name still reads Automatic; K00 migration unchanged). `cutoutOptionsOf`: the precise engine for Precise and Automatic when the pack is ready, never for Standard. `preciseCutoutEnabled` = the method is not Standard. Hanger options: pack ready and method not Standard (`cutoutHangerOptionsAvailable`, `hangerDetectionVisible`). `ui/PreciseCutoutSettings.kt`: the method dialog offers Standard, Precise, Automatic (each with its one-line help; Precise is not selectable while the pack is not ready, unless it is the stored one); the Experimental label on Precise and Automatic (`cutoutMethodExperimentalRes`); the row's help text follows the stored method. Strings (EN | ES | DE): `cutout_method_precise` "Precise" | "Preciso" | "Präzise"; `cutout_method_precise_hint` "Best for hangers and similar-coloured backgrounds. Slower. Needs the cutout pack." | "Ideal con perchas o fondos parecidos. Más lento. Necesita el paquete de recorte." | "Am besten bei Bügeln oder ähnlichem Hintergrund. Langsamer. Braucht das Zuschnitt-Paket."; `cutout_method_standard_hint` "Fast. Works for most photos. Does not remove hangers." | "Rápido. Sirve para la mayoría de fotos. No quita perchas." | "Schnell. Passt für die meisten Fotos. Entfernt keine Bügel."; `cutout_method_automatic_hint` "Uses Precise when the pack is downloaded, otherwise Standard." | "Usa Preciso si el paquete está descargado; si no, Estándar." | "Nutzt Präzise, wenn das Paket geladen ist, sonst Standard."; `cutout_needs_precise` "Needs Precise or Automatic and the cutout pack" (ES and DE by the worker); `precise_cutout_title` "Cutout pack" | "Paquete de recorte" | "Zuschnitt-Paket" and a body that says it is needed by Precise and Automatic; ES "Estándar / Automático" and DE "Standard / Automatisch" stay as they are. The existing experimental sentence is shown under the method row while the stored method is Precise or Automatic.
- **Out of scope:** `ui/SettingsScreen.kt` (it only reads `preciseCutoutEnabled`); the registry fallback chain (`CutoutEngineRegistry`); the pack download; the editor's "Cut out again" sheet (`RedoCutoutChoice` already says Standard and Precise); a per-photo automatic choice (needs a detector; separate task if Manu wants it).
- **Acceptance:** V-FB3-CUT-NAMES-1, V-FB3-CUT-NAMES-2, V-FB3-CUT-NAMES-3, V-FB3-CUT-NAMES-D.
- **Updates:** V-K00-1, V-K00-2 and V-K00-D (the "Experimental" option is now Precise and Automatic).

## FB3-GPU-DROP — Remove the GPU benchmark row (ui3 F31)
- **Reqs:** REQ-FB-31 · **Predecessors:** — · **Successors:** — · **Independent review: yes** (deletion)
- **Files:** `suggestions/DevBenchmark.kt`; `ui/SettingsScreen.kt` (the GPU row and its state only); the three `strings.xml` (`dev_model_benchmark_gpu`); delete `app/src/test/java/com/gusanitolabs/robia/suggestions/GpuBenchmarkReportTest.kt`.
- **In scope:** Remove, with a grep per symbol in the receipt (zero hits afterwards unless the brief says it stays): `DevBenchmark.measure(useGpu)` (the parameter and its KDoc; `measure()` stays), `runGpuBenchmark`, `gpuReport`, `gpuBenchmarkReport`, `gpuUnavailableReport`, `GPU_LOG_TAG`, the `Backend` import and `Backend.GPU()` if unused, in `SettingsScreen.kt` the `benchmarkGpuText` / `benchmarkGpuRunning` state, the GPU `ListItem` with its divider, and the `!benchmarkGpuRunning` conditions on the CPU row; the string `dev_model_benchmark_gpu` in three locales; `GpuBenchmarkReportTest`. Helpers that become unused only because of this (`deviceLine` if unused, imports) go with them; `EmbeddingEngineHolder` is not touched.
- **Stays:** the CPU benchmark row and `measure()`; `LiteRtNativeEmbedder` and its `backend` / `visionBackend` parameters with their `Backend.CPU()` defaults (production uses them; a future static-shape retry needs them); `dev_benchmark_running`, `dev_benchmark_failed`, `dev_run`, `dev_copy`.
- **Out of scope:** Production backend selection (stays CPU); the model, the recipe, any retry with another export; the docs (the planner updates them).
- **Acceptance:** V-FB3-GPU-DROP-1, V-FB3-GPU-DROP-2, V-FB3-GPU-DROP-D.

## FB3-SMART-DROP — Remove the smart eraser; the brush is the only tool (ui3 F32)
- **Reqs:** REQ-FB-32 · **Predecessors:** — · **Successors:** — · **Independent review: yes** (deletion)
- **Files:** `ui/PhotoEditorScreen.kt`; delete `media/cutout/SelectionClip.kt`; delete `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`, `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt`, `app/src/test/java/com/gusanitolabs/robia/media/cutout/SmartRemoveUndoTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PhotoEditorMappingTest.kt` (the three `selectPixelOf` tests only); `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt` (only to keep one `apply(..., hard = true)` undo/redo case if it is covered nowhere else); the three `strings.xml`.
- **In scope:** In `PhotoEditorScreen.kt`: the `brush` state and the "Brush" `FilterChip` with the `brush` / `onBrushChange` parameters of `CutoutControls`; `SMART_MAX_POINTS`, `SMART_TRAIL_DASH_DP`, `SMART_TRAIL_GAP_DP`, `SMART_SNAP_DP`, `PULSE_MS`, `PULSE_RADIUS_DP`, `PulseMark`, the `pulse` state, its effect and its drawing, `runSmartSelection`, the smart branch of `finishGesture` (and its `moved` parameter if nothing else reads it), `selectPixelOf`, `smartSelectionPoints`, `samplePath`, the dashed trail branch (the trail is the solid round brush trail), the `refine_component_tap` diagnostic, the `pointerInput` key `brush`, the `wholeGarmentMessage` and `keepHintMessage` values, the imports they leave unused, the KDoc mentioning smart tap. A drag or a tap always calls `commitStroke` (a tap paints one dot, as with Brush on today). Strings: delete `refine_brush`, `editor_smart_whole_garment`, `editor_smart_keep_hint` in three locales; reword `refine_hint` for the brush (EN "Paint over a part to keep or remove it.", ES "Pinta sobre una parte para mantenerla o quitarla.", DE "Male über einen Teil, um ihn zu behalten oder zu entfernen.").
- **Must stay (grep in the receipt):** `media/cutout/InteractiveSelector.kt` (`MediaPipeInteractiveSelector`), `PreciseCutoutEngine.kt`, `InteractiveSelectorContractTest`, `InteractiveSelectionSeamTest`; `MaskOps.labelComponents` (used by `MaskOps.kt` itself); `RefineSession.apply(..., hard)`, `hardSelection`, `EditOp.Hard` (history replay and format); `applyBrush`, `commitStroke`, `brushRadiusPx`, the `BRUSH_*` constants; the Keep/Remove control, Undo, Redo, Reset, "Cut out again", the OR3 Keep-from-original brush.
- **Out of scope:** `RefineSession`, `EditHistory`, `MaskOps` (other than reading); the engines; the Light tab; restoring from the original (OR3).
- **Acceptance:** V-FB3-SMART-DROP-1, V-FB3-SMART-DROP-2, V-FB3-SMART-DROP-3, V-FB3-SMART-DROP-D.
- **Supersedes:** UI-SMART and ED1 (their code, tests and V-UI-SMART-* / V-ED1-1..4 rows); R3-DEC-043's failure rule is invoked (R3-DEC-067).

## FB3-CHK — Device checklist for Round 3f
- **Reqs:** all of Round 3f · **Predecessors:** ALL of Round 3f · **Successors:** — · **Independent review: no**
- **Files:** `docs/specs/robia-round3/artifacts/device-checklist.md`.
- **In scope:** The 3f rows and the marks of Manu's feedback 3 are already in the checklist (planner, R3-DEC-056 and R3-DEC-068). Update the build under test (staging APK link, main SHA, CI run, signer), compare each 3f row with the merged behaviour in the task receipts (change a step only where the behaviour differs from the brief and say so in the PR), re-run the count commands of the "Counts" section and fix the numbers.
- **Out of scope:** Code; the planning docs other than the checklist.
- **Acceptance:** V-FB3-CHK-1.
