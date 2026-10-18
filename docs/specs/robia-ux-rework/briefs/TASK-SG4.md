# TASK-SG4 — Cutout settings + "Redo cutout" in the photo editor

## Goal
Manu (2026-10-18, `feedback/2026-10-18-manu-cutout-settings.md`, answers 1A 2A 3A):
- Let the user choose the cutout behaviour in Settings.
- Let the user redo the cutout of one photo with another method from the photo editor.
- Offer only options that were measured: no matting-model picker (IS-Net, BEN2 and BiRefNet were rejected, see `artifacts/inv-r3-matting.md`).
- Hanger "High" is backed by `artifacts/inv-r3-hanger-sensitivity.md`.

## Read first
- TS§SG4, TS§SG2a ("Pipeline", "Engine selection"), TS§SG1 (`AlphaRefiner`), TS§B01 (`BatchParallelism`), TS§SG3.
- The SG2b screen `ui/PreciseCutoutSettings.kt`.
- `media/cutout/{CutoutEngineRegistry,PreciseCutoutEngine,HangerRemoval}.kt`, `media/AlphaRefiner.kt`.
- `ui/PhotoEditorScreen.kt`, `media/cutout/RefineSession.kt`.
- `RobiaApplication.kt`: registry and BatchParallelism wiring.

## Steps
1. **Settings model** (`core/model/ClothingModels.kt` `RobiaSettings`, `data/SettingsRepository.kt` with keys and setters, and the test fakes):
   - `cutoutEngine: CutoutEngineChoice = Automatic` (`Automatic` | `Standard`). Migration: the SG2b boolean `preciseCutoutEnabled` maps `true → Automatic` and `false → Standard`. Keep reading the old key once and write the new key `cutout_engine`. Afterwards the old boolean is no longer used by the UI.
   - `cutoutRemoveHanger: Boolean = true` (`cutout_remove_hanger`).
   - `cutoutHangerSensitivity: HangerSensitivity = Normal` (`Normal` | `High`; `cutout_hanger_sensitivity`).
   - `cutoutEdges: CutoutEdges = Soft` (`Soft` | `Sharp`; `cutout_edges`).
   - `batchCutoutMode: BatchCutoutMode = Balanced` (`Balanced` | `MemorySaver`; `batch_cutout_mode`).
   - Unknown or missing stored values fall back to the defaults.
2. **Options object** (new pure `media/cutout/CutoutOptions.kt`):
   - `data class CutoutOptions(val usePrecise: Boolean, val removeHanger: Boolean, val hangerThreshold: Float, val edges: CutoutEdges)`.
   - `fun cutoutOptionsOf(settings, packReady: Boolean)`:
     - `usePrecise = packReady && engine == Automatic`.
     - `hangerThreshold = 0.25f` for Normal and `0.12f` for High.
   - The registry gets `@Volatile var options: () -> CutoutOptions` (set in `RobiaApplication` from the settings flow). `isPreciseActive` becomes `options().usePrecise`.
   - `fun batchCutouts(mode, preciseActive) = if (mode == MemorySaver) 1 else if (preciseActive) 1 else 2`. Wire it to `BatchParallelism.cutouts`.
3. **Pipeline:**
   - `HangerRemoval` takes the threshold as a parameter (both detector passes) instead of the constant 0.25.
   - When `removeHanger` is false, the precise pipeline skips S3/S4/S4b and does not run D-FINE or MobileSAM.
   - The caps (15 % kill, plausibility) are unchanged.
4. **Edges:** `AlphaRefiner.refine(..., profile: CutoutEdges = Soft)`.
   - Soft = today's smoothstep(0.35, 0.65) plus feather.
   - Sharp = smoothstep(0.47, 0.53) and no feather. The 1-px decontamination stays.
   - Both engines (ML Kit and precise) and the editor's Done pass the setting.
5. **Settings UI.** The SG2b row "Precise cutout" becomes **"Cutout"**, and its sub-screen keeps the pack download block at the top. Under it, with the same row style as the other Settings sub-screens:
   - "Cutout method": Automatic / Standard.
     - Automatic supporting text: "Uses precise cutout when downloaded, otherwise standard."
     - Standard supporting text: "Google ML Kit only. Faster, no hanger removal."
   - "Remove hangers" switch. Disabled with the supporting text "Needs precise cutout" when the pack is absent or the method is Standard.
   - "Hanger detection": Normal / High.
     - Supporting text for High: "Finds more clips and wooden hangers. Check the result."
     - Disabled when "Remove hangers" is off or disabled.
   - "Edges": Soft / Sharp.
   - "Batch speed": Balanced / Memory saver. Supporting text: "Memory saver processes one photo at a time."
   - Single-choice rows open a small dialog with radio buttons, as the app's existing single-choice settings do (reuse that composable if one exists; otherwise use a plain `AlertDialog` with `RadioButton` rows).
6. **Redo cutout** (`ui/PhotoEditorScreen.kt`, `media/cutout/RefineSession.kt`):
   - The Cutout tab gets a "Redo cutout" text button, shown only when `rawSourceUri != null`. That means new photos before saving, and batch drafts. Saved garments have no original photo (decision rev 3, answer 3A), so the button is hidden for them, without any message.
   - Tapping it opens a bottom sheet with up to three choices: "Standard", "Precise" and "Precise without hanger removal". The precise choices are shown only when the pack is ready.
   - The chosen engine runs once on the raw source, with the current edge setting and a one-off `CutoutOptions` override; the global settings are not changed. The editor shows a progress pulse while it runs.
   - The result replaces the base alpha through `RefineSession.replaceBase(alpha)`. This is undoable: Undo restores the previous base and edits, and Reset goes back to the new base. On failure, show a snackbar "Could not redo the cutout" and change nothing.
7. **Strings** (EN exact; ES and DE in the receipt): `cutout_settings_title` "Cutout", `cutout_method` "Cutout method", `cutout_method_automatic` "Automatic", `cutout_method_standard` "Standard", the two supporting texts above, `cutout_remove_hangers` "Remove hangers", `cutout_needs_precise` "Needs precise cutout", `cutout_hanger_detection` "Hanger detection", `cutout_hanger_normal` "Normal", `cutout_hanger_high` "High", `cutout_hanger_high_hint`, `cutout_edges` "Edges", `cutout_edges_soft` "Soft", `cutout_edges_sharp` "Sharp", `batch_speed` "Batch speed", `batch_speed_balanced` "Balanced", `batch_speed_memory_saver` "Memory saver", `batch_speed_hint`, `redo_cutout` "Redo cutout", `redo_cutout_standard` "Standard", `redo_cutout_precise` "Precise", `redo_cutout_precise_keep_hanger` "Precise without hanger removal", `redo_cutout_failed` "Could not redo the cutout".

## Tests
- `CutoutOptionsTest` (V-SG4-1): the mapping table for engine × pack × sensitivity, `batchCutouts` for each case, and the legacy boolean migration.
- `AlphaRefinerTest` addition (V-SG4-2): the Sharp profile gives a narrower transition than Soft on a synthetic ramp.
- `MaskOpsTest` / `HangerRemovalTest` addition (V-SG4-3): `removeHanger = false` leaves the selection untouched and does not invoke the detector (fake). The threshold parameter is passed to both passes.
- `RefineSessionTest` addition (V-SG4-4): `replaceBase` followed by undo restores the previous base and edits.

## Done when
V-SG4-1..5, CI green, independent review PASS; device rows V-Z2-28..30.

## Do not
- Add a matting-model picker, expose numeric thresholds, or change the default results: Automatic, hanger removal on, Normal and Soft equal today's behaviour.
- Store original photos.
- Change the batch ordering logic of B01 beyond the `cutouts` value.
