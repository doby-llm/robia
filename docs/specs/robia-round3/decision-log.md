# Decision log — robia-round3

Legend: **D** decided by the planner on evidence; **M** decided by Manu (answers 2026-10-22, `feedback/2026-10-22-manu-answers-r1.md`); **R** rejected; **P** pending (not in this round).

## R3-DEC-001 — Finish robia-ux-rework first (R3-17) · D
PRs #132, #133 (single-column review, R3-17) and #134 are green but unmerged; FIX10 was never dispatched. W0 merges the three; FIX10 is superseded by K01/K02 (device dumps + reference comparison instead of a CI-only port check).

## R3-DEC-002 — Precise cutout off by default now (N3, #135 D2) · D
Manu: the precise cutout destroys almost any garment. The robia-ux-rework stop rule for a failed G-SEG-2 applies: default Standard, precise labelled Experimental (K00). Evidence that the defect is in the Android path, not the algorithm: `artifacts/inv-r3-cutout.md` §1–2 (Python reference keeps the pink shorts complete; MediaPipe 1.1.1 masks equal the raw model).

## R3-DEC-003 — Diagnose on the real phone before fixing (K01 → G-ADB1 → K02) · D
Static reading produced seven divergences but no proof (`inv-r3-f.md` §1). A stage-by-stage dump with the reference's file names (`tools/cutout-diag/`) turns the fix into a closed list. Manu agreed to ADB sessions (answer 9A). R: re-port the pipeline blind (high risk of another silent mismatch).

## R3-DEC-004 — Smart selection semantics (N4) · D
magic_touch selects whole objects (verified in Python on Manu's photo and on its cutout: any tap on the garment selects 99–100 % of it). The tool is kept for leftover pieces; ED1 clips to touched regions, refuses whole-garment removals with a hint, shows the trail, removes the 600 ms drop, and adds a developer tap dump. R: replace magic_touch by MobileSAM point prompts in the editor (3.9 s per embedding on the Pi; revisit if G-ADB1 shows the selector itself is wrong on device).

## R3-DEC-005 — Full edit history (answer 8) · M
Operation log with checkpoints, 64 MB budget, "Cut out again" photos as cache temp files, history ends when the editor closes (ED2). "Redo cutout" renamed "Cut out again" to avoid the clash with Redo.

## R3-DEC-006 — Original photo = engine input frame (answer 1A + size remark) · M/D
Manu accepted originals on phone and Drive but found 2048 px a lot. Decision: keep exactly the frame the engine processed (1024 px precise, ≤ 2048 px Standard), JPEG q90 (~0.15–0.7 MB). It is the smallest frame that still reproduces the cutout and lets Keep restore background; anything larger adds no editing value. Snapshot fields optional, schemaVersion stays 2, Drive prefix `originals/` (old apps never purge it).

## R3-DEC-007 — Palette · P
None of the five schemes convinced Manu (answer 2). Not in this round; new UI uses existing tokens so a later change stays token-only. Facts for the next attempt: `inv-r3-c.md` §C4–C5.

## R3-DEC-008 — Smart styling (answer 3A) · M
All five `aiSurface` call sites lose the animated shine; static ✦; one-shot burst when a smart action completes (SPK).

## R3-DEC-009 — Sync status UI (answers 4A, 5A; R3-15) · M/D
Icon-only per-card pill from 4 columns; detail "Last synced" row developer-only; while the sign-in banner shows, cards carry no Waiting/Sign-in badge (VIS).

## R3-DEC-010 — Duplicates in two steps (R3-07, answer 6A) · D/M
Evidence: different black dresses reach cosine 0.955–0.977, re-shots 0.966–0.989 (`inv-r3-models.md` §Duplicates). DUP1 ships the exact tier + a calibration screen; G-DUP sets T_DUP from Manu's marks; DUP2 adds "very similar" (cosine + same category + same colour) and dismissals in Room + Drive snapshot (6A).

## R3-DEC-011 — Models: no d3, outfit lab on existing vectors (R3-12, R3-16, answer 7A) · D/M
d3-edge 55–57 % vs EmbeddingGemma 2 84–86 % on category; d3-lite equal (86 %) at 15.7 s/question on the Pi and no LiteRT port; d3-edge below chance on FITB. EmbeddingGemma 2 zero-shot FITB 63 % (trained literature 59–69 %); Marqo-FashionSigLIP lower (57 % / 45 %). → OUT (developer lab) + G-OUT; GPU benchmark task (GPU) to measure LiteRT GPU on the Poco F3. Full table: `artifacts/inv-r3-models.md`.

## R3-DEC-012 — Batch (N1, N2, #135 D4a) · D
Premise check: Back already opened a discard dialog and an ETA existed; the gaps were an on-screen close, file cleanup, an ETA that appears late/resets/scrolls away, and up to 120 s module wait per photo (`inv-r3-e.md`). BAT1 fixes all four; one shared 60 s module budget per batch (Manu accepted 8A).

## R3-DEC-013 — #135 D4 other defaults (answer 8A) · M
Keep the 2048 px cap, keep the account binding, pack download Cancel + auto-resume (BAT2). D3 hanger edges confirmed by the red-shorts photo → K03.

## R3-DEC-014 — Fit filter semantics · D
"Fits" includes garments with no fit value, as the editor switch treats them as fitting (`inv-r3-a.md` §A3).

## R3-DEC-015 — Zip export from local data · D
Offline, SAF file picker, streamed, snapshot JSON + photos + originals; no import this round (R3-11).

## R3-DEC-016 — No technical-specification.md this round · D
Technical detail lives in each brief (files, line anchors, constants, tests) because tasks are smaller and more independent than in robia-ux-rework; the briefs cite the investigator reports.

## R3-DEC-017 — Model policy: Haiku coordinator, Sonnet only on escalation · M
Manu (2026-10-22): reduce usage without losing quality. Coordinator, workers and reviewers are Claude Haiku 5.5 high. Claude Sonnet 5.5 high runs only as one-shot, read-only escalations: E1 analysis of device gates G-ADB1/G-ADB2 (writes the defect-list PLAN-CHANGE), E2 final pre-merge data-safety check of OR1, OR2, OR3, CLN, DUP2, E3 diagnosis of a blocked lane. ≈ 9 runs expected; the coordinator asks Manu before a 13th. Mitigation for a Haiku coordinator: anti-trust rules in `PROMPT_COORDINATOR.md` (diff and CI over receipts, REC rows re-run). R: Sonnet as standing coordinator (higher usage for mostly mechanical work).

## R3-DEC-018 — No Waiting badge while Drive is not added (feedback 2026-10-23, item 1) · M
Manu: with no Drive account every unsynced card shows Waiting; this pressures users who do not use Drive. Keep the badge when Drive is connected. Evidence: artifacts/inv-r3-drive-badge.md. Rule: VIS step 5. Signed-out (Disconnected) "Sign in" badges are NOT changed this round; open question for Manu.

## R3-DEC-019 — Stats bars grow on open (feedback 2026-10-23, item 2) · M
Analytics bars animate from the axis on screen entry, one short transition (<= 400 ms). Added to ANA steps 4-5. None when reduced motion is on.

## R3-DEC-020 — Smart draft stays visible · M
Manu (2026-10-23): do not hide the smart tool; there are no users yet. G-ADB1 finding: 15 of 20 taps give the same selection (3.4 % of the photo, top-left corner). Fix belongs to K02 (suspect InteractiveSelector.kt:38 masks[0], hypothesis to verify first). Stays visible until K02 lands.

_Photo labels P1–P7 and F1–F3 refer to local files kept outside the repository._

## R3-DEC-021 — G-ADB1 results: closed defect list for K02 (d) / ED2 step 4 · D (2026-10-23)

**Scope.** I read the app and reference dumps, `compare.txt`, the editor dumps, the perf summaries and the GPU lines. The Python selector outputs for the editor taps were **not produced**, so no editor mask has been compared with `research`. The set has 7 precision photos plus 3 the attachment files files, and no white top. `compare.py` exits 1. Every `report.json` says `engine=precise`, `fallback=none`, so these are real precise outputs.

### 1. Per photo (first divergent stage, IoU)

| Photo | First divergent stage | What differs |
|---|---|---|
| P1 (dress) | s2 0.825 | All 3 prompts rejected, so s2 is a copy of s1. Hanger arms and straps stay in. s4 0.983 and s5 0.987 are fine. |
| P2 | none (min 0.978–0.99) | All 3 prompts rejected, so s2 equals s1. The result is only fine because s1 is already good. |
| P3 (skirt) | s2 0.925 | Same fallback, with the hanger-bar rows kept. s5 is 0.995, but `band_edge_ratio` is 3.89 (halo and clips). |
| P4 | s2 0.939 | Same fallback. The app drops the waistband strip that the reference keeps. s5 is 0.949 (< 0.97) with a speckled edge. |
| P5 (accessory) | s1 0.736 | The app fills the saliency interior (26.7 % of frame vs 19.8 %). s2 is then 0.070: prompts accepted, but the mask is weak and covers 2 %. |
| P6 | none (min 0.994) | Same rejection pattern as above. `band_edge_ratio` 1.27. |
| P7 | none (min 0.978) | Same rejection pattern as above. `band_edge_ratio` 0.99. |
| F1 (pink source) | s2 0.115 | s1 is fine (0.988). s2 is a weak map, bright only on the white piping. The hanger box is found but `kill=false`, so s4 is missing. |
| F2 | s1 0.868 | **Not a photo.** The image is a screenshot of the app's broken pink result in a UI card. Exclude it. |
| F3 (red) | s2 0.038 | Weak map, bright only on the white stripes. **Not an original:** it is an already-cut result on white with no hanger (`box=null`). |

### 2. Closed defect list

**D1. The interactive selector ignores the tap (K02 (d) 1; ED2 does not touch the selector).**
- **Evidence:**
  - In the editor dumps, `mask.png` is byte-identical for every tap on the same input. 15 taps share one hash, 3 share another and 2 share a third, one hash per input. The taps are single, drags and door/clip positions.
  - 3.4 % of the mask is above 127.
  - The same weak map, bright on light details, appears in the app's s2 for the pink shorts, red shorts and accessory.
- **Reference behaviour:** one tap selects the whole garment (the Python MediaPipe 1.1.1 reference, IoU ≈ 1).
- **Suspected cause:** `InteractiveSelector.kt:33-38`, either the keypoint or `RegionOfInterest` not reaching the model, or `masks[0]` being the wrong mask on Android.
- **Status:** hypothesis — verify with a JVM test first. A fake segmenter should assert the normalised keypoint passes through and that the code chooses the right mask when `masks.size` is 1 or 2. Then log `masks.size` and per-point masks once on the device.

**D2. S2 silently falls back to saliency (K02 (d) 2).**
- **Evidence:** 7 of 10 photos have all prompts rejected where the reference accepts them. In 3 other photos the prompts are accepted but the mask is weak.
- **Location:** `PreciseCutoutEngine.kt:242` (acceptance) and `:249` (`coarse.copyOf()` with no marker).
- **Fix:** a consequence of D1 (hypothesis). Add `s2_fallback` and `s2_accepted` to `report.json`, and fix D1 first.

**D3. The garment-loss guard (b) would not catch D1.**
- **Evidence:** the guard divides by `count(S2 hard)`, and S2 is already the weak map. On the pink shorts `opaque_fraction_of_s2` is 0.83 while S2 covers only about 12 % of the reference garment (IoU 0.115).
- **Fix:** also measure against S1 hard (`s1_saliency` is 0.988 on the pink shorts). This is a change to the K02 (b) design.

**D4. S1 interior on the accessory (K02 (d) 3).**
- **Suspected cause:** `PreciseCutoutEngine.kt:212-224` with `MaskOps.kt:248` (resize without antialias, D3 in inv-r3-f).
- **Status:** hypothesis — verify with a JVM test first (compare against PIL bilinear). The other 9 photos have s1 ≥ 0.986.

**D5. `kill=false` on the pink shorts despite a found box.**
- **Suspected cause:** `hangerKill` (`:290-297`) returns null through the 15 % cap, probably because `garmentArea` is tiny after D1.
- **Status:** hypothesis. No separate fix; re-check after D1.

**D6. Band edge and fringe (K03, not K02).**
- Edge ratio 3.89 on 161436. Speckled fringe and a missing top strip on 170643 (the strip is a D1/D2 consequence).

**D7. SAM scaling (K02 step 3).** The dumps neither show nor refute it. Only photos with a hanger run SAM, and all of those have a work long edge of 1024. Keep step 3 as written.

**ED2 step 4.** No editor-wiring defect is supported. `points.json` carries sane coordinates; one drag point has y=985.5 against height 960, and its pixel was clamped to 959. The empty `clipped.png` in 17 of 20 dumps follows from D1. Nothing to fix in `PhotoEditorScreen.kt` yet.

**Set fixes before G-ADB2.** Replace `F2` with the original pink photo. Add the original red-shorts photo, because V-K03-3 cannot be measured on `F3`. Add the white top. Measure the ratio on the pink source as well; its hanger band is real.

### 3. Perf (staging 7ad6884, 4 columns, 3 runs)

| Metric | Run 1 | Run 2 | Run 3 | Median |
|---|---|---|---|---|
| Janky % | 2.12 | 1.97 | 2.15 | **2.12** |
| p90 frame (ms) | 19 | 16 | 20 | **19** |

p50 is 11 ms in all runs. Legacy janky % is 9.93, 6.68 and 11.18 (median 9.93). Frames per run are 1163–1178. The run-to-run spread is about 0.2 pp and 4 ms, so a V-PRF-4 delta inside that is noise. G-ADB2 has not run, so there is no delta and V-PRF-4 is not evaluated.

### 4. GPU (G-ADB1, Poco F3, API 33)

| | CPU | GPU |
|---|---|---|
| Load | 511 ms | n/a |
| Median | 1501 ms | n/a |
| p90 | 1594 ms (n=5) | n/a |
| PSS | 760 MB | n/a |
| Parity vs CPU | 1.0000 (CPU against itself, not informative) | n/a |

The GPU run failed with `ComputeEmbedding failed: TensorBuffer must have all static dimensions`. **Verdict:** the GPU path is unusable with this model (dynamic dimensions); stay on CPU. Any retry needs a static-shape export, which is a decision for Manu.

### 5. G-ADB2

Not run, so V-K02-4 and V-K03-3 have no numbers. Current baseline for V-K02-4: `compare.py` exits 1, with s2 < 0.95 on 6 of 10 photos and s5 < 0.97 on 5. After the fixes above, expect K02 to own D1–D4. For V-K03-3 the only photo currently measurable is the pink source, which has the real hanger band (ratio 1.36, from the reference's band row, so unreliable); the only app ratio over 1.5 is 161436 at 3.89.

## R3-DEC-022 — K02 garment-loss denominator and K02 scope (PLAN-CHANGE 2026-10-23) · D
K02 step 2 (V-K02-2) divides by the S2 area. E1 (R3-DEC-021, D3) showed that S2 can be a weak map (pink shorts: S2 covers about 12 % of the reference garment). Decision: the denominator is max(S1 hard, S2 hard), as implemented in PR #147. Trade-off: an oversized S1 can raise false rejects; a rejected result falls back to ML Kit, which is safe, not data loss. Required: one test for the oversized-S1 case. Scope: media/cutout/CutoutDebugDump.kt, media/PhotoBackgroundRemover.kt and the two test files added to tasks.md are part of K02, because brief steps 2 and 5 require the garment_loss chain reason and the report.json keys.

## R3-DEC-023 — SparkleBurst placement (SPK step 3) · D
The brief says one-shot SparkleBurst on completion. Four of the five controls leave the screen or cover themselves when tapped (See similar, Review colours, Review garments, Filters sparkle when it opens the model prompt), and Edit photo's Done covers its button. Decision: the burst plays only on Edit photo when processing ends and the button is visible again. The other four show no burst; their ✦ stays static. Rejected: a burst on tap at all five, which does not mark completion.

## R3-DEC-024 — Drive disconnected: one top banner, no per-photo Sign in or Waiting (Manu, 2026-10-23) · M
When Drive was configured and is now disconnected, Browse shows one sign-in banner at the top. While it shows, cards do not repeat "Sign in" or "Waiting" (BrowseCardText.browseCardBadgeFor, rule hiddenByBanner). This is the behaviour already merged in VIS (#146). Manu confirmed it on 2026-10-23 after the A/B question: a single banner, as it is now. Photo-restore attention is kept. No code change in this PR.

## R3-DEC-025 — K02 review round 2: test seam, D4 proof rule, guard margin (PLAN-CHANGE 2026-10-23) · D
- InteractiveSelectionSeamTest.kt is added to K02 Files. The seam moves the post-segmenter logic into selectWholeObject; production behaviour is unchanged (reviewer confirmed file:line). D1 is recorded as "not reproduced on JVM" only: the test pins how the code handles the segmenter output, not what MediaPipe returns. D1 stays open until G-ADB2.
- D4 proof rule: K02 must show that the OLD resizeBilinear does not produce the PIL values on the 12 to 4 checker input (characterisation test). If it does produce them, D4 is not reproduced: the saliency resize reverts to the old function and the D4 claim is removed.
- Guard margin under R3-DEC-022 (reviewer arithmetic): with the 15 % kill cap, a good result is rejected once S1 > 1.21 x S2; without the kill, once S1 > 1.43 x S2. Accepted trade-off: the ML Kit fallback is the safe outcome. This records the number; it is not a threshold change.
- V-K02-2 wording now matches R3-DEC-022 and the code: denominator max(S1 hard, S2 hard); ORIGINAL_FALLBACK with failure message garment_loss.

## R3-DEC-026 — DUP1 review round 1: dismissal rule, banner gating, scan cadence, calibration reachability, Delete colour (PLAN-CHANGE 2026-10-23) · D
- Dismissed pairs: a dismissed pair never sits in one group, even when a later garment links its two members through a third one (REQ-DU-02, session scope). groupsFromEdges must refuse that merge. The dismissal test in DuplicateScanTest.kt (about lines 87-88) asserts the inverted behaviour and is corrected.
- Banner gating: the duplicates banner is hidden while the merge proposal card, the review-complete overlay or any sync attention card is shown. Sync attention cards count as prompts.
- Scan cadence: a scan runs at most once per 24 hours per process, or after 5 new garments. The 5-garment trigger is not limited by the daily rule (REQ-DU-04 reads "or after N=5" as a separate trigger). Persisting the last-scan time needs storage outside DUP1's Files, so it is not added: a cold start rescans once. This is a deliberate deviation from a daily cap that survives restarts; revisit with DUP2 if Manu wants it persisted.
- Calibration screen: reachable only through the existing developer-mode unlock, the same gate as the other developer tools. Accepted; not restricted to debug builds.
- The Delete button on the duplicate review screen uses the error colour (REQ-DU-03). ReviewScaffold.kt gets one parameter with its current default; no other screen changes.

## R3-DEC-027 — ED2: history is session-local and temporary; 64 MiB budget kept; PhotoHistory replaces NewPhoto (PLAN-CHANGE 2026-10-23) · D
- Manu on 2026-10-23, verbatim: "La idea de tener un historial es únicamente cuando se está añadiendo la foto como tal. Una vez se guarda el garment, se puede editar la foto pero ya ahí no queda ningún historial. Con esto hacemos que el historial sea solo local y temporal." Reading applied: history exists only while a photo is being added (before the garment is saved). It is local to the editor session and temporary: never kept across app restarts, discarded on save, and editing the photo of an already saved garment starts with no history. This narrows R3-DEC-005 and REQ-ED-03 ("history ends when the editor closes") to the add flow; it adds no persistence.
- Budget (option B, chosen by Manu on 2026-10-23): the 64 MiB budget stays. At the next edit after a cut-out on a 2048x2048 photo, the budget can drop the photo kept before the cut-out, so undo does not reach across that cut-out. Within the budget every step can be undone and redone.
- PhotoHistory: the brief (step 1) named `EditOp.NewPhoto(tempFile)`. PR #149 replaced it with `PhotoHistory`, which holds the "Cut out again" photos (cut-out lives there). Accepted; no PLAN-CHANGE had recorded it.
- Supersedes: the lifetime question of R3-DEC-005 and REQ-ED-03 ("history ends when the editor closes"), narrowed as above, and the wording of V-ED2-2 and V-ED2-D. Also superseded: the budget question (PR #149 finding a: V-ED2-D cannot pass at 2048x2048), answered by option B. Rows in tasks.md (ED2 scope) updated; Files and Acceptance unchanged.

## R3-DEC-029 — DUP1 review round 5: startup gate, recreation-safe state, order-insensitive calibration signature (PLAN-CHANGE 2026-10-23) · D
- Startup gate (REQ-DU-04): the duplicate scan waits for the embedding backfill to settle. "Settled" means the backfill has finished one pass, including a pass with nothing to do. A garment that keeps failing to embed is removed from pending after two attempts, so it does not hold `settled` false. If the model never becomes ready, `settled` stays false and the scan waits (accepted). EmbeddingBackfill.kt and EmbeddingBackfillTest.kt are added to the DUP1 Files list (edited, not new).
- Duplicate groups and session dismissals survive activity recreation (rotation, theme change) within one process. They are held in a process-scoped holder beside DuplicateScanHistory. Nothing is written to disk; persisted dismissals stay out of scope (DUP2).
- Calibration screen (developer only): the signature is order-insensitive (garments sorted by id), because the garment list is ordered by favourite and last update (data/local/Dao.kt) and every wardrobe write reordered it. Marks reset only when a garment's id, photo or category changes. Marks are keyed by the ordered garment-id pair. Rotation still clears calibration marks (developer-only screen; accepted).
- Deferred, not in this round: a failed scan is still recorded and blocks the retry for 24 h; exact-tier edges all have strength 1.0, so the strongest-pairs split falls back to id order; calibration marks are lost on rotation.

## R3-DEC-030 — DUP1 review round 6: pruning on any route; calibration archive state (PLAN-CHANGE 2026-10-23) · D
- Files: `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateCalibrationScreenTest.kt` (new) is added to the DUP1 Files list. Review round 6 found it in PR #152 (76 lines) without a Files entry.
- Pruning on any route: duplicate groups drop any garment that leaves the active wardrobe, whether by archive, delete from Browse (RobiaApp.kt, deleteItemsAndReturnToBrowse archives garments but never calls DuplicateSession.removeGarments) or delete in review. Otherwise a gone garment stays in a group until the next scan, which waits 24 h when the count only drops, and the banner count depends on it. The rule must be pure and testable in JVM; the implementation is free. The 24-hour cadence and the 5-garment trigger (R3-DEC-026) are unchanged.
- Calibration signature amends R3-DEC-029 bullet 3 (which is not edited): the signature also includes archive state (isArchived, DuplicateCalibrationScreen.kt CalibrationSignature), so archiving or un-archiving a garment resets the marks. Accepted on a developer-only screen. R3-DEC-026 does not mention archive state, so it gives no rule to keep or reset marks on archive.

## R3-DEC-028 — K03 V-K03-1 becomes a regression row (PLAN-CHANGE 2026-10-23) · D
- Numbering: 028 is a gap left by an unmerged earlier draft; this entry uses the number for the K03 decision.
- Manu chose option A on 2026-10-23. V-K03-1 asked that the band transition width of `refineAlpha` on a synthetic hanger-band photo be ≤ 1.5× the side width. The K03 worker (round 2, head 00e114e) found no synthetic case that fails on the pre-K03 behaviour. Results of a Python float32 replica, not the JVM: band-to-body ratio 1.0 with both the old and the new guide; across 475 random scenes no case where the old guide exceeded 1.5× and the new one stayed within it.
- V-K03-1 is rewritten as a CI regression row: the `MaskOpsTest` synthetic scene passes, and the side-edge width helper matches the compare.py definition (thresholds 0.05 and 0.95, per-row halving, max > 0.95 gate). It does not show that the band is fixed.
- V-K03-3 (compare.py on the red-shorts dump, `band_edge_ratio` ≤ 1.5, all stage IoUs pass, coordinator at G-ADB2) is the only evidence that the band is fixed. Threshold unchanged. V-K03-D unchanged.
- Rationale: a row that passes on the old code must not read as proof of the fix.

## R3-DEC-031 — ED2: undo on saved garments is editor-session only (PLAN-CHANGE 2026-10-23) · D
- Manu chose option A on 2026-10-23. For a saved garment, undo works only inside the open editor session (the editor's own edits, RefineSession ops). "Cut out again" photos are not kept in history for saved garments, and nothing survives closing the editor. For a new garment (add flow) the full history of R3-DEC-027 stays; R3-DEC-027 is not edited.
- Implementation fact: PR #149 (head ce8baeb) passes `keepsHistory = !isEditing` at `ui/AddEditClothingScreen.kt:704`, a one-line call site. The file is added to the ED2 Files list; Acceptance IDs unchanged.
- V-ED2 rows stay as they are; V-ED2-D gains one sentence stating the saved-garment limit.
- Rationale: it keeps the temporary photo files out of the saved-garment flow, in line with R3-DEC-027.

## R3-DEC-032 — DUP1 review closure rule (PLAN-CHANGE 2026-10-23) · D
- Context: DUP1 (PR #152) has had six independent-review rounds (all NOT SAFE) and a seventh worker round. The task is not tagged "Independent review: yes" in tasks.md; the coordinator added the reviews. The rounds kept finding new edge cases in a feature whose real acceptance is device-side (V-DUP1-D, G-DUP) and whose threshold work is DUP2's.
- BLOCKING class: a review of DUP1 returns SAFE when it has no finding in this class: data loss or wrong deletion; crash or unbounded main-thread work; a violated V-DUP1-1..3 row or REQ-DU-01/03/04; a file outside Files; a weakened existing test.
- Everything else is a DEFERRED follow-up: listed in the PR receipt and in the follow-up list below, never a reason for another worker round.
- Follow-up list (already deferred): a failed scan still records history and blocks the retry for 24 h; exact-tier edge strength is 1.0, so the strongest-pairs split falls back to id order; calibration marks are lost on rotation (developer-only screen); optional hardening items from rounds 1-6.
- Next review: the round-7 review of the code is the last one before merge unless it finds a BLOCKING item. A BLOCKING item gets one closed-list worker round; the review after that checks only that list.
- Merge also needs CI green on the head and a merge of origin/main. V-DUP1-D stays Manu's.
- Rationale: review rounds must end on a stated class, not on the reviewer running out of edge cases.

## R3-DEC-033 — OR1 review round 1: sync keeps originals; batch subject deleted after save (PLAN-CHANGE 2026-10-23) · D
- Context: independent review of OR1 (PR #164, head 8dcd09a) returned NOT SAFE with two BLOCKING items. B1: `LocalWardrobeSyncSnapshotRepository.importRecords` builds entities with `GarmentSyncRecord.toEntity`, which never sets `original_uri` / `original_recipe`, then calls the `@Upsert` in `Dao.kt`. The merge path (`WardrobeSyncOutboxProcessor`) and `replaceLocalWithRemote` ("Use backup", which calls `deleteAllItems` first) therefore write NULL over saved originals and the files become orphans. B2: batch add deletes `subject-*.png` in the `finally` of `cutoutStage` (`BatchAddClothingScreen.kt`, about lines 574-577) before the save; brief step 4 requires deletion after a successful save.
- Coordinator decision (Manu may overturn). Files: `data/local/LocalWardrobeSyncSnapshotRepository.kt` is added to the OR1 Files list, for B1 only. Acceptance IDs unchanged.
- B1 rule: a sync import never erases a local garment's `original_uri` / `original_recipe`. For ids that exist locally and are written by merge import, the local values are kept (the sync record carries none until OR2 defines the Drive format). For `replaceLocalWithRemote`, the local original fields for the ids are read before `deleteAllItems` and written back on the records whose id matches; originals of ids absent from the backup stay orphans for CLN. Implementation is free, but the rule must be JVM-testable. Drive upload/restore of originals stays out of scope (OR2).
- B2 rule: for batch add, `subject-*.png` is deleted only after the garment save succeeds (registered for release on save like the raw copy; deleted on discard or cancel).
- Deferred, not OR1: recipe stale after editor/Quick Edit saves (OR3); orphan originals from cancelled stages, the `cutoutFallbackChain` timeout race, partial JPEG writes and garment deletion (CLN/OR2); ML Kit recipe test strength.
- Rationale: OR1 must not lose data on a path it introduces, and the raw-copy rule (delete only after a successful save) applies equally to the batch subject file.

## R3-DEC-034 — ANA: BrowseGrid.kt entry-point wiring (PLAN-CHANGE 2026-10-23) · D
- Context: the ANA worker (PR #163, head f31bbb7, CI green) built everything except the entry point. The Analytics icon lives in `FilterBar` (`ui/BrowseFilterBar.kt`), and its only caller is `BrowseWardrobeScreen` in `ui/BrowseGrid.kt`, which is not in the ANA Files list. Without a change there the icon cannot open the screen.
- Coordinator decision (Manu may overturn). `ui/BrowseGrid.kt` is added to the ANA Files list for entry-point wiring only: one optional-callback parameter on `BrowseWardrobeScreen`, passed to `FilterBar` (about four lines), plus one line in `ui/RobiaApp.kt` to open the route. No other change in that file; SEL drag select, PRF changes, the duplicates banner and pinch must not be reverted. Acceptance IDs unchanged.
- Worker choices accepted as they are, to be checked at V-ANA-D: the colour "Not set" bar is not tappable (Browse has no filter for no colour); zero-count bars are omitted; colours are counted by stored palette id; tapping a bar keeps an active text search.
- Rationale: the entry point is required by REQ-AN-01 (icon next to Filters opens the screen), and the smallest change that provides it is a callback parameter, not a rewrite of Browse.

## R3-DEC-035 — OR1 review round 2: Files path and edited test (PLAN-CHANGE 2026-10-24) · D
- Context: independent review of OR1 round 2 (PR #164, head bab962b): B1 and B2 hold. The only BLOCKING item is a Files gap, in two parts. (1) R3-DEC-033 wrote `data/local/LocalWardrobeSyncSnapshotRepository.kt`; the file is `sync/LocalWardrobeSyncSnapshotRepository.kt` (same file, wrong directory). (2) The existing test `app/src/test/java/com/gusanitolabs/robia/sync/ReplaceLocalWithRemoteTest.kt` was modified for B1 (new target name, equal strength) and is not in Files.
- Coordinator decision (Manu may overturn). In the OR1 Files list the sync repository path is corrected to `sync/LocalWardrobeSyncSnapshotRepository.kt`, and `app/src/test/java/com/gusanitolabs/robia/sync/ReplaceLocalWithRemoteTest.kt` is added (edited, equal strength). The new tests `OriginalPhotoImportTest` and `BatchSubjectFileTest` are new test files and are already allowed. Acceptance IDs unchanged.
- Deferred, not OR1: add `subjectUri` and `originalUri` to the `discardOutput` delete in `RobiaApp.kt` (cancel-race orphan leak, CLN); in-memory release registry; merge keeping the local original when the remote photo changed (OR3 stale recipe); partial JPEG write.
- Rationale: the rule of R3-DEC-032 applies: a file outside Files is BLOCKING, so the list must name every file the worker touched; the fix is to the list, not to the code.

## R3-DEC-036 — OR3: record the stored-canvas offset in the recipe (PLAN-CHANGE 2026-10-24) · D
- Context: the OR3 worker stopped before coding. Keep-from-original and "Cut out again" need the position of the stored cutout inside the original frame. `CutoutRecipe` (`core/model/ClothingModels.kt`) says "full frame" for ML Kit, but every cutout is then trimmed by `ClothingImageStore.cropTransparentPixels` (`ClothingImageStore.kt`, about line 228: alpha > 24 bounds plus 4% padding, min 8 px) at `AddEditClothingScreen.kt:403` and `:539`, `BatchAddClothingScreen.kt:573` and `PhotoEditorScreen.kt:1196`. That offset is not recorded, so the mapping would be wrong for almost every Standard garment. The Precise engine is exact (its second crop is a no-op).
- Coordinator decision (Manu may overturn). Files added to OR3: `media/ClothingImageStore.kt` (`cropTransparentPixels` returns/records the crop bounds; the cropped output is unchanged), `ui/BatchAddClothingScreen.kt` (batch call site only, to store the offset in the recipe) and `core/model/ClothingModels.kt` (`CutoutRecipe` gains an optional stored-canvas offset field, default null = old behaviour; JSON stays backward compatible, a missing or unknown field reads as null). Acceptance IDs unchanged.
- Rules: the recipe records the offset at import (single and batch) and is rewritten whenever editor Done changes the stored canvas (this is the stale-recipe handling deferred from OR1). A garment whose recipe lacks the offset (saved before this change, or ML Kit without it) falls back to today's behaviour: no Keep-from-original and no Cut out again are offered. Garments without a usable original are unchanged. No Room schema change (the recipe is already a JSON column), no sync change.
- Rationale: the mapping needs the real crop offset, and the smallest change that provides it is to record it where the crop happens and store it in the existing recipe, with a safe fallback for everything saved before.

## R3-DEC-037 — CLN review round 1: register in-use drafts; delayed run (PLAN-CHANGE 2026-10-24) · D
- Context: independent review of CLN (PR #168, head 7ae8ef5) returned NOT SAFE with one BLOCKING item. An unsaved single add/edit draft whose photo is older than 24 h would be deleted while in use: `AddEditClothingScreen.kt:149-150` keeps `photoUri` and `originalPhotoUri` in `rememberSaveable`, so a draft survives process death and an app update; the one-time cleanup runs at the first ON_START of the new build; and `ActivePhotoRefs` has no register call sites.
- Coordinator decision (Manu may overturn). `ui/AddEditClothingScreen.kt` is added to the CLN Files list, for registration only: register the draft's photo URIs in `ActivePhotoRefs` when they are set or restored, and unregister on save or leave; no behaviour change otherwise. Acceptance IDs unchanged.
- Rules: the one-time run waits 60 s after the first ON_START (on IO) so a restored draft has registered before the planner runs; the run-once flag is still set only after a completed run. A planner test covers that a registered file older than 24 h is kept.
- Deferred, not CLN: no JVM test of `deleteOrphans` / the run-once path; `File.delete()` failures are not retried; V-CLN-D label wording (use Android Settings > Apps > Robia > Storage).
- Rationale: a file in use by a restorable draft must never be deleted, and the smallest change that guarantees it is to register the draft's URIs where they are set and to give restoration time to run before the planner does.

## R3-DEC-038 — CI: Kotlin daemon heap raised to 3 GB (coordinator decision 2026-10-24) · D

- Symptom: CI (`gradle --no-daemon testDebugUnitTest assembleDebug assembleStaging`) intermittently fails in `:app:compileDebugKotlin` with `java.lang.OutOfMemoryError` / "Not enough memory to run compilation. Try to increase it via gradle.properties: kotlin.daemon.jvmargs=-Xmx<size>". Seen on main and on PRs CLN, OR2, OR3; it is intermittent and often passes on rerun.
- Change: `gradle.properties` only, no code: `kotlin.daemon.jvmargs` raised from `-Xmx1g` to `-Xmx3g`. `org.gradle.jvmargs` is unchanged.

## R3-DEC-039 — OR3 review round 1: sync keeps a matching recipe; missing original never crashes (PLAN-CHANGE 2026-10-24) · D
- Context: independent review of OR3 (PR #171) returned NOT SAFE with one BLOCKING item. In `sync/LocalWardrobeSyncSnapshotRepository.kt` (about lines 552-556, `photoUri` from `importedPhotoUri` about 666-667) a sync import takes the restored cutout photo but keeps the LOCAL `originalRecipe` (including `storedCanvasOffset`) when no restorable original comes with it. If the remote cutout canvas changed, the local recipe maps the original onto the wrong pixels, and Keep-from-original then paints wrong pixels onto the synced cutout. Second item (crash class): `PhotoEditorScreen.kt` `loadOriginalLayer` (about line 1236) throws for a missing original file (`openInputStream`) and the `LaunchedEffect` (about 302-306) has no guard, so a missing original would crash the editor.
- Coordinator decision (Manu may overturn). `sync/LocalWardrobeSyncSnapshotRepository.kt` is added to the OR3 Files list, for this fix only. This supersedes the "no sync change" line of R3-DEC-036 for this one case. Acceptance IDs unchanged.
- Rules: when a sync import replaces a garment's cutout photo and no restorable original arrives with it, the local recipe's `storedCanvasOffset` (and the recipe) is cleared, so the garment falls back to today's behaviour (no Keep-from-original, no Cut out again). When the imported photo is unchanged, nothing changes. A JVM test is required. `loadOriginalLayer` returns null on any I/O failure, and the editor then behaves as without an original.
- Deferred, not OR3: the V-OR3-3 test covers `alphaCropRect`, not `cropTransparentPixels`; Quick Edit save clears the recipe of a pre-OR3 garment; per-`RefineSession` canvas arrays outside `EditBudget`; `colour()` allocation per render; race of a superseded import.
- Rationale: a recipe is only valid for the cutout it was recorded against, so when the photo changes and no original comes with it the recipe must go; a missing file must degrade to the old behaviour, never crash.
