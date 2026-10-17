# Implementation plan — robia-ux-rework

**G-APPROVAL** = Manu writes APROBADO on the planning PR and it is merged.

## Waves and leases
Max 3 workers in parallel (coordinator rule). A file listed for two tasks is a lease: the second task starts only after the first merges (or rebases on it). `strings.xml` is not a lease: each task appends one tagged block (tasks.md rule 10).

| Wave | Tasks (parallel, max 3) | Why this order |
|---|---|---|
| W1 | **U00**, **SG1**, **T01** | U00 unblocks every UI file; SG1 and T01 touch disjoint files |
| W1b | **U09** (any free slot; disjoint files), then **U01**, **U04** after U00 merges | U01 edits the `RobiaTheme {` root line of `RobiaApp.kt`; U04 shares `ManagePerformanceContractTest.kt` with U00 |
| W2 | **U02** (after U00 + U01) | disjoint files created by U00 |
| W2b | **U03** (after U02), **U06** (after U02 + SG1), then **U05** (after U03) | U03 edits the `FilterBar(` call in `BrowseGrid.kt`; U06 uses `cardTitle` from U02; U05 follows U03 in `RegressionSourceContractTest.kt` |
| W3 | **SG2a** (after SG1 + U05), **U07** (after U06), **U08** (after U03 + U06) | |
| Gate | **G-SEG-1** (Manu, after SG2a) | performance on device |
| W4 | **SG2b** (after U05 + G-SEG-1 PASS), **SG3** (after U07 + SG2a; after SG2b if both are ready, otherwise rebase) | |
| Gate | **G-SEG-2** (Manu, after SG2b) | visual quality |
| End | **Z02** | checklist + APK |

## Dependency graph (edges = must be merged first)
```
G-APPROVAL → U00, SG1, T01, U09
U00 → U01, U02, U03, U04, U05, U06, U08
U01 → U02, U03, U05, U06, U07, U08
U02 → U03 (BrowseGrid.kt call site), U06 (cardTitle)
SG1 → U06 (AddEditClothingScreen.kt), SG2a
U05 → SG2a (Developer rows in SettingsScreen.kt), SG2b
U06 → U07, U08 (Similar button line)
U07 → SG3 (AddEditClothingScreen.kt)
U03 → U08 (sparkle button line), U05 (RegressionSourceContractTest order)
SG2b → SG3 (RobiaApp.kt route; or rebase if SG3 is ready first)
SG2a → G-SEG-1 → SG2b ; SG2a → SG3
SG2b → G-SEG-2
all → Z02
```

## Hot files and owners (every pair below is also an edge above)
| File | Order of writers |
|---|---|
| `ui/RobiaApp.kt` | U00 → U01 (root theme line) → U05 (routes, plumbing) → SG2b (route) → SG3 (route; rebase if SG2b still open) |
| `ui/AddEditClothingScreen.kt` | SG1 (fallback message) → U06 → U07 (`PhotoPreview`) → SG3 (Refine slot) |
| `ui/BrowseGrid.kt` | U00 → U02 → U03 (call site only) |
| `ui/BrowseFilterBar.kt` | U00 → U03 → U08 (sparkle modifier) |
| `ui/ItemDetailScreen.kt` | U00 → U06 → U08 (Similar modifier) |
| `ui/SettingsScreen.kt` | U00 → U05 → SG2a (Developer rows) → SG2b |
| `data/SettingsRepository.kt`, `core/model/ClothingModels.kt` | U01 → SG2a → SG2b |
| `ui/BatchAddClothingScreen.kt` | SG1 (retry) → U07 (`BatchPhotoPreview`) |
| `media/PhotoBackgroundRemover.kt`, `media/BackgroundRemovalEngine.kt`, `RobiaApplication.kt` | SG1 → SG2a |
| `media/ClothingImageStore.kt` | SG1 only |
| `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro` | SG1 → SG2a |
| `RegressionSourceContractTest.kt` | U00 → U02 → U03 → U05 (one test each); U09 edits only the two PDF tests and may run in parallel — rebase on conflict |
| `ManagePerformanceContractTest.kt` | U00 → U04 |

## Review budget (finite)
One implementation pass → one independent review (U03, U06, T01, SG1, SG2a) → one consolidated remediation → coordinator final check. Stop after round 2 and ask Manu.

## Stop rules
- G-SEG-1 FAIL → pre-approved fallback (drop the MobileSAM stage, re-measure once). Second FAIL → SG2b is not started; the pack stays developer-only; SG3 still ships (bundled model). Manu decides further work.
- G-SEG-2 FAIL → the Settings row stays but the switch defaults to OFF and the row is labelled "Experimental"; Manu decides.
- A task whose CI fails twice after fixes → Blocked with log excerpt (worker rule 7).

## Rollback
Each task is one PR; revert the merge commit. U00 revert must happen before any W2 merge, or not at all (later tasks depend on its file layout). The precise cutout pack is opt-in and deletable from Settings; the ML Kit path stays intact.
