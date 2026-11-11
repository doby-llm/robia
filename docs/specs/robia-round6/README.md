# Robia round 6: batch import feedback, AI mark in batch, developer mode in release, forced duplicate check, Play update

**Status: APPROVED when Manu launches the coordinator.** The coordinator's first step is to merge this plan PR.
Planner: Hermes (Claude Opus 5.5). Source: `feedback/2026-11-11-manu.md` (F1–F4) plus "publish it directly as a new
Play update".

| File | Purpose |
|---|---|
| `tasks.md` | Worker rules, graph, hot files, and per task: root cause found in planning, brief, Files, acceptance (V- rows) |
| `decision-log.md` | Decisions taken in planning (type D: Manu can overturn them) |
| `RELEASE.md` | PUB1: version, release AAB, checks, Play Console upload (Internal + closed Alpha), release notes EN/ES/DE |
| `PROMPT_COORDINATOR.md`, `PROMPT_WORKER.md`, `PROMPT_REVIEW.md` | Copy-paste prompts |
| `feedback/` | Manu's message, verbatim |

## Gates
- **G-APPROVAL**: Manu launches the coordinator (counts as approval; recorded in the issue).
- **G-CODE**: none. Manu asked for direct publication. Replacing it: green CI on every PR, the coordinator's diff read,
  the DU1 independent review, and a green main CI before the release build. Manu tests the Internal-testing build after
  it is published (device rows at the end of `tasks.md`).
- **G-PLAY**: the Play Console upload needs the Hermes browser (`browser_exec local=True`) signed in as GusanitoLabs.
  If the coordinator runs without that tool, it stops at the PUB1 handoff and Hermes does the upload.

## Execution models
Coordinator Sonnet 5.5 high · code workers Haiku 5.5 high · DU0 read-only investigator Sonnet 5.5 high ·
DU1 independent review Opus 5.5 high (a forced scan running concurrently with the automatic one is an interleaving).

## Counted tasks ("Hechos X de 7")
DU0, BI1, BS1, DM1, REL2, DU1, PUB1.
