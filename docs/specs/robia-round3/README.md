# robia-round3 — planning package

Round 3 of Robia improvements: Manu's email of 2026-10-22 (`feedback/2026-10-22-manu-email.md`, R3-01…R3-17) and his answers the same day (`feedback/2026-10-22-manu-answers-r1.md`, incl. new items N1–N5): Browse speed and small visuals, smart-feature styling, fit filter, drag-select, analytics, export screen, original photos (phone + Drive), zip export, duplicate finder, batch cancel/ETA, precise-cutout failures (pink and red shorts), broken smart erase, full edit history, model research (d3, outfits, GPU).

Status: **PROPOSED** — waiting for Manu's APROBADO on this planning PR. Palette (R3-13) is pending by Manu's choice and not part of the round.

| File | Content |
|---|---|
| `human_summary.md` | Spanish summary: what changes, what Manu does, gates |
| `requirements.md` | REQ-* with concrete behaviour and Manu's answers |
| `decision-log.md` | R3-DEC-001…016 with evidence and rejected alternatives |
| `implementation-plan.md` | W0, waves, dependency edges, file leases, device gates (ADB), stop rules, rollback |
| `tasks.md` | worker rules + one section per task (files, scope, acceptance) |
| `verification-matrix.md` | V-* rows (CI / REC / REV / DEV) and gates |
| `briefs/` | step-by-step brief per task + shared README |
| `PROMPT_COORDINATOR.md`, `PROMPT_WORKER.md`, `PROMPT_REVIEW.md` | copy-paste prompts |
| `mockups/` | HTML mockups (synthetic garments only; `colors.html` kept for the pending palette topic) |
| `artifacts/` | investigator reports `inv-r3-a…f.md`, `inv-r3-cutout.md`, `inv-r3-models.md` |
| `../../../tools/cutout-diag/` | reference stage dump + comparison for the precise cutout (G-ADB1/G-ADB2) |
| `../../../tools/perf/browse_fling.sh` | Browse fling benchmark on the phone |
| `../../../tools/model-feasibility/` | planning benchmark scripts (d3, EmbeddingGemma 2, FashionSigLIP, Polyvore FITB) |

Roles: planner (this package; implements nothing) → coordinator Claude Sonnet 5.5 high → workers Claude Haiku 5.5 high, one task per run; independent reviewers are separate read-only Haiku runs.
Previous round `docs/specs/robia-ux-rework/` closes with W0 (PRs #132–#134).
