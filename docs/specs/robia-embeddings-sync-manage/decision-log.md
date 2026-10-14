# Decision log — robia-embeddings-sync-manage

Each entry: context → decision → rationale → consequences → review trigger. Source of approval: Telegram planning rounds 1–3 and follow-ups with Manu (2026-10-11 … 2026-10-14).

## DEC-001 — On-device runtime: LiteRT-LM 0.18.0
- **Context:** EmbeddingGemma 2 text+vision must run on the phone. Options: LiteRT-LM `EmbeddingEngine`, MediaPipe `tasks-retrieval` (wrapper over the same runtime), ML Kit (announced, not available).
- **Decision:** `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, CPU backend, `InputData.Image(bytes)` / `InputData.Text`.
- **Rationale:** direct API, fewer layers, verified with `javap` (EmbeddingEngine, EmbeddingEngineConfig, EmbeddingOptions, InputData).
- **Consequences:** requires Kotlin 2.4 + JDK 21 (DEC-002); adds ~22 MB arm64 native lib; transitive gson 2.14.0, kotlin-reflect 2.4.0, coroutines 1.11.0.
- **Review trigger:** ML Kit ships a managed EmbeddingGemma model.

## DEC-002 — Toolchain
- **Decision:** Kotlin 2.4.21 (KGP + Compose compiler plugin), KSP 2.3.12, AGP 8.13.2, Gradle 8.14.3 in CI, JDK 21 (`sourceCompatibility`/`jvmTarget` 21), Room 2.8.4, WorkManager 2.11.2 (`work-runtime-ktx`). compileSdk/targetSdk stay 35, minSdk 26.
- **Bounded fallback (pre-approved):** if AGP 8.13.2 cannot build with Kotlin 2.4.21, use the latest stable AGP 9.x and its minimum Gradle; nothing else may change. Record the result in the task receipt.
- **Rationale:** litertlm classes are Java 21 bytecode (major 65) with Kotlin 2.4 metadata.

## DEC-003 — Model distribution: pinned runtime download
- **Decision:** download at runtime from `https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm`, size 388 MB (exact byte size recorded by TASK-E01 from the HTTP `Content-Length`), SHA-256 `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0`. Prompt once after update + Settings entry. Wi-Fi only by default; mobile data on explicit confirmation; resumable.
- **Rationale:** 388 MB cannot live in git (100 MB limit) nor in a Play base module (200 MB). Same path works for CI APKs and future Play.
- **Review trigger:** ML Kit managed model; Play release planning.

## DEC-004 — Two vectors per garment, never tags
- **Decision:** IMAGE vector (image only) used for classification, review and similarity; TEXT vector (name + notes) used only for search at weight 0.3.
- **Rationale:** tags in the vector would bias predictions to the old label; notes like "return, too big" add noise to classification. Separate vectors allow a real 70/30 weighting and recomputing only what changed.
- **Consequences:** ~6 KB per garment in Room and Drive (2 × 768 float32).

## DEC-005 — Image input
- **Decision:** saved garment photo (ML Kit cutout) composited on white, longest edge 1024, PNG bytes, 140 vision tokens.
- **Rationale:** matches the current pipeline; 140 tokens measured as speed/fidelity compromise (70 is 2.5× faster on the Pi but changes ranking). Precision tool used raw photos (background kept) — real app accuracy is expected equal or better.
- **Review trigger:** NFR-01 failure → try 70 tokens (pre-approved), else STOP.

## DEC-006 — Storage
- **Decision:** new Room table `garment_embeddings` (DB v13), not part of the garment row; does not touch garment revision/sync status. Tag columns `description TEXT` (nullable) and `suggest_occasions INTEGER NOT NULL DEFAULT 1` on `garment_tags`.
- **Rollback:** additive migration; old app versions cannot open v13 DB (standard Room downgrade rule) — acceptable, CI APKs only move forward.

## DEC-007 — Drive contract evolution
- **Decision:** add optional fields only, keep `WARDROBE_SYNC_SCHEMA_VERSION = 2`: `garments[].embeddings`, `taxonomies.tags[].description`, `taxonomies.tags[].suggestOccasions`, `metadata.writerDeviceId`, `photos[].blobPath` new short format. Old app versions ignore unknown fields (org.json `opt*`).
- **Rationale:** approved round 1 Q11.

## DEC-008 — Category algorithm (combined, no fine-tuning)
- **Decision:**
  1. `text[c] = cos(IMAGE, labelVec[c])` for every category tag `c`.
  2. `knn[c] = mean of the top-3 cos(IMAGE, IMAGE_j)` over saved garments `j ≠ self` having category `c`; undefined if none.
  3. If at least 2 categories have `knn` defined: `score[c] = z(text)[c] + z(knn over defined)[c]` for defined, `z(text)[c]` otherwise (z = (x − mean)/population-std over the vector; std 0 → all zeros). Mode = COMBINED. Else `score = text`, mode = TEXT.
  4. Mark top1 iff `top1 − top2 ≥ 0.50` (COMBINED) or `≥ 0.010` (TEXT).
- **Evidence:** 128 photos: text-only 86 % top-1 (98 % top-3); combined with 1/3/5 examples per category 89/94/96 %; name-only labels 76 %.

## DEC-009 — Occasion algorithm
- **Decision:** same score construction as DEC-008 over occasion tags (kNN uses garments having that occasion). Selection: `g12 = s1−s2`, `g23 = s2−s3`; if `g12 > T` → {1}; else if `g23 > T` → {1,2}; else ∅. `T = 0.010` (TEXT) or `0.50` (COMBINED). Skip when the category tag has `suggestOccasions = false`.
- **Evidence:** 130 photos, TEXT mode, T = 0.010: 69 one / 16 two / 45 none. Manu chose "compare 2nd with 3rd" and start at 0.010.

## DEC-010 — Review affected sets
- **Decision:**
  - Category added/renamed: run DEC-008 over all garments with IMAGE vector; affected = garments whose marked category (rule passes) equals the touched tag and differs from their current category.
  - Category deleted: affected = garments that had it; proposal = DEC-008 winner among remaining categories even if the margin fails (shown as "suggested", user must confirm); if no vector → listed with "choose manually".
  - Occasion added/renamed: affected = garments where DEC-009 selects the touched occasion and it is not already set. Category flag respected.
  - Occasion deleted: no review (tag simply removed, current behaviour).
  - Restore defaults (category or occasion): union of affected sets for every tag added or renamed by the restore.
- **Rationale:** "only affected garments" (round 1 Q9).

## DEC-011 — Search
- **Decision:** query prefix `task: search query | text: `; score `0.7·cos(q,IMAGE)+0.3·cos(q,TEXT)`; all filtered items ranked, no threshold, missing vectors last. Fallback substring search when model not Ready.
- **Evidence:** Pi probe: "vestido rojo" top-6 dresses (top-3 red), "falda negra" top-6 skirts, "cinturón" accessories, "vaqueros cortos" shorts. Prefix `task: search query` separated slightly better than no prefix.

## DEC-012 — No fine-tuning
- **Decision:** out of scope. No verified export path from a fine-tuned EmbeddingGemma 2 to `.litertlm`; it would also fix the label set. Revisit if on-device accuracy disappoints.

## DEC-013 — Engine lifecycle
- **Decision:** lazy, singleton, serialized, preload on intent, release after 60 s idle / ON_STOP / trim memory. Embeddings only computed in foreground; never in WorkManager.
- **Rationale:** ~0.8–0.9 GB RSS; keeping it resident makes Android kill Robia sooner.

## DEC-014 — Sync architecture
- **Decision:** keep `WardrobeSyncOutboxProcessor` as the single sync brain; add `DriveSyncWorker` (CoroutineWorker) that calls the processor; in-app triggers enqueue unique work. Transient errors → pending (unlimited); only user-action errors surface. Photo keys short and content-addressed; WebP q90 ≤ 2048; one listing; 4 parallel streamed downloads; per-photo isolation; correct 403/429 handling; writer-device protection.
- **Rationale:** root causes in `PLANNING_CHECKPOINT` / technical spec §4; rewrite of the whole processor rejected (too large, high regression risk) — targeted changes instead.

## DEC-015 — Build artifacts
- **Decision:** `staging` build type (`initWith(debug)`-free: copies release, `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false`, `signingConfig` = CI debug signing or default debug, `matchingFallbacks = ["release"]`). Release also minified with the same rules. Baseline profiles postponed.

## DEC-016 — UX
- **Decision:** light theme only; Earth & Steel; HTML mockups approved before UI tasks start; "See similar garments" approved (must, REQ-E12); mockups approved by Manu on 2026-10-14; sync notices in-app only.

## PLAN-CHANGE 1 — B01 may repair pre-existing failing unit tests (2026-10-14)
- **What:** TASK-B01 may edit existing files under `app/src/test/` to make `testDebugUnitTest` pass, because 8 tests (`RegressionSourceContractTest` x4, `ImagePipelineContractTest` x2, `DriveRestoreDiagnosticsTest` x1, `CloudRestoreProgressTextTest` x1) fail against unchanged app source: CI never ran `testDebugUnitTest` before. Rules: (1) no app code changes; (2) no new libraries (no Robolectric); `Uri.parse` "not mocked" is solved by removing the Android `Uri` dependency from the test or, if unavoidable, `testOptions.unitTests.isReturnDefaultValues = true` in `app/build.gradle.kts` (already in B01's file list); (3) update an assertion only when it is stale relative to current behaviour; if a failure shows a real app bug, mark that single test `@Ignore("TODO: <reason>")`, list it in the receipt and the coordinator files a Follow-up; (4) every adapted/ignored test is listed in the receipt.
- **Why:** V-B01 requires `testDebugUnitTest` green; the failures predate B01 and are not caused by the toolchain. Rule 5 of tasks.md ("adapt a test only when the task says so") is satisfied by this change. No scope, UX, data model or dependency change.

## PLAN-CHANGE 2 — D01 may patch ManageTagsScreen save path (2026-10-14)
- **What:** TASK-D01 may edit `ui/ManageTagsScreen.kt` only in the tag save path, passing `description = existing?.description` and `suggestOccasions = existing?.suggestOccasions ?: true` when building `GarmentTag`.
- **Why:** without it, renaming a default tag (Accessories, Jewelry) rebuilds `GarmentTag` with the new defaults and silently re-enables occasion suggestions (DEC-007 intent: those two are off). It preserves existing data; no scope, UX, data-model or dependency change.

## PLAN-CHANGE 3 — E07 Cancel keeps the partial download; E07 may patch ModelStore (2026-10-14)
- **What:** In the model-download UI, Cancel stops the WorkManager job, keeps the `.part` file (so a later download resumes via HTTP Range, REQ-E01) and sets state to `NotDownloaded`. TASK-E07 may edit `suggestions/model/ModelStore.kt` only to catch cancellation in `download()` and publish `NotDownloaded` (today the state stays `Downloading` until restart).
- **Why:** the plan was silent on Cancel; E01 left cancellation unhandled. Resumable download is already a requirement; no scope, data-model or dependency change.

## PLAN-CHANGE 4 — S03 may edit the DriveWardrobeRepository interface (2026-10-14)
- **What:** TASK-S03 may edit `sync/DriveWardrobeRepository.kt` (the interface and its two implementers `NotConfiguredDriveWardrobeRepository`, `InMemoryDriveWardrobeRepository`) only for: (1) the new parameter `remote: WardrobeSyncSnapshot?` on `upsertSnapshot`, (2) a `photoUploadFailures` field on the upsert result.
- **Why:** tasks.md S03 requires the signature `upsertSnapshot(snapshot, remote)` and per-photo failures in the result, but the method is declared in that interface, which was missing from S03's file list. No behaviour, scope, UX, data-model or dependency change.

## PLAN-CHANGE 5 — S04 file list: DriveManifest.writerDeviceId and replaceLocalWithRemote (2026-10-14)
- **What:** TASK-S04 may also edit (1) `sync/DriveWardrobeRepository.kt`: add `writerDeviceId` to `DriveManifest` and copy it in `DriveManifest.fromSnapshot`, nothing else; (2) `sync/LocalWardrobeSyncSnapshotRepository.kt` and `data/local/EmbeddingDao.kt`: add `replaceLocalWithRemote(snapshot)` used by `useBackup()`.
- **Safety rules for `replaceLocalWithRemote`:** the remote snapshot JSON must already be fetched and decoded successfully before anything is cleared; one Room transaction clears garments, garment-tag and garment-colour mappings, photo references and `garment_embeddings`, then imports the snapshot taxonomies and records through the existing import path (default tags are seeded exactly as on a fresh install; no new taxonomy rules); local photo files are NOT deleted by this call (the existing restore path replaces them); on any exception the transaction rolls back and the conflict stays unresolved. Covered by a sqlite-jdbc or fake test that proves rollback leaves local data intact.
- **Why:** TS§4.5 and REQ-S08 require "Use backup" to clear and restore, and the TS§4.5 step-1 JSON-only manifest check needs `writerDeviceId`; both files were missing from S04's list. No scope, UX, data-model or dependency change.
