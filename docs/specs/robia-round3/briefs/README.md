# Briefs — robia-round3

## Authority order
`tasks.md` (scope, files, acceptance) > `briefs/TASK-<ID>.md` (how) > `requirements.md` > `artifacts/` (evidence) > mockups (look). A real contradiction: stop and ask one precise question.

## Shared facts
- Repo `doby-llm/robia`, Kotlin + Jetpack Compose + Material3, minSdk 26, targetSdk 35, Kotlin 2.4.21. Base of this plan: main `f03ea9f` plus W0 (#132, #133, #134 merged). Line numbers in briefs refer to `f03ea9f`; after W0 and earlier tasks they may shift by a few lines — search the named function.
- Package root: `app/src/main/java/com/gusanitolabs/robia/`. Room DB is v13 at the start (OR1 → v14, DUP2 → v15). Snapshot `schemaVersion` stays 2 in this round.
- CI (`.github/workflows/android-apk.yml`): `gradle testDebugUnitTest assembleDebug assembleStaging`, artifacts `robia-debug-apk`, `robia-staging-apk`, `room-schemas`. JVM tests: JUnit4, sqlite-jdbc, org.json; no Robolectric, no Compose UI tests — put logic in pure functions.
- Debug-only features (K01 dump, ED1 tap dump) must sit behind `BuildConfig.DEBUG` or developer mode as the brief says.
- Investigation reports (file:line evidence): `artifacts/inv-r3-a.md` (Browse), `inv-r3-b.md` (detail, export, storage, zip), `inv-r3-c.md` (embeddings, analytics, theme, DB), `inv-r3-d.md` (editor), `inv-r3-e.md` (batch), `inv-r3-f.md` (engines), `inv-r3-cutout.md` (device-vs-reference evidence), `inv-r3-models.md` (d3, outfits, duplicates).
- Tools: `tools/cutout-diag/` (reference stage dump + compare), `tools/perf/browse_fling.sh`, `tools/model-feasibility/` (planning benchmarks, not run by CI).
- The repository is PUBLIC: never commit personal photos, dumps, tokens or absolute home paths.

## Preferences (Manu, standing)
Same list as `docs/specs/robia-ux-rework/briefs/README.md` §Preferences (idle indicators hidden, alphabetical lists, discreet transitions, scroll hints in sheets, stacked buttons for 3+ actions, minimum end inset, neutral offline states, immediate prompts, subtle motion respecting "remove animations").
