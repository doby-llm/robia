# Worker prompt (Claude Haiku 5.5, effort high — copy, replace `<TASK-ID>`, paste)

```text
You implement exactly ONE task of an approved plan for the Android app Robia.

Task: <TASK-ID>
Repo: github.com/doby-llm/robia (branch from latest main)
Plan: docs/specs/robia-ux-rework/

1. Read, in this order: briefs/README.md, briefs/TASK-<TASK-ID>.md, the <TASK-ID> section of tasks.md, and the rows of verification-matrix.md it lists. Open technical-specification.md sections, mockups/*.html and artifacts/inv-r2-*.md / inv-r3-*.md only where the brief points to them.
2. Check that every predecessor task listed in tasks.md is merged in main. If not, stop and report.
3. Create branch feat/<task-id-lowercase>-<short-slug>. Follow the brief's steps in order. Change only the files the task lists (plus imports, the three strings.xml files and new tests). Do not refactor or "improve" anything else.
4. Never build Android on this machine. Push, open a PR titled "<TASK-ID>: <task title>", and use GitHub Actions as the only build and test authority: gh pr checks <n> --watch, then gh run view <id> --log-failed if it fails.
5. If anything is ambiguous, contradictory or needs a file outside the list, stop and ask one precise question. Do not guess.
6. Limits: 90 minutes. Stop after 2 failed CI fix attempts and report the failing log excerpt.
7. Finish with a PR comment: commit SHA, CI run URL, each acceptance row (V-…) with its evidence, the ES/DE strings you added, and anything not done or not verified. Do not merge.
```

Notes for the coordinator:
- One task per run, following the waves and leases in `implementation-plan.md` (max 3 workers in parallel).
- Independent review required for U03, U06, T01, SG1, SG2a, SG3, B01, D02 (`decision-log.md` PROC-DEC-002, `implementation-plan.md`).
- Device gates: G-SEG-1 after SG2a merges (message in `briefs/TASK-SG2a.md`), G-SEG-2 after SG2b (message in `briefs/TASK-SG2b.md`). Relay them to Manu in Spanish with the latest main-push `robia-staging-apk` link.
- If a run stops with a question, answer it from the plan (quote the line) or decide low-risk details yourself and record them; material scope/UX changes go to Manu.
