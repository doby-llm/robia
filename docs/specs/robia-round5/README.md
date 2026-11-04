# Robia round 5 — palettes, promo video, Google Play

**Status: PROPOSED — waiting for G-APPROVAL (Manu writes APROBADO).** Planner: Claude Opus 5.5 (planning only).

| File | Purpose |
|---|---|
| `SPEC.md` | Goal and order, palette requirements, release hygiene, marketing assets, Play path, risks |
| `tasks.md` | Worker rules, graph, Files and acceptance per task (11 counted tasks + Hermes/Manu Play steps) |
| `verification-matrix.md` | V- rows (CI / REC / DEV / EXT) |
| `decision-log.md` | M/D decisions |
| `briefs/` | One brief per worker task, CAP shot rules, Play runbook, Play Console answer sheet |
| `artifacts/tobacco-bordeaux-airy.md` | Token table of the new default palette (with the contrast fix) |
| `feedback/` | Manu's request and answers, verbatim |
| `PROMPT_COORDINATOR.md`, `PROMPT_WORKER.md` | Copy-paste prompts |

## Gates
- **G-APPROVAL** — Manu: APROBADO.
- **G-CODE** — Manu checks the staging build (CHK5) and closes the code. Marketing and Play start after it.
- **G-CR** — Manu approves the videos and screenshots.
- **G-TESTERS** — Manu provides ≥ 15 tester e-mails (≥ 12 must stay opted in 14 consecutive days).
- **PB1–PB5** — Play Console steps by the Hermes browser agent with Manu (sign-in, first upload, irreversible clicks).

## Execution models
Coordinator Sonnet 5.5 high · workers Haiku 5.5 high · PL3 review and creative lane Opus 5.5 high ·
Play Console browser agent = Hermes subagent (Haiku 5.5) launched from Hermes chat.
