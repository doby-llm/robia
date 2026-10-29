# Investigation f3-c — F30 cutout method names, F31 GPU benchmark, F32 smart eraser

Build 0de712b (main b7b5476). Read-only. Paths are under `app/src/main/java/com/gusanitolabs/robia/` unless they start with `app/` or `docs/`.

---

## F30 — Cutout method naming

### Current behaviour / root cause
- One setting, `RobiaSettings.cutoutEngine`, type `CutoutEngineChoice { Automatic, Standard }` (`core/model/ClothingModels.kt:207`), default Standard (`:240`). Stored as `cutout_engine` (`data/SettingsRepository.kt:366`); the read falls back to Automatic for an unknown stored name (`:341`); K00 migration `migratedCutoutEngine` (`:349`).
- **Standard** = ML Kit only. It is the default (R3-DEC-002, K00; `docs/specs/robia-round3/briefs/TASK-K00.md`).
- **Automatic** = precise engine (ONNX pipeline + MediaPipe/SAM, `media/cutout/PreciseCutoutEngine.kt`) when the pack is downloaded (`media/cutout/CutoutOptions.kt:25` → `useEnginePrecise` = `packReady && enabled`, `CutoutEngineRegistry.kt:57`); no pack → ML Kit. A failed, rejected or timed-out precise result also falls back to ML Kit (`CutoutEngineRegistry.kt:59-65`). There is no per-photo choice.
- **"Precise" does not exist as a method.** The engine exists, but only under the label "Automatic" (carrying the "Experimental" chip, `ui/PreciseCutoutSettings.kt:248-249,350-353`). The word "Precise" appears only in the editor's "Cut out again" sheet (`RedoCutoutChoice.Precise`, `media/cutout/CutoutOptions.kt:39`; `ui/PhotoEditorScreen.kt:815-819`).
- Manu's confusion: the Cutout screen says "Precise cutout" (pack card), "Needs precise cutout" (hanger row) and "Uses precise cutout when downloaded" (Automatic hint), yet the method list has no "Precise".

### Evidence — every user-visible name and reference (EN / ES / DE)
| Key (`values/strings.xml`) | EN | ES | DE | Used at |
|---|---|---|---|---|
| `cutout_method` :764 | Cutout method | Método de recorte | Zuschnittmethode | `PreciseCutoutSettings.kt:181,245` |
| `cutout_method_standard` :766 | Standard | Estándar | Standard | `:347` |
| `cutout_method_automatic` :765 | Automatic | Automático | Automatisch | `:346` |
| `cutout_method_standard_hint` :768 | Google ML Kit only. Faster, no hanger removal. | | | `:187` |
| `cutout_method_automatic_hint` :767 | Uses precise cutout when downloaded, otherwise standard. | | | `:185` |
| `cutout_method_experimental(_hint)` :789-790 | Experimental / May remove parts… | | | `:249,353` |
| `cutout_needs_precise` :770 | Needs precise cutout | | | `:196` |
| `precise_cutout_title/body` :752-753 | Precise cutout / Better background removal… | Recorte preciso | Präziser Zuschnitt | pack section `:118,125` |
| `redo_cutout_standard/precise/precise_keep_hanger` :784-786 | Standard / Precise / Precise without hanger removal | Estándar / Preciso / … | Standard / Präzise / … | editor sheet |
| `dev_precise_cutout(_summary)` :117-118 | Use precise cutout (developer switch) | | | `ui/SettingsScreen.kt:370`; flips Standard↔Automatic (`ClothingModels.kt:256`, `SettingsRepository.kt:52`) |
Also: hanger rows are gated on `engine == Automatic` (`PreciseCutoutSettings.kt:71-76`); `V-K00-1/2/D` (`verification-matrix.md:7-9`) assert "Standard selected, precise option labelled Experimental"; `SettingsMappingTest`, `PreciseCutoutSettingsTest`, `CutoutHangerOptionsTest`, `CutoutOptionsTest` use `CutoutEngineChoice.Automatic`.

### Proposed fix
Three methods, with semantics (Manu to confirm, see Unknowns):
- **Standard** — ML Kit. Help: "Fast. Works for most photos. Does not remove hangers."
- **Precise** — always the precise engine; needs the downloaded cutout pack (row disabled until ready; per-photo failures still fall back to ML Kit). Help: "Best for hangers and similar-coloured backgrounds. Slower. Needs the cutout pack." (keeps the Experimental chip).
- **Automatic** — precise when the pack is downloaded, otherwise Standard (today's "Automatic" behaviour, unchanged). Help: "Uses Precise when the pack is downloaded, otherwise Standard."

Strings: EN as above; ES Estándar / Preciso / Automático ("Rápido. Sirve para la mayoría de fotos. No quita perchas." / "Ideal con perchas o fondos parecidos. Más lento. Necesita el paquete de recorte." / "Usa Preciso si el paquete está descargado; si no, Estándar."); DE Standard / Präzise / Automatisch ("Schnell. Passt für die meisten Fotos. Entfernt keine Bügel." / "Am besten bei Bügeln oder ähnlichem Hintergrund. Langsamer. Braucht das Zuschnitt-Paket." / "Nutzt Präzise, wenn das Paket geladen ist, sonst Standard."). New keys `cutout_method_precise`, `cutout_method_precise_hint`. Replace `cutout_needs_precise` with "Needs Precise or Automatic and the cutout pack"; rename `precise_cutout_title` to "Cutout pack" (ES "Paquete de recorte", DE "Zuschnitt-Paket") and its body to say "Needed by Precise and Automatic" so the word "Precise" means one thing only. Redo sheet already says Standard/Precise: keep.

Enum: add `Precise` to `CutoutEngineChoice`; keep `Automatic` and `Standard` names and the stored strings (no migration). Alternative with zero enum change: relabel only — rejected, it cannot give three names.

Files: `core/model/ClothingModels.kt`; `data/SettingsRepository.kt` (unknown → still Automatic; dev switch unchanged); `media/cutout/CutoutOptions.kt` (`usePrecise` = Precise or Automatic, then pack check); `ui/PreciseCutoutSettings.kt` (options list, labelRes, hints, Precise disabled without pack, hanger gating `!= Standard`); `ui/SettingsScreen.kt` only if the dev summary text changes; three `strings.xml`; tests `SettingsMappingTest`, `PreciseCutoutSettingsTest`, `CutoutHangerOptionsTest`, `CutoutOptionsTest`; matrix rows V-K00-*.

JVM-testable: `cutoutOptionsOf` for the 3 values × pack ready/not; `cutoutHangerOptionsAvailable`; option list/availability function; labels/hints present in 3 locales; stored `Automatic` still reads Automatic.

### Risks
Stored `Automatic` users keep precise (fine; only the developer). "Precise" selected with pack deleted silently runs ML Kit unless the row is disabled — the registry fallback already covers it.

### Unknowns
What Manu means by "Automatic" beyond "precise when available". There is no per-photo heuristic today (choosing precise only for photos with a hanger would need running a detector first: new engine work, not specified). I recommend shipping the semantics above and asking him; if he wants a real auto-choice it is a separate task.

---

## F31 — GPU benchmark failure

### Cause
- Dev row "Model benchmark (GPU)" (`ui/SettingsScreen.kt:319-361`) → `DevBenchmark.measure(useGpu = true)` → `runGpuBenchmark` (`suggestions/DevBenchmark.kt:63-76,79-107`) builds `LiteRtNativeEmbedder(backend = Backend.GPU(), visionBackend = Backend.GPU())` (`:82-87`), then `engine.computeEmbedding` (`suggestions/engine/LiteRtNativeEmbedder.kt:35-36`). The native `ComputeEmbedding` throws `TensorBuffer must have all static dimensions`: the GPU delegate only accepts fixed tensor shapes, and this model has dynamic dims (variable text length / vision token count; recipe `VISION_TOKENS = 70`, `suggestions/EmbeddingRecipe.kt:15`). Model: `embeddinggemma-2-text-vision-440m.litertlm` (`EmbeddingRecipe.kt:8-13`), library `litertlm 0.18.0` (`gradle/libs.versions.toml:17,40`).
- The repo says the same: G-ADB1 already produced this error and the verdict was "GPU path unusable with this model (dynamic dimensions); stay on CPU; any retry needs a static-shape export, a decision for Manu" (`docs/specs/robia-round3/decision-log.md:132-142`). That verdict never reached Manu as a question, so he re-tested.
- The brief limited GPU to the developer row: production stays `Backend.CPU()` (`briefs/TASK-GPU.md`, V-GPU-2; `tasks.md:73-78`, "Switching the app to GPU" out of scope).
- Not established: which sub-model (text or vision) raises it. Web search (standard) found no matching report; no conclusion drawn from it.

### Should GPU work?
No, not with this file. A fix needs Google to publish a static-shape export of this model, or us to convert one ourselves (we do not have the conversion tooling or the right to rebuild the recipe: `EmbeddingRecipe` is pinned by hash/id). Fixing the input shape alone is not possible from the app: shapes are inside the `.litertlm`, and LiteRT-LM exposes no shape option in `EmbeddingEngineConfig`.

### Expected gain
Small for what Manu wants. The embedder (~1.5 s median per image on CPU, 760 MB PSS) runs in the background suggestions/duplicate pipeline (`suggestions/SuggestionService.kt:86`, `EmbeddingBackfill.kt:143`). The user-visible cost of photo processing is the cutout (ML Kit / ONNX), which this GPU path does not touch. Even a working GPU might give 1.5–3× on the embedder, unmeasured, with the Adreno 650 driver risk.

### Options
1. Static-shape export → GPU: large effort, outside our tooling, speculative gain.
2. CPU/XNNPACK fallback with a message: the production path already is CPU; the row only exists to measure GPU.
3. Cheap experiment: GPU for the vision tower only (`backend = CPU`, `visionBackend = GPU`): one parameter change in `DevBenchmark.kt:85-86`; may fail with the same error.
4. Drop the GPU row.

**Recommendation: option 4**, closing the question as "tested, not supported by this model". Optionally run option 3 once on the device before deleting (5 minutes, dev-only). Files: `suggestions/DevBenchmark.kt` (`useGpu`, `runGpuBenchmark`, `gpuReport`, `gpuBenchmarkReport`, `gpuUnavailableReport`, `GPU_LOG_TAG`); `ui/SettingsScreen.kt` (GPU row and `benchmarkGpu*` state); `suggestions/engine/LiteRtNativeEmbedder.kt` (`backend`/`visionBackend` params, optional); `dev_model_benchmark_gpu` in three `strings.xml` (EN/ES/DE keys at :794); delete `app/src/test/.../suggestions/GpuBenchmarkReportTest.kt`; matrix V-GPU-*; add a decision-log line. Deletion task: independent review yes. Risk: none for users (developer-only row); losing the ability to re-measure GPU after a LiteRT-LM upgrade (re-add from git).

---

## F32 — Smart eraser

### What was decided
- R3-DEC-043 (Manu 2026-10-24, `decision-log.md:274-278`): do **not** remove; replace the broken model tap by a deterministic component tap (UI-SMART, option C). **Failure rule:** if V-UI-SMART-D still fails on the device, the next step is removal (option B, brush becomes the only tool, `MediaPipeInteractiveSelector` stays for `PreciseCutoutEngine`), no second fix. Also `tasks.md:244,347-351`, R3-DEC-046 (UI-SMART independent review: no).
- UI-SMART is merged and matches its brief. So the task was complete, not contradicted: F32 is Manu invoking option B (V-UI-SMART-D open in `artifacts/device-checklist.md:74`; `r3-exec/decisions.md:4` lists "smart eraser" as not passed). The decision was clear; Manu's memory "it was going to be removed" is only the conditional branch. Treat F32 as the trigger.
- Manu's description of the defaults is inverted today: `brush` starts false (`ui/PhotoEditorScreen.kt:234`), so Smart is the default and "Brush" is an opt-in chip (`:852-856`); mode starts at Keep (`:233`), where Smart only shows a hint (`media/cutout/SelectionClip.kt:101`). Intended end state: brush only, no chip.

### Still present (grep of main, tests, scripts)
`ui/PhotoEditorScreen.kt`: `brush` state :234, `SMART_MAX_POINTS/TRAIL_DASH/TRAIL_GAP/SNAP` :154-158, `PULSE_MS/RADIUS` :159-160, `PulseMark` :186, `pulse` :263 + effect :330, `runSmartSelection` :370-395, smart branch of `finishGesture` :407-413 (`moved` param), `pointerInput(tab, brush, …)` :611, dashed trail :706-720, pulse drawing :723-731, `CutoutControls` params `brush/onBrushChange` and the `FilterChip` :826-856, `selectPixelOf` :1037, `smartSelectionPoints` :1044, `samplePath` :1051, imports :127,135, diagnostic `refine_component_tap`, KDoc :197. `media/cutout/SelectionClip.kt`: `ComponentPick`, `componentsToRemove`, `componentAt`, `snapRadiusPhotoPx`, `SmartTapOutcome`, `smartTapOutcome`, `removesMostOfGarment`, `SELECTION_THRESHOLD`, snap constants (whole file, if nothing else uses it). Strings (3 locales each): `refine_brush` :299, `editor_smart_whole_garment` :792, `editor_smart_keep_hint` :879; `refine_hint` :300 ("Tap or swipe…") needs a brush wording. Tests: `SelectionClipTest`, `SmartGestureTest`, `SmartRemoveUndoTest`, and `PhotoEditorMappingTest.kt:110-136` (the three `selectPixelOf` tests only). `scripts/check_*_static.py`, `tools/` (bench only): no references. Docs: matrix `V-ED1-3/4/D`, `V-UI-SMART-1..5/D`; checklist :35,:74; tasks.md, requirements REQ-FB-10.

### Must stay
`media/cutout/InteractiveSelector.kt` (`MediaPipeInteractiveSelector`), `PreciseCutoutEngine.kt`, `InteractiveSelectorContractTest` (S2 of the precise engine); `MaskOps.labelComponents` (also used at `MaskOps.kt:195`); `RefineSession.apply(hard)`, `hardSelection`, `EditOp.Hard` (history replay/format, `RefineSessionTest`; leave, may become test-only); `applyBrush`, `commitStroke`, `brushRadiusPx` (`BrushStroke.kt`), `BRUSH_*` constants, Keep/Remove segmented control, undo/redo/reset, "Cut out again".

### Fix (task UI-SMART-DROP)
Brush is the only gesture; Keep/Remove selects what the stroke does; no chip; trail drawn solid round as the old brush trail. Files: `ui/PhotoEditorScreen.kt`; `media/cutout/SelectionClip.kt` (delete if `SELECTION_THRESHOLD` has no other user, per grep); delete `SelectionClipTest.kt`, `SmartGestureTest.kt`, `SmartRemoveUndoTest.kt`; `PhotoEditorMappingTest.kt` (drop the `selectPixelOf` tests); three `strings.xml` (remove `refine_brush`, `editor_smart_*`; reword `refine_hint`: EN "Paint over a part to keep or remove it.", ES "Pinta sobre una parte para mantenerla o quitarla.", DE "Male über einen Teil, um ihn zu behalten oder zu entfernen."); docs: matrix (replace V-UI-SMART-* by new rows: no Brush chip, brush works in Keep and Remove), device checklist, decision-log new entry R3-DEC-0xx "option B invoked", requirements REQ-FB-10. JVM tests: existing brush tests stay; add a test of the removed-symbol absence only by grep in REV.

Risks: gesture regression (tap with brush = one-point stroke; multi-touch zoom unchanged); `moved`/touchSlop code left dead; compile errors from the many imports. Unknown: whether a single tap should paint a dot (it does today with Brush on).

---

## Recommended task split
| Id | Scope | Files | Predecessors | Independent review |
|---|---|---|---|---|
| **UI-SMART-DROP** (F32) | Remove smart tool and Brush chip | as in F32 Fix | none (file order: before any other `ui/PhotoEditorScreen.kt` task) | **yes** (deletion) |
| **GPU-DROP** (F31) | Remove GPU benchmark row and code | as in F31 option 4 | none (touches `SettingsScreen.kt`: order against CUT-NAMES) | **yes** (deletion) |
| **CUT-NAMES** (F30) | Standard / Precise / Automatic + help texts + config wording | as in F30 Fix | none; Manu confirms "Automatic" semantics first (D-F30) | no |
Run UI-SMART-DROP and GPU-DROP in parallel; CUT-NAMES after GPU-DROP on `ui/SettingsScreen.kt` only if it edits it (probably not, so fully parallel). All three touch the three `strings.xml` → merge one at a time.
