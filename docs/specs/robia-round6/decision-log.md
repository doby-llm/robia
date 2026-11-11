# Decision log: robia-round6

Type M = Manu said it. Type D = decided in planning; Manu can overturn it with one line.

- **R6-DEC-001 (M)** No device gate before publishing: once the code is merged, it is published to Play as a new update
  (Manu 2026-11-11).
- **R6-DEC-002 (D)** "Publish" means **Internal testing** (Manu and Caro install it within minutes) **and** a new
  release on the **closed Alpha** track (replaces 986/992; goes through Google review). Production is not possible yet:
  it needs 12 testers opted in for 14 days. Nothing is sent to Production.
- **R6-DEC-003 (D)** Version **1.0.1**. versionCode stays CI-derived (`GITHUB_RUN_NUMBER + 100`); it must be > 992.
- **R6-DEC-004 (D)** F1: both a speed fix and a visible indicator. Bounded parallel copy (4 at a time, order kept) plus
  a determinate "Preparing photos… N of M" shown the moment the picker returns. Back cancels and deletes the copies.
  Lazy copy on the batch screen (persistable URI grants) is out of scope: it changes which URI drafts and saved
  garments reference.
- **R6-DEC-005 (D)** F2: the AI mark in batch edit follows the same rule as single add. A field shows it while every
  selected value in it came from the model. A field the user changed loses it, also after leaving and reopening the
  draft. Nothing new is persisted in the database. The mark exists only while the batch is open, as in single add.
- **R6-DEC-006 (M+D)** F3: developer mode is unlocked the same way (10 taps on Settings) in every build, release
  included. Debug-only performance fixtures stay behind `BuildConfig.DEBUG`. Any developer surface that could delete
  data or send data anywhere other than the user's own share sheet stays debug-only, and the worker lists every
  such surface.
- **R6-DEC-007 (D)** F4: new row **Settings › Smart features › "Check for duplicates"**, visible only when the
  suggestion model is Ready (same rule as other model features). A tap runs the scan now, skipping the "already scanned
  this wardrobe" check, and shows progress in the row. Groups found open "Possible duplicates" directly. With none
  found, the row says so inline, with no popup. Pairs marked "Not duplicates" stay hidden. If some garments have
  no vector yet, the scan runs anyway and the result line says how many were not checked.
- **R6-DEC-008 (D)** F4 "doesn't always appear": DU0 (read-only) names the causes. DU1 fixes the causes DU0 marks
  CERTAIN, when the fix stays inside DU1's Files. Hypotheses become follow-ups in the issue.
- **R6-DEC-009 (D)** Release notes do not mention developer mode.
