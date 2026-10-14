# Handoff — robia-embeddings-sync-manage

**Valid only after Manu writes `APROBADO` for revision 1.** Approval: **APROBADO by Manu on 2026-10-14** (Telegram), including mockups and REQ-E12. Execution by lighter models: give each worker `PROMPT_WORKER.md` with the task id.

## For the Kanban orchestrator
- Repo: `doby-llm/robia` (GitHub namespace `doby-llm`). Base: latest `origin/main`. Do not use or modify Manu's local checkout `~/git/robia`; workers clone/worktree separately. Do not touch `robia_ai`.
- Coding workers: Codex (never OpenHands). Python tooling (E02): `uv`, `UV_CACHE_DIR=/tmp/uv-cache`.
- Create one card per task in `tasks.md` with the same ID, predecessors and the waves of `implementation-plan.md`. Respect file leases.
- CI is the only build authority (`Android APK CI`). Never build Android on the Pi.
- Known: since ~2026-10 the step "Set up Android SDK" fails on every branch (runner change, not our code). TASK-B01 step 0 fixes it; run B01 first.
- Gates needing Manu: G-DEVICE-1 (after E01), G-DEVICE-2 / Z01 (end). Send him: the staging APK artifact link and the exact 3–5 steps from the task. Everything else proceeds autonomously.
- Stop conditions: any ambiguity in the spec, a needed file outside a task's list, NFR-01 failing twice, two failed remediation cycles → Blocked + one precise question to Manu.
- Planner data on the Pi (persistent, outside the scratch dir): `~/.local/share/robia-planning/` — `precision-data/` (scripts, photos `photos_raw/` never committed, vectors, provisional labels), `model/embeddinggemma-2-text-vision-440m.litertlm` (SHA in DEC-003), `planner-workspace/` (package sources, mockup/PDF generators).
- Pre-approved fallbacks (no need to ask): DEC-002 AGP/BOM fallback; E01b vision tokens 70.
- Out of scope reminders: fine-tuning, dark theme, Play release, notifications for sync, device filter.

## Worker entry point
- `PROMPT_WORKER.md` (copy-paste prompt, one task per run) → `briefs/<TASK-ID>.md` → `tasks.md` → `verification-matrix.md`.
- `briefs/README.md` explains authority order and shared facts.
