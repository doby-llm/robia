# Implementation plan — robia-round3

Execution model: one coordinator **Claude Haiku 5.5 high** (`PROMPT_COORDINATOR.md`), workers **Claude Haiku 5.5 high** (`PROMPT_WORKER.md`), reviewers Haiku 5.5 high read-only (`PROMPT_REVIEW.md`); **Claude Sonnet 5.5 high** only as one-shot read-only escalation runs for E1 device-gate analysis, E2 final pre-merge check of OR1/OR2/OR3/CLN/DUP2, E3 blocked lanes (`PROMPT_ESCALATION.md`, R3-DEC-017). One task per run, max 3 workers in parallel, GitHub Actions is the only build authority, every task is one PR. Hermes skill `haiku-worker-coordination` applies.

## W0 (coordinator, before any task)
Merge PRs #132, #133, #134 when green (rebase if needed); close the FIX10 lane on #100 as superseded by K01/K02; record in the tracking issue.

## Waves (earliest start; a task starts as soon as its predecessors are merged — waves are not barriers)
- **W1:** K00, K01, ED1, GPU, BAT1
- **W2:** BAT2, VIS
- **W3:** K02, ED2, DUP1, SPK
- **W4:** K03, FIT, SEL
- **W5:** OR1
- **W6:** OR2, OR3, CLN, PRF
- **W7:** ANA
- **W8:** OUT
- **W9:** EX1
- **W10:** ZIP
- **W12:** DUP2
- **W13:** CHK

## Dependency edges
```
K00   <- W0
K01   <- W0
K02   <- K01, G-ADB1
K03   <- K02
ED1   <- W0
ED2   <- ED1, G-ADB1
GPU   <- W0
OR1   <- K03, ED2, BAT1, SPK
OR2   <- OR1
OR3   <- OR1
CLN   <- OR1
EX1   <- OR1, SPK, OUT
ZIP   <- OR2, EX1
DUP1  <- VIS, GPU
DUP2  <- DUP1, G-DUP, OR2, ZIP
BAT1  <- W0
BAT2  <- K00
VIS   <- BAT1
SPK   <- VIS, W0
FIT   <- SPK
SEL   <- DUP1, SPK
PRF   <- SEL, G-ADB1
ANA   <- FIT, PRF
OUT   <- ANA, DUP1
CHK   <- ALL
```

## File leases (every file touched by 2+ tasks; the order is enforced by the edges above)
| File | Writers in order |
|---|---|
| `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` | VIS → SPK → SEL → PRF |
| `app/src/test/java/com/gusanitolabs/robia/media/cutout/HangerRemovalTest.kt` | K02 → K03 |
| `app/src/test/java/com/gusanitolabs/robia/media/cutout/MaskOpsTest.kt` | K02 → K03 |
| `app/src/test/java/com/gusanitolabs/robia/media/cutout/RefineSessionTest.kt` | ED2 → OR3 |
| `app/src/test/java/com/gusanitolabs/robia/suggestions/DuplicateScanTest.kt` | DUP1 → DUP2 |
| `app/src/test/java/com/gusanitolabs/robia/sync/SnapshotJsonCompatTest.kt` | OR2 → DUP2 |
| `core/model/ClothingModels.kt` | K00 → OR1 → EX1 |
| `core/model/SyncModels.kt` | OR2 → DUP2 |
| `data/SettingsRepository.kt` | K00 → EX1 |
| `data/local/Dao.kt` | OR1 → DUP2 |
| `data/local/RobiaDatabase.kt` | OR1 → DUP2 |
| `media/ClothingImageStore.kt` | BAT1 → OR1 → CLN |
| `media/EditorPhotoState.kt` | OR1 → OR3 |
| `media/MlKitSubjectSegmentationBackgroundRemovalEngine.kt` | BAT1 → OR1 |
| `media/PhotoBackgroundRemover.kt` | K01 → OR1 |
| `media/cutout/CutoutEngineRegistry.kt` | K01 → K02 |
| `media/cutout/EditHistory.kt` | ED2 → OR3 |
| `media/cutout/HangerRemoval.kt` | K02 → K03 |
| `media/cutout/MaskOps.kt` | K02 → K03 |
| `media/cutout/PreciseCutoutEngine.kt` | K01 → K02 → K03 → OR1 |
| `media/cutout/RefineSession.kt` | ED2 → OR3 |
| `suggestions/DuplicateScan.kt` | DUP1 → DUP2 |
| `sync/GoogleDriveWardrobeRepository.kt` | OR2 → DUP2 |
| `sync/LocalWardrobeSyncSnapshotRepository.kt` | OR2 → DUP2 |
| `ui/AddEditClothingScreen.kt` | SPK → OR1 → OR3 |
| `ui/BatchAddClothingScreen.kt` | BAT1 → OR1 |
| `ui/BrowseFilterBar.kt` | SPK → FIT → ANA |
| `ui/BrowseGrid.kt` | VIS → DUP1 → SEL → PRF |
| `ui/DriveSettingsScreen.kt` | OR2 → ZIP |
| `ui/DuplicateReviewScreen.kt` | DUP1 → DUP2 |
| `ui/ItemDetailScreen.kt` | VIS → SPK → EX1 |
| `ui/PhotoEditorScreen.kt` | ED1 → ED2 → OR3 |
| `ui/PreciseCutoutSettings.kt` | K00 → BAT2 |
| `ui/RobiaApp.kt` | BAT1 → VIS → DUP1 → SEL → PRF → ANA → OUT → EX1 → ZIP → DUP2 |
| `ui/SettingsScreen.kt` | GPU → DUP1 → OUT |

## Independent review
Required for: K02, K03, ED2, OR1, OR2, OR3, CLN, DUP2, BAT1. One review → one consolidated remediation → coordinator final check; stop after round 2 and ask Manu. For OR1, OR2, OR3, CLN, DUP2 a Sonnet E2 check follows the Haiku review before merge; a lane blocked after 2 rounds gets one Sonnet E3 diagnosis before asking Manu.

## Device gates (Manu agreed to ADB, answer 9A)
Phone: Poco F3 (M2012K11AG). The coordinator runs everything from the Pi; Manu only plugs the phone in, accepts the ADB prompt and keeps it unlocked. Message to Manu (Spanish) before each session: what will run, ~20–30 min, his data stays on the phone.

### G-ADB1 (after K00, K01, ED1, GPU are merged)
1. Download from the latest main push: `robia-debug-apk` and `robia-staging-apk`. Check both carry the same signing certificate as the installed app (`apksigner verify --print-certs`); if not, STOP and ask Manu (installing would wipe his data).
2. **Browse baseline (staging):** install staging from main (`adb install -r`), open Browse, Photos-only at 4 columns, wait 1 min, run `tools/perf/browse_fling.sh <ws>/g-adb1/perf 20` three times; keep the median.
3. **GPU benchmark (staging):** Developer › Model benchmark (CPU) and (GPU); save the `RobiaBenchmark` lines.
4. **Cutout dumps (debug build):** install debug (`adb install -r`), copy the diagnostic set into `/sdcard/Android/data/com.gusanitolabs.robia/files/cutout-debug/in/` (Manu's pink + red shorts from the planner workspace + 8 photos from the precision set: 2 dresses, 2 skirts, 2 shorts, 1 white top, 1 accessory), make sure the cutout pack is downloaded, launch `adb shell am start -n com.gusanitolabs.robia/.MainActivity --ez com.gusanitolabs.robia.CUTOUT_DEBUG true`, wait for `cutout_debug_all_done` in logcat, `adb pull` the `out/` dir.
5. Run `tools/cutout-diag/reference_stages.py` on the same photos (Pi) and `compare.py <app> <ref>`; read the stage PNGs where IoU < 0.95.
6. **Editor taps:** with developer mode on, open the pink shorts in the editor (Remove › smart), tap the hanger clips, a door strip and the garment; pull `cutout-debug/editor/`; compare each mask with `research` Python selector output for the same input/point.
7. Reinstall the staging APK (leave the phone on staging). Hand the collected outputs to one Sonnet E1 run (`PROMPT_ESCALATION.md`), which writes the PLAN-CHANGE: per photo the first divergent stage and its IoU, the suspected defect (file:line), editor findings, perf baseline numbers, GPU numbers. This PLAN-CHANGE is the closed defect list for K02 (d) and ED2 step 4. Personal images stay in the planner workspace.

### G-ADB2 (after K03, ED2, PRF are merged)
Same procedure: perf on staging (3 runs, median vs G-ADB1), cutout dumps on debug + compare.py (must exit 0; `band_edge_ratio` ≤ 1.5 on the red shorts), editor taps. Sonnet E1 writes the PLAN-CHANGE; failures go back to the owning task as one consolidated remediation.

### G-CUT, G-DUP, G-OUT
Messages to Manu in Spanish with the latest staging link; see `verification-matrix.md` §Gates. G-OUT runs after the round closes and only informs the next planning round.

## Stop rules
- G-ADB1 cannot run (phone unavailable for 48 h): K02 proceeds with items (a)–(c) only, PRF proceeds without baseline (V-PRF-4 then measured at G-ADB2 against the pre-PRF main APK installed temporarily).
- G-CUT FAIL: precise stays Experimental and off; Manu decides further work.
- A task whose CI fails twice after fixes → Blocked with log excerpt.
- G-DUP not answered within 7 days: DUP2 ships with T_DUP = 0.985 (above every different-garment pair seen in planning) and the label "Very similar"; recorded as a PLAN-CHANGE.

## Rollback
One PR per task: revert its merge commit. Room v14/v15 migrations are additive (nullable columns, new table); a revert of OR1/DUP2 after release needs a forward migration, not a revert — say so in the PR. Originals in Drive are optional fields; reverting OR2 leaves blobs that the old purge never touches.
