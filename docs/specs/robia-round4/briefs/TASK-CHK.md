# TASK-CHK — Device checklist round 4 (docs only)

## Goal
One file Manu can follow on the phone: `docs/specs/robia-round4/artifacts/device-checklist.md`.

## Steps
1. Latest green main-push run → `robia-staging-apk` artifact link (`gh api repos/doby-llm/robia/actions/runs/<run>/artifacts`),
   signer SHA-256 (`apksigner verify --print-certs`) and whether it matches the previous staging (same = update keeps data).
2. Translate every `-D` row of `verification-matrix.md` (V-C02-D, V-C03-D, V-C05-D, V-P01-D, V-P02-D, V-P03-D) into
   numbered plain-Spanish steps with "what you should see", using the UI labels of current main (`grep` the strings
   files), answerable as `<number> PASS/FAIL`. Never show matrix ids to Manu.
3. Order: Collections (no cloud) → filters/editor → Android 16 checks → Drive sync between two phones (if he has two)
   → Settings links. Mark destructive steps (none expected) and put them last.
4. Known limitations section: R4-D06 "not in v1" list, R4-D09, follow-ups.

## Done when
V-CHK-1; docs-only PR merged on green CI.
