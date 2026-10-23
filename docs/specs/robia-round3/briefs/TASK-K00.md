# TASK-K00 — Precise cutout off by default (G-SEG-2 fallback)

## Goal
Manu reports that the precise cutout "destroys almost any garment" (pink shorts: interior removed, hanger kept; feedback/2026-10-22-manu-answers-r1.md, N3). G-SEG-2 has therefore failed and the robia-ux-rework stop rule applies: the precise engine stays available but is off by default and labelled Experimental until gate G-CUT passes. This task only changes the default and the label; K01–K03 diagnose and fix the engine.

## Read first
`core/model/ClothingModels.kt` `CutoutEngineChoice` (l.108-109) and `RobiaSettings.cutoutEngine` (l.129); `data/SettingsRepository.kt` keys l.33-40 and `toRobiaSettings()`; `ui/PreciseCutoutSettings.kt` engine options (~l.224); `docs/specs/robia-ux-rework/implementation-plan.md` §Stop rules (G-SEG-2 FAIL).

## Files
`core/model/ClothingModels.kt`; `data/SettingsRepository.kt`; `ui/PreciseCutoutSettings.kt`; `app/src/test/java/com/gusanitolabs/robia/data/SettingsMappingTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PreciseCutoutSettingsTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `RobiaSettings.cutoutEngine` default → `CutoutEngineChoice.Standard`. Keep the enum unchanged.
2. `SettingsRepository`: new boolean key `cutout_default_v3_applied`. On the first read where it is absent: write `cutout_engine = Standard` and the flag in one `edit {}`. Afterwards the stored value is used as is. Put the decision in a pure function `internal fun migratedCutoutEngine(stored: CutoutEngineChoice?, flagApplied: Boolean): Pair<CutoutEngineChoice, Boolean>` (returns value + whether to write) and test it.
3. `PreciseCutoutSettings.kt`: the Automatic/precise option shows the suffix label "Experimental" (string `cutout_method_experimental` = "Experimental") as a small tonal chip or supporting text; supporting line under it: `cutout_method_experimental_hint` = "May remove parts of the garment. Check each result." Standard is shown first.
4. Nothing else changes: hanger/sensitivity rows keep their current enable rules.

## Tests
Extend `SettingsMappingTest` with the three cases of V-K00-1 (pure function + `toRobiaSettings` default). Extend `PreciseCutoutSettingsTest` for the label.

## Done when
V-K00-1, V-K00-2, V-K00-D pass; CI green.

## Do not
Change engine code, delete the pack, change `cutoutOptionsOf`, or touch the editor.
