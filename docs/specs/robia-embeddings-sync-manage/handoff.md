# Handoff — robia-embeddings-sync-manage

**Valid only after Manu writes `APROBADO` for revision 1.** Approval: mockups approved 2026-10-14 (incl. REQ-E12 "See similar garments"); package `APROBADO` _pending_.

## For the Kanban orchestrator
- Repo: `doby-llm/robia` (GitHub namespace `doby-llm`). Base: latest `origin/main`. Do not use or modify Manu's local checkout `~/git/robia`; workers clone/worktree separately. Do not touch `robia_ai`.
- Coding workers: Codex (never OpenHands). Python tooling (E02): `uv`, `UV_CACHE_DIR=/tmp/uv-cache`.
- Create one card per task in `tasks.md` with the same ID, predecessors and the waves of `implementation-plan.md`. Respect file leases.
- CI is the only build authority (`Android APK CI`). Never build Android on the Pi.
- Gates needing Manu: G-DEVICE-1 (after E01), G-DEVICE-2 / Z01 (end). Send him: the staging APK artifact link and the exact 3–5 steps from the task. Everything else proceeds autonomously.
- Stop conditions: any ambiguity in the spec, a needed file outside a task's list, NFR-01 failing twice, two failed remediation cycles → Blocked + one precise question to Manu.
- Planner workspace inputs for E02: `~/.hermes/cache/scratch/robia-precision/scripts/` (scripts), `~/.hermes/cache/scratch/robia-precision/provisional_labels.json`, photos `photos_raw/` (never committed), model `~/.hermes/cache/scratch/litertlm-spike/model.litertlm` (SHA in DEC-003), venv `~/.hermes/cache/scratch/litertlm-spike/.venv` (reference only; E02 creates a uv project).
- Pre-approved fallbacks (no need to ask): DEC-002 AGP/BOM fallback; E01b vision tokens 70.
- Out of scope reminders: fine-tuning, dark theme, Play release, notifications for sync, device filter.
