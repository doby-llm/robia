# robia-ux-rework — planning package

Round 2 of Robia improvements requested by Manu on 2026-10-16 (`feedback/2026-10-16-manu-email.md`, items R1–R15) and his answers to revision 1 (`feedback/2026-10-17-manu-rev1-feedback.md`): Browse (photos-only default + details setting), filters, Manage swipe, garment form/detail, processing feedback, segmentation (reliability, edges, precise cutout pack with hanger-first), photo editor (cutout + light), batch speed and batch list with ETA, review completion screen, PDF (sparse garments), Settings, motion (no haptics) and categories (Socks, Underwear, Accessories with belts).

Status: **APPROVED (revision 3)** — Manu wrote APROBADO on 2026-10-18 (Telegram, `feedback/2026-10-18-manu-approval.md`). G-APPROVAL passes when this docs PR (#98) is merged. Rev 3 adds U10, C01, D01, D02 (see `decision-log.md` rev-3 section).

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
| `artifacts/` | investigator reports (`inv-r2-*.md`), segmentation/taxonomy evidence (`seg-benchmark.md`), Spanish decision PDF (`planning-review-r2.pdf`) |
| `PROMPT_COORDINATOR.md` | copy-paste prompt for the coordinating model |
| `../../../tools/segmentation-bench/` | Python reference pipeline used for the benchmark |

Roles: planner (this package; plans only, implements nothing) → coordinator **Claude Sonnet 5.5, effort high** (dispatch, review, merge, gates; `PROMPT_COORDINATOR.md`) → workers **Claude Haiku 5.5, effort high**, one task per run (`PROMPT_WORKER.md`); independent reviewers are separate read-only Haiku 5.5 high runs.
Previous plan (`docs/specs/robia-embeddings-sync-manage/`) is complete; its open follow-ups stay on issue #22 and are not part of this round.
