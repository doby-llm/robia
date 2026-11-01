# TASK-CLN — Workspace cleanup (coordinator, host only, no PR)

## Goal
Months of Robia work left ~17 GB under `~/.local/share/robia-planning/`, 122 git worktrees of `~/git/robia`, 165 local
branches, APK copies, benchmark models and venvs. Remove what is provably unused, in the categories Manu approved
(gate G-CLN passed: his answers are recorded verbatim in `feedback/2026-10-30-manu-telegram.md`). The inventory with every path, size and evidence is on
the host (not in the public repo): `~/.local/share/robia-planning/r4/inventory/cleanup.md` (+ `worktrees.tsv`,
`workers.tsv`, `branches_local.tsv`, `worktrees_unpushed.tsv`).

## Approved scope (G-CLN, Manu 2026-11-01, R4-DEC-015) — nothing else
- **A** — worker clones under `~/.local/share/robia-planning/workers/` that are clean (only uv.lock / `__pycache__`
  noise allowed) with no unpushed commits, or whose commits are verified in origin/main (inventory rows 20–23);
  linked worktrees of classes A, B, C-a, C-b and the 7 prunable entries (rows 1–4, 8); local branches merged into
  origin/main or with content in main (rows 9–11). Keep `workers/r4-*` and any clone a live worker of THIS round uses.
- **B** — APK copies `r3-exec/apk-*` and `ux-rework/apk/` EXCEPT the newest staging copy `r3-exec/apk-bf25366`
  (current device-check build, keep until issue #137 closes); stop the leftover `adb` fork-server only if no ADB
  device session is active (`adb devices` empty and no `adb` child of a live worker).
- **C** — benchmark/research data and environments: `r2/seg/{models,out,r3bench}`, `r3/research/{llama,eg2,polyvore,mpvenv}`
  and other `r3/research` outputs, `sg2a-models/`, `venv-sg2a/`, `sg2a-*` loose files, `model/` (local LiteRT copy +
  xnnpack caches), the Hugging Face cache repo `models--google--embeddinggemma-2` (delete only the blobs its snapshot
  symlinks point to; never other HF repos such as faster-whisper, which is not Robia), shared Gradle cache
  `~/.gradle/caches/9.5.0` only if `grep -r "9.5.0"` finds no wrapper/CI of Packly or Robia using it.
  "Only where it does not interfere with the current plan": KEEP `precision-data/` (personal photos + dataset),
  every file under `~/git/robia/tools/` (code lives in the repo), and anything a round-4 brief references.
- **E** — the 7 worktrees with unique work (C-c `t_8b9545cd`, C-d `t_b33889ff`, `t_57a17af7`, `t_495785fc`,
  `t_8da89377`, `t_b0f95268`, `t_d698f592`) and the clone `workers/r3-docs7`: first archive to
  `~/.local/share/robia-planning/archive/r4-cln/<name>/` = `git format-patch origin/main..HEAD`, `git diff` (+ `--cached`),
  `git status --short`, untracked files copied with `rsync -a`, and a `README.txt` (branch, HEAD sha, date); verify the
  archive files are non-empty and readable, THEN remove the worktree/clone.
- **NOT approved — do not do:** D (deleting remote branches on GitHub), stashes, the main checkout's untracked files
  (`logs/`, `docs/ux/color_palette_playground/`), kanban `rovia` workspaces/DB, `/tmp` evidence, `~/.claude` transcripts.

## Rules
- Load Hermes skill `storage-capacity-operations` first. Work one approved category at a time; record before/after
  `df -B1 /` and a ledger at `~/.local/share/robia-planning/r4/cleanup-ledger.md` (path, bytes, evidence, result).
- Immediately before deleting each item re-verify: no live process (`pgrep -af`, `lsof +D <dir>` bounded with
  `timeout 60`), git clean (`git -C <d> status --porcelain` empty except the allowed uv.lock/__pycache__ noise),
  commits reachable on origin (`git -C <d> log --oneline --branches --not --remotes` empty, or `git cherry` all `-`).
  Any surprise → skip the item and list it as ASK in the ledger. Never relax a check to make an item pass.
- Worktrees: `git -C ~/git/robia worktree remove <path>` (never `--force` unless the only dirt is approved noise),
  then `git worktree prune`. Branches: `git branch -d` (merged) / `-D` only for the approved "content in main" list.
  Remote branches: only if Manu approved that category, via `git push origin --delete` in batches of 20, logging each.
- Never touch: `precision-data/photos_raw`, `r3/feedback-assets` (personal), any `state.json`/`issue_body.md`/
  `feedback*`/`decisions*`, `r4/`, `workers/r4-*`, the main checkout `~/git/robia` itself, `~/.android`, kanban.db,
  anything not Robia (`m5-wave1`, `~/.hermes/state.db`, other Docker containers).
- Order: E (archive first) → A → B → C. Measure `du -sb` per item before deleting; ledger each line immediately.

## Done when
Ledger complete; `df` delta reported to Manu in one Spanish line; skipped items listed.
