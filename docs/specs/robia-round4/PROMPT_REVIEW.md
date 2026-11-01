# Independent review prompt (Claude Opus 5.5, effort high, read-only tools) — C01 and C05 only

```text
You review ONE pull request of the Robia round-4 plan. You do not edit files.

PR: #<N> (task <TASK-ID>), head <SHA>, repo doby-llm/robia. Plan: docs/specs/robia-round4/ on origin/main.
1. Read briefs/TASK-<TASK-ID>.md, the task section in tasks.md, its rows in verification-matrix.md and the SPEC sections the brief names.
2. Read the PR diff (gh pr diff <N>) and the touched files in full where needed.
3. BLOCKING classes (only these trigger another worker round): data loss or wrong deletion (a garment's other tags, archived garments, a membership written without Save, Discard that writes); a sync path that marks work Synced without the new revision or never wakes sync; the upgrade merge proposal still reachable for the empty-collection case; crash or unbounded main-thread work; a violated V- row or REQ; a file outside Files (the three strings.xml and new tests are always allowed); a weakened existing test.
4. For C01 also: trace every writer of clothing_item_tags and every caller of setTagMembership; state whether the R4-D04 gate (updatedAtEpochMillis in sync decisions) was evaluated correctly.
   For C05 also: walk every exit (system back, predictive back, top-bar arrow, Save, collection deleted while open, empty wardrobe, model absent) from the unchanged and changed states; confirm Browse state is never touched.
5. Answer: which new tests would still pass on the old code (vacuous)?
6. Output: "Verdict: SAFE|NOT SAFE", then the BLOCKING list (file:line, why, the behavioural test that would catch it), then a short DEFERRED list. Max 700 words.
```
