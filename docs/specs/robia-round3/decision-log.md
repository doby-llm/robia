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
