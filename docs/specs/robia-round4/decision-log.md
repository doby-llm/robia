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

| R4-DEC-012 | M | 2026-11-01 | APROBADO (answer 1A). Coordinator Claude Sonnet 5.5 high (3a); workers Haiku 5.5 high; C01/C05 reviews Opus 5.5 high. |
| R4-DEC-013 | M | 2026-11-01 | Privacy policy lives in a separate public repo so it survives if robia goes private: the existing `doby-llm/robia-privacy-policy` (Packly precedent). URL `https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md`. Its outdated content is fixed by task POL. |
| R4-DEC-014 | M | 2026-11-01 | Play Console account GusanitoLabs exists (the.gusanito.lab@gmail.com). Play upload/Console work (G-PLAY, P05) is deferred until Manu finishes screenshots and colours; it may later be done by a browser agent on that account. P01–P04 and POL go ahead now. |
| R4-DEC-015 | M | 2026-11-01 | Cleanup: categories A, B, C ("as long as they do not interfere with the current plan") and E approved; D (deleting merged remote branches) not approved. Interpretation (type D): B keeps the newest staging APK copy (`apk-bf25366`, current device-check build) until issue #137 closes; C keeps `precision-data/` entirely (personal photos and the dataset the optional ML spike F-R4-ML-1 would use) and the tools' code in the repo. |
| R4-DEC-016 | E | 2026-11-01 | magic_touch.tflite licence CONFIRMED Apache-2.0: official MediaPipe model card "Model Card MagicTouch.pdf" (https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf) says "LICENSED UNDER Apache License, Version 2.0", authors Valentin Bazarevsky and Ben Hahn (Google), 13 Mar 2023; SHA-256 of the bundled file equals the upstream file (e24338a7…cd25). Resolves the P03 open item. |
| R4-DEC-017 | D | 2026-11-01 | Device-test feedback (feedback/2026-11-01-manu-device-test.md): tasks FT1 (chip wrap, privacy-link crash, licences row less prominent, magic_touch licence text) and FT2 (Collections group in Analytics). Licences stay reachable in-app (Apache-2.0 notice duty; Play review) but move from the General group to the About group: tapping the app-version row opens them, no dedicated row. Analytics Collections group shows one bar per collection (tap = Browse filtered by that collection) and a "Not set" bar; the group is omitted when no collection exists. |

## Follow-ups (not in this round)
- F-R4-1 Garment editor open while the members screen saves can overwrite membership on editor save (narrow race).
- F-R4-ML-1 Re-evaluate ML Kit Embeddings at GA (optional spike on the precision dataset).
- F-R4-PLAY-1 Play for On-device AI (AI packs) instead of Hugging Face hosting, if availability becomes a problem.
- F-R4-PLAY-3 Play Console plan (browser agent on GusanitoLabs): app setup, listing, Data safety, closed test (if personal account: 12 testers × 14 days), production access.
- F-R4-PLAY-2 Separate staging applicationId (`.staging`) once Manu installs from Play.
- F-R4-2 Per-link tombstones for removed tag/collection memberships (merge proposal could re-add a removed link).
- F-R4-3 Browse multi-select "Add to collection"; collections in export/share/analytics.
