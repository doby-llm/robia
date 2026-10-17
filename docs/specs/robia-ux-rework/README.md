# robia-ux-rework — planning package

Round 2 of Robia improvements requested by Manu on 2026-10-16 (`feedback/2026-10-16-manu-email.md`, items R1–R15): Browse cards, filters, Manage swipe, garment form/detail, processing feedback, segmentation (reliability, edges, precise cutout pack, refine tool), PDF, Settings, haptics/motion and two new categories.

Status: **Revision 1, awaiting APROBADO** (questions Q1–Q7 in the PR description; the plan uses option A for each).

| File | Content |
|---|---|
| `human_summary.md` | Spanish summary of what Manu will see and do |
| `requirements.md` | REQ-* with concrete behaviour |
| `decision-log.md` | UX-/SEG-/PROC-DEC with evidence and rejected alternatives |
| `technical-specification.md` | TS§0–§Z: APIs, files, constants, model I/O and hashes |
| `implementation-plan.md` | waves, leases, graph, stop rules, rollback |
| `tasks.md` | worker rules + one section per task (files, scope, acceptance) |
| `verification-matrix.md` | V-* rows (CI / receipt / review / device) and gates G-SEG-1/2 |
| `briefs/` | one step-by-step brief per task + shared README |
| `PROMPT_WORKER.md` | copy-paste prompt for the implementing model |
| `mockups/` | HTML mockups (Earth & Steel, light; synthetic garments only) |
| `artifacts/` | investigator reports (`inv-r2-*.md`), segmentation/taxonomy evidence (`seg-benchmark.md`) |
| `../../../tools/segmentation-bench/` | Python reference pipeline used for the benchmark |

Roles: planner (this package) → coordinator model (dispatch, review, merge, gates) → implementer model (one task per run).
Previous plan (`docs/specs/robia-embeddings-sync-manage/`) is complete; its open follow-ups stay on issue #22 and are not part of this round.
