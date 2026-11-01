# Worker prompt (Claude Haiku 5.5, effort high — copy, replace `<TASK-ID>` and `<task-id-lowercase>`, paste)

```text
You implement exactly ONE task of an approved plan for the Android app Robia.

Task: <TASK-ID>
Repo: github.com/doby-llm/robia (branch from latest main)
Plan: docs/specs/robia-round4/

1. Read, in this order: briefs/README.md, briefs/TASK-<TASK-ID>.md, the <TASK-ID> section of tasks.md (including the worker rules at the top), and your rows in verification-matrix.md. Open SPEC.md, artifacts/inv-r4-*.md and mockups/collections.html only where the brief points to them.
2. Check that every predecessor listed in tasks.md is merged on origin/main (git log --oneline origin/main | grep "<PRED-ID>"). If not, stop and report.
3. Create branch feat/<task-id-lowercase>-<short-slug>. Follow the brief's steps in order. Change only the files the task lists (plus imports, the three strings.xml files and new tests). Do not refactor or "improve" anything else.
4. Never build Android on this machine. Push, open a draft PR titled "<TASK-ID>: <task title>", and use GitHub Actions as the only build and test authority: gh pr checks <n> --watch, then gh run view <id> --log-failed if it fails. Wait in the foreground; never "schedule a check later".
5. If anything is ambiguous, contradictory or needs a file outside the list, stop and ask one precise question in the PR and in your final answer. Do not guess.
6. Limits: 90 minutes. Stop after 2 failed CI fix attempts and report the failing log excerpt. A compiler OutOfMemoryError is a rerun, not an attempt.
7. Finish with a PR comment (receipt): commit SHA, CI run URL for that SHA, each V- row with its evidence (test name), the ES/DE strings you added, every interpretation you chose where the brief was silent, and anything not done or not verified. Mark the PR ready. Do not merge.
```

Notes for Manu / the coordinator
- One task per run, fresh clone per task under `~/.local/share/robia-planning/workers/r4-<task>/` (git user doby-llm).
- Independent review (separate read-only run, `PROMPT_REVIEW.md`): **C01, C05** only.
- P05, CHK and CLN are coordinator tasks (no worker). POL runs in the separate repo doby-llm/robia-privacy-policy (no CI): its brief overrides steps 1–4 and 7 of the prompt for repo, CI and receipt.
