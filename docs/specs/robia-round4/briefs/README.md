# Briefs — robia-round4

## Authority order
`tasks.md` (scope, files, acceptance) > `briefs/TASK-<ID>.md` (how) > `SPEC.md` > `artifacts/` (evidence) > mockup (look).
A real contradiction: stop and ask one precise question.

## Shared facts
- Repo `doby-llm/robia`, Kotlin + Compose + Material3, minSdk 26, targetSdk 35 (→ 36 in P01), AGP 8.13.2, CI Gradle 8.14.3.
  Base `main` @ `0ceb916`; line numbers in briefs refer to it — after earlier tasks, search the named symbol.
- Package root `app/src/main/java/com/gusanitolabs/robia/`. Room DB v15, **unchanged this round**. Drive snapshot
  `WARDROBE_SYNC_SCHEMA_VERSION = 2`, **unchanged**.
- CI: `.github/workflows/android-apk.yml` runs `gradle testDebugUnitTest assembleDebug assembleStaging`; artifacts
  `robia-debug-apk`, `robia-staging-apk`, `room-schemas`. JVM tests only (JUnit4, sqlite-jdbc, org.json); no
  Robolectric, no Compose UI tests → put logic in pure functions and pin wiring with source-contract tests
  (pattern: `RegressionSourceContractTest`, which reads the file under test as text).
- sqlite-jdbc DAO tests read `@Query` SQL from `Dao.kt` with a `daoStatement(method, bindings)` helper: copy the private
  helper from `app/src/test/java/com/gusanitolabs/robia/data/DefaultCategorySeedingTest.kt:139-147` into your new test
  file. Room-generated `@Insert` SQL is written by hand in the test.
- Evidence: `artifacts/inv-r4-collections-sites.md` (every category-id site, sync/merge analysis),
  `artifacts/inv-r4-member-screen.md` (Browse composition, search, filters, navigation, write path, tests),
  `artifacts/inv-r4-play-readiness.md` (Play audit), `artifacts/inv-r4-mlkit-embeddings.md`.
- Mockup `mockups/collections.html`: frames 1–9 plus the EN/ES/DE copy table at the end.
- The repository is PUBLIC: no personal photos, dumps, tokens, keystores or absolute home paths.

## Preferences (Manu, standing — apply to every UI task)
Status indicators hidden when idle; every list/picker/tab alphabetical (locale-aware `Collator`, fixed rows such as
"Not in any" last); removed items leave with a discreet transition; dialogs with 3+ actions use stacked full-width
buttons, destructive action in the error colour, Cancel/outside tap/back never change data; controls with no meaning
are hidden, not disabled; no snackbar/popup for routine actions; prompts appear immediately; minimum end inset at the
bottom of scroll lists; check every icon tint in dark mode; signed +/− language never relies on colour only.
