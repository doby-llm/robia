# Worker prompt (Claude Haiku 5.5, effort high): replace `<TASK-ID>` and `<task-id-lowercase>`, pass on stdin

```text
You implement exactly ONE task of an approved plan for the Android app Robia.

Task: <TASK-ID>
Repo: github.com/doby-llm/robia (branch from latest main)
Plan: docs/specs/robia-round6/

1. Read, in this order: the worker rules at the top of tasks.md, the <TASK-ID> section of tasks.md, and decision-log.md. DU1 also reads artifacts/inv-r6-duplicates.md. The section holds the brief, root cause, Files, copy and acceptance rows.
2. Check that every predecessor is merged on origin/main (git log --oneline origin/main | grep "<PRED-ID>"). If not, stop and report.
3. Verify the planning root cause in the code before changing anything. If it does not match, stop and say what you found.
4. Create branch feat/<task-id-lowercase>-<short-slug>. Change only the listed files and regions, plus imports, the three strings.xml files and new tests. Do not refactor or "improve" anything else.
5. Never build Android on this machine. Push, open a draft PR titled "<TASK-ID>: <task title>", and use GitHub Actions as the only build and test authority: gh pr checks <n> --watch, and gh run view <id> --log-failed if it fails. Wait in the foreground. Never "schedule a check later".
6. If anything is ambiguous, contradictory or needs a file outside the list, stop and ask one precise question in the PR and in your final answer. Do not guess.
7. Limits: 90 minutes. Stop after 2 failed CI fix attempts and report the failing log excerpt. A compiler OutOfMemoryError is a rerun, not an attempt.
8. Finish with a PR comment (receipt): commit SHA, CI run URL for that SHA, each V- row with its evidence (test name), the ES/DE strings you added, every interpretation you chose where the brief was silent, and anything not done or not verified. Mark the PR ready. Do not merge.
```

Notes: one fresh clone per task under `~/.local/share/robia-planning/workers/r6-<task>/` (git user doby-llm).
Launch: `cd <clone> && CLAUDE_CONFIG_DIR=<dir> claude -p --model claude-haiku-5-5 --effort high --permission-mode bypassPermissions < prompt.txt > log 2>&1`.

## DU0 investigator (Sonnet 5.5 high, read-only)
`claude -p --model claude-sonnet-5-5 --effort high --permission-mode bypassPermissions --disallowedTools Edit Write < prompt.txt`.
Prompt: "Read docs/specs/robia-round6/tasks.md section DU0 and answer it. Read-only. Your final answer is the
artifact text (markdown, 600–900 words): one section per cause with file:line, CERTAIN or HYPOTHESIS, the smallest
fix, and whether that fix fits DU1's Files." The coordinator saves the answer as `artifacts/inv-r6-duplicates.md`
in a docs-only PR and merges it.
