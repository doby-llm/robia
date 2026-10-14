# Requirements — robia-embeddings-sync-manage

Revision: 1 · Base commit: `origin/main` = `791dacdb976f2c46075ba5862037c579bc91b413` · Status: awaiting approval.

IDs are stable. `REQ-*` = functional, `NFR-*` = non-functional. Decisions are in `decision-log.md` (`DEC-*`).

## Scope summary

Four capabilities (see `README.md` capability map):

| Capability | ID prefix | Summary |
|---|---|---|
| `build-foundation` | REQ-B | Toolchain upgrade, staging APK, remove old classifier |
| `cloud-sync` | REQ-S | Robust and fast Drive sync, actionable error UX |
| `smart-suggestions` | REQ-E | EmbeddingGemma 2 suggestions, review flow, search, dev mode |
| `manage-redesign` | REQ-M | Manage redesign and scroll performance |

## Non-goals

- Fine-tuning EmbeddingGemma (DEC-012). Dark theme. Play publication. True multi-device concurrent editing. Baseline profiles. Device allow-list (arm64 assumed). Season prediction. Android system notifications for sync.

---

## build-foundation

### REQ-B01 — Toolchain compatible with LiteRT-LM
**Priority:** must · **Actor:** system
**Statement:** The build shall use Kotlin 2.4.x, a matching KSP, JDK 21 (compile + CI), and an AGP version supported by that Kotlin, so that `com.google.ai.edge.litertlm:litertlm-android:0.18.0` (Java 21 bytecode, Kotlin 2.4 metadata) compiles and links.
**Acceptance:** Given CI on a PR, when `assembleDebug` and `assembleStaging` run, then both succeed with the LiteRT-LM dependency declared, and all existing JVM unit tests pass.

### REQ-B02 — Staging APK
**Priority:** must · **Actor:** operator
**Statement:** CI shall build, in addition to the debug APK, a `staging` APK: R8 minify + resource shrink, not debuggable, signed with the CI debug key, same `applicationId`, installable over the debug APK without data loss.
**Acceptance:** The workflow uploads artifacts `robia-debug-apk` and `robia-staging-apk`; `apksigner verify --print-certs` shows the same certificate for both when the CI debug keystore secret exists; `aapt2 dump badging` shows `package: name='com.gusanitolabs.robia'` for both and `application-debuggable` only for debug.
**Edge cases:** missing CI signing secret on PRs → staging is signed with the default debug key (same rule as today's debug job).

### REQ-B03 — R8 keep rules
**Priority:** must · **Statement:** Keep rules shall protect LiteRT-LM (JNI + Gson reflection), Gson, kotlin-reflect usages, ML Kit subject segmentation and Room. The same rules apply to `release`, which also enables minify.
**Acceptance:** Staging APK starts, adds a garment with background removal, loads the embedding model and computes one embedding without crash (device check in TASK-E01 / TASK-Z01).

### REQ-B04 — Old classifier removed
**Priority:** must · **Statement:** All TFLite "additional info" code, assets, Gradle modules, scripts, tests, docs, CI steps and UI warnings shall be removed. Season keeps manual tagging.
**Acceptance:** `git grep -n -i -E "tflite|tensorflow|additionalinfo|additional_info|AdditionalInfo|AiDetectedTag|rename_ai_tag|delete_ai_tag"` returns no matches outside `docs/specs/`. CI no longer runs the TFLite drift check.

---

## cloud-sync

### REQ-S01 — Stable short photo keys (fixes lost photos)
**Priority:** must · **Statement:** Drive photo blob names and restored local file names shall not depend on previous file names. Drive key: `photos/<garmentId>/<sha256-first-16-hex>.webp`. Restored local file: `restored-<garmentId>-<sha256-first-16-hex>.webp`.
**Acceptance:** Given a garment restored N times (N = 1..10), the local file name length stays constant (≤ 120 bytes) and restore succeeds. Unit test simulates 10 cycles.
**Edge cases:** legacy blobs with long names are still readable once (exact blobPath from snapshot) and are deleted after the new key is uploaded and verified (REQ-S03).

### REQ-S02 — WebP photos in Drive
**Priority:** must · **Statement:** Uploaded photos shall be WebP lossy quality 90, longest edge ≤ 2048 px, alpha preserved. Local photo files are not rewritten except on restore.
**Acceptance:** Unit/instrumented-free test of the encoder helper on a fixture bitmap: output starts with `RIFF....WEBP`, decodes, longest edge ≤ 2048, alpha preserved for a transparent input.

### REQ-S03 — Upload only what changed; no orphans
**Priority:** must · **Statement:** A sync shall upload a photo only when its content hash differs from the hash recorded in the last uploaded snapshot for that garment, or the blob is missing remotely. After a verified upload of the snapshot, blobs under `photos/<garmentId>/` that are not referenced by the snapshot and blobs of tombstoned garments shall be deleted.
**Acceptance:** With a fake Drive API: sync of an unchanged 30-garment wardrobe performs 0 photo uploads; changing 1 photo performs exactly 1 upload; after re-keying a legacy blob, the old blob is deleted only after the snapshot upload succeeded.

### REQ-S04 — One listing, concurrent streamed downloads, per-photo isolation
**Priority:** must · **Statement:** Restore shall list `appDataFolder` once (paginated) and resolve blobs by name from that listing; download up to 4 photos concurrently, streaming to a temp file and renaming atomically; apply a per-photo timeout (60 s) instead of a global phase deadline; a failed photo marks only that garment as "photo pending" and the restore continues.
**Acceptance:** Fake API test: 34 photos with photo #5 failing (IOException) → 33 restored, 1 guarded, restore result success-with-attention; max concurrent fetches observed = 4; listing endpoint called once per page.

### REQ-S05 — Correct HTTP error classification
**Priority:** must · **Statement:** 401 → auth required. 403 with reason `rateLimitExceeded`/`userRateLimitExceeded`, 429, 5xx, timeouts, DNS/connect errors → transient (retry with exponential backoff + jitter, max 5 attempts per request). 403 `storageQuotaExceeded` → "Drive full" (user action). Other 403 → auth required.
**Acceptance:** Table-driven unit test over status code + error body JSON samples.

### REQ-S06 — Background sync with WorkManager
**Priority:** must · **Statement:** Pending changes shall be uploaded by a unique WorkManager job (`robia-drive-sync`, `NetworkType.CONNECTED`, exponential backoff starting 30 s) even when the app is closed. In-app triggers enqueue the same unique work (`ExistingWorkPolicy.KEEP`/`APPEND_OR_REPLACE` per spec). Embedding computation is never done in this worker.
**Acceptance:** Unit test with `WorkManagerTestInitHelper` (or a fake scheduler interface) shows enqueue on save; device check: edit offline, close app, reconnect → garment shows "Synced" on next open without user action.

### REQ-S07 — Transient errors are not failures
**Priority:** must · **Statement:** Network/transient errors shall leave work in a "pending" state with unlimited retries and no user-visible error. Only user-action errors (auth required, Drive full, newer writer conflict, photo unrecoverable) produce an error state.
**Acceptance:** DAO test: 10 consecutive transient failures leave `sync_status = 'Queued'` (displayed "Pending upload"), never `NeedsUserAction`.

### REQ-S08 — Guarded photo isolates only its garment
**Priority:** must · **Statement:** A garment with a guarded (not restored) photo shall not block uploads of other garments. The snapshot upload keeps the guarded garment's remote photo record unchanged (never deletes its blob).
**Acceptance:** Fake test: 1 guarded garment + 1 edited garment → upload happens; guarded garment's remote `photos` record and blob remain.

### REQ-S09 — Actionable sync status UX
**Priority:** must · **Statement:** The string "Sync failed — retry/details" is removed. Per-garment and global status shall show one of: Synced, Pending upload, Uploading…, or an action card with plain cause + working buttons: "Sign in again", "Drive is full · Retry", "Photo could not be recovered · Retry photo / Keep without photo", "Another phone updated your backup · Review". A "Details" button opens the existing sync log only in developer mode. Texts in EN/ES/DE.
**Acceptance:** Every button invokes its handler (unit tests on the state→action mapping); screenshot of mockup states matches implementation (manual check in TASK-Z01).

### REQ-S10 — Newer-writer protection and phone change
**Priority:** must · **Statement:** Snapshot metadata shall carry `writerDeviceId` (random UUID stored in DataStore) and `revision`. Before uploading, if the remote snapshot's writer differs from this device and its revision is newer than this device's last known remote revision, the app shall not upload and shall show "Another phone updated your backup" with options "Use backup (replace this phone)" and "Keep this phone (overwrite backup)". A fresh/empty install restores everything automatically (existing behaviour preserved). Connecting Drive on a non-empty phone shows a merge summary "Drive: X, phone: Y → Z" before merging (newest edit wins; a later delete wins).
**Acceptance:** Fake-API tests for: empty phone restore; non-empty phone merge summary numbers; newer-writer conflict blocks upload.

### REQ-S11 — Restore speed hygiene
**Priority:** should · **Statement:** No full re-upload after restore when nothing changed; no triple decode per photo after write (one bounds decode); developer sync log writes batched (append-only file, flush at most every 2 s or 50 events, trimmed when > 256 KB).
**Acceptance:** Fake test: restore of 34 photos performs 0 uploads; log repository test: 500 appends → ≤ 15 file writes.

---

## smart-suggestions

### REQ-E01 — Model download
**Priority:** must · **Statement:** The model `embeddinggemma-2-text-vision-440m.litertlm` shall be downloaded from the pinned Hugging Face revision (DEC-003), resumable (HTTP Range), Wi-Fi only unless the user confirms mobile data, verified by SHA-256 before use, stored in `filesDir/models/`. Prompt "Download now / Later" on first launch after the update (once); Settings entry to download, see progress, cancel, delete.
**Acceptance:** Unit tests for state machine (NotDownloaded → Downloading → Verifying → Ready / Failed(reason)) with a fake HTTP source including a resume and a hash mismatch (file deleted, state Failed(CorruptDownload)).

### REQ-E02 — Engine lifecycle
**Priority:** must · **Statement:** A single app-wide engine holder shall load lazily, serialize requests (one at a time), preload on: entering Add (single/batch), first keystroke in search, opening a tag review, starting the embedding backfill; release after 60 s idle, on app background (process ON_STOP) and on `onTrimMemory(≥ TRIM_MEMORY_RUNNING_LOW)`.
**Acceptance:** Unit tests with a fake engine factory and virtual time: idle release at 60 s; concurrent requests never overlap; ON_STOP closes.

### REQ-E03 — Two vectors per garment
**Priority:** must · **Statement:** Each garment stores an IMAGE vector (image only) and a TEXT vector (name + notes; absent when both empty). Tags are never input. Vectors are stored in a separate table; computing them does not change `updatedAtEpochMillis`, `syncRevision` or sync status of the garment.
**Acceptance:** DAO test: upserting an embedding leaves the garment row byte-identical.

### REQ-E04 — Fixed recipe
**Priority:** must · **Statement:** Image input = the saved garment photo (ML Kit cutout PNG) composited on white, longest edge 1024 px, encoded PNG, passed as `InputData.Image`; vision tokens 140; normalize = true. Label text = `"task: classification | text: " + description`. Query text = `"task: search query | text: " + query`. Garment TEXT vector input = `"title: none | text: " + name + ". " + notes` trimmed (EmbeddingGemma document format; confirmed on the Pi tool in TASK-E02). `recipeId = "eg2-r1"`, `modelId = "embeddinggemma-2-text-vision-440m@e301f74d"`.
**Acceptance:** Constants in one Kotlin file; parity test against fixture vectors produced by the Pi tool (`tools/embedding-precision`) — cosine ≥ 0.98 for the 3 fixture images is checked on device in TASK-E01 (not in JVM CI).

### REQ-E05 — Category suggestion
**Priority:** must · **Statement:** At capture (new photo, Quick Edit re-process, batch processing), the app predicts category with the combined algorithm (DEC-008) and marks the top category only if the margin rule passes; otherwise marks nothing and shows no chips. Manual user choices in the editor always win.
**Acceptance:** Pure-Kotlin unit tests with fixture score vectors covering: text-only pass/fail at 0.010; combined pass/fail at 0.50; categories without examples; < 2 categories with examples; user override preserved.

### REQ-E06 — Occasion suggestion
**Priority:** must · **Statement:** Same moment as REQ-E05. Not predicted when the selected (or predicted) category tag has `suggestOccasions = false`. Rule: if top1−top2 > T → {top1}; else if top2−top3 > T → {top1, top2}; else none. T = 0.010 raw cosine (text-only mode) or 0.50 in z units (combined mode, same equivalence as category, DEC-009).
**Edge cases:** no category determined → occasion predicted anyway (flag check only applies when a category is known). Manual category change later does not touch occasions.
**Acceptance:** Unit tests for each branch and the flag.

### REQ-E07 — Tag description and suggest-occasions flag
**Priority:** must · **Statement:** Tags gain an optional `description` (≤ 200 chars) and category tags gain `suggestOccasions` (default true; false for `category-accessories` and `category-jewelry`). The tag editor shows both (description with example placeholder; switch only for category tags). Both sync to Drive as optional fields. Default tags use internal English descriptions until renamed or given a user description.
**Acceptance:** Migration test v12→v13 sets defaults; round-trip JSON test of optional fields; old snapshot without fields decodes with defaults.

### REQ-E08 — Background backfill and indicator
**Priority:** must · **Statement:** While the app is in the foreground and the model is Ready, garments missing a vector for the current modelId+recipeId (or whose input hash changed) are computed one by one, newest first. Browse shows "Preparing suggestions for N garments…" with a thin progress bar while N > 0; it disappears at 0. Pausing on background, resuming on foreground.
**Acceptance:** Unit test of the queue selection query; manual device check.

### REQ-E09 — Embeddings in Drive
**Priority:** must · **Statement:** Snapshot garments gain optional `embeddings: [{kind, modelId, recipeId, dim, inputHash, b64}]` (float32 little-endian, base64). No schemaVersion bump. On restore, vectors are imported only when modelId and recipeId match the app's current ones; otherwise they are queued for backfill. Uploading a garment never waits for its embedding; an embedding computed later is uploaded with the next snapshot without changing the garment revision.
**Acceptance:** JSON round-trip test; old-format snapshot decodes; mismatch → not imported.

### REQ-E10 — Tag change review
**Priority:** must · **Statement:** Adding, renaming, deleting a category or occasion tag, or restoring defaults of those categories, opens a review screen listing only affected garments (DEC-010) with proposed changes the user can accept, edit or skip. Nothing is applied without acceptance. Garments without IMAGE vector are skipped and counted. Toggling `suggestOccasions` or editing only a description does not open a review.
**Acceptance:** Pure-Kotlin tests of the affected-set computation for each trigger; UI follows `ColorReviewScreen` pattern.

### REQ-E11 — Semantic search in Browse
**Priority:** must · **Statement:** Browse gets a search field. With a non-blank query and model Ready: results = items passing current filters, sorted by `0.7·cos(q, IMAGE) + 0.3·cos(q, TEXT)` (TEXT term omitted → image score only), items without IMAGE vector last (by name). No threshold. With model not Ready: case/accent-insensitive substring match on name + notes, filtered items not matching are hidden. Debounce 300 ms.
**Acceptance:** Pure-Kotlin ranking test; manual check "vestido rojo".

### REQ-E12 — Similar garments
**Priority:** could (subject to mockup approval) · **Statement:** Item detail shows "See similar garments", opening Browse sorted by cosine to the garment's IMAGE vector (excluding itself), keeping filters cleared.
**Acceptance:** Ranking unit test.

### REQ-E13 — Developer diagnostics
**Priority:** must · **Statement:** With developer mode enabled, item detail shows: top-5 category and occasion scores, applied/not and reason, modelId, recipeId, computed-at, inputs used, duration; buttons "Recompute embedding" and "Export input image". Settings (developer section) adds "Model benchmark": load time, median/p90 per-image time over 5 runs, peak PSS, vision tokens.
**Acceptance:** Visible only in dev mode (unit test on visibility predicate); benchmark output format documented.

### REQ-E14 — Latency budget
**Priority:** must · see NFR-01.

---

## manage-redesign

### REQ-M01 — Manage redesign
**Priority:** must · **Statement:** Manage is implemented per the approved mockup `mockups/manage.html` (Earth & Steel, light). Tag editor includes description and suggest-occasions switch.
**Acceptance:** Manual visual comparison on device against the mockup screenshot.

### REQ-M02 — Smooth scrolling
**Priority:** must · **Statement:** Manage list renders one lazy item per section header and per tag row (stable keys, contentType); no per-recomposition mapping of all wardrobe items when the route does not need them.
**Acceptance:** See NFR-02.

---

## Non-functional

### NFR-01 — Suggestion latency
Extra time from end of color extraction to suggestions applied ≤ 2.0 s (median of 5 photos) on Poco F3, staging APK, model already loaded. Model load time is measured and reported but not gated. If > 2.0 s with vision tokens 140 → retry with 70; if still > 2.0 s → STOP, replan with Manu.

### NFR-02 — Manage smoothness
Staging APK on Poco F3, `adb shell dumpsys gfxinfo com.gusanitolabs.robia` after 20 s of continuous scrolling on Manage: janky frames ≤ 5 %.

### NFR-03 — Memory
Peak PSS of the app while computing an embedding ≤ 1.3 GB on Poco F3; engine released within 65 s of last use (PSS drops ≥ 300 MB).

### NFR-04 — Restore speed
Restore of 34 photos on Wi-Fi completes ≤ 20 s (today 47 s) on Poco F3 — measured, reported; gate is "faster than 47 s".

### NFR-05 — Privacy
No photo, name, note, token or email in logs outside developer-mode sync log (existing sanitization rules). Model download sends no user data.

### NFR-06 — Localization
All new user-visible strings exist in `values/`, `values-es/`, `values-de/`.

## Decision category checklist

| # | Category | State |
|---|---|---|
| 1 | Product/UX/accessibility | DECIDED (mockups, human_summary) |
| 2 | Scope/non-goals | DECIDED |
| 3 | Functional/edge cases | DECIDED (above) |
| 4 | Data model/migration/rollback | DECIDED (Room v13, optional Drive fields, DEC-006) |
| 5 | Contracts (Drive JSON, recipe) | DECIDED (DEC-004, DEC-007) |
| 6 | Architecture | DECIDED (technical-specification) |
| 7 | Dependencies | DECIDED (DEC-001, DEC-002) |
| 8 | Security/privacy | DECIDED (NFR-05, SHA pin) |
| 9 | Performance | DECIDED (NFR-01..04) |
| 10 | Observability | DECIDED (dev mode, sync log) |
| 11 | Deployment/config | DECIDED (CI artifacts) |
| 12 | Compatibility | DECIDED (old snapshots readable; arm64 assumed) |
| 13 | Rollback/recovery | DECIDED (implementation-plan per slice) |
| 14 | Testing | DECIDED (verification-matrix) |
| 15 | Operation | N/A — personal app, no service |
| 16 | Cost/licence | DECIDED (Apache-2.0 model, free HF download) |
