# TASK-SG2b — Precise cutout in Settings

## Goal
After G-SEG-1 passes, expose the precise cutout pack to users in Settings > Smart features with the same download UX as Smart suggestions.

## Read first
TS§SG2b; `ui/SettingsScreen.kt` (U05 layout, `SettingsNavRow`), `ui/ModelDownloadUi.kt` (download states UI to mirror), SG2a `CutoutPack` state, `data/SettingsRepository.kt`, `core/model/ClothingModels.kt`.

## Steps
1. Change the default of `preciseCutoutEnabled` (added by SG2a) from false to true in `RobiaSettings` and `toRobiaSettings()`.
2. Keep the Developer switch (same setting).
3. Route `PreciseCutoutSettings` in `ui/RobiaApp.kt` (+ plumbing of the pack store and setter, nothing else). Settings row "Precise cutout" (value Off / Downloading n % / On) → `PreciseCutoutSettings` sub-screen: title, body, Download (≈ 91 MB) with the Wi-Fi rule and mobile-data confirmation reused from the Smart suggestions flow, progress, Delete (confirmation dialog stating the pack is removed and photos keep their current cutout), switch "Use precise cutout for new photos".
4. If G-SEG-2 later fails, the coordinator flips the default to false and adds "Experimental" (not part of this task).

## Tests
V-SG2-6.

## Done when
V-SG2-6; device row V-Z2-17 and G-SEG-2 message in receipt:
"G-SEG-2: con 'Precise cutout' activado y desactivado, añade al menos 10 prendas (3 con percha, 2 blancas sobre fondo claro, calcetines si tienes). Para cada una dime OK/NOK con y sin. Pasa si con el pack hay menos NOK y no se pierde ninguna parte de prenda que ML Kit sí mantenía."

## Do not
Change the pipeline or the download engine.
