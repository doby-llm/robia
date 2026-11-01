# TASK-CLN — Workspace cleanup (coordinator, host only, no PR)

## Goal
Months of Robia work left ~17 GB under `~/.local/share/robia-planning/`, 122 git worktrees of `~/git/robia`, 165 local
branches, APK copies, benchmark models and venvs. Remove what is provably unused, in the categories Manu approved
(gate G-CLN = his answers, recorded verbatim in `feedback/`). The inventory with every path, size and evidence is on
the host (not in the public repo): `~/.local/share/robia-planning/r4/inventory/cleanup.md` (+ `worktrees.tsv`,
`workers.tsv`, `branches_local.tsv`, `worktrees_unpushed.tsv`).

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
- Shared caches (`~/.gradle`): only the version Manu approved, after checking Packly's wrapper/CI version.
- Running processes: stop the leftover `adb` fork-server only if Manu approved and no device check is running.

## Done when
Ledger complete; `df` delta reported to Manu in one Spanish line; skipped items listed.
