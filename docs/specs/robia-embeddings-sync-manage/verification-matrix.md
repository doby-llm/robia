# Verification matrix — robia-embeddings-sync-manage

Frozen with revision 1. Reviewers check only these rows; a new material criterion = planning change.
Evidence levels: **CI** (GitHub Actions on the task PR), **Pi** (Raspberry Pi tool run), **Device** (Manu, Poco F3, staging APK).

| ID | Req | Owner | Level | Exact assertion | Command / evidence |
|---|---|---|---|---|---|
| V-B01 | REQ-B01 | B01 | CI | `testDebugUnitTest assembleDebug assembleStaging` succeed with litertlm dependency | PR run URL |
| V-B02 | REQ-B02 | B01 | CI | artifacts `robia-debug-apk`, `robia-staging-apk`; same package name; staging not debuggable; same cert when secret present | `aapt2 dump badging`, `apksigner verify --print-certs` output in receipt |
| V-B03 | REQ-B04 | B02 | CI | `git grep -n -i -E "tflite|tensorflow|additionalinfo|additional_info|AiDetectedTag|rename_ai_tag|delete_ai_tag" -- . ':!docs/specs'` → no output | receipt |
| V-B04 | REQ-B04 | B02 | CI | CI green without TFLite steps | PR run URL |
| V-B05 | — | B03 | CI | CI green; `MainActivity` no longer constructs repositories | PR run URL + diff |
| V-D01 | REQ-E03/E07 | D01 | CI | migration SQL on v12 schema creates `garment_embeddings`, adds tag columns, sets `suggest_occasions=0` for 2 ids, resets FailedRetryable/NeedsUserAction(non-guarded) to Queued | `Migration12To13Test` |
| V-D02 | REQ-E09/S10 | D01 | CI | JSON round-trip preserves embeddings, description, suggestOccasions, writerDeviceId; base-commit snapshot fixture decodes with defaults | `SnapshotJsonCompatTest` |
| V-D03 | REQ-E03 | D01 | CI | embedding upsert leaves `clothing_items` row unchanged (updated_at, sync_revision, sync_status) | `EmbeddingRepositoryTest` (sqlite-jdbc) |
| V-D04 | REQ-E07 | D01 | CI | `DefaultTags` accessories/jewelry `suggestOccasions=false`, others true | `DefaultTagsTest` |
| V-S01 | REQ-S01 | S01 | CI | key regex; 10 restore cycles → constant local name ≤ 120 bytes | `PhotoBlobKeysTest` |
| V-S02 | REQ-S02 | S01 | CI+Device | constants WEBP q90 2048 present (CI); restored file is WebP and visually identical (Device V-Z04) | source test + Z01 |
| V-S05 | REQ-S05 | S01 | CI | classification table + retry delays | `DriveHttpErrorsTest`, `RetryPolicyTest` |
| V-S04 | REQ-S04 | S02 | CI | 34 photos, #5 fails → 33 restored, 1 guarded; peak concurrency 4; one listing per page | `HydrateConcurrencyTest` |
| V-S11a | REQ-S11 | S02 | CI | restore with equivalent merged snapshot → 0 uploads | `RestoreNoReuploadTest` |
| V-S11b | REQ-S11 | S02 | CI | 500 appends → ≤ 15 file writes | `RestoreSyncLogBatchingTest` |
| V-S03a | REQ-S03 | S03 | CI | unchanged 30 garments → 0 photo uploads | `UploadChangeDetectionTest` |
| V-S03b | REQ-S03 | S03 | CI | 1 changed → 1 upload; old blob deleted after snapshot upload | same |
| V-S03c | REQ-S03 | S03 | CI | snapshot upload failure → no deletion | same |
| V-S03d | REQ-S01 | S03 | CI | legacy long key → re-keyed short key, legacy deleted after verified snapshot | same |
| V-S08 | REQ-S08 | S03 | CI | guarded garment's remote photo record copied; blob not deleted | `GuardedIsolationTest` |
| V-S06 | REQ-S06 | S04 | CI | save → unique work `robia-drive-sync` requested with CONNECTED constraint | `DriveSyncSchedulerTest` (fake scheduler) |
| V-S07 | REQ-S07 | S04 | CI | 10 transient failures → status Queued, no attention | `TransientIsPendingTest` |
| V-S08b | REQ-S08 | S04 | CI | 1 guarded + 1 edited → upload happens | `ProcessorGuardTest` |
| V-S10a | REQ-S10 | S04 | CI | empty phone → full restore, no merge dialog | `ConnectFlowTest` |
| V-S10b | REQ-S10 | S04 | CI | non-empty phone → merge summary (X, Y, Z) correct | same |
| V-S10c | REQ-S10 | S04 | CI | remote writer ≠ device and newer revision → no upload, Conflict attention | `NewerWriterTest` |
| V-S09 | REQ-S09 | S05 | CI | each `SyncAttentionKind` maps to title, body and ≥ 1 action handler; old string absent | `SyncAttentionMappingTest` + grep |
| V-E02 | REQ-E04 | E02 | Pi | fixture valid; `export_fixture.py --check` recomputes identically; `garment_vector.b64` 768 floats | Pi command output |
| V-E04a | REQ-E05 | E04 | CI | TEXT margin 0.010 pass/fail; COMBINED 0.50 pass/fail; < 2 example categories → TEXT | `CategoryScorerTest` |
| V-E04b | REQ-E06 | E04 | CI | occasion rule branches {1}, {1,2}, ∅; flag off → skip | `OccasionSelectorTest` |
| V-E04c | REQ-E10 | E04 | CI | affected sets for added/renamed/deleted category, added occasion, restore defaults | `ReviewPlannerTest` |
| V-E04d | REQ-E11/12 | E04 | CI | 0.7/0.3 weighting; text term omitted when absent; missing vectors last; accent-insensitive fallback; similar excludes self | `SearchRankerTest` |
| V-E04e | REQ-E05/06 | E04 | CI | Kotlin results equal Python golden fixture (rank, selection, ±1e-4) | `GoldenFixtureTest` |
| V-E01a | REQ-E01/E02 | E01 | CI | ModelStore states incl. resume/mismatch/metered; holder idle release 60 s, serialization, ON_STOP | `ModelStoreTest`, `EmbeddingEngineHolderTest` |
| V-E01b | NFR-01/03, REQ-B03 | E01 | Device | staging APK: `suggestion_ms` median ≤ 2000; `peak_pss_mb` ≤ 1300; `parity_cosine` ≥ 0.98; no crash | benchmark text pasted by Manu |
| V-E05a | REQ-E05/06 | E05 | CI | service: model unavailable → no tag change; user override kept; flag off → no occasion | `SuggestionServiceTest` |
| V-E06a | REQ-E08 | E06 | CI | backfill order newest-first; stale text hash recomputed; skip after 2 failures | `EmbeddingBackfillTest` |
| V-E06b | REQ-E09 | E06 | CI | embedding-only change → snapshot upload, garment revision unchanged | `EmbeddingUploadTest` |
| V-E06c | REQ-E09 | E06 | CI | restore with mismatched modelId → not imported, queued | same |
| V-E07 | REQ-E01 | E07 | CI | prompt predicate: shown once, not when Ready | `ModelPromptPolicyTest` |
| V-E08a | REQ-E10 | E08 | CI | change-set built for each trigger; description-only edit/flag toggle → no review | `TagChangeSetTest` |
| V-E09 | REQ-E11 | E09 | CI | debounce 300 ms; fallback when engine unavailable | `SearchControllerTest` |
| V-E10 | REQ-E13 | E10 | CI | diagnostics visible only when dev mode unlocked+enabled | `DiagnosticsVisibilityTest` |
| V-M01 | REQ-M02 | M01 | CI | lazy row keys/contentType present; `toUiWardrobeItems` non-Composable | `ManagePerformanceContractTest` |
| V-Z01 | REQ-M02/NFR-02 | Z01 | Device | Manage 20 s scroll: janky frames ≤ 5 % | `adb shell dumpsys gfxinfo com.gusanitolabs.robia` |
| V-Z02 | NFR-01 | Z01 | Device | 5 new photos: suggestions appear ≤ 2 s after colors (dev diagnostics duration) | dev diagnostics |
| V-Z03 | REQ-S06/S07 | Z01 | Device | edit offline, close app, reconnect → Synced on next open; no error shown | observation |
| V-Z04 | REQ-S01/S02/S04/NFR-04 | Z01 | Device | fresh install restore: all photos present, time < 47 s (target ≤ 20 s); restore twice → names unchanged | timing + dev log |
| V-Z05 | REQ-E05/E06 | Z01 | Device | dress photo → Dresses marked; accessory → no occasion | observation |
| V-Z06 | REQ-E08 | Z01 | Device | after model download banner counts down to 0 | observation |
| V-Z07 | REQ-E10 | Z01 | Device | add category "Polos" with description → review lists only affected garments; accept applies | observation |
| V-Z08 | REQ-E11 | Z01 | Device | "vestido rojo" with no filters → red dresses first; with a filter → only filtered garments | observation |
| V-Z09 | REQ-S09 | Z01 | Device | revoke Drive access in Google account → "Sign in again" card, button works | observation |
| V-Z10 | REQ-M01/S09/E07 | Z01 | Device | screens match approved mockups | side-by-side screenshots |

## Review policy
- Each task: one consolidated review against its rows (spec + quality together); one remediation batch; final check. High-risk tasks with a second independent review: S03 (Drive deletion) and D01 (migration).
- Reviewers report only failed/missing rows; new ideas go to a follow-up list, not into the task.
