# Decision log — robia-round4

Types: M = Manu decided · D = planner default (approved with the packet unless overturned) · E = evidence-backed fact.

| ID | Type | Date | Decision |
|---|---|---|---|
| R4-DEC-001 | M | 2026-10-30 | Collections feature: user-made collections in Manage, multi-membership, editor field, ± members screen. |
| R4-DEC-002 | M | 2026-10-30 | The ± members screen exists ONLY for Collections (Manu withdrew the generic version for Category/Occasion; do not reintroduce). |
| R4-DEC-003 | M | 2026-10-30 | Planner only; execution by a coordinator with Haiku 5.5 high workers; delegate to Haiku where quality does not drop. |
| R4-DEC-004 | M | 2026-11-01 | The app is to be published on Google Play: audit and fix Play blockers in this round. |
| R4-DEC-005 | D | 2026-11-01 | R4-D01 … R4-D16 as written in SPEC §2–§3. |
| R4-DEC-006 | D | 2026-11-01 | ML Kit GenAI Embeddings not adopted now (alpha, Developer-Preview enrolment, AICore-only, model swaps force re-index). Follow-up F-R4-ML-1. |
| R4-DEC-007 | E | 2026-11-01 | Downloading model weights from Hugging Face/GitHub is compatible with Play's Device and Network Abuse policy (only executable code — dex/jar/.so — is forbidden). All five URLs are public, commit-pinned, size- and SHA-256-checked. |
| R4-DEC-008 | E | 2026-11-01 | Play requires targetSdk 36 for new apps/updates since 2026-08-31 → P01 is a blocker. |
| R4-DEC-009 | E | 2026-11-01 | `drive.appdata` is a non-sensitive Drive scope: no OAuth verification/CASA; consent screen must still be In production for >100 users. |
| R4-DEC-010 | D | 2026-11-01 | Staging keeps the release applicationId (Manu's phone data); follow-up F-R4-PLAY-2. Release build becomes arm64-only. |
| R4-DEC-011 | D | 2026-11-01 | Coordinator model Claude Sonnet 5.5 high (as in round 3); Opus 5.5 high only for the C01/C05 reviews. |

## Follow-ups (not in this round)
- F-R4-1 Garment editor open while the members screen saves can overwrite membership on editor save (narrow race).
- F-R4-ML-1 Re-evaluate ML Kit Embeddings at GA (optional spike on the precision dataset).
- F-R4-PLAY-1 Play for On-device AI (AI packs) instead of Hugging Face hosting, if availability becomes a problem.
- F-R4-PLAY-2 Separate staging applicationId (`.staging`) once Manu installs from Play.
- F-R4-2 Per-link tombstones for removed tag/collection memberships (merge proposal could re-add a removed link).
- F-R4-3 Browse multi-select "Add to collection"; collections in export/share/analytics.
