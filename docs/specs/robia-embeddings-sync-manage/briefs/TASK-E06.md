# TASK-E06 — Backfill, indicator, embeddings in Drive

## Goal
Existing garments (and garments restored without vectors) get their vectors computed automatically while the app is open, with a simple "Preparing suggestions for N garments…" banner; vectors are backed up to Drive so a new phone does not recompute them. Uploading vectors never changes the garment's revision.

## Read first
TS§3.8, §3.9 (processor part). `EmbeddingDao.garmentsNeedingImage`, `EmbeddingRepository`. Processor "pending work" checks (grep `hasPending` / where it decides there is work). Mockup `model-download.html` (banner).

## Steps
1. `suggestions/EmbeddingBackfill.kt` per TS§3.8: `progress: StateFlow<BackfillProgress>`; `resume()`/`pause()`; loop takes 1 garment at a time (IMAGE first, then stale TEXT); `yield()` between items; skip a garment after 2 failures this process; restart when model state becomes Ready, on ON_START, and after restore import.
2. Register in `RobiaApplication`; connect lifecycle (ON_STOP pause, ON_START resume).
3. Processor: treat `embeddingRepository.pendingUploadKeys().isNotEmpty()` as pending work; after a successful snapshot upload call `markUploaded(keys that were exported)`; `EmbeddingRepository.upsert` calls `syncScheduler.requestSync()`.
4. `ui/SuggestionsProgressBanner.kt` per mockup; show at top of Browse when `remaining > 0`; plurals strings from tasks.md E06.

## Tests
`EmbeddingBackfillTest` (fake embedder, ordering, stale TEXT, skip after 2), `EmbeddingUploadTest` (V-E06b, V-E06c with `FakeDriveSnapshotApi`).

## Done when
CI green; V-E06a/b/c.
