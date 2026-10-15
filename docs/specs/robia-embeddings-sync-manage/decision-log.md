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

## PLAN-CHANGE 6 — New task S06 "Sync progress UX", requested by Manu (2026-10-14)
- **What:** add TASK-S06 after S05: (A) the remaining-time text no longer flickers or shifts the layout; (B) the sync overlay shows one smooth determinate bar for the stage that downloads photos, and for the upload stage when photos are uploaded, instead of one bar that jumps by stage; short stages use an indeterminate indicator with their label; (C) the upload stage reports progress, so the bar no longer looks stuck after the download.
- **Why:** Manu's report from his Poco F3 on slow Wi-Fi. Read-only investigation on main: the bar uses only `completedWork/12` and ignores the item and byte progress that already exist; the ETA text is hidden for one frame on every update and is not smoothed; the upload stage (9 to 11 of 12) emits no progress, which matches the bar sitting at 75% after the garments were already imported. The whole-cycle timeout removal stays in S04 (TS section 4.5).
- **Order:** `ui/RobiaApp.kt` lease becomes B02 -> M01 -> S05 -> S06 -> E05 ...; `sync/WardrobeSyncOutboxProcessor.kt` lease becomes S02 -> S03 -> S04 -> S06 -> E06.
- Approved because it is a direct request from Manu; it adds one task and removes none.

## PLAN-CHANGE 7 — Newer-writer protection also covers backups without a writer id (2026-10-14)
- **What:** TS section 4.5 blocks an upload only when `remote.writerDeviceId != null && != deviceId && remote.revision > lastKnownRemoteRevision`. Now a remote whose `writerDeviceId` is null (every backup written before S04) or different from this device also counts as another writer when `remote.revision > lastKnownRemoteRevision` and the remote has user data. The upload is then replaced by the merge proposal (non-empty phone) or the Conflict state. The "merge proposed or declined" state is persisted in `SettingsRepository`, not kept in memory only.
- **Why:** independent review of S04 found that after declining a merge, restarting the app, or a background run before the proposal exists, the app would overwrite an existing pre-S04 Drive backup with the phone data and lose garments that exist only on Drive. This is a data-loss guard; no new UX or data model beyond one persisted flag.

## PLAN-CHANGE 8 — S04 review closure: explicit Retry moves to S05; fetchSnapshotRecords approved (2026-10-14)
- **Explicit Retry for `drive_full` / `photo_upload_failed` moves from S04 to S05.** S04 only parks those rows as NeedsUserAction (no automatic retry loop). S05 adds the Retry operation (a `WardrobeSyncOperation`, a DAO query that releases NeedsUserAction rows to Queued, exposed through the repositories), because S05 renders the attention row and owns its button; today there is no UI that could call it.
- **S04 may add `fetchSnapshotRecords` (JSON only, no blobs) to `DriveWardrobeRepository.kt` and `GoogleDriveWardrobeRepository.kt`** so uploads can copy guarded photo records from the remote (TS section 4.5; review finding 2). No other change in those files.
- **S04 accepted behaviour:** a newer backup from another phone shows the merge proposal first; Conflict appears after the user declines (declined state persisted as `syncConflict`). The pending merge proposal itself stays in memory and is proposed again after a restart; nothing is uploaded meanwhile.

## PLAN-CHANGE 9 — S05 file list and PhotoUploadFailed copy (2026-10-14)
- **S05 may edit** `sync/WardrobeSyncGateway.kt` (new `WardrobeSyncOperation` for Retry, new gateway method for Keep-without-photo), `sync/WardrobeSyncOutboxProcessor.kt` (dispatch of those operations and the exhaustive `when` in `affectedGarmentIds()` only), and `data/local/Dao.kt` (a query releasing NeedsUserAction rows to Queued for Retry; a query clearing `photo_restore_guarded` for Keep-without-photo), plus the repository wrappers they need. Rule from PLAN-CHANGE 8 stays: Retry is called by the button only, never by automatic cycles; Keep-without-photo clears the guard only for the chosen garment and never deletes a remote blob.
- **PhotoUploadFailed copy (EN/ES/DE), approved:** keep the title key `sync_attention_photo_upload_title`; add `sync_attention_photo_upload_body`. EN title "Some photos couldn't be backed up", body "Your garments are saved on this phone. A photo didn't upload to Google Drive. Tap Retry to try again." ES title "Algunas fotos no se pudieron copiar", body "Tus prendas están guardadas en este móvil. Una foto no se subió a Google Drive. Pulsa Reintentar para volver a intentarlo." DE title "Einige Fotos konnten nicht gesichert werden", body "Deine Kleidungsstücke sind auf diesem Handy gespeichert. Ein Foto wurde nicht zu Google Drive hochgeladen. Tippe auf Erneut versuchen, um es noch einmal zu versuchen." Action button reuses the existing Retry label.
- No scope, UX or data-model change beyond what S05 already owns.

## PLAN-CHANGE 10 — S06 may extend DriveWardrobeRepository.upsertSnapshot with an optional progress callback (2026-10-14)
- **What:** S06 may add `onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int) -> Unit)? = null` to `DriveWardrobeRepository.upsertSnapshot` (interface, implementations in `sync/DriveWardrobeRepository.kt` and `GoogleDriveWardrobeRepository.kt`, test fakes) and forward it from `WardrobeSyncOutboxProcessor` into `CloudRestoreProgress.uploadProgress`. Default null keeps every existing caller unchanged. No behavioural change to upload, purge or conflict logic.
- **Why:** the upload stage must report progress for the stuck-looking bar reported by Manu; the callback has to cross the interface.

## PLAN-CHANGE 11 — G-DEVICE-1 PASSED with 70 vision tokens; two tasks added from Manu's feedback (2026-10-14)
- **G-DEVICE-1 result (Poco F3, charging, staging APK from main run 38626273279):** 70 tokens, 3 runs: suggestion_ms median 1613 / 1675 / 1912 (limit 2000, pass), p90 2414 / 1937 / 2447, peak_pss_mb 783 / 657 / 648 (limit 1300), parity_cosine 1.0000. 140 tokens: median 4350 / 4236 (fail), pss 1111 / 819. Earlier runs (3584 and 8927 ms) were noise from unfavourable device state. **Decision: keep recipe `eg2-r1-vt70`; E05 to E10 are unblocked.** p90 is slightly above 2000 on two runs; the criterion is the median.
- **New task M02 "Manage UI polish" (Manu request):** (1) the Colors tab uses the same list format as Season/Occasions, not a boxed card; (2) scrolling to the bottom of any Manage tab leaves no blank space at the end; (3) remove the "Search tags" box from Manage (it takes space and makes tabs without it, like Colors, jump in height when switching). Files: the Manage screens only (`ui/ManageTagsScreen.kt` and the shared Manage scaffold in `ui/RobiaApp.kt`), strings only to remove the unused search label, tests. Lease: after S06, before E05 on `ui/RobiaApp.kt`.
- **New task S07 "Cloud backup upload stage" (Manu request, investigate first):** the "Updating cloud backup" screen after the photo download takes about 3 minutes, its bar advances at a constant rate (looks like a mockup), ends and stays there, and the user must close the app to continue (the data is already there). It also shows "3 out of 12 steps remaining" and Manu doubts that the 12 steps are real. Investigate whether the upload stage really emits progress after S06, whether the final upload hangs or finishes silently, why the cycle does not end, and whether the step count (`RESTORE_TOTAL_STEPS` = 12) reflects real work; then fix with per-stage honest progress and a clean completion. May touch `sync/` files; independent review required if it does.
- Note: the earlier stuck bar (S06 attempt 3) fixed only the Blocked/Failure branches; Manu's new observation says the Success path may still not finish, so S07 must start from a device log or a reproduction in a fake, not from assumptions.

## PLAN-CHANGE 12 — S07 scope from the read-only investigation (2026-10-15)
- **Findings (code reading, not device-verified):** the bar is real (per-photo fraction, 450 ms tween); the ~3 minutes are the final upload re-encoding and re-uploading every restored photo, because `planPhotoUploads` compares `known.sourceHash == state.sourceHash` and a restored photo's local hash (of the remote WebP) never equals the remote record's original `sourceHash`; the stall at 100% is the untimed tail after the last photo (full JSON PATCH with embeddings, listing, purge deletes, flush) with nothing in the UI naming it; "N of 12 steps" is a constant (`RESTORE_TOTAL_STEPS`), milestone 10 is never emitted and `completed` stays at 9 during the whole upload, so the text is not truthful; the Blocked/Failure upload branches set Complete with status Running and `finally` clears the overlay silently.
- **S07 scope (smallest first):** (1) do not re-upload a restored photo whose local file was restored from the remote blob and whose record still points at that same `blobPath` (treat as Reuse in `planPhotoUploads`; only when `restoredLocalUri` is set and the blob path matches; any doubt => upload as today; never delete or skip uploading a photo that was edited locally). (2) In the Uploading phase show "Uploading photos X of N" from `uploadProgress` and stop using the 12-step counter there (also hide the step counter in the Downloading phase; strings EN/ES/DE, section `<!-- TASK-S07 -->`). (3) Add a short "Finishing…" label after the photo loop (after the last photo, before Complete) so the tail is not read as a hang. (4) Blocked/Failure final-upload branches must not look like success: set a non-Running terminal status the existing overlay handles, or keep the clear but log it in the restore sync log; do not add UI beyond existing statuses.
- **Files:** `sync/GoogleDriveWardrobeRepository.kt` (`planPhotoUploads` / pipeline only), `sync/WardrobeSyncOutboxProcessor.kt` (phase/label emission, Blocked/Failure branches), `sync/WardrobeSyncGateway.kt` (only if a phase/label field is needed), `ui/CloudRestoreProgressText.kt`, `ui/RobiaApp.kt` (CloudRestoreProgressOverlay only), strings, tests. Independent review required (change 1 touches what is uploaded to Drive).

## PLAN-CHANGE 13 — S07 closure: reuse by content hash; Blocked/Failure overlay stays cleared (2026-10-15)
- **Reuse condition (supersedes PLAN-CHANGE 12 item 1):** a photo is reused (not re-uploaded) when the SHA-256 prefix of the real local bytes equals the remote record's `contentHash` prefix and the blob path is present in the Drive listing. `restoredLocalUri` is not required because upload snapshots are built from DB rows where it is always null. A null `contentHash`, a listing failure (fails the cycle) or a missing blob never reuses. Independent review found no data-loss path.
- **PLAN-CHANGE 12 item 4 is superseded by S06 attempt 3:** when the final upload is Blocked/Failure the restored wardrobe is already applied and WorkManager retries the upload, so the overlay is cleared at cycle end; the failure is in the restore sync log, no row is marked Synced. Accepted as a UX gap only.
- **Accepted:** the `N of 12 steps` line may still show during Preparing, Validating, Applying and RollingBack (these are real sequential milestones); it is hidden in Downloading and Uploading.

## PLAN-CHANGE 14 — Manu feedback of 2026-10-15 (email, saved in feedback/2026-10-15-manu-email.md): tasks F01, F02, F03 (2026-10-15)
- **F01 Browse, search and save flow** (`ui/RobiaApp.kt`, `suggestions/SearchController.kt`, strings, tests): (a) the "Similar garments" chip shows `Browse to ""` with empty quotes: replace the quotes with a clothing icon and add a small caption saying results are sorted by similarity; (b) searching "dress" gives no feedback (running / finished) and Browse barely changes: show a search state, make the ranking visibly reorder (most similar first) and show a small "Sorted by similarity" caption (first investigate why little changes: threshold, ranking, only IMAGE vs TEXT vectors); (c) after "Save item" the app lands on an "Item details / No garments yet" screen and the only exit is Back, which looks as if the item was not saved; fix the navigation so Save returns to Browse with the new item visible, clear any search/filters/similar state when a new item is added and sort by recently added.
- **F02 Tag edit dialog and return target** (`ui/ManageTagsScreen.kt`, `ui/TagReviewScreen.kt`, `ui/RobiaApp.kt`): default tags such as Dresses already have a description but the Description box is empty: show the default description (no hidden internal text the user cannot see or improve); remove the unnecessary blank gap between the Description box and "Suggest occasions for this category"; after finishing a review the app must return to Manage, not Browse.
- **F03 Unified tag-change review** (design in two steps): when a tag (category, season, occasion, colour) is added, edited or deleted, nothing visible happens and seconds later the app jumps into Review garments. Normalise to the colour flow Manu likes: after a change, propose a review; if declined everything stays as is (a deleted tag is still removed from garments that carry it); if accepted, open a mapping screen like "Color mapping" with scan progress of the wardrobe and, below it, garments appearing as suggestions are found; replace the bottom button by "Accept all". Occasions deletion (e.g. "Dressed-up") must also offer this review. Review garments fixes: "From -> To" for categories breaks across lines ("Trousers -> Shoes" wraps and enlarges the card), make it a stable layout; make the review progress bar smooth (the odd dot at its end is optional, skip if unclear). Colour mapping bug: after deleting Yellow the review listed one T-shirt going from "Black, Brown" to "Black, Brown", which is not a change and unrelated to yellow: investigate and fix (suspect: review lists unchanged garments or compares against the wrong tag).
- **Order and leases (one writer on `ui/RobiaApp.kt`):** F01 first, F03 investigation read-only in parallel, then F02, then F03 implementation. Independent review is not required unless `sync/` is touched.
- **Z01 stays open:** the device checklist still applies; these tasks add rows V-Z13 (F01), V-Z14 (F02) and V-Z15 (F03) to it after merge.

## PLAN-CHANGE 15 — F02/F03 design from the read-only investigation (2026-10-15)
- **Supersedes DEC-010 (occasion deletion has no review):** deleting an occasion now also offers the review (proposal = current occasions minus the deleted one, no replacement forced). Manu asked for it explicitly.
- **Save-first model everywhere (colour and tags):** a tag/colour change is saved first, then a review is proposed. Declining keeps the change; a deleted tag stays removed from the garments that carried it. The current colour path for colours referenced by garments (palette not saved until commit; discard cancels the deletion) is changed to save-first so colour and tags behave identically.
- **After any review (commit or discard, colour or tags) the app returns to Manage**, on the tab that was open (hoist `selectedTabId` into `RobiaApp` if needed), not Browse.
- **"Accept all"** replaces the bottom button in both reviews. It accepts every proposal that has a concrete target. Proposals needing a manual choice (deleted category with no vector, colour with no analyzer target) are excluded and stay visible; the button label says how many will be accepted.
- **Tag review scan:** replace the synchronous `planTagReview` by an incremental, cancellable scan on `Dispatchers.Default` with a progress header like the colour mapping and proposals appended as found; cancel on navigation; ignore results if the change set is no longer current; show a "model not ready" state instead of silently skipping. Hoist the `examples` map out of the per-garment loop in `ReviewPlanner.plan`.
- **Colour bug fix:** list a garment only when its resolved target colour ids differ from its current ones or a referenced id is missing from the palette (pure function with unit tests); pin the candidate list to a snapshot at review start so a sync emission does not restart the scan and drop accepted items.
- **From -> To layout:** stacked, no `weight` columns: full-width "From" line (maxLines 1, ellipsis), then a full-width "-> To" chip (maxLines 1). **Progress bars** animate (300 ms) in the tag review like the colour review; the end dot is the Material3 stop indicator: remove it with `drawStopIndicator = {}` only if the resolved material3 version supports it, otherwise skip.
- **Default tag descriptions:** the editor prefills `DEFAULT_LABEL_DESCRIPTIONS[id]` when the stored description is null and stores null when the trimmed text equals the default (so defaults do not become "modified"); descriptions are localised through string resources (EN/ES/DE). The blank gap in `TagEditorDialog` comes from `spacedBy(12.dp)` plus the Switch row's 48 dp touch target: tighten both.
- **Task split (one writer on `ui/RobiaApp.kt`, order F01 -> F02 -> F03a -> F03b):** F02 = dialog fixes + return to Manage. F03a = colour bug, save-first colour, shared `ui/ReviewScaffold.kt` extraction, Accept all for colour. F03b = tag scan incremental + occasions review + Accept all + From->To layout + bar. No `sync/` changes; independent review not required.
