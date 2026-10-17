# TASK-Z02 — Device checklist

## Goal
Give Manu one list to validate the round on his phone.

## Steps
1. Wait until every task is merged and the `main` CI run is green (`gh run watch <id> --exit-status`).
2. Get the `robia-staging-apk` artifact id of that run (`gh api repos/doby-llm/robia/actions/runs/<run>/artifacts`).
3. Write `docs/specs/robia-ux-rework/artifacts/device-checklist.md`: APK link; signature warning ("if install conflicts on signature, do not uninstall; tell the coordinator"); rows V-Z2-01..22 grouped A (no cloud: Browse incl. layout setting, filters, Manage, form, detail, settings, PDF, review completion screen), B (photos and segmentation: V-Z2-10, 13-18, G-SEG-2 table), D (batch: V-Z2-21 timing with the `RobiaBatch` log lines, V-Z2-22 screen), C (mockup comparison: open `mockups/*.html` next to the app). Use the UI labels of CURRENT main (grep `strings.xml`). Each row: ID, steps, expected, PASS/FAIL blank.
4. Docs-only PR, merge on green.
