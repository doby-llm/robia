# Verification matrix — robia-round3

Levels: **CI** = JVM test/build in GitHub Actions on the PR; **REC** = command output in the receipt; **REV** = independent reviewer or coordinator check; **DEV** = device row for Manu (or the coordinator during an ADB gate), collected in CHK.

| ID | Task | Level | Check | Pass |
|---|---|---|---|---|
| V-K00-1 | K00 | CI | `SettingsMappingTest`: absent `cutout_engine` → Standard; stored Automatic and migration flag absent → Standard and flag `cutout_default_v3_applied` written; flag present + Automatic → Automatic (the user may opt in again) | pass |
| V-K00-2 | K00 | CI | `PreciseCutoutSettingsTest`: the Automatic option carries the Experimental label resource; build green | pass |
| V-K00-D | K00 | DEV | After updating the app, Settings › Cutout shows Standard selected and the precise option labelled "Experimental" | Manu |
| V-K01-1 | K01 | CI | `CutoutFallbackChainTest`: reason is `none` when precise is accepted, `not_active`, `tripped`, `timeout`, `error`, `not_removed` for each path; mlKit result returned unchanged in every fallback case | pass |
| V-K01-2 | K01 | CI | `CutoutDebugDumpTest`: `stageFileName(stage)` table equals `tools/cutout-diag/compare.py` STAGES + `s3_hanger.json`, `s4b_filled.png`, `final.png`, `mlkit.png`; `reportJson(...)` has keys work, s2_points, hanger_box, kill, band_bottom, seconds, opaque_fraction_of_s2, engine, fallback | pass |
| V-K01-3 | K01 | REV | grep: the dump runner is reachable only behind `BuildConfig.DEBUG`; the default `StageSink` is a no-op object and the release path allocates no stage copies | confirmed |
| V-K01-D | K01 | DEV | G-ADB1: the dump runs on the phone for every image in `cutout-debug/in/` and writes the files | coordinator via ADB |
| V-K02-1 | K02 | CI | `HangerRemovalTest`: band reselection that keeps < 85 % of the first selection inside the band falls back to the first selection there (pure `guardedBand`) | pass |
| V-K02-2 | K02 | CI | `GarmentLossGuardTest`: final opaque area < 70 % of the max(S1 hard, S2 hard) (R3-DEC-022) → `ORIGINAL_FALLBACK with failure message garment_loss` (engine returns not-REMOVED, chain falls back, reason `garment_loss`) | pass |
| V-K02-3 | K02 | CI | `MaskOpsTest`: SAM point/box scaling for work sizes 576×1024, 1024×768, 800×600 equals the reference formula (long edge → 1024, coords × scale) | pass |
| V-K02-4 | K02 | REC | `python -I tools/cutout-diag/compare.py <app dump> <ref dump>` on the G-ADB1 set after the fix (coordinator re-runs at G-ADB2) | exit 0 |
| V-K02-5 | K02 | REV | independent review: every item of the G-ADB1 defect list is fixed or explicitly reported as not reproduced; no threshold changed outside the list | confirmed |
| V-K03-1 | K03 | CI | Regression row (R3-DEC-028): `MaskOpsTest` synthetic hanger-band photo passes (transition width of `refineAlpha` output in the band ≤ 1.5× width on the sides), and the side-edge width helper matches the compare.py definition (thresholds 0.05 and 0.95, per-row halving, max > 0.95 gate). The synthetic scene does not fail on pre-K03 code (Python float32 replica, not the JVM: band-to-body ratio 1.0 with the old and the new guide; none of 475 random scenes has the old guide above 1.5× and the new one within it), so this row does not show that the band is fixed | pass |
| V-K03-2 | K03 | CI | `HangerRemovalTest`: pixels within 2 px of the kill support get the fill colour in the composited RGB; pixels farther away are untouched | pass |
| V-K03-3 | K03 | REC | compare.py on the red-shorts dump: `band_edge_ratio` ≤ 1.5 and all stage IoUs still pass (coordinator, G-ADB2). This is the only evidence that the band is fixed (R3-DEC-028) | pass |
| V-K03-D | K03 | DEV | Red shorts redone with Experimental: top edge as crisp as the sides (G-CUT photo set) | Manu |
| V-ED1-1 | ED1 | CI | `SelectionClipTest`: union of 2 blobs, tap inside blob A → only A kept; tap on a pixel outside any blob → empty; 8-connectivity | pass |
| V-ED1-2 | ED1 | CI | `SelectionClipTest`: `removesMostOfGarment(selection, alpha)` true when ≥ 60 % of opaque (alpha > 0.5) pixels are selected, false at 59 % | pass |
| V-ED1-3 | ED1 | CI | `SmartGestureTest`: a 2 s drag produces a selection request (no time cap); path sampled to ≤ 12 points evenly by length; a tap produces 1 point | pass |
| V-ED1-4 | ED1 | CI | strings `redo_cutout` EN "Cut out again", ES "Volver a recortar", DE "Erneut freistellen"; new `editor_smart_whole_garment` in 3 locales | present |
| V-ED1-D | ED1 | DEV | Remove › smart: dragging shows the trail; tapping a leftover background piece removes only that piece; tapping the garment shows the hint and removes nothing | Manu |
| V-ED2-1 | ED2 | CI | `EditHistoryTest`: 30 random ops then undo×30 returns the base alpha exactly; redo×30 returns the final alpha exactly; a new op after 5 undos drops the 5-step redo tail | pass |
| V-ED2-2 | ED2 | CI | `EditHistoryTest`: budget (R3-DEC-027) — with a fake 1 MB budget, oldest ops fold into a checkpoint, `canUndo` stays true down to the checkpoint and the state is still exact; a "Cut out again" photo that the budget cannot hold is dropped with the steps before it, so undo stops at the cut-out and never restores a wrong photo; saving the garment clears the history (`canUndo` and `canRedo` false, temp files deleted; new test required, not yet written) | budget and fold: pass; save clear: pending |
| V-ED2-3 | ED2 | CI | `EditHistoryTest`: "Cut out again" step stores the previous photo as a temp file; undo restores the previous photo and its own history position; temp files deleted on `close()` | pass |
| V-ED2-4 | ED2 | REV | no history state survives the editor (no saveable/persisted fields); each recycled bitmap is never drawn afterwards | confirmed |
| V-ED2-D | ED2 | DEV | Editor, adding a new photo (R3-DEC-027): 15 mixed edits + 2 "Cut out again" can all be undone and redone in order at the test photo size, which must fit the 64 MiB history budget (a 2048x2048 photo does not: there undo stops at the cut-out, by design). Manu's device check covers photos within the budget only. On a saved garment, undo works only within the open editor session; "Cut out again" is not undoable there (R3-DEC-031) | Manu |
| V-GPU-1 | GPU | CI | `GpuBenchmarkReportTest`: report string for success (load, median, p90, PSS, parity) and for failure (`GPU unavailable: <class>`) — pure formatter | pass |
| V-GPU-2 | GPU | REV | production embedder still constructed with `Backend.CPU()`; GPU path only from the developer row; `Throwable` other than cancellation caught and reported | confirmed |
| V-GPU-D | GPU | DEV | G-ADB1: coordinator runs the GPU row and records numbers vs CPU (`RobiaBenchmark` logcat line) | coordinator |
| V-OR1-1 | OR1 | CI | `Migration13To14Test`: the exact `MIGRATION_13_14_SQL` adds `original_uri TEXT`, `original_recipe TEXT` (nullable) to `clothing_items` on a v13 sqlite-jdbc DB with rows; old rows read null | pass |
| V-OR1-2 | OR1 | CI | `CutoutRecipeTest`: JSON round trip of `CutoutRecipe(engine, removeHanger, hangerThreshold, edges, workW, workH, rotationDegrees, sampleSize, cropLeft, cropTop, cropRight, cropBottom, version=1)`; unknown keys ignored; missing → null | pass |
| V-OR1-3 | OR1 | CI | `CutoutRecipeTest`: precise crop rect in original-frame pixels equals the rect `cropToAlpha` used (fake alpha); ML Kit recipe has the full frame and the power-of-two sample size | pass |
| V-OR1-4 | OR1 | REV | after a successful single save and a batch save, the raw `gallery-*`/`camera-*` copy and `subject-*.png` of that photo are deleted; on fallback-to-original nothing referenced is deleted | confirmed |
| V-OR1-D | OR1 | DEV | A new garment shows "Original photo" in Export (after EX1) and its original file is ≤ 2048 px long edge (1024 for Experimental) | Manu |
| V-OR2-1 | OR2 | CI | `SnapshotJsonCompatTest`: schema-2 fixture without the new fields decodes (fields null) and re-encodes byte-identical; a record with `originalBlobPath/originalHash/originalByteSize/recipe` round-trips | pass |
| V-OR2-2 | OR2 | CI | `OriginalBlobTest`: key `originals/<id>/<16 hex>.jpg` (pure function + regex); `isLegacyKey` unchanged for `photos/`; purge never deletes an `originals/` blob whose garment still has a snapshot record, even when the record lacks the original fields (old-app re-upload) | pass |
| V-OR2-3 | OR2 | CI | `DriveUsageTest`: `originals/` counted in a separate `originalCount/originalBytes`, not in photos or backup; `PhotoIntegrityCheckTest`: originals never counted as orphan photos | pass |
| V-OR2-4 | OR2 | REV | restore downloads originals after all cutouts; a missing/corrupt original logs and continues, never sets `photoRestoreGuarded`; upload reuses an existing original blob by hash+size | confirmed |
| V-OR2-D | OR2 | DEV | Reinstall + restore: a garment added after OR1 has its original again (Export shows "Original photo"); Drive settings shows the originals line | Manu |
| V-OR3-1 | OR3 | CI | `OriginalMappingTest`: for a synthetic frame + recipe (crop rect, rotation 0/90), mapping cutout pixel → original pixel is exact at corners and centre; out-of-crop pixels map outside | pass |
| V-OR3-2 | OR3 | CI | `RefineSessionTest`: Keep with an original source raises alpha from 0 and takes RGB from the original; without original the old rule (base alpha < 0.05 unchanged) holds | pass |
| V-OR3-3 | OR3 | CI | parity (REQ-OR-02): re-running the crop of a stored recipe on the stored frame yields the stored cutout size and alpha bbox (pure function on synthetic data) | pass |
| V-OR3-D | OR3 | DEV | New garment → edit photo → Keep brush over removed background brings it back; "Cut out again" is offered for a saved garment with original | Manu |
| V-CLN-1 | CLN | CI | `OrphanPhotoCleanupTest`: pure planner — referenced (photo_uri, original_uri, any open batch/editor draft) kept; unreferenced matching prefixes older than 24 h deleted; younger kept; other prefixes (`restored-*`, `original-*`) never deleted | pass |
| V-CLN-2 | CLN | REV | runs after DB open and backfill start, off the main thread, at most once (DataStore flag), logs `orphan_cleanup deleted=N bytes=B` | confirmed |
| V-CLN-D | CLN | DEV | Settings › Storage usage drops after the first launch; all garments still show photos | Manu |
| V-EX1-1 | EX1 | CI | `ExportOptionsTest`: defaults Name, Category, Colours, Occasions on; persisted round trip; "Original photo" option disabled when the item has no original | pass |
| V-EX1-2 | EX1 | CI | `PdfLayoutTest`: only chosen fields appear; empty fields omitted; no blank rows | pass |
| V-EX1-3 | EX1 | CI | image card size: long edge 1600 px; field block height grows with chosen fields only (pure layout function) | pass |
| V-EX1-D | EX1 | DEV | Item detail › share opens Export; preview matches; Share opens the system sheet with an image or PDF | Manu |
| V-ZIP-1 | ZIP | CI | `WardrobeZipExporterTest` (JVM, temp dirs): entries `robia-data.json`, `photos/<id>.<ext>`, `originals/<id>.jpg` when present, `README.txt`; JSON equals `DriveSnapshotJson.encode` with `localUri` rewritten to zip paths; missing file → listed in README "missing", export continues | pass |
| V-ZIP-2 | ZIP | CI | cancellation mid-stream stops writing and the partial document is deleted (fake output) | pass |
| V-ZIP-D | ZIP | DEV | Settings › Google Drive › Download all my data: file saved where chosen, opens on a PC with photos and JSON | Manu |
| V-DUP1-1 | DUP1 | CI | `DuplicateScanTest`: equal IMAGE `input_hash` → one group; archived and stale-vector garments excluded; groups = connected components, max 6 (split by strongest pairs) | pass |
| V-DUP1-2 | DUP1 | CI | `DuplicateScanTest`: `calibrationPairs(top = 30)` returns same-category pairs sorted by cosine desc with their cosine; 500 synthetic garments scanned < 2 s on the JVM runner | pass |
| V-DUP1-3 | DUP1 | REV | scan reads a one-shot snapshot (not `observeAll`), runs off the main thread, at most once per 24 hours per process, except that 5 new garments trigger a scan without waiting for the 24 hours (R3-DEC-026, bullet 3); banner hidden while another prompt is visible | confirmed |
| V-DUP1-D | DUP1 | DEV | G-DUP: Manu marks the 30 pairs Same/Different in Developer › Duplicate calibration and sends the export | Manu |
| V-DUP2-1 | DUP2 | CI | `DuplicateScanTest`: cosine ≥ T_DUP and same category and same primary colour → pair; any condition false → no pair; dismissed pair never returned; pair key order-independent | pass |
| V-DUP2-2 | DUP2 | CI | `Migration14To15Test`: exact SQL creates `dismissed_duplicate_pairs(garment_a TEXT, garment_b TEXT, dismissed_at INTEGER, PRIMARY KEY(garment_a, garment_b))` | pass |
| V-DUP2-3 | DUP2 | CI | `SnapshotJsonCompatTest`: optional `dismissedDuplicates` list round-trips; absent → empty; schemaVersion stays 2; import is a union | pass |
| V-DUP2-D | DUP2 | DEV | Dismiss a group, reinstall + restore: the group does not come back | Manu |
| V-BAT1-1 | BAT1 | CI | `BatchEtaTest` (rewritten contract): first estimate after 1 completion using the batch start; P=2 first value ≥ true remaining time × 0.8 on the reference sequence; rise ≤ 20 %/update; displayed text changes at most once per 5 s (fake clock) | pass |
| V-BAT1-2 | BAT1 | CI | `SegmentationModuleGateTest`: `startBatch()` gives a 60 s budget shared by concurrent and sequential calls; after it is spent `ensureAvailable` returns Unavailable at once; fast path still wins when the module appears; retry does not reset | pass |
| V-BAT1-3 | BAT1 | CI | `BatchProcessingCoordinatorTest`: cancel stops the job before cleanup; a cutout finishing after cancel deletes its own output instead of publishing | pass |
| V-BAT1-4 | BAT1 | REV | top bar shows ✕ on the batch route at every stage; one discard dialog (duplicate in RobiaApp removed); discard deletes raw copies, intermediates and cutouts of unsaved drafts only | confirmed |
| V-BAT1-D | BAT1 | DEV | Batch of 10: ETA visible after the first photo and stays visible while scrolling; ✕ → Discard returns to Browse and nothing is saved | Manu |
| V-BAT2-1 | BAT2 | CI | `CutoutPackDownloadTest`: cancel keeps `.part` files and sets state NotDownloaded/Paused; next run resumes with Range (fake store); unique work name differs from the embedding model | pass |
| V-BAT2-D | BAT2 | DEV | Start the pack download, tap Cancel → stops; turn Wi-Fi off/on during a download → it continues by itself | Manu |
| V-VIS-1 | VIS | CI | `GarmentSwatchBackgroundTest`: White, Cream/Beige-light and luminance > 0.82 → stops `#E6E0D9`→`#CFC5BA`; Black/Navy/Burgundy unchanged | pass |
| V-VIS-2 | VIS | CI | `BrowseCardTextTest`: `badgeFor(status, columns, signInBannerShown)` — columns ≥ 4 → icon-only flag; banner shown → AuthBlocked/Waiting → null; Uploading and photo-attention unchanged (update the old AuthBlocked → sign_in assertions to the new rule with equal strength) | pass |
| V-VIS-3 | VIS | REV | `ItemDetailScreen`: `GarmentCloudStatusRow` rendered only when `showSyncDetails`; attention cards unchanged | confirmed |
| V-VIS-D | VIS | DEV | White top visible on its tile; 4 columns show only the cloud icon; with Drive signed out only the banner says Sign in | Manu |
| V-VIS-4 | VIS | CI | `BrowseCardTextTest`: Dirty, Queued and FailedRetryable give no badge when Drive is NotConfigured or Disabled; Connected Dirty still gives Waiting; Disconnected Queued keeps sign-in; Running and photo attention unchanged | pass |
| V-VIS-5 | VIS | DEV | Drive not added: no Waiting badge on any card. Add Drive: Waiting comes back on unsynced cards | Manu |
| V-SPK-1 | SPK | REC | `grep -rn "aiSurface\|rememberAiShimmerBrush" app/src/main` → no hits | empty |
| V-SPK-2 | SPK | CI | `MotionTest`: burst disabled when reduced motion; `SparkleBurst` duration ≤ 900 ms | pass |
| V-SPK-D | SPK | DEV | No shiny buttons; ✦ next to Filters is a plain icon; applying suggestions shows one short sparkle | Manu |
| V-FIT-1 | FIT | CI | `BrowseFilterStateTest`: Fits = fitValue != 0 incl. null; Doesn't fit = 0; both = all; AND with other sections; counts and Reset | pass |
| V-FIT-D | FIT | DEV | Advanced filters › Fit works and shows a chip | Manu |
| V-SEL-1 | SEL | CI | `DragSelectTest`: range from anchor to current index both directions; moving back shrinks the range; items selected before the gesture stay selected | pass |
| V-SEL-2 | SEL | CI | `DragSelectTest`: autoscroll speed 0 outside the 64 dp edge zones, proportional inside | pass |
| V-SEL-D | SEL | DEV | Long-press a photo and drag down: all photos in between get selected; delete asks once | Manu |
| V-PRF-1 | PRF | CI | `ImagePipelineContractTest`: with the default (sampled, disabled) sink no `cacheStats()`/`listFiles` call happens per event (counting fake); with a developer sink the stats still arrive | pass |
| V-PRF-2 | PRF | CI | one request per cell: first request uses measured bounds (fake measure); prefetch key equals display key for 2/3/4 columns | pass |
| V-PRF-3 | PRF | REC | static check script updated to the new telemetry contract and passing | pass |
| V-PRF-4 | PRF | REC | G-ADB2: coordinator runs `tools/perf/browse_fling.sh` (planning PR) on the same phone, same data, before (G-ADB1 baseline) and after: janky % and p90 frame time at 4 columns lower than baseline | lower |
| V-ANA-1 | ANA | CI | `WardrobeStatsTest`: counts per tag group (multi-tag counted per tag), primary colour, fit (Fits incl. null), "Not set" last, ties alphabetical by Collator, archived excluded | pass |
| V-ANA-2 | ANA | CI | tap mapping: bar → `BrowseFilterState` with exactly that filter (pure function) | pass |
| V-ANA-D | ANA | DEV | Analytics opens from the icon; tapping "Dresses" shows only dresses in Browse | Manu |
| V-ANA-3 | ANA | CI | `WardrobeStatsTest`: bar width for progress 0..1 is monotonic and clamped, equals the final width at 1, and jumps to the final width when reduced motion is on | pass |
| V-ANA-4 | ANA | REV | bars animate once on screen entry (<= 400 ms, left to right), not on data change or recomposition | confirmed |
| V-OUT-1 | OUT | CI | `OutfitComposerTest`: composition rules (dress/jumpsuit excludes top+bottom; outerwear only for Autumn/Winter); season/occasion filters; 3 outfits differ in ≥ 1 slot; deterministic for a seed | pass |
| V-OUT-2 | OUT | CI | ranking = mean IMAGE cosine to chosen items (+ prompt TEXT cosine when a prompt is given); garments without vectors excluded | pass |
| V-OUT-D | OUT | DEV | G-OUT: Manu rates ≥ 20 proposals in a week; export shared with the planner | Manu |
| V-CHK-1 | CHK | REC | checklist lists every `-D` row of verification-matrix.md with Pass/Fail/Notes columns and the APK link | complete |


### Round 3b — device feedback (2026-10-24)
| ID | Task | Level | Check | Pass |
|---|---|---|---|---|
| V-SYNC-AUTH-1 | SYNC-AUTH | CI | `DriveHttpErrorsTest`: 403 `dailyLimitExceeded`, `sharingRateLimitExceeded`, `quotaExceeded` and status `RESOURCE_EXHAUSTED` → Transient; 401 and 403 `forbidden`/`insufficientPermissions` → AuthRequired; an unlisted 403 reason or an empty/HTML 403 body → Permanent(403); the old "unlisted → AuthRequired" assertions are replaced with equal strength | pass |
| V-SYNC-AUTH-2 | SYNC-AUTH | CI | fake `DriveSnapshotApi` (+ authorize counter): 401 then 200 → Success with `authorize()` called twice; 401 twice → Blocked after exactly one retry; 401 on the snapshot PUT is retried the same way | pass |
| V-SYNC-AUTH-3 | SYNC-AUTH | CI | `Retry-After: N` (seconds) parsed into Transient (HTTP-date or garbage → null, value capped at 60 s); `withRetry` with an injected sleeper waits max(backoff, Retry-After) | pass |
| V-SYNC-AUTH-4 | SYNC-AUTH | CI | processor test: the "sync ended" line carries HTTP code and reason for AuthBlocked/Retry outcomes and the Blocked cause of an ordinary upload is logged; the `authBlocked` flow is cleared when a cycle starts | pass |
| V-SYNC-AUTH-5 | SYNC-AUTH | REV | `MainActivity` Sign in tap with Drive still Connected shows a toast and requests a sync (no silent no-op); nothing else changes in the sign-in flow | confirmed |
| V-SYNC-AUTH-D | SYNC-AUTH | DEV | Add 100+ garments and let them upload: no red Drive sign-in error appears. If one still appears, Developer › log "sync ended" line names the HTTP code and reason (send it) | Manu |
| V-SYNC-BANNER-1 | SYNC-BANNER | CI | `MergeCombineStateTest`: pure timeout predicate is false while a cycle is running (`restoreProgress != null` or Syncing) and true after 90 s with no cycle | pass |
| V-SYNC-BANNER-2 | SYNC-BANNER | CI | `MergeCombineStateTest`: Combining/Failed + newer finished cycle + no pending proposal → Idle; Combining + newer cycle + proposal pending → Failed; Failed + proposal pending stays Failed; Idle and Done never move (replaces `idleAndDoneAndFailedDoNotMove`, equal strength) | pass |
| V-SYNC-BANNER-3 | SYNC-BANNER | CI | `MergeCombineStateTest`: pure card-visible function — true only with a pending proposal (dialog dismissed) or phase Combining; false for Failed/Done/Idle without a proposal | pass |
| V-SYNC-BANNER-4 | SYNC-BANNER | CI | `MergeProposalScenarioTest`: `pendingMergeSummary` is cleared once the local merge import succeeded when the upload ends AuthBlocked or Retry; a later cycle is not blocked by it | pass |
| V-SYNC-BANNER-5 | SYNC-BANNER | REV | independent review: Replace is reachable only from a pending proposal; no path shows Replace in Failed with an empty summary | confirmed |
| V-SYNC-BANNER-D | SYNC-BANNER | DEV | Sign in with the Google account that has a backup, tap Combine in the popup and wait until the upload finishes: Browse shows no Combine/Replace card afterwards | Manu |
| V-SYNC-ETA-1 | SYNC-ETA | CI | `UploadProgressForwardingTest` + fake repo: the callback carries completed, total and reused counts through `RestoreUploadProgress.reusedPhotos` | pass |
| V-SYNC-ETA-2 | SYNC-ETA | CI | `CloudRestoreProgressTextTest`: pure upload-ETA function — no ETA before the first real upload completes; Reuse completions add no gap; remaining = total − completed; text uses the batch_eta strings | pass |
| V-SYNC-ETA-3 | SYNC-ETA | REV | overlay shows the ETA only in the Uploading phase; the download-phase byte ETA is unchanged | confirmed |
| V-SYNC-ETA-D | SYNC-ETA | DEV | Combine or upload 20+ garments: "Updating cloud backup" shows "About N min/s left" after the first photos and it does not jump wildly | Manu |
| V-SYNC-SPEED-1 | SYNC-SPEED | CI | fake API: a blob whose name+size the index lists causes no POST; a name absent from the full index causes no find-GET | pass |
| V-SYNC-SPEED-2 | SYNC-SPEED | CI | fake API: with concurrency 2–3 every garment ends with exactly one cutout blob and one original blob (no duplicates), progress counts are monotonic, and a `findFileByName` failure never produces a duplicate POST | pass |
| V-SYNC-SPEED-3 | SYNC-SPEED | CI | fake API: Unauthorized/Transient on one worker cancels its siblings; 429/Retry-After drops concurrency to 1; a failed cycle followed by a retry uploads no blob already in the index | pass |
| V-SYNC-SPEED-4 | SYNC-SPEED | REV | independent review: connection is disconnected only on error; no shared mutable state without synchronisation; memory bounded by the concurrency limit | confirmed |
| V-SYNC-SPEED-D | SYNC-SPEED | DEV | Upload 100 garments on the new build and send the total time from the Developer log (before: about 160 s for 114 garments); no sign-in error, no duplicate files in Drive | Manu |
| V-UI-CAL-1 | UI-CAL | CI | `DuplicateScanTest`: the scan reports counts (active, with vector, hash matched, categorised) for a mix of no vector, stale hash and no category | pass |
| V-UI-CAL-2 | UI-CAL | CI | `DuplicateCalibrationScreenTest`: pure state function — pairs null → Loading, empty list → Empty(counts), non-empty → Pairs | pass |
| V-UI-CAL-D | UI-CAL | DEV | Developer › Duplicate calibration shows a progress indicator, then the pairs (or an empty state with counts); then redo the 30 pairs (G-DUP) | Manu |
| V-UI-WHITE-1 | UI-WHITE | CI | `GarmentSwatchBackgroundTest`: White, Cream/Beige-light and luminance > 0.82 → stops `#F3F3F4`→about `#D6D5D6`; each stop's RGB channel spread < 10; Black/Navy/Burgundy unchanged (replaces `nearWhiteSwatchesGetTheWarmNeutralStops`, equal strength) | pass |
| V-UI-WHITE-D | UI-WHITE | DEV | A white T-shirt in Browse shows a subtle grey gradient like in the add flow, not flat beige; same in the garment detail | Manu |
| V-EX2-1 | EX2 | CI | `ExportOptionsTest`: the frame flag defaults to on and round-trips through persistence; pure rule — it may be off only when every Show field is off, and with any field on it resolves to on and disabled | pass |
| V-EX2-2 | EX2 | CI | `PdfLayoutTest`: frame off + no fields → only the photo, no border, no logo, no text; frame on → unchanged output | pass |
| V-EX2-3 | EX2 | CI | image card layout: frame off + no fields → output is the photo alone (Original or Robia cutout); size rules of V-EX1-3 hold with the frame on | pass |
| V-EX2-4 | EX2 | REV | Preview shows a loading state, then scrolls smoothly to the preview; no animated scroll when reduced motion is on | confirmed |
| V-EX2-D | EX2 | DEV | Export: turn every field off, then switch Robia frame off → Preview shows the pure photo; turn one field on → the switch turns on and is disabled; tap Preview → the screen scrolls down to it | Manu |
| V-BAT3-1 | BAT3 | CI | `BatchFollowStateTest`: following starts on; a manual scroll turns it off; the Follow affordance or returning to the bottom turns it on; the target index is the garment being processed | pass |
| V-BAT3-2 | BAT3 | REV | no scroll animation with reduced motion; the Follow affordance shows only while following is off; ETA header and ✕ unaffected | confirmed |
| V-BAT3-D | BAT3 | DEV | Batch-add 30+ garments and watch: the list follows the garment being processed; scroll up by hand and it stops; tap Follow and it resumes | Manu |

## Gates
| Gate | After | Who | Pass |
|---|---|---|---|
| G-ADB1 | K00, K01, ED1, GPU merged | coordinator + Manu | dumps collected for the diagnostic set, compare.py report saved, first divergent stage per photo recorded; editor tap dumps; Browse baseline saved; GPU numbers saved → PLAN-CHANGE |
| G-DUP | DUP1, UI-CAL | Manu | 30 pairs marked on a build that contains UI-CAL (the 2026-10-24 calibration screen was blank, R3-DEC-040: Manu redoes the 30 pairs on that build); T_DUP = lowest cosine with 100 % precision on Same + 0.005 → PLAN-CHANGE |
| G-ADB2 | K03, ED2, PRF | coordinator + Manu | compare.py exit 0 on the diagnostic set (V-K02-4, V-K03-3); Browse janky % and p90 lower than G-ADB1 (V-PRF-4) |
| G-CUT | G-ADB2 | Manu | Experimental NOK < Standard NOK on ≥ 10 garments and no part lost that Standard kept → Manu decides the default |
| G-OUT | OUT (after the round) | Manu | ≥ 20 rated proposals; ≥ 60 % 👍 → user feature planned next round |
