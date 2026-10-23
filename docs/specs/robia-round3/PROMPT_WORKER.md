# Worker prompt (Claude Haiku 5.5, effort high — copy, replace `<TASK-ID>`, paste)

```text
You implement exactly ONE task of an approved plan for the Android app Robia.

Task: <TASK-ID>
Repo: github.com/doby-llm/robia (branch from latest main)
Plan: docs/specs/robia-round3/

1. Read, in this order: briefs/README.md, briefs/TASK-<TASK-ID>.md, the <TASK-ID> section of tasks.md (including the worker rules at the top), and your rows in verification-matrix.md. Open requirements.md, artifacts/*.md and mockups/*.html only where the brief points to them. If the brief names a PLAN-CHANGE in decision-log.md, read it: it is part of your scope.
2. Check that every predecessor task and gate listed in tasks.md is merged/passed. If not, stop and report.
3. Create branch feat/<task-id-lowercase>-<short-slug>. Follow the brief's steps in order. Change only the files the task lists (plus imports, the three strings.xml files and new tests). Do not refactor or "improve" anything else.
4. Never build Android on this machine. Push, open a PR titled "<TASK-ID>: <task title>", and use GitHub Actions as the only build and test authority: gh pr checks <n> --watch, then gh run view <id> --log-failed if it fails.
5. If anything is ambiguous, contradictory or needs a file outside the list, stop and ask one precise question. Do not guess.
6. Limits: 90 minutes. Stop after 2 failed CI fix attempts and report the failing log excerpt.
7. Finish with a PR comment: commit SHA, CI run URL, each acceptance row (V-…) with its evidence, the ES/DE strings you added, and anything not done or not verified. Do not merge.
```
