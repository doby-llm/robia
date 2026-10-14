# Worker prompt (copy, replace `<TASK-ID>`, paste)

```text
You implement exactly ONE task of an approved plan for the Android app Robia.

Task: <TASK-ID>
Repo: github.com/doby-llm/robia (branch from latest main)
Plan: docs/specs/robia-embeddings-sync-manage/

1. Read, in this order: briefs/README.md, briefs/<TASK-ID>.md, the <TASK-ID> section of tasks.md, and the rows of verification-matrix.md it lists. Open other plan files only when the brief points to them.
2. Check that every predecessor task listed in tasks.md is merged in main. If not, stop and report.
3. Create branch feat/<task-id-lowercase>-<short-slug>. Follow the brief's steps in order. Change only the files the task lists (plus imports, the three strings.xml files and tests). Do not refactor or "improve" anything else.
4. Never build Android on this machine. Push, open a PR titled "<TASK-ID>: <task title>", and use GitHub Actions as the only build and test authority: gh pr checks <n> --watch, then gh run view <id> --log-failed if it fails.
5. If anything is ambiguous, contradictory or needs a file outside the list, stop and ask one precise question. Do not guess.
6. Limits: 90 minutes. Stop after 2 failed CI fix attempts and report the failing log excerpt.
7. Finish with a PR comment: commit SHA, CI run URL, each acceptance row (V-…) with its evidence, and anything not done. Do not merge.
```

Notes for Manu:
- One task per run, following the waves in `implementation-plan.md`.
- Tasks with a device step (E01, Z01) end by asking you to install the staging APK and paste results.
- TASK-E02 runs Python on the Raspberry Pi; it uses the data in `~/.local/share/robia-planning/`.
- If a run stops with a question, answer it and paste the same prompt again plus your answer.
