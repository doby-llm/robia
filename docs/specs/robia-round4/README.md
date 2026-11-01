# robia-round4 — Collections, Play Store readiness, cleanup

**Status: PROPOSED — waiting for G-APPROVAL (Manu writes APROBADO on the planning PR).** Planner: Claude Opus 5.5 (planning only).

| File | Purpose |
|---|---|
| `SPEC.md` | Decisions (R4-D*), requirements (REQ-COL-*), Play audit and blockers (§7), ML Kit decision (§8), cleanup scope (§9) |
| `tasks.md` | Worker rules, graph, hot-file order, per-task Files/acceptance (12 tasks: C01–C05, P01–P05, CHK, CLN; plus gates) |
| `verification-matrix.md` | V- rows (CI / REV / DEV / REC) |
| `briefs/` | One brief per task (`TASK-<ID>.md`) + shared facts |
| `PROMPT_COORDINATOR.md`, `PROMPT_WORKER.md`, `PROMPT_REVIEW.md` | Copy-paste prompts |
| `mockups/collections.html` | 9 frames + EN/ES/DE copy table |
| `artifacts/` | Read-only investigations (file:line), privacy-policy draft; later data-safety facts and device checklist |
| `feedback/` | Manu's requests, verbatim |
| `decision-log.md` | Decisions and plan changes |

## Gates
- **G-APPROVAL** — APROBADO on this PR (approves every R4-D* default listed in SPEC §2–§3 and §7.2).
- **G-PLAY** — Manu: upload key + 4 GitHub secrets, Play Console app + App Signing, Android OAuth client with the
  App-signing SHA-1, consent screen in production, privacy policy published (steps in `briefs/TASK-P04.md`).
- **G-CLN** — Manu's answers to the cleanup questions (which categories to delete).
- **Device checks** — CHK checklist on the latest staging APK.

## Execution models
Coordinator Claude Sonnet 5.5 high · workers Claude Haiku 5.5 high · independent reviewers (C01, C05 only) Claude Opus 5.5 high.
