# Briefs — robia-ux-rework

## Authority order
`tasks.md` (scope, files, acceptance) > `briefs/TASK-<ID>.md` (how) > `technical-specification.md` (detail) > mockups (look). A real contradiction: stop and ask one precise question.

## Shared facts
- Repo `doby-llm/robia`, Kotlin + Jetpack Compose + Material3, Compose BOM 2024.12.01, minSdk 26, targetSdk 35, AGP 8.13.2, Kotlin 2.4.21. Base of this plan: main fc45cec.
- Package root: `app/src/main/java/com/gusanitolabs/robia/`. Theme: `core/designsystem/` (Earth & Steel, `docs/ux/earth_steel/DESIGN.md`). Shapes: small 8, medium 12, large 16, extraLarge 24 dp.
- Strings: `app/src/main/res/values{,-es,-de}/strings.xml` (644 lines each).
- CI (`.github/workflows/android-apk.yml`) runs `gradle testDebugUnitTest assembleDebug assembleStaging` and uploads `robia-staging-apk`. JVM tests use JUnit4, sqlite-jdbc and org.json; no Robolectric, no Compose UI tests. Put logic in pure functions to test it.
- Source-contract tests (`RegressionSourceContractTest`, `ManagePerformanceContractTest`, `SyncAttentionMappingTest`) read source text. After U00 they use `uiSource()`. If you restructure a pinned block, keep the property the test checks (rewrite the anchor, never delete the assertion) and say so in the receipt.
- The repo is PUBLIC: never commit personal photos, tokens or absolute home paths.
- Investigation reports with file:line evidence for this round: `artifacts/inv-r2-a.md` (Browse, Manage, filters, settings, card), `artifacts/inv-r2-b.md` (form, processing overlay, PDF, review animations), `artifacts/inv-r2-c.md` (segmentation pipeline, taxonomy). Segmentation evidence: `artifacts/seg-benchmark.md`; reference Python: `tools/segmentation-bench/` at the repo root (added by the planning PR).

## Preferences (Manu, standing)
- Status indicators show nothing when idle/synced; when active they appear immediately and stay a minimum time (no blinking).
- Every list/picker/tab of tags, occasions, colours is alphabetical (locale-aware `java.text.Collator`), fixed rows (None / Not set) first or last as specified.
- Accepted/removed items leave with a discreet transition, never a bare disappearance.
- Lists that scroll inside dialogs/sheets need a visible scroll hint.
- Dialogs with 3+ actions use stacked full-width buttons; destructive actions use the error colour; Cancel / outside tap / back never change data.
- End-of-scroll spacing = minimum inset only.
- Offline/DNS states render neutral, not red.
- Prompts appear immediately after the user's confirm (no delayed popups).
- Subtle motion: nothing exaggerated; respect system "remove animations".
