# robia-embeddings-sync-manage

**Status:** awaiting approval (revision 1) · **Base:** `origin/main` `791dacd` · **Owner:** Manu · **Planner:** Hermes

Replace the TFLite classifier with on-device EmbeddingGemma 2 suggestions (category, occasion, review flow, semantic search), make Google Drive sync robust and fast with actionable errors, and redesign Manage without scroll lag.

## Documents
| File | Purpose |
|---|---|
| `human_summary.md` | Spanish, user-facing summary of what will exist after implementation |
| `requirements.md` | REQ/NFR with acceptance criteria, non-goals, decision checklist |
| `decision-log.md` | DEC-001…016 with rationale and evidence |
| `technical-specification.md` | design per capability, exact files/symbols, contracts |
| `implementation-plan.md` | gates, dependency graph, waves, file leases, budgets, rollback |
| `tasks.md` | bounded worker tasks with files, scope, tests, acceptance |
| `verification-matrix.md` | frozen acceptance rows (CI / Pi / Device) |
| `handoff.md` | execution handoff to the Kanban orchestrator |
| `mockups/*.html` | Earth & Steel mobile mockups (light theme) |
| `diagrams/` | Archify architecture/dependency diagram sources |
| `artifacts/planning-review-v1.pdf` | Spanish review packet |

## Capability map
| ID | Depends on | Tasks |
|---|---|---|
| `build-foundation` | — | B01, B02, B03 |
| `cloud-sync` | build-foundation | D01 (shared), S01–S05 |
| `smart-suggestions` | build-foundation, D01 | E01, E01b, E02, E04–E10 |
| `manage-redesign` | build-foundation (B02) | M01 (+ E08 editor fields) |

Evidence from planning: precision report on 142 user photos (Pi, LiteRT-LM 0.18.0, vision tokens 140): category top-1 86 % text-only, 89–96 % combined with 1–5 examples per category; search probe shows correct Spanish queries. Raw tool output stays in the planner workspace; the tool itself is committed by TASK-E02 without personal photos.
