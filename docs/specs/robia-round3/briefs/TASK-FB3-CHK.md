# TASK-FB3-CHK — Device checklist for Round 3f

## Goal
Close Round 3f with the checklist Manu uses: the 3f rows tested on the build that contains the 3f tasks.

## Read first
`artifacts/device-checklist.md` (already marked with Manu's feedback 3 and carrying the 12 Round 3f rows, planner, R3-DEC-056 and R3-DEC-068); `verification-matrix.md` Round 3f section; the receipts of the 3f tasks; `briefs/TASK-CHK.md` (the first checklist).

## Files
`docs/specs/robia-round3/artifacts/device-checklist.md`.

## Steps
1. Fill the build under test: the latest main staging APK link, its SHA, the CI run, the signer (the same check as the first checklist: say if the signer differs).
2. For each Round 3f row compare "What to do" and "Expected" with the merged behaviour in the task receipt; change a step only where the behaviour differs from the brief, and list each change in the PR.
3. Re-run the count commands of the "Counts" section against the current matrix and checklist and fix the numbers (expected 55 / 53).
4. Rows that Manu's feedback 3 left open (see the notes in the file) stay open until he marks them.

## Tests
V-FB3-CHK-1 (REC).

## Done when
V-FB3-CHK-1 passes.

## Do not
Change code, the matrix or any document other than the checklist.
