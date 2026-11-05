# Briefs — robia-round5

## Authority order
`tasks.md` (scope, files, acceptance) > `briefs/TASK-<ID>.md` (how) > `SPEC.md` > `artifacts/` (evidence).
A real contradiction: stop and ask one precise question.

## Shared facts
- Repo `doby-llm/robia` (PUBLIC), Kotlin + Compose + Material3, minSdk 26, targetSdk 36. Base `main` @ `651a305`;
  line numbers refer to it — after earlier tasks, search the named symbol.
- Package root `app/src/main/java/com/gusanitolabs/robia/`. Room DB and Drive snapshot schema **unchanged** this round.
- CI `.github/workflows/android-apk.yml`: `testDebugUnitTest assembleDebug assembleStaging` (+ `assembleCapture` after
  REL1); JVM tests only (JUnit4); no Robolectric/Compose UI tests → pure functions + source-contract tests.
- Colour code: `core/designsystem/Color.kt` (Earth & Steel), `Theme.kt` (schemes, `RobiaExtras`, `RobiaTheme`).
- Large/personal files (captures, demo photos, keys, renders) live under `~/.local/share/robia-planning/r5/`, never in git.

## Index
| Brief | Task(s) | Executor |
|---|---|---|
| TASK-TH1.md | TH1 | Haiku worker |
| TASK-TH2.md | TH2 | Haiku worker |
| TASK-REL1.md | REL1 | Haiku worker |
| TASK-PL3.md | PL3 | Haiku worker + Opus review |
| TASK-CR.md | CR1, CR2 | Opus 5.5 high creative lane |
| CAP-SHOTLIST.md | CAP | coordinator + Manu's phone |
| PLAY-RUNBOOK.md | PL1, PL2, PB1–PB5, PL5, PL6 | coordinator / Hermes browser agent / Manu |
| PLAY-CONSOLE-SHEET.md | PB1, PB3, PB4, PB5 answers | Hermes browser agent |

## Preferences (Manu, standing — every UI task)
Lists/pickers alphabetical (locale `Collator`); no snackbar/popup for routine actions; controls with no meaning hidden;
check every icon tint in dark mode; never rely on colour only.
