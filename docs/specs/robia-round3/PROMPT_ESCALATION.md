# Escalation prompts (Claude Sonnet 5.5, effort high, read-only, one-shot)

Used by the Haiku coordinator ONLY for triggers E1–E3 of `PROMPT_COORDINATOR.md`. Sonnet never edits files, never pushes, never merges; it returns text the coordinator acts on.

## E1 — Device-gate analysis (G-ADB1 / G-ADB2)
```text
You analyse the results of device gate <G-ADB1|G-ADB2> of the Robia round-3 plan. Read-only.
Plan: docs/specs/robia-round3/ (read implementation-plan.md §Device gates, artifacts/inv-r3-cutout.md, artifacts/inv-r3-f.md, artifacts/inv-r3-d.md, briefs/TASK-K02.md, briefs/TASK-ED2.md, briefs/TASK-PRF.md).
Inputs (local, personal data — do not copy them into your answer beyond numbers):
- compare.py output: <path>
- app dump dir: <path>   reference dump dir: <path>   (open the stage PNGs where IoU < 0.95)
- editor tap dumps: <path>   Python selector outputs for the same inputs: <path>
- perf summaries (3 runs): <paths>   GPU/CPU benchmark lines: <text>
Write the PLAN-CHANGE section for decision-log.md:
1. Per photo: first divergent stage, its IoU, what differs (one line).
2. Closed defect list: each defect with suspected cause (file:line in the Kotlin code), the reference behaviour it must match, and which task fixes it (K02 (d) or ED2 step 4). Only defects supported by the dumps; mark anything uncertain as "hypothesis — verify with a JVM test first".
3. Perf: median janky % and p90 frame ms (G-ADB1 baseline; for G-ADB2 also the delta and V-PRF-4 pass/fail).
4. GPU (G-ADB1): load, median, p90, PSS, parity vs CPU, and one-line verdict.
5. For G-ADB2: V-K02-4 and V-K03-3 pass/fail with numbers; any remaining failure as one consolidated remediation per task.
Max 900 words.
```

## E2 — Final pre-merge check (OR1, OR2, OR3, CLN, DUP2)
```text
You do the final pre-merge check of PR #<N> (task <TASK-ID>) of the Robia round-3 plan. Read-only.
Read briefs/TASK-<TASK-ID>.md, its section in tasks.md, its verification-matrix rows, the Haiku review comment and the remediation commits. Read gh pr diff <N> and the touched files in full.
Focus only on data safety: (a) can any user photo, original, garment row or Drive blob be lost, overwritten or orphaned (incl. cancellation, crash between steps, restore on an old app version, reinstall)? (b) migrations exact and additive; (c) snapshot stays schemaVersion 2 and old fixtures decode/re-encode unchanged; (d) deletions limited to the files the brief allows; (e) tests actually exercise these paths.
Answer with exactly one first line: MERGE or BLOCK. If BLOCK, list required changes with file:line (max 10 items). Max 600 words.
```

## E3 — Blocked lane diagnosis
```text
Task <TASK-ID> of the Robia round-3 plan is blocked: <2 failed CI fix cycles | 2 review rounds with remaining required changes>. Read-only.
Read briefs/TASK-<TASK-ID>.md, its tasks.md section, the PR #<N> diff, the failing CI log (gh run view <id> --log-failed) or the review comments, and the worker receipts.
Answer with one first line: CONTINUE or ASK-MANU.
- CONTINUE: give the exact continuation for one more Haiku worker run (what to change, file:line, which test proves it), staying inside the task's files and scope.
- ASK-MANU: the decision Manu must take, in 2–3 sentences, with options (recommended first).
Max 500 words.
```
