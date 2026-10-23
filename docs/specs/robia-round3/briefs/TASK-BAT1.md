# TASK-BAT1 — Batch add: close button, clean cancel, early pinned ETA, one module wait per batch

## Goal
Manu (N1, N2): wants to cancel and leave the batch, and an ETA. Premise check (inv-r3-e.md): Back already opens a discard dialog but there is no on-screen control; an ETA exists but needs two completions, resets when leaving the screen, scrolls away and starts low with two parallel cutouts; each photo can wait 60 + 60 s for the ML Kit module.

## Read first
`artifacts/inv-r3-e.md` (whole); `ui/BatchAddClothingScreen.kt` l.118-187, 206-213, 288-361, 558-630; `ui/BatchEta.kt`; `ui/RobiaApp.kt` l.888-902, 1156-1221, 1554-1566, 1715-1744; `media/SegmentationModuleGate.kt` l.66-119; ML Kit engine l.53-56, 117, 152-165.

## Files
`ui/BatchAddClothingScreen.kt`; `ui/BatchEta.kt`; `ui/BatchProcessingCoordinator.kt`; `ui/RobiaApp.kt`; `media/SegmentationModuleGate.kt`; `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt`; `media/ClothingImageStore.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BatchEtaTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/SegmentationModuleGateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinatorTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Close: top-bar ✕ for `BatchAddClothing` calling the screen's `requestCancel`; remove the duplicate dialog in RobiaApp; dialog body `batch_discard_body` = "Processing stops and unsaved garments are lost."
2. Cancel: `coordinator.cancel()` and join, then delete temp files of unsaved drafts (raw copy, `subject-*`, `cropped-subject-*`); late results after cancel delete their files.
3. ETA: move `BatchEtaEstimator` next to `batchDrafts` (created in `startBatchAdd`, reset in `cancelBatchAdd`), stamp in `updateBatchDraft` when a draft turns terminal, seed with the batch start; compute once per completion; header (bar, "N of M", ETA) pinned above the LazyColumn; text throttled to 5 s.
4. Module gate: `startBatch()` from `startBatchAdd`; budget 60 s shared; the ML Kit retry path uses the remaining budget; single-photo add keeps today's behaviour when no batch is active.

## Tests
V-BAT1-1..3.

## Done when
V-BAT1-1, V-BAT1-2, V-BAT1-3, V-BAT1-4, V-BAT1-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Change save semantics, review states, parallelism or engines.
